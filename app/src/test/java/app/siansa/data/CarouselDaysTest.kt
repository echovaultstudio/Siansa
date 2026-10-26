package app.siansa.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home carousels for certain days or all week (issue #512). */
class CarouselDaysTest {
    private val mixes = listOf(CarouselMix("A", "a"), CarouselMix("B", "b"), CarouselMix("C", "c"))
    private val everyMorning = Carousel("morning", "Your morning", 6, 11, mixes)
    private val weekendMorning = Carousel("weekend", "Lazy weekend", 6, 11, mixes, days = listOf(6, 7))
    private val workdays = Carousel("day", "Your day", 11, 17, mixes, days = listOf(1, 2, 3, 4, 5))

    @Test fun aCarouselForCertainDaysComesBeforeOneForAllWeek() {
        val all = listOf(everyMorning, weekendMorning, workdays)
        assertEquals(weekendMorning, carouselFor(all, hour = 8, day = 6))
        assertEquals(everyMorning, carouselFor(all, hour = 8, day = 2))
    }

    @Test fun onOtherDaysThereIsNone() {
        assertEquals(workdays, carouselFor(listOf(workdays), hour = 12, day = 5))
        assertNull(carouselFor(listOf(workdays), hour = 12, day = 7))
    }

    @Test fun everyDayPickedIsAllWeek() {
        assertEquals(emptyList<Int>(), normalDays((1..7).toList()))
        assertEquals(emptyList<Int>(), normalDays(emptyList()))
        assertEquals(listOf(1, 5, 6), normalDays(listOf(6, 1, 5, 5, 9)))
        assertTrue(everyMorning.coversDay(3))
    }

    @Test fun carouselsSavedBeforeHaveNoDays() {
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString(Carousel.serializer(), """{"id":"day","title":"Your day","startHour":11,"endHour":17,"mixes":[]}""")
        assertTrue(old.days.isEmpty())
    }

    @Test fun daysAndHoursAnotherCarouselHasCantBeSaved() {
        val all = listOf(everyMorning, workdays)
        // Your morning on weekends only overlaps nothing (Your day starts at 11).
        assertNull(carouselClash(everyMorning.copy(days = listOf(6, 7)), all))
        // Your day stretched to 9 on weekdays overlaps Your morning (all week, 6–11).
        val clash = carouselClash(workdays.copy(startHour = 9), all)
        assertTrue(clash.orEmpty(), clash.orEmpty().startsWith("\"Your morning\" already has 06:00–11:00, all week"))
        // On weekends only, from 9, it overlaps Your morning too (all week includes weekends).
        assertTrue(carouselClash(workdays.copy(startHour = 9, days = listOf(6, 7)), all) != null)
        // A weekend morning next to a weekday morning is fine.
        val weekdayMorning = everyMorning.copy(days = listOf(1, 2, 3, 4, 5))
        assertNull(carouselClash(weekendMorning, listOf(weekdayMorning, workdays)))
        // Saving a carousel doesn't clash with itself.
        assertNull(carouselClash(everyMorning, all))
        // The suggested carousels don't overlap each other.
        DefaultCarousels.all.forEach { assertNull(it.title, carouselClash(it, DefaultCarousels.all)) }
    }
}
