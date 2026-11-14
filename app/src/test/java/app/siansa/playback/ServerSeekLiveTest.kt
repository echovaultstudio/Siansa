package app.siansa.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A converted song isn't a live stream (issue #1039): the player calls it one, as it has no length it can see, and
 * the media session then gave Android Auto, the lock screen and the notification no length and no position.
 */
class ServerSeekLiveTest {
    @Test fun aConvertedSongIsNotLive() {
        assertFalse(ServerSeekPlayer.live(playerSaysLive = true, converted = true))
        assertFalse(ServerSeekPlayer.live(playerSaysLive = false, converted = true))
    }

    @Test fun aRadioStationStaysLive() {
        assertTrue(ServerSeekPlayer.live(playerSaysLive = true, converted = false))
        assertFalse(ServerSeekPlayer.live(playerSaysLive = false, converted = false))
    }
}
