package app.musix

import app.musix.data.AppSettings
import app.musix.data.Song
import app.musix.net.ConnectionState
import app.musix.net.offlinePick
import app.musix.net.retryDelayMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline: what can play, and when the AI tools say they're not available. */
class OfflineTest {
    private fun songs(vararg ids: String) = ids.map { Song(id = it, title = it) }
    private val downloaded = setOf("b", "d")

    @Test fun onlyDownloadedSongsPlayFromTheChosenOne() {
        val pick = offlinePick(songs("a", "b", "c", "d"), 1, downloaded::contains)
        assertEquals(listOf("b", "d"), pick.songs.map { it.id })
        assertEquals(0, pick.start)
        assertFalse(pick.skipped)
    }

    @Test fun aSongNotDownloadedStartsAtTheNextDownloadedOne() {
        val pick = offlinePick(songs("a", "b", "c", "d"), 2, downloaded::contains)
        assertEquals("d", pick.songs[pick.start].id)
        assertTrue(pick.skipped)
    }

    @Test fun afterTheLastDownloadedSongItWrapsRound() {
        val pick = offlinePick(songs("a", "b", "c", "d", "e"), 4, downloaded::contains)
        assertEquals("b", pick.songs[pick.start].id)
    }

    @Test fun nothingDownloadedPlaysNothing() {
        assertTrue(offlinePick(songs("a", "c"), 0, downloaded::contains).songs.isEmpty())
    }

    private val withAudioMuse = AppSettings(audioMuseUrl = "http://am")

    @Test fun offlineMeansNoNetworkOrServerDown() {
        assertTrue(ConnectionState(network = false).offline)
        assertTrue(ConnectionState(network = true, server = false).offline)
        assertFalse(ConnectionState(network = true, server = null).offline)
        assertFalse(ConnectionState(network = true, server = true).offline)
    }

    @Test fun aiToolsSayWhyTheyCantRun() {
        assertEquals("You're offline.", ConnectionState(network = false).aiProblem(withAudioMuse))
        assertEquals("AudioMuse isn't answering.", ConnectionState(network = true, audioMuse = false).aiProblem(withAudioMuse))
        assertNull(ConnectionState(network = true, audioMuse = true).aiProblem(withAudioMuse))
        assertNull(ConnectionState(network = true, audioMuse = null).aiProblem(withAudioMuse))
        assertTrue(ConnectionState().aiProblem(AppSettings())!!.contains("isn't set up"))
    }

    @Test fun looksAgainSoonAfterTheConnectionIsLostThenEveryFewMinutes() {
        assertEquals(listOf(5_000L, 15_000L, 30_000L, 180_000L, 180_000L), (0..4).map { retryDelayMs(it) })
    }
}
