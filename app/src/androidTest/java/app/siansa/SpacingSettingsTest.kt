package app.siansa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.siansa.net.SourceKind
import app.siansa.playback.EqPreset
import app.siansa.ui.ArrangeList
import app.siansa.ui.EqPresetRow
import app.siansa.ui.HubRow
import app.siansa.ui.SiansaTheme
import app.siansa.ui.SourceRow
import app.siansa.ui.Space
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Even spacing, measured on a real screen: Settings and its pages, Style, Themes and the first-run source picker. Shared setup in [SpacingCheck]. */
@RunWith(AndroidJUnit4::class)
class SpacingSettingsTest : SpacingCheck() {
    /** Equaliser page: the preset chips sit evenly between the "Preset" heading and the line below them. */
    @Test fun equaliserPresetChipsAreEvenlySpaced() {
        compose.setContent {
            SiansaTheme {
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

    /** Themes: the rows of colour swatches are one even gap apart (issue #148). */
    @Test fun themeSwatchRowsAreEvenlySpaced() {
        compose.setContent { SiansaTheme { app.siansa.ui.SwatchGridForTest() } }
        val first = compose.onNodeWithText("Siansa").getBoundsInRoot()
        val second = compose.onNodeWithText("Sunset").getBoundsInRoot()
        val grid = compose.onNodeWithTag("swatches").getBoundsInRoot()
        assertEven("Swatch rows", second.top - first.bottom, app.siansa.ui.SWATCH_GAP, tolerance = 1.dp)
        // The grid ends with the second row: no gap left below it.
        assertEven("Swatch grid end", grid.bottom - second.bottom, 0.dp, tolerance = 1.dp)
    }

    /** "Your colour" picker: hue slider, quick picks, colour code, opacity and previews are one even gap apart (issues #148, #181). */
    @Test fun yourColourPickerIsEvenlySpaced() {
        compose.setContent { SiansaTheme { app.siansa.ui.YourColourPicker(0xFF0E9AA7.toInt(), onCancel = {}, onUse = {}) } }
        val hue = compose.onNodeWithTag("hue").getBoundsInRoot()
        val quick = compose.onNodeWithTag("quick").getBoundsInRoot()
        val hex = compose.onNodeWithTag("hex").getBoundsInRoot()
        val opacity = compose.onNodeWithTag("opacity").getBoundsInRoot()
        val previews = compose.onNodeWithTag("previews").getBoundsInRoot()
        val gap = app.siansa.ui.PICKER_GAP
        assertEven("Hue slider to quick picks", quick.top - hue.bottom, gap, tolerance = 1.dp)
        assertEven("Quick picks to colour code", hex.top - quick.bottom, gap, tolerance = 1.dp)
        // The text field reserves a little room above it for its floating label.
        assertEven("Colour code to opacity", opacity.top - hex.bottom, gap, tolerance = 1.dp)
        assertEven("Opacity to previews", previews.top - opacity.bottom, gap, tolerance = 1.dp)
    }

    /** Crossfade and levelling: the source and crossfade chips sit evenly between their heading and the hint below (issue #60). */
    @Test fun crossfadeChipsAreEvenlySpaced() {
        compose.setContent {
            SiansaTheme {
                Column(verticalArrangement = Arrangement.spacedBy(app.siansa.ui.CROSSFADE_GAP)) {
                    Box(Modifier.fillMaxWidth().height(24.dp).testTag("heading"))
                    app.siansa.ui.LoudnessSourceRow(app.siansa.playback.LoudnessSource.TAGS) { }
                    Box(Modifier.fillMaxWidth().height(16.dp).testTag("hint"))
                    app.siansa.ui.CrossfadeModeRow(app.siansa.playback.CrossfadeMode.OFF) { }
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

    /** Beat-matched blends (issue #646): the Off / DJ radio / Everywhere chips sit evenly between their heading and hint. */
    @Test fun beatMatchChipsAreEvenlySpaced() {
        compose.setContent {
            SiansaTheme {
                Column(verticalArrangement = Arrangement.spacedBy(app.siansa.ui.CROSSFADE_GAP)) {
                    Box(Modifier.fillMaxWidth().height(24.dp).testTag("heading"))
                    app.siansa.ui.BeatMatchRow(app.siansa.playback.BeatMatchMode.DJ_RADIO) { }
                    Box(Modifier.fillMaxWidth().height(16.dp).testTag("hint"))
                }
            }
        }
        val heading = compose.onNodeWithTag("heading").getBoundsInRoot()
        val chips = compose.onNode(hasClickAction() and hasAnyDescendant(hasText("DJ radio")), useUnmergedTree = true).getBoundsInRoot()
        val hint = compose.onNodeWithTag("hint").getBoundsInRoot()
        assertEven("Beat-match chips", chips.top - heading.bottom, hint.top - chips.bottom)
    }

    /** Today's mix tuning (issue #776): a row of chips sits evenly between its label and the next label. */
    @Test fun todaysMixTuningChipsAreEvenlySpaced() {
        compose.setContent {
            SiansaTheme {
                Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    app.siansa.ui.TuningRow("Songs", 40, app.siansa.data.DailyMix.LENGTHS, { "$it" }, "dailyLength") { }
                    app.siansa.ui.TuningRow("Surprise", null, listOf(0, 15, 30), { "$it %" }, "dailySurprise") { }
                }
            }
        }
        val songs = compose.onNodeWithText("Songs").getBoundsInRoot()
        val chips = compose.onNodeWithTag("dailyLength").getBoundsInRoot()
        val surprise = compose.onNodeWithText("Surprise").getBoundsInRoot()
        assertEven("Tuning chips", chips.top - songs.bottom, surprise.top - chips.bottom)
    }

    /** A folded settings section (issue #776): the arrow is centred on the heading, open or folded. */
    @Test fun foldSectionArrowIsCentredOnItsHeading() {
        // The real default, not a forced one (issue #780): nothing remembered for this section yet.
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        app.siansa.ui.FoldMemory.setFolded(context, "radio", false)
        compose.setContent {
            SiansaTheme {
                Column {
                    app.siansa.ui.FoldSection("Song radio", "A little", tag = "radio") { Box(Modifier.fillMaxWidth().height(40.dp).testTag("radio-content")) }
                }
            }
        }
        val header = compose.onNodeWithTag("section-radio-header").getBoundsInRoot()
        // Open by default (issue #780): the body is there and the arrow says Fold; a tap folds it.
        val arrow = compose.onNode(hasContentDescription("Fold Song radio"), useUnmergedTree = true).getBoundsInRoot()
        assertEven("Fold arrow centred", arrow.top - header.top, header.bottom - arrow.bottom, 2.dp)
        compose.onNodeWithTag("radio-content").assertExists()
        compose.onNodeWithTag("section-radio-header").performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithTag("radio-content").fetchSemanticsNodes().isEmpty())
        // Folded once, it stays folded the next time the page opens.
        assertTrue(app.siansa.ui.FoldMemory.isFolded(context, "radio"))
        app.siansa.ui.FoldMemory.setFolded(context, "radio", false)
    }

    /** Downloads › Image cache (issue #780, moved from Storage): the size chips sit evenly between their label and Clear. */
    @Test fun imageCacheChipsAreEvenlySpaced() {
        val graph = (androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SiansaApp).graph
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(app.siansa.ui.LocalGraph provides graph) {
                SiansaTheme {
                    Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Medium)) { app.siansa.ui.ImageCacheSection() }
                }
            }
        }
        val label = compose.onNodeWithText("Maximum size").getBoundsInRoot()
        val chips = compose.onNode(hasClickAction() and hasAnyDescendant(hasText("No limit")), useUnmergedTree = true).getBoundsInRoot()
        val clear = compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Clear image cache")), useUnmergedTree = true).getBoundsInRoot()
        assertEven("Image cache chips", chips.top - label.bottom, clear.top - chips.bottom)
    }

    /** Beat-matched blends (issue #759): the 4 / 8 / 16 bars chips sit evenly between "Blend length" and their hint. */
    @Test fun blendLengthChipsAreEvenlySpaced() {
        compose.setContent {
            SiansaTheme {
                Column(verticalArrangement = Arrangement.spacedBy(app.siansa.ui.CROSSFADE_GAP)) {
                    app.siansa.ui.BeatMatchSection(app.siansa.playback.BeatMatchMode.DJ_RADIO, 8, true, { }, { }, { })
                }
            }
        }
        val label = compose.onNodeWithText("Blend length").getBoundsInRoot()
        val chips = compose.onNode(hasClickAction() and hasAnyDescendant(hasText("8 bars")), useUnmergedTree = true).getBoundsInRoot()
        val hint = compose.onNode(hasText("Apart from the crossfade length", substring = true)).getBoundsInRoot()
        assertEven("Blend length chips", chips.top - label.bottom, hint.top - chips.bottom)
    }

    /** Style › Phone's top bar (issue #681): its chips sit evenly between the heading and the hint. */
    @Test fun topBarChipsAreEvenlySpaced() {
        compose.setContent {
            SiansaTheme {
                app.siansa.ui.TopBarChoice(app.siansa.data.TopBarStyle.BLACK) { }
            }
        }
        val title = compose.onNodeWithText("Phone's top bar").getBoundsInRoot()
        val chips = compose.onNodeWithTag("topBar-HIDDEN").getBoundsInRoot()
        val hint = compose.onNodeWithText(app.siansa.data.TopBarStyle.BLACK.hint).getBoundsInRoot()
        assertEven("Top bar chips", chips.top - title.bottom, hint.top - chips.bottom)
    }

    /** Crossfade and levelling: the Off / Track / Album chips sit evenly between their heading and hint (issue #214). */
    @Test fun levellingChipsAreEvenlySpaced() {
        compose.setContent {
            SiansaTheme {
                Column(verticalArrangement = Arrangement.spacedBy(app.siansa.ui.CROSSFADE_GAP)) {
                    Box(Modifier.fillMaxWidth().height(24.dp).testTag("heading"))
                    app.siansa.ui.ReplayGainRow(app.siansa.data.ReplayGainMode.TRACK) { }
                    Box(Modifier.fillMaxWidth().height(16.dp).testTag("hint"))
                }
            }
        }
        val heading = compose.onNodeWithTag("heading").getBoundsInRoot()
        val chip = compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Album")), useUnmergedTree = true).getBoundsInRoot()
        val hint = compose.onNodeWithTag("hint").getBoundsInRoot()
        assertEven("Levelling chips", chip.top - heading.bottom, hint.top - chip.bottom)
    }

    /** Settings › Playback › Song radio (issue #256): the three drift chips sit the same distance apart. */
    @Test fun radioDriftChipsAreEvenlySpaced() {
        compose.setContent { SiansaTheme { app.siansa.ui.RadioDriftChips(app.siansa.mix.RadioDrift.SOME) { } } }
        val chips = app.siansa.mix.RadioDrift.entries.map { compose.onNodeWithTag("radioDrift-${it.name}").getBoundsInRoot() }
        val gaps = chips.zipWithNext().map { (a, b) -> b.left - a.right }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between drift chips", a, b, 1.dp) }
    }

    /** Settings › Playback › Keep playing (issue #337): Off, Steady and DJ sit the same distance apart. */
    @Test fun keepPlayingChipsAreEvenlySpaced() {
        compose.setContent { SiansaTheme { app.siansa.ui.ContinuousChips(app.siansa.mix.ContinuousMode.STEADY) { } } }
        val chips = app.siansa.mix.ContinuousMode.entries.map { compose.onNodeWithTag("continuousPlay-${it.name}").getBoundsInRoot() }
        val gaps = chips.zipWithNext().map { (a, b) -> b.left - a.right }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between keep playing chips", a, b, 1.dp) }
    }

    /** Settings › Playback › Load songs ahead (issue #254): the choices sit the same distance apart. */
    @Test fun preloadChipsAreEvenlySpaced() {
        compose.setContent { SiansaTheme { androidx.compose.foundation.layout.Column { app.siansa.ui.PreloadRow("On Wi-Fi", 2, "preload-wifi") { } } } }
        val chips = app.siansa.playback.Preload.CHOICES.map { compose.onNodeWithTag("preload-wifi-$it").getBoundsInRoot() }
        val gaps = chips.zipWithNext().map { (a, b) -> b.left - a.right }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between preload choices", a, b, 1.dp) }
    }

    /** Settings › Scrobbling (issue #270): the share choices sit the same distance apart. */
    @Test fun scrobblePercentChipsAreEvenlySpaced() {
        compose.setContent { SiansaTheme { app.siansa.ui.ScrobblePercentChips(50) { } } }
        val chips = app.siansa.scrobble.SCROBBLE_PERCENTS.map { compose.onNodeWithTag("scrobblePercent-$it").getBoundsInRoot() }
        val gaps = chips.zipWithNext().map { (a, b) -> b.left - a.right }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between scrobble choices", a, b, 1.dp) }
    }

    /** Themes › Accent colour (issues #313, #404): the colours sit in even columns, four to a row. */
    @Test fun selectionSwatchesAreEven() {
        compose.setContent { SiansaTheme { app.siansa.ui.SelectionSwatches(app.siansa.ui.SelectionColour.THEME, onPick = {}) } }
        val names = listOf("THEME", "AMBER", "VIOLET", "BLUE", "TEAL")
        val b = names.map { compose.onNodeWithTag("selection-$it").getBoundsInRoot() }
        assertEven("Even columns", b[1].left - b[0].left, b[2].left - b[1].left, 1.dp)
        assertEven("Fifth under first", b[4].left - b[0].left, 0.dp, 1.dp)
        assertTrue("fifth on the next row", b[4].top > b[0].top)
    }

    /** Style › Mini Player and Nav Bar (issue #372): the arrange rows sit evenly, one step apart. */
    @Test fun styleArrangeRowsAreEven() {
        compose.setContent {
            SiansaTheme {
                ArrangeList(
                    order = listOf("a", "b", "c", "d"),
                    shown = setOf("a", "b", "c"),
                    label = { it },
                    icon = { Icons.Rounded.Palette },
                    key = { it },
                    canHide = { true },
                    canShowMore = true,
                    onChange = { _, _ -> },
                )
            }
        }
        val rows = compose.onAllNodesWithTag("arrangeRow").fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(4, rows.size)
        val gaps = rows.zipWithNext { a, b -> with(compose.density) { (b.top - a.bottom).toDp() } }
        gaps.forEach { assertEven("Arrange rows", gaps[0], it, 1.dp) }
    }

    /** Settings (issue #375): every entry is just its name, so each row is the same height. */
    @Test fun settingsRowsAreOneHeight() {
        compose.setContent {
            SiansaTheme {
                Column {
                    HubRow(Icons.Rounded.Palette, "Style") { }
                    HubRow(Icons.Rounded.Storage, "Storage") { }
                }
            }
        }
        val rows = compose.onAllNodesWithTag("hubRow").fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(2, rows.size)
        assertEven("Settings rows", with(compose.density) { rows[0].height.toDp() }, with(compose.density) { rows[1].height.toDp() }, 1.dp)
    }

    /** Settings' groups (issue #780): each group's label sits the same distance above its first row, and its left edge
     *  lines up with the rows' page margin. */
    @Test fun settingsGroupLabelsSitEvenlyOverTheirRows() {
        compose.setContent {
            SiansaTheme {
                Column {
                    app.siansa.ui.HubGroup("Sources", first = true)
                    HubRow(Icons.Rounded.Palette, "Servers and services") { }
                    app.siansa.ui.HubGroup("Look")
                    HubRow(Icons.Rounded.Palette, "Style") { }
                }
            }
        }
        val labels = compose.onAllNodesWithTag("hubGroup").fetchSemanticsNodes().map { it.boundsInRoot }
        val rows = compose.onAllNodesWithTag("hubRow").fetchSemanticsNodes().map { it.boundsInRoot }
        fun dp(px: Float) = with(compose.density) { px.toDp() }
        assertEven("Label to its row", dp(rows[0].top - labels[0].bottom), dp(rows[1].top - labels[1].bottom), 1.dp)
        assertTrue("A group stands apart from the row above it", dp(labels[1].top - rows[0].bottom) > dp(rows[1].top - labels[1].bottom))
    }

    /** First run (issue #113): every source in the picker is one row height, the text centred against its icon. */
    @Test fun sourceRowsAreOneHeight() {
        compose.setContent { MusicSourceRows() }
        val rows = SourceKind.entries.map { compose.onNodeWithTag("source-${it.name}").getBoundsInRoot() }
        rows.forEach { assertEven("Source rows", rows[0].bottom - rows[0].top, it.bottom - it.top, 1.dp) }
        val gaps = rows.zipWithNext { a, b -> b.top - a.bottom }
        gaps.forEach { assertEven("Gap between source rows", gaps[0], it, 1.dp) }
    }

    /** Settings › Servers and services (issue #665): folded cards are one height, evenly spaced, the page margin from the edges. */
    @Test fun connectionCardsAreEven() {
        compose.setContent {
            SiansaTheme {
                Column(Modifier.padding(app.siansa.ui.Space.Page), verticalArrangement = Arrangement.spacedBy(app.siansa.ui.Space.Small)) {
                    listOf("a" to "Navidrome", "b" to "Audiobookshelf", "c" to "AudioMuse-AI").forEach { (key, title) ->
                        app.siansa.ui.FoldCard(androidx.compose.material.icons.Icons.Rounded.Settings, title, "Set up", expanded = false, onToggle = {}, tag = "fold-$key") { }
                    }
                }
            }
        }
        val screen = compose.onRoot().getBoundsInRoot()
        val cards = listOf("a", "b", "c").map { compose.onNodeWithTag("fold-$it").getBoundsInRoot() }
        cards.forEach { assertEven("Card height", cards[0].bottom - cards[0].top, it.bottom - it.top, 1.dp) }
        assertEven("Gap between cards", cards[1].top - cards[0].bottom, cards[2].top - cards[1].bottom, 1.dp)
        assertEven("Card margins", cards[0].left - screen.left, screen.right - cards[0].right, 1.dp)
    }

    @androidx.compose.runtime.Composable
    private fun MusicSourceRows() {
        SiansaTheme { Column { SourceKind.entries.forEach { SourceRow(it) { } } } }
    }
}
