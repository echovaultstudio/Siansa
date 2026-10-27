package app.siansa.mix

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The time machine's report (#578): the month or year chosen, compared with the one before. */
class TimeMachineReportTest {
    private val today = LocalDate.of(2026, 10, 27)

    @Test fun aPastMonthIsTheWholeMonth() {
        val w = timeMachineReportWindow(2025, 5, today)
        assertEquals(LocalDate.of(2025, 5, 1), w.from)
        assertEquals(LocalDate.of(2025, 6, 1), w.to)
        assertEquals(LocalDate.of(2025, 4, 1), w.prevFrom)
        assertEquals(w.title, w.subtitle)
    }

    @Test fun aPastYearIsTheWholeYearAgainstTheOneBefore() {
        val w = timeMachineReportWindow(2025, null, today)
        assertEquals(LocalDate.of(2025, 1, 1), w.from)
        assertEquals(LocalDate.of(2026, 1, 1), w.to)
        assertEquals(LocalDate.of(2024, 1, 1), w.prevFrom)
        assertEquals("2025", w.title)
    }

    @Test fun thisMonthGoesUpToToday() {
        val w = timeMachineReportWindow(2026, 10, today)
        assertEquals(LocalDate.of(2026, 10, 28), w.to)
        assertEquals("So far", w.subtitle)
    }
}
