package app.musix.data

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Playlists' songs kept on the phone (issue #251). */
class PlaylistCacheTest {
    private val songs = listOf(Song("s1", "One", durationSec = 100), Song("s2", "Two", durationSec = 200))
    private val kept = CachedPlaylist("p1", "Road trip", changed = "2026-10-20T07:00:00Z", owner = "max", songs = songs)

    @Test fun unchangedPlaylistIsTakenFromThePhone() {
        assertTrue(isFresh(kept, Playlist("p1", "Road trip", songCount = 2, changed = "2026-10-20T07:00:00Z")))
    }

    @Test fun changedOnNavidromeIsAskedForAgain() {
        assertFalse(isFresh(kept, Playlist("p1", "Road trip", songCount = 3, changed = "2026-10-20T08:00:00Z")))
        // Same date but another number of songs: asked for again to be safe.
        assertFalse(isFresh(kept, Playlist("p1", "Road trip", songCount = 5, changed = "2026-10-20T07:00:00Z")))
        // Not in the synced list, or no date to compare: asked for again.
        assertFalse(isFresh(kept, null))
        assertFalse(isFresh(kept.copy(changed = null), Playlist("p1", "Road trip", songCount = 2)))
    }

    @Test fun keptCopyBecomesAPlaylistPage() {
        val d = kept.toDetail()
        assertEquals("Road trip", d.playlist.name)
        assertEquals(2, d.playlist.songCount)
        assertEquals(300, d.playlist.durationSec)
        assertEquals(listOf("s1", "s2"), d.songs.map { it.id })
        assertEquals(kept, CachedPlaylist.of(d))
    }

    @Test fun keptCopiesSurviveBeingSavedAndRead() {
        val serializer = MapSerializer(String.serializer(), CachedPlaylist.serializer())
        val text = AppJson.encodeToString(serializer, mapOf("p1" to kept))
        assertEquals(mapOf("p1" to kept), AppJson.decodeFromString(serializer, text))
    }
}
