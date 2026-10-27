package app.siansa

import androidx.compose.foundation.layout.fillMaxHeight
import app.siansa.ui.LyricsSwitchRow
import app.siansa.ui.PathMoodPicker
import app.siansa.ui.Space
import app.siansa.ui.HubRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storage
import app.siansa.ui.StateButton
import app.siansa.ui.DayChips
import app.siansa.ui.HeroPage
import androidx.compose.ui.test.onNodeWithContentDescription
import app.siansa.ui.CollectionByline
import app.siansa.ui.ContinueButton
import app.siansa.ui.lastPlayedLine
import app.siansa.ui.FastScrollBar
import app.siansa.ui.SourceRow
import app.siansa.net.SourceKind
import app.siansa.ui.VibeSliders
import app.siansa.ui.ArrangeList
import app.siansa.mix.SoundDial
import app.siansa.mix.VibeTuning
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.padding
import app.siansa.ui.MoreChipsHint
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.onRoot
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.siansa.data.AlbumCategory
import app.siansa.playback.EqPreset
import app.siansa.ui.CHIPS_GAP
import app.siansa.ui.EqPresetRow
import app.siansa.ui.SiansaTheme
import app.siansa.ui.ReleaseTypeRow
import app.siansa.ui.SectionTitle
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
            SiansaTheme {
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

    /** Now playing's ⋮ menu: every row the same height, so the list reads evenly. */
    @Test fun menuRowsAreOneHeight() {
        assertEven("Menu rows", app.siansa.ui.MENU_ROW_HEIGHT, 48.dp, tolerance = 0.dp)
    }

    /** Downloads: Pause sits on the same line as the totals, centred with them, not squeezed into the buttons. */
    @Test fun downloadPauseSitsWithTheTotals() {
        compose.setContent {
            SiansaTheme {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Text("120 songs · 840 MB", modifier = Modifier.weight(1f))
                    app.siansa.ui.DownloadControlForTest()
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
        compose.setContent { SiansaTheme { app.siansa.ui.SourceCardForTest() } }
        val name = compose.onNodeWithTag("source-name").getBoundsInRoot()
        // The chip merges its tick and name, so its text node is the whole chip.
        val chipBox = compose.onNodeWithText("albums").getBoundsInRoot()
        val card = compose.onNodeWithTag("source-card").getBoundsInRoot()
        assertEven("Source card libraries", chipBox.top - name.bottom, card.bottom - chipBox.bottom)
    }

    /** Home: the one settings button sits centred on the greeting's line (issue #152). */
    @Test fun homeButtonSitsOnTheGreetingLine() {
        compose.setContent {
            SiansaTheme {
                app.siansa.ui.HomeHeader("Good evening") { Box(Modifier.height(48.dp).fillMaxWidth(0.12f).testTag("button")) }
            }
        }
        val title = compose.onNodeWithText("Good evening").getBoundsInRoot()
        val button = compose.onNodeWithTag("button").getBoundsInRoot()
        // Centred on one line: as far past the greeting's top as past its bottom.
        assertEven("Home button against the greeting", title.top - button.top, button.bottom - title.bottom, tolerance = 2.dp)
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

    /** Time machine: the year and the month chips are one even gap apart (issue #186). */
    @Test fun timeMachinePickerIsEvenlySpaced() {
        compose.setContent {
            SiansaTheme { app.siansa.ui.TimePicker(2025, 3, java.time.LocalDate.of(2026, 10, 18), onYear = {}, onMonth = {}) }
        }
        val year = compose.onNodeWithTag("year").getBoundsInRoot()
        val months = compose.onNodeWithTag("months").getBoundsInRoot()
        assertEven("Year to months", months.top - year.bottom, app.siansa.ui.TIME_MACHINE_GAP, tolerance = 1.dp)
    }

    /** Recent searches: the rows are one height, follow each other without gaps, and their words sit in the middle (issue #177). */
    @Test fun recentSearchRowsAreEvenlySpaced() {
        val searches = listOf("alt-j", "bonobo", "vance joy").map { app.siansa.data.RecentSearch(it) }
        compose.setContent { SiansaTheme { app.siansa.ui.RecentSearchList(searches, onPick = {}, onRemove = {}, onClear = {}) } }
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
            SiansaTheme {
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

    /** Album page (issue #228): "Find similar albums" sits on the line of Shuffle, the same gap from it as Shuffle from Play. */
    @Test fun findSimilarSitsBesideShuffle() {
        compose.setContent {
            SiansaTheme {
                app.siansa.ui.ActionButtons(enabled = true, onQueue = {}, download = {}, onSimilar = {}, onShuffle = {}, onPlay = {})
            }
        }
        val similar = compose.onNodeWithTag("findSimilarAlbums").getBoundsInRoot()
        val shuffle = compose.onNodeWithTag("shuffleAll").getBoundsInRoot()
        assertEven("Find similar and Shuffle, vertical centre", (similar.top + similar.bottom) / 2, (shuffle.top + shuffle.bottom) / 2, 1.dp)
        assertTrue("Find similar is left of Shuffle and doesn't overlap it", similar.right <= shuffle.left + 0.5.dp)
        // Icon buttons keep 4 dp of touch room on each side of their 40 dp, so neighbours sit 8 dp apart.
        assertTrue("Find similar right next to Shuffle (${(shuffle.left - similar.right).value} dp)", shuffle.left - similar.right <= app.siansa.ui.Space.Small + 0.5.dp)
    }

    /** Mix pages (beta #19): "Save as playlist" sits on the line of "Add all to queue", right next to it. */
    @Test fun saveAsPlaylistSitsBesideQueueAll() {
        compose.setContent {
            SiansaTheme {
                app.siansa.ui.ActionButtons(enabled = true, onQueue = {}, download = {}, onSimilar = null, onShuffle = {}, onPlay = {}, onSave = {})
            }
        }
        val queue = compose.onNodeWithTag("queueAll").getBoundsInRoot()
        val save = compose.onNodeWithTag("saveAsPlaylist").getBoundsInRoot()
        assertEven("Queue all and Save as playlist, vertical centre", (queue.top + queue.bottom) / 2, (save.top + save.bottom) / 2, 1.dp)
        assertTrue("Save as playlist is right of Queue all and doesn't overlap it", queue.right <= save.left + 0.5.dp)
        assertTrue("Save as playlist right next to Queue all (${(save.left - queue.right).value} dp)", save.left - queue.right <= app.siansa.ui.Space.Small + 0.5.dp)
    }

    /** Album page (issue #240): room under the big Play button before the first song, marked when it's playing. */
    @Test fun roomBelowThePlayButton() {
        compose.setContent {
            SiansaTheme {
                Column {
                    app.siansa.ui.ActionButtons(enabled = true, onQueue = {}, download = {}, onSimilar = {}, onShuffle = {}, onPlay = {})
                    Box(Modifier.fillMaxWidth().height(60.dp).testTag("firstSong"))
                }
            }
        }
        val play = compose.onNodeWithTag("bigPlay").getBoundsInRoot()
        val song = compose.onNodeWithTag("firstSong").getBoundsInRoot()
        assertTrue("Only ${(song.top - play.bottom).value} dp under the Play button", song.top - play.bottom >= app.siansa.ui.Space.BelowPlayButton - 0.5.dp)
    }

    /** Playlist page (beta #18): the turning cog sits on the title bar's line, the size of the ⋮ button beside it. */
    @Test fun checkingCogSitsLevelWithTheMenuButton() {
        compose.setContent {
            SiansaTheme {
                androidx.compose.foundation.layout.Row {
                    app.siansa.ui.CheckingCog()
                    androidx.compose.material3.IconButton(onClick = { }, modifier = Modifier.testTag("menu")) {
                        androidx.compose.material3.Icon(Icons.Rounded.FavoriteBorder, "Menu")
                    }
                }
            }
        }
        val cog = compose.onNodeWithTag("checkingPlaylist").getBoundsInRoot()
        val menu = compose.onNodeWithTag("menu").getBoundsInRoot()
        assertEven("Cog and menu, vertical centre", (cog.top + cog.bottom) / 2, (menu.top + menu.bottom) / 2, 1.dp)
    }

    /** Downloads (issue #235): the ✕ that stops all downloads sits on the progress line's centre. */
    @Test fun stopAllSitsOnTheProgressLine() {
        compose.setContent { SiansaTheme { app.siansa.ui.ProgressWithStop(0.4f) { } } }
        val bar = compose.onNodeWithTag("downloadProgress").getBoundsInRoot()
        val stop = compose.onNodeWithTag("stopAllDownloads").getBoundsInRoot()
        assertEven("Stop all and the progress line, vertical centre", (bar.top + bar.bottom) / 2, (stop.top + stop.bottom) / 2, 1.dp)
        assertTrue("Stop all is right of the line", stop.left >= bar.right - 0.5.dp)
    }

    /** Vibes panel (issue #247): the "Use the moment" switch rows are the same distance apart. */
    @Test fun situationSwitchesAreEvenlySpaced() {
        compose.setContent {
            SiansaTheme { app.siansa.ui.SituationSwitches(app.siansa.situation.SituationOptions(weather = true)) { } }
        }
        val names = listOf("Weather", "Movement", "Light", "Charging", "Listening device")
        val rows = names.map { compose.onNodeWithTag("situation-$it").getBoundsInRoot() }
        // The tag sits inside each row's padding, so the gaps are the padding: the same between every pair of rows.
        val gaps = rows.zipWithNext().map { (a, b) -> b.top - a.bottom }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between switch rows", a, b, 1.dp) }
    }

    /** Your sonic fingerprint's own page (issue #255): its stat tiles sit the same distance apart. */
    @Test fun fingerprintStatTilesAreEvenlySpaced() {
        val p = app.siansa.mix.FingerprintProfile("d", listOf("happy" to 0.6), emptyList(), 120, "minor", listOf("1", "2"))
        compose.setContent { SiansaTheme { app.siansa.ui.FingerprintStats(p) } }
        val tiles = compose.onAllNodesWithTag("fingerprint-stat").fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(4, tiles.size)
        val gaps = tiles.zipWithNext().map { (a, b) -> with(compose.density) { (b.left - a.right).toDp() } }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between fingerprint tiles", a, b, 1.dp) }
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

    /** Liked songs and playlists (issue #257): the sort chips sit the same distance apart. */
    @Test fun sortChipsAreEvenlySpaced() {
        compose.setContent { SiansaTheme { androidx.compose.foundation.layout.Column { app.siansa.ui.SortChips(app.siansa.data.ListSort.DEFAULT, "Recently liked") { } } } }
        val chips = listOf("DEFAULT", "PLAYED", "ADDED").map { compose.onNodeWithTag("sort-$it").getBoundsInRoot() }
        val gaps = chips.zipWithNext().map { (a, b) -> b.left - a.right }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between sort chips", a, b, 1.dp) }
    }

    /** Home carousel (issue #253): the dots under the cards sit the same distance apart. */
    @Test fun carouselDotsAreEvenlySpaced() {
        compose.setContent { SiansaTheme { app.siansa.ui.CarouselDots(count = 4, current = 1) } }
        val dots = compose.onAllNodesWithTag("carouselDot").fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(4, dots.size)
        val gaps = dots.zipWithNext().map { (a, b) -> with(compose.density) { (b.left - a.right).toDp() } }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between carousel dots", a, b, 1.dp) }
    }

    /** Settings › Scrobbling (issue #270): the share choices sit the same distance apart. */
    @Test fun scrobblePercentChipsAreEvenlySpaced() {
        compose.setContent { SiansaTheme { app.siansa.ui.ScrobblePercentChips(50) { } } }
        val chips = app.siansa.scrobble.SCROBBLE_PERCENTS.map { compose.onNodeWithTag("scrobblePercent-$it").getBoundsInRoot() }
        val gaps = chips.zipWithNext().map { (a, b) -> b.left - a.right }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between scrobble choices", a, b, 1.dp) }
    }

    /**
     * Pages that load their content (issue #488: search's "Albums for …" started under the title bar): once loaded,
     * the page starts below its title bar, and nothing reaches past the screen's left or right edge.
     */
    @Test fun loadedPagesStartBelowTheTitleBarAndStayOnScreen() {
        val albums = (1..9).map { app.siansa.data.Album("a$it", "Album $it", artist = "Artist") }
        val graph = (androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SiansaApp).graph
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(app.siansa.ui.LocalGraph provides graph) {
                SiansaTheme {
                    app.siansa.ui.Screen("Albums for test", nav = null) { padding ->
                        app.siansa.ui.LoadBox(app.siansa.ui.Load.Ok(albums), Modifier.padding(top = padding.calculateTopPadding())) { list ->
                            app.siansa.ui.AlbumsGridOrList(list, page = "spacing-loaded", onOpen = {}, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        val title = compose.onNodeWithText("Albums for test").getBoundsInRoot()
        val grid = compose.onNodeWithTag("albumsGridOrList").getBoundsInRoot()
        assertTrue("The page starts below the title bar: ${grid.top} vs ${title.bottom}", grid.top >= title.bottom)
        val screen = compose.onRoot().getBoundsInRoot()
        (1..3).forEach { i ->
            val t = compose.onNodeWithText("Album $i").getBoundsInRoot()
            assertTrue("Album $i on screen: ${t.left}..${t.right} of ${screen.left}..${screen.right}", t.left >= screen.left && t.right <= screen.right + 1.dp)
        }
    }

    /** Every grid of albums is three across, evenly spaced (issue #289). */
    @Test fun albumGridsAreThreeAcrossAndEven() {
        val albums = (1..6).map { app.siansa.data.Album("a$it", "Album $it", artist = "Artist") }
        // The tiles show covers, which need the app's graph.
        val graph = (androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SiansaApp).graph
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(app.siansa.ui.LocalGraph provides graph) {
                SiansaTheme { app.siansa.ui.AlbumsGridOrList(albums, page = "spacing-test", onOpen = {}) }
            }
        }
        val titles = (1..4).map { compose.onNodeWithText("Album $it").getBoundsInRoot() }
        // Albums 1–3 share a row; album 4 starts the next one, under album 1.
        assertEven("Same row", titles[0].top - titles[1].top, titles[1].top - titles[2].top, 1.dp)
        assertTrue("fourth starts a new row", titles[3].top > titles[0].top)
        assertEven("Even columns", titles[1].left - titles[0].left, titles[2].left - titles[1].left, 1.dp)
        assertEven("Fourth under first", titles[3].left - titles[0].left, 0.dp, 1.dp)
    }

    /** Long album grids (beta testers #24): the quick-scroll handle has room of its own, the columns stay even. */
    @Test fun longAlbumGridsMakeRoomForTheScrollHandle() {
        val albums = (1..40).map { app.siansa.data.Album("a$it", "Album $it", artist = "Artist $it") }
        val graph = (androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SiansaApp).graph
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(app.siansa.ui.LocalGraph provides graph) {
                SiansaTheme { app.siansa.ui.AlbumsGridOrList(albums, page = "spacing-test-long", onOpen = {}) }
            }
        }
        val bar = compose.onNodeWithTag("fastScroll").getBoundsInRoot()
        val titles = (1..3).map { compose.onNodeWithText("Album $it").getBoundsInRoot() }
        assertTrue("The third column ends before the handle: ${titles[2].right} vs ${bar.left}", titles[2].right <= bar.left + 1.dp)
        assertEven("Even columns", titles[1].left - titles[0].left, titles[2].left - titles[1].left, 1.dp)
    }

    /** Song rows (issue #309): a long title stops a gap short of the song's length, and lengths line up. */
    @Test fun songLengthKeepsAGapAfterALongTitle() {
        val graph = (androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SiansaApp).graph
        val long = app.siansa.data.Song("1", "ID1 (from Hospital30: Hugh Hardie) a very long title that must be cut off", artist = "ID", durationSec = 153)
        val short = app.siansa.data.Song("2", "Short", artist = "ID", durationSec = 89)
        compose.setContent {
            // Song rows need the app's graph, a navigation controller (their ⋮ menu) and the snackbar ("Added to the queue").
            androidx.compose.runtime.CompositionLocalProvider(
                app.siansa.ui.LocalGraph provides graph,
                app.siansa.ui.LocalNav provides androidx.navigation.compose.rememberNavController(),
                app.siansa.ui.LocalSnack provides androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() },
            ) {
                SiansaTheme { Column { app.siansa.ui.SongRow(long, onClick = {}, leading = "1", showCover = false); app.siansa.ui.SongRow(short, onClick = {}, leading = "2", showCover = false) } }
            }
        }
        // The row is one clickable item, so its parts are only found in the unmerged tree.
        val lengths = compose.onAllNodesWithTag("songLength", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(2, lengths.size)
        assertEven("Lengths line up", with(compose.density) { lengths[0].right.toDp() }, with(compose.density) { lengths[1].right.toDp() }, 1.dp)
        val title = compose.onNodeWithText(long.title, substring = true, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("A gap after the title: ${title.right} vs ${lengths[0].left}", title.right <= with(compose.density) { lengths[0].left.toDp() } - Space.Small + 1.dp)
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

    /** Album tiles (issue #338): the artist sits straight under the album name, a step under the cover. */
    @Test fun albumTileKeepsTheArtistUnderTheName() {
        val graph = (androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SiansaApp).graph
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(app.siansa.ui.LocalGraph provides graph) {
                SiansaTheme {
                    app.siansa.ui.AlbumTile("Spacing album", "Spacing artist", null, onClick = {}, modifier = Modifier.padding(Space.Page))
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
            SiansaTheme {
                Column {
                    app.siansa.ui.GradientTile("Count only", app.siansa.ui.Brand.AmberDeep to app.siansa.ui.Brand.VioletDeep, onClick = {}, caption = "25 tracks")
                    app.siansa.ui.GradientTile("With subtitle", app.siansa.ui.Brand.AmberDeep to app.siansa.ui.Brand.VioletDeep, onClick = {}, subtitle = "For you")
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
            SiansaTheme {
                Column {
                    Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                        app.siansa.ui.StatTile("2,241", "minutes listened", "37 hours", Modifier.weight(1f).fillMaxHeight(), app.siansa.ui.Brand.AmberDeep)
                        app.siansa.ui.StatTile("585", "plays", null, Modifier.weight(1f).fillMaxHeight(), app.siansa.ui.Brand.VioletDeep)
                    }
                    Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                        app.siansa.ui.StatTile("430", "artists", "95 new to you", Modifier.weight(1f).fillMaxHeight(), app.siansa.ui.Brand.PlumHigh)
                        app.siansa.ui.StatTile("471", "different songs", null, Modifier.weight(1f).fillMaxHeight(), app.siansa.ui.Brand.PlumHigh)
                    }
                }
            }
        }
        val tiles = compose.onAllNodesWithTag("statTile").fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(4, tiles.size)
        val heights = tiles.map { with(compose.density) { it.height.toDp() } }
        heights.drop(1).forEach { assertEven("Tile heights", heights[0], it, 1.dp) }
    }

    /** Dscvr's sliders (issue #339): the same gap between each slider and the next. */
    @Test fun vibeSlidersAreEvenlySpaced() {
        compose.setContent {
            SiansaTheme {
                VibeSliders(VibeTuning(sounds = listOf(SoundDial("piano", 2))), onChange = { })
            }
        }
        val energy = compose.onNodeWithTag("sliderEnergy").getBoundsInRoot()
        val tempo = compose.onNodeWithTag("sliderTempo").getBoundsInRoot()
        val familiar = compose.onNodeWithTag("sliderFamiliar").getBoundsInRoot()
        val sound = compose.onNodeWithTag("sliderSound").getBoundsInRoot()
        assertEven("Vibe sliders", tempo.top - energy.bottom, familiar.top - tempo.bottom, 1.dp)
        assertEven("Vibe sliders", familiar.top - tempo.bottom, sound.top - familiar.bottom, 1.dp)
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

    /** Home (issue #347): the dice and the filter button sit side by side, both centred on the greeting's line. */
    @Test fun diceSitsBesideTheFilterOnTheGreetingLine() {
        compose.setContent {
            SiansaTheme {
                app.siansa.ui.HomeHeader("Good evening") {
                    Box(Modifier.size(48.dp).testTag("dice"))
                    Box(Modifier.size(48.dp).testTag("filter"))
                }
            }
        }
        val title = compose.onNodeWithText("Good evening").getBoundsInRoot()
        val dice = compose.onNodeWithTag("dice").getBoundsInRoot()
        val filter = compose.onNodeWithTag("filter").getBoundsInRoot()
        assertEven("Dice against the greeting", title.top - dice.top, dice.bottom - title.bottom, tolerance = 2.dp)
        assertEven("Dice and filter on one line", dice.top, filter.top, 1.dp)
        assertTrue("The dice is left of the filter", dice.right <= filter.left)
    }

    /** Daily vibes (issue #348): the day chips sit one even gap apart. */
    @Test fun dayChipsAreEvenlySpaced() {
        compose.setContent { SiansaTheme { DayChips(setOf(5)) { } } }
        val chips = (1..4).map { compose.onNodeWithTag("day-$it").getBoundsInRoot() }
        val gaps = chips.zipWithNext { a, b -> b.left - a.right }
        gaps.forEach { assertEven("Day chips", gaps[0], it, 1.dp) }
    }

    /** Search (beta #21): a section's title and its "show all" arrow sit on one line. */
    @Test fun searchSectionArrowSitsOnTheTitlesLine() {
        compose.setContent { SiansaTheme { app.siansa.ui.SearchSectionTitle("Albums") { } } }
        val row = compose.onNodeWithTag("searchMore-Albums").getBoundsInRoot()
        val title = compose.onNodeWithText("Albums").getBoundsInRoot()
        val arrow = compose.onNodeWithContentDescription("Show all").getBoundsInRoot()
        assertEven("Title and arrow, vertical centre", (title.top + title.bottom) / 2, (arrow.top + arrow.bottom) / 2, 1.dp)
        assertTrue("Arrow inside the row", arrow.right <= row.right + 0.5.dp)
    }

    /** Artist page (issue #397): the heart sits on the title bar's line, level with the back arrow. */
    @Test fun artistHeartSitsLevelWithTheBackArrow() {
        compose.setContent {
            SiansaTheme {
                HeroPage(
                    title = "Burial",
                    heroHeight = 200.dp,
                    scrolledPx = 0f,
                    onBack = { },
                    color = androidx.compose.ui.graphics.Color.DarkGray,
                    image = { m -> Box(m) },
                    // The artist page's heart is an IconButton like the back arrow (FavouriteArtistButton).
                    actions = {
                        androidx.compose.material3.IconButton(onClick = { }, modifier = Modifier.testTag("heart")) {
                            androidx.compose.material3.Icon(Icons.Rounded.FavoriteBorder, "Heart")
                        }
                    },
                ) { }
            }
        }
        val back = compose.onNodeWithContentDescription("Back").getBoundsInRoot()
        val heart = compose.onNodeWithTag("heart").getBoundsInRoot()
        assertEven("Heart and back arrow on one line", back.top, heart.top, 1.dp)
        assertEven("Heart and back arrow the same size", back.bottom - back.top, heart.bottom - heart.top, 1.dp)
    }

    /** Album page (issue #367): Continue sits the same step under the details as the details under the artist. */
    @Test fun continueButtonSitsAStepUnderTheDetails() {
        compose.setContent {
            SiansaTheme {
                Column {
                    CollectionByline("Various Artists", null, null, "DJ-mix · 2024 · 7 songs")
                    ContinueButton("5") { }
                }
            }
        }
        val artist = compose.onNodeWithText("Various Artists").getBoundsInRoot()
        val details = compose.onNodeWithText("DJ-mix · 2024 · 7 songs").getBoundsInRoot()
        val button = compose.onNodeWithTag("continueFrom").getBoundsInRoot()
        // The button you see is 40 dp tall, centred in its taller touch area.
        val seenTop = button.top + ((button.bottom - button.top) - 40.dp) / 2
        assertEven("Continue under the details", details.top - artist.bottom, seenTop - details.bottom, 1.dp)
    }

    /** Album page (issue #367): the last-played line is drawn behind its row, so the title keeps its even room. */
    @Test fun lastPlayedLineKeepsTheRowEven() {
        compose.setContent {
            SiansaTheme {
                Box(Modifier.lastPlayedLine(androidx.compose.ui.graphics.Color.Red).testTag("row")) {
                    Text("Shadow Box", Modifier.padding(vertical = Space.Small))
                }
            }
        }
        compose.onNodeWithTag("row").assertExists()
        val row = compose.onNodeWithTag("row").getBoundsInRoot()
        val text = compose.onNodeWithText("Shadow Box").getBoundsInRoot()
        assertEven("Room above and below the title", text.top - row.top, row.bottom - text.bottom, 1.dp)
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

    /** Time machine (issue #496): the chosen month's chip is scrolled into view, even far to the right. */
    @Test fun chosenMonthChipIsOnScreen() {
        compose.setContent {
            SiansaTheme { app.siansa.ui.TimePicker(2025, 10, java.time.LocalDate.of(2026, 10, 18), onYear = {}, onMonth = {}) }
        }
        compose.waitForIdle()
        val screen = compose.onRoot().getBoundsInRoot()
        val october = compose.onNodeWithTag("month-10", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("October on screen: ${october.left}..${october.right} of ${screen.right}", october.left >= screen.left && october.right <= screen.right + 1.dp)
    }

    /** Your Library (issue #497): a › shows while chips are hidden off the right edge, centred in the row, and goes at the end. */
    @Test fun moreChipsHintShowsUntilTheEnd() {
        lateinit var scroll: androidx.compose.foundation.ScrollState
        compose.setContent {
            SiansaTheme {
                scroll = androidx.compose.foundation.rememberScrollState()
                Box(Modifier.width(320.dp).testTag("chipRow")) {
                    Row(
                        Modifier.horizontalScroll(scroll).padding(horizontal = Space.Page, vertical = Space.Small),
                        horizontalArrangement = Arrangement.spacedBy(Space.Small),
                    ) { listOf("Playlists", "Music", "Mixes", "Favourites", "Your listening").forEach { FilterChip(selected = false, onClick = {}, label = { Text(it) }) } }
                    MoreChipsHint(scroll)
                }
            }
        }
        val row = compose.onNodeWithTag("chipRow").getBoundsInRoot()
        val more = compose.onNodeWithTag("chipsMore").getBoundsInRoot()
        assertEven("› centred in the row", more.top - row.top, row.bottom - more.bottom, 1.dp)
        compose.runOnIdle { scroll.dispatchRawDelta(scroll.maxValue.toFloat()) }
        compose.waitForIdle()
        assertTrue("› gone at the end", compose.onAllNodesWithTag("chipsMore").fetchSemanticsNodes().isEmpty())
    }

    /**
     * Your Library (#521): chips stay at full size; on a narrow phone the › button sits in its own clear space at the
     * row's end, no chip under it, and the fade's clear part is as wide as the button plus its edge.
     */
    @Test fun moreButtonSitsInClearSpaceAfterTheChips() {
        lateinit var scroll: androidx.compose.foundation.ScrollState
        compose.setContent {
            SiansaTheme {
                scroll = androidx.compose.foundation.rememberScrollState()
                Box(Modifier.width(360.dp).testTag("fitRow")) {
                    Row(
                        Modifier.horizontalScroll(scroll).padding(horizontal = Space.Page, vertical = Space.Small),
                        horizontalArrangement = Arrangement.spacedBy(Space.Small),
                    ) { listOf("Playlists", "Music", "Mixes", "Favourites", "Your listening").forEach { app.siansa.ui.LibraryPill(it, active = false) { } } }
                    MoreChipsHint(scroll)
                }
            }
        }
        // The chips' own size: their bounds on screen are clipped, so a chip scrolled out of the row measures 0.
        val pills = compose.onAllNodesWithTag("libraryPill", useUnmergedTree = true).fetchSemanticsNodes().map { it.size }
        val density = compose.density.density
        fun dp(px: Float) = (px / density).dp
        // Full size: the text keeps its 14 sp height, so the chips are as tall as a lone full-size chip.
        val tall = pills.map { dp(it.height.toFloat()) }.distinct()
        assertTrue("One chip height: $tall", tall.size == 1)
        val more = compose.onNodeWithTag("chipsMore").getBoundsInRoot()
        val fade = compose.onNodeWithTag("chipsMoreFade", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("Button inside the fade's clear half", more.left >= fade.left + (fade.right - fade.left) * 0.45f)
        val row = compose.onNodeWithTag("fitRow").getBoundsInRoot()
        assertEven("Button centred in the row", more.top - row.top, row.bottom - more.bottom, 1.dp)
    }

    /** Days (issue #512): "All week" sits in the row of days with the same gap as between the days. */
    @Test fun allWeekChipIsEvenlySpacedWithTheDays() {
        compose.setContent {
            SiansaTheme { Box(Modifier.width(480.dp)) { app.siansa.ui.DayChips(setOf(6, 7), onAllWeek = { }) { } } }
        }
        val all = compose.onNodeWithTag("day-all").getBoundsInRoot()
        val mon = compose.onNodeWithTag("day-1").getBoundsInRoot()
        val tue = compose.onNodeWithTag("day-2").getBoundsInRoot()
        assertEven("Gaps between chips", mon.left - all.right, tue.left - mon.right, 1.dp)
        assertEven("Chip tops", all.top, mon.top, 1.dp)
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

    /**
     * A book's page (#550, styled like an album's): the cover's top lines up with the title's, a page's gap at the
     * left edge, and the words start a page's gap after the cover.
     */
    @Test fun bookPageCoverLinesUpWithItsWords() {
        val graph = (androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SiansaApp).graph
        val book = app.siansa.data.Book(id = "b1", title = "A long title", authors = listOf("An author"), narrators = (1..8).map { "Reader $it" })
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(app.siansa.ui.LocalGraph provides graph) {
                SiansaTheme {
                    Box(Modifier.width(360.dp).testTag("bookHeader")) { app.siansa.ui.BookPageHeader(book, Color.DarkGray, Color.Black) }
                }
            }
        }
        val header = compose.onNodeWithTag("bookHeader").getBoundsInRoot()
        val cover = compose.onNodeWithTag("bookCover", useUnmergedTree = true).getBoundsInRoot()
        val words = compose.onNodeWithTag("bookWords", useUnmergedTree = true).getBoundsInRoot()
        assertEven("Cover and words start at the same height", cover.top, words.top, 1.dp)
        assertEven("A page's gap at the left", cover.left - header.left, Space.Page, 1.dp)
        assertEven("A page's gap between cover and words", words.left - cover.right, Space.Page, 1.dp)
    }

    /**
     * The foot of an album's page (#558): the length line, the genres and the labels each a medium gap apart, the
     * page's gap at the left, and the tags a small gap below their heading.
     */
    @Test fun albumFootIsEvenlySpaced() {
        val graph = (androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SiansaApp).graph
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(app.siansa.ui.LocalGraph provides graph) {
                SiansaTheme {
                    Box(Modifier.width(360.dp).testTag("footBox")) {
                        app.siansa.ui.AlbumFootSection(
                            app.siansa.ui.AlbumFoot(listOf("Pop", "Folk"), listOf("Warner Music Japan"), "Deluxe", "16 songs · 1:05:35"),
                            androidx.navigation.compose.rememberNavController(),
                        )
                    }
                }
            }
        }
        val box = compose.onNodeWithTag("footBox").getBoundsInRoot()
        val length = compose.onNodeWithTag("albumLength", useUnmergedTree = true).getBoundsInRoot()
        val genres = compose.onNodeWithTag("albumGenres", useUnmergedTree = true).getBoundsInRoot()
        val labels = compose.onNodeWithTag("albumLabels", useUnmergedTree = true).getBoundsInRoot()
        assertEven("Page gap at the left", length.left - box.left, Space.Page, 1.dp)
        assertEven("Length to genres", genres.top - length.bottom, Space.Medium, 1.dp)
        assertEven("Genres to labels", labels.top - genres.bottom, Space.Medium, 1.dp)
    }

    /** Your Library (issue #374): the quick-scroll handle is centred in its bar and reaches both ends. */
    @Test fun fastScrollHandleIsCentredAndReachesTheEnds() {
        var at by mutableFloatStateOf(0f)
        compose.setContent {
            SiansaTheme {
                Box(Modifier.height(300.dp)) { FastScrollBar(fraction = at, labelAt = { null }, onScroll = { }) }
            }
        }
        val bar = compose.onNodeWithTag("fastScroll").getBoundsInRoot()
        var thumb = compose.onNodeWithTag("fastScrollThumb", useUnmergedTree = true).getBoundsInRoot()
        assertEven("Handle left and right", thumb.left - bar.left, bar.right - thumb.right, 1.dp)
        assertEven("Handle at the top", thumb.top, bar.top, 1.dp)
        at = 1f
        compose.waitForIdle()
        thumb = compose.onNodeWithTag("fastScrollThumb", useUnmergedTree = true).getBoundsInRoot()
        assertEven("Handle at the bottom", thumb.bottom, bar.bottom, 1.dp)
    }

    /** First run (issue #113): every source in the picker is one row height, the text centred against its icon. */
    @Test fun sourceRowsAreOneHeight() {
        compose.setContent { MusicSourceRows() }
        val rows = SourceKind.entries.map { compose.onNodeWithTag("source-${it.name}").getBoundsInRoot() }
        rows.forEach { assertEven("Source rows", rows[0].bottom - rows[0].top, it.bottom - it.top, 1.dp) }
        val gaps = rows.zipWithNext { a, b -> b.top - a.bottom }
        gaps.forEach { assertEven("Gap between source rows", gaps[0], it, 1.dp) }
    }

    @androidx.compose.runtime.Composable
    private fun MusicSourceRows() {
        SiansaTheme { Column { SourceKind.entries.forEach { SourceRow(it) { } } } }
    }
}
