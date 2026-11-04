package app.siansa.tvapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.data.Song
import app.siansa.log.AppLog
import app.siansa.ui.Cover
import app.siansa.ui.LARGE_COVER
import app.siansa.ui.LocalGraph
import app.siansa.ui.Space
import kotlinx.coroutines.launch

/** The Library's three lists. */
private enum class Shelf(val label: String) { ALBUMS("Albums"), ARTISTS("Artists"), PLAYLISTS("Playlists") }

/** Albums, artists and playlists, in grids of covers. */
@Composable
fun TvLibrary(open: (TvPage) -> Unit) {
    val graph = LocalGraph.current
    val version by graph.library.version.collectAsStateWithLifecycle()
    var shelf by remember { mutableStateOf(Shelf.ALBUMS) }
    val items by produceState<List<TvItem>?>(null, shelf, version) {
        value = null
        value = runCatching {
            when (shelf) {
                Shelf.ALBUMS -> graph.library.albums("name").map { TvItem.OfAlbum(it) }
                Shelf.ARTISTS -> graph.library.artists().map { TvItem.OfArtist(it) }
                Shelf.PLAYLISTS -> graph.library.playlists().map { TvItem.OfPlaylist(it) }
            }
        }.onFailure { AppLog.w("Tv", "Couldn't read the library's ${shelf.label.lowercase()}: ${it.message}") }.getOrDefault(emptyList())
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.Page)) {
        Row(Modifier.padding(horizontal = TV_EDGE), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
            for (s in Shelf.entries) TvButton(s.label, null, onClick = { shelf = s }, strong = s == shelf)
        }
        val list = items
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> EmptyNote("Nothing here yet.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(TILE_WIDTH),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = TV_EDGE, vertical = Space.Page),
                horizontalArrangement = Arrangement.spacedBy(Space.Section),
                verticalArrangement = Arrangement.spacedBy(Space.Section),
            ) {
                items(list, key = { it.key }) { item -> TvItemTile(item, open) }
            }
        }
    }
}

@Composable
fun TvAlbum(id: String, open: (TvPage) -> Unit) {
    val graph = LocalGraph.current
    val detail by produceState<Pair<app.siansa.data.Album, List<Song>>?>(null, id) {
        value = runCatching {
            graph.library.album(id)?.let { album -> album to graph.library.songsOf(album) }
        }.onFailure { AppLog.w("Tv", "Couldn't open album $id: ${it.message}") }.getOrNull()
    }
    val d = detail
    if (d == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val (album, songs) = d
    val about = listOfNotNull(album.year?.toString(), "${songs.size} songs").joinToString(" · ")
    SongsPage(album.name, album.artist, about, album.coverArt, songs, onArtist = album.artistId?.let { a -> { open(TvPage.Artist(a)) } })
}

@Composable
fun TvPlaylist(id: String, name: String) {
    val graph = LocalGraph.current
    val songs by produceState<List<Song>?>(null, id) {
        value = runCatching { graph.playlistCache.get(id).songs }
            .onFailure { AppLog.w("Tv", "Couldn't open playlist $id: ${it.message}") }.getOrDefault(emptyList())
    }
    val list = songs
    if (list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    SongsPage(name, "Playlist", "${list.size} songs", list.firstOrNull()?.coverArt, list, onArtist = null)
}

@Composable
fun TvArtist(id: String, open: (TvPage) -> Unit) {
    val graph = LocalGraph.current
    val artist by produceState<app.siansa.data.Artist?>(null, id) { value = graph.library.artist(id) }
    val albums by produceState<List<TvItem>?>(null, artist) {
        value = artist?.let { a -> graph.library.albumsOf(a).map { TvItem.OfAlbum(it) } }
    }
    val a = artist
    val list = albums
    if (a == null || list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(TILE_WIDTH),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TV_EDGE, vertical = Space.Page),
        horizontalArrangement = Arrangement.spacedBy(Space.Section),
        verticalArrangement = Arrangement.spacedBy(Space.Section),
    ) {
        item(key = "header", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.Page)) {
                Text(a.name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                PlayButtons(
                    play = { graph.library.songsOf(a) },
                    note = "${list.size} albums",
                )
            }
        }
        items(list, key = { it.key }) { item -> TvItemTile(item, open) }
    }
}

/** An album or playlist: its cover and buttons on the left, its songs on the right. */
@Composable
private fun SongsPage(title: String, byline: String, about: String, coverId: String?, songs: List<Song>, onArtist: (() -> Unit)?) {
    val graph = LocalGraph.current
    val player by graph.player.state.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxSize().padding(horizontal = TV_EDGE), horizontalArrangement = Arrangement.spacedBy(Space.Large)) {
        Column(Modifier.width(SIDE_WIDTH), verticalArrangement = Arrangement.spacedBy(Space.Page)) {
            Cover(coverId, LARGE_COVER, Modifier.size(SIDE_WIDTH), RoundedCornerShape(Space.Medium))
            Column(verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(byline, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PlayButtons(play = { songs }, note = null)
            if (onArtist != null) TvButton("Go to artist", null, onClick = onArtist)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(bottom = Space.Large),
            verticalArrangement = Arrangement.spacedBy(Space.Tight),
        ) {
            itemsIndexed(songs, key = { i, s -> "$i:${s.id}" }) { i, song ->
                SongRow(i + 1, song, playing = player.current?.id == song.id) { graph.player.play(songs, startIndex = i) }
            }
        }
    }
}

/** Play and Shuffle for a list of songs, read when pressed. */
@Composable
private fun PlayButtons(play: suspend () -> List<Song>, note: String?) {
    val graph = LocalGraph.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(Space.Medium), verticalAlignment = Alignment.CenterVertically) {
        TvButton("Play", Icons.Rounded.PlayArrow, strong = true, onClick = {
            scope.launch { play().takeIf { it.isNotEmpty() }?.let { graph.player.play(it) } }
        })
        TvButton("Shuffle", Icons.Rounded.Shuffle, onClick = {
            scope.launch { play().takeIf { it.isNotEmpty() }?.let { graph.player.play(it, shuffle = true) } }
        })
        if (note != null) Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SongRow(number: Int, song: Song, playing: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Space.Small)
    Row(
        Modifier
            .fillMaxWidth()
            .tvFocus(shape, onClick, grow = ROW_GROW)
            .background(if (playing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background, shape)
            .padding(horizontal = Space.Page, vertical = Space.Medium),
        horizontalArrangement = Arrangement.spacedBy(Space.Page),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(NUMBER_WIDTH),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(minutes(song.durationSec * 1000L), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyNote(text: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = TV_EDGE), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "3:07" for 187 000 ms. */
fun minutes(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
}

private val SIDE_WIDTH = 300.dp
private val NUMBER_WIDTH = 32.dp
private const val ROW_GROW = 1.02f
