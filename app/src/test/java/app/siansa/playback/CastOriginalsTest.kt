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
