package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The slide's up choice on the seek bar's line (issue #963). */
class UpReachTest {
    @Test
    fun theSeekBarWhenItsInReach() {
        assertEquals(260, upReach(playCentreY = 1700f, trackY = 1440f))
        // Not known yet: the usual reach.
        assertNull(upReach(null, 1440f))
        assertNull(upReach(1700f, null))
        // A bar right on the button, or far up the page, isn't used.
        assertNull(upReach(1700f, 1680f))
        assertNull(upReach(1700f, 200f))
    }
}
