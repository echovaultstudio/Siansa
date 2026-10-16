package app.musix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.musix.data.AlbumCategory
import app.musix.data.AlbumDetail
import app.musix.data.category
import app.musix.data.Song

private val albumSorts = listOf(
    "alphabeticalByName" to "A–Z",
    "newest" to "Recently added",
    "year" to "Year",
    "frequent" to "Most played",
    "random" to "Random",
)

@Composable
fun LibraryScreen(nav: NavController) {
    val graph = LocalGraph.current
    val status by graph.library.status.collectAsStateWithLifecycle()
    val loaded by graph.library.loaded.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("albums") }
    Screen(title = "Your library", nav = null) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            when {
                status != null -> {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("playlists" to "Playlists", "albums" to "Albums", "artists" to "Artists").forEach { (key, label) ->
                            FilterChip(selected = tab == key, onClick = { tab = key }, label = { Text(label) })
                        }
                    }
                    when (tab) {
                        "playlists" -> PlaylistList(nav)
                        "artists" -> ArtistList(nav)
                        else -> AlbumGrid(nav)
                    }
                }
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> SyncPrompt(nav)
            }
        }
    }
}

@Composable
private fun PlaylistList(nav: NavController) {
    val graph = LocalGraph.current
    val playlists by loadState("library-playlists") {
        runCatching { graph.subsonic.playlists() }
            .onSuccess { graph.library.updatePlaylists(it) }
            .getOrElse { graph.library.playlists() }
    }
    LoadBox(playlists) { list ->
        if (list.isEmpty()) {
            Text(
                "No playlists on Navidrome yet. Playlists you import or create there show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { nav.navigate("liked") }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(6.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF4B2BD6), Color(0xFF8FD3C8)))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Favorite, null, tint = Color.White) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Liked songs", style = MaterialTheme.typography.bodyLarge)
                        Text("Playlist · your hearts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(list, key = { it.id }) { playlist ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { nav.navigate("playlist/${playlist.id}") }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(playlist.coverArt, 150, Modifier.size(56.dp), RoundedCornerShape(6.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Playlist · ${playlist.songCount} songs" + (playlist.owner?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Songs with a heart, newest first as Navidrome lists them. */
@Composable
fun LikedScreen(nav: NavController) {
    val graph = LocalGraph.current
    val changes by graph.favorites.changes.collectAsStateWithLifecycle()
    val songs by loadState(changes) {
        runCatching { graph.subsonic.starredSongs() }
            .map { list ->
                val local = graph.library.songs(list.map { it.id }).associateBy { it.id }
                list.map { local[it.id] ?: it }
            }
            .getOrElse { if (graph.library.isSynced) graph.library.starredSongs() else throw it }
            .filter { graph.favorites.isLiked(it) }
    }
    Screen(title = "Liked songs", nav = nav) { padding ->
        Box(Modifier.padding(top = padding.calculateTopPadding())) {
            LoadBox(songs) { list ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Text("${list.size} songs", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            PlayButtons(list)
                        }
                    }
                    itemsIndexed(list, key = { i, s -> "$i-${s.id}" }) { index, song ->
                        SongRow(song = song, onClick = { graph.player.play(list, index) })
                    }
                }
            }
        }
    }
}

/** A Navidrome playlist, with songs matched to the synced library. */
@Composable
fun PlaylistScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val detail by loadState(id) {
        val remote = graph.subsonic.playlist(id)
        val local = graph.library.songs(remote.songs.map { it.id }).associateBy { it.id }
        remote.copy(songs = remote.songs.map { local[it.id] ?: it })
    }
    val title = detail.valueOrNull()?.playlist?.name ?: "Playlist"
    Screen(title = title, nav = nav) { padding ->
        Box(Modifier.padding(top = padding.calculateTopPadding())) {
            LoadBox(detail) { d ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Cover(d.playlist.coverArt, null, Modifier.fillMaxWidth(0.6f).aspectRatio(1f), RoundedCornerShape(12.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(d.playlist.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            d.playlist.comment?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            }
                            Text(
                                listOfNotNull(d.playlist.owner, "${d.songs.size} songs", formatDuration(d.songs.sumOf { it.durationSec.toLong() }))
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            PlayButtons(d.songs)
                        }
                    }
                    itemsIndexed(d.songs, key = { i, s -> "$i-${s.id}" }) { index, song ->
                        SongRow(song = song, onClick = { graph.player.play(d.songs, index) })
                    }
                }
            }
        }
    }
}

@Composable
fun SyncPrompt(nav: NavController) {
    val graph = LocalGraph.current
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Your library isn't on the phone yet", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            "Sync it once to browse albums and artists. You choose which Navidrome libraries to include (Settings → Library sync).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = {
            graph.sync.start()
            nav.navigate("sync")
        }) {
            Icon(Icons.Rounded.Sync, null)
            Spacer(Modifier.width(8.dp))
            Text("Sync library")
        }
    }
}

