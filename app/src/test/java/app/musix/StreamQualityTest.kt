package app.musix

import app.musix.playback.StreamQuality
import app.musix.playback.Streaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The format shown under the seek bar in now playing, and reloading a song at a new quality. */
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

    @Test fun reloadMarkerMakesTheAddressDifferAndIsRemovedAgain() {
        val url = "https://music.example.com/rest/stream?id=abc&format=raw&v=1.16.1"
        val marked = Streaming.withReload(url, 3)
        assertNotEquals(url, marked)
        assertEquals("$url&musixReload=3", marked)
        assertEquals(url, Streaming.withoutReload(marked))
    }

    @Test fun reloadMarkerIsReplacedNotRepeated() {
        val url = "https://music.example.com/rest/stream?id=abc"
        assertEquals("$url&musixReload=2", Streaming.withReload(Streaming.withReload(url, 1), 2))
    }

    @Test fun reloadMarkerAsTheOnlyOrFirstParameter() {
        assertEquals("https://x/rest/stream", Streaming.withoutReload("https://x/rest/stream?musixReload=1"))
        assertEquals("https://x/rest/stream?id=a", Streaming.withoutReload("https://x/rest/stream?musixReload=1&id=a"))
        assertEquals("https://x/rest/stream?id=a", Streaming.withoutReload("https://x/rest/stream?id=a"))
    }
}
