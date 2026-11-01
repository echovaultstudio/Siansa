package app.siansa

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.siansa.ui.SiansaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

    /** A Home section in the Play button look works the same way. */
    @Test fun aHomeSectionOnlyPlaysFromItsBadge() {
        var opened = 0
        var played = 0
        val items = (1..6).map { app.siansa.ui.HomeItem("a$it", "Album $it", "Artist", open = {}) }
        compose.setContent {
            SiansaTheme { Column { app.siansa.ui.HomeLookRow(app.siansa.data.HomeLook.BUTTON, "added", "Recently added", items, openAll = { opened++ }, play = { played++ }) } }
        }
        compose.onNodeWithText("Recently added").performClick()
        assertEquals("a tap on the title opens the section", 1 to 0, opened to played)
        compose.onNodeWithTag("homeLookButtonPlay").performClick()
        assertEquals("a tap on ▶ plays", 1 to 1, opened to played)
        assertTrue("no arrow to open it", compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Open Recently added")).fetchSemanticsNodes().isEmpty())
    }

    /** A Home section in Cover rows (issue #789): the cover plays an album that has a play, else opens the item; the row opens it. */
    @Test fun aHomeSectionInCoverRowsPlaysFromTheCover() {
        var opened = 0
        var played = 0
        var radioOpened = 0
        val items = listOf(
            app.siansa.ui.HomeItem("a1", "Album one", "Artist", open = { opened++ }, play = { played++ }, meta = "11 songs"),
            app.siansa.ui.HomeItem("r1", "Melancholy radio", open = { radioOpened++ }),
        )
        compose.setContent {
            SiansaTheme { Column { app.siansa.ui.HomeLookRow(app.siansa.data.HomeLook.COVER_ROWS, "recent", "Recently played", items, openAll = {}) } }
        }
        compose.onNodeWithText("Album one").performClick()
        assertEquals("a tap on the row opens the album", 1 to 0, opened to played)
        compose.onAllNodesWithTag("coverRowCover")[0].performClick()
        assertEquals("a tap on the cover plays it", 1 to 1, opened to played)
        // No play of its own: the radio's cover opens it, as the row does.
        compose.onAllNodesWithTag("coverRowCover")[1].performClick()
        assertEquals(1, radioOpened)
    }
}
