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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.data.Album
import app.siansa.data.Artist
import app.siansa.data.Song
import app.siansa.data.cover
import app.siansa.log.AppLog
import app.siansa.ui.Cover
import app.siansa.ui.LARGE_COVER
import app.siansa.ui.LocalGraph
import app.siansa.ui.LocalNav
import app.siansa.ui.LocalSnack
import app.siansa.ui.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The Library's lists. */
private enum class Shelf(val label: String) {
    ALBUMS("Albums"), ARTISTS("Artists"), PLAYLISTS("Playlists"), LIKED("Liked songs"), GENRES("Genres")
}

/** Albums, artists, playlists and genres in grids of covers, and the liked songs as a list. */
@Composable
fun TvLibrary() {
    val graph = LocalGraph.current
    val version by graph.library.version.collectAsStateWithLifecycle()
    val likes by graph.favorites.changes.collectAsStateWithLifecycle()
    var shelf by rememberSaveable { mutableStateOf(Shelf.ALBUMS) }
    var tiles by remember { mutableStateOf<List<TvItem>?>(null) }
    var liked by remember { mutableStateOf<List<Song>?>(null) }
    var shown by remember { mutableStateOf<Shelf?>(null) }
    // As on Home: read again once a sync has stopped changing the library for a moment, keeping what's shown meanwhile.
    LaunchedEffect(shelf, version, likes) {
        if (shown == shelf) delay(RELOAD_PAUSE_MS)
        runCatching {
            if (shelf == Shelf.LIKED) {
                liked = graph.favorites.mergeLiked(graph.library.starredSongs())
            } else {
                tiles = when (shelf) {
                    Shelf.ALBUMS -> graph.library.albums("name").map { TvItem.OfAlbum(it) }
                    Shelf.ARTISTS -> graph.library.artists().map { TvItem.OfArtist(it) }
                    Shelf.PLAYLISTS -> graph.playlists.removed.visible(graph.library.playlists()).map { TvItem.OfPlaylist(it) }
                    Shelf.GENRES -> graph.library.genres(GENRES).map { TvItem.OfGenre(it) }
                    Shelf.LIKED -> emptyList()
                }
            }
            shown = shelf
        }.onFailure { AppLog.w("Tv", "Couldn't read the library's ${shelf.label.lowercase()}: ${it.message}") }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.Page)) {
        Row(Modifier.padding(horizontal = TV_EDGE), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
            for (s in Shelf.entries) TvButton(s.label, null, onClick = { shelf = s }, strong = s == shelf)
        }
        when {
            shown != shelf -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            shelf == Shelf.LIKED -> {
                val songs = liked.orEmpty()
                if (songs.isEmpty()) EmptyNote("Songs you like show here.") else SongList(songs, header = {
                    PlayButtons(play = { songs }, note = "${songs.size} songs")
                })
            }
            tiles.isNullOrEmpty() -> EmptyNote("Nothing here yet.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(TILE_WIDTH),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = TV_EDGE, vertical = Space.Page),
                horizontalArrangement = Arrangement.spacedBy(Space.Section),
                verticalArrangement = Arrangement.spacedBy(Space.Section),
            ) {
                items(tiles.orEmpty(), key = { it.key }) { item -> TvItemTile(item) }
            }
        }
    }
}

@Composable
fun TvAlbum(id: String) {
    val graph = LocalGraph.current
    val nav = LocalNav.current
    val detail by produceState<Pair<Album, List<Song>>?>(null, id) {
        value = runCatching {
            val album = graph.library.album(id)
            if (album != null) album to graph.library.songsOf(album) else graph.source.album(id).let { it.album to it.songs }
        }.onFailure { AppLog.w("Tv", "Couldn't open album $id: ${it.message}") }.getOrNull()
    }
    val d = detail ?: return Loading()
    val (album, songs) = d
    val about = listOfNotNull(album.year?.toString(), "${songs.size} songs").joinToString(" · ")
    val artistId = album.artistId
    SongsPage(
        album.name, album.artist, about, album.coverArt, songs,
        shortcut = Shortcut("album/$id", album.name, album.coverArt),
        extra = if (artistId != null) {
            { TvButton("Go to artist", null, onClick = { nav.navigate(TvRoutes.artist(artistId)) }) }
        } else {
            null
        },
    )
}

@Composable
fun TvPlaylist(id: String) {
    val graph = LocalGraph.current
    val detail by produceState<app.siansa.data.PlaylistDetail?>(null, id) {
        value = runCatching { graph.playlistCache.get(id) }.onFailure { AppLog.w("Tv", "Couldn't open playlist $id: ${it.message}") }.getOrNull()
    }
    val d = detail ?: return Loading()
    val cover = d.playlist.coverArt ?: d.songs.firstOrNull()?.cover
    SongsPage(d.playlist.name, "Playlist", "${d.songs.size} songs", cover, d.songs, shortcut = Shortcut("playlist/$id", d.playlist.name, cover))
}

@Composable
fun TvMix(id: String) {
    val graph = LocalGraph.current
    val mix by produceState<app.siansa.data.Mix?>(null, id) { value = runCatching { graph.mixes.get(id) }.getOrNull() }
    val m = mix ?: return Loading()
    val cover = m.songs.firstOrNull()?.cover
    SongsPage(m.title, m.description.ifBlank { "Mix" }, "${m.songs.size} songs", cover, m.songs, shortcut = Shortcut("mix/$id", m.title, cover))
}

