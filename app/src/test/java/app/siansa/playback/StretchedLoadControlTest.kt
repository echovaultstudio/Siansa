package app.siansa.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Beta #16: E-AC3 files whose header claims far too much time weren't gapless. */
class StretchedLoadControlTest {
    @Test fun aFileFarLongerThanTheSongIsStretched() {
        assertTrue(StretchedSong.isStretched(2_298_000, 258))
        assertFalse(StretchedSong.isStretched(258_400, 258))
        assertFalse(StretchedSong.isStretched(androidx.media3.common.C.TIME_UNSET, 258))
        assertFalse(StretchedSong.isStretched(2_298_000, 0))
    }

    @Test fun keepsLoadingByBytesOnlyForAStretchedSong() {
        assertTrue(StretchedLoadControl.keepLoading(stretched = true, heldBytes = 1_000_000))
        assertFalse(StretchedLoadControl.keepLoading(stretched = true, heldBytes = StretchedLoadControl.STRETCHED_BYTES))
        assertFalse(StretchedLoadControl.keepLoading(stretched = false, heldBytes = 0))
    }
}
