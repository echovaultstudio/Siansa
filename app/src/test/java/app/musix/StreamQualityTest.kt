package app.musix

import app.musix.playback.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The format shown under the seek bar in now playing, and the qualities you can pick there. */
class StreamQualityTest {
    @Test fun namesCommonFormats() {
        assertEquals("FLAC", StreamQuality.codecName("audio/flac"))
        assertEquals("ALAC", StreamQuality.codecName("audio/alac"))
        assertEquals("MP3", StreamQuality.codecName("audio/mpeg"))
        assertEquals("OPUS", StreamQuality.codecName("audio/opus"))
        assertEquals("AAC", StreamQuality.codecName("audio/mp4a-latm"))
        assertEquals("VORBIS", StreamQuality.codecName("audio/vorbis"))
    }

    @Test fun wavIsToldApartFromOtherPlainAudio() {
        assertEquals("WAV", StreamQuality.codecName("audio/raw", "audio/wav"))
        assertEquals("PCM", StreamQuality.codecName("audio/raw", null))
    }

    @Test fun unknownAudioFormatsUseTheirOwnName() {
        assertEquals("APE", StreamQuality.codecName("audio/x-ape"))
        assertEquals("WAVPACK", StreamQuality.codecName("Audio/WavPack"))
    }

    @Test fun nothingWhenNotKnownYet() {
        assertNull(StreamQuality.codecName(null))
        assertNull(StreamQuality.codecName(""))
        assertNull(StreamQuality.codecName("video/avc"))
    }

    @Test fun convertedSongsShowOpusAndTheBitrate() {
        assertEquals("OPUS 160", StreamQuality.label("FLAC", 160))
        assertEquals("OPUS 96", StreamQuality.label(null, 96))
    }

    @Test fun originalFilesShowTheirFormat() {
        assertEquals("FLAC", StreamQuality.label("FLAC", 0))
        assertNull(StreamQuality.label(null, 0))
    }

    @Test fun choicesMatchSettings() {
        assertEquals(listOf(0, 96, 128, 160, 192, 256, 320), StreamQuality.choices)
        assertEquals("Original", StreamQuality.choiceLabel(0))
        assertEquals("Opus 256", StreamQuality.choiceLabel(256))
    }
}
