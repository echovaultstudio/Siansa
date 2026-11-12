package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TimeMachineTitleTest {
    @Test
    fun `a whole year is just the year`() {
        assertEquals("2025", timeMachineTitle(2025, null, Locale.UK))
    }

    @Test
    fun `a month is its short name and the year`() {
        assertEquals("Oct 2025", timeMachineTitle(2025, 10, Locale.UK))
        assertEquals("May 2019", timeMachineTitle(2019, 5, Locale.UK))
    }

    @Test
    fun `a short name's full stop is left out`() {
        assertEquals("Okt 2025", timeMachineTitle(2025, 10, Locale.GERMANY))
    }
}
