package app.siansa

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.siansa.ui.HomePullToRefresh
import app.siansa.ui.SiansaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pulling Home down with a finger (issue #1359): a whole pull and a quick flick load it again with the logo turning
 * for its own moment, a short slow pull goes back up, a pull is taken again right after one, and a list that isn't at
 * its top just scrolls.
 */
@RunWith(AndroidJUnit4::class)
class HomePullTest {
    @get:Rule val compose = createComposeRule()

    private var loads = 0

    private fun page() {
        compose.setContent {
            SiansaTheme {
                HomePullToRefresh(onRefresh = { loads++ }) {
                    LazyColumn(Modifier.fillMaxSize().testTag("pullList")) {
                        items(40) { Text("Row $it", Modifier.fillMaxWidth().height(80.dp)) }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
    }

    /** A finger down the list by [dp], taking [ms]. */
    private fun drag(dp: Int, ms: Long) = compose.onNodeWithTag("pullList").performTouchInput {
        val from = Offset(centerX, top + 100.dp.toPx())
        swipe(from, from.copy(y = from.y + dp.dp.toPx()), ms)
    }

    private fun turning() = compose.onNodeWithTag("homePullTurning", useUnmergedTree = true)
    private fun resting() = compose.onNodeWithTag("homePullLogo", useUnmergedTree = true)

    @Test fun aWholePullLoadsHomeAndTheLogoTurnsForItsOwnMoment() {
        page()
        drag(dp = 220, ms = 500)
        compose.mainClock.advanceTimeBy(700)
        turning().assertExists()
        assertEquals("Loaded once", 1, loads)
        compose.mainClock.advanceTimeBy(4_000)
        resting().assertExists()
        turning().assertDoesNotExist()
        assertEquals("Still once", 1, loads)
    }

    @Test fun aQuickFlickLoadsHomeToo() {
        page()
        drag(dp = 90, ms = 60)
        compose.mainClock.advanceTimeBy(700)
        turning().assertExists()
        assertEquals(1, loads)
    }

    @Test fun aShortSlowPullGoesBackUp() {
        page()
        drag(dp = 50, ms = 1_500)
        compose.mainClock.advanceTimeBy(1_500)
        resting().assertExists()
        assertEquals(0, loads)
    }

    @Test fun aPullRightAfterOneIsTaken() {
        page()
        drag(dp = 220, ms = 500)
        // The moment the logo stops turning, with the page still on its way back up.
        compose.mainClock.advanceTimeBy(1_350)
        drag(dp = 220, ms = 500)
        compose.mainClock.advanceTimeBy(700)
        turning().assertExists()
        assertEquals("Each pull loaded", 2, loads)
    }

    @Test fun aListAwayFromItsTopJustScrolls() {
        page()
        drag(dp = -300, ms = 500)
        compose.mainClock.advanceTimeBy(2_000)
        drag(dp = 150, ms = 500)
        compose.mainClock.advanceTimeBy(700)
        resting().assertExists()
        assertEquals(0, loads)
    }
}
