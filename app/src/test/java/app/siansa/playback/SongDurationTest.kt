package app.siansa.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

/** Beta report 7552a06d: an E-AC3 .m4a whose header says 34 minutes for a 4-minute song. */
class SongDurationTest {
    @Test fun theServersLengthWinsWhenTheFileIsFarOff() {
        assertEquals(284_000L, songDurationMs(2_023_581L, 284))
        assertEquals(284_000L, songDurationMs(100_000L, 284))
    }

    @Test fun thePlayersLengthIsUsedWhenItsClose() {
        // The player's own reading is exact to the millisecond; the server rounds to seconds.
        assertEquals(284_512L, songDurationMs(284_512L, 284))
        assertEquals(300_000L, songDurationMs(300_000L, 284))
    }

    @Test fun whicheverIsKnown() {
        assertEquals(284_000L, songDurationMs(C.TIME_UNSET, 284))
        assertEquals(284_512L, songDurationMs(284_512L, 0))
        assertEquals(0L, songDurationMs(C.TIME_UNSET, 0))
    }
}
