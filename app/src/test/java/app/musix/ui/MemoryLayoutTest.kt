package app.musix.ui

import app.musix.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #186: a memory's picture is well spaced, the same space around the grid and between its covers. */
class MemoryLayoutTest {
    private val size = MemoryLayout.SIZE
    private val margin = MemoryLayout.MARGIN

    @Test fun fourCoversInAnEvenGrid() {
        val t = MemoryLayout.tiles(4)
        assertEquals(4, t.size)
        assertTrue("all the same size", t.all { it.size == t[0].size })
        // The same space on every side of the grid.
        assertEquals(margin, t[0].left)
        assertEquals(margin, t[0].top)
        assertTrue("right edge", kotlin.math.abs(size - t[1].right - margin) <= 1)
        assertTrue("bottom edge", kotlin.math.abs(size - t[3].bottom - margin) <= 1)
        // The same gap across and down.
        assertEquals(MemoryLayout.GAP, t[1].left - t[0].right)
        assertEquals(MemoryLayout.GAP, t[2].top - t[0].bottom)
    }

    @Test fun fewerThanFourIsOneBigCover() {
        for (n in 1..3) {
            val t = MemoryLayout.tiles(n).single()
            assertEquals(margin, t.left)
            assertEquals(margin, t.top)
            assertEquals(size - margin, t.right)
            assertEquals(size - margin, t.bottom)
        }
    }

    @Test fun noCoversNoTiles() = assertTrue(MemoryLayout.tiles(0).isEmpty())

    @Test fun oneCoverPerAlbumBestFirst() {
        val songs = listOf(
            Song(id = "1", title = "a", albumId = "x"),
            Song(id = "2", title = "b", albumId = "x"),
            Song(id = "3", title = "c", albumId = "al-y"),
            Song(id = "4", title = "d", coverArt = "mf-4"),
            Song(id = "5", title = "e", albumId = "z"),
            Song(id = "6", title = "f", albumId = "w"),
        )
        assertEquals(listOf("al-x", "al-y", "mf-4", "al-z"), MemoryLayout.coverIds(songs))
    }
}
