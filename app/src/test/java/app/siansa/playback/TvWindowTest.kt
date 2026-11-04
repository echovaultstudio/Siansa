package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The few songs of the phone's queue a Chromecast holds (issue #114): the phone keeps the queue; the TV gets the one
 * playing, one before and four after, kept in step without touching the one playing.
 */
class TvWindowTest {
    @Test fun theSongPlayingOneBeforeAndFourAfter() {
        assertEquals(listOf(9, 10, 11, 12, 13, 14), TvWindow.around(37, 10, wrap = false))
        assertEquals(listOf(0, 1, 2, 3, 4), TvWindow.around(37, 0, wrap = false))
        assertEquals(listOf(35, 36), TvWindow.around(37, 36, wrap = false))
        assertEquals(listOf(0), TvWindow.around(1, 0, wrap = false))
        assertTrue(TvWindow.around(0, 0, wrap = false).isEmpty())
    }

    @Test fun repeatAllWrapsAroundTheQueue() {
        assertEquals(listOf(35, 36, 0, 1, 2, 3), TvWindow.around(37, 36, wrap = true))
        assertEquals(listOf(36, 0, 1, 2, 3, 4), TvWindow.around(37, 0, wrap = true))
        // A queue shorter than the window doesn't repeat songs in it.
        assertEquals(listOf(0, 1, 2), TvWindow.around(3, 1, wrap = true))
    }

    @Test fun nextIsTheQueuesOrTheEnd() {
        assertEquals(5, TvWindow.next(10, 4, wrap = false))
        assertNull(TvWindow.next(10, 9, wrap = false))
        assertEquals(0, TvWindow.next(10, 9, wrap = true))
    }

    @Test fun inStepNothingChanges() {
        val plan = TvWindow.plan(listOf(1L, 2L, 3L, 4L), 1, listOf(1L, 2L, 3L, 4L), 1)
        assertTrue(plan.nothing)
    }

    @Test fun asTheTvMovesOnTheOldestGoAndTheNextComeAtTheEnd() {
        // The TV moved from 2 to 3 by itself: 1 (two before) goes, 7 comes.
        val plan = TvWindow.plan(listOf(1L, 2L, 3L, 4L, 5L, 6L), 2, listOf(2L, 3L, 4L, 5L, 6L, 7L), 1)
        assertEquals(listOf(0), plan.remove)
        assertEquals(listOf(7L), plan.insertAfter)
        assertTrue(plan.insertBefore.isEmpty())
    }

    @Test fun songsQueuedNextReplaceWhatFollowsButNeverTheOnePlaying() {
        // "Play next" put 9 after the current 3: what follows from there is replaced; 3 stays.
        val plan = TvWindow.plan(listOf(2L, 3L, 4L, 5L, 6L, 7L), 1, listOf(2L, 3L, 9L, 4L, 5L, 6L), 1)
        assertEquals(listOf(2, 3, 4, 5), plan.remove)
        assertEquals(listOf(9L, 4L, 5L, 6L), plan.insertAfter)
        assertTrue(plan.insertBefore.isEmpty())
    }

    @Test fun songsOfAnotherQueueOnTheTvAreReplaced() {
        val plan = TvWindow.plan(listOf(null, 3L, null), 1, listOf(2L, 3L, 4L), 1)
        assertEquals(listOf(0, 2), plan.remove)
        assertEquals(listOf(4L), plan.insertAfter)
        assertEquals(listOf(2L), plan.insertBefore)
        assertEquals(1, plan.insertBeforeAt)
    }

    @Test fun aJumpBackOnTheTvGetsTheSongBeforeItInFront() {
        // Previous on the TV's remote: 2 plays now; 1 goes in front of it, the last one after it goes.
        val plan = TvWindow.plan(listOf(2L, 3L, 4L, 5L, 6L, 7L), 0, listOf(1L, 2L, 3L, 4L, 5L, 6L), 1)
        assertEquals(listOf(5), plan.remove)
        assertTrue(plan.insertAfter.isEmpty())
        assertEquals(listOf(1L), plan.insertBefore)
        assertEquals(0, plan.insertBeforeAt)
    }
}
