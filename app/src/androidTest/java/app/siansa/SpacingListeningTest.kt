package app.siansa

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.siansa.ui.SiansaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Even spacing, measured on a real screen: Search, Your listening, the sonic fingerprint and the Time machine. Shared setup in [SpacingCheck]. */
@RunWith(AndroidJUnit4::class)
class SpacingListeningTest : SpacingCheck() {
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

    /** Your sonic fingerprint's own page (issue #255): its stat tiles sit the same distance apart. */
    @Test fun fingerprintStatTilesAreEvenlySpaced() {
        val p = app.siansa.mix.FingerprintProfile("d", listOf("happy" to 0.6), emptyList(), 120, "minor", listOf("1", "2"))
        compose.setContent { SiansaTheme { app.siansa.ui.FingerprintStats(p) } }
        val tiles = compose.onAllNodesWithTag("fingerprint-stat").fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(4, tiles.size)
        val gaps = tiles.zipWithNext().map { (a, b) -> with(compose.density) { (b.left - a.right).toDp() } }
        gaps.zipWithNext().forEach { (a, b) -> assertEven("Gap between fingerprint tiles", a, b, 1.dp) }
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

    /** Search (beta #21): a section's title and its "show all" arrow sit on one line. */
    @Test fun searchSectionArrowSitsOnTheTitlesLine() {
        compose.setContent { SiansaTheme { app.siansa.ui.SearchSectionTitle("Albums") { } } }
        val row = compose.onNodeWithTag("searchMore-Albums").getBoundsInRoot()
        val title = compose.onNodeWithText("Albums").getBoundsInRoot()
        val arrow = compose.onNodeWithContentDescription("Show all").getBoundsInRoot()
        assertEven("Title and arrow, vertical centre", (title.top + title.bottom) / 2, (arrow.top + arrow.bottom) / 2, 1.dp)
        assertTrue("Arrow inside the row", arrow.right <= row.right + 0.5.dp)
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
}
