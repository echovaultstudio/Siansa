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
import androidx.compose.foundation.layout.widthIn
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
import app.siansa.ui.PathMoodPicker
import app.siansa.ui.SiansaTheme
import app.siansa.ui.Space
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Even spacing, measured on a real screen: Home, its cards and rows, the source filter, mixes, vibes and discovery. Shared setup in [SpacingCheck]. */
@RunWith(AndroidJUnit4::class)
class SpacingHomeTest : SpacingCheck() {
    /**
     * The top of Home (issues #784, #872): the notice chips a small gap apart from the page margin, and Home's menu
     * button a circle exactly a chip's height, its top and bottom in line with the chips', a page margin from the
     * right edge. At the normal text size and a larger one, where a fixed-size circle would fall out of line.
     */
    @Test fun noticeChipsAndTheMenuButtonSitInLine() {
        // One content (a test may set it only once), its text size changed between the checks.
        var scale by androidx.compose.runtime.mutableFloatStateOf(1f)
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, fontScale = scale),
            ) {
                SiansaTheme {
                    app.siansa.ui.HomeTopBar(
                        listOf(app.siansa.mix.Notices.carryOn("b", "Dune"), app.siansa.mix.Notices.newAlbums(2, "a")!!),
                        onOpen = {},
                    ) {
                        app.siansa.ui.HomeMenuButton(app.siansa.ui.rememberHomeMenuState(), casting = true, filterActive = false, onPick = {})
                    }
                }
            }
        }
        for (fontScale in listOf(1f, 1.3f)) {
            scale = fontScale
            compose.waitForIdle()
            val screen = compose.onRoot().getBoundsInRoot()
            val first = compose.onNodeWithTag("notice-carry_on").getBoundsInRoot()
            val second = compose.onNodeWithTag("notice-new_albums").getBoundsInRoot()
            val button = compose.onNodeWithTag("homeMenu").getBoundsInRoot()
            assertEven("Notices: from the left edge ($fontScale)", first.left - screen.left, Space.Page, 1.dp)
            assertEven("Notices: between chips ($fontScale)", second.left - first.right, Space.Small, 1.dp)
            assertEven("Menu button: a chip's height ($fontScale)", button.bottom - button.top, first.bottom - first.top, 0.5.dp)
            assertEven("Menu button: round ($fontScale)", button.right - button.left, button.bottom - button.top, 0.5.dp)
            assertEven("Menu button: top in line with the chips ($fontScale)", button.top, first.top, 0.5.dp)
            assertEven("Menu button: bottom in line with the chips ($fontScale)", button.bottom, first.bottom, 0.5.dp)
            assertEven("Menu button: a page margin from the right edge ($fontScale)", screen.right - button.right, Space.Page, 1.dp)
        }
    }

    /** What's new after an update (the owner, 16 Nov): the title and the headlines start a page margin in, the two buttons end the same room from the edges. */
    @Test fun whatsNewSheetIsEvenlySpaced() {
        compose.setContent {
            SiansaTheme {
                Box(Modifier.width(360.dp)) {
                    app.siansa.ui.WhatsNewPanel(app.siansa.ui.UpdateSummary("1.6.0", listOf("Siansa Beats", "Star ratings for songs"), more = 3), onOpenAll = {}, onDone = {})
                }
            }
        }
        val panel = compose.onNodeWithTag("whatsNewPanel").getBoundsInRoot()
        val title = compose.onNodeWithTag("whatsNewTitle").getBoundsInRoot()
        val line = compose.onNodeWithTag("whatsNewLine-0").getBoundsInRoot()
        val all = compose.onNodeWithTag("whatsNewAll").getBoundsInRoot()
        val done = compose.onNodeWithTag("whatsNewDone").getBoundsInRoot()
        assertEven("The title and the first headline, one left edge", title.left, line.left, tolerance = 1.dp)
        assertEven("The headlines, full width", line.left - panel.left, panel.right - line.right, tolerance = 1.dp)
        assertEven("The buttons, the same room at both edges", all.left - panel.left, panel.right - done.right, tolerance = 1.dp)
        assertEven("The buttons, in line", all.top + (all.bottom - all.top) / 2, done.top + (done.bottom - done.top) / 2, tolerance = 1.dp)
    }

    /** Home's notice chips are Your Library's pills (issue #794): the same height, so Home and the library match. */
    @Test fun noticeChipsMatchTheLibrarysPills() {
        compose.setContent {
            SiansaTheme {
                Column {
                    app.siansa.ui.LibraryPill("Books", active = false) {}
                    app.siansa.ui.HomeTopBar(listOf(app.siansa.mix.Notices.newAlbums(2, "a")!!), onOpen = {}) {}
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

    /**
     * Home's menu (the owner, 12 Nov): a pill in the top row, exactly the chips' and the button's height, the four places
     * along it an even distance apart and centred on the row.
     */
    @Test fun homeMenuPillIsTheRowsHeightWithEvenPlaces() {
        compose.setContent {
            SiansaTheme {
                val state = app.siansa.ui.rememberHomeMenuState().also { it.open = true }
                val info = app.siansa.ui.HomeMenuInfo(app.siansa.ui.PlayOnLine("Playing on SHIELD", "Chromecast", true), "Downloaded only", true)
                app.siansa.ui.HomeTopBar(
                    listOf(app.siansa.mix.Notices.newAlbums(24, "a")!!), onOpen = {}, menuOpen = 1f,
                    menuBar = { app.siansa.ui.HomeMenuBar(state, info, app.siansa.data.HomeMenuItem.default, open = 1f, onPick = {}) },
                ) { app.siansa.ui.HomeMenuButton(state, casting = true, filterActive = true, onPick = {}) }
            }
        }
        val button = compose.onNodeWithTag("homeMenu").getBoundsInRoot()
        val pill = compose.onNodeWithTag("homeMenuPanel").getBoundsInRoot()
        assertEven("Menu: the pill's height is the button's", pill.bottom - pill.top, button.bottom - button.top, 0.5.dp)
        assertEven("Menu: the pill's top is the button's", pill.top, button.top, 0.5.dp)
        assertEven("Menu: the room before the button", button.left - pill.right, Space.Small, 0.5.dp)
        val places = listOf("dice", "play_on", "sources", "arrange").map { compose.onNodeWithTag("homeMenu-$it", useUnmergedTree = true).getBoundsInRoot() }
        val circles = compose.onAllNodesWithTag("homeMenuCircle", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("The four pills are drawn", circles.size == 4)
        val width = places[0].right - places[0].left
        places.forEachIndexed { i, p ->
            assertEven("Menu: place $i as wide as the first", p.right - p.left, width, 0.5.dp)
            assertEven("Menu: place $i the pill's height", p.bottom - p.top, pill.bottom - pill.top, 0.5.dp)
        }
        // A pill each (the owner, 13 Nov): a small step between them, the four filling the chips' room.
        for (i in 1 until places.size) assertEven("Menu: the gap after place ${i - 1}", places[i].left - places[i - 1].right, Space.Small, 0.5.dp)
        assertEven("Menu: the pills fill the room", places.last().right - places.first().left, pill.right - pill.left, 0.5.dp)
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

    /**
     * The shortcut grid look (the owner, 10 Nov): two tiles a row a page margin from both edges, the same small gap
     * between the two of a row as between rows.
     */
    @Test fun tileGridHasEvenMarginsAndGaps() {
        val tiles = (1..4).map { i -> app.siansa.ui.GridTile("t$i", "Tile $i with a long name to cut", null, Color.Red to Color.Blue, onClick = {}) }
        compose.setContent { SiansaTheme { Column { app.siansa.ui.TileGrid("Recently played", tiles, 4) } } }
        val screen = compose.onRoot().getBoundsInRoot()
        val boxes = compose.onAllNodesWithTag("tileGridTile").fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(4, boxes.size)
        val d = compose.density
        fun dp(px: Float) = with(d) { px.toDp() }
        assertEven("Grid margins", dp(boxes[0].left) - screen.left, screen.right - dp(boxes[1].right), 1.dp)
        assertEven("Grid left margin is the page margin", dp(boxes[0].left) - screen.left, Space.Page, 1.dp)
        assertEven("Gap across and gap down", dp(boxes[1].left - boxes[0].right), dp(boxes[2].top - boxes[0].bottom), 1.dp)
        assertEven("The gap is a small step", dp(boxes[1].left - boxes[0].right), Space.Small, 1.dp)
    }

    /** A hero with cards at its foot (the owner, 22 Nov: "spacing needs to be 100% correct"). */
    @Test fun heroCardsSitEvenlyAtItsFoot() {
        val items = (1..5).map { i -> app.siansa.ui.HomeItem(key = "h$i", title = "Mix $i with a long name", subtitle = "Calm music", open = {}) }
        compose.setContent { SiansaTheme { Column { app.siansa.ui.HomeHero("For now", items.first(), play = {}, rowSongs = emptyList(), under = items.drop(1)) { _, _ -> } } } }
        val screen = compose.onRoot().getBoundsInRoot()
        val cards = compose.onAllNodesWithTag("homeHeroCard").fetchSemanticsNodes().map { it.boundsInRoot }
        val play = compose.onNodeWithTag("homeHeroPlay").fetchSemanticsNode().boundsInRoot
        assertEquals(4, cards.size)
        val d = compose.density
        fun dp(px: Float) = with(d) { px.toDp() }
        // The hero is a card with the page's margins beside it (the owner, 22 Nov), and its cards a page margin inside it.
        val hero = compose.onNodeWithTag("homeHero").fetchSemanticsNode().boundsInRoot
        assertEven("The hero's left margin is the page margin", dp(hero.left) - screen.left, Space.Page, 1.dp)
        assertEven("The hero's margins left and right", dp(hero.left) - screen.left, screen.right - dp(hero.right), 1.dp)
        assertEven("The cards' outer edges sit evenly in the hero", dp(cards.first().left - hero.left), dp(hero.right - cards.last().right), 1.dp)
        assertEven("The cards' left margin in the hero", dp(cards.first().left - hero.left), Space.Page, 1.dp)
        assertEven("The gaps between the cards", dp(cards[1].left - cards[0].right), dp(cards[3].left - cards[2].right), 0.5.dp)
        assertEven("The gap between cards is a small step", dp(cards[1].left - cards[0].right), Space.Small, 1.dp)
        assertEven("A section's gap from the buttons to the cards", dp(cards[0].top - play.bottom), Space.Section, 1.dp)
    }

    /** Your listening's card (issue #1343): the hero's margins, its pages a page margin inside it, its look back squares evenly spread. */
    @Test fun listeningCardSitsAsTheHeroWithEvenPages() {
        fun list(kind: app.siansa.mix.TimeListKind, title: String, tag: String) = app.siansa.mix.TimeCardList(kind.id, title, tag, "November 2025", listOf("a", "b", "c"), listOf(3, 2, 1), year = 2025)
        val card = app.siansa.mix.TimeCard("2026-11-23", listOf(
            list(app.siansa.mix.TimeListKind.YEAR_AGO, "This time last year", "NOV 2025"), list(app.siansa.mix.TimeListKind.DAY, "This day last year", "23 NOV"),
            list(app.siansa.mix.TimeListKind.LAST_MONTH, "Last month", "OCT"), list(app.siansa.mix.TimeListKind.LAST_YEAR, "Your 2025", "2025"),
        ), app.siansa.mix.TIME_CARD_MAKE)
        compose.setContent { SiansaTheme { Column { app.siansa.ui.ListeningCard(card, app.siansa.mix.ListenRange.MONTH, app.siansa.data.ListenColour.TEAL,
            app.siansa.ui.ListeningActions({ emptyList() }, { _, _, _ -> }, {}, {}, {})) } } }
        val screen = compose.onRoot().getBoundsInRoot()
        val d = compose.density
        fun dp(px: Float) = with(d) { px.toDp() }
        val whole = compose.onNodeWithTag("listeningCard").fetchSemanticsNode().boundsInRoot
        assertEven("Your listening: its left margin is the page margin", dp(whole.left) - screen.left, Space.Page, 1.dp)
        assertEven("Your listening: margins left and right", dp(whole.left) - screen.left, screen.right - dp(whole.right), 1.dp)
        val squares = compose.onAllNodesWithTag("listeningBack").fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(4, squares.size)
        assertEven("Your listening: its lists' outer edges sit evenly", dp(squares.first().left - whole.left), dp(whole.right - squares.last().right), 1.dp)
        assertEven("Your listening: its lists' left margin in the card", dp(squares.first().left - whole.left), Space.Page, 1.dp)
        assertEven("Your listening: the gaps between its lists", dp(squares[1].left - squares[0].right), dp(squares[3].left - squares[2].right), 0.5.dp)
        assertEven("Your listening: the gap between lists is a small step", dp(squares[1].left - squares[0].right), Space.Small, 1.dp)
    }

    /** An artist's round photo (issue #1355): centred in its picture, the same room on every side, and as wide as it's tall. */
    @Test fun anArtistsRoundPhotoIsCentredInItsPicture() {
        compose.setContent {
            SiansaTheme {
                androidx.compose.runtime.CompositionLocalProvider(app.siansa.ui.LocalCoverDraw provides { _, _, modifier, _, _ -> androidx.compose.foundation.layout.Box(modifier) }) {
                    // In a box with a tag of its own: the picture's tag names its look, which follows the row's Pictures.
                    androidx.compose.foundation.layout.Box(Modifier.size(200.dp).testTag("roundCard")) {
                        app.siansa.ui.CardPicture(listOf("photo"), "photo", app.siansa.ui.tileColors("artist"), "Artist radio", Modifier.size(200.dp), round = true)
                    }
                }
            }
        }
        val whole = compose.onNodeWithTag("roundCard", useUnmergedTree = true).getBoundsInRoot()
        val photo = compose.onNodeWithTag("cardPictureRound", useUnmergedTree = true).getBoundsInRoot()
        assertEven("Round photo: as wide as it's tall", photo.right - photo.left, photo.bottom - photo.top, 0.5.dp)
        assertEven("Round photo: room left and right", photo.left - whole.left, whole.right - photo.right, 0.5.dp)
        assertEven("Round photo: room above and below", photo.top - whole.top, whole.bottom - photo.bottom, 0.5.dp)
    }

    /** Your listening's colours under its cog (issue #1347): three to a row, evenly spread, both rows alike. */
    @Test fun listenColoursAreEvenlySpread() {
        compose.setContent { SiansaTheme { Column(Modifier.padding(Space.Section)) { app.siansa.ui.ListenColourSwatches(app.siansa.data.ListenColour.TEAL) {} } } }
        val all = compose.onNodeWithTag("listenColours").getBoundsInRoot()
        val b = app.siansa.data.ListenColour.entries.map { compose.onNodeWithTag("listenColour-${it.id}").getBoundsInRoot() }
        assertEven("Listen colours: the first starts at the edge", b[0].left, all.left, 1.dp)
        assertEven("Listen colours: the third ends at the edge", b[2].right, all.right, 1.dp)
        assertEven("Listen colours: the gaps in a row", b[1].left - b[0].right, b[2].left - b[1].right, 1.dp)
        assertEven("Listen colours: the gap is a small step", b[1].left - b[0].right, Space.Small, 1.dp)
        assertEven("Listen colours: the second row under the first", b[3].left, b[0].left, 1.dp)
        assertEven("Listen colours: all as wide", b[4].right - b[4].left, b[1].right - b[1].left, 1.dp)
    }

    /** A cover grid with one featured (the owner, 22 Nov): the gap under the big square is the gap between all of them. */
    @Test fun featuredCoverGridHasEvenGaps() {
        val items = (1..6).map { i -> app.siansa.ui.HomeItem(key = "f$i", title = "Mix $i with a long name", subtitle = "Calm music", open = {}) }
        compose.setContent { SiansaTheme { Column { app.siansa.ui.HomeFeaturedGrid("Made for you", items) } } }
        val big = compose.onNodeWithTag("homeFeatured").fetchSemanticsNode().boundsInRoot
        val cells = compose.onAllNodesWithTag("homeCoverGridCell").fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(5, cells.size)
        val d = compose.density
        fun dp(px: Float) = with(d) { px.toDp() }
        assertEven("The featured square ends with the two beside it", dp(big.bottom), dp(cells[1].bottom), 0.5.dp)
        assertEven("Gap beside and gap under the featured square", dp(cells[0].left - big.right), dp(cells[2].top - big.bottom), 0.5.dp)
        assertEven("Gap between the two beside it and in the row under", dp(cells[1].top - cells[0].bottom), dp(cells[3].left - cells[2].right), 0.5.dp)
        assertEven("The row under ends where the two beside it do", dp(cells[4].right), dp(cells[0].right), 0.5.dp)
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

    /** Arrange Home's Cards (the owner, 13 Nov): four small cards of one size, an even gap apart, page margins at the ends. */
    @Test fun cardLookChooserIsFourEvenCards() {
        compose.setContent {
            SiansaTheme {
                app.siansa.ui.CardLookChooser(app.siansa.data.CardLook.SOFT, app.siansa.data.CardLook.forHome, Modifier.padding(horizontal = Space.Page)) {}
            }
        }
        val screen = compose.onRoot().getBoundsInRoot()
        val cards = app.siansa.data.CardLook.forHome.map { compose.onNodeWithTag("cardLook-${it.id}").getBoundsInRoot() }
        assertEven("Cards: left margin", cards.first().left - screen.left, Space.Page, 1.dp)
        assertEven("Cards: right margin", screen.right - cards.last().right, Space.Page, 1.dp)
        val width = cards[0].right - cards[0].left
        cards.forEach { assertEven("Cards: each as wide as the first", it.right - it.left, width, 1.dp) }
        val gaps = cards.zipWithNext { a, b -> b.left - a.right }
        gaps.forEach { assertEven("Cards: the gap between them", it, Space.Medium, 1.dp) }
    }

    /** A slider of choices (the owner, 23 Nov: "perfect text spacing"): each name centred under its stop, the outer two inside the edges. */
    @Test fun stopSliderNamesSitUnderTheirStops() {
        val shapes = listOf("Two a row", "Three a row", "Big tiles", "Pill chips")
        compose.setContent {
            SiansaTheme {
                Column(Modifier.padding(Space.Section)) {
                    app.siansa.ui.StopSlider(shapes, shapes[1], { it }, { "stop-" + shapes.indexOf(it) }) {}
                }
            }
        }
        val row = compose.onNodeWithTag("stopLabels").getBoundsInRoot()
        val names = shapes.indices.map { compose.onNodeWithTag("stop-$it").getBoundsInRoot() }
        val width = row.right - row.left
        assertEven("Stop slider: the first name starts at the edge", names[0].left, row.left, 1.dp)
        assertEven("Stop slider: the last name ends at the edge", names[3].right, row.right, 1.dp)
        assertEven("Stop slider: the second name is centred on its stop", (names[1].left + names[1].right) / 2 - row.left, width / 3, 1.dp)
        assertEven("Stop slider: the third name is centred on its stop", (names[2].left + names[2].right) / 2 - row.left, width * 2 / 3, 1.dp)
    }

    /** With only the chosen name shown (the owner, 23 Nov: "only show the selected text so it fits neatly"): it alone, centred under its stop. */
    @Test fun stopSliderShowsOnlyTheChosenNameUnderItsStop() {
        val shapes = listOf("Two a row", "Three a row", "Big tiles", "Pill chips")
        compose.setContent {
            SiansaTheme {
                Column(Modifier.padding(Space.Section)) {
                    app.siansa.ui.StopSlider(shapes, shapes[2], { it }, { "stop-" + shapes.indexOf(it) }, onlyChosen = true) {}
                }
            }
        }
        val row = compose.onNodeWithTag("stopLabels").getBoundsInRoot()
        val name = compose.onNodeWithTag("stop-2").getBoundsInRoot()
        assertEven("Stop slider: the one name is centred on its stop", (name.left + name.right) / 2 - row.left, (row.right - row.left) * 2 / 3, 1.dp)
        listOf(0, 1, 3).forEach { compose.onNodeWithTag("stop-$it").assertDoesNotExist() }
    }

    /** A row's settings sheet (the owner, 22 Nov): its switch lines one height, one after the other, with the same margin on both sides. */
    @Test fun rowSettingsLinesAreEven() {
        compose.setContent {
            SiansaTheme {
                app.siansa.ui.HomeLookDialog(app.siansa.data.HomeSection.MADE_FOR_YOU, app.siansa.data.HomeLook.TILES, titleShown = true, onTitleShown = {},
                    onPick = {}, onDismiss = {}, sheet = false)
            }
        }
        val screen = compose.onRoot().getBoundsInRoot()
        val lines = listOf("rowOnHome", "homeTitleShown", "homeCardLines").map { compose.onNodeWithTag(it).getBoundsInRoot() }
        val height = lines[0].bottom - lines[0].top
        lines.forEach {
            assertEven("Row settings: each switch line as tall as the first", it.bottom - it.top, height, 1.dp)
            assertEven("Row settings: a line's margins", it.left - screen.left, screen.right - it.right, 1.dp)
            assertEven("Row settings: a line's left margin", it.left - screen.left, Space.Section, 1.dp)
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

    /** Sources: a user's libraries sit their gap below the name, and start under it, past the round mark (the owner, 12 Nov). */
    @Test fun sourceRowLibrariesSitUnderTheName() {
        compose.setContent { SiansaTheme { app.siansa.ui.SourceCardForTest() } }
        val name = compose.onNodeWithText("anna on music.example.com").getBoundsInRoot()
        val nameRow = compose.onNodeWithTag("source-name").getBoundsInRoot()
        // The chip merges its tick and name, so its text node is the whole chip.
        val chipBox = compose.onNodeWithText("albums").getBoundsInRoot()
        assertEven("Libraries below the name", chipBox.top - nameRow.bottom, app.siansa.ui.SOURCE_CARD_GAP)
        assertEven("Libraries start under the name", chipBox.left - name.left, 0.dp)
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

    /** Flow's mood pad (15 Nov): the same gap between the pad, the mood's name, each heading and what's under it. */
    @Test fun theMoodShaperIsEvenlySpaced() {
        compose.setContent {
            SiansaTheme {
                app.siansa.ui.MoodShaper(
                    VibeTuning(energy = 1, sounds = listOf(SoundDial("piano", 3), SoundDial("Drone", -3, genre = true))),
                    app.siansa.ui.MoodLook(0.5f, 0f), app.siansa.ui.MoodHold(), onChange = { }, traits = false, picker = { },
                )
            }
        }
        val pad = compose.onNodeWithTag("soundPad").getBoundsInRoot()
        val words = compose.onNodeWithTag("moodWords").getBoundsInRoot()
        val labels = compose.onAllNodesWithTag("moodLabel")
        val sounds = labels[0].getBoundsInRoot()
        val artists = labels[1].getBoundsInRoot()
        val picks = compose.onNodeWithTag("moodPicks").getBoundsInRoot()
        val choice = compose.onNodeWithTag("moodArtists").getBoundsInRoot()
        assertEven("Pad to the mood's name, and the picks to Artists", words.top - pad.bottom, choice.top - artists.bottom, 1.dp)
        assertEven("Each heading to what's under it", picks.top - sounds.bottom, choice.top - artists.bottom, 1.dp)
        assertEven("Above each heading", sounds.top - words.bottom, artists.top - picks.bottom, 1.dp)
        val chips = compose.onAllNodesWithTag("moodPick")
        val add = compose.onNodeWithTag("moodAdd").getBoundsInRoot()
        assertEven("Picks and Add are as tall", chips[0].getBoundsInRoot().let { it.bottom - it.top }, add.bottom - add.top, 0.5.dp)
        assertEven("Between the picks, and before Add", chips[1].getBoundsInRoot().left - chips[0].getBoundsInRoot().right, add.left - chips[1].getBoundsInRoot().right, 1.dp)
    }

    /** Flow's line under the pad (the owner, 15 Nov): right under it, in the middle, and the mood's name as far below it as below the pad without it. */
    @Test fun thePadsNoteIsCentredRightUnderIt() {
        compose.setContent {
            SiansaTheme {
                app.siansa.ui.MoodShaper(VibeTuning(), app.siansa.ui.MoodLook(0f, 0f), app.siansa.ui.MoodHold(), onChange = { }, padNote = app.siansa.ui.FLOW_PAD_NOTE, traits = false, picker = { })
            }
        }
        val pad = compose.onNodeWithTag("soundPad").getBoundsInRoot()
        val note = compose.onNodeWithTag("padNote").getBoundsInRoot()
        val words = compose.onNodeWithTag("moodWords").getBoundsInRoot()
        assertEven("The note right under the pad", note.top - pad.bottom, app.siansa.ui.Space.Tight, 1.dp)
        assertEven("The note as wide as the pad, its words in the middle", note.left + note.right, pad.left + pad.right, 1.dp)
        assertEven("The mood's name under the note", words.top - note.bottom, app.siansa.ui.Space.Medium, 1.dp)
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

    /** A section shown without its title keeps the same gap above its row as a titled one has above its title (issue #961). */
    @Test fun aHiddenTitleKeepsTheGap() {
        var shown by androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            SiansaTheme {
                Column(Modifier.testTag("top")) {
                    androidx.compose.runtime.CompositionLocalProvider(app.siansa.ui.LocalSectionTitleShown provides shown) {
                        app.siansa.ui.Shelf("Your evening", listOf(1, 2), key = { it }) { Box(Modifier.size(60.dp).testTag("tile")) }
                    }
                }
            }
        }
        val titled = compose.onAllNodesWithTag("tile").onFirst().getBoundsInRoot().top - compose.onNodeWithTag("top").getBoundsInRoot().top
        shown = false
        compose.waitForIdle()
        val untitled = compose.onAllNodesWithTag("tile").onFirst().getBoundsInRoot().top - compose.onNodeWithTag("top").getBoundsInRoot().top
        assertTrue("the title takes no room once hidden", untitled < titled)
        assertEven("Gap above the row, hidden title", untitled, app.siansa.ui.SECTION_GAP, 1.dp)
        assertTrue("no title text", compose.onAllNodes(hasText("Your evening")).fetchSemanticsNodes().isEmpty())
    }
}
