package app.musix.mix

import app.musix.net.Played
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** Issue #186: the time machine's dates, names and merged plays. */
class TimeMachineTest {
    @Test fun aMonth() {
        val w = timeWindow(2019, 3, Locale.ENGLISH)
        assertEquals(LocalDate.of(2019, 3, 1), w.from)
        assertEquals(LocalDate.of(2019, 4, 1), w.to)
        assertEquals("March 2019", w.label)
    }

    @Test fun decemberEndsInTheNewYear() {
        assertEquals(LocalDate.of(2020, 1, 1), timeWindow(2019, 12, Locale.ENGLISH).to)
    }

    @Test fun aWholeYear() {
        val w = timeWindow(2019, null, Locale.ENGLISH)
        assertEquals(LocalDate.of(2019, 1, 1), w.from)
        assertEquals(LocalDate.of(2020, 1, 1), w.to)
        assertEquals("2019", w.label)
    }

    @Test fun memoryIsNamedAsAsked() = assertEquals("Time Machine - March 2019", memoryName("March 2019"))

    @Test fun noMonthsInTheFuture() {
        val today = LocalDate.of(2026, 10, 18)
        assertEquals(1..12, monthsAvailable(2025, today))
        assertEquals(1..10, monthsAvailable(2026, today))
        assertTrue(monthsAvailable(2027, today).isEmpty())
    }

    @Test fun bothServicesCountTheSameListenOnce() {
        val koito = listOf(Played("alt-J", "Left Hand Free", 12), Played("Bonobo", "Elysian", 4))
        val lastFm = listOf(Played("Alt-J", "left hand free", 11), Played("Vance Joy", "Riptide", 9))
        val merged = mergePlays(koito, lastFm)
        assertEquals(listOf("Left Hand Free" to 12L, "Riptide" to 9L, "Elysian" to 4L), merged.map { it.title to it.count })
        // The first service's spelling is kept.
        assertEquals("alt-J", merged.first().artist)
    }

    @Test fun theHigherCountWins() {
        val merged = mergePlays(listOf(Played("Bonobo", "Kerala", 3)), listOf(Played("Bonobo", "Kerala", 8)))
        assertEquals(8L, merged.single().count)
        assertEquals("Bonobo", merged.single().artist)
    }

    @Test fun artistsWithoutATitleAreLeftOut() {
        assertTrue(mergePlays(listOf(Played("Bonobo", null, 30))).isEmpty())
    }
}
