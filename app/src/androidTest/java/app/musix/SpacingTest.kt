package app.musix

import app.musix.ui.LyricsSwitchRow
import app.musix.ui.PathMoodPicker
import app.musix.ui.SteeringChips
import app.musix.ui.Space
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.test.onAllNodesWithTag
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
import org.junit.Assert.assertEquals
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

    /** "Your colour" picker: hue slider, quick picks, colour code, opacity and previews are one even gap apart (issues #148, #181). */
    @Test fun yourColourPickerIsEvenlySpaced() {
        compose.setContent { MusixTheme { app.musix.ui.YourColourPicker(0xFF0E9AA7.toInt(), onCancel = {}, onUse = {}) } }
        val hue = compose.onNodeWithTag("hue").getBoundsInRoot()
        val quick = compose.onNodeWithTag("quick").getBoundsInRoot()
        val hex = compose.onNodeWithTag("hex").getBoundsInRoot()
        val opacity = compose.onNodeWithTag("opacity").getBoundsInRoot()
        val previews = compose.onNodeWithTag("previews").getBoundsInRoot()
        val gap = app.musix.ui.PICKER_GAP
        assertEven("Hue slider to quick picks", quick.top - hue.bottom, gap, tolerance = 1.dp)
        assertEven("Quick picks to colour code", hex.top - quick.bottom, gap, tolerance = 1.dp)
        // The text field reserves a little room above it for its floating label.
        assertEven("Colour code to opacity", opacity.top - hex.bottom, gap, tolerance = 1.dp)
        assertEven("Opacity to previews", previews.top - opacity.bottom, gap, tolerance = 1.dp)
    }

    /** Crossfade and levelling: the source and crossfade chips sit evenly between their heading and the hint below (issue #60). */
    @Test fun crossfadeChipsAreEvenlySpaced() {
        compose.setContent {
            MusixTheme {
                Column(verticalArrangement = Arrangement.spacedBy(app.musix.ui.CROSSFADE_GAP)) {
                    Box(Modifier.fillMaxWidth().height(24.dp).testTag("heading"))
                    app.musix.ui.LoudnessSourceRow(app.musix.playback.LoudnessSource.TAGS) { }
                    Box(Modifier.fillMaxWidth().height(16.dp).testTag("hint"))
                    app.musix.ui.CrossfadeModeRow(app.musix.playback.CrossfadeMode.OFF) { }
                    Box(Modifier.fillMaxWidth().height(16.dp).testTag("hint2"))
                }
            }
        }
        val heading = compose.onNodeWithTag("heading").getBoundsInRoot()
        val source = compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Song tags")), useUnmergedTree = true).getBoundsInRoot()
        val hint = compose.onNodeWithTag("hint").getBoundsInRoot()
        assertEven("Loudness source chips", source.top - heading.bottom, hint.top - source.bottom)
        val mode = compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Smart")), useUnmergedTree = true).getBoundsInRoot()
        val hint2 = compose.onNodeWithTag("hint2").getBoundsInRoot()
        assertEven("Crossfade chips", mode.top - hint.bottom, hint2.top - mode.bottom)
    }

    /** Time machine: the year and the month chips are one even gap apart (issue #186). */
    @Test fun timeMachinePickerIsEvenlySpaced() {
        compose.setContent {
            MusixTheme { app.musix.ui.TimePicker(2025, 3, java.time.LocalDate.of(2026, 10, 18), onYear = {}, onMonth = {}) }
        }
        val year = compose.onNodeWithTag("year").getBoundsInRoot()
        val months = compose.onNodeWithTag("months").getBoundsInRoot()
        assertEven("Year to months", months.top - year.bottom, app.musix.ui.TIME_MACHINE_GAP, tolerance = 1.dp)
    }

    /** Recent searches: the rows are one height, follow each other without gaps, and their words sit in the middle (issue #177). */
    @Test fun recentSearchRowsAreEvenlySpaced() {
        val searches = listOf("alt-j", "bonobo", "vance joy").map { app.musix.data.RecentSearch(it) }
        compose.setContent { MusixTheme { app.musix.ui.RecentSearchList(searches, onPick = {}, onRemove = {}, onClear = {}) } }
        val rows = compose.onAllNodesWithTag("recent", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        assertTrue("three rows: ${rows.size}", rows.size == 3)
        val density = compose.density.density
        fun dp(px: Float) = (px / density).dp
        assertEven("Row heights", dp(rows[0].height), dp(rows[1].height), tolerance = 1.dp)
        assertEven("Gap between rows", dp(rows[1].top - rows[0].bottom), 0.dp, tolerance = 1.dp)
        val text = compose.onNodeWithText("bonobo", useUnmergedTree = true).getBoundsInRoot()
        val row = rows[1]
        assertEven("Words in the row", text.top - dp(row.top), dp(row.bottom) - text.bottom)
    }

    /** Sound search: the "more / less" concept chips sit evenly under the Library / Sound chips (issue #208). */
    @Test fun steeringChipsAreEvenlySpaced() {
        compose.setContent {
            MusixTheme {
                Column {
                    Row(Modifier.padding(horizontal = Space.Page, vertical = Space.Small)) {
                        FilterChip(selected = true, onClick = {}, label = { Text("Sound") }, modifier = Modifier.testTag("modeChip"))
                    }
                    app.musix.ui.ConceptGroupRow(
                        listOf(app.musix.net.ConceptCategory("instrument", "Instrument", listOf("piano", "vocals"))),
                        "instrument",
                        mapOf("piano" to true),
                    ) { }
                    SteeringChips(listOf("piano", "vocals"), mapOf("piano" to true)) { }
                    Box(Modifier.fillMaxWidth().height(40.dp).testTag("results"))
                }
            }
        }
        val mode = compose.onNodeWithTag("modeChip").getBoundsInRoot()
        val groupChip = compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Instrument · 1")), useUnmergedTree = true).getBoundsInRoot()
        val chip = compose.onNode(hasClickAction() and hasAnyDescendant(hasText("More piano")), useUnmergedTree = true).getBoundsInRoot()
        val results = compose.onNodeWithTag("results").getBoundsInRoot()
        // The group chips and the concept chips are one block: the same gap above it as below it.
        assertEven("Steering block", groupChip.top - mode.bottom, results.top - chip.bottom)
    }

    /** Song path: the mood chips sit evenly between their label and the "Follow the lyrics" row (issue #208). */
    @Test fun pathMoodChipsAreEvenlySpaced() {
        compose.setContent {
            MusixTheme {
                Column {
                    PathMoodPicker(toHere = false) { }
                    LyricsSwitchRow(false) { }
                }
            }
        }
        val label = compose.onNodeWithTag("pathMoodLabel").getBoundsInRoot()
        val chip = compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Happy")), useUnmergedTree = true).getBoundsInRoot()
        val lyrics = compose.onNodeWithTag("lyricsTitle", useUnmergedTree = true).getBoundsInRoot()
        assertEven("Path mood chips", chip.top - label.bottom, lyrics.top - chip.bottom)
    }

    /** Crossfade and levelling: the Off / Track / Album chips sit evenly between their heading and hint (issue #214). */
    @Test fun levellingChipsAreEvenlySpaced() {
        compose.setContent {
            MusixTheme {
                Column(verticalArrangement = Arrangement.spacedBy(app.musix.ui.CROSSFADE_GAP)) {
                    Box(Modifier.fillMaxWidth().height(24.dp).testTag("heading"))
                    app.musix.ui.ReplayGainRow(app.musix.data.ReplayGainMode.TRACK) { }
                    Box(Modifier.fillMaxWidth().height(16.dp).testTag("hint"))
                }
            }
        }
        val heading = compose.onNodeWithTag("heading").getBoundsInRoot()
        val chip = compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Album")), useUnmergedTree = true).getBoundsInRoot()
        val hint = compose.onNodeWithTag("hint").getBoundsInRoot()
        assertEven("Levelling chips", chip.top - heading.bottom, hint.top - chip.bottom)
    }

    /** Album page (issue #228): "Find similar albums" sits on the line of Shuffle, the same gap from it as Shuffle from Play. */
    @Test fun findSimilarSitsBesideShuffle() {
        compose.setContent {
            MusixTheme {
                app.musix.ui.ActionButtons(enabled = true, onQueue = {}, download = {}, onSimilar = {}, onShuffle = {}, onPlay = {})
            }
        }
        val similar = compose.onNodeWithTag("findSimilarAlbums").getBoundsInRoot()
        val shuffle = compose.onNodeWithTag("shuffleAll").getBoundsInRoot()
        assertEven("Find similar and Shuffle, vertical centre", (similar.top + similar.bottom) / 2, (shuffle.top + shuffle.bottom) / 2, 1.dp)
        assertTrue("Find similar is left of Shuffle and doesn't overlap it", similar.right <= shuffle.left + 0.5.dp)
        // Icon buttons keep 4 dp of touch room on each side of their 40 dp, so neighbours sit 8 dp apart.
        assertTrue("Find similar right next to Shuffle (${(shuffle.left - similar.right).value} dp)", shuffle.left - similar.right <= app.musix.ui.Space.Small + 0.5.dp)
    }

    /** Album page (issue #240): room under the big Play button before the first song, marked when it's playing. */
    @Test fun roomBelowThePlayButton() {
        compose.setContent {
            MusixTheme {
                Column {
                    app.musix.ui.ActionButtons(enabled = true, onQueue = {}, download = {}, onSimilar = {}, onShuffle = {}, onPlay = {})
                    Box(Modifier.fillMaxWidth().height(60.dp).testTag("firstSong"))
                }
            }
        }
        val play = compose.onNodeWithTag("bigPlay").getBoundsInRoot()
        val song = compose.onNodeWithTag("firstSong").getBoundsInRoot()
        assertTrue("Only ${(song.top - play.bottom).value} dp under the Play button", song.top - play.bottom >= app.musix.ui.Space.BelowPlayButton - 0.5.dp)
    }

    /** Downloads (issue #235): the ✕ that stops all downloads sits on the progress line's centre. */
    @Test fun stopAllSitsOnTheProgressLine() {
        compose.setContent { MusixTheme { app.musix.ui.ProgressWithStop(0.4f) { } } }
        val bar = compose.onNodeWithTag("downloadProgress").getBoundsInRoot()
        val stop = compose.onNodeWithTag("stopAllDownloads").getBoundsInRoot()
        assertEven("Stop all and the progress line, vertical centre", (bar.top + bar.bottom) / 2, (stop.top + stop.bottom) / 2, 1.dp)
        assertTrue("Stop all is right of the line", stop.left >= bar.right - 0.5.dp)
    }

    /** Vibes panel (issue #247): the "Use the moment" switch rows are the same distance apart. */
    @Test fun situationSwitchesAreEvenlySpaced() {
        compose.setContent {
            MusixTheme { app.musix.ui.SituationSwitches(app.musix.situation.SituationOptions(weather = true)) { } }
        }
        val names = listOf("Weather", "Movement", "Light", "Charging", "Listening device")
        val rows = names.map { compose.onNodeWithTag("situation-$it").getBoundsInRoot() }
        // The tag sits inside each row's padding, so the gaps are the padding: the same between every pair of rows.
        val gaps = rows.zipWithNext().map { (a, b) -> b.top - a.bottom }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between switch rows", a, b, 1.dp) }
    }

    /** Your sonic fingerprint's own page (issue #255): its stat tiles sit the same distance apart. */
    @Test fun fingerprintStatTilesAreEvenlySpaced() {
        val p = app.musix.mix.FingerprintProfile("d", listOf("happy" to 0.6), emptyList(), 120, "minor", listOf("1", "2"))
        compose.setContent { MusixTheme { app.musix.ui.FingerprintStats(p) } }
        val tiles = compose.onAllNodesWithTag("fingerprint-stat").fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(4, tiles.size)
        val gaps = tiles.zipWithNext().map { (a, b) -> with(compose.density) { (b.left - a.right).toDp() } }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between fingerprint tiles", a, b, 1.dp) }
    }

    /** Settings › Playback › Song radio (issue #256): the three drift chips sit the same distance apart. */
    @Test fun radioDriftChipsAreEvenlySpaced() {
        compose.setContent { MusixTheme { app.musix.ui.RadioDriftChips(app.musix.mix.RadioDrift.SOME) { } } }
        val chips = app.musix.mix.RadioDrift.entries.map { compose.onNodeWithTag("radioDrift-${it.name}").getBoundsInRoot() }
        val gaps = chips.zipWithNext().map { (a, b) -> b.left - a.right }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between drift chips", a, b, 1.dp) }
    }

    /** Settings › Playback › Load songs ahead (issue #254): the choices sit the same distance apart. */
    @Test fun preloadChipsAreEvenlySpaced() {
        compose.setContent { MusixTheme { androidx.compose.foundation.layout.Column { app.musix.ui.PreloadRow("On Wi-Fi", 2, "preload-wifi") { } } } }
        val chips = app.musix.playback.Preload.CHOICES.map { compose.onNodeWithTag("preload-wifi-$it").getBoundsInRoot() }
        val gaps = chips.zipWithNext().map { (a, b) -> b.left - a.right }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between preload choices", a, b, 1.dp) }
    }

    /** Liked songs and playlists (issue #257): the sort chips sit the same distance apart. */
    @Test fun sortChipsAreEvenlySpaced() {
        compose.setContent { MusixTheme { androidx.compose.foundation.layout.Column { app.musix.ui.SortChips(app.musix.data.ListSort.DEFAULT, "Recently liked") { } } } }
        val chips = listOf("DEFAULT", "PLAYED", "ADDED").map { compose.onNodeWithTag("sort-$it").getBoundsInRoot() }
        val gaps = chips.zipWithNext().map { (a, b) -> b.left - a.right }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between sort chips", a, b, 1.dp) }
    }

    /** Home carousel (issue #253): the dots under the cards sit the same distance apart. */
    @Test fun carouselDotsAreEvenlySpaced() {
        compose.setContent { MusixTheme { app.musix.ui.CarouselDots(count = 4, current = 1) } }
        val dots = compose.onAllNodesWithTag("carouselDot").fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(4, dots.size)
        val gaps = dots.zipWithNext().map { (a, b) -> with(compose.density) { (b.left - a.right).toDp() } }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between carousel dots", a, b, 1.dp) }
    }
}
