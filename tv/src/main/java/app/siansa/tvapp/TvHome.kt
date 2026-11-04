package app.siansa.tvapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.AppGraph
import app.siansa.data.Album
import app.siansa.data.Artist
import app.siansa.data.Playlist
import app.siansa.log.AppLog
import app.siansa.ui.LocalGraph
import app.siansa.ui.Space
import kotlinx.coroutines.launch

/** One thing in a row on Home or a grid in the Library. */
sealed interface TvItem {
    val key: String
    data class OfAlbum(val album: Album) : TvItem { override val key get() = "al:" + album.id }
    data class OfArtist(val artist: Artist) : TvItem { override val key get() = "ar:" + artist.id }
    data class OfPlaylist(val playlist: Playlist) : TvItem { override val key get() = "pl:" + playlist.id }
}

data class TvRow(val title: String, val items: List<TvItem>)

/** How many covers a row on Home holds. */
private const val ROW_SIZE = 24

/** Home's rows, from the library on this TV (synced from the server like the phone's). */
suspend fun homeRows(graph: AppGraph): List<TvRow> {
    val library = graph.library
    fun albums(list: List<Album>) = list.take(ROW_SIZE).map { TvItem.OfAlbum(it) }
    return listOf(
        TvRow("Recently added", albums(library.albums("newest"))),
        TvRow("Most played", albums(library.albums("frequent").filter { it.playCount > 0 })),
        TvRow("Playlists", runCatching { library.playlists() }.getOrDefault(emptyList()).take(ROW_SIZE).map { TvItem.OfPlaylist(it) }),
        TvRow("Something different", albums(library.randomAlbums(ROW_SIZE))),
        TvRow("Artists", library.artists().filter { it.coverArt != null }.shuffled().take(ROW_SIZE).map { TvItem.OfArtist(it) }),
    ).filter { it.items.isNotEmpty() }
}

@Composable
fun TvHome(open: (TvPage) -> Unit) {
    val graph = LocalGraph.current
    val version by graph.library.version.collectAsStateWithLifecycle()
    val rows by produceState<List<TvRow>?>(null, version) {
        value = runCatching { homeRows(graph) }.onFailure { AppLog.w("Tv", "Couldn't read Home's rows: ${it.message}") }.getOrDefault(emptyList())
    }
    val scope = rememberCoroutineScope()
    val loaded = rows
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Space.Large),
        verticalArrangement = Arrangement.spacedBy(Space.Section),
    ) {
        item("quick") {
            Row(Modifier.padding(horizontal = TV_EDGE), horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
                TvButton("Shuffle your library", Icons.Rounded.Shuffle, strong = true, onClick = {
                    scope.launch { graph.player.play(graph.library.randomSongs(SHUFFLE_SONGS)) }
                })
                TvButton("Liked songs", Icons.Rounded.Favorite, onClick = {
                    scope.launch { graph.library.starredSongs().takeIf { it.isNotEmpty() }?.let { graph.player.play(it, shuffle = true) } }
                })
            }
        }
        if (loaded.isEmpty()) {
            item("empty") {
                Text(
                    "Your library is syncing. Albums show here once it's done.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = TV_EDGE),
                )
            }
        }
        items(loaded, key = { it.title }) { row -> TvItemRow(row, open) }
    }
}

/** A titled row of covers that scrolls sideways as the remote moves along it. */
@Composable
fun TvItemRow(row: TvRow, open: (TvPage) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.Page)) {
        Text(row.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = TV_EDGE))
        LazyRow(
            contentPadding = PaddingValues(horizontal = TV_EDGE, vertical = Space.Small),
            horizontalArrangement = Arrangement.spacedBy(Space.Section),
        ) {
            items(row.items, key = { it.key }) { item -> TvItemTile(item, open) }
        }
    }
}

@Composable
fun TvItemTile(item: TvItem, open: (TvPage) -> Unit, modifier: Modifier = Modifier) {
    when (item) {
        is TvItem.OfAlbum -> TvTile(item.album.name, item.album.artist, item.album.coverArt, { open(TvPage.Album(item.album.id)) }, modifier)
        is TvItem.OfArtist -> TvTile(item.artist.name, null, item.artist.coverArt, { open(TvPage.Artist(item.artist.id)) }, modifier, round = true)
        is TvItem.OfPlaylist -> TvTile(
            item.playlist.name,
            "${item.playlist.songCount} songs",
            item.playlist.coverArt,
            { open(TvPage.Playlist(item.playlist.id, item.playlist.name)) },
            modifier,
        )
    }
}

private const val SHUFFLE_SONGS = 200
