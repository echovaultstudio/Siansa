package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The now playing and book player sheets as one finger-driven motion (#600). */
class SheetMotionTest {
    @Test fun aHardSwipeDecidesByItsDirection() {
        // Swiped down fast from nearly open: closes. Flicked up fast from the mini player: opens.
        assertFalse(sheetShouldOpen(wasOpen = true, progress = 0.95f, velocityPxPerSec = 3000f))
        assertTrue(sheetShouldOpen(wasOpen = false, progress = 0.05f, velocityPxPerSec = -3000f))
    }

    @Test fun aGentleReleaseGoesOnPastAFifthOfTheWay() {
        // Open, dragged down a little: back up. Dragged down past 22 %: closes.
        assertTrue(sheetShouldOpen(wasOpen = true, progress = 0.9f, velocityPxPerSec = 100f))
        assertFalse(sheetShouldOpen(wasOpen = true, progress = 0.7f, velocityPxPerSec = 100f))
        // Closed, the mini player pulled up a little: back down. Past 22 %: opens.
        assertFalse(sheetShouldOpen(wasOpen = false, progress = 0.1f, velocityPxPerSec = -100f))
        assertTrue(sheetShouldOpen(wasOpen = false, progress = 0.3f, velocityPxPerSec = -100f))
    }

    @Test fun theMiniPlayerFadesAndRecedesOverTheFirstPartOfTheWay() {
        assertEquals(1f, miniAlpha(0f), 0.001f)
        assertEquals(0.5f, miniAlpha(0.175f), 0.01f)
        assertEquals(0f, miniAlpha(0.35f), 0.001f)
        assertEquals(0f, miniAlpha(1f), 0.001f)
        assertEquals(1f, miniScale(0f), 0.001f)
        assertEquals(0.94f, miniScale(0.5f), 0.001f)
    }

    @Test fun theSheetFadesInAsItRises() {
        assertEquals(0.6f, sheetAlpha(0f), 0.001f)
        assertEquals(0.8f, sheetAlpha(0.15f), 0.001f)
        assertEquals(1f, sheetAlpha(0.3f), 0.001f)
        assertEquals(1f, sheetAlpha(1f), 0.001f)
    }

    @Test fun theCornersRoundAsTheSheetLeavesTheTop() {
        assertEquals(0f, sheetCornerPx(1f, 2000f, 70f), 0.001f)
        assertEquals(20f, sheetCornerPx(0.99f, 2000f, 70f), 0.001f)
        assertEquals(70f, sheetCornerPx(0.5f, 2000f, 70f), 0.001f)
    }
}
