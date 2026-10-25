package app.siansa.playback

import app.siansa.playback.dlna.DlnaFormats
import app.siansa.playback.dlna.Upnp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Original files (FLAC…) to DLNA TVs and speakers that say they play them (beta testers #23). */
class DlnaFormatsTest {
    private val sink = "http-get:*:audio/mpeg:DLNA.ORG_PN=MP3;DLNA.ORG_OP=01,http-get:*:audio/x-flac:*," +
        " http-get:*:audio/wav;rate=44100:*,rtsp-rtp-udp:*:audio/L16:*,http-get:*:image/jpeg:*"

    @Test fun readsWhatTheDeviceSaysItPlays() {
        assertEquals(setOf("audio/mpeg", "audio/x-flac", "audio/wav", "image/jpeg"), Upnp.sinkFormats(sink))
        assertTrue(Upnp.sinkFormats(null).isEmpty())
        assertTrue(Upnp.sinkFormats("garbage").isEmpty())
    }

    @Test fun theSameFormatUnderAnotherName() {
        val formats = Upnp.sinkFormats(sink)
        assertTrue(DlnaFormats.accepts(formats, "audio/flac"))
        assertTrue(DlnaFormats.accepts(formats, "audio/x-wav"))
        assertTrue(DlnaFormats.accepts(formats, "audio/mp3"))
        assertFalse(DlnaFormats.accepts(formats, "audio/mp4"))
        assertFalse(DlnaFormats.accepts(formats, null))
        // A device that didn't say gets MP3 320.
        assertFalse(DlnaFormats.accepts(null, "audio/flac"))
        assertTrue(DlnaFormats.accepts(setOf("audio/*"), "audio/x-ape"))
    }

    @Test fun theFileTypeComesFromTheSongOrItsDetails() {
        assertEquals("audio/flac", DlnaFormats.mimeOf("flac"))
        // As Song details names it.
        assertEquals("audio/flac", DlnaFormats.mimeOf("FLAC"))
        assertEquals("audio/mp4", DlnaFormats.mimeOf("ALAC"))
        assertEquals("audio/mp4", DlnaFormats.mimeOf(".m4a"))
        assertEquals("audio/x-flac", DlnaFormats.mimeOf("audio/x-flac"))
        assertNull(DlnaFormats.mimeOf("WavPack"))
        assertNull(DlnaFormats.mimeOf(null))
    }

    @Test fun contentTypesFromTheServerAreReadPlainly() {
        assertEquals("audio/flac", DlnaFormats.plain("Audio/FLAC; charset=binary"))
        assertNull(DlnaFormats.plain("flac"))
        assertNull(DlnaFormats.plain(null))
    }

    @Test fun theSettingIsOnlyOfferedWhenItCanHelp() {
        assertEquals(listOf("FLAC", "WAV", "MP3"), DlnaFormats.names(Upnp.sinkFormats(sink)))
        assertTrue(DlnaFormats.playsMoreThanMp3(Upnp.sinkFormats(sink)))
        assertFalse(DlnaFormats.playsMoreThanMp3(setOf("audio/mpeg")))
        assertFalse(DlnaFormats.playsMoreThanMp3(null))
        assertTrue(DlnaFormats.playsMoreThanMp3(setOf("audio/*")))
    }

    @Test fun theDescriptionNamesWhereToAskForFormats() {
        val xml = """
            <root><device><friendlyName>Bar</friendlyName><UDN>uuid:9</UDN><serviceList>
              <service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>/avt</controlURL></service>
              <service><serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType><controlURL>/cm</controlURL></service>
            </serviceList></device></root>
        """.trimIndent()
        val device = Upnp.parseDescription(xml, "http://10.0.0.5:8080/d.xml")!!
        assertEquals("http://10.0.0.5:8080/cm", device.connectionUrl)
        assertNull(device.formats)
    }
}
