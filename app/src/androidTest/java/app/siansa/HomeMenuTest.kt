package app.siansa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.siansa.ui.HomeMenuButton
import app.siansa.ui.HomeMenuInfo
import app.siansa.ui.HomeMenuItem
import app.siansa.ui.HomeMenuOverlay
import app.siansa.ui.HomeSettingsSheet
import app.siansa.ui.LocalGraph
import app.siansa.ui.PlayOnLine
import app.siansa.ui.SiansaTheme
import app.siansa.ui.rememberHomeMenuState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home's menu (issue #872): a tap opens it and a row opens its page; a press slid down onto a row opens that row as
 * the finger lets go (a glide), and the slide never scrolls Home.
 */
@RunWith(AndroidJUnit4::class)
class HomeMenuTest {
    @get:Rule val compose = createComposeRule()

    private val picked = mutableListOf<HomeMenuItem>()
    private var stopped = 0
    private lateinit var scroll: androidx.compose.foundation.ScrollState

    private fun home(casting: Boolean = false) {
        val graph = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SiansaApp).graph
        val info = HomeMenuInfo(
            playOn = if (casting) PlayOnLine("Playing on SHIELD", "Chromecast", true) else PlayOnLine("Play on…", "TV or speaker", false),
            sources = "All libraries",
            filterActive = false,
        )
        compose.setContent {
            CompositionLocalProvider(LocalGraph provides graph) {
                SiansaTheme {
                    val nav = rememberNavController()
                    val menu = rememberHomeMenuState()
                    var panel by remember { mutableStateOf<Boolean?>(null) }
                    scroll = rememberScrollState()
                    fun pick(item: HomeMenuItem) {
                        picked += item
                        when (item) {
                            HomeMenuItem.SOURCES -> panel = true
                            HomeMenuItem.ARRANGE -> panel = false
                            HomeMenuItem.PLAY_ON -> Unit
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        // Home's page: long enough to scroll, the menu button in its top line.
                        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text("Good morning", Modifier.weight(1f))
                                HomeMenuButton(menu, casting = casting, filterActive = false, onPick = ::pick)
                            }
                            Box(Modifier.fillMaxWidth().height(3000.dp))
                        }
                        HomeMenuOverlay(menu, info, onPick = ::pick, onStop = { stopped++ })
                    }
                    panel?.let { sources -> HomeSettingsSheet(nav, startOnSources = sources) { panel = null } }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun glidingDownOntoSourcesOpensSources() {
        home()
        val button = compose.onNodeWithTag("homeMenu")
        val origin = button.getBoundsInRoot()
        button.performTouchInput { down(center) }
        compose.waitForIdle()
        // The menu opens at once, under the finger.
        compose.onNodeWithTag("homeMenuPanel").assertIsDisplayed()
        val row = compose.onNodeWithTag("homeMenu-sources").getBoundsInRoot()
        val target = with(compose.density) {
            Offset(((row.left + row.right) / 2 - origin.left).toPx(), ((row.top + row.bottom) / 2 - origin.top).toPx())
        }
        button.performTouchInput {
            val from = center
            repeat(12) { i -> moveTo(from + (target - from) * ((i + 1) / 12f)) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Let go to open").assertIsDisplayed()
        button.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(listOf(HomeMenuItem.SOURCES), picked)
        // The Sources panel is open; the menu is closed, and Home didn't scroll with the finger.
        compose.onNodeWithText("Only downloaded music").assertExists()
        assertTrue("The menu closed", compose.onAllNodesWithTagCount("homeMenuPanel") == 0)
        assertEquals(0, scroll.value)
    }

    @Test fun glidingOffTheMenuClosesIt() {
        home()
        val button = compose.onNodeWithTag("homeMenu")
        button.performTouchInput { down(center) }
        compose.waitForIdle()
        // Left and a little up, away from the menu (which hangs below), past the touch slop.
        button.performTouchInput { repeat(10) { moveBy(Offset(-40f, -5f)) } }
        compose.waitForIdle()
        button.performTouchInput { up() }
        compose.waitForIdle()
        assertTrue(picked.isEmpty())
        assertTrue("The menu closed", compose.onAllNodesWithTagCount("homeMenuPanel") == 0)
        assertEquals(0, scroll.value)
    }

    @Test fun aTapOpensTheMenuAndItsRowsOpen() {
        home()
        compose.onNodeWithTag("homeMenu").performClick()
        compose.waitForIdle()
        // A tap leaves it open, with its three rows.
        compose.onNodeWithText("Play on…").assertIsDisplayed()
        compose.onNodeWithText("Sources").assertIsDisplayed()
        compose.onNodeWithText("Arrange Home").assertIsDisplayed()
        // The bubbles hang straight down from the button: each one's centre on the button's centre line.
        val button = compose.onNodeWithTag("homeMenu").getBoundsInRoot()
        for (tag in listOf("play_on", "sources", "arrange")) {
            val bubble = compose.onNodeWithTag("homeMenuBubble-$tag", useUnmergedTree = true).getBoundsInRoot()
            val off = ((bubble.left + bubble.right) / 2 - (button.left + button.right) / 2).value
            assertTrue("Bubble $tag is ${off} dp off the button's centre line", kotlin.math.abs(off) <= 0.5f)
        }
        compose.onNodeWithTag("homeMenu-arrange").performClick()
        compose.waitForIdle()
        assertEquals(listOf(HomeMenuItem.ARRANGE), picked)
        compose.onNodeWithTag("homeSpacing").assertExists()
    }

    @Test fun whileCastingTheButtonHasADotAndStopEndsIt() {
        home(casting = true)
        compose.onNodeWithTag("homeMenuCastDot", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("homeMenu").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Playing on SHIELD").assertIsDisplayed()
        compose.onNodeWithTag("homeMenuStop").performClick()
        compose.waitForIdle()
        assertEquals(1, stopped)
        // Stop doesn't open Play on.
        assertTrue(picked.isEmpty())
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
