package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tapping Home on the Home page goes back to its top; coming from anywhere else keeps the old spot. */
class HomeScrollToTopTest {
    @Test fun homeTappedAgainScrollsUp() = assertTrue(tabTapScrollsHomeToTop("home", "home"))

    @Test fun comingBackFromAnAlbumKeepsTheSpot() {
        assertFalse(tabTapScrollsHomeToTop("album/{id}", "home"))
        assertFalse(tabTapScrollsHomeToTop("home/{section}", "home"))
    }

    @Test fun comingFromAnotherTabKeepsTheSpot() {
        assertFalse(tabTapScrollsHomeToTop("search", "home"))
        assertFalse(tabTapScrollsHomeToTop("library", "home"))
        assertFalse(tabTapScrollsHomeToTop(null, "home"))
    }

    @Test fun otherTabsNeverScrollHome() {
        assertFalse(tabTapScrollsHomeToTop("home", "search"))
        assertFalse(tabTapScrollsHomeToTop("library", "library"))
    }

    @Test fun eachRequestIsHandledOnce() {
        val before = HomeScrollToTop.requests.value
        HomeScrollToTop.request()
        assertEquals(before + 1, HomeScrollToTop.requests.value)
        assertTrue(homeScrollPending(HomeScrollToTop.requests.value, before))
        assertFalse(homeScrollPending(HomeScrollToTop.requests.value, HomeScrollToTop.requests.value))
        // After the app was restarted the count starts again from 0: an old, higher one isn't a new request.
        assertFalse(homeScrollPending(0, 3))
    }
}
