package app.siansa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Star ratings (issue #1043): off unless switched on, shown in one place at least, a tap on the rating takes it off. */
class StarRatingsTest {
    @Test fun offByDefaultAndInAboutThisSongWhenSwitchedOn() {
        val fresh = StarRatings()
        assertFalse(fresh.on)
        assertFalse(fresh.showsUnderTitle || fresh.showsInAbout)
        assertEquals("Off", fresh.summary)
        val on = fresh.copy(on = true)
        assertTrue(on.showsInAbout)
        assertFalse(on.showsUnderTitle)
        assertEquals("In About this song", on.summary)
    }

    @Test fun bothPlacesCanBeOn() {
        val both = StarRatings(on = true).withUnderTitle(true)
        assertTrue(both.showsUnderTitle && both.showsInAbout)
        assertEquals("Under the title and in About this song", both.summary)
        assertEquals("Under the title", both.withInAbout(false).summary)
    }

    @Test fun onePlaceAlwaysStays() {
        val about = StarRatings(on = true)
        // Taking the only place away moves the stars to the other.
        assertEquals(StarRatings(on = true, underTitle = true, inAbout = false), about.withInAbout(false))
        val title = about.withInAbout(false)
        assertEquals(StarRatings(on = true, underTitle = false, inAbout = true), title.withUnderTitle(false))
        // With both on, either can go.
        val both = about.withUnderTitle(true)
        assertEquals(StarRatings(on = true, underTitle = false, inAbout = true), both.withUnderTitle(false))
        assertEquals(StarRatings(on = true, underTitle = true, inAbout = false), both.withInAbout(false))
    }

    @Test fun switchedOffNothingShowsButThePlacesAreKept() {
        val off = StarRatings(on = true).withUnderTitle(true).copy(on = false)
        assertFalse(off.showsUnderTitle || off.showsInAbout)
        assertTrue(off.copy(on = true).showsUnderTitle)
    }

    @Test fun aTapRatesAndTheSameStarAgainClears() {
        assertEquals(4, StarRatings.tapped(0, 4))
        assertEquals(2, StarRatings.tapped(4, 2))
        assertEquals(0, StarRatings.tapped(4, 4))
        assertEquals(5, StarRatings.tapped(1, 9))
    }

    @Test fun theRatingInWords() {
        assertEquals(listOf("Not rated", "1 star", "2 stars", "5 stars"), listOf(0, 1, 2, 5).map(StarRatings::words))
    }
}
