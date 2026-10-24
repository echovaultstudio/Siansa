package app.siansa

import app.siansa.mix.ReportRange
import app.siansa.mix.reportWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Which days each Your listening page covers. */
class ReportWindowTest {
    private val today = LocalDate.of(2026, 10, 16)

    @Test fun thisTimeLastYearIsTheWholeMonthAYearAgo() {
        val w = reportWindow(ReportRange.YEAR_AGO, today)
        assertEquals(LocalDate.of(2025, 10, 1), w.from)
        assertEquals(LocalDate.of(2025, 11, 1), w.to)
        assertEquals(LocalDate.of(2025, 9, 1), w.prevFrom)
        assertEquals("This time last year", w.title)
        assertEquals(true, w.subtitle.endsWith("2025"))
    }

    @Test fun thisTimeLastYearInJanuaryAndAtMonthEnd() {
        val jan = reportWindow(ReportRange.YEAR_AGO, LocalDate.of(2026, 1, 31))
        assertEquals(LocalDate.of(2025, 1, 1), jan.from)
        assertEquals(LocalDate.of(2025, 2, 1), jan.to)
        assertEquals(LocalDate.of(2024, 12, 1), jan.prevFrom)
        val leap = reportWindow(ReportRange.YEAR_AGO, LocalDate.of(2025, 2, 28))
        assertEquals(LocalDate.of(2024, 2, 1), leap.from)
        assertEquals(LocalDate.of(2024, 3, 1), leap.to)
    }

    @Test fun otherRangesUnchanged() {
        val month = reportWindow(ReportRange.THIS_MONTH, today)
        assertEquals(LocalDate.of(2026, 10, 1), month.from)
        assertEquals(LocalDate.of(2026, 10, 17), month.to)
        val last = reportWindow(ReportRange.LAST_MONTH, today)
        assertEquals(LocalDate.of(2026, 9, 1), last.from)
        assertEquals(LocalDate.of(2026, 10, 1), last.to)
        val year = reportWindow(ReportRange.THIS_YEAR, today)
        assertEquals(LocalDate.of(2026, 1, 1), year.from)
        assertEquals(LocalDate.of(2025, 1, 1), year.prevFrom)
    }

    @Test fun rangeFromRouteId() {
        assertEquals(ReportRange.YEAR_AGO, ReportRange.of("year-ago"))
        assertEquals(ReportRange.THIS_MONTH, ReportRange.of("unknown"))
    }
}
