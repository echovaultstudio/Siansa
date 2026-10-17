package app.musix

import app.musix.mix.mayFetchUnsynced
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Songs found by AudioMuse that aren't in the synced library are only fetched when no library was left out. */
class UnsyncedLibraryTest {
    @Test fun fetchesBeforeTheFirstSync() {
        assertTrue(mayFetchUnsynced(null))
    }

    @Test fun fetchesWhenEveryLibraryIsSynced() {
        assertTrue(mayFetchUnsynced("*"))
    }

    @Test fun leavesOutSongsWhenSomeLibrariesAreUnchecked() {
        assertFalse(mayFetchUnsynced("1"))
        assertFalse(mayFetchUnsynced("1,3"))
    }
}
