package app.siansa

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.siansa.ui.SiansaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** A play button card (issue #730): the card opens, only its ▶ plays, and there's no arrow. */
@RunWith(AndroidJUnit4::class)
class PlayButtonCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onlyThePlayBadgePlays() {
        var opened = 0
        var played = 0
        val card = app.siansa.mix.PinnedCard.new("Shuffle it").copy(look = app.siansa.mix.CardLook.BUTTON, favourites = true)
        val songs = (1..3).map { app.siansa.data.Song(id = "s$it", title = "Song $it", artist = "Artist") }
        compose.setContent {
            SiansaTheme { Column { app.siansa.ui.PinnedPlayButton(card, songs, open = { opened++ }, play = { _, _, _ -> played++ }) } }
        }
        compose.onNodeWithText("Shuffle it").performClick()
        assertEquals("a tap on the title opens the card", 1 to 0, opened to played)
        compose.onNodeWithTag("pinnedButtonPlay").performClick()
        assertEquals("a tap on ▶ plays", 1 to 1, opened to played)
        compose.onNodeWithTag("pinnedButtonPlay").assertExists()
        assertTrue("no arrow to open it", compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Open Shuffle it")).fetchSemanticsNodes().isEmpty())
    }

    @Test fun thePlayBadgeFillsTheCardsStart() {
        val card = app.siansa.mix.PinnedCard.new("Shuffle it").copy(look = app.siansa.mix.CardLook.BUTTON, favourites = true)
        val songs = (1..3).map { app.siansa.data.Song(id = "s$it", title = "Song $it", artist = "Artist") }
        compose.setContent { SiansaTheme { Column { app.siansa.ui.PinnedPlayButton(card, songs, open = {}, play = { _, _, _ -> }) } } }
        val cardBounds = compose.onNodeWithTag("pinnedButton").getBoundsInRoot()
        val badge = compose.onNodeWithTag("pinnedButtonPlay").getBoundsInRoot()
        assertTrue("the badge starts at the card's edge", abs((badge.left - cardBounds.left).value) <= 1f)
        assertTrue("the badge is the card's full height", abs(((badge.bottom - badge.top) - (cardBounds.bottom - cardBounds.top)).value) <= 1f)
        assertTrue(badge.bottom - badge.top >= 48.dp)
    }
}
