package app.musix.ui

import androidx.compose.foundation.clickable
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
import app.musix.data.AlbumDetail
import app.musix.data.LibraryIndex
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
    val library by graph.library.index.collectAsStateWithLifecycle()
    val loaded by graph.library.loaded.collectAsStateWithLifecycle()
    val progress by graph.sync.progress.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    Screen(
        title = "Library",
        nav = null,
        actions = {
            IconButton(onClick = { nav.navigate("sync") }) {
                if (progress.running) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Sync, "Library sync")
            }
        },
    ) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            val index = library
            when {
                index != null -> {
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Albums") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Artists") })
                    }
                    if (tab == 0) AlbumGrid(index, nav) else ArtistList(index, nav)
                }
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> SyncPrompt(nav)
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
            "Sync it once to browse albums and artists. You choose which Navidrome libraries to include.",
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
private fun AlbumGrid(index: LibraryIndex, nav: NavController) {
    var sort by rememberSaveable { mutableStateOf("alphabeticalByName") }
    val albums = remember(index, sort) { index.sortedAlbums(sort) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                albumSorts.forEach { (key, label) ->
                    FilterChip(selected = sort == key, onClick = { sort = key }, label = { Text(label, maxLines = 1) })
                }
            }
        }
        items(albums, key = { it.id }) { album ->
            AlbumTile(
                name = album.name,
                subtitle = listOfNotNull(album.artist.takeIf { it.isNotBlank() }, album.year?.toString()).joinToString(" · "),
                coverArt = album.coverArt,
                onClick = { nav.navigate("album/${album.id}") },
            )
        }
    }
}

@Composable
private fun ArtistList(index: LibraryIndex, nav: NavController) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(index.artists, key = { it.id }) { artist ->
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

/** An album from the synced library (all merged copies together), or straight from Navidrome before a sync. */
@Composable
fun AlbumScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val detail by loadState(id) {
        val index = graph.library.current
        val album = index?.album(id)
        if (index != null && album != null && index.songsOf(album).isNotEmpty()) {
            AlbumDetail(album, index.songsOf(album))
        } else {
            graph.subsonic.album(id)
        }
    }
    val title = detail.valueOrNull()?.album?.name ?: "Album"
    Screen(title = title, nav = nav) { padding ->
        Box(Modifier.padding(top = padding.calculateTopPadding())) {
            LoadBox(detail) { d ->
                val multiDisc = d.songs.mapNotNull { it.disc }.distinct().size > 1
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Cover(d.album.coverArt, null, Modifier.fillMaxWidth(0.7f).aspectRatio(1f), RoundedCornerShape(12.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                d.album.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                d.album.artist,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(enabled = d.album.artistId != null) {
                                    d.album.artistId?.let { nav.navigate("artist/$it") }
                                },
                            )
                            val total = d.songs.sumOf { it.durationSec.toLong() }
                            Text(
                                listOfNotNull(d.album.year?.toString(), d.album.genre, "${d.songs.size} tracks", formatDuration(total))
                                    .joinToString(" · "),
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
