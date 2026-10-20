package app.musix.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Swiping the mini player (issue #310). */
class MiniSwipeTest {
    @Test fun aQuarterOfTheBarChangesTheSong() {
        assertEquals(MiniSwipe.NEXT, miniSwipe(-300f, 1000))
        assertEquals(MiniSwipe.PREVIOUS, miniSwipe(300f, 1000))
    }

    @Test fun lessSpringsBack() {
        assertEquals(MiniSwipe.NONE, miniSwipe(-200f, 1000))
        assertEquals(MiniSwipe.NONE, miniSwipe(240f, 1000))
        assertEquals(MiniSwipe.NONE, miniSwipe(0f, 1000))
    }
}
