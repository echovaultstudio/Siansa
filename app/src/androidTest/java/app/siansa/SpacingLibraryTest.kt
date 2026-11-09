package app.siansa

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.siansa.data.AlbumCategory
import app.siansa.ui.CollectionByline
import app.siansa.ui.FastScrollBar
import app.siansa.ui.MoreChipsHint
import app.siansa.ui.ReleaseTypeTabs
import app.siansa.ui.SectionTitle
import app.siansa.ui.SiansaTheme
import app.siansa.ui.Space
import app.siansa.ui.TABS_GAP
import app.siansa.ui.lastPlayedLine
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Even spacing, measured on a real screen: Your Library, artist, album, playlist and book pages, album grids, song rows and Downloads. Shared setup in [SpacingCheck]. */
@RunWith(AndroidJUnit4::class)
class SpacingLibraryTest : SpacingCheck() {
    /**
     * Artist page (issue #1029): the release types are tabs, the chosen one's line inside the row's own room, and with
     * many types, the chosen one is scrolled into view.
     */
    @Test fun releaseTypeTabsShowTheChosenOneOfMany() {
        compose.setContent {
            SiansaTheme {
                Column(Modifier.width(360.dp)) {
                    ReleaseTypeTabs(AlbumCategory.entries.toList(), AlbumCategory.OTHER) { }
                    // Not "Other releases": the chosen tab says that too.
                    SectionTitle("Releases", top = TABS_GAP)
                }
            }
        }
        compose.waitForIdle()
        val row = compose.onNodeWithTag("releaseTypeTabs").getBoundsInRoot()
        val chosen = compose.onNodeWithTag("releaseTypeTabChosen").getBoundsInRoot()
        assertTrue("the chosen tab is in view: $chosen in $row", chosen.left >= row.left && chosen.right <= row.right)
        assertTrue("the tabs are easy to tap: ${row.bottom - row.top}", row.bottom - row.top >= 44.dp)
        val title = compose.onNodeWithText("Releases").getBoundsInRoot()
        assertTrue("the heading comes under the tabs", title.top >= row.bottom)
    }

