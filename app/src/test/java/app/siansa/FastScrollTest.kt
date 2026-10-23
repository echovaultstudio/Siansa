package app.siansa

import app.siansa.ui.FastScroll
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FastScrollTest {
    private val zone = ZoneId.of("Europe/Helsinki")
    private fun at(y: Int, m: Int, d: Int) = ZonedDateTime.of(y, m, d, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun touchPicksAPlaceInTheWholeList() {
        assertEquals(0, FastScroll.indexAt(0f, 500))
        assertEquals(499, FastScroll.indexAt(1f, 500))
        assertEquals(250, FastScroll.indexAt(0.5f, 501))
        // Past either end of the bar stays in the list.
        assertEquals(0, FastScroll.indexAt(-0.2f, 500))
        assertEquals(499, FastScroll.indexAt(1.4f, 500))
        assertEquals(0, FastScroll.indexAt(0.7f, 0))
    }

    @Test fun handleReachesTheBottomOnceTheLastRowsShow() {
        assertEquals(0f, FastScroll.fractionOf(0, 100, 10), 0f)
        assertEquals(0.5f, FastScroll.fractionOf(45, 100, 10), 0.0001f)
        assertEquals(1f, FastScroll.fractionOf(90, 100, 10), 0f)
        // Everything fits on screen: the handle stays at the top.
        assertEquals(0f, FastScroll.fractionOf(0, 8, 10), 0f)
    }

    @Test fun datesShowAsTheirMonth() {
        val now = at(2026, 10, 23)
        assertEquals("This month", FastScroll.monthLabel(at(2026, 10, 2), "Not played", now, zone))
        assertEquals("Sep 2026", FastScroll.monthLabel(at(2026, 9, 30), "Not played", now, zone))
        assertEquals("Mar 2019", FastScroll.monthLabel(at(2019, 3, 1), "Not played", now, zone))
        assertEquals("Not played", FastScroll.monthLabel(0, "Not played", now, zone))
    }

    @Test fun playCountsReadNaturally() {
        assertEquals("Not played", FastScroll.playsLabel(0))
        assertEquals("1 play", FastScroll.playsLabel(1))
        assertEquals("214 plays", FastScroll.playsLabel(214))
    }
}
