package app.siansa.servers

import app.siansa.playback.songCacheKey
import app.siansa.playback.songCacheKeysToDrop
import app.siansa.playback.streamSongId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The player finds the song in a stream address on every server (issue #903). On Emby a downloaded album streamed
 * instead of playing from the phone, and stopped with no connection, as the song's id was only read from Navidrome's
 * `?id=`, and Emby's and Jellyfin's addresses have it in the path.
 */
class PlaybackOnEveryServerTest {
    @Test fun theSongIsFoundInEveryServersStreamAddress() {
        EveryServer.all.forEach { server ->
            assertEquals(server.name, server.songId, streamSongId(server.source().streamUrl(server.songId)))
        }
    }

    @Test fun aSongIsCachedUnderTheSameKeyAfterANewLogin() {
        EveryServer.all.forEach { server ->
            val today = server.source(login = 1).streamUrl(server.songId)
            val tomorrow = server.source(login = 2).streamUrl(server.songId)
            if (server.name != "Navidrome") assertNotEquals("${server.name}: the login is in the address", today, tomorrow)
            assertEquals(server.name, songCacheKey(today), songCacheKey(tomorrow))
            assertEquals(server.name, "song:${server.songId}:raw:", songCacheKey(today))
        }
    }

    @Test fun thePlayingSongStaysInTheCacheWhileItIsOff() {
        EveryServer.all.forEach { server ->
            val key = songCacheKey(server.source().streamUrl(server.songId))
            assertEquals(server.name, emptyList<String>(), songCacheKeysToDrop(listOf(key), setOf(server.songId)))
        }
    }

    @Test fun otherAddressesHaveNoSong() {
        assertNull(streamSongId("https://radio.example/live.mp3"))
        assertNull(streamSongId("http://emby.local/Items/52131/Images/Primary?api_key=x"))
        assertNull(streamSongId("not an address"))
        assertFalse(songCacheKey("https://radio.example/live.mp3").startsWith("song:"))
        // Subsonic's opus conversions are kept apart from the original.
        assertEquals("song:s1:opus:128", songCacheKey("http://n/rest/stream?id=s1&format=opus&maxBitRate=128&t=a&s=b"))
    }
}
