package app.siansa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import app.siansa.ui.Space
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import app.siansa.data.HomeMenuItem
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
                            HomeMenuItem.PLAY_ON, HomeMenuItem.DICE -> Unit
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        // Home's page: long enough to scroll, the notices and the menu button at its top (issue #872).
                        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                            val open = app.siansa.ui.rememberHomeMenuOpen(menu)
                            app.siansa.ui.HomeTopBar(
                                listOf(app.siansa.mix.Notices.newAlbums(2, "a")!!), onOpen = {}, menuOpen = open,
                                menuBar = { app.siansa.ui.HomeMenuBar(menu, info, HomeMenuItem.default, open = open, onPick = ::pick) },
                            ) {
                                HomeMenuButton(menu, casting = casting, filterActive = false, onPick = ::pick)
                            }
                            Box(Modifier.fillMaxWidth().height(3000.dp))
                        }
                        HomeMenuOverlay(menu)
                    }
                    panel?.let { sources -> HomeSettingsSheet(nav, startOnSources = sources) { panel = null } }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun glidingAlongOntoSourcesOpensSources() {
        home()
        val button = compose.onNodeWithTag("homeMenu")
        val origin = button.getBoundsInRoot()
        button.performTouchInput { down(center) }
        compose.waitForIdle()
        // The menu opens at once, in the row the finger is on.
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
        // The place under the finger is lit: grown and filled.
        compose.onNodeWithTag("homeMenuChosenCircle", useUnmergedTree = true).assertExists()
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
        // Down and away from the row (the menu is in the row itself), past the touch slop.
        button.performTouchInput { repeat(10) { moveBy(Offset(-5f, 40f)) } }
        compose.waitForIdle()
        button.performTouchInput { up() }
        compose.waitForIdle()
        assertTrue(picked.isEmpty())
        assertTrue("The menu closed", compose.onAllNodesWithTagCount("homeMenuPanel") == 0)
        assertEquals(0, scroll.value)
    }

    @Test fun aTapOpensTheMenuInTheRowAndItsPlacesOpen() {
        home()
        compose.onNodeWithTag("homeMenu").performClick()
        compose.waitForIdle()
        // A tap leaves it open, with its four places, their names read out.
        for (id in listOf("dice", "play_on", "sources", "arrange")) compose.onNodeWithTag("homeMenu-$id").assertIsDisplayed()
        compose.onNodeWithContentDescription("Arrange Home").assertExists()
        // The pill is in the row: the chips' height and top, from the page's edge to the room before the button.
        val button = compose.onNodeWithTag("homeMenu").getBoundsInRoot()
        val panel = compose.onNodeWithTag("homeMenuPanel").getBoundsInRoot()
        val screen = compose.onRoot().getBoundsInRoot()
        assertTrue("The pill's top is the button's", kotlin.math.abs((panel.top - button.top).value) <= 1f)
        assertTrue("The pill's height is the button's", kotlin.math.abs(((panel.bottom - panel.top) - (button.bottom - button.top)).value) <= 1f)
        assertTrue("The pill starts at the page's edge", kotlin.math.abs((panel.left - screen.left - Space.Page).value) <= 1f)
        assertTrue("The pill ends ${(button.left - panel.right).value} dp before the button", kotlin.math.abs((button.left - panel.right - Space.Small).value) <= 1f)
        compose.onNodeWithTag("homeMenu-arrange").performClick()
        compose.waitForIdle()
        assertEquals(listOf(HomeMenuItem.ARRANGE), picked)
        compose.onNodeWithTag("homeSpacing").assertExists()
    }

    @Test fun whileCastingTheButtonHasADotAndPlayOnOpensItsUsualMenu() {
        home(casting = true)
        compose.onNodeWithTag("homeMenuCastDot", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("homeMenu").performClick()
        compose.waitForIdle()
        // The place says where it plays; Stop and the rest are in the TVs and speakers menu it opens.
        compose.onNodeWithContentDescription("Play on… Playing on SHIELD").assertExists()
        assertTrue("No Stop in the places", compose.onAllNodesWithTagCount("homeMenuStop") == 0)
        compose.onNodeWithTag("homeMenu-play_on").performClick()
        compose.waitForIdle()
        assertEquals(listOf(HomeMenuItem.PLAY_ON), picked)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
