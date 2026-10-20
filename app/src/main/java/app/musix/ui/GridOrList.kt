package app.musix.ui

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
    albums: List<app.musix.data.Album>,
    page: String,
    onOpen: (app.musix.data.Album) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: (app.musix.data.Album) -> String = { a -> listOfNotNull(a.artist.ifBlank { null }, a.year?.toString()).joinToString(" · ") },
) {
    var grid by rememberGridMode(page)
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = gridCells(grid),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = if (grid) Space.Small else 0.dp,
            end = if (grid) Space.Small else 0.dp,
            bottom = bottomBarPadding(Space.Section),
        ),
        modifier = modifier.testTag("albumsGridOrList"),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { GridListHeader(title, grid) { grid = !grid } }
        androidx.compose.foundation.lazy.grid.items(albums, key = { it.id }) { a ->
            if (grid) AlbumTile(a.name, subtitle(a), a.coverArt, onClick = { onOpen(a) })
            else MediaRow(a.name, subtitle(a), a.coverArt, onClick = { onOpen(a) })
        }
    }
}
