package app.siansa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.siansa.mix.SoundDial
import app.siansa.mix.VibeTuning
import app.siansa.ui.DayChips
import app.siansa.ui.LyricsSwitchRow
import app.siansa.ui.PathMoodPicker
import app.siansa.ui.SiansaTheme
import app.siansa.ui.Space
import app.siansa.ui.VibeSliders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Even spacing, measured on a real screen: Home, its cards and rows, the source filter, mixes, vibes and discovery. Shared setup in [SpacingCheck]. */
@RunWith(AndroidJUnit4::class)
class SpacingHomeTest : SpacingCheck() {
    /** Notices (issue #784): the chips sit one section gap below what's above, the same gap as below them, and a small gap apart. */
    @Test fun noticeChipsSitEvenly() {
        compose.setContent {
            SiansaTheme {
                androidx.compose.runtime.CompositionLocalProvider(app.siansa.ui.LocalSectionGap provides Space.Page) {
                    Column {
                        Box(Modifier.fillMaxWidth().height(24.dp).testTag("above"))
                        app.siansa.ui.NoticeChips(
                            listOf(app.siansa.mix.Notices.carryOn("b", "Dune"), app.siansa.mix.Notices.newAlbums(2, "a")!!),
                            onOpen = {},
                        )
                        Box(Modifier.fillMaxWidth().padding(top = Space.Page).height(24.dp).testTag("below"))
                    }
                }
            }
        }
        val above = compose.onNodeWithTag("above").getBoundsInRoot()
        val below = compose.onNodeWithTag("below").getBoundsInRoot()
        val first = compose.onNodeWithTag("notice-carry_on").getBoundsInRoot()
        val second = compose.onNodeWithTag("notice-new_albums").getBoundsInRoot()
        val row = compose.onNodeWithTag("noticesRow").getBoundsInRoot()
        assertEven("Notices: above and below", row.top - above.bottom, below.top - row.bottom)
        assertEven("Notices: between chips", second.left - first.right, Space.Small, tolerance = 1.dp)
    }

    /** Home's notice chips are Your Library's pills (issue #794): the same height, so Home and the library match. */
    @Test fun noticeChipsMatchTheLibrarysPills() {
        compose.setContent {
            SiansaTheme {
                Column {
                    app.siansa.ui.LibraryPill("Books", active = false) {}
                    app.siansa.ui.NoticeChips(listOf(app.siansa.mix.Notices.newAlbums(2, "a")!!), onOpen = {})
                }
            }
        }
        val pill = compose.onNodeWithTag("libraryPill", useUnmergedTree = true).onParent().getBoundsInRoot()
        val notice = compose.onNodeWithTag("notice-new_albums").getBoundsInRoot()
        assertEven("Notice chip as tall as a library pill", notice.bottom - notice.top, pill.bottom - pill.top, 2.dp)
    }

