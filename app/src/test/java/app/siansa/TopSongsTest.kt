package app.siansa

import app.siansa.data.AppJson
import app.siansa.data.Song
import app.siansa.data.TOP_SONGS_FRESH_MS
import app.siansa.data.TopSongsEntry
import app.siansa.data.isTopSongsFresh
import app.siansa.data.nextTopSongs
import app.siansa.data.preferLocal
import app.siansa.data.putTopSongs
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Popular songs kept on the phone: when they're fresh, matching to the library and what gets saved. */
class TopSongsTest {
    private fun song(id: String, title: String = "T$id", starred: Boolean = false) = Song(id = id, title = title, starred = starred)
    private fun entry(artistId: String, savedAt: Long, vararg ids: String, fromServer: Boolean = true) =
        TopSongsEntry(artistId, savedAt, ids.map { song(it) }, fromServer)

    @Test fun freshForThreeDays() {
        val now = 10 * TOP_SONGS_FRESH_MS
        assertTrue(isTopSongsFresh(entry("a", now), now))
        assertTrue(isTopSongsFresh(entry("a", now - TOP_SONGS_FRESH_MS + 1), now))
        assertFalse(isTopSongsFresh(entry("a", now - TOP_SONGS_FRESH_MS), now))
        assertFalse(isTopSongsFresh(entry("a", 0L), now))
    }

    @Test fun savedInTheFutureIsNotFresh() {
        // The phone's clock was moved back: ask again rather than trust the list forever.
        assertFalse(isTopSongsFresh(entry("a", 2_000L), 1_000L))
    }

    @Test fun prefersTheLibraryCopyAndKeepsOrder() {
        val remote = listOf(song("1", "Remote one"), song("2"), song("3"))
        val local = listOf(song("3", starred = true), song("1", "Local one"))
        val result = preferLocal(remote, local)
        assertEquals(listOf("1", "2", "3"), result.map { it.id })
        assertEquals("Local one", result[0].title)
        assertEquals("T2", result[1].title)
        assertTrue(result[2].starred)
    }

    @Test fun newServerAnswerReplacesTheSavedOne() {
        val next = nextTopSongs(entry("a", 1L, "old"), listOf(song("new")), "a", 100L) { error("no fallback") }
        assertEquals(listOf("new"), next!!.songs.map { it.id })
        assertEquals(100L, next.savedAt)
        assertTrue(next.fromServer)
    }

    @Test fun failedRequestKeepsTheSavedList() {
        assertNull(nextTopSongs(entry("a", 1L, "old"), null, "a", 100L) { error("no fallback") })
    }

    @Test fun failedRequestWithNothingSavedUsesOwnSongsAndTriesAgainNextTime() {
        val next = nextTopSongs(null, null, "a", 100L) { listOf(song("own")) }!!
        assertEquals(listOf("own"), next.songs.map { it.id })
        assertFalse(next.fromServer)
        assertFalse(isTopSongsFresh(next, 100L))
    }

    @Test fun emptyAnswerKeepsTheSavedListAndWaitsAgain() {
        val next = nextTopSongs(entry("a", 1L, "old"), emptyList(), "a", 100L) { error("no fallback") }!!
        assertEquals(listOf("old"), next.songs.map { it.id })
        assertEquals(100L, next.savedAt)
    }

    @Test fun emptyAnswerWithNothingSavedUsesOwnSongs() {
        val next = nextTopSongs(null, emptyList(), "a", 100L) { listOf(song("own")) }!!
        assertEquals(listOf("own"), next.songs.map { it.id })
        assertFalse(next.fromServer)
        assertTrue(isTopSongsFresh(next, 100L))
    }

    @Test fun putsNewestFirstReplacesAndTrims() {
        val list = listOf(entry("a", 1L, "1"), entry("b", 2L, "2"), entry("c", 3L, "3"))
        val result = putTopSongs(list, entry("b", 9L, "9"), max = 2)
        assertEquals(listOf("b", "a"), result.map { it.artistId })
        assertEquals(9L, result[0].savedAt)
    }

    @Test fun savesAndReadsBack() {
        val list = listOf(entry("a", 5L, "1", "2", fromServer = false))
        val serializer = ListSerializer(TopSongsEntry.serializer())
        assertEquals(list, AppJson.decodeFromString(serializer, AppJson.encodeToString(serializer, list)))
    }
}
