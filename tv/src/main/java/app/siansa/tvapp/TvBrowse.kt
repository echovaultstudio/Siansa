package app.siansa.tvapp

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
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
import app.siansa.data.ArtistRef
import app.siansa.data.Song
import app.siansa.data.cover
import app.siansa.data.isFavouriteAlbum
import app.siansa.log.AppLog
import app.siansa.ui.AlbumTag
import app.siansa.ui.Cover
import app.siansa.ui.LARGE_COVER
import app.siansa.ui.LocalGraph
import app.siansa.ui.LocalNav
import app.siansa.ui.LocalSnack
import app.siansa.ui.Space
import app.siansa.ui.albumTagRoute
import app.siansa.ui.openSongDetails
import app.siansa.ui.openSongPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The Library's lists. */
private enum class Shelf(val label: String) {
    ALBUMS("Albums"), ARTISTS("Artists"), PLAYLISTS("Playlists"), LIKED("Liked songs"),
    FAVOURITE_ALBUMS("Favourite albums"), FAVOURITE_ARTISTS("Favourite artists"), GENRES("Genres"),
}

/** Albums, artists, playlists, favourites and genres in grids of covers, and the liked songs as a list. */
@Composable
fun TvLibrary() {
    val graph = LocalGraph.current
    val nav = LocalNav.current
    val version by graph.library.version.collectAsStateWithLifecycle()
    val likes by graph.favorites.changes.collectAsStateWithLifecycle()
    val favouriteAlbums by graph.favouriteAlbums.ids.collectAsStateWithLifecycle()
    val favouriteArtists by graph.favouriteArtists.ids.collectAsStateWithLifecycle()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    var shelf by rememberSaveable { mutableStateOf(Shelf.ALBUMS) }
    var tiles by remember { mutableStateOf<List<TvItem>?>(null) }
    var liked by remember { mutableStateOf<List<Song>?>(null) }
    var shown by remember { mutableStateOf<Shelf?>(null) }
    // As on Home: read again once a sync has stopped changing the library for a moment, keeping what's shown meanwhile.
    LaunchedEffect(shelf, version, likes, favouriteAlbums, favouriteArtists) {
        if (shown == shelf) delay(RELOAD_PAUSE_MS)
        runCatching {
            if (shelf == Shelf.LIKED) {
                // As the phone's Liked songs: Navidrome's list, which the library may not have marked yet.
                liked = app.siansa.ui.likedSongs(graph)
            } else {
                tiles = when (shelf) {
                    Shelf.ALBUMS -> graph.library.albums("name").map { TvItem.OfAlbum(it) }
                    Shelf.ARTISTS -> graph.library.artists().map { TvItem.OfArtist(it) }
                    Shelf.PLAYLISTS -> graph.playlists.removed.visible(graph.library.playlists()).map { TvItem.OfPlaylist(it) }
                    Shelf.FAVOURITE_ALBUMS -> graph.favouriteAlbums.refresh().filter { isFavouriteAlbum(it, favouriteAlbums) }
                        .map { a -> graph.library.album(a.id) ?: a }.distinctBy { it.id }.map { TvItem.OfAlbum(it) }
                    Shelf.FAVOURITE_ARTISTS -> graph.favouriteArtists.refresh().filter { it.id in favouriteArtists }.map { TvItem.OfArtist(it) }
                    Shelf.GENRES -> graph.library.genres(GENRES).map { TvItem.OfGenre(it) }
                    Shelf.LIKED -> emptyList()
                }
            }
            shown = shelf
        }.onFailure { AppLog.w("Tv", "Couldn't read the library's ${shelf.label.lowercase()}: ${it.message}") }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = TV_EDGE, vertical = Space.Medium),
            horizontalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            for (s in Shelf.entries) {
                TvButton(s.label, null, onClick = { shelf = s }, strong = s == shelf, modifier = if (s == Shelf.ALBUMS) Modifier.initialFocus() else Modifier)
            }
            // The phone's pages for these: your listening by year, and (with AudioMuse) the sound of your library.
            TvButton("Time machine", null, onClick = { nav.navigate("timemachine") })
            if (settings.audioMuseReady) TvButton("Your sound", null, onClick = { nav.navigate("fingerprint") })
        }
        val empty = when (shelf) {
            Shelf.FAVOURITE_ALBUMS -> "No favourite albums yet. Press the heart on an album's page to add it."
            Shelf.FAVOURITE_ARTISTS -> "No favourite artists yet. Press the heart on an artist's page to add them."
            else -> "Nothing here yet."
        }
        when {
            shown != shelf -> Loading()
            shelf == Shelf.LIKED -> {
                val songs = liked.orEmpty()
                if (songs.isEmpty()) {
                    EmptyNote("Songs you like show here. Like one from its actions or in Now playing.")
                } else {
                    SongList(songs, header = { PlayButtons(play = { songs }, note = "${songs.size} songs") })
                }
            }
            tiles.isNullOrEmpty() -> EmptyNote(empty)
            // Six across, as many as Home's rows show.
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = TV_EDGE, vertical = Space.Page),
                horizontalArrangement = Arrangement.spacedBy(Space.Page),
                verticalArrangement = Arrangement.spacedBy(Space.Section),
            ) {
                items(tiles.orEmpty(), key = { it.key }) { item -> TvItemTile(item, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
fun TvAlbum(id: String) {
    val graph = LocalGraph.current
    val nav = LocalNav.current
    val favourites by graph.favouriteAlbums.ids.collectAsStateWithLifecycle()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val detail by produceState<Pair<Album, List<Song>>?>(null, id) {
        value = runCatching {
            val album = graph.library.album(id)
            if (album != null) album to graph.library.songsOf(album) else graph.source.album(id).let { it.album to it.songs }
        }.onFailure { AppLog.w("Tv", "Couldn't open album $id: ${it.message}") }.getOrNull()
    }
    val d = detail ?: return Loading()
    val (album, songs) = d
    val favourite = isFavouriteAlbum(album, favourites)
    val total = songs.sumOf { it.durationSec } / 60
    // Every artist on the album, each opening their page: the album's own, then the songs' (a compilation, features).
    val artists = (listOfNotNull(album.artistId?.let { ArtistRef(it, album.artist) }) + songs.flatMap { it.artists })
        .distinctBy { it.id }.take(MAX_ARTISTS)
    CollectionPage(
        title = album.name,
        coverId = album.coverArt,
        songs = songs,
        shortcut = Shortcut("album/$id", album.name, album.coverArt),
        about = listOfNotNull(album.year?.toString(), "${songs.size} songs", "$total min".takeIf { total > 0 }).joinToString(" · "),
        animated = true,
        values = {
            if (artists.isEmpty()) {
                Text(album.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            } else {
                for (a in artists) TvChip(a.name, onClick = { nav.navigate(TvRoutes.artist(a.id)) }, strong = true)
            }
            album.genre?.takeIf { it.isNotBlank() }?.let { g -> TvChip(g, onClick = { nav.navigate(albumTagRoute(AlbumTag.GENRE, g)) }) }
            album.labels.firstOrNull()?.let { l -> TvChip(l, onClick = { nav.navigate(albumTagRoute(AlbumTag.LABEL, l)) }) }
        },
        buttons = {
            TvButton(
                if (favourite) "Favourite" else "Add to favourites",
                if (favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                onClick = { graph.favouriteAlbums.toggle(album) },
            )
            if (settings.audioMuseReady) TvButton("Similar albums", null, onClick = { nav.navigate("similar/albums/${android.net.Uri.encode(id)}") })
        },
    )
}

@Composable
fun TvPlaylist(id: String) {
    val graph = LocalGraph.current
    val detail by produceState<app.siansa.data.PlaylistDetail?>(null, id) {
        value = runCatching { graph.playlistCache.get(id) }.onFailure { AppLog.w("Tv", "Couldn't open playlist $id: ${it.message}") }.getOrNull()
    }
    val nav = LocalNav.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val d = detail ?: return Loading()
    // The playlist's own cover from the server (one picked for it, or the one Navidrome makes); a song's only without.
    val cover = d.playlist.coverArt ?: d.songs.firstOrNull()?.cover
    val total = d.songs.sumOf { it.durationSec } / 60
    CollectionPage(
        title = d.playlist.name,
        coverId = cover,
        songs = d.songs,
        shortcut = Shortcut("playlist/$id", d.playlist.name, cover),
        about = listOfNotNull("Playlist", "${d.songs.size} songs", "$total min".takeIf { total > 0 }).joinToString(" · "),
        note = d.playlist.comment?.takeIf { it.isNotBlank() },
        // AudioMuse's song alchemy: songs that blend the sound of this playlist's songs.
        buttons = if (settings.audioMuseReady) {
            { TvButton("Song alchemy", null, onClick = { nav.navigate("blend/${android.net.Uri.encode(id)}") }) }
        } else {
            null
        },
    )
}

@Composable
fun TvMix(id: String) {
    val graph = LocalGraph.current
    val mix by produceState<app.siansa.data.Mix?>(null, id) { value = runCatching { graph.mixes.get(id) }.getOrNull() }
    val m = mix ?: return Loading()
    val cover = m.songs.firstOrNull()?.cover
    CollectionPage(
        title = m.title,
        coverId = cover,
        songs = m.songs,
        shortcut = Shortcut("mix/$id", m.title, cover),
        about = listOfNotNull(m.description.ifBlank { "Mix" }, "${m.songs.size} songs").joinToString(" · "),
    )
}

/**
 * An artist: their picture and buttons, then their top songs, their albums and similar artists, one under the other.
 * Down from the buttons goes straight to the first song.
 */
@Composable
fun TvArtist(id: String) {
    val graph = LocalGraph.current
    val nav = LocalNav.current
    val play = rememberTvPlay()
    val favourites by graph.favouriteArtists.ids.collectAsStateWithLifecycle()
    val player by graph.player.state.collectAsStateWithLifecycle()
    val artist by produceState<Artist?>(null, id) {
        value = runCatching { graph.library.artist(id) ?: graph.source.artist(id).artist }
            .onFailure { AppLog.w("Tv", "Couldn't open artist $id: ${it.message}") }.getOrNull()
    }
    val albums by produceState<List<Album>?>(null, artist) {
        val a = artist ?: return@produceState
        value = runCatching { graph.library.albumsOf(a).ifEmpty { graph.source.artist(a.id).albums } }.getOrDefault(emptyList())
    }
    val top by produceState<List<Song>>(emptyList(), artist) {
        val a = artist ?: return@produceState
        value = runCatching { graph.source.topSongs(a.name, TOP_SONGS) }.getOrDefault(emptyList())
    }
    val similar by produceState<List<Artist>>(emptyList(), artist) {
        val a = artist ?: return@produceState
        value = runCatching {
            graph.source.artistInfo(a.id).similar.mapNotNull { s -> s.id?.let { Artist(id = it, name = s.name, coverArt = s.coverArt) } }
        }.getOrDefault(emptyList())
    }
    val a = artist ?: return Loading()
    val favourite = a.id in favourites
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.Large), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
        item("header") {
            Row(
                Modifier.padding(start = TV_EDGE, end = TV_EDGE, bottom = Space.Page),
                horizontalArrangement = Arrangement.spacedBy(Space.Large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(a.coverArt, LARGE_COVER, Modifier.size(HEADER_COVER), CircleShape)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    Text(a.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    albums?.let { Text("${it.size} albums", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.Tight, vertical = Space.Medium), horizontalArrangement = Arrangement.spacedBy(Space.Page)) {
                        PlayButtons(play = { graph.library.songsOf(a).ifEmpty { top } }, note = null, shortcut = Shortcut("artist/$id", a.name, a.coverArt), focusFirst = true)
                        TvButton("Artist radio", Icons.Rounded.Radio, onClick = { play.artistRadio(a) })
                        TvButton(
                            if (favourite) "Favourite" else "Add to favourites",
                            if (favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            onClick = { graph.favouriteArtists.toggle(a) },
                        )
                    }
                }
            }
        }
        if (top.isNotEmpty()) {
            item("top-title") { SectionTitle("Top songs") }
            itemsIndexed(top.take(TOP_SHOWN), key = { i, s -> "top:$i:${s.id}" }) { i, song ->
                Box(Modifier.padding(horizontal = TV_EDGE)) {
                    SongRow(i + 1, song, playing = player.current?.id == song.id) { graph.player.play(top, startIndex = i) }
                }
            }
        }
        albums?.takeIf { it.isNotEmpty() }?.let { list ->
            item("albums") {
                Box(Modifier.padding(top = Space.Section)) {
                    TvItemRow(TvRow("Albums", list.sortedByDescending { it.year ?: 0 }.map { TvItem.OfAlbum(it, it.year?.toString()) }))
                }
            }
        }
        if (similar.isNotEmpty()) {
            item("similar") { Box(Modifier.padding(top = Space.Section)) { TvItemRow(TvRow("Similar artists", similar.map { TvItem.OfArtist(it) })) } }
        }
        item("appears") {
            Row(Modifier.padding(start = TV_EDGE, end = TV_EDGE, top = Space.Section)) {
                TvButton("Appears on", null, onClick = { nav.navigate("artist/${android.net.Uri.encode(a.id)}/appears-on") })
            }
        }
    }
}

/** What playing a page as a whole notes as played last (the phone's Home shortcuts, the TV's Jump back in). */
data class Shortcut(val route: String, val title: String, val cover: String?)

/**
 * An album, playlist or mix as one list: the cover with the title, what it is ([values]: artists and genre to open),
 * Play, Shuffle and the page's own [buttons] at the top, then the songs. The remote starts on Play, and down goes
 * straight to the first song.
 */
@Composable
private fun CollectionPage(
    title: String,
    coverId: String?,
    songs: List<Song>,
    shortcut: Shortcut?,
    about: String,
    values: (@Composable () -> Unit)? = null,
    buttons: (@Composable () -> Unit)? = null,
    animated: Boolean = false,
    note: String? = null,
) {
    val graph = LocalGraph.current
    val player by graph.player.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.Large), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
        item("header") {
            Row(
                Modifier.padding(start = TV_EDGE, end = TV_EDGE, bottom = Space.Page),
                horizontalArrangement = Arrangement.spacedBy(Space.Large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // An album's animated cover moves here, as on the phone's album page.
                if (animated) {
                    app.siansa.ui.BigCover(coverId, coverId, Modifier.size(HEADER_COVER), RoundedCornerShape(Space.Medium))
                } else {
                    Cover(coverId, LARGE_COVER, Modifier.size(HEADER_COVER), RoundedCornerShape(Space.Medium))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (values != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Medium), verticalArrangement = Arrangement.spacedBy(Space.Medium)) { values() }
                    }
                    Text(about, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    note?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.Tight, vertical = Space.Medium), horizontalArrangement = Arrangement.spacedBy(Space.Page)) {
                        PlayButtons(play = { songs }, note = null, shortcut = shortcut, focusFirst = true)
                        buttons?.invoke()
                    }
                }
            }
        }
        if (songs.isEmpty()) {
            item("empty") {
                Text("No songs here.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = TV_EDGE))
            }
        }
        songRows(songs, player.current?.id, shortcut)
    }
}

/** The songs of a list, for a LazyColumn: OK plays from that song. */
private fun LazyListScope.songRows(songs: List<Song>, playingId: String?, shortcut: Shortcut?) {
    itemsIndexed(songs, key = { i, s -> "$i:${s.id}" }) { i, song ->
        val graph = LocalGraph.current
        Box(Modifier.padding(horizontal = TV_EDGE)) {
            SongRow(i + 1, song, playing = playingId == song.id) {
                graph.player.play(songs, startIndex = i)
                shortcut?.let { s -> graph.scope.launch { graph.recentPlays.note(s.route, s.title, s.cover) } }
            }
        }
    }
}

/** Songs, one under the other, with an optional [header] above them. */
@Composable
fun SongList(songs: List<Song>, shortcut: Shortcut? = null, header: (@Composable () -> Unit)? = null) {
    val graph = LocalGraph.current
    val player by graph.player.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.Large), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
        if (header != null) item("header") { Box(Modifier.padding(start = TV_EDGE, end = TV_EDGE, bottom = Space.Page)) { header() } }
        songRows(songs, player.current?.id, shortcut)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = TV_EDGE, end = TV_EDGE, top = Space.Page, bottom = Space.Small))
}

