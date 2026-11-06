package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An album's Explore slides (issue #965): up onto the cover for similar albums, right onto Play for a DJ mix. */
class ExploreArmedTest {
    @Test
    fun farEnoughEachWay() {
        assertTrue(exploreArmed(SlideWay.UP, 0f, -150f, upArm = 140f, rightArm = 250f))
        assertFalse(exploreArmed(SlideWay.UP, 0f, -100f, upArm = 140f, rightArm = 250f))
        // Right: only once the finger is on the Play button.
        assertFalse(exploreArmed(SlideWay.RIGHT, 200f, 0f, upArm = 140f, rightArm = 250f))
        assertTrue(exploreArmed(SlideWay.RIGHT, 260f, 0f, upArm = 140f, rightArm = 250f))
        assertFalse(exploreArmed(SlideWay.DOWN, 0f, 400f, upArm = 140f, rightArm = 250f))
    }

    @Test
    fun onlyUpAndRightDoAnything() {
        assertEquals(ExploreChoice.SIMILAR, ExploreChoice.of(SlideWay.UP))
        assertEquals(ExploreChoice.DJ_MIX, ExploreChoice.of(SlideWay.RIGHT))
        assertNull(ExploreChoice.of(SlideWay.LEFT))
        assertNull(ExploreChoice.of(SlideWay.DOWN))
        assertNull(ExploreChoice.of(null))
    }
}
