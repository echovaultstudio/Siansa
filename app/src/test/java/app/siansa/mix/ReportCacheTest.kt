package app.siansa.mix

import app.siansa.data.AppJson
import app.siansa.net.HistoryApi
import app.siansa.net.Played
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Reports keep Koito's data on the phone and read its listens in parallel (#592). */
class ReportCacheTest {
    private val today = LocalDate.of(2026, 10, 27)

    @Test fun aPastYearIsFinishedAndTheCurrentOneIsNot() {
        assertTrue(reportIsFinished(timeMachineReportWindow(2025, null, today), today))
        assertFalse(reportIsFinished(timeMachineReportWindow(2026, null, today), today))
        assertTrue(reportIsFinished(timeMachineReportWindow(2026, 9, today), today))
        assertFalse(reportIsFinished(timeMachineReportWindow(2026, 10, today), today))
    }

    @Test fun aMonthIsFinishedOnlyOnceItsLastDayHasPassed() {
        val window = timeMachineReportWindow(2026, 10, LocalDate.of(2026, 10, 31))
        assertFalse(reportIsFinished(window, LocalDate.of(2026, 10, 31)))
        assertTrue(reportIsFinished(timeMachineReportWindow(2026, 10, LocalDate.of(2026, 11, 1)), LocalDate.of(2026, 11, 1)))
    }

    @Test fun theKeyTellsServersAndWindowsApart() {
        val a = reportKey("https://koito.example/", 1L, 2L, 0L)
        assertEquals("https://koito.example|1|2|0", a)
        assertEquals(a, reportKey("https://koito.example", 1L, 2L, 0L))
        assertTrue(a != reportKey("https://koito.example", 1L, 3L, 0L))
        assertTrue(a != reportKey("https://other.example", 1L, 2L, 0L))
    }

    @Test fun koitoDataSurvivesTheRoundTripToTheFile() {
        val data = Insights.KoitoData(
            totals = HistoryApi.Totals(26205, 10289, 400, 3000, 900, 12, 300),
            artists = listOf(Played("Karnivool", null, 500)),
            tracks = listOf(Played("Karnivool", "Simple Boy", 40)),
            albums = listOf(Played("Karnivool", "Sound Awake", 120)),
            previous = listOf(Played("Sleep Token", null, 300)),
            times = listOf(1767218400L, 1767222000L),
        )
        val back = AppJson.decodeFromString(Insights.KoitoData.serializer(), AppJson.encodeToString(Insights.KoitoData.serializer(), data))
        assertEquals(10289, back.totals.plays)
        assertEquals("Simple Boy", back.tracks.single().title)
        assertEquals(data.times, back.times)
    }

    @Test fun thePagesForAYearOfPlays() {
        assertEquals(21, HistoryApi.listenPages(10289))
        assertEquals(1, HistoryApi.listenPages(1))
        assertEquals(1, HistoryApi.listenPages(500))
        assertEquals(2, HistoryApi.listenPages(501))
        assertEquals(0, HistoryApi.listenPages(null))
        assertEquals(0, HistoryApi.listenPages(0))
        // Never more than the most a period is asked for.
        assertEquals(100, HistoryApi.listenPages(1_000_000))
    }
}
