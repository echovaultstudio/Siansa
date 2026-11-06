package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What "Remove songs up to here" on a queue row takes out of the queue. */
class QueueRemoveTest {
    @Test fun everythingBeforeTheRow() {
        // Row 4 of 10 (index 3): the three songs before it go, and it becomes the first.
        assertEquals(0..2, queueRemoveUpTo(3, 10))
    }

    @Test fun theLastRowKeepsOnlyItself() {
        assertEquals(0..8, queueRemoveUpTo(9, 10))
    }

    @Test fun nothingBeforeTheFirstRow() {
        assertTrue(queueRemoveUpTo(0, 10).isEmpty())
    }

    @Test fun rowOutsideTheQueue() {
        assertTrue(queueRemoveUpTo(10, 10).isEmpty())
        assertTrue(queueRemoveUpTo(-1, 10).isEmpty())
        assertTrue(queueRemoveUpTo(2, 0).isEmpty())
    }

    // Swipe right in the queue (issue #915): the song plays straight after the one playing.
    @Test fun aLaterSongMovesToRightAfterTheOnePlaying() {
        assertEquals(3, queuePlayNextIndex(index = 7, current = 2, count = 10))
    }

    @Test fun anEarlierSongLandsWhereThePlayingSongWas() {
        // Taken out from before it, the song playing moves up one: at 4 it's straight after it.
        assertEquals(4, queuePlayNextIndex(index = 1, current = 4, count = 10))
    }

    @Test fun nothingMovesForThePlayingOrNextSongOrOutsideTheQueue() {
        assertNull(queuePlayNextIndex(index = 2, current = 2, count = 10))
        assertNull(queuePlayNextIndex(index = 3, current = 2, count = 10))
        assertNull(queuePlayNextIndex(index = 10, current = 2, count = 10))
        assertNull(queuePlayNextIndex(index = -1, current = 2, count = 10))
        assertNull(queuePlayNextIndex(index = 1, current = -1, count = 10))
    }
}
