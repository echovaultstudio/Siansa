package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which songs Siansa's TV screen may play as their original file (issue #114). */
class CastOriginalsTest {
    @Test fun filesEveryCastDevicePlaysGoAsTheOriginalWithTheirType() {
        assertEquals("audio/flac", CastOriginals.typeOf("flac"))
        assertEquals("audio/mpeg", CastOriginals.typeOf("mp3"))
        assertEquals("audio/ogg", CastOriginals.typeOf("ogg"))
        assertEquals("audio/ogg", CastOriginals.typeOf("oga"))
        assertEquals("audio/ogg", CastOriginals.typeOf("opus"))
        assertEquals("audio/wav", CastOriginals.typeOf("wav"))
        assertEquals("audio/aac", CastOriginals.typeOf("aac"))
    }

    @Test fun alacAndM4aGoAsMp3() {
        // An ALAC .m4a ended the cast on a SHIELD (0.52.0-dev); .m4a can be AAC or ALAC and only the type is known.
        assertNull(CastOriginals.typeOf("m4a"))
        assertNull(CastOriginals.typeOf("mp4"))
        assertNull(CastOriginals.typeOf("alac"))
    }

    @Test fun unknownOrMissingTypesGoAsMp3() {
        assertNull(CastOriginals.typeOf(null))
        assertNull(CastOriginals.typeOf(""))
        assertNull(CastOriginals.typeOf("wma"))
        assertNull(CastOriginals.typeOf("dsf"))
        assertNull(CastOriginals.typeOf("ape"))
    }

    @Test fun caseSpacesAndADotDontMatter() {
        assertEquals("audio/flac", CastOriginals.typeOf("FLAC"))
        assertEquals("audio/flac", CastOriginals.typeOf(" .flac "))
        assertNull(CastOriginals.typeOf("M4A"))
    }
}

/** Lossless or MP3 320 for Chromecast (issue #114): what the TV plays instead of the MP3. */
class CastQualityTest {
    private val raw = "https://music.example/rest/stream?u=me&t=abc&s=salt&v=1.16.1&c=Siansa&f=json&id=song1&format=raw"

    @Test fun losslessIsTheDefault() {
        assertEquals(CastQuality.Lossless, CastQuality.of(null))
        assertEquals(CastQuality.Lossless, CastQuality.of("something new"))
        assertEquals(CastQuality.Mp3, CastQuality.of("mp3"))
        CastQuality.entries.forEach { assertEquals(it, CastQuality.of(it.key)) }
    }

    @Test fun losslessSendsFilesTheTvPlaysAsTheyAre() {
        assertEquals(CastSource(raw, "audio/flac", converted = false), CastOriginals.source("flac", CastQuality.Lossless, raw))
        assertEquals(CastSource(raw, "audio/mpeg", converted = false), CastOriginals.source("mp3", CastQuality.Lossless, raw))
    }

    @Test fun losslessAsksTheServerForFlacOfAlacAndOtherLosslessFiles() {
        val flac = raw.replace("format=raw", "format=flac")
        listOf("m4a", "mp4", "alac", "ape", "wv", "aiff", "aif", "dsf", "dff", "tta", " .M4A ").forEach {
            assertEquals(it, CastSource(flac, "audio/flac", converted = true), CastOriginals.source(it, CastQuality.Lossless, raw))
        }
    }

    @Test fun lossyFilesTheTvCantPlayAndUnknownOnesGoAsMp3() {
        listOf("wma", "mpc", "ra", null, "").forEach { assertNull(it, CastOriginals.source(it, CastQuality.Lossless, raw)) }
    }

    @Test fun mp3QualityAlwaysSendsTheMp3() {
        listOf("flac", "mp3", "m4a", "ape", null).forEach { assertNull(it, CastOriginals.source(it, CastQuality.Mp3, raw)) }
    }

    @Test fun onlySubsonicAddressesAreConverted() {
        // Jellyfin and Emby have their own way to convert: their ALAC still goes as the MP3.
        val emby = "https://jf.example/Audio/abc/stream?Static=true&ApiKey=x"
        assertNull(CastOriginals.source("m4a", CastQuality.Lossless, emby))
        assertEquals(CastSource(emby, "audio/flac", false), CastOriginals.source("flac", CastQuality.Lossless, emby))
    }

    @Test fun theFlacAddressSwapsOnlyTheFormat() {
        assertEquals("https://m.example/rest/stream?id=1&format=flac&v=1", CastOriginals.flacUrl("https://m.example/rest/stream?id=1&format=raw&v=1"))
        assertEquals("https://m.example/rest/stream?format=flac&id=1", CastOriginals.flacUrl("https://m.example/rest/stream?format=raw&id=1"))
        assertEquals("https://m.example/rest/stream?id=1&format=flac", CastOriginals.flacUrl("https://m.example/rest/stream?id=1"))
        assertEquals("https://m.example/rest/stream.view?id=1&format=flac", CastOriginals.flacUrl("https://m.example/rest/stream.view?id=1"))
        // A "format" inside another parameter's name is left alone.
        assertEquals("https://m.example/rest/stream?xformat=raw&format=flac", CastOriginals.flacUrl("https://m.example/rest/stream?xformat=raw"))
        assertNull(CastOriginals.flacUrl("https://m.example/rest/getCoverArt?id=1"))
    }
}
