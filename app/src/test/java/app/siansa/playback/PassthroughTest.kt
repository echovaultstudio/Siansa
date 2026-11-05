package app.siansa.playback

import androidx.media3.common.MimeTypes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Audio passthrough on the TV app (issue #860). */
class PassthroughTest {
    @After
    fun forget() = Passthrough.played.clear()

    @Test
    fun dolbyAndDtsAreBitstreams() {
        for (mime in listOf(
            MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_TRUEHD,
            MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD,
        )) {
            assertTrue(mime, Passthrough.isBitstream(mime))
        }
    }

    @Test
    fun everythingElsePlaysAsUsual() {
        for (mime in listOf(MimeTypes.AUDIO_FLAC, MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_OPUS, MimeTypes.AUDIO_ALAC, null)) {
            assertFalse(mime.toString(), Passthrough.isBitstream(mime))
        }
    }

    @Test
    fun dolbyFilesStreamUntouchedOnlyWhilePassthroughIsOn() {
        assertTrue(Passthrough.keepsOriginal(PassthroughMode.DOLBY_DTS, "eac3"))
        assertTrue(Passthrough.keepsOriginal(PassthroughMode.DOLBY_DTS, ".MKA"))
        assertTrue(Passthrough.keepsOriginal(PassthroughMode.DOLBY_DTS, " thd "))
        assertFalse(Passthrough.keepsOriginal(PassthroughMode.OFF, "eac3"))
        assertFalse(Passthrough.keepsOriginal(PassthroughMode.DOLBY_DTS, "flac"))
        // Dolby Atmos music is mostly sold as .m4a or .mp4: those stream untouched too, but only while passthrough is on.
        assertTrue(Passthrough.keepsOriginal(PassthroughMode.DOLBY_DTS, "m4a"))
        assertTrue(Passthrough.keepsOriginal(PassthroughMode.DOLBY_DTS, "MP4"))
        assertFalse(Passthrough.keepsOriginal(PassthroughMode.OFF, "m4a"))
        // An .m4a isn't taken for Dolby when deciding about blends until it went to the receiver.
        assertFalse(Passthrough.likely(PassthroughMode.DOLBY_DTS, "m4a"))
        assertFalse(Passthrough.keepsOriginal(PassthroughMode.DOLBY_DTS, null))
    }

    @Test
    fun anM4aIsKnownOnceItWentToTheReceiver() {
        assertFalse(Passthrough.involves(PassthroughMode.DOLBY_DTS, "song-1", "m4a"))
        Passthrough.played.add("song-1")
        assertTrue(Passthrough.involves(PassthroughMode.DOLBY_DTS, "song-1", "m4a"))
        // Off means nothing is a bitstream, whatever played before.
        assertFalse(Passthrough.involves(PassthroughMode.OFF, "song-1", "m4a"))
        assertFalse(Passthrough.involves(PassthroughMode.DOLBY_DTS, null, "eac3"))
    }

    @Test
    fun settingReadsBackAndDefaultsToOff() {
        assertEquals(PassthroughMode.DOLBY_DTS, PassthroughMode.parse("DOLBY_DTS"))
        assertEquals(PassthroughMode.OFF, PassthroughMode.parse(null))
        assertEquals(PassthroughMode.OFF, PassthroughMode.parse("SOMETHING_ELSE"))
    }

    @Test
    fun formatsHavePlainNames() {
        assertEquals("Dolby Atmos (E-AC-3 JOC)", Passthrough.name(MimeTypes.AUDIO_E_AC3_JOC))
        assertEquals("Dolby TrueHD", Passthrough.name(MimeTypes.AUDIO_TRUEHD))
    }
}
