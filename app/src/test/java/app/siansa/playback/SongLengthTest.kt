package app.siansa.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class SongLengthTest {
    @Test
    fun aFileFarLongerThanTheSongIsPlayedAsTheSong() {
        // The Atmos DJ mix of issue #860: 431 s in the library, 6498 s in its header.
        // Without the file's last frame: the library's seconds rounded up, never short of the real sound.
        assertEquals(432_000_000L, SongLength.corrected(6_498_000_000L, 431))
    }

    @Test
    fun aFileAboutAsLongAsTheSongKeepsItsOwnLength() {
        assertEquals(431_734_000L, SongLength.corrected(431_734_000L, 431))
        assertEquals(440_000_000L, SongLength.corrected(440_000_000L, 431))
    }

    @Test
    fun aShorterFileKeepsItsOwnLength() {
        assertEquals(200_000_000L, SongLength.corrected(200_000_000L, 431))
    }

    @Test
    fun unknownLengthsAreLeftAlone() {
        assertEquals(C.TIME_UNSET, SongLength.corrected(C.TIME_UNSET, 431))
        assertEquals(6_498_000_000L, SongLength.corrected(6_498_000_000L, null))
        assertEquals(6_498_000_000L, SongLength.corrected(6_498_000_000L, 0))
    }

    @Test fun aWrongLengthIsReplacedByTheFilesLastFrame() {
        // 0.52.0-dev, TV, 20:36: "the floor" (431 s in the library, 6498 s in its header) really ends at 431.9 s. Played
        // as 431.5 s, its last 0.4 s ran into the next song, which stalled at "1 s". Its last frame says where it ends.
        assertEquals(431_872_000L + SongLength.FRAME_US, SongLength.corrected(6_498_000_000L, 431, lastFrameUs = 431_872_000L))
        // A last frame far from the library's length isn't believed: the seconds rounded up.
        assertEquals(432_000_000L, SongLength.corrected(6_498_000_000L, 431, lastFrameUs = 6_497_968_000L))
        assertEquals(432_000_000L, SongLength.corrected(6_498_000_000L, 431, lastFrameUs = 100_000_000L))
        // A right length is left alone whatever the last frame.
        assertEquals(431_734_000L, SongLength.corrected(431_734_000L, 431, lastFrameUs = 431_702_000L))
    }

    @Test fun theLastFrameIsReadFromTheSeekMap() {
        val table = object : androidx.media3.extractor.SeekMap {
            override fun isSeekable() = true
            override fun getDurationUs() = 6_498_000_000L
            override fun getSeekPoints(timeUs: Long) =
                androidx.media3.extractor.SeekMap.SeekPoints(androidx.media3.extractor.SeekPoint(minOf(timeUs, 431_872_000L), 0L))
        }
        assertEquals(431_872_000L, SongLength.lastFrameUs(table, 6_498_000_000L))
        val unseekable = androidx.media3.extractor.SeekMap.Unseekable(6_498_000_000L)
        assertEquals(C.TIME_UNSET, SongLength.lastFrameUs(unseekable, 6_498_000_000L))
    }
}
