package app.siansa.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import app.siansa.data.Song
import app.siansa.data.StarRatings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Star ratings on screen (issue #1043): the stars take taps, the switches in Settings › Player do what they say,
 * and the stars sit evenly where they show.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class StarsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun rated(tag: String) =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    @Test fun aTapRatesAndTheSameStarAgainClears() {
        var stars by mutableStateOf(0)
        val sent = mutableListOf<Int>()
        compose.setContent { MaterialTheme(colorScheme = playerColors()) { StarRow(stars) { sent += it; stars = it } } }
        assertEquals("Not rated", rated("stars"))
        compose.onNodeWithTag("stars-4").performClick()
        assertEquals("4 stars", rated("stars"))
        compose.onNodeWithTag("stars-2").performClick()
        assertEquals("2 stars", rated("stars"))
        compose.onNodeWithTag("stars-2").performClick()
        assertEquals("Not rated", rated("stars"))
        compose.onNodeWithTag("stars-5").performClick()
        compose.onNodeWithTag("stars-1").performClick()
        assertEquals(listOf(4, 2, 0, 5, 1), sent)
        assertEquals("1 star", rated("stars"))
    }

    /** Settings › Player › Star ratings, with what the switches save. */
    @Test fun theSwitchesTurnStarsOnAndPickTheirPlaces() {
        var stars by mutableStateOf(StarRatings())
        compose.setContent { SiansaTheme { Column(Modifier.padding(Space.Page)) { StarRatingSettings(stars) { stars = it } } } }
        // Off: only the main switch.
        compose.onNodeWithTag("starsSwitch").assertIsOff()
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasTestTag("starsUnderTitleSwitch")).fetchSemanticsNodes().size)
        compose.onNodeWithTag("starsSwitch").performClick()
        assertEquals(StarRatings(on = true, underTitle = false, inAbout = true), stars)
        compose.onNodeWithTag("starsSwitch").assertIsOn()
        compose.onNodeWithTag("starsUnderTitleSwitch").assertIsOff()
        compose.onNodeWithTag("starsInAboutSwitch").assertIsOn()
        // Both places.
        compose.onNodeWithTag("starsUnderTitleSwitch").performClick()
        assertTrue(stars.showsUnderTitle && stars.showsInAbout)
        compose.onNodeWithTag("starsUnderTitleSwitch").assertIsOn()
        // Only under the title.
        compose.onNodeWithTag("starsInAboutSwitch").performClick()
        assertEquals(StarRatings(on = true, underTitle = true, inAbout = false), stars)
        compose.onNodeWithTag("starsInAboutSwitch").assertIsOff()
        // The last place can't go: the stars move to the other one.
        compose.onNodeWithTag("starsUnderTitleSwitch").performClick()
        assertEquals(StarRatings(on = true, underTitle = false, inAbout = true), stars)
        compose.onNodeWithTag("starsUnderTitleSwitch").assertIsOff()
        compose.onNodeWithTag("starsInAboutSwitch").assertIsOn()
        // Off again: the places are kept for next time, and their switches go.
        compose.onNodeWithTag("starsSwitch").performClick()
        assertEquals(StarRatings(on = false, underTitle = false, inAbout = true), stars)
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasTestTag("starsInAboutSwitch")).fetchSemanticsNodes().size)
    }

    private val song = Song(id = "s1", title = "Harbour Lights", artist = "The Quiet Tide")

    /** Under the title: there only when asked for, the first star's left edge under the text's, and it takes taps. */
    @Test fun theStarsUnderTheTitleLineUpWithIt() {
        var shown by mutableStateOf(false)
        var stars by mutableStateOf(3)
        compose.setContent {
            MaterialTheme(colorScheme = playerColors()) {
                Column(Modifier.width(328.dp)) { TitleRow(song, liked = false, stars = stars.takeIf { shown }, onRate = { stars = it }, onArtist = {}) {} }
            }
        }
        assertEquals("no stars unless they're switched on", 0, compose.onAllNodes(androidx.compose.ui.test.hasTestTag("titleStars")).fetchSemanticsNodes().size)
        shown = true
        compose.waitForIdle()
        val artist = compose.onNodeWithText("The Quiet Tide").getBoundsInRoot()
        val first = compose.onNodeWithTag("titleStars-1").getBoundsInRoot()
        // The star's drawing sits in the middle of its touch room.
        val inset = (STAR_TOUCH - STAR_SIZE) / 2
        assertEquals("the first star under the text's left edge", artist.left.value, (first.left + inset).value, 0.6f)
        // A thin gap under the title row, and the heart stays beside the title and artist, not pulled down by the stars.
        val heart = compose.onNodeWithContentDescription("Like").getBoundsInRoot()
        assertTrue("the stars are under the artist", first.top >= artist.bottom + Space.Tight - 1.dp)
        assertTrue("the heart stays above the stars", heart.bottom <= first.top + 1.dp)
        assertEquals(STAR_TOUCH.value, first.height.value, 0.6f)
        compose.onNodeWithTag("titleStars-5").performClick()
        assertEquals(5, stars)
    }

    /** In About this song: the stars at the right, and the line under them with the same gap above and below. */
    @Test fun theRatingInAboutThisSongIsEvenlySpaced() {
        var stars by mutableStateOf(4)
        compose.setContent {
            MaterialTheme(colorScheme = playerColors()) {
                Column(Modifier.width(296.dp)) {
                    AboutRating(stars, line = true) { stars = it }
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(Space.Section).testTag("cells"))
                }
            }
        }
        val row = compose.onNodeWithTag("aboutRating").getBoundsInRoot()
        val line = compose.onNodeWithTag("aboutRatingLine").getBoundsInRoot()
        val cells = compose.onNodeWithTag("cells").getBoundsInRoot()
        assertEquals("the same gap above and below the line", (line.top - row.bottom).value, (cells.top - line.bottom).value, 0.6f)
        assertEquals(Space.Medium.value, (line.top - row.bottom).value, 0.6f)
        // The five stars end at the card's right edge, each with room for a finger.
        val last = compose.onNodeWithTag("aboutStars-5").getBoundsInRoot()
        assertEquals(row.right.value, last.right.value, 0.6f)
        assertEquals(STAR_TOUCH_BIG.value, last.width.value, 0.6f)
        compose.onNodeWithText("4 stars").assertExists()
        compose.onNodeWithTag("aboutStars-4").performClick()
        compose.onNodeWithText("Not rated").assertExists()
    }

    /**
     * File → Playing (issue #1038): one box, its two sides of one height with the arrow between them, the same gap on
     * both of its sides and the same room inside the box at its left and right.
     */
    @Test fun fileAndPlayingSitEvenlySideBySide() {
        var playing by mutableStateOf(app.siansa.playback.PlayingAs.of(null, null, 256, cached = false, wifi = false))
        compose.setContent {
            MaterialTheme(colorScheme = playerColors()) {
                Column(Modifier.width(296.dp)) { StreamPath(app.siansa.playback.PlayingAs.file("FLAC", null, 2116), playing) }
            }
        }
        val path = compose.onNodeWithTag("streamPath").getBoundsInRoot()
        val file = compose.onNodeWithTag("streamFile").getBoundsInRoot()
        val now = compose.onNodeWithTag("streamPlaying").getBoundsInRoot()
        assertEquals("the two sides are one height", file.height.value, now.height.value, 0.6f)
        // Their labels, and their first lines, on one line each.
        val labels = compose.onAllNodes(androidx.compose.ui.test.hasText("FILE") or androidx.compose.ui.test.hasText("PLAYING")).fetchSemanticsNodes().map { it.boundsInRoot.top }
        assertEquals(2, labels.size)
        assertEquals("FILE and PLAYING on one line", labels[0], labels[1], 1f)
        assertEquals("the first line", compose.onNodeWithText("FLAC").getBoundsInRoot().top.value, compose.onNodeWithText("Opus " + app.siansa.playback.PlayingAs.kbit(256)).getBoundsInRoot().top.value, 0.6f)
        // One box around both: the same room inside it at the left and the right, and above and below.
        assertEquals(Space.Page.value, (file.left - path.left).value, 0.6f)
        assertEquals(Space.Page.value, (path.right - now.right).value, 0.6f)
        assertEquals(Space.Small.value, (file.top - path.top).value, 0.6f)
        assertEquals(Space.Small.value, (path.bottom - file.bottom).value, 0.6f)
        // The arrow sits between them with the same gap on each side.
        val arrow = STREAM_ARROW_FOR_TESTS
        assertEquals((Space.Small * 2 + arrow).value, (now.left - file.right).value, 0.6f)
        assertTrue("Playing is the wider side", now.width > file.width)
        compose.onNodeWithText("Opus " + app.siansa.playback.PlayingAs.kbit(256)).assertExists()
        compose.onNodeWithText("Converted by your server · mobile data").assertExists()
        // The original: "Same as the file", and where it comes from.
        playing = app.siansa.playback.PlayingAs.of(null, null, 0, cached = true, wifi = true)
        compose.waitForIdle()
        compose.onNodeWithText("Same as the file").assertExists()
        compose.onNodeWithText("From the song cache on this phone").assertExists()
    }

    /** In a song's menu: "Rate" is a row like the others, and its stars rate without closing it. */
    @Test fun rateInTheMenuIsARowLikeTheOthers() {
        var stars by mutableStateOf(0)
        compose.setContent { SiansaDarkTheme { Column(Modifier.width(360.dp)) { RateRow(stars) { stars = it } } } }
        assertEquals(MENU_ROW_HEIGHT.value, compose.onNodeWithTag("rateRow").getBoundsInRoot().height.value, 0.6f)
        compose.onNodeWithTag("menuStars-3").performClick()
        assertEquals(3, stars)
    }
}
