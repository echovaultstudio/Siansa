package app.musix.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.musix.AppGraph
import app.musix.data.Album
import app.musix.data.Artist
import app.musix.data.ArtistInfo
import app.musix.data.SimilarArtist
import app.musix.data.Song
import coil3.compose.AsyncImage

private data class ArtistPage(val artist: Artist, val albums: List<Album>, val songs: List<Song>)

/**
 * An artist with what Navidrome knows about them: photo, biography, top songs and similar artists
 * (from Navidrome's agents, or AudioMuse when its plugin is installed), plus their albums.
 */
@Composable
fun ArtistScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val page by loadState(id) {
        val index = graph.library.current
        val local = index?.artist(id)
        if (index != null && local != null) {
            ArtistPage(local, index.albumsOf(local).sortedByDescending { it.year ?: 0 }, index.songsOf(local))
        } else {
            val remote = graph.subsonic.artist(id)
            ArtistPage(remote.artist, remote.albums.sortedByDescending { it.year ?: 0 }, emptyList())
        }
    }
    val info by loadState("info-$id") { graph.subsonic.artistInfo(id) }
    val title = page.valueOrNull()?.artist?.name ?: "Artist"

    Screen(title = title, nav = nav) { padding ->
        Box(Modifier.padding(top = padding.calculateTopPadding())) {
            LoadBox(page) { p ->
                val artistInfo = info.valueOrNull()
                val top by loadState("top-$id") { loadTopSongs(graph, p) }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    full { Header(p, artistInfo) }
                    artistInfo?.biography?.let { bio -> full { Biography(bio) } }

                    val topSongs = top.valueOrNull().orEmpty()
                    if (topSongs.isNotEmpty()) {
                        full { SectionTitle("Top songs") }
                        itemsIndexed(topSongs, key = { i, s -> "top-$i-${s.id}" }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { index, song ->
                            SongRow(song = song, onClick = { graph.player.play(topSongs, index) })
                        }
                    }

                    if (p.albums.isNotEmpty()) {
                        full { SectionTitle(if (p.albums.size == 1) "Album" else "Albums") }
                        items(p.albums, key = { "album-${it.id}" }) { album ->
                            AlbumTile(
                                name = album.name,
                                subtitle = album.year?.toString() ?: "",
                                coverArt = album.coverArt,
                                onClick = { nav.navigate("album/${album.id}") },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }

                    val similar = artistInfo?.similar.orEmpty()
                    if (similar.isNotEmpty()) {
                        full { SectionTitle("Similar artists") }
                        full { SimilarRow(similar, nav) }
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** Navidrome's top songs, matched to the synced library; the artist's own songs when Navidrome has none. */
private suspend fun loadTopSongs(graph: AppGraph, page: ArtistPage): List<Song> {
    val index = graph.library.current
    val remote = runCatching { graph.subsonic.topSongs(page.artist.name, 10) }.getOrDefault(emptyList())
    if (remote.isNotEmpty()) return remote.map { index?.song(it.id) ?: it }
    return page.songs.shuffled().take(10)
}

@Composable
private fun Header(page: ArtistPage, info: ArtistInfo?) {
    val graph = LocalGraph.current
    Column {
        Box(Modifier.fillMaxWidth().aspectRatio(1.4f)) {
            val photo = info?.imageUrl ?: page.artist.imageUrl
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Cover(page.artist.coverArt, null, Modifier.fillMaxSize(), RectangleShape)
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface), startY = 200f)
                )
            )
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(page.artist.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                val songs = page.songs.size
                Text(
                    listOfNotNull(
                        "${page.albums.size} ${if (page.albums.size == 1) "album" else "albums"}",
                        if (songs > 0) "$songs songs" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (page.songs.isNotEmpty()) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { PlayButtons(page.songs) }
        } else if (page.albums.isNotEmpty()) {
            // Before a sync, the artist's songs are loaded album by album when Play is pressed.
            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                val loaded by loadState("songs-${page.artist.id}") {
                    page.albums.flatMap { runCatching { graph.subsonic.album(it.id).songs }.getOrDefault(emptyList()) }
                }
                PlayButtons(loaded.valueOrNull().orEmpty())
            }
        }
    }
}

@Composable
private fun Biography(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp).animateContentSize()) {
        Text(
            "About",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { expanded = !expanded },
        )
        if (text.length > 240) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Read more") }
        }
    }
}

@Composable
private fun SimilarRow(similar: List<SimilarArtist>, nav: NavController) {
    val graph = LocalGraph.current
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(similar, key = { it.id ?: it.name }) { artist ->
            // Prefer the artist as synced on the phone; Navidrome only lists artists you have.
            val local = graph.library.current?.let { index ->
                artist.id?.let { index.artist(it) } ?: index.artistByName(artist.name)
            }
            val target = local?.id ?: artist.id
            Column(
                Modifier
                    .width(100.dp)
                    .clickable(enabled = target != null) { target?.let { nav.navigate("artist/$it") } }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val photo = artist.imageUrl ?: local?.imageUrl
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                } else {
                    Cover(artist.coverArt ?: local?.coverArt, 150, Modifier.size(84.dp), CircleShape)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    artist.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
