package app.siansa.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Animated covers until they're set: on for a phone, off for the TV app (issue #945). */
class AnimatedCoversDefaultTest {
    @Test
    fun offOnlyOnTheTv() {
        assertTrue(animatedCoversByDefault(tv = false))
        assertFalse(animatedCoversByDefault(tv = true))
        // The phone app's settings start with them on, as before.
        assertTrue(AppSettings().animatedCovers)
    }
}
