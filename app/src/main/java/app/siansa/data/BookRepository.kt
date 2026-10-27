package app.siansa.data

import app.siansa.log.AppLog
import app.siansa.net.AbsLibrary
import app.siansa.net.AudiobookshelfApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Books and podcasts from Audiobookshelf for the screens (issue #521): the libraries, the Books panel's shelves,
 * all books, progress. Read live from the server (no library sync for books), with the last answers kept while
 * the app runs so pages open at once. Progress is the server's record; the player updates it.
 */
class BookRepository(private val api: AudiobookshelfApi, private val settings: () -> AppSettings) {
    private var libraries: List<AbsLibrary>? = null
    private val booksById = HashMap<String, Book>()
    private val _progress = MutableStateFlow<Map<String, BookProgress>>(emptyMap())
    /** Progress by book id, as last read from the server or set by the player. */
    val progress: StateFlow<Map<String, BookProgress>> = _progress.asStateFlow()

    val ready: Boolean get() = settings().booksReady

    suspend fun libraries(): List<AbsLibrary> = libraries ?: api.libraries().also { libraries = it }

    private val _shelves = MutableStateFlow<Map<Boolean, List<BookShelf>>>(emptyMap())
    /**
     * Home's shelves as last read, books under false and podcasts under true (#569). Kept while the app runs, so the
     * carousel comes back with its cards, at its full height, when it scrolls into view again.
     */
    val shelvesState: StateFlow<Map<Boolean, List<BookShelf>>> = _shelves.asStateFlow()
    private val shelvesAt = HashMap<Boolean, Long>()
    private var progressAt = 0L
    private val refreshing = Mutex()

    /**
     * Reads Home's shelves and the progress again when the last read is older than [FRESH_MS] (#569), else keeps them.
     * Once at a time, so the books and podcasts carousels don't both ask. With no library of that kind, nothing is
     * asked at all.
     */
    suspend fun refreshShelves(podcasts: Boolean, force: Boolean = false) = refreshing.withLock {
        val now = System.currentTimeMillis()
        if (!force && isFresh(shelvesAt[podcasts], now)) return@withLock
        val libs = attempt("Libraries") { libraries() } ?: return@withLock
        if (libs.none { it.podcasts == podcasts }) {
            shelvesAt[podcasts] = now
            _shelves.value = _shelves.value + (podcasts to emptyList())
            return@withLock
        }
        refreshProgressLocked(force, now)
        _shelves.value = _shelves.value + (podcasts to shelves(podcasts))
        shelvesAt[podcasts] = now
    }

    /** The shelves of every book library, then every podcast library (Audiobookshelf's own home order). */
    suspend fun shelves(podcasts: Boolean): List<BookShelf> {
        val libs = libraries().filter { it.podcasts == podcasts }
        val shelves = libs.flatMap { lib -> attempt("Shelves of ${lib.name}") { api.shelves(lib.id) } ?: emptyList() }
        shelves.forEach { s -> s.books.forEach { booksById[it.id] = it } }
        AppLog.i("Books", "${if (podcasts) "Podcast" else "Book"} shelves: ${shelves.size} from ${libs.size} libraries, ${shelves.sumOf { it.books.size }} items")
        return shelves
    }

    /** Every book (or podcast) the user can see, [sort] as Audiobookshelf names it, across all its libraries. */
    suspend fun all(podcasts: Boolean, sort: BookSort): List<Book> {
        val libs = libraries().filter { it.podcasts == podcasts }
        val out = ArrayList<Book>()
        for (lib in libs) {
            var page = 0
            while (true) {
                val items = api.items(lib.id, page, PAGE, sort.field, sort.desc)
                out += items
                if (items.size < PAGE) break
                page++
            }
        }
        out.forEach { booksById[it.id] = it }
        AppLog.i("Books", "All ${if (podcasts) "podcasts" else "books"}: ${out.size}")
        return out
    }

    /** A book with its chapters: from the server, then kept. */
    /** Books and podcasts across every library matching [query]; a library that fails is skipped (issue #531). */
    suspend fun search(query: String): List<Book> = libraries().flatMap { lib ->
        runCatching { api.search(lib.id, query) }.onFailure { AppLog.w("Books", "Search in ${lib.name}: ${it.message}") }.getOrDefault(emptyList())
    }.distinctBy { it.id }.also { list -> list.forEach { booksById[it.id] = it } }

    suspend fun book(id: String): Book = api.book(id).also { booksById[id] = it }

    /** The book as last seen, without asking the server; null when never loaded. */
    fun known(id: String): Book? = booksById[id]

    /**
     * [block]'s answer, or null with a warning when the server fails. A cancelled call (the screen asking for it has
     * gone) is passed on, not logged as the server's failure (#553).
     */
    private suspend fun <T> attempt(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.w("Books", "$what: ${e.message}")
        null
    }

    /** Reads every progress the user has, unless read within [FRESH_MS] (#569); the player keeps it current between. */
    suspend fun refreshProgress(force: Boolean = false) = refreshing.withLock { refreshProgressLocked(force, System.currentTimeMillis()) }

    private suspend fun refreshProgressLocked(force: Boolean, now: Long) {
        if (!force && isFresh(progressAt, now)) return
        val all = attempt("Progress") { api.allProgress() } ?: return
        _progress.value = all
        progressAt = now
    }

    /** What the player found out, kept here so the screens follow. */
    fun noteProgress(p: BookProgress) {
        _progress.value = _progress.value + (p.bookId to p)
    }

    /** Books the listener is part-way through, newest first, for "Continue listening". */
    suspend fun inProgress(): List<Book> = api.inProgress().also { list -> list.forEach { booksById[it.id] = it } }

    companion object {
        const val PAGE = 100
        /** How long Home's shelves and the progress count as fresh: scrolling past doesn't ask the server again (#569). */
        const val FRESH_MS = 60_000L

        /** Whether something read at [at] is still fresh at [now]; never read is never fresh. */
        fun isFresh(at: Long?, now: Long, freshMs: Long = FRESH_MS): Boolean = at != null && at > 0 && now - at in 0 until freshMs
    }
}

/** How a list of books is sorted (issue #521), by Audiobookshelf's field names. */
enum class BookSort(val label: String, val field: String, val desc: Boolean) {
    RECENT("Recents", "mtimeMs", true),
    TITLE("Title", "media.metadata.title", false),
    AUTHOR("Author", "media.metadata.authorName", false),
    SERIES("Series", "media.metadata.seriesName", false),
    ADDED("Recently added", "addedAt", true),
}