    /** Arrange Home (issue #694): the spacing step's name stays on one line, even "Compact", with a larger font. */
    @Test fun homeSpacingLabelIsOneLine() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, fontScale = 1.3f),
            ) {
                SiansaTheme { Box(Modifier.width(360.dp)) { app.siansa.ui.SpacingRow(gap = 1) { } } }
            }
        }
        val label = compose.onNodeWithTag("homeSpacingLabel").getBoundsInRoot()
        val title = compose.onNodeWithText("Spacing").getBoundsInRoot()
        // "Spacing" is one line of a larger style: the label is no taller.
        assertTrue("\"Compact\" wraps: ${label.bottom - label.top} tall", label.bottom - label.top <= title.bottom - title.top)
        assertEven("Spacing label centred on the title's line", (label.top + label.bottom) / 2, (title.top + title.bottom) / 2)
    }

    /** Radios (issue #697): "Melancholy" on a tile stays one whole word, even with a larger font. */
    @Test fun tileTitleKeepsWholeWords() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, fontScale = 1.3f),
            ) {
                SiansaTheme { app.siansa.ui.GradientTile("Melancholy", Color(0xFF445566) to Color(0xFF223344), onClick = {}, size = 130.dp) }
            }
        }
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Melancholy", useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val box = compose.onNodeWithTag("gradientTileTitle", useUnmergedTree = true).getBoundsInRoot()
        // What was measured, so a failure says why (issue #738).
        val sizes = "lines=${layout.lineCount}, word ${layout.getLineRight(0)} px wide in ${layout.size.width} px, " +
            "font ${layout.layoutInput.style.fontSize}, box ${box.right - box.left} wide, ellipsis=${layout.isLineEllipsized(0)}, " +
            "too tall=${layout.didOverflowHeight}"
        // One whole line, no "…", and the word's last letter inside the text's width. (hasVisualOverflow also counts a
        // paragraph a fraction of a pixel wider than its box, with every letter showing: issue #738.)
        val fits = layout.lineCount == 1 && !layout.isLineEllipsized(0) && layout.getLineRight(0) <= layout.size.width && !layout.didOverflowHeight
        assertTrue("\"Melancholy\" doesn't fit on one line: $sizes", fits)
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

    /** Home carousel (issue #253): the dots under the cards sit the same distance apart. */
    @Test fun carouselDotsAreEvenlySpaced() {
        compose.setContent { SiansaTheme { app.siansa.ui.CarouselDots(count = 4, current = 1) } }
        val dots = compose.onAllNodesWithTag("carouselDot").fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(4, dots.size)
        val gaps = dots.zipWithNext().map { (a, b) -> with(compose.density) { (b.left - a.right).toDp() } }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between carousel dots", a, b, 1.dp) }
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

    /** Custom cards (issue #622): the banner sits a page margin from both edges of the screen. */
    @Test fun pinnedBannerHasEvenPageMargins() {
        val card = app.siansa.mix.PinnedCard.new("Late night").copy(look = app.siansa.mix.CardLook.BANNER, genre = "Jazz")
        val songs = (1..3).map { app.siansa.data.Song(id = "s$it", title = "Song $it", artist = "Artist") }
        compose.setContent { SiansaTheme { Column { app.siansa.ui.PinnedBanner(card, songs, open = {}, play = { _, _, _ -> }) } } }
        val screen = compose.onRoot().getBoundsInRoot()
        val banner = compose.onNodeWithTag("pinnedBanner").getBoundsInRoot()
        assertEven("Banner margins", banner.left - screen.left, screen.right - banner.right, 1.dp)
        assertEven("Banner left margin is the page margin", banner.left - screen.left, Space.Page, 1.dp)
    }

    /** Custom cards (issue #622): the play button card sits a page margin from both edges of the screen. */
    @Test fun pinnedPlayButtonHasEvenPageMargins() {
        val card = app.siansa.mix.PinnedCard.new("Shuffle it").copy(look = app.siansa.mix.CardLook.BUTTON, favourites = true)
        val songs = (1..3).map { app.siansa.data.Song(id = "s$it", title = "Song $it", artist = "Artist") }
        compose.setContent { SiansaTheme { Column { app.siansa.ui.PinnedPlayButton(card, songs, open = {}, play = { _, _, _ -> }) } } }
        val screen = compose.onRoot().getBoundsInRoot()
        val button = compose.onNodeWithTag("pinnedButton").getBoundsInRoot()
        assertEven("Play button margins", button.left - screen.left, screen.right - button.right, 1.dp)
        assertEven("Play button left margin is the page margin", button.left - screen.left, Space.Page, 1.dp)
    }

    /**
     * Play button cards (issue #730): the ▶ badge fills the card's start, top to bottom, and the title sits a step from
     * it, on a custom card and a Home section alike.
     */
    @Test fun playBadgeFillsTheCardsStart() {
        val card = app.siansa.mix.PinnedCard.new("Shuffle it").copy(look = app.siansa.mix.CardLook.BUTTON, favourites = true)
        val songs = (1..3).map { app.siansa.data.Song(id = "s$it", title = "Song $it", artist = "Artist") }
        val items = (1..6).map { app.siansa.ui.HomeItem("a$it", "Album $it", "Artist", open = {}) }
        compose.setContent {
            SiansaTheme {
                Column {
                    app.siansa.ui.PinnedPlayButton(card, songs, open = {}, play = { _, _, _ -> })
                    app.siansa.ui.HomeLookRow(app.siansa.data.HomeLook.BUTTON, "added", "Recently added", items, openAll = {}, play = {})
                }
            }
        }
        for ((cardTag, title) in listOf("pinnedButton" to "Shuffle it", "homeLookButton" to "Recently added")) {
            val box = compose.onNodeWithTag(cardTag).getBoundsInRoot()
            val badge = compose.onNodeWithTag(cardTag + "Play").getBoundsInRoot()
            val text = compose.onNode(hasText(title), useUnmergedTree = true).getBoundsInRoot()
            assertEven("$cardTag: the badge starts at the card's edge", badge.left, box.left, 1.dp)
            assertEven("$cardTag: the badge is the card's full height", badge.bottom - badge.top, box.bottom - box.top, 1.dp)
            assertEven("$cardTag: the badge's top and bottom", badge.top - box.top, box.bottom - badge.bottom, 1.dp)
            assertEven("$cardTag: the title sits a step from the badge", text.left - badge.right, Space.Medium, 1.dp)
            assertTrue("$cardTag: the badge is big enough to tap", badge.bottom - badge.top >= 48.dp)
        }
    }

    /** Home's rows in another look (issue #655): the banner and the play button card sit a page margin from both edges. */
    @Test fun homeLookCardsHaveEvenPageMargins() {
        val items = (1..6).map { app.siansa.ui.HomeItem("a$it", "Album $it", "Artist", open = {}) }
        compose.setContent {
            SiansaTheme {
                Column {
                    app.siansa.ui.HomeLookRow(app.siansa.data.HomeLook.BANNER, "recent", "Recently played", items, openAll = {})
                    app.siansa.ui.HomeLookRow(app.siansa.data.HomeLook.BUTTON, "added", "Recently added", items, openAll = {})
                }
            }
        }
        val screen = compose.onRoot().getBoundsInRoot()
        for ((label, tag) in listOf("Banner" to "homeLookBanner", "Play button" to "homeLookButton")) {
            val card = compose.onNodeWithTag(tag).getBoundsInRoot()
            assertEven("$label margins", card.left - screen.left, screen.right - card.right, 1.dp)
            assertEven("$label left margin is the page margin", card.left - screen.left, Space.Page, 1.dp)
        }
    }

    /** Home's rows as a list (issue #655): five rows of one height, evenly spaced. */
    @Test fun homeLookListRowsAreEven() {
        val items = (1..8).map { app.siansa.ui.HomeItem("a$it", "Album $it", "Artist", open = {}) }
        compose.setContent { SiansaTheme { Column { app.siansa.ui.HomeLookRow(app.siansa.data.HomeLook.LIST, "added", "Recently added", items, openAll = {}) } } }
        val rows = compose.onAllNodesWithTag("homeLookListRow").fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(5, rows.size)
        val density = compose.onRoot().fetchSemanticsNode().layoutInfo.density.density
        val heights = rows.map { ((it.bottom - it.top) / density).dp }
        heights.forEach { assertEven("List row height", heights[0], it, 1.dp) }
        val gaps = rows.zipWithNext { a, b -> ((b.top - a.bottom) / density).dp }
        gaps.forEach { assertEven("Gap between list rows", gaps[0], it, 1.dp) }
    }

    /** A custom card's rule (issue #663): its ways of comparing sit evenly between its name and its value. */
    @Test fun cardRuleChipsAreEvenlySpaced() {
        val rule = app.siansa.mix.CardRule(app.siansa.mix.RuleField.LABEL, app.siansa.mix.RuleOp.CONTAINS, "Ninja Tune")
        compose.setContent { SiansaTheme { Column { app.siansa.ui.RuleCard(rule, emptyList(), onChange = {}, onRemove = {}) } } }
        // The name is also the label of the text field below: find the name by its tag.
        val name = compose.onNodeWithTag("ruleName").getBoundsInRoot()
        val chip = compose.onNodeWithTag("op-CONTAINS").getBoundsInRoot()
        val value = compose.onNodeWithTag("ruleValue").getBoundsInRoot()
        assertEven("Rule chips", chip.top - name.bottom, value.top - chip.bottom, 12.dp)
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

    /** Source filter (issue #693): the sync line starts under the source's name, and its libraries keep their gap below it. */
    @Test fun sourceSyncLineSitsUnderTheName() {
        compose.setContent { SiansaTheme { app.siansa.ui.SourceCardForTest(syncLine = "Synced · 12 books") } }
        val name = compose.onNodeWithText("anna on music.example.com").getBoundsInRoot()
        val line = compose.onNodeWithText("Synced · 12 books").getBoundsInRoot()
        val chip = compose.onNodeWithText("albums").getBoundsInRoot()
        val strip = compose.onNodeWithTag("source-sync-progress").getBoundsInRoot()
        val tick = compose.onNodeWithTag("source-sync-done", useUnmergedTree = true).getBoundsInRoot()
        assertEven("Sync line starts under the name", tick.left - name.left, 0.dp)
        assertTrue("The line sits above the libraries", line.bottom <= chip.top)
        assertEven("Libraries below the sync line", chip.top - strip.bottom, app.siansa.ui.SOURCE_CARD_GAP)
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

    /** Daily vibes (issue #348): the day chips sit one even gap apart. */
    @Test fun dayChipsAreEvenlySpaced() {
        compose.setContent { SiansaTheme { DayChips(setOf(5)) { } } }
        val chips = (1..4).map { compose.onNodeWithTag("day-$it").getBoundsInRoot() }
        val gaps = chips.zipWithNext { a, b -> b.left - a.right }
        gaps.forEach { assertEven("Day chips", gaps[0], it, 1.dp) }
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
     * Cover rows (issue #782): the three rows of a column are one height apart, and the page dots sit with the same
     * gap above them (under the rows) as below (before what follows).
     */
    @Test fun coverRowsAndTheirDotsAreEvenlySpaced() {
        fun a(i: Int) = app.siansa.ui.CoverRowAction(
            app.siansa.mix.CoverRow("$i", app.siansa.mix.CoverRow.Kind.ALBUM, null, "Artist $i", "Album $i", "11 songs"),
            false, {}, {}, emptyList(),
        )
        compose.setContent {
            SiansaTheme {
                Column {
                    app.siansa.ui.CoverRowsShelf("Recently added", (1..5).map(::a)) {}
                    Box(Modifier.fillMaxWidth().height(24.dp).testTag("after"))
                }
            }
        }
        val rows = compose.onAllNodesWithTag("coverRow").fetchSemanticsNodes().take(3).map { it.boundsInRoot }
        val step1 = rows[1].top - rows[0].top
        val step2 = rows[2].top - rows[1].top
        assertEquals("Rows one height apart", step1, step2, 1f)
        val column = compose.onAllNodesWithTag("coverRowsColumn").fetchSemanticsNodes().first().boundsInRoot
        val dots = compose.onNodeWithTag("coverRowsDots").getBoundsInRoot()
        val after = compose.onNodeWithTag("after").getBoundsInRoot()
        assertEven("Gap above and below the dots", dots.top - column.bottom.toDp(), after.top - dots.bottom, 1.dp)
    }

    /** Cover rows (issue #800): swiping to the last column moves the dots along, though it can't reach the left edge. */
    @Test fun coverRowsDotsFollowTheSwipe() {
        fun a(i: Int) = app.siansa.ui.CoverRowAction(
            app.siansa.mix.CoverRow("$i", app.siansa.mix.CoverRow.Kind.ALBUM, null, "Artist $i", "Album $i", "11 songs"),
            false, {}, {}, emptyList(),
        )
        compose.setContent { SiansaTheme { app.siansa.ui.CoverRowsShelf("Keep listening", (1..6).map(::a)) {} } }
        val dots = compose.onNodeWithTag("coverRowsDots")
        dots.assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "1 of 2"))
        compose.onAllNodesWithTag("coverRowsColumn").onFirst().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        dots.assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "2 of 2"))
    }

    private fun Float.toDp() = with(compose.density) { this@toDp.toDp() }
}
