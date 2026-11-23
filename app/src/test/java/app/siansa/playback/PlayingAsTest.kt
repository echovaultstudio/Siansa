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
        assertEquals(PlayingAs(PlayingAs.kbit(256).let { "Opus $it" }, "Converted by your server · mobile data", differs = true), of(opus = 256, wifi = false))
        assertEquals("Converted by your server · Wi-Fi", of(opus = 128).detail)
        // Converting wins over a copy of the original in the song cache: the conversion is what plays.
        assertTrue(of(opus = 128, cached = true).differs)
    }

    @Test fun aDownloadPlaysFromThePhoneWhateverTheNetwork() {
        assertEquals(PlayingAs("Same as the file", "Downloaded · plays from this phone", differs = false), of(download = 0, opus = 256, wifi = false))
        assertEquals(PlayingAs(PlayingAs.kbit(192).let { "Opus $it" }, "Downloaded · plays from this phone", differs = true), of(download = 192, cached = true))
    }

    @Test fun theSongCachePlaysTheOriginalFromThePhone() {
        assertEquals(PlayingAs("Same as the file", "From the song cache on this phone", differs = false), of(cached = true, wifi = false))
    }

    /** The owner, 16 Nov: the song cache off, a song loaded ahead said "From the song cache". */
    @Test fun withTheSongCacheOffASongLoadedAheadSaysSo() {
        assertEquals(PlayingAs("Same as the file", "Loaded ahead · plays from this phone", differs = false),
            PlayingAs.of(null, null, 0, cached = true, wifi = true, cacheOn = false))
        // Not loaded: streamed, whatever the cache.
        assertEquals("Streamed as it is · Wi-Fi", PlayingAs.of(null, null, 0, cached = false, wifi = true, cacheOn = false).detail)
    }

    @Test fun onATvOrSpeakerItSaysWhere() {
        val tv = of(device = "Living room TV", download = 192, opus = 256)
        assertEquals("On Living room TV", tv.title)
        assertFalse(tv.differs)
    }

    @Test fun theFilesSide() {
        assertEquals("FLAC" to PlayingAs.kbit(2116), PlayingAs.file("FLAC", "flac", 2116))
        // The number and its unit never break apart: no plain space, and the slash joined.
        assertEquals("2,116\u00A0kbit\u2060/\u2060s", PlayingAs.kbit(2116))
        // Offline the server's details are missing: the library's file type, and no bit rate.
        assertEquals("M4A" to null, PlayingAs.file(null, ".m4a", null))
        assertEquals("Unknown" to null, PlayingAs.file(null, null, 0))
    }

    /** The owner, 23 Nov: "actually show what's being played, whether it's resampled or not. Because even in DJ mix it says playing same as file". */
    @Test fun itSaysWhatThePhoneDoesWithTheSound() {
        fun heard(hz: Int?, untouched: Boolean = false, blended: String? = null, opus: Int = 0, download: Int? = null, device: String? = null) =
            PlayingAs.of(device, download, opus, cached = false, wifi = true, cacheOn = true, fileHz = hz, untouched = untouched, blended = blended)
        val nb = "\u00A0"
        // A 44.1 kHz file passes the mixer as it is.
        assertEquals(PlayingAs("Same as the file", "Streamed as it is · Wi-Fi", differs = false, sound = "Unchanged at 44.1${nb}kHz"), heard(44_100))
        // Any other rate is resampled there, and that isn't the file: lit, and said first.
        val hiRes = heard(96_000)
        assertEquals("Resampled to 44.1${nb}kHz", hiRes.title)
        assertTrue(hiRes.differs)
        assertEquals("Resampled from 96${nb}kHz to 44.1${nb}kHz", hiRes.sound)
        assertEquals("Resampled from 48${nb}kHz to 44.1${nb}kHz", heard(48_000).sound)
        // In a DJ mix or with crossfade the song is the file only between its blends.
        val dj = heard(44_100, blended = "DJ mix")
        assertEquals("Same as the file between blends", dj.title)
        assertFalse(dj.differs)
        assertEquals("Unchanged at 44.1${nb}kHz · blended at song changes (DJ mix)", dj.sound)
        assertEquals("Resampled to 44.1${nb}kHz", heard(96_000, blended = "crossfade").title)
        assertEquals("Resampled from 96${nb}kHz to 44.1${nb}kHz · blended at song changes (crossfade)", heard(96_000, blended = "crossfade").sound)
        // Untouched playback: the file's own rate, nothing blended, whatever else is set.
        assertEquals(PlayingAs("Same as the file", "Streamed as it is · Wi-Fi", differs = false, sound = "Untouched · 96${nb}kHz"), heard(96_000, untouched = true, blended = "crossfade"))
        assertEquals("Untouched, at its own sample rate", heard(null, untouched = true).sound)
        // The server didn't say the rate: no claim about resampling.
        assertEquals("Through Siansa's mixer at 44.1${nb}kHz", heard(null).sound)
        assertEquals("Same as the file", heard(null).title)
        // Opus comes out at 48 kHz whatever the file was: converted by the server, then resampled by the mixer.
        val opus = heard(44_100, opus = 256)
        assertTrue(opus.title.startsWith("Opus"))
        assertEquals("Resampled from 48${nb}kHz to 44.1${nb}kHz", opus.sound)
        assertEquals("Untouched · 48${nb}kHz", heard(96_000, untouched = true, download = 192).sound)
        // A download of the original plays as the file, whatever the stream would be converted to.
        assertEquals("Unchanged at 44.1${nb}kHz", heard(44_100, opus = 256, download = 0).sound)
        // On a TV or speaker the phone plays nothing.
        assertEquals(null, heard(96_000, device = "Living room TV").sound)
        // The rate comes with the song's details, after the rest: added then.
        val later = PlayingAs.of(null, null, 0, cached = false, wifi = true).copy(told = PlayingAs.Told(null, null, 0, false, true, true, untouched = false, blended = "DJ mix"))
        assertEquals("Resampled to 44.1${nb}kHz", later.heard(88_200).title)
        assertEquals(dj.sound, later.heard(44_100).sound)
    }
}
