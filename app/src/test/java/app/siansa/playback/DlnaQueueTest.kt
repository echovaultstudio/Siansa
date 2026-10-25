package app.siansa.playback

import androidx.media3.common.Player
import app.siansa.playback.dlna.DlnaQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Songs on a DLNA TV or speaker follow each other without a gap when the device allows it (beta testers #23). */
class DlnaQueueTest {
    @Test fun nextSongFollowsTheQueueAndRepeat() {
        assertEquals(1, DlnaQueue.nextIndex(0, 3, Player.REPEAT_MODE_OFF))
        assertEquals(-1, DlnaQueue.nextIndex(2, 3, Player.REPEAT_MODE_OFF))
        assertEquals(0, DlnaQueue.nextIndex(2, 3, Player.REPEAT_MODE_ALL))
        assertEquals(2, DlnaQueue.nextIndex(2, 3, Player.REPEAT_MODE_ONE))
        assertEquals(-1, DlnaQueue.nextIndex(0, 0, Player.REPEAT_MODE_ALL))
    }

    private val navidrome = "http://192.168.1.106:30043/rest/stream?u=max&t=abc&s=salt&v=1.16.1&c=siansa&f=json&id=5kEZ&format=mp3&maxBitRate=320&estimateContentLength=true"

    @Test fun theSameSongIsRecognisedHowEverTheDeviceWritesIt() {
        assertTrue(DlnaQueue.sameStream(navidrome, navidrome))
        // XML-escaped, parts in another order, another login.
        val back = "http://192.168.1.106:30043/rest/stream?id=5kEZ&amp;format=mp3&amp;maxBitRate=320&amp;estimateContentLength=true&amp;u=max&amp;t=zzz&amp;s=other&amp;v=1.16.1&amp;c=siansa&amp;f=json"
        assertTrue(DlnaQueue.sameStream(navidrome, back))
        // Percent-encoded id.
        assertTrue(DlnaQueue.sameStream("http://h:1/rest/stream?id=a%3Ab&format=mp3", "http://h:1/rest/stream?format=mp3&id=a:b"))
    }

    @Test fun anotherSongIsNotTheSame() {
        assertFalse(DlnaQueue.sameStream(navidrome, navidrome.replace("id=5kEZ", "id=6pRj")))
        assertFalse(DlnaQueue.sameStream(navidrome, null))
        assertFalse(DlnaQueue.sameStream("", navidrome))
        assertFalse(DlnaQueue.sameStream("NOT_IMPLEMENTED", navidrome))
        // Emby: the song is in the path.
        assertFalse(DlnaQueue.sameStream("http://e:8096/Audio/1/stream?Static=true&api_key=k", "http://e:8096/Audio/2/stream?Static=true&api_key=k"))
        assertTrue(DlnaQueue.sameStream("http://e:8096/Audio/1/stream?Static=true&api_key=k", "http://e:8096/Audio/1/stream?api_key=j&Static=true"))
    }

    @Test fun onlyAnOutrightRefusalTurnsTheNextSongOff() {
        assertTrue(DlnaQueue.nextRefused("SetNextAVTransportURI refused: 401 Invalid Action"))
        assertTrue(DlnaQueue.nextRefused("SetNextAVTransportURI refused: 602 Optional Action Not Implemented"))
        assertTrue(DlnaQueue.nextRefused("SetNextAVTransportURI refused: HTTP 500"))
        assertFalse(DlnaQueue.nextRefused("timeout"))
        assertFalse(DlnaQueue.nextRefused("SetNextAVTransportURI refused: 701 Transition not allowed"))
        assertFalse(DlnaQueue.nextRefused(null))
    }

    @Test fun theDeviceIsAskedMoreOftenNearASongsEnd() {
        assertEquals(DlnaQueue.POLL_MS, DlnaQueue.pollDelayMs(true, 10_000, 200_000))
        assertEquals(DlnaQueue.NEAR_END_POLL_MS, DlnaQueue.pollDelayMs(true, 197_000, 200_000))
        assertEquals(DlnaQueue.POLL_MS, DlnaQueue.pollDelayMs(false, 197_000, 200_000))
        assertEquals(DlnaQueue.POLL_MS, DlnaQueue.pollDelayMs(true, 197_000, -1))
    }
}
