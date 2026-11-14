package app.siansa.ui

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

    /** The owner, 11 Nov: one rolled song, swiped left, and the music stopped with the mini player left empty. */
    @Test fun withNoSongAfterASwipeLeftSpringsBack() {
        assertEquals(MiniSwipe.NONE, miniSwipe(-600f, 1000, hasNext = false, hasPrevious = false))
        assertEquals(MiniSwipe.NONE, miniSwipe(-600f, 1000, hasNext = false, hasPrevious = true))
        assertEquals(MiniSwipe.NEXT, miniSwipe(-600f, 1000, hasNext = true, hasPrevious = false))
    }

    @Test fun withNoSongBeforeASwipeRightStartsThisOneAgain() {
        assertEquals(MiniSwipe.RESTART, miniSwipe(600f, 1000, hasNext = false, hasPrevious = false))
        assertEquals(MiniSwipe.PREVIOUS, miniSwipe(600f, 1000, hasNext = false, hasPrevious = true))
        assertEquals(MiniSwipe.NONE, miniSwipe(100f, 1000, hasNext = true, hasPrevious = false))
    }
}
