package app.siansa.mix

import app.siansa.data.Song
import app.siansa.net.ClapHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** AudioMuse's songs found on the server by title and artist when the phone's library doesn't have them (beta testers #40). */
class ServerMatchTest {
    private val hit = ClapHit("navidrome-id", "Ignorance (acoustic version)", "Paramore", null, 0.6)

    @Test fun theSongByTheSameArtistIsPicked() {
        val results = listOf(
            Song("e1", "Ignorance", "Paramore"),
            Song("e2", "Ignorance (Acoustic Version)", "Some Cover Band"),
            Song("e3", "Ignorance (acoustic version)", "Paramore"),
        )
        assertEquals("e3", serverMatch(hit, results)?.id)
    }

    @Test fun anArtistCreditedWithOthersStillMatches() {
        // Emby joins a song's artists with ", ".
        val results = listOf(Song("e4", "Ignorance (acoustic version)", "Paramore, Hayley Williams"))
        assertEquals("e4", serverMatch(hit, results)?.id)
    }

    @Test fun anotherSongOrArtistIsNoMatch() {
        assertNull(serverMatch(hit, listOf(Song("e5", "Ignorance", "Paramore"))))
        assertNull(serverMatch(hit, listOf(Song("e6", "Ignorance (acoustic version)", "Someone Else"))))
        assertNull(serverMatch(hit, emptyList()))
    }
}
