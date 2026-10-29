package app.siansa.ui

import app.siansa.data.Song
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A page's Play button knows its songs are the ones playing (issue #644). */
class ListPlaybackTest {
    private fun songs(vararg ids: String) = ids.map { Song(id = it, title = it) }

    @Test fun theSameSongsInTheQueueAreLoaded() {
        assertTrue(listLoaded(songs("a", "b", "c"), songs("a", "b", "c")))
    }

    @Test fun shuffledOrWithSongsAddedAfterStillCounts() {
        assertTrue(listLoaded(songs("c", "a", "b"), songs("a", "b", "c")))
        assertTrue(listLoaded(songs("a", "b", "c", "x", "y"), songs("a", "b", "c")))
    }

    @Test fun otherSongsAreNotLoaded() {
        assertFalse(listLoaded(songs("a", "b"), songs("a", "b", "c")))
        assertFalse(listLoaded(songs("a", "b", "x"), songs("a", "b", "c")))
        assertFalse(listLoaded(songs("x", "a", "b", "c"), songs("a", "b", "c")))
        assertFalse(listLoaded(songs("a"), emptyList()))
        assertFalse(listLoaded(emptyList(), songs("a")))
    }

    @Test fun idleStartsTheList() {
        var started = false
        ListPlayback.IDLE.press { started = true }
        assertTrue(started)
        assertFalse(ListPlayback.IDLE.playing)
    }
}
