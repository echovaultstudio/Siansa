package app.siansa.ui

import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Tiles across in every grid of albums, artists and the like (issue #289). */
const val GRID_COLUMNS = 3

/** A grid of [GRID_COLUMNS] tiles, or one row per item for the list. */
fun gridCells(grid: Boolean): GridCells = GridCells.Fixed(if (grid) GRID_COLUMNS else 1)

/**
 * Whether the list called [page] ("library", "artist", "albums"…) shows as a grid, kept on the phone so each
 * kind of list opens the way it was left (issue #289). Writing the state saves it.
 */
@Composable
fun rememberGridMode(page: String, default: Boolean = true): MutableState<Boolean> {
    val context = LocalContext.current
    return remember(page) {
        val prefs = context.getSharedPreferences("view_mode", Context.MODE_PRIVATE)
        object : MutableState<Boolean> {
            private val state = mutableStateOf(prefs.getBoolean(page, default))
            override var value: Boolean
                get() = state.value
                set(v) {
                    state.value = v
                    prefs.edit().putBoolean(page, v).apply()
                }
            override fun component1() = value
            override fun component2(): (Boolean) -> Unit = { value = it }
        }
    }
}

/** The grid / list switch, with the same words everywhere ("Show as grid", "Show as list"). */
@Composable
fun GridListToggle(grid: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier.testTag("gridListToggle")) {
        Icon(if (grid) Icons.Rounded.ViewList else Icons.Rounded.GridView, if (grid) "Show as list" else "Show as grid")
    }
}

/** A title with the grid / list switch at its right, at the top of a list. */
@Composable
fun GridListHeader(title: String?, grid: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = Space.Page, end = Space.Tight), verticalAlignment = Alignment.CenterVertically) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        GridListToggle(grid, onToggle)
    }
}

/**
 * One item as a list row (issue #289): its cover (or a gradient square when it has none) on the left, the
 * name and a line under it on the right.
 */
@Composable
fun MediaRow(
    title: String,
    subtitle: String,
    coverArt: String?,
    onClick: () -> Unit,
    round: Boolean = false,
    colors: Pair<Color, Color>? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.Page, vertical = Space.Small).testTag("mediaRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = if (round) CircleShape else RoundedCornerShape(6.dp)
        if (coverArt != null || colors == null) {
            Cover(coverArt, 150, Modifier.size(56.dp), shape)
        } else {
            Box(Modifier.size(56.dp).clip(shape).background(Brush.linearGradient(listOf(colors.first, colors.second))))
        }
        Spacer(Modifier.width(Space.Medium))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(Space.Tight))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A page's albums as a grid of [GRID_COLUMNS] or as a list, with the switch at the top (issue #289). [subtitle]
 * gives each album's second line.
 */
@Composable
fun AlbumsGridOrList(
    albums: List<app.siansa.data.Album>,
    page: String,
    onOpen: (app.siansa.data.Album) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: (app.siansa.data.Album) -> String = { a -> listOfNotNull(a.artist.ifBlank { null }, a.year?.toString()).joinToString(" · ") },
) {
    var grid by rememberGridMode(page)
    val state = rememberLazyGridState()
    val fast = showFastScroll(albums.size)
    // A to Z by artist or by title shows the letter while you drag the handle (beta testers #24).
    val letterBy = remember(albums) {
        val artists = albums.map { it.artist }
        val names = albums.map { it.name }
        when {
            FastScroll.inLetterOrder(artists) -> artists
            FastScroll.inLetterOrder(names) -> names
            else -> null
        }
    }
    Box(modifier) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = gridCells(grid),
            state = state,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = if (grid) Space.Small else 0.dp,
                end = (if (grid) Space.Small else 0.dp) + (if (fast) AlphabetBarWidth else 0.dp),
                bottom = bottomBarPadding(Space.Section),
            ),
            modifier = Modifier.fillMaxSize().testTag("albumsGridOrList"),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { GridListHeader(title, grid) { grid = !grid } }
            items(albums, key = { it.id }) { a ->
                if (grid) AlbumTile(a.name, subtitle(a), a.coverArt, onClick = { onOpen(a) })
                else MediaRow(a.name, subtitle(a), a.coverArt, onClick = { onOpen(a) })
            }
        }
        if (fast) GridFastScroll(state, albums.size) { i -> FastScroll.nameLabel(i, albums.size, letterBy) }
    }
}

/**
 * Artists as a grid of [GRID_COLUMNS] or as a list, with round pictures and the switch at the top (the favourite
 * artists page, issue #397).
 */
@Composable
fun ArtistsGridOrList(
    artists: List<app.siansa.data.Artist>,
    page: String,
    onOpen: (app.siansa.data.Artist) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    var grid by rememberGridMode(page)
    val state = rememberLazyGridState()
    val fast = showFastScroll(artists.size)
    val letterBy = remember(artists) { artists.map { it.name }.takeIf { FastScroll.inLetterOrder(it) } }
    fun line(a: app.siansa.data.Artist) = if (a.albumCount > 0) "${a.albumCount} ${if (a.albumCount == 1) "album" else "albums"}" else "Artist"
    Box(modifier) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = gridCells(grid),
            state = state,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = if (grid) Space.Small else 0.dp,
                end = (if (grid) Space.Small else 0.dp) + (if (fast) AlphabetBarWidth else 0.dp),
                bottom = bottomBarPadding(Space.Section),
            ),
            modifier = Modifier.fillMaxSize().testTag("artistsGridOrList"),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { GridListHeader(title, grid) { grid = !grid } }
            items(artists, key = { it.id }) { a ->
                val cover = a.coverArt ?: "ar-${a.id}"
                if (grid) AlbumTile(a.name, line(a), cover, onClick = { onOpen(a) }, round = true)
                else MediaRow(a.name, line(a), cover, onClick = { onOpen(a) }, round = true)
            }
        }
        if (fast) GridFastScroll(state, artists.size) { i -> FastScroll.nameLabel(i, artists.size, letterBy) }
    }
}

/**
 * The quick-scroll handle over the right edge of a grid or list page whose first row is its header (beta testers
 * #24). The grid leaves [AlphabetBarWidth] free on the right for it.
 */
@Composable
fun BoxScope.GridFastScroll(state: LazyGridState, count: Int, labelAt: (Int) -> String?) {
    val scope = rememberCoroutineScope()
    val positions = remember(count) { FastScroll.positions(count, headers = 1) }
    ListFastScroll(
        count = count,
        positions = positions,
        firstVisible = { state.firstVisibleItemIndex },
        visible = { state.layoutInfo.visibleItemsInfo.size },
        scrollTo = { i -> scope.launch { state.scrollToItem(i) } },
        labelAt = labelAt,
        modifier = Modifier.align(Alignment.TopEnd).padding(bottom = bottomBarPadding(Space.Small)),
    )
}

