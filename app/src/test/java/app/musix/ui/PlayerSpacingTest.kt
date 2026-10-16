package app.musix.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The gap above "Playing from album" in now playing, on phones of different sizes. */
class PlayerSpacingTest {
    @Test fun growsWithTheScreen() {
        assertEquals(20f, playerTopGapDp(800), 0.001f)
        assertEquals(22f, playerTopGapDp(880), 0.001f)
    }

    @Test fun smallPhonesKeepAtLeastAModestGap() {
        assertEquals(12f, playerTopGapDp(400), 0.001f)
    }

    @Test fun tallScreensDoNotPushEverythingDown() {
        assertEquals(28f, playerTopGapDp(1400), 0.001f)
    }
}
