package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A held row near the edge of Arrange Home's list scrolls it (issue #701). */
class AutoScrollTest {
    private val edge = 100f

    @Test fun inTheMiddleNothingScrolls() {
        assertEquals(0f, autoScrollSpeed(top = 300f, bottom = 400f, viewportStart = 0f, viewportEnd = 1000f, edge = edge), 0f)
    }

    @Test fun nearTheBottomItScrollsDownFasterTheDeeper() {
        val shallow = autoScrollSpeed(top = 820f, bottom = 920f, viewportStart = 0f, viewportEnd = 1000f, edge = edge)
        val deep = autoScrollSpeed(top = 880f, bottom = 980f, viewportStart = 0f, viewportEnd = 1000f, edge = edge)
        assertTrue(shallow > 0f && deep > shallow)
        assertEquals(AUTO_SCROLL_MAX, autoScrollSpeed(top = 950f, bottom = 1100f, viewportStart = 0f, viewportEnd = 1000f, edge = edge), 0.001f)
    }

    @Test fun nearTheTopItScrollsUp() {
        assertTrue(autoScrollSpeed(top = 20f, bottom = 120f, viewportStart = 0f, viewportEnd = 1000f, edge = edge) < 0f)
    }

    /** Dragged too far up or down, a held row stays in the list's box (the owner, 27 Nov: it vanished past the top). */
    @Test fun aHeldRowStaysInTheBox() {
        // A row laid out at 100, 50 tall, in a box from 0 to 300.
        assertEquals(-100f, clampDrag(-400f, itemOffset = 100, itemSize = 50, viewStart = 0, viewEnd = 300), 0f)
        assertEquals(150f, clampDrag(900f, itemOffset = 100, itemSize = 50, viewStart = 0, viewEnd = 300), 0f)
        assertEquals(-30f, clampDrag(-30f, itemOffset = 100, itemSize = 50, viewStart = 0, viewEnd = 300), 0f)
        // A box smaller than the row: as it is.
        assertEquals(-30f, clampDrag(-30f, itemOffset = 0, itemSize = 80, viewStart = 0, viewEnd = 40), 0f)
    }

    /**
     * Rows of different heights (the owner, 27 Nov: a short row glitched on one spot over a taller one): it swaps only
     * past the other's middle, and after the swap it's not past it again the other way.
     */
    @Test fun rowsOfDifferentHeightsSwapOnce() {
        // A 52 tall row at 0 going down over a 70 tall one at 52: not before its foot passes 87.
        assertTrue(!passesMiddle(top = 30f, size = 52, targetOffset = 52, targetSize = 70, down = true))
        assertTrue(passesMiddle(top = 40f, size = 52, targetOffset = 52, targetSize = 70, down = true))
        // Swapped: the tall one at 0, the held one laid out under it at 70; still under the finger at 40.
        val laidOut = offsetAfterSwap(size = 52, targetOffset = 52, targetSize = 70, down = true)
        assertEquals(70, laidOut)
        // Going back up needs its top above the tall one's middle (35): at 40 it doesn't swap back.
        assertTrue(!passesMiddle(top = 40f, size = 52, targetOffset = 0, targetSize = 70, down = false))
        assertTrue(passesMiddle(top = 30f, size = 52, targetOffset = 0, targetSize = 70, down = false))
        // Going up, it's laid out where the other row began.
        assertEquals(0, offsetAfterSwap(size = 52, targetOffset = 0, targetSize = 70, down = false))
    }
}
