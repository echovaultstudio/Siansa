package app.siansa.data

import app.siansa.log.AppLog
import app.siansa.net.AbsLibrary
import app.siansa.net.AudiobookshelfApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    /** Reads every progress the user has, once per app start or when asked. */
    suspend fun refreshProgress() {
        val all = attempt("Progress") { api.allProgress() } ?: return
        _progress.value = all
    }

    /** What the player found out, kept here so the screens follow. */
    fun noteProgress(p: BookProgress) {
        _progress.value = _progress.value + (p.bookId to p)
    }

    /** Books the listener is part-way through, newest first, for "Continue listening". */
    suspend fun inProgress(): List<Book> = api.inProgress().also { list -> list.forEach { booksById[it.id] = it } }

    companion object {
        const val PAGE = 100
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
