package app.musix.ui

import app.musix.data.extraArtists
import app.musix.data.normalizeKey
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
import app.musix.AppGraph
import app.musix.data.AlbumCategory
import app.musix.data.AlbumDetail
import app.musix.data.category
import app.musix.data.Song
import app.musix.log.AppLog
import kotlinx.coroutines.CancellationException

/** Songs with a heart, newest first as Navidrome lists them. */
@Composable
fun LikedScreen(nav: NavController) {
    val graph = LocalGraph.current
    val changes by graph.favorites.changes.collectAsStateWithLifecycle()
    val songs by loadState(changes) {
        runCatching { graph.subsonic.starredSongs() }
            .map { list ->
                val local = graph.library.songs(list.map { it.id }).associateBy { it.id }
                graph.library.keepSyncedSongs(list.map { local[it.id] ?: it }, "Liked songs")
            }
            .getOrElse { if (graph.library.isSynced) graph.library.starredSongs() else throw it }
            .filter { graph.favorites.isLiked(it) }
    }
    val heart = Color(0xFF4B2BD6) to Color(0xFF8FD3C8)
    CollectionPage(
        nav = nav,
        title = "Liked songs",
        color = lerp(heart.first, Color.Black, 0.2f),
        songs = songs.valueOrNull(),
        error = (songs as? Load.Err)?.message,
        cover = { m ->
            Box(m.clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(heart.first, heart.second))), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(72.dp))
            }
        },
        header = { CollectionByline(null, null, null, "${songs.valueOrNull()?.size ?: 0} songs") },
    )
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
    val d = detail.valueOrNull()
    CollectionPage(
        nav = nav,
        title = d?.playlist?.name ?: "Playlist",
        color = rememberCoverColor(d?.playlist?.coverArt),
        songs = d?.songs,
        error = (detail as? Load.Err)?.message,
        cover = { m -> Cover(d?.playlist?.coverArt, LARGE_COVER, m, RoundedCornerShape(6.dp)) },
        header = {
            d?.playlist?.comment?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
            }
            CollectionByline(
                d?.playlist?.owner,
                null,
                null,
                if (d == null) "" else "Playlist · ${d.songs.size} songs · ${formatDuration(d.songs.sumOf { it.durationSec.toLong() })}",
            )
        },
    )
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

/**
 * An album from the synced library (all merged copies together), or straight from Navidrome before a
 * sync, in the Spotify layout with the cover's colour behind the header.
 */
@Composable
fun AlbumScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val detail by loadState(id) {
        val album = graph.library.album(id)
        val songs = album?.let { graph.library.songsOf(it) }.orEmpty()
        if (album != null && songs.isNotEmpty()) AlbumDetail(album, songs) else graph.subsonic.album(id)
    }
    val d = detail.valueOrNull()
    val artistCover by loadState(d?.album?.artistId) { d?.album?.artistId?.let { graph.library.artist(it)?.coverArt } }
    // Disc titles aren't kept in the synced library: for an album with several discs, ask Navidrome.
    // Without them (offline, older server) the discs are simply headed "Disc 1", "Disc 2"…
    val discTitles by loadState(d) { d?.let { discTitlesOf(graph, it) }.orEmpty() }
    // Library artists by name, so a name like "Chase & Status" isn't split into two artists.
    val knownArtists by loadState("artist-names") { graph.library.artists().map { normalizeKey(it.name) }.toSet() }
    CollectionPage(
        nav = nav,
        title = d?.album?.name ?: "Album",
        color = rememberCoverColor(d?.album?.coverArt),
        songs = d?.songs,
        error = (detail as? Load.Err)?.message,
        numbered = true,
        discTitles = discTitles.valueOrNull() ?: d?.discTitles.orEmpty(),
        showCovers = false,
        // Only artists beyond the album's own, as Navidrome's artist tags list them.
        subtitleOf = d?.album?.let { album ->
            val known = knownArtists.valueOrNull().orEmpty()
            val subtitle: (Song) -> String? = { song -> extraArtists(song, album.artistId, album.artist) { normalizeKey(it) in known } }
            subtitle
        },
        cover = { m ->
            BigCover(d?.album?.coverArt, d?.album?.coverArt ?: d?.album?.id, m, RoundedCornerShape(6.dp))
        },
        header = {
            if (d != null) {
                CollectionByline(
                    d.album.artist,
                    artistCover.valueOrNull(),
                    d.album.artistId?.let { artistId -> { nav.navigate("artist/$artistId") } },
                    listOfNotNull(
                        d.album.category.single,
                        d.album.year?.toString(),
                        d.album.genre,
                        "${d.songs.size} songs",
                        formatDuration(d.songs.sumOf { it.durationSec.toLong() }),
                    ).joinToString(" · "),
                )
            }
        },
    )
}

private suspend fun discTitlesOf(graph: AppGraph, detail: AlbumDetail): Map<Int, String> {
    if (detail.discTitles.isNotEmpty()) return detail.discTitles
    if (detail.songs.map { it.disc ?: 1 }.distinct().size < 2) return emptyMap()
    for (id in detail.album.allIds) {
        val titles = try {
            graph.subsonic.album(id).discTitles
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Album", "Disc titles not loaded: ${e.message}")
            return emptyMap()
        }
        if (titles.isNotEmpty()) {
            AppLog.d("Album", "${titles.size} disc titles")
            return titles
        }
    }
    return emptyMap()
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
    val m = mix.valueOrNull()
    CollectionPage(
        nav = nav,
        title = m?.title ?: "Mix",
        color = lerp(tileColors(m?.selectorId ?: "mix").first, Color.Black, 0.25f),
        songs = m?.songs,
        error = (mix as? Load.Err)?.message,
        cover = { mod -> if (m != null) CoverMosaic(m, mod) else Box(mod) },
        header = {
            if (m != null) {
                if (m.description.isNotBlank()) {
                    Text(m.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                }
                CollectionByline(null, null, null, "Made for you · ${m.songs.size} songs · sound search: ${m.query}")
            }
        },
    )
}
