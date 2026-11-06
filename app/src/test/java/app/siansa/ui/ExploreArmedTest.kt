package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An album's Explore slide (issue #965): right onto Play for a DJ mix; similar albums is a tap. */
class ExploreArmedTest {
    @Test
    fun farEnoughEachWay() {
        assertFalse(exploreArmed(SlideWay.UP, 0f, rightArm = 250f))
        // Right: only once the finger is on the Play button.
        assertFalse(exploreArmed(SlideWay.RIGHT, 200f, rightArm = 250f))
        assertTrue(exploreArmed(SlideWay.RIGHT, 260f, rightArm = 250f))
        assertFalse(exploreArmed(SlideWay.DOWN, 400f, rightArm = 250f))
    }

    @Test
    fun onlyRightDoesAnything() {
        assertNull(ExploreChoice.of(SlideWay.UP)) // similar albums is a tap only
        assertEquals(ExploreChoice.DJ_MIX, ExploreChoice.of(SlideWay.RIGHT))
        assertNull(ExploreChoice.of(SlideWay.LEFT))
        assertNull(ExploreChoice.of(SlideWay.DOWN))
        assertNull(ExploreChoice.of(null))
    }
}
