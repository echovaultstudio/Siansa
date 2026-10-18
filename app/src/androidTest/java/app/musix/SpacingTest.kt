package app.musix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.musix.data.AlbumCategory
import app.musix.playback.EqPreset
import app.musix.ui.CHIPS_GAP
import app.musix.ui.EqPresetRow
import app.musix.ui.MusixTheme
import app.musix.ui.ReleaseTypeRow
import app.musix.ui.SectionTitle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Even spacing of layout pieces, measured on a real screen. Add a check here for any visual change
 * that places things next to each other.
 */
@RunWith(AndroidJUnit4::class)
class SpacingTest {
    @get:Rule val compose = createComposeRule()

    private fun assertEven(what: String, above: Dp, below: Dp, tolerance: Dp = 3.dp) {
        assertTrue("$what: ${above.value} dp above but ${below.value} dp below", abs(above.value - below.value) <= tolerance.value)
    }

    /** Artist page: the release-type chips sit evenly between the last Popular song and the first heading. */
    @Test fun releaseTypeChipsAreEvenlySpaced() {
        compose.setContent {
            MusixTheme {
                Column {
                    // A song row: 60 dp, of which the last 6 dp are its own padding below the cover.
                    Box(Modifier.fillMaxWidth().height(60.dp).testTag("song"))
                    ReleaseTypeRow(listOf(AlbumCategory.ALBUM, AlbumCategory.SINGLE), null) { }
                    SectionTitle("Discography", top = CHIPS_GAP)
                }
            }
        }
        val song = compose.onNodeWithTag("song").getBoundsInRoot()
        // The chip's label can sit in the unmerged tree only, depending on how the chip merges it.
        val chip = compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Singles")), useUnmergedTree = true).getBoundsInRoot()
        val title = compose.onNodeWithText("Discography").getBoundsInRoot()
        assertEven("Release-type chips", chip.top - (song.bottom - 6.dp), title.top - chip.bottom)
    }

    /** Equaliser page: the preset chips sit evenly between the "Preset" heading and the line below them. */
    @Test fun equaliserPresetChipsAreEvenlySpaced() {
        compose.setContent {
            MusixTheme {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth().height(24.dp).testTag("heading"))
                    EqPresetRow(EqPreset.FLAT) { }
                    Box(Modifier.fillMaxWidth().height(1.dp).testTag("divider"))
                }
            }
        }
        val heading = compose.onNodeWithTag("heading").getBoundsInRoot()
        // The chip's label can sit in the unmerged tree only, depending on how the chip merges it.
        val chip = compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Flat")), useUnmergedTree = true).getBoundsInRoot()
        val divider = compose.onNodeWithTag("divider").getBoundsInRoot()
        assertEven("Equaliser preset chips", chip.top - heading.bottom, divider.top - chip.bottom)
    }

    /** Now playing's ⋮ menu: every row the same height, so the list reads evenly. */
    @Test fun menuRowsAreOneHeight() {
        assertEven("Menu rows", app.musix.ui.MENU_ROW_HEIGHT, 48.dp, tolerance = 0.dp)
    }

    /** Downloads: Pause sits on the same line as the totals, centred with them, not squeezed into the buttons. */
    @Test fun downloadPauseSitsWithTheTotals() {
        compose.setContent {
            MusixTheme {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Text("120 songs · 840 MB", modifier = Modifier.weight(1f))
                    app.musix.ui.DownloadControlForTest()
                }
            }
        }
        val totals = compose.onNodeWithText("120 songs · 840 MB").getBoundsInRoot()
        val pause = compose.onNodeWithText("Pause").getBoundsInRoot()
        val centreGap = ((pause.top + pause.bottom) - (totals.top + totals.bottom)) / 2
        assertTrue("Pause is ${centreGap.value} dp off the totals' line", abs(centreGap.value) <= 2f)
    }

    /** Source filter: a user's libraries sit as far below their name as they sit above the card's edge. */
    @Test fun sourceCardLibrariesAreEvenlySpaced() {
        compose.setContent { MusixTheme { app.musix.ui.SourceCardForTest() } }
        val name = compose.onNodeWithTag("source-name").getBoundsInRoot()
        // The chip merges its tick and name, so its text node is the whole chip.
        val chipBox = compose.onNodeWithText("albums").getBoundsInRoot()
        val card = compose.onNodeWithTag("source-card").getBoundsInRoot()
        assertEven("Source card libraries", chipBox.top - name.bottom, card.bottom - chipBox.bottom)
    }

    /** Home: the one settings button sits centred on the greeting's line (issue #152). */
    @Test fun homeButtonSitsOnTheGreetingLine() {
        compose.setContent {
            MusixTheme {
                app.musix.ui.HomeHeader("Good evening") { Box(Modifier.height(48.dp).fillMaxWidth(0.12f).testTag("button")) }
            }
        }
        val title = compose.onNodeWithText("Good evening").getBoundsInRoot()
        val button = compose.onNodeWithTag("button").getBoundsInRoot()
        // Centred on one line: as far past the greeting's top as past its bottom.
        assertEven("Home button against the greeting", title.top - button.top, button.bottom - title.bottom, tolerance = 2.dp)
    }

    /** Themes: the rows of colour swatches are one even gap apart (issue #148). */
    @Test fun themeSwatchRowsAreEvenlySpaced() {
        compose.setContent { MusixTheme { app.musix.ui.SwatchGridForTest() } }
        val first = compose.onNodeWithText("Musix").getBoundsInRoot()
        val second = compose.onNodeWithText("Sunset").getBoundsInRoot()
        val grid = compose.onNodeWithTag("swatches").getBoundsInRoot()
        assertEven("Swatch rows", second.top - first.bottom, app.musix.ui.SWATCH_GAP, tolerance = 1.dp)
        // The grid ends with the second row: no gap left below it.
        assertEven("Swatch grid end", grid.bottom - second.bottom, 0.dp, tolerance = 1.dp)
    }

    /** "Your colour" picker: hue slider, quick picks, colour code and previews are one even gap apart (issue #148). */
    @Test fun yourColourPickerIsEvenlySpaced() {
        compose.setContent { MusixTheme { app.musix.ui.YourColourPicker(0xFF0E9AA7.toInt(), onCancel = {}, onUse = {}) } }
        val hue = compose.onNodeWithTag("hue").getBoundsInRoot()
        val quick = compose.onNodeWithTag("quick").getBoundsInRoot()
        val hex = compose.onNodeWithTag("hex").getBoundsInRoot()
        val previews = compose.onNodeWithTag("previews").getBoundsInRoot()
        val gap = app.musix.ui.PICKER_GAP
        assertEven("Hue slider to quick picks", quick.top - hue.bottom, gap, tolerance = 1.dp)
        assertEven("Quick picks to colour code", hex.top - quick.bottom, gap, tolerance = 1.dp)
        // The text field reserves a little room above it for its floating label.
        assertEven("Colour code to previews", previews.top - hex.bottom, gap, tolerance = 1.dp)
    }
}
