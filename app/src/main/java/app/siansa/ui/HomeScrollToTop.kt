package app.siansa.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tapping Home while on the Home page itself goes back to the top of it. From an album or mix opened from Home, or
 * from another tab, the tap only goes back to Home and keeps the spot it was left at.
 */
fun tabTapScrollsHomeToTop(currentRoute: String?, tapped: String): Boolean = tapped == "home" && currentRoute == "home"

/** Asks the Home page to scroll back to the top ([tabTapScrollsHomeToTop]). */
object HomeScrollToTop {
    private val _requests = MutableStateFlow(0)
    val requests: StateFlow<Int> = _requests
    fun request() { _requests.value++ }
}

/** Whether the Home page should act on request [latest], having handled [handled]. */
fun homeScrollPending(latest: Int, handled: Int): Boolean = latest > handled
