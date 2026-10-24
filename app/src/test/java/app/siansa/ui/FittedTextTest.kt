package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The device name on now playing shrinks to fit before it's cut off (issue #311). */
class FittedTextTest {
    @Test fun shrinksInSmallStepsDownToTheLimit() {
        assertEquals(0.92f, nextFitScale(1f, 0.7f), 1e-4f)
        assertEquals(0.7f, nextFitScale(0.72f, 0.7f), 1e-4f)
        var s = 1f
        repeat(10) { s = nextFitScale(s, 0.7f) }
        assertEquals(0.7f, s, 1e-4f)
    }
}
