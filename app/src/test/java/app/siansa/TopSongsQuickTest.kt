package app.siansa

import app.siansa.data.Song
import app.siansa.data.TOP_SONGS_FRESH_MS
import app.siansa.data.TopSongsEntry
import app.siansa.data.quickTopSongs
import app.siansa.data.shouldFetchTopSongsAhead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopSongsQuickTest {
    private val own = (1..14).map { Song("s$it", "Song $it") }

    @Test fun mostPlayedOnThisPhoneComeFirst() {
        val quick = quickTopSongs(own, mapOf("s9" to 12, "s3" to 4, "s12" to 4))
        // Most plays first; equal plays keep the library's order.
        assertEquals(listOf("s9", "s3", "s12", "s1", "s2"), quick.take(5).map { it.id })
        assertEquals(10, quick.size)
    }

    @Test fun neverPlayedKeepsTheLibraryOrder() {
        assertEquals(own.take(10), quickTopSongs(own, emptyMap()))
        assertEquals(own.take(3), quickTopSongs(own.take(3), emptyMap()))
    }

    @Test fun aSongIsListedOnce() {
        assertEquals(listOf("a", "b"), quickTopSongs(listOf(Song("a", "A"), Song("a", "A"), Song("b", "B")), emptyMap()).map { it.id })
    }

    @Test fun fetchedAheadOnlyWhenNoneOrOld() {
        val now = 1_800_000_000_000L
        assertTrue(shouldFetchTopSongsAhead(null, now))
        assertFalse(shouldFetchTopSongsAhead(TopSongsEntry("a", now - 1000, own), now))
        assertTrue(shouldFetchTopSongsAhead(TopSongsEntry("a", now - TOP_SONGS_FRESH_MS - 1, own), now))
        // The artist's own songs saved in place of a failed request are asked for again.
        assertTrue(shouldFetchTopSongsAhead(TopSongsEntry("a", 0L, own, fromServer = false), now))
    }
}
