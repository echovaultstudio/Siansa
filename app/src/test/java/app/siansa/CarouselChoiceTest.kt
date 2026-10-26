package app.siansa

import app.siansa.data.Carousel
import app.siansa.data.CarouselMix
import app.siansa.data.Mix
import app.siansa.ui.CarouselChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** When a later part of the day begins, Home keeps the carousel you have until you ask (beta testers #37). */
class CarouselChoiceTest {
    private val today = "2026-10-26"
    private fun carousel(id: String, from: Int, to: Int) = Carousel(
        id, "Your $id", from, to,
        listOf(CarouselMix("A", "a sound"), CarouselMix("B", "b sound"), CarouselMix("C", "c sound")),
    )
    private val morning = carousel("morning", 6, 11)
    private val day = carousel("day", 11, 17)
    private fun mixOf(c: Carousel, i: Int, date: String = today) =
        "$date-${c.selectorFor(i).id}".let { Mix(it, c.selectorFor(i).id, date, "t", "d", "q", emptyList(), 1L, false) }

    @Test fun theMorningMixesStayWhenTheDayBegins() {
        val mixes = (0..2).map { mixOf(morning, it) }
        assertEquals(morning, CarouselChoice.earlier(day, listOf(morning, day), mixes, today))
    }

    @Test fun theNewOnesShowOnceMade() {
        val mixes = (0..2).map { mixOf(morning, it) } + mixOf(day, 0)
        assertNull(CarouselChoice.earlier(day, listOf(morning, day), mixes, today))
    }

    @Test fun theDaysFirstCarouselShowsAsItIs() {
        assertNull(CarouselChoice.earlier(day, listOf(morning, day), emptyList(), today))
        // Yesterday's morning doesn't hold today's day back.
        assertNull(CarouselChoice.earlier(day, listOf(morning, day), (0..2).map { mixOf(morning, it, "2026-10-25") }, today))
    }
}
