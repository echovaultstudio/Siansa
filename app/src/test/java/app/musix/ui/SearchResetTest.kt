package app.musix.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #189: tapping Search again starts a new search; coming from another tab keeps the old one. */
class SearchResetTest {
    @Test fun searchTappedAgainClears() = assertTrue(tabTapClearsSearch("search", "search"))

    @Test fun comingFromAnotherTabKeepsTheSearch() {
        assertFalse(tabTapClearsSearch("home", "search"))
        assertFalse(tabTapClearsSearch("library", "search"))
        assertFalse(tabTapClearsSearch(null, "search"))
    }

    @Test fun otherTabsNeverClearIt() {
        assertFalse(tabTapClearsSearch("search", "home"))
        assertFalse(tabTapClearsSearch("library", "library"))
    }

    @Test fun eachRequestIsHandledOnce() {
        val before = SearchReset.requests.value
        SearchReset.request()
        assertEquals(before + 1, SearchReset.requests.value)
        assertTrue(searchResetPending(SearchReset.requests.value, before))
        assertFalse(searchResetPending(SearchReset.requests.value, SearchReset.requests.value))
        // After the app was restarted the count starts again from 0: an old, higher one isn't a new request.
        assertFalse(searchResetPending(0, 3))
    }
}
