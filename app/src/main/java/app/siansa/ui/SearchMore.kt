package app.siansa.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.siansa.data.SearchResults

/** How many of each kind the search page shows before "see all" (beta #21). */
const val SEARCH_PREVIEW = 5

/** A kind of search result with a page of its own (beta #21): the section header opens every match. */
enum class SearchMore(val id: String) {
    ARTISTS("artists"), ALBUMS("albums"), SONGS("songs"), SOUND("sound");

    fun title(query: String): String = when (this) {
        ARTISTS -> "Artists for “$query”"
        ALBUMS -> "Albums for “$query”"
        SONGS -> "Songs for “$query”"
        SOUND -> "Sounds like “$query”"
    }

    companion object {
        fun of(id: String?): SearchMore = entries.firstOrNull { it.id == id } ?: SONGS
    }
}

fun searchMoreRoute(kind: SearchMore, query: String) = "search/more/${kind.id}?q=${Uri.encode(query)}"

/** A results section's title; tapping it (or its arrow) opens every match on a page of its own. */
@Composable
internal fun SearchSectionTitle(text: String, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onMore).padding(start = Space.Page, end = Space.Small, top = Space.Section, bottom = Space.Medium)
            .testTag("searchMore-$text"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Show all", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Every match of one kind for [query] (beta #21): songs with Play and Shuffle at the top, or a grid of albums or artists. */
@Composable
fun SearchMoreScreen(nav: NavController, kindId: String?, query: String) {
    val graph = LocalGraph.current
    val kind = SearchMore.of(kindId)
    val title = kind.title(query)
    suspend fun byName(): SearchResults = if (graph.library.isSynced) graph.library.search(query) else graph.source.search(query)
    when (kind) {
        SearchMore.SONGS, SearchMore.SOUND -> {
            val songs by loadState("search-more-${kind.id}-$query") {
                if (kind == SearchMore.SOUND) graph.generator.soundSearch(query) else byName().songs
            }
            val colors = tileColors("search-$query")
            CollectionPage(
                nav = nav,
                title = title,
                color = lerp(colors.first, Color.Black, 0.25f),
                songs = songs.valueOrNull(),
                error = (songs as? Load.Err)?.message,
                cover = { m -> GeneratedCover(query, colors, m) },
                header = {},
            )
        }
        SearchMore.ARTISTS, SearchMore.ALBUMS -> {
            val results by loadState("search-more-names-$query") { byName() }
            Screen(title = title, nav = nav) { padding ->
                LoadBox(results, Modifier.padding(top = padding.calculateTopPadding())) { r ->
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Space.Small, end = Space.Small, bottom = bottomBarPadding(Space.Section)),
                    ) {
                        if (kind == SearchMore.ALBUMS) {
                            items(r.albums, key = { it.id }) { album ->
                                AlbumTile(album.name, album.artist, album.coverArt, onClick = { nav.navigate("album/${album.id}") })
                            }
                        } else {
                            items(r.artists, key = { it.id }) { artist ->
                                Column(
                                    Modifier.clickable { nav.navigate("artist/${artist.id}") }.padding(Space.Small),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(Space.Small),
                                ) {
                                    Cover(artist.coverArt, 300, Modifier.size(132.dp), CircleShape)
                                    Text(artist.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
