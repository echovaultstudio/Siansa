package app.siansa.offline

import android.content.Context
import app.siansa.data.Book
import app.siansa.log.AppLog
import app.siansa.net.AudiobookshelfApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Whole-book downloads (issue #521, owner's decision: the whole book, never part): a book's files under
 * books/<id>/, one per file as Audiobookshelf sends them, so the book player plays them off the phone when
 * there, with the same positions. Progress still syncs to the server when it can be reached.
 */
class BookDownloads(context: Context, private val http: OkHttpClient, private val api: AudiobookshelfApi) {
    private val root = File(context.filesDir, "books")
    private val _state = MutableStateFlow(scan())
    /** Downloaded book ids, and the one being fetched with its progress (files done of total). */
    val state: StateFlow<BookDownloadState> = _state.asStateFlow()

    private fun dir(id: String) = File(root, id.replace(Regex("[^A-Za-z0-9_-]"), "_"))

    private fun scan(): BookDownloadState = BookDownloadState(
        done = root.listFiles()?.filter { File(it, DONE).isFile }?.map { it.name }?.toSet().orEmpty(),
    )

    fun isDownloaded(bookId: String): Boolean = File(dir(bookId), DONE).isFile

    /** The downloaded file for the book's file [index], or null when the book isn't on the phone. */
    fun fileFor(bookId: String, index: Int): File? = File(dir(bookId), "$index.audio").takeIf { isDownloaded(bookId) && it.isFile }

    /** Fetches every file of [book]. Opens a session for the file list, then closes it without a listen. */
    suspend fun download(book: Book) = withContext(Dispatchers.IO) {
        val d = dir(book.id)
        d.mkdirs()
        _state.value = _state.value.copy(fetching = book.id, fetched = 0, of = 0)
        try {
            val session = api.startSession(book.id)
            _state.value = _state.value.copy(of = session.tracks.size)
            AppLog.i("BookDownloads", "Downloading \"${book.title}\": ${session.tracks.size} files")
            session.tracks.forEachIndexed { i, track ->
                val target = File(d, "$i.audio")
                if (!target.isFile) {
                    val part = File(d, "$i.part")
                    http.newCall(Request.Builder().url(api.trackUrl(track.contentUrl)).build()).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Audiobookshelf answered HTTP ${response.code} for file ${i + 1}")
                        val body = response.body ?: throw IOException("No data for file ${i + 1}")
                        part.outputStream().use { out -> body.byteStream().copyTo(out) }
                    }
                    if (!part.renameTo(target)) throw IOException("Couldn't save file ${i + 1}")
                }
                _state.value = _state.value.copy(fetched = i + 1)
            }
            runCatching { api.syncSession(session.id, session.startSec, 0.0, session.durationSec, close = true) }
            File(d, DONE).writeText(book.id)
            _state.value = scan().copy(fetching = null)
            AppLog.i("BookDownloads", "Downloaded \"${book.title}\" (${session.tracks.size} files)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("BookDownloads", "Download of \"${book.title}\" failed: ${e.message}")
            _state.value = scan().copy(fetching = null)
            throw e
        }
    }

    fun remove(bookId: String) {
        dir(bookId).deleteRecursively()
        _state.value = scan()
        AppLog.i("BookDownloads", "Removed the download of $bookId")
    }

    companion object {
        private const val DONE = "done"
    }
}

data class BookDownloadState(val done: Set<String> = emptySet(), val fetching: String? = null, val fetched: Int = 0, val of: Int = 0)
