package app.siansa.playback

import app.siansa.AppGraph
import app.siansa.data.Book
import app.siansa.data.BookProgress
import app.siansa.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the book player is doing (issue #521), for the book page, the mini player and the player page. */
data class BookPlayerState(
    val book: Book? = null,
    val positionSec: Double = 0.0,
    val playing: Boolean = false,
    val speed: Float = 1f,
)

/**
 * Plays audiobooks and podcasts (issue #521), apart from the music player: no queue, shuffle, repeat, crossfade
 * or scrobbling. Progress is Audiobookshelf's record: read when a book opens, written while it plays.
 *
 * Step 2 of #521: the state and the progress it keeps; the audio itself arrives with the player page (step 3).
 */
class BookPlayer(private val graph: AppGraph) {
    private val _state = MutableStateFlow(BookPlayerState())
    val state: StateFlow<BookPlayerState> = _state.asStateFlow()

    /** Starts [book] at [fromSec]: the first chapter, where the listener was, or a chapter they tapped. */
    fun play(book: Book, fromSec: Double) {
        AppLog.i("BookPlayer", "Play \"${book.title}\" from ${fromSec.toInt()} s")
        _state.value = BookPlayerState(book = book, positionSec = fromSec, playing = true, speed = _state.value.speed)
        graph.books.noteProgress(progressAt(book, fromSec))
    }

    /** Audiobookshelf's record of where the listener is, from a position. */
    private fun progressAt(book: Book, sec: Double, finished: Boolean = false) = BookProgress(
        bookId = book.id,
        currentSec = sec,
        durationSec = book.durationSec.toDouble(),
        fraction = if (book.durationSec > 0) (sec / book.durationSec).coerceIn(0.0, 1.0) else 0.0,
        finished = finished,
        updatedAt = System.currentTimeMillis(),
    )

    /** Marks a book finished, or not: on the server and in the screens. */
    fun markFinished(book: Book, finished: Boolean) {
        val now = graph.books.progress.value[book.id]
        val sec = if (finished) book.durationSec.toDouble() else 0.0
        val p = progressAt(book, if (finished) sec else now?.currentSec?.takeIf { !now.finished } ?: 0.0, finished)
        graph.books.noteProgress(p)
        graph.scope.launch {
            try {
                graph.audiobookshelf.setProgress(book.id, p.currentSec, p.durationSec, finished)
                AppLog.i("BookPlayer", "\"${book.title}\" marked ${if (finished) "finished" else "unfinished"}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("BookPlayer", "Couldn't mark \"${book.title}\": ${e.message}")
            }
        }
    }
}
