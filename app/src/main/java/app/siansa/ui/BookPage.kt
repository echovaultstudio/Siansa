package app.siansa.ui

import app.siansa.data.Book
import app.siansa.data.BookProgress
import app.siansa.data.bookLength
import app.siansa.net.Episode
import app.siansa.playback.BookPlayerState

/**
 * Where the listener is on a book's or podcast's page (issue #626): for a podcast that's an episode, with its own
 * length, since a podcast as a whole has none (its page showed "47 min of 0 min", with a full bar).
 */
data class BookPosition(
    /** The episode's title on a podcast; null on a book. */
    val episodeTitle: String?,
    val episodeId: String?,
    val currentSec: Double,
    val totalSec: Double,
    val finished: Boolean,
) {
    val fraction: Float get() = if (totalSec > 0) (currentSec / totalSec).toFloat().coerceIn(0f, 1f) else 0f
    val leftSec: Double get() = (totalSec - currentSec).coerceAtLeast(0.0)

    /** "47 min of 1 h 26 min · 55 % · 39 min left". */
    val line: String get() = "${bookLength(currentSec)} of ${bookLength(totalSec)} · ${(fraction * 100).toInt()} % · ${bookLength(leftSec)} left"
}

/**
 * The position shown on [book]'s page: while it plays, where the player is; else Audiobookshelf's saved progress.
 * Null when there's nothing to show yet (not started, or no length to measure against).
 */
fun bookPosition(book: Book, playing: BookPlayerState, progress: BookProgress?, episodes: List<Episode>): BookPosition? {
    val live = playing.book?.takeIf { it.id == book.id }
    val position = if (book.podcast) {
        if (live != null) {
            // Playing an episode: the player's book is the episode, with its own title and length.
            val ep = episodes.firstOrNull { it.title == live.title }
            BookPosition(live.title, ep?.id ?: progress?.episodeId, playing.positionSec, live.durationSec.toDouble().takeIf { it > 0 } ?: ep?.durationSec?.toDouble() ?: 0.0, finished = false)
        } else {
            val p = progress ?: return null
            val ep = episodes.firstOrNull { it.id == p.episodeId }
            BookPosition(ep?.title, p.episodeId, p.currentSec, p.durationSec.takeIf { it > 0 } ?: ep?.durationSec?.toDouble() ?: 0.0, p.finished)
        }
    } else {
        val current = live?.let { playing.positionSec } ?: progress?.currentSec ?: return null
        BookPosition(null, null, current, book.durationSec.toDouble(), finished = live == null && progress?.finished == true)
    }
    return position.takeIf { it.totalSec > 0 && (it.currentSec > 0 || it.finished) }
}

/**
 * The details under a book's author, like an album's: "Podcast · Comedy, Society · 5 episodes", or
 * "Audiobook · read by Ray Porter · The Expanse, book 2 · 2012 · 12 h 11 min".
 */
fun bookDetails(book: Book, episodeCount: Int?, finished: Boolean): String = buildList {
    add(if (book.podcast) "Podcast" else "Audiobook")
    if (!book.podcast && book.narrator.isNotBlank()) add("read by ${book.narrator}")
    book.series?.takeIf { it.isNotBlank() }?.let { s -> add(s + (book.sequence?.takeIf { it.isNotBlank() }?.let { ", book $it" } ?: "")) }
    if (book.genres.isNotEmpty()) add(book.genres.take(3).joinToString(", "))
    book.year?.let { add(it.toString()) }
    if (book.podcast) episodeCount?.takeIf { it > 0 }?.let { add(if (it == 1) "1 episode" else "$it episodes") }
    else if (book.durationSec > 0) add(bookLength(book.durationSec.toDouble()))
    if (finished) add("Finished")
}.joinToString(" · ")

/** A description is folded to a few lines until tapped, when it's longer than this. */
const val BOOK_ABOUT_LINES = 4
