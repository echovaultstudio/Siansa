package app.musix.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/** Where "Add to queue" (and swiping a song to the left) puts a song, Spotify style. */
class QueueInsertTest {
    private fun at(current: Int, queued: Set<Int>, count: Int) = queueInsertIndex(current, count) { it in queued }

    @Test fun rightAfterTheSongPlaying() {
        // Playing song 3 of an album of 10: the queued song comes next, before the rest of the album.
        assertEquals(3, at(current = 2, queued = emptySet(), count = 10))
    }

    @Test fun afterSongsQueuedBefore() {
        // Two songs queued already (positions 3 and 4): the third goes after them, in the order added.
        assertEquals(5, at(current = 2, queued = setOf(3, 4), count = 10))
    }

    @Test fun queuedSongsAlreadyPlayedDontCount() {
        assertEquals(6, at(current = 5, queued = setOf(1, 2), count = 10))
    }

    @Test fun atTheEndOfTheQueue() {
        assertEquals(10, at(current = 9, queued = emptySet(), count = 10))
        assertEquals(10, at(current = 7, queued = setOf(8, 9), count = 10))
    }

    @Test fun emptyQueue() {
        assertEquals(0, at(current = 0, queued = emptySet(), count = 0))
    }
}
