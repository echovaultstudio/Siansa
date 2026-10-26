package app.siansa

import app.siansa.ui.chipScrollTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/** The chosen month's chip is scrolled into the middle of the row (issue #496). */
class TimeMachineChipsTest {
    @Test fun aChipFarRightIsCentred() {
        // A 1080 px wide window; the October chip at 1900..2100 of a row that scrolls up to 1600.
        assertEquals(1900 + 100 - 540, chipScrollTarget(left = 1900, width = 200, viewport = 1080, max = 1600))
    }

    @Test fun theRowNeverScrollsPastItsEnds() {
        // Whole year, the first chip: stays at the start.
        assertEquals(0, chipScrollTarget(left = 48, width = 260, viewport = 1080, max = 1400))
        // December, the last: as far as the row goes.
        assertEquals(1400, chipScrollTarget(left = 2400, width = 180, viewport = 1080, max = 1400))
        // A row that fits on screen doesn't scroll.
        assertEquals(0, chipScrollTarget(left = 600, width = 180, viewport = 1080, max = 0))
    }
}
