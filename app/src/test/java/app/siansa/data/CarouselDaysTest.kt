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
}
