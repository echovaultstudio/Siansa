package app.siansa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.siansa.ui.SiansaTheme
import app.siansa.ui.Space
import app.siansa.ui.StateButton
import org.junit.Assert.assertEquals
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

    /** A menu row whose label wraps (issue #778) grows to fit it, with the same gap above and below; a short one stays 48 dp. */
    @Test fun aWrappedMenuLabelFitsItsRow() {
        compose.setContent {
            SiansaTheme {
                Column(Modifier.width(280.dp)) {
                    app.siansa.ui.MenuRow(Icons.Rounded.Palette, "Short") { }
                    app.siansa.ui.MenuRow(Icons.Rounded.Palette, "Sleep at the end of the chapter, uncharacteristically", badge = "on") { }
                }
            }
        }
        val short = compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Short")), useUnmergedTree = true).getBoundsInRoot()
        assertEven("Short row", short.bottom - short.top, 48.dp, 1.dp)
        val row = compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Sleep", substring = true)), useUnmergedTree = true).getBoundsInRoot()
        val label = compose.onNodeWithText("Sleep", substring = true, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("The label (${label.top}..${label.bottom}) fits its row (${row.top}..${row.bottom})", label.top >= row.top && label.bottom <= row.bottom)
        assertEven("Wrapped label centred", label.top - row.top, row.bottom - label.bottom, 2.dp)
    }

    /**
     * The queue's swipes (issue #915): right to left takes the song out, the way a song is swiped into the queue, once
     * the bin has shaken; left to right moves it to play next.
     */
    @Test fun aQueuedSongIsRemovedBySwipingLeftAndPlaysNextBySwipingRight() {
        var removed = 0
        var next = 0
        compose.setContent {
            SiansaTheme {
                Box(Modifier.width(360.dp).testTag("queued")) {
                    app.siansa.ui.QueueSwipes(rowColour = app.siansa.ui.QUEUE_ROW, onRemove = { removed++ }, onPlayNext = { next++ }) { background ->
                        Box(Modifier.then(background).fillMaxWidth().height(56.dp))
                    }
                }
            }
        }
        compose.onNodeWithTag("queued").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals("A right swipe moves the song to play next", 1, next)
        assertEquals("A right swipe doesn't take it out", 0, removed)
        compose.onNodeWithTag("queued").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals("A left swipe takes the song out", 1, removed)
        assertEquals("A left swipe doesn't move it", 1, next)
    }

    /** Without play next (the song playing), a right swipe in the queue does nothing; a left one still takes it out. */
    @Test fun thePlayingSongOnlySwipesOut() {
        var removed = 0
        compose.setContent {
            SiansaTheme {
                Box(Modifier.width(360.dp).testTag("queued")) {
                    app.siansa.ui.QueueSwipes(rowColour = app.siansa.ui.QUEUE_ROW, onRemove = { removed++ }, onPlayNext = null) { background ->
                        Box(Modifier.then(background).fillMaxWidth().height(56.dp))
                    }
                }
            }
        }
        compose.onNodeWithTag("queued").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(0, removed)
        compose.onNodeWithTag("queued").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(1, removed)
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


    /** Song radio's choices (issue #916): two rows of the same height, each name centred in its row beside its icon. */
    @Test fun radioChoicesAreEvenRows() {
        compose.setContent { SiansaTheme { Box(Modifier.width(320.dp)) { app.siansa.ui.RadioModeChoices {} } } }
        val steady = compose.onNodeWithTag("radioMode-steady").getBoundsInRoot()
        val dj = compose.onNodeWithTag("radioMode-dj").getBoundsInRoot()
        assertEven("Row heights", steady.bottom - steady.top, dj.bottom - dj.top, 1.dp)
        for ((row, name) in listOf(steady to "Steady", dj to "DJ")) {
            val label = compose.onNodeWithText(name, useUnmergedTree = true).getBoundsInRoot()
            assertEven("$name above and below", label.top - row.top, row.bottom - label.bottom, 2.dp)
        }
    }

    /**
     * Sliding from the play button (issue #919): up runs up's choice, a way with Nothing on it does nothing, a tap still
     * plays and pauses, and pressing and holding still stops.
     */
    @Test fun playButtonSlidesTapsAndHolds() {
        val slid = mutableListOf<app.siansa.ui.PlaySlide>()
        var toggled = 0
        var stopped = 0
        compose.setContent {
            SiansaTheme {
                Box(Modifier.size(300.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    app.siansa.ui.PlayButton(
                        playing = true,
                        slides = app.siansa.ui.PlaySlides(left = app.siansa.ui.PlaySlide.NOTHING),
                        djMix = false,
                        onToggle = { toggled++ },
                        onStop = { stopped++ },
                        onSlide = { slid += it },
                    )
                }
            }
        }
        compose.onNodeWithTag("playButton", useUnmergedTree = true).performTouchInput { swipe(center, center + Offset(0f, -height.toFloat())) }
        compose.waitForIdle()
        assertEquals(listOf(app.siansa.ui.PlaySlide.DJ_MIX), slid)
        compose.onNodeWithTag("playButton", useUnmergedTree = true).performTouchInput { swipe(center, center + Offset(width.toFloat(), 0f)) }
        compose.waitForIdle()
        assertEquals(listOf(app.siansa.ui.PlaySlide.DJ_MIX, app.siansa.ui.PlaySlide.SONIC_MATCH), slid)
        compose.onNodeWithTag("playButton", useUnmergedTree = true).performTouchInput { swipe(center, center + Offset(-width.toFloat(), 0f)) }
        compose.waitForIdle()
        assertEquals("Nothing on left", 2, slid.size)
        assertEquals("No slide plays or pauses", 0, toggled)
        compose.onNodeWithTag("playButton", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(1, toggled)
        compose.onNodeWithTag("playButton", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(1, stopped)
        assertEquals("A hold doesn't play or pause", 1, toggled)
    }

    /** A slide's choice sits the same gap from the play button's edge, up and to the side (issue #919). */
    @Test fun slideChoicesAreEvenlyAway() {
        var way by androidx.compose.runtime.mutableStateOf(app.siansa.ui.SlideWay.UP)
        compose.setContent {
            SiansaTheme {
                Box(Modifier.size(300.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    app.siansa.ui.PlayButton(
                        playing = false, slides = app.siansa.ui.PlaySlides(), djMix = false,
                        onToggle = {}, onStop = {}, onSlide = {},
                        preview = app.siansa.ui.SlideState(way, armed = true),
                    )
                }
            }
        }
        val button = compose.onNodeWithTag("playButton", useUnmergedTree = true).getBoundsInRoot()
        val up = compose.onNodeWithTag("slideChoice-up", useUnmergedTree = true).getBoundsInRoot()
        val upGap = button.top - (up.top + up.bottom) / 2
        way = app.siansa.ui.SlideWay.LEFT
        compose.waitForIdle()
        val left = compose.onNodeWithTag("slideChoice-left", useUnmergedTree = true).getBoundsInRoot()
        val leftGap = button.left - (left.left + left.right) / 2
        assertEven("Up and left", upGap, leftGap, 1.dp)
    }

    /** A slide's name sits in the middle of the cover, out from under the thumb (siansa-dev#38). */
    @Test fun slideNameIsCentredInTheCover() {
        compose.setContent {
            SiansaTheme {
                Box(Modifier.size(300.dp)) {
                    app.siansa.ui.SlideCoverLabel(
                        app.siansa.ui.SlideLabel(app.siansa.ui.PlaySlide.DJ_MIX, app.siansa.ui.SlideWay.UP, armed = true),
                        Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.waitForIdle()
        val cover = compose.onNodeWithTag("slideLabel", useUnmergedTree = true).getBoundsInRoot()
        val name = compose.onNodeWithTag("slideName", useUnmergedTree = true).getBoundsInRoot()
        assertEven("Left and right of the name", name.left - cover.left, cover.right - name.right, 1.dp)
    }
}
