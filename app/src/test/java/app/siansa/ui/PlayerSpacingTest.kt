package app.siansa.ui

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

/** The rounded top corners of now playing while it is dragged down. */
class PlayerCardCornerTest {
    @Test fun squareWhenOpen() {
        assertEquals(0f, playerCardCornerPx(0f, 70f), 0.001f)
    }

    @Test fun roundsAsItIsDragged() {
        assertEquals(30f, playerCardCornerPx(30f, 70f), 0.001f)
    }

    @Test fun stopsAtTheLargestRadius() {
        assertEquals(70f, playerCardCornerPx(900f, 70f), 0.001f)
    }

    @Test fun neverNegative() {
        assertEquals(0f, playerCardCornerPx(-20f, 70f), 0.001f)
        assertEquals(0f, playerCardCornerPx(50f, -5f), 0.001f)
    }
}
