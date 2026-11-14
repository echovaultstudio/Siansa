package app.siansa.ui

import app.siansa.mix.TIME_MACHINE_FIRST_YEAR
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeMachineYearsTest {
    @Test
    fun `the years run from this one back to the first, newest first`() {
        val years = timeMachineYears(2026)
        assertEquals(2026, years.first())
        assertEquals(TIME_MACHINE_FIRST_YEAR, years.last())
        assertEquals(years.sortedDescending(), years)
        assertEquals(2026 - TIME_MACHINE_FIRST_YEAR + 1, years.size)
    }
}
