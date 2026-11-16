package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Playing" in About this song (issue #1038): what really plays and where from. The box is lit only when that isn't the
 * file as it is on the server.
 */
class PlayingAsTest {
    private fun of(device: String? = null, download: Int? = null, opus: Int = 0, cached: Boolean = false, wifi: Boolean = true) =
        PlayingAs.of(device, download, opus, cached, wifi)

    @Test fun theOriginalStreamedIsTheSameAsTheFile() {
        assertEquals(PlayingAs("Same as the file", "Streamed as it is · Wi-Fi", differs = false), of())
        assertEquals("Streamed as it is · mobile data", of(wifi = false).detail)
    }

    @Test fun convertedByTheServerIsLitWithItsBitrateAndNetwork() {
        assertEquals(PlayingAs("Opus 256 kbit/s", "Converted by your server · mobile data", differs = true), of(opus = 256, wifi = false))
        assertEquals("Converted by your server · Wi-Fi", of(opus = 128).detail)
        // Converting wins over a copy of the original in the song cache: the conversion is what plays.
        assertTrue(of(opus = 128, cached = true).differs)
    }

    @Test fun aDownloadPlaysFromThePhoneWhateverTheNetwork() {
        assertEquals(PlayingAs("Same as the file", "Downloaded · plays from this phone", differs = false), of(download = 0, opus = 256, wifi = false))
        assertEquals(PlayingAs("Opus 192 kbit/s", "Downloaded · plays from this phone", differs = true), of(download = 192, cached = true))
    }

    @Test fun theSongCachePlaysTheOriginalFromThePhone() {
        assertEquals(PlayingAs("Same as the file", "From the song cache on this phone", differs = false), of(cached = true, wifi = false))
    }

    @Test fun onATvOrSpeakerItSaysWhere() {
        val tv = of(device = "Living room TV", download = 192, opus = 256)
        assertEquals("On Living room TV", tv.title)
        assertFalse(tv.differs)
    }

    @Test fun theFilesSide() {
        assertEquals("FLAC" to "2,116 kbit/s", PlayingAs.file("FLAC", "flac", 2116))
        // Offline the server's details are missing: the library's file type, and no bit rate.
        assertEquals("M4A" to null, PlayingAs.file(null, ".m4a", null))
        assertEquals("Unknown" to null, PlayingAs.file(null, null, 0))
    }
}
