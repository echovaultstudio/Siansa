package app.siansa.playback

import android.app.Application
import android.net.Uri
import app.siansa.data.AppSettings
import app.siansa.data.Song
import app.siansa.net.SubsonicApi
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Songs converted for a Cast TV go as Opus 320 in Ogg (issue #114: Navidrome's Opus plays on the owner's SHIELD), with
 * MP3 320 after it for TVs that can't play Opus; MP3 320 quality sends only MP3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CastOpusTest {
    private val api = SubsonicApi(OkHttpClient()) { AppSettings(navidromeUrl = "http://nd.example:4533", navidromeUser = "me", navidromePassword = "pw") }
    private val song = Song(id = "s1", title = "Song", albumId = "a1", suffix = "wma", durationSec = 200)

    @After fun back() = CastSettings.setQuality(CastQuality.Lossless)

    @Test fun opusIsNavidromesOpusAt320WithoutAnEstimatedLength() {
        val opus = Streaming.forCastOpus(Uri.parse(api.streamUrl("s1")))
        assertEquals("opus", opus.getQueryParameter("format"))
        assertEquals("320", opus.getQueryParameter("maxBitRate"))
        assertEquals(null, opus.getQueryParameter("estimateContentLength"))
        assertEquals("s1", opus.getQueryParameter("id"))
    }

    @Test fun losslessAndOpusSendOpusWithMp3AfterIt() {
        for (q in listOf(CastQuality.Lossless, CastQuality.Opus)) {
            CastSettings.setQuality(q)
            val info = CastItems(api).toMediaQueueItem(song.toMediaItem(api)).media!!
            assertTrue(q.name, info.contentUrl!!.contains("format=opus"))
            assertEquals("audio/ogg", info.contentType)
            val custom = info.customData!!
            assertTrue(custom.getString("opusUrl").contains("format=opus"))
            assertTrue(custom.getString("mp3Url").contains("format=mp3"))
        }
    }

    @Test fun mp3QualitySendsOnlyMp3() {
        CastSettings.setQuality(CastQuality.Mp3)
        val info = CastItems(api).toMediaQueueItem(song.toMediaItem(api)).media!!
        assertTrue(info.contentUrl!!.contains("format=mp3"))
        assertEquals("audio/mpeg", info.contentType)
        assertFalse(info.customData!!.has("opusUrl"))
        assertFalse(info.customData!!.has("originalUrl"))
    }

    @Test fun theSettingKnowsOpus() {
        assertEquals(CastQuality.Opus, CastQuality.of("opus"))
        assertEquals(CastQuality.Lossless, CastQuality.of(null))
    }
}
