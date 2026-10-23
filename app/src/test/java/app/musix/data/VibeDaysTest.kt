package app.musix.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Daily vibes for certain days, times of day, or both (issue #348). */
class VibeDaysTest {
    private fun vibe(id: String, start: Int, end: Int, days: List<Int> = emptyList(), anyTime: Boolean = false, enabled: Boolean = true) =
        Selector(id, id, start, end, guidance = "g", baseQuery = "q", days = days, anyTime = anyTime, enabled = enabled)

    private val MONDAY = 1
    private val FRIDAY = 5

    @Test fun noDaysMeansEveryDay() {
        val morning = vibe("m", 6, 12)
        assertTrue(morning.appliesAt(8, MONDAY))
        assertTrue(morning.appliesAt(8, FRIDAY))
        assertFalse(morning.appliesAt(14, FRIDAY))
    }

    @Test fun daysAndHoursWorkTogetherAndAlone() {
        val fridayAfternoon = vibe("f", 12, 17, days = listOf(FRIDAY))
        assertTrue(fridayAfternoon.appliesAt(14, FRIDAY))
        assertFalse(fridayAfternoon.appliesAt(14, MONDAY))
        assertFalse(fridayAfternoon.appliesAt(9, FRIDAY))
        val allMonday = vibe("a", 18, 22, days = listOf(MONDAY), anyTime = true)
        assertTrue(allMonday.appliesAt(3, MONDAY))
        assertFalse(allMonday.appliesAt(3, FRIDAY))
    }

    @Test fun theMostSpecificVibeWins() {
        val everyAfternoon = vibe("every", 12, 17)
        val fridays = vibe("fridays", 0, 0, days = listOf(FRIDAY), anyTime = true)
        val fridayAfternoon = vibe("friAfternoon", 12, 17, days = listOf(FRIDAY))
        val all = listOf(everyAfternoon, fridays, fridayAfternoon)
        assertEquals("friAfternoon", fittingSelector(all, 14, FRIDAY)?.id)
        assertEquals("fridays", fittingSelector(all, 20, FRIDAY)?.id)
        assertEquals("every", fittingSelector(all, 14, MONDAY)?.id)
        assertNull(fittingSelector(all, 20, MONDAY))
    }

    @Test fun turningOneOffLeavesTheOthers() {
        val off = vibe("fri", 12, 17, days = listOf(FRIDAY), enabled = false)
        val every = vibe("every", 12, 17)
        assertEquals("every", fittingSelector(listOf(off, every), 14, FRIDAY)?.id)
    }

    @Test fun labels() {
        assertEquals("12:00–17:00", vibe("a", 12, 17).hoursLabel)
        assertEquals("Fri · 12:00–17:00", vibe("a", 12, 17, days = listOf(FRIDAY)).hoursLabel)
        assertEquals("Weekends · any time", vibe("a", 0, 0, days = listOf(7, 6), anyTime = true).hoursLabel)
        assertEquals("Weekdays", daysLabel(listOf(5, 4, 3, 2, 1)))
        assertEquals("Mon, Wed", daysLabel(setOf(3, 1)))
        assertEquals("Every day", daysLabel(emptyList()))
    }
}
