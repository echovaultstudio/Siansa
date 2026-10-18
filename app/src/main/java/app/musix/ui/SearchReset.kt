package app.musix.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tapping Search while already in it (on the search page, or an artist or album opened from it) starts a new
 * search: back to the search page with an empty, focused box (issue #189). Coming to Search from another tab
 * keeps what was there.
 */
fun tabTapClearsSearch(currentTab: String?, tapped: String): Boolean = tapped == "search" && currentTab == "search"

/** Asks the search page to empty its box and focus it ([tabTapClearsSearch]). */
object SearchReset {
    private val _requests = MutableStateFlow(0)
    val requests: StateFlow<Int> = _requests
    fun request() { _requests.value++ }
}

/** Whether the search page should act on reset request [latest], having handled [handled] (issue #189). */
fun searchResetPending(latest: Int, handled: Int): Boolean = latest > handled
