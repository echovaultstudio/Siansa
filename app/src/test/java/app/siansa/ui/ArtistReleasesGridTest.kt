package app.siansa.ui

import android.app.Application
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.siansa.data.Album
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The artist page's releases, switched between the list and the grid and then scrolled (the owner, 15 Nov: Siansa
 * closed while the page was flung, "Place was called on a node which was placed already"). The grid kept the rows it
 * had worked out for the list, so a release came up in two rows at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi", application = Application::class)
class ArtistReleasesGridTest {
    @get:Rule val compose = createComposeRule()

    private val albums = List(60) { Album(id = "al-$it", name = "Album $it") }

    @Test fun switchedToTheGridAndScrolledBackEveryReleaseShowsOnce() {
        val mode = mutableStateOf(false)
        lateinit var state: LazyGridState
        compose.setContent {
            state = rememberLazyGridState()
            // As on the page: the switch's state, read where the releases are listed.
            val grid by mode
            LazyVerticalGrid(GridCells.Fixed(GRID_COLUMNS), Modifier.size(360.dp, 600.dp), state) {
                item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().height(200.dp)) }
                releaseItems(albums, grid) { Box(Modifier.fillMaxWidth().height(60.dp)) }
            }
        }
        // Down the list, a release a row, so the grid has worked out its rows well past the first screen.
        scroll(state, +400f, times = 8)
        compose.runOnIdle { mode.value = true }
        // Back up, a little at a time, as a fling does.
        scroll(state, -150f, times = 30)
        // And down again in the grid, then back to the list.
        scroll(state, +150f, times = 30)
        compose.runOnIdle { mode.value = false }
        scroll(state, -400f, times = 40)
        compose.runOnIdle { assertEquals(0, state.firstVisibleItemIndex) }
    }

    private fun scroll(state: LazyGridState, by: Float, times: Int) = repeat(times) {
        compose.runOnIdle {
            runBlocking { state.scrollBy(by) }
            val shown = state.layoutInfo.visibleItemsInfo.map { it.index }
            assertEquals("each release once", shown.distinct(), shown)
            assertTrue("in order", shown == shown.sorted())
        }
    }
}
