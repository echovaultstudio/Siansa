package app.musix

import androidx.compose.foundation.layout.fillMaxHeight
import app.musix.ui.LyricsSwitchRow
import app.musix.ui.PathMoodPicker
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

    /** Settings › Playback › Keep playing (issue #337): Off, Steady and DJ sit the same distance apart. */
    @Test fun keepPlayingChipsAreEvenlySpaced() {
        compose.setContent { MusixTheme { app.musix.ui.ContinuousChips(app.musix.mix.ContinuousMode.STEADY) { } } }
        val chips = app.musix.mix.ContinuousMode.entries.map { compose.onNodeWithTag("continuousPlay-${it.name}").getBoundsInRoot() }
        val gaps = chips.zipWithNext().map { (a, b) -> b.left - a.right }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between keep playing chips", a, b, 1.dp) }
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

    /** Settings › Scrobbling (issue #270): the share choices sit the same distance apart. */
    @Test fun scrobblePercentChipsAreEvenlySpaced() {
        compose.setContent { MusixTheme { app.musix.ui.ScrobblePercentChips(50) { } } }
        val chips = app.musix.scrobble.SCROBBLE_PERCENTS.map { compose.onNodeWithTag("scrobblePercent-$it").getBoundsInRoot() }
        val gaps = chips.zipWithNext().map { (a, b) -> b.left - a.right }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between scrobble choices", a, b, 1.dp) }
    }

    /** Every grid of albums is three across, evenly spaced (issue #289). */
    @Test fun albumGridsAreThreeAcrossAndEven() {
        val albums = (1..6).map { app.musix.data.Album("a$it", "Album $it", artist = "Artist") }
        // The tiles show covers, which need the app's graph.
        val graph = (androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MusixApp).graph
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(app.musix.ui.LocalGraph provides graph) {
                MusixTheme { app.musix.ui.AlbumsGridOrList(albums, page = "spacing-test", onOpen = {}) }
            }
        }
        val titles = (1..4).map { compose.onNodeWithText("Album $it").getBoundsInRoot() }
        // Albums 1–3 share a row; album 4 starts the next one, under album 1.
        assertEven("Same row", titles[0].top - titles[1].top, titles[1].top - titles[2].top, 1.dp)
        assertTrue("fourth starts a new row", titles[3].top > titles[0].top)
        assertEven("Even columns", titles[1].left - titles[0].left, titles[2].left - titles[1].left, 1.dp)
        assertEven("Fourth under first", titles[3].left - titles[0].left, 0.dp, 1.dp)
    }

    /** Song rows (issue #309): a long title stops a gap short of the song's length, and lengths line up. */
    @Test fun songLengthKeepsAGapAfterALongTitle() {
        val graph = (androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MusixApp).graph
        val long = app.musix.data.Song("1", "ID1 (from Hospital30: Hugh Hardie) a very long title that must be cut off", artist = "ID", durationSec = 153)
        val short = app.musix.data.Song("2", "Short", artist = "ID", durationSec = 89)
        compose.setContent {
            // Song rows need the app's graph, a navigation controller (their ⋮ menu) and the snackbar ("Added to the queue").
            androidx.compose.runtime.CompositionLocalProvider(
                app.musix.ui.LocalGraph provides graph,
                app.musix.ui.LocalNav provides androidx.navigation.compose.rememberNavController(),
                app.musix.ui.LocalSnack provides androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() },
            ) {
                MusixTheme { Column { app.musix.ui.SongRow(long, onClick = {}, leading = "1", showCover = false); app.musix.ui.SongRow(short, onClick = {}, leading = "2", showCover = false) } }
            }
        }
        // The row is one clickable item, so its parts are only found in the unmerged tree.
        val lengths = compose.onAllNodesWithTag("songLength", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(2, lengths.size)
        assertEven("Lengths line up", with(compose.density) { lengths[0].right.toDp() }, with(compose.density) { lengths[1].right.toDp() }, 1.dp)
        val title = compose.onNodeWithText(long.title, substring = true, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("A gap after the title: ${title.right} vs ${lengths[0].left}", title.right <= with(compose.density) { lengths[0].left.toDp() } - Space.Small + 1.dp)
    }

    /** Themes › Selected items (issue #313): the colours sit in even columns, four to a row. */
    @Test fun selectionSwatchesAreEven() {
        compose.setContent { MusixTheme { app.musix.ui.SelectionSwatches(app.musix.ui.SelectionColour.THEME, onPick = {}) } }
        val names = listOf("THEME", "AMBER", "VIOLET", "BLUE", "TEAL")
        val b = names.map { compose.onNodeWithTag("selection-$it").getBoundsInRoot() }
        assertEven("Even columns", b[1].left - b[0].left, b[2].left - b[1].left, 1.dp)
        assertEven("Fifth under first", b[4].left - b[0].left, 0.dp, 1.dp)
        assertTrue("fifth on the next row", b[4].top > b[0].top)
    }

    /** Album tiles (issue #338): the artist sits straight under the album name, a step under the cover. */
    @Test fun albumTileKeepsTheArtistUnderTheName() {
        val graph = (androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MusixApp).graph
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(app.musix.ui.LocalGraph provides graph) {
                MusixTheme {
                    app.musix.ui.AlbumTile("Spacing album", "Spacing artist", null, onClick = {}, modifier = Modifier.padding(Space.Page))
                }
            }
        }
        val name = compose.onNodeWithText("Spacing album", useUnmergedTree = true).getBoundsInRoot()
        val artist = compose.onNodeWithText("Spacing artist", useUnmergedTree = true).getBoundsInRoot()
        val gap = artist.top - name.bottom
        assertTrue("Artist ${gap.value} dp under the name", gap.value >= -1f && gap.value <= Space.Tight.value)
    }

    /** Made for you tiles (issue #338): with only the track count, it sits the same step under the tile. */
    @Test fun gradientTileCaptionSitsAStepUnderTheTile() {
        compose.setContent {
            MusixTheme {
                Column {
                    app.musix.ui.GradientTile("Count only", app.musix.ui.Brand.AmberDeep to app.musix.ui.Brand.VioletDeep, onClick = {}, caption = "25 tracks")
                    app.musix.ui.GradientTile("With subtitle", app.musix.ui.Brand.AmberDeep to app.musix.ui.Brand.VioletDeep, onClick = {}, subtitle = "For you")
                }
            }
        }
        val tiles = compose.onAllNodesWithTag("gradientTileArt", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(2, tiles.size)
        val caption = compose.onNodeWithText("25 tracks", useUnmergedTree = true).getBoundsInRoot()
        val subtitle = compose.onNodeWithText("For you", useUnmergedTree = true).getBoundsInRoot()
        val captionGap = caption.top - with(compose.density) { tiles[0].bottom.toDp() }
        val subtitleGap = subtitle.top - with(compose.density) { tiles[1].bottom.toDp() }
        assertEven("Gap under the tile", captionGap, subtitleGap, 1.dp)
        assertEven("Gap under the tile", captionGap, Space.Small, 1.dp)
    }

    /** Your listening's stat tiles (issue #325): the same height with or without a line under the label. */
    @Test fun statTilesAreTheSameSize() {
        compose.setContent {
            MusixTheme {
                Column {
                    Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                        app.musix.ui.StatTile("2,241", "minutes listened", "37 hours", Modifier.weight(1f).fillMaxHeight(), app.musix.ui.Brand.AmberDeep)
                        app.musix.ui.StatTile("585", "plays", null, Modifier.weight(1f).fillMaxHeight(), app.musix.ui.Brand.VioletDeep)
                    }
                    Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                        app.musix.ui.StatTile("430", "artists", "95 new to you", Modifier.weight(1f).fillMaxHeight(), app.musix.ui.Brand.PlumHigh)
                        app.musix.ui.StatTile("471", "different songs", null, Modifier.weight(1f).fillMaxHeight(), app.musix.ui.Brand.PlumHigh)
                    }
                }
            }
        }
        val tiles = compose.onAllNodesWithTag("statTile").fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(4, tiles.size)
        val heights = tiles.map { with(compose.density) { it.height.toDp() } }
        heights.drop(1).forEach { assertEven("Tile heights", heights[0], it, 1.dp) }
    }
}
