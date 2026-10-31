package app.siansa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.siansa.ui.SiansaTheme
import app.siansa.ui.Space
import app.siansa.ui.StateButton
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Even spacing, measured on a real screen: Now playing, the queue's mix, playing on a speaker and the audiobook player. Shared setup in [SpacingCheck]. */
@RunWith(AndroidJUnit4::class)
class SpacingPlayerTest : SpacingCheck() {
    /** Now playing's ⋮ menu: every row the same height, so the list reads evenly. */
    @Test fun menuRowsAreOneHeight() {
        assertEven("Menu rows", app.siansa.ui.MENU_ROW_HEIGHT, 48.dp, tolerance = 0.dp)
    }

    /** Shape the mix (issue #649): the shape chips sit evenly between their title and the hint under them. */
    @Test fun mixShapeChipsAreEvenlySpaced() {
        compose.setContent {
            SiansaTheme {
                app.siansa.ui.MixShapeControls(app.siansa.mix.MixShape.STEADY, 20) { _, _ -> }
            }
        }
        val title = compose.onNodeWithText("Shape the mix").getBoundsInRoot()
        val chips = compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Euphoric")), useUnmergedTree = true).getBoundsInRoot()
        val hint = compose.onNodeWithText(app.siansa.mix.MixShape.STEADY.hint).getBoundsInRoot()
        assertEven("Mix shape chips", chips.top - title.bottom, hint.top - chips.bottom)
    }

    /** Shape the mix: "Surprises" stays on one line beside its slider (issue #677), as tall as one line of text. */
    @Test fun mixSurpriseLabelIsOneLine() {
        compose.setContent {
            SiansaTheme {
                androidx.compose.foundation.layout.Box(Modifier.width(360.dp)) {
                    app.siansa.ui.MixShapeControls(app.siansa.mix.MixShape.STEADY, 50) { _, _ -> }
                }
            }
        }
        val label = compose.onNodeWithTag("mixSurpriseLabel").getBoundsInRoot()
        val value = compose.onNodeWithText("Some").getBoundsInRoot()
        val labelHeight = label.bottom - label.top
        assertTrue("\"Surprises\" wraps: $labelHeight tall", labelHeight < (value.bottom - value.top) * 1.6f)
        val slider = compose.onNodeWithTag("mixSurprise").getBoundsInRoot()
        assertEven("Surprise label and slider centres", (label.top + label.bottom) / 2, (slider.top + slider.bottom) / 2)
    }

    /** Now playing's small header (issue #683): the fade under it starts right at its edge, across the whole width. */
    @Test fun playerMiniHeaderFadesFromItsEdge() {
        compose.setContent {
            SiansaTheme {
                app.siansa.ui.PlayerMiniHeader(null, isPlaying = false, progress = 0.5f, liked = false, onHeart = {}, onPlayPause = {}, onTop = {})
            }
        }
        val header = compose.onNodeWithTag("playerMiniHeader").getBoundsInRoot()
        val fade = compose.onNodeWithTag("playerMiniHeaderFade", useUnmergedTree = true).getBoundsInRoot()
        assertEven("Fade under the header: left edge and bottom", fade.left - header.left, header.bottom - fade.bottom)
        assertEven("Fade under the header: width", fade.right - fade.left, header.right - header.left)
    }

    /** Now playing (issue #370): an active button's dot sits centred under its icon, a small step above the edge. */
    @Test fun activeButtonDotIsCentred() {
        compose.setContent {
            SiansaTheme {
                StateButton(Icons.Rounded.Palette, "Repeat all", on = true, tag = "repeatButton") { }
            }
        }
        val button = compose.onNodeWithTag("repeatButton").getBoundsInRoot()
        val dot = compose.onNodeWithTag("repeatButton-on", useUnmergedTree = true).getBoundsInRoot()
        assertEven("Dot left and right", dot.left - button.left, button.right - dot.right, 1.dp)
        assertEven("Dot above the edge", button.bottom - dot.bottom, Space.Tight, 1.dp)
    }

    /** Play on (beta testers #23): a DLNA row with its settings cog is as tall as the others, the cog centred in it. */
    @Test fun castRowsWithACogKeepTheirHeight() {
        compose.setContent {
            SiansaTheme {
                Column {
                    Box(Modifier.testTag("plain")) { app.siansa.ui.TargetRow(Icons.Rounded.Palette, "Living Room", "Chromecast", selected = false) { } }
                    Box(Modifier.testTag("withCog")) { app.siansa.ui.TargetRow(Icons.Rounded.Palette, "JBL BAR 800MK2", "DLNA · JBL", selected = true, onSettings = { }) { } }
                }
            }
        }
        val plain = compose.onNodeWithTag("plain").getBoundsInRoot()
        val withCog = compose.onNodeWithTag("withCog").getBoundsInRoot()
        assertEven("Row heights", plain.bottom - plain.top, withCog.bottom - withCog.top, 1.dp)
        val cog = compose.onNodeWithTag("dlnaSettings", useUnmergedTree = true).getBoundsInRoot()
        assertEven("Room above and below the cog", cog.top - withCog.top, withCog.bottom - cog.bottom, 1.dp)
    }

    /**
     * The book player (issue #521, now in now playing's style, #542): the real controls row at a phone's width.
     * The skips sit evenly either side of play, the chapter buttons evenly outside them, all centred on one line.
     */
    @Test fun bookPlayerControlsAreEvenlySpaced() {
        val graph = (androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SiansaApp).graph
        val book = app.siansa.data.Book(id = "b1", title = "A book", durationSec = 3600, chapters = listOf(app.siansa.data.Chapter(0, "One", 0.0, 1800.0), app.siansa.data.Chapter(1, "Two", 1800.0, 3600.0)))
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(app.siansa.ui.LocalGraph provides graph) {
                SiansaTheme {
                    Box(Modifier.width(360.dp).padding(horizontal = Space.Section)) {
                        app.siansa.ui.BookControls(app.siansa.playback.BookPlayerState(book = book, positionSec = 10.0), backSec = 10, forwardSec = 30)
                    }
                }
            }
        }
        val prev = compose.onNodeWithTag("bookPrevChapter").getBoundsInRoot()
        val back = compose.onNodeWithTag("bookSkipBack").getBoundsInRoot()
        val play = compose.onNodeWithTag("bookPlayPause").getBoundsInRoot()
        val fwd = compose.onNodeWithTag("bookSkipForward").getBoundsInRoot()
        val next = compose.onNodeWithTag("bookNextChapter").getBoundsInRoot()
        assertEven("Gaps either side of play", play.left - back.right, fwd.left - play.right, 1.dp)
        assertEven("Gaps outside the skips", back.left - prev.right, next.left - fwd.right, 1.dp)
        listOf(prev, back, fwd, next).forEach { assertEven("Centred on play", (it.top + it.bottom) / 2 - (play.top + play.bottom) / 2, 0.dp, 1.dp) }
    }

}
