package app.musix.ui

import app.musix.data.Song
import app.musix.data.cover

/**
 * Where things go on a time machine memory's picture (issue #186), kept apart from the drawing so it can be
 * checked without a phone: the same space around the grid on every side, the same gap between the covers.
 */
object MemoryLayout {
    const val SIZE = 1000
    /** Around the grid, on every side. */
    const val MARGIN = 56
    /** Between the covers. */
    const val GAP = 16
    /** From the grid's edge to the words, left and bottom alike. */
    const val TEXT_INSET = 40

    /** A cover's square on the picture. */
    data class Tile(val left: Int, val top: Int, val size: Int) {
        val right get() = left + size
        val bottom get() = top + size
    }

    /** Where the covers go: four in a grid when there are at least four, else the first one big, none without covers. */
    fun tiles(covers: Int, size: Int = SIZE, margin: Int = MARGIN, gap: Int = GAP): List<Tile> = when {
        covers >= 4 -> {
            val s = (size - 2 * margin - gap) / 2
            listOf(Tile(margin, margin, s), Tile(margin + s + gap, margin, s), Tile(margin, margin + s + gap, s), Tile(margin + s + gap, margin + s + gap, s))
        }
        covers >= 1 -> listOf(Tile(margin, margin, size - 2 * margin))
        else -> emptyList()
    }

    /** Distinct album covers of the songs, best first, at most four. */
    fun coverIds(songs: List<Song>): List<String> =
        songs.mapNotNull { s -> s.albumId?.let { if (it.startsWith("al-")) it else "al-$it" } ?: s.cover }.distinct().take(4)
}