@Composable
private fun AlbumGrid(nav: NavController) {
    val graph = LocalGraph.current
    val version by graph.library.version.collectAsStateWithLifecycle()
    var sort by rememberSaveable { mutableStateOf("alphabeticalByName") }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    val loadedAlbums by loadState(Triple(version, sort, type)) {
        val sorted = graph.library.albums(sort)
        if (type == null) sorted else sorted.filter { it.category.name == type }
    }
    val categories by loadState(version to "categories") { graph.library.categories() }
    val albums = loadedAlbums.valueOrNull().orEmpty()
    val typeList: List<Pair<AlbumCategory, Int>> = categories.valueOrNull().orEmpty()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    albumSorts.forEach { (key, label) ->
                        FilterChip(selected = sort == key, onClick = { sort = key }, label = { Text(label, maxLines = 1) })
                    }
                }
                // Release types from the albums' tags: Album, EP, Single, Live, Compilation, DJ-mix…
                if (typeList.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = type == null, onClick = { type = null }, label = { Text("All types") })
                        typeList.forEach { (category, count) ->
                            FilterChip(
                                selected = type == category.name,
                                onClick = { type = if (type == category.name) null else category.name },
                                label = { Text("${category.plural} ($count)", maxLines = 1) },
                            )
                        }
                    }
                }
            }
        }
        items(albums, key = { it.id }) { album ->
            AlbumTile(
                name = album.name,
                subtitle = listOfNotNull(
                    album.artist.takeIf { it.isNotBlank() },
                    album.year?.toString(),
                    album.category.single.takeIf { album.category != AlbumCategory.ALBUM },
                ).joinToString(" · "),
                coverArt = album.coverArt,
                onClick = { nav.navigate("album/${album.id}") },
            )
        }
    }
}

@Composable
private fun ArtistList(nav: NavController) {
    val graph = LocalGraph.current
    val version by graph.library.version.collectAsStateWithLifecycle()
    val artists by loadState(version to "artists") { graph.library.artists() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists.valueOrNull().orEmpty(), key = { it.id }) { artist ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { nav.navigate("artist/${artist.id}") }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(artist.coverArt, 150, Modifier.size(48.dp), CircleShape)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(artist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (artist.albumCount == 1) "1 album" else "${artist.albumCount} albums",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * An album from the synced library (all merged copies together), or straight from Navidrome before a
 * sync. The cover fills the top of the screen and blends away behind the track list as it scrolls.
 */
@Composable
fun AlbumScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val detail by loadState(id) {
        val album = graph.library.album(id)
        val songs = album?.let { graph.library.songsOf(it) }.orEmpty()
        if (album != null && songs.isNotEmpty()) AlbumDetail(album, songs) else graph.subsonic.album(id)
    }
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 100_000f }
    }
    val hero = LocalConfiguration.current.screenWidthDp.dp.coerceAtMost(440.dp)
    val d = detail.valueOrNull()

    HeroPage(
        title = d?.album?.name ?: "Album",
        heroHeight = hero,
        scrolledPx = scrolled,
        onBack = { nav.popBackStack() },
        image = { modifier -> Cover(d?.album?.coverArt, null, modifier, RectangleShape) },
    ) { cardModifier ->
        if (d == null) {
            Box(Modifier.padding(top = hero)) { LoadBox(detail) { } }
            return@HeroPage
        }
        val multiDisc = d.songs.mapNotNull { it.disc }.distinct().size > 1
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().then(cardModifier), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { HeroSpacer(hero) }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(d.album.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        d.album.artist,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(enabled = d.album.artistId != null) {
                            d.album.artistId?.let { nav.navigate("artist/$it") }
                        },
                    )
                    val total = d.songs.sumOf { it.durationSec.toLong() }
                    Text(
                        listOfNotNull(
                            d.album.category.single,
                            d.album.year?.toString(),
                            d.album.genre,
                            "${d.songs.size} tracks",
                            formatDuration(total),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    PlayButtons(d.songs)
                }
            }
            itemsIndexed(d.songs, key = { i, s -> "$i-${s.id}" }) { index, song ->
                val number = song.track?.let { t -> if (multiDisc && song.disc != null) "${song.disc}.$t" else "$t" }
                SongRow(
                    song = song,
                    onClick = { graph.player.play(d.songs, index) },
                    leading = number ?: "${index + 1}",
                    showCover = false,
                )
            }
        }
    }
}

@Composable
fun PlayButtons(songs: List<Song>) {
    val graph = LocalGraph.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { graph.player.play(songs) }, enabled = songs.isNotEmpty()) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(4.dp))
            Text("Play")
        }
        FilledTonalButton(onClick = { graph.player.play(songs, shuffle = true) }, enabled = songs.isNotEmpty()) {
            Icon(Icons.Rounded.Shuffle, null)
            Spacer(Modifier.width(4.dp))
            Text("Shuffle")
        }
    }
}

@Composable
fun MixScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val mix by loadState(id) { graph.mixes.get(id) ?: throw IllegalStateException("This mix is no longer saved") }
    val title = mix.valueOrNull()?.title ?: "Mix"
    Screen(title = title, nav = nav) { padding ->
        Box(Modifier.padding(top = padding.calculateTopPadding())) {
            LoadBox(mix) { m ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            CoverMosaic(m, Modifier.size(160.dp).align(Alignment.CenterHorizontally))
                            Spacer(Modifier.height(12.dp))
                            Text(m.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            if (m.description.isNotBlank()) Text(m.description, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Sound search: ${m.query}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            PlayButtons(m.songs)
                        }
                    }
                    itemsIndexed(m.songs, key = { i, s -> "$i-${s.id}" }) { index, song ->
                        SongRow(song = song, onClick = { graph.player.play(m.songs, index) })
                    }
                }
            }
        }
    }
}
