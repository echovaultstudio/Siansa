package app.siansa.data

import kotlinx.serialization.Serializable

/**
 * An audiobook or podcast on the Audiobookshelf server (issue #521). Books and podcasts stay apart from music:
 * never in mixes, radios, scrobbling or history.
 */
@Serializable
data class Book(
    /** Audiobookshelf's library item id. */
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val narrators: List<String> = emptyList(),
    /** The series it's in, and its number there ("Lantern Coast", "2"). */
    val series: String? = null,
    val sequence: String? = null,
    val year: Int? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val durationSec: Int = 0,
    val chapters: List<Chapter> = emptyList(),
    /** Audiobookshelf's library the book is in. */
    val libraryId: String = "",
    /** A podcast rather than a book. */
    val podcast: Boolean = false,
    val addedAt: Long = 0L,
) {
    /** The cover id Siansa's cover cache knows: Audiobookshelf's item through its cover endpoint. */
    val cover: String get() = COVER_PREFIX + id

    /** "Ada Quill", or "Ada Quill, Ben Reed". */
    val author: String get() = authors.joinToString(", ")
    val narrator: String get() = narrators.joinToString(", ")

    /** The chapter at [positionSec], or null when the book has no chapters. */
    fun chapterAt(positionSec: Double): Chapter? = chapters.lastOrNull { it.startSec <= positionSec }

    companion object {
        /** Cover ids of books start with this, so the cover loader knows where to fetch from. */
        const val COVER_PREFIX = "bk-"
    }
}

/** A chapter: where it starts and ends in the whole book, in seconds. */
@Serializable
data class Chapter(val index: Int, val title: String, val startSec: Double, val endSec: Double) {
    val durationSec: Double get() = endSec - startSec
}

/** How far through a book the listener is, as Audiobookshelf keeps it. */
@Serializable
data class BookProgress(
    val bookId: String,
    val currentSec: Double = 0.0,
    val durationSec: Double = 0.0,
    /** 0..1 */
    val fraction: Double = 0.0,
    val finished: Boolean = false,
    /** When it was last updated, epoch ms. */
    val updatedAt: Long = 0L,
    /** The episode this is for, when the "book" is a podcast (beta #45); null for a book. */
    val episodeId: String? = null,
) {
    val started: Boolean get() = currentSec > 0.0 || finished
    val leftSec: Double get() = (durationSec - currentSec).coerceAtLeast(0.0)
}

/** A shelf on the Books panel, as Audiobookshelf's own home has them ("Continue listening", "Recently added"). */
data class BookShelf(val id: String, val label: String, val books: List<Book>)

/** The hours and minutes a book takes: "12 h 11 min", "48 min". */
fun bookLength(sec: Double): String {
    val total = sec.toLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    return when {
        h > 0 && m > 0 -> "$h h $m min"
        h > 0 -> "$h h"
        else -> "$m min"
    }
}
