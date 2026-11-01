package app.siansa.mix

import app.siansa.data.Album
import app.siansa.data.Book
import app.siansa.data.BookProgress
import app.siansa.data.Song
import app.siansa.data.bookLength
import app.siansa.data.cover
import app.siansa.net.Episode

/**
 * One row of a Cover rows card (issue #782): a cover with three lines beside it. [small] is who it's by (the artist,
 * the author, the podcast), [title] what it is, [meta] how much of it there is ("11 songs · 2024", "16 h 10 min ·
 * 6 h left"). [progress] (0–1) shows along the bottom of a book's or an episode's cover once it's started.
 */
data class CoverRow(
    val key: String,
    val kind: Kind,
    val cover: String?,
    val small: String,
    val title: String,
    val meta: String,
    val progress: Float? = null,
    /** Two colours (ARGB) drawn as a block where there's no cover: a radio or a genre on Home (issue #789). */
    val colors: Pair<Long, Long>? = null,
    /** A round cover (an artist). */
    val round: Boolean = false,
) {
    enum class Kind { ALBUM, SONG, BOOK, EPISODE, OTHER }
}

/** What a card's rows are, from what it holds. */
object CoverRows {
    /** At most this many rows on a card on Home; its page has all of them. */
    const val ON_HOME = 15

    /** Rows per column on Home. */
    const val PER_COLUMN = 3

    fun album(album: Album): CoverRow = CoverRow(
        key = "album-${album.id}",
        kind = CoverRow.Kind.ALBUM,
        cover = album.coverArt,
        small = album.artist,
        title = album.name,
        meta = listOfNotNull(songCount(album.songCount), album.year?.toString()).joinToString(" · "),
    )

    fun song(song: Song): CoverRow = CoverRow(
        key = "song-${song.id}",
        kind = CoverRow.Kind.SONG,
        cover = song.cover,
        small = song.artist,
        title = song.title,
        meta = listOfNotNull(song.album.ifBlank { null }, song.durationSec.takeIf { it > 0 }?.let(::clock)).joinToString(" · "),
    )

    fun book(book: Book, progress: BookProgress?): CoverRow = CoverRow(
        key = "book-${book.id}",
        kind = CoverRow.Kind.BOOK,
        cover = book.cover,
        small = book.author,
        title = book.title,
        meta = listOfNotNull(bookLength(book.durationSec.toDouble()).takeIf { book.durationSec > 0 }, progressText(progress)).joinToString(" · "),
        progress = progress?.takeIf { it.started && !it.finished }?.fraction?.toFloat()?.coerceIn(0f, 1f),
    )

    fun episode(podcast: Book, episode: Episode, progress: BookProgress?): CoverRow = CoverRow(
        key = "episode-${episode.id}",
        kind = CoverRow.Kind.EPISODE,
        cover = podcast.cover,
        small = podcast.title,
        title = episodeTitle(episode),
        meta = listOfNotNull(bookLength(episode.durationSec.toDouble()).takeIf { episode.durationSec > 0 }, progressText(progress) ?: "new".takeIf { progress == null }).joinToString(" · "),
        progress = progress?.takeIf { it.started && !it.finished }?.fraction?.toFloat()?.coerceIn(0f, 1f),
    )

    /** "Ep. 479 · Exploring the Dopamine Chamber", or just the title without a number. */
    fun episodeTitle(episode: Episode): String =
        episode.number?.trim()?.takeIf { it.isNotEmpty() }?.let { "Ep. $it · ${episode.title}" } ?: episode.title

    /** "6 h left", "finished", or null before it's started. */
    fun progressText(progress: BookProgress?): String? = when {
        progress == null -> null
        progress.finished -> "finished"
        progress.started -> "${bookLength(progress.leftSec)} left"
        else -> null
    }

    fun songCount(n: Int): String? = when {
        n <= 0 -> null
        n == 1 -> "1 song"
        else -> "$n songs"
    }

    /** Rows in columns of [perColumn], for swiping sideways. */
    fun columns(rows: List<CoverRow>, perColumn: Int = PER_COLUMN): List<List<CoverRow>> = rows.chunked(perColumn.coerceAtLeast(1))

    private fun clock(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
}
