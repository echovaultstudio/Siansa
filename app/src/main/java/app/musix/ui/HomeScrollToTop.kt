package app.musix.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tapping Home while already in it (on the Home page, or an album or mix opened from it) goes back to the top of
 * the Home page. Coming to Home from another tab keeps the spot it was left at.
 */
fun tabTapScrollsHomeToTop(currentTab: String?, tapped: String): Boolean = tapped == "home" && currentTab == "home"

/** Asks the Home page to scroll back to the top ([tabTapScrollsHomeToTop]). */
object HomeScrollToTop {
    private val _requests = MutableStateFlow(0)
    val requests: StateFlow<Int> = _requests
    fun request() { _requests.value++ }
}

/** Whether the Home page should act on request [latest], having handled [handled]. */
fun homeScrollPending(latest: Int, handled: Int): Boolean = latest > handled
