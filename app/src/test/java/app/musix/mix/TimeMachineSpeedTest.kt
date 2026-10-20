package app.musix.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** The time machine shows what's there at once and keeps finished months (issue #319). */
class TimeMachineSpeedTest {
    private val today = LocalDate.of(2026, 10, 20)

    @Test fun finishedMonthsAndYearsAreKeptTheCurrentOnesNot() {
        assertTrue(periodFinished(timeWindow(2025, 10, Locale.ENGLISH), today))
        assertTrue(periodFinished(timeWindow(2026, 9, Locale.ENGLISH), today))
        assertTrue(periodFinished(timeWindow(2025, null, Locale.ENGLISH), today))
        assertFalse(periodFinished(timeWindow(2026, 10, Locale.ENGLISH), today))
        assertFalse(periodFinished(timeWindow(2026, null, Locale.ENGLISH), today))
        // The last day of a month: still going.
        assertFalse(periodFinished(timeWindow(2026, 10, Locale.ENGLISH), LocalDate.of(2026, 10, 31)))
        assertTrue(periodFinished(timeWindow(2026, 10, Locale.ENGLISH), LocalDate.of(2026, 11, 1)))
    }

    @Test fun theLineSaysWhatIsStillBeingAdded() {
        assertEquals("From Koito · adding plays from Last.fm…", timeMachineSource(listOf("Koito"), listOf("Last.fm")))
        assertEquals("From Koito and Last.fm", timeMachineSource(listOf("Koito", "Last.fm"), emptyList()))
    }

    @Test fun addingLastFmLaterGivesTheSameListAsBothAtOnce() {
        val koito = listOf(app.musix.net.Played("A", "One", 10), app.musix.net.Played("B", "Two", 4))
        val lastFm = listOf(app.musix.net.Played("a", "one", 12), app.musix.net.Played("C", "Three", 7))
        val first = mergePlays(koito)
        assertEquals(listOf("One", "Two"), first.map { it.title })
        val both = mergePlays(koito, lastFm)
        assertEquals(listOf("One" to 12L, "Three" to 7L, "Two" to 4L), both.map { it.title to it.count })
    }
}
