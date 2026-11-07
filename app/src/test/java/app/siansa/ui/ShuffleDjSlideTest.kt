package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** An album's shuffle button slides right onto Play for a DJ mix (issue #965). */
class ShuffleDjSlideTest {
    @Test
    fun armedOnlyOnPlay() {
        // Play's near edge 120 px right of shuffle's middle: short of it, nothing; on it, armed.
        assertFalse(djArmed(dx = 100f, rightArm = 120f))
        assertTrue(djArmed(dx = 120f, rightArm = 120f))
        assertTrue(djArmed(dx = 180f, rightArm = 120f))
    }

    @Test
    fun alwaysTowardsPlay() {
        assertEquals(SlideWay.RIGHT, DjSlide(armed = false).way)
    }
}