@Composable
fun TvArtist(id: String) {
    val graph = LocalGraph.current
    val play = rememberTvPlay()
    val artist by produceState<Artist?>(null, id) { value = graph.library.artist(id) }
    val albums by produceState<List<TvItem>?>(null, artist) {
        value = artist?.let { a -> graph.library.albumsOf(a).map { TvItem.OfAlbum(it, it.year?.toString()) } }
    }
    val a = artist ?: return Loading()
    val list = albums ?: return Loading()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(TILE_WIDTH),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TV_EDGE, vertical = Space.Page),
        horizontalArrangement = Arrangement.spacedBy(Space.Section),
        verticalArrangement = Arrangement.spacedBy(Space.Section),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.Page)) {
                Text(a.name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.Medium), verticalAlignment = Alignment.CenterVertically) {
                    PlayButtons(play = { graph.library.songsOf(a) }, note = null, shortcut = Shortcut("artist/$id", a.name, a.coverArt))
                    TvButton("Artist radio", null, onClick = { play.artistRadio(a) })
                    Text("${list.size} albums", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(list, key = { it.key }) { item -> TvItemTile(item) }
    }
}

/** What playing a page as a whole notes as played last (the phone's Home shortcuts, the TV's Jump back in). */
data class Shortcut(val route: String, val title: String, val cover: String?)

/** An album, playlist or mix: its cover and buttons on the left, its songs on the right. */
@Composable
private fun SongsPage(
    title: String,
    byline: String,
    about: String,
    coverId: String?,
    songs: List<Song>,
    shortcut: Shortcut?,
    extra: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxSize().padding(horizontal = TV_EDGE), horizontalArrangement = Arrangement.spacedBy(Space.Large)) {
        Column(Modifier.width(SIDE_WIDTH), verticalArrangement = Arrangement.spacedBy(Space.Page)) {
            Cover(coverId, LARGE_COVER, Modifier.size(SIDE_WIDTH), RoundedCornerShape(Space.Medium))
            Column(verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(byline, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(about, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PlayButtons(play = { songs }, note = null, shortcut = shortcut)
            extra?.invoke()
        }
        Box(Modifier.weight(1f)) {
            if (songs.isEmpty()) EmptyNote("No songs here.") else SongList(songs, shortcut = shortcut, edge = false)
        }
    }
}

/** Songs, one under the other: OK plays from that song, and the button at the end of each has its other actions. */
@Composable
fun SongList(songs: List<Song>, shortcut: Shortcut? = null, edge: Boolean = true, header: (@Composable () -> Unit)? = null) {
    val graph = LocalGraph.current
    val player by graph.player.state.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = if (edge) TV_EDGE else 0.dp, end = if (edge) TV_EDGE else 0.dp, bottom = Space.Large),
        verticalArrangement = Arrangement.spacedBy(Space.Tight),
    ) {
        if (header != null) item("header") { Box(Modifier.padding(bottom = Space.Page)) { header() } }
        itemsIndexed(songs, key = { i, s -> "$i:${s.id}" }) { i, song ->
            SongRow(i + 1, song, playing = player.current?.id == song.id) {
                graph.player.play(songs, startIndex = i)
                shortcut?.let { s -> graph.scope.launch { graph.recentPlays.note(s.route, s.title, s.cover) } }
            }
        }
    }
}

/** Play and Shuffle for a list of songs, read when pressed. */
@Composable
fun PlayButtons(play: suspend () -> List<Song>, note: String?, shortcut: Shortcut? = null) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    fun start(shuffle: Boolean) {
        scope.launch {
            val songs = play().takeIf { it.isNotEmpty() } ?: return@launch
            graph.player.play(songs, shuffle = shuffle)
            shortcut?.let { s -> graph.recentPlays.note(s.route, s.title, s.cover) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.Medium), verticalAlignment = Alignment.CenterVertically) {
        TvButton("Play", Icons.Rounded.PlayArrow, strong = true, onClick = { start(false) })
        TvButton("Shuffle", Icons.Rounded.Shuffle, onClick = { start(true) })
        if (note != null) Text(note, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SongRow(number: Int, song: Song, playing: Boolean, onClick: () -> Unit) {
    val graph = LocalGraph.current
    val likes by graph.favorites.changes.collectAsStateWithLifecycle()
    val liked = likes[song.id] ?: song.starred
    var actions by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Space.Small)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
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
            if (liked) Icon(Icons.Rounded.Favorite, "Liked", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(LIKED_ICON))
            Text(minutes(song.durationSec * 1000L), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier.size(MORE_SIZE).tvFocus(CircleShape, onClick = { actions = true }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MoreVert, "More for ${song.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (actions) SongActions(song, liked, onDismiss = { actions = false })
}

/** A song's other actions: play it next, add it to the queue, like it, start its radio, go to its album or artist. */
@Composable
fun SongActions(song: Song, liked: Boolean, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val nav = LocalNav.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    fun say(text: String) { scope.launch { snack.showSnackbar(text) } }
    val choices = buildList<Pair<String, () -> Unit>> {
        add("Play next" to { graph.player.playNext(song); say("Plays next: ${song.title}") })
        add("Add to queue" to { graph.player.addToQueue(song); say("Added to the queue: ${song.title}") })
        add((if (liked) "Unlike" else "Like") to { graph.favorites.toggle(song) })
        // Steady or DJ, asked the way the phone asks (RadioChooserHost, in the TV's root too).
        add("Start song radio" to { app.siansa.ui.RadioChooser.song.value = song })
        song.albumId?.let { id -> add("Go to album" to { nav.navigate(TvRoutes.album(id)) }) }
        song.artistId?.let { id -> add("Go to artist" to { nav.navigate(TvRoutes.artist(id)) }) }
    }
    TvChoices(song.title, choices, onDismiss)
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
private val MORE_SIZE = 48.dp
private val LIKED_ICON = 20.dp
private const val ROW_GROW = 1.02f
private const val GENRES = 200
private const val RELOAD_PAUSE_MS = 4_000L