/** Play and Shuffle for a list of songs, read when pressed. */
@Composable
fun PlayButtons(play: suspend () -> List<Song>, note: String?, shortcut: Shortcut? = null, focusFirst: Boolean = false) {
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
        TvButton("Play", Icons.Rounded.PlayArrow, strong = true, onClick = { start(false) }, modifier = if (focusFirst) Modifier.initialFocus() else Modifier)
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
            Icon(Icons.Rounded.MoreVert, "More for ${song.title}", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
    if (actions) SongActions(song, liked, onDismiss = { actions = false })
}

/** A song's other actions: play it next, add it to the queue, like it, start its radio, go to its album or artists. */
@Composable
fun SongActions(song: Song, liked: Boolean, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val nav = LocalNav.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val radioSeed by graph.songRadio.seed.collectAsStateWithLifecycle()
    var pickPlaylist by remember { mutableStateOf(false) }
    fun say(text: String) { scope.launch { snack.showSnackbar(text) } }
    if (pickPlaylist) {
        // The phone's own: server, other users' and phone playlists, and a new one (issue #895).
        app.siansa.ui.AddToPlaylistDialog(listOf(song)) { message ->
            pickPlaylist = false
            if (message != null) say(message)
            onDismiss()
        }
        return
    }
    val choices = buildList<Pair<String, () -> Unit>> {
        add("Play next" to { graph.player.playNext(song); say("Plays next: ${song.title}") })
        add("Add to queue" to { graph.player.addToQueue(song); say("Added to the queue: ${song.title}") })
        add("Add to playlist" to { pickPlaylist = true })
        add((if (liked) "Unlike" else "Like") to { graph.favorites.toggle(song) })
        // As on the phone: while a song radio plays, this stops it; otherwise Steady or DJ is asked (RadioChooserHost).
        if (radioSeed != null) add("Stop song radio" to { graph.songRadio.stop(); say("Song radio off") })
        else add("Start song radio" to { app.siansa.ui.RadioChooser.song.value = song })
        // AudioMuse (or the server) finds songs that sound like it, on the phone's page for it.
        add("Similar songs" to { nav.navigate("similar/songs/${android.net.Uri.encode(song.id)}") })
        // AudioMuse's song path: from this song to another, through songs that sound in between.
        if (graph.settings.current.audioMuseReady) add("Song path from here" to { nav.openSongPath(song.id) })
        song.albumId?.let { id -> add("Go to album: ${song.album}" to { nav.navigate(TvRoutes.album(id)) }) }
        val artists = song.artists.ifEmpty { listOfNotNull(song.artistId?.let { ArtistRef(it, song.artist) }) }
        for (a in artists.take(MAX_ARTISTS)) add("Go to artist: ${a.name}" to { nav.navigate(TvRoutes.artist(a.id)) })
        add("Song details" to { nav.openSongDetails(song.id) })
    }
    TvChoices(song.title, choices, onDismiss, keepOpen = setOf("Add to playlist"))
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

private val HEADER_COVER = 180.dp
private val NUMBER_WIDTH = 32.dp
private val MORE_SIZE = 48.dp
private val LIKED_ICON = 20.dp
private const val ROW_GROW = 1.02f
private const val GENRES = 200
private const val TOP_SONGS = 10
private const val TOP_SHOWN = 5
private const val MAX_ARTISTS = 6
private const val RELOAD_PAUSE_MS = 4_000L
