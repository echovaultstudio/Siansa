package app.siansa.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class SongLengthTest {
    @Test
    fun aFileFarLongerThanTheSongIsPlayedAsTheSong() {
        // The Atmos DJ mix of issue #860: 431 s in the library, 6498 s in its header.
        assertEquals(431_500_000L, SongLength.corrected(6_498_000_000L, 431))
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
}