    /** Stations in Your Library (issue #353): a station's row has the same room above and below its picture, with its ⋮ menu. */
    @Test fun stationRowIsEvenlyPadded() {
        compose.setContent {
            SiansaTheme {
                Column {
                    app.siansa.ui.LibraryStationRowForTest("Radio Paradise", "Radio · On this phone", "own-1")
                }
            }
        }
        val row = compose.onNodeWithTag("libraryRow").getBoundsInRoot()
        val cover = compose.onNodeWithTag("libraryRowCover", useUnmergedTree = true).getBoundsInRoot()
        val menu = compose.onNodeWithTag("stationMenu").getBoundsInRoot()
        assertEven("Station row", cover.top - row.top, row.bottom - cover.bottom)
        // The ⋮ button sits centred with the picture.
        assertEven("Station row's ⋮", menu.top - cover.top, cover.bottom - menu.bottom)
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

    /**
     * Album page (the owner's layout, 7 Nov): the plus right in line with the details above, download beside it; similar
     * albums, shuffle and Play to the right, Play at the row's end; all on one line; nothing under Play.
     */
    @Test fun albumBarLinesUp() {
        compose.setContent {
            SiansaTheme {
                Column {
                    Text("Album · 2019", Modifier.testTag("details"))
                    app.siansa.ui.ActionButtons(
                        enabled = true, onQueue = {}, onSimilar = {}, onShuffle = {}, onPlay = {}, onDjMix = {}, albumBar = true,
                        download = { androidx.compose.foundation.layout.Box(Modifier.size(48.dp).testTag("download")) },
                        resume = app.siansa.ui.PlayResume(7, "8") to 12,
                        onAddToPlaylist = {},
                    )
                }
            }
        }
        val details = compose.onNodeWithTag("details").getBoundsInRoot()
        val plus = compose.onNodeWithTag("albumAddToPlaylist").getBoundsInRoot()
        val similar = compose.onNodeWithTag("findSimilarAlbums").getBoundsInRoot()
        val shuffle = compose.onNodeWithTag("shuffleAll", useUnmergedTree = true).getBoundsInRoot()
        val play = compose.onNodeWithTag("bigPlay").getBoundsInRoot()
        // The plus's circle (28 dp, centred on the button, with 2 of its drawing's 24 units round it) starts where the text
        // above starts. From the button's middle: its bounds are the 40 dp button, not the 48 dp touch area.
        assertEven("The plus in line with the details", (plus.left + plus.right) / 2 - 14.dp + 28.dp * 2 / 24, details.left, 1.dp)
        val line = (play.top + play.bottom) / 2
        listOf(plus, similar, shuffle).forEach { assertEven("On Play's middle line", (it.top + it.bottom) / 2, line, 1.dp) }
        assertTrue("Similar, shuffle, then Play, left to right", similar.right <= shuffle.left + 0.5.dp && shuffle.right <= play.left + 0.5.dp)
        assertTrue("Nothing hangs under Play", compose.onAllNodesWithTag("playFrom").fetchSemanticsNodes().isEmpty())
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

    /** Liked songs and playlists (issue #257): the sort chips sit the same distance apart. */
    @Test fun sortChipsAreEvenlySpaced() {
        compose.setContent { SiansaTheme { androidx.compose.foundation.layout.Column { app.siansa.ui.SortChips(app.siansa.data.ListSort.DEFAULT, "Recently liked") { } } } }
        val chips = listOf("DEFAULT", "PLAYED", "ADDED").map { compose.onNodeWithTag("sort-$it").getBoundsInRoot() }
        val gaps = chips.zipWithNext().map { (a, b) -> b.left - a.right }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between sort chips", a, b, 1.dp) }
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

    /**
     * Artist page (the owner's layout, 9 Nov): the heart on the left, its drawing in line with the text above; Radio,
     * Shuffle and Play on the right, side by side; all on Play's middle line.
     */
    @Test fun artistBarLinesUp() {
        compose.setContent {
            SiansaTheme {
                Column {
                    Text("12 releases · 140 songs", Modifier.testTag("details"))
                    app.siansa.ui.ArtistButtons(
                        heart = { m -> app.siansa.ui.FavouriteHeart(on = false, modifier = m, iconSize = 28.dp) { } },
                        onRadio = {}, onShuffle = {}, playing = false, onPlay = {},
                    )
                }
            }
        }
        val details = compose.onNodeWithTag("details").getBoundsInRoot()
        val heart = compose.onNodeWithTag("favouriteArtist").getBoundsInRoot()
        val radio = compose.onNodeWithTag("artistRadio").getBoundsInRoot()
        val shuffle = compose.onNodeWithTag("shuffleAll").getBoundsInRoot()
        val play = compose.onNodeWithTag("bigPlay").getBoundsInRoot()
        // The heart's drawing (28 dp, centred on the 40 dp button, with 2 of its 24 units round it) starts where the
        // text above starts.
        assertEven("The heart in line with the details", (heart.left + heart.right) / 2 - 14.dp + 28.dp * 2 / 24, details.left, 1.dp)
        val line = (play.top + play.bottom) / 2
        listOf(heart, radio, shuffle).forEach { assertEven("On Play's middle line", (it.top + it.bottom) / 2, line, 1.dp) }
        assertTrue("Radio, shuffle, then Play, left to right", radio.right <= shuffle.left + 0.5.dp && shuffle.right <= play.left + 0.5.dp)
        assertTrue("Radio right next to Shuffle (${(shuffle.left - radio.right).value} dp)", shuffle.left - radio.right <= Space.Small + 0.5.dp)
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

    /**
     * A book's page, laid out like an album's (#626): the cover centred, the words a page's gap from the left edge,
     * and the row of buttons ending in the big Play button a page's gap from the right edge.
     */
    @Test fun bookPageIsLaidOutLikeAnAlbum() {
        val graph = (androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SiansaApp).graph
        val book = app.siansa.data.Book(id = "b1", title = "A long title", authors = listOf("An author"), narrators = listOf("A reader"), description = "About the book.", durationSec = 3600)
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(app.siansa.ui.LocalGraph provides graph) {
                SiansaTheme {
                    Box(Modifier.width(360.dp).testTag("bookHeader")) {
                        app.siansa.ui.BookPageHeader(book, Color.DarkGray, Color.Black, "Audiobook · 1 h", null) {
                            app.siansa.ui.PlayButtonRow { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)); app.siansa.ui.BigPlayButton(true) {} }
                        }
                    }
                }
            }
        }
        val header = compose.onNodeWithTag("bookHeader").getBoundsInRoot()
        val cover = compose.onNodeWithTag("bookCover", useUnmergedTree = true).getBoundsInRoot()
        val words = compose.onNodeWithTag("bookWords", useUnmergedTree = true).getBoundsInRoot()
        val play = compose.onNodeWithTag("bigPlay").getBoundsInRoot()
        assertEven("The cover is centred", cover.left - header.left, header.right - cover.right, 1.dp)
        assertEven("A page's gap left of the words", words.left - header.left, Space.Page, 1.dp)
        assertEven("A page's gap right of Play", header.right - play.right, Space.Page, 1.dp)
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

    /**
     * Home's rows (#560): an album row and a genre row are spaced alike. The first tile starts at the page's edge,
     * the title is the same gap above it, and the tiles are a medium gap apart; album tiles add nothing around them.
     */
    @Test fun albumAndGenreRowsAreSpacedAlike() {
        val graph = (androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SiansaApp).graph
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(app.siansa.ui.LocalGraph provides graph) {
                SiansaTheme {
                    androidx.compose.foundation.layout.Column(Modifier.width(400.dp)) {
                        Box(Modifier.testTag("albumShelf")) {
                            app.siansa.ui.Shelf("Albums", listOf("a", "b", "c"), key = { it }) { id ->
                                app.siansa.ui.AlbumTile("Album $id", "Artist", null, {}, Modifier.width(150.dp))
                            }
                        }
                        Box(Modifier.testTag("genreShelf")) {
                            app.siansa.ui.Shelf("Genres", listOf("x", "y", "z"), key = { it }) { g ->
                                app.siansa.ui.GradientTile(g, Color.Red to Color.Blue, {}, size = 130.dp)
                            }
                        }
                    }
                }
            }
        }
        val albumBox = compose.onNodeWithTag("albumShelf").getBoundsInRoot()
        val genreBox = compose.onNodeWithTag("genreShelf").getBoundsInRoot()
        val covers = compose.onAllNodesWithTag("albumTileCover", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.left }
        val tiles = compose.onAllNodesWithTag("gradientTileArt", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.left }
        val density = compose.density.density
        fun dp(px: Float) = (px / density).dp
        assertEven("Album row starts at the page's edge", dp(covers[0].left) - albumBox.left, Space.Page, 1.dp)
        assertEven("Genre row starts at the page's edge", dp(tiles[0].left) - genreBox.left, Space.Page, 1.dp)
        assertEven("Gap between albums", dp(covers[1].left - covers[0].right), Space.Medium, 1.dp)
        assertEven("Gap between genres", dp(tiles[1].left - tiles[0].right), Space.Medium, 1.dp)
        assertEven("Title to the row, albums and genres alike", dp(covers[0].top) - albumBox.top, dp(tiles[0].top) - genreBox.top, 1.dp)
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

    /**
     * A single's page (issue #723): scrolled to its end, its title comes up under the title bar like a long album's,
     * with no empty band left where the cover was. The spare room goes below its one song.
     */
    @Test fun aShortAlbumScrollsItsCoverAway() {
        val graph = (androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SiansaApp).graph
        val song = app.siansa.data.Song(id = "single-1", title = "The only song", artist = "Sunborn", album = "A single")
        compose.setContent {
            // The page's song rows need the navigation controller and the snackbar too (their ⋮ menu, "Added to the
            // queue"): without them the app crashes.
            val nav = androidx.navigation.compose.rememberNavController()
            androidx.compose.runtime.CompositionLocalProvider(
                app.siansa.ui.LocalGraph provides graph,
                app.siansa.ui.LocalNav provides nav,
                app.siansa.ui.LocalSnack provides androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() },
            ) {
                SiansaTheme {
                    app.siansa.ui.CollectionPage(
                        nav = nav,
                        title = "A single",
                        color = androidx.compose.ui.graphics.Color(0xFF3A3A44),
                        songs = listOf(song),
                        cover = { m -> Box(m.background(androidx.compose.ui.graphics.Color.Gray)) },
                        header = { androidx.compose.material3.Text("Sunborn") },
                        numbered = true,
                    )
                }
            }
        }
        compose.waitForIdle()
        // To the end of the page: the room after the song.
        compose.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToIndex(2)
        compose.waitForIdle()
        val bar = compose.onNodeWithTag("fadingTitleBar").getBoundsInRoot()
        // The page's own title, not the title bar's: the lower of the two.
        val title = compose.onAllNodesWithText("A single").fetchSemanticsNodes().maxBy { it.boundsInRoot.top }
        val titleTop = with(compose.density) { title.boundsInRoot.top.toDp() }
        assertTrue("The title sits under the title bar: ${titleTop.value} dp vs bar ${bar.bottom.value} dp",
            abs((titleTop - bar.bottom).value) <= Space.Page.value)
        compose.onNodeWithText("The only song").assertIsDisplayed()
    }
}
