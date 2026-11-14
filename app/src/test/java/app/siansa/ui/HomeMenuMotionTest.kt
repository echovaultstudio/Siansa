package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How Home's menu comes into the top row (the owner, 12 Nov): one spring, the chips shrinking away and the pills growing in
 * one after another, nothing taller than the chips.
 */
class HomeMenuMotionTest {
    @Test fun closedNothingShowsAndOpenEverythingDoes() {
        for (i in 0 until 4) {
            assertEquals(0f, placeIn(0f, i), 0f)
            assertEquals(1f, placeIn(1f, i), 0f)
            assertEquals(1f, placeScale(1f, i), 1e-6f)
        }
        assertEquals(1f, chipShrink(0f), 0f)
        assertTrue("The chips shrink, but never to nothing", chipShrink(1f) in 0.4f..0.8f)
    }

    @Test fun thePlacesComeInOneAfterAnother() {
        val half = (0 until 4).map { placeIn(0.5f, it) }
        for (i in 1 until half.size) assertTrue("Place $i is behind place ${i - 1}", half[i] < half[i - 1])
        assertTrue("The first is well in at half way", half[0] > 0.6f)
        assertTrue("The last has started", half[3] > 0f)
        // Growing in: small and clear at the start, full size when in.
        assertTrue(placeScale(0.2f, 0) < 1f && placeScale(0.2f, 0) >= 0.6f)
    }

    @Test fun theSpringsOvershootShowsInThePlacesOnly() {
        // Past 1 the pills swell a touch; the chips' shrink and the pills' alpha stay at their full value.
        assertTrue(placeScale(1.05f, 0) > 1f)
        assertEquals(1f, placeIn(1.05f, 0), 0f)
        assertEquals(chipShrink(1f), chipShrink(1.05f), 0f)
    }

    @Test fun theSpringIsSoftAndQuick() {
        // Softer than the DJ bar's (800), with a little give: about 0.4 s to settle.
        assertTrue(MENU_SPRING_STIFFNESS in 200f..500f)
        assertTrue(MENU_SPRING_DAMPING in 0.7f..0.95f)
    }
}
