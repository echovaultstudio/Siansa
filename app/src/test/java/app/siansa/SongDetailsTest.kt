package app.siansa

import app.siansa.data.AppJson
import app.siansa.data.PlayEvent
import app.siansa.net.SongDetails
import app.siansa.net.codecName
import app.siansa.net.formatFileSize
import app.siansa.net.formatSampleRate
import app.siansa.net.parseSongDetails
import app.siansa.net.skipCount
import app.siansa.ui.songDetailRows
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The "Show details" page of a song (issue #69). */
class SongDetailsTest {
    private fun parse(json: String) = parseSongDetails(AppJson.parseToJsonElement(json).jsonObject)

    @Test fun readsNavidromesSongFields() {
        val d = parse(
            """{"id":"s1","suffix":"flac","contentType":"audio/flac","bitRate":1024,"samplingRate":44100,
               "bitDepth":16,"channelCount":2,"size":34000000,"path":"Artist/Album/01 - Song.flac","playCount":12}""",
        )
        assertEquals(SongDetails("FLAC", 1024, 44100, 16, 2, 34000000, "Artist/Album/01 - Song.flac", 12), d)
    }

    @Test fun missingFieldsStayEmpty() {
        val d = parse("""{"id":"s1","bitRate":0,"path":""}""")
        assertNull(d.codec)
        assertNull(d.bitRateKbps)
        assertNull(d.path)
        assertNull(d.playCount)
    }

    @Test fun codecNames() {
        assertEquals("MP3", codecName("mp3", null))
        assertEquals("AAC", codecName("m4a", "audio/mp4"))
        assertEquals("Opus", codecName("opus", null))
        assertEquals("FLAC", codecName(null, "audio/x-flac"))
        assertEquals("MP3", codecName("", "audio/mpeg"))
        assertEquals("XYZ", codecName("xyz", null))
        assertNull(codecName(null, null))
    }

    @Test fun sampleRatesAndSizes() {
        assertEquals("44.1 kHz", formatSampleRate(44100))
        assertEquals("96 kHz", formatSampleRate(96000))
        assertEquals("850 KB", formatFileSize(850L * 1024))
        assertEquals("32.4 MB", formatFileSize((32.4 * 1024 * 1024).toLong()))
        assertEquals("1.5 GB", formatFileSize(1536L * 1024 * 1024))
    }

    @Test fun countsOnlyThisSongsSkips() {
        val history = listOf(
            PlayEvent("a", "A", "X", at = 1, skipped = true),
            PlayEvent("a", "A", "X", at = 2, skipped = false),
            PlayEvent("a", "A", "X", at = 3, skipped = true),
            PlayEvent("b", "B", "X", at = 4, skipped = true),
        )
        assertEquals(2, skipCount(history, "a"))
        assertEquals(0, skipCount(history, "c"))
    }

    @Test fun rowsShowUnknownForMissingValuesAndHideEmptyOnes() {
        val rows = songDetailRows(SongDetails(), 0)
        assertEquals(
            listOf(
                "Format" to "Unknown",
                "Bit rate" to "Unknown",
                "Plays" to "0",
                "Skips on this phone" to "0",
                "File" to "Unknown",
            ),
            rows,
        )
        val full = songDetailRows(SongDetails("FLAC", 1024, 44100, 16, 2, 34000000, "a/b.flac", 3), 1)
        assertEquals("Quality" to "44.1 kHz · 16-bit · Stereo", full[2])
        assertEquals("File size" to "32.4 MB", full[3])
    }
}
