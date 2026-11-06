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

    /** Issue #901: a TV can't seek in a conversion as it's made; the song is loaded again starting at the seek. */
    @Test fun aSongStartedPartWayInStartsEveryConversionThere() {
        CastSettings.setQuality(CastQuality.Lossless)
        val item = CastItems(api).toMediaQueueItem(song.toMediaItem(api), 7L, 92_400L)
        val info = item.media!!
        val custom = info.customData!!
        assertTrue(custom.getString("opusUrl").contains("timeOffset=92"))
        assertTrue(custom.getString("mp3Url").contains("timeOffset=92"))
        assertTrue(info.contentUrl!!.contains("timeOffset=92"))
        // The original file would start at its beginning: not offered.
        assertFalse(custom.has("originalUrl") && !custom.optBoolean("converted"))
        if (song.durationSec > 0) assertEquals(song.durationSec * 1000L - 92_400L, info.streamDuration)
        // From the start, nothing changes.
        val whole = CastItems(api).toMediaQueueItem(song.toMediaItem(api), 7L, 0L).media!!
        assertFalse(whole.contentUrl!!.contains("timeOffset"))
    }

    @Test fun onlyTheServersConversionsCountAsConversions() {
        assertTrue(TvSeek.isConversion("http://nd/rest/stream?id=s1&format=opus&maxBitRate=320&u=a"))
        assertTrue(TvSeek.isConversion("http://nd/rest/stream?id=s1&format=mp3&maxBitRate=320"))
        assertTrue(TvSeek.isConversion("http://nd/rest/stream?id=s1&format=flac"))
        assertFalse(TvSeek.isConversion("http://nd/rest/stream?id=s1&format=raw"))
        assertFalse(TvSeek.isConversion("http://nd/rest/stream?id=s1"))
        assertFalse(TvSeek.isConversion("http://emby/Audio/52131/stream?Static=true"))
        assertFalse(TvSeek.isConversion(null))
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
