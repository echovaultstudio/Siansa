package app.siansa.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.siansa.AppGraph
import app.siansa.data.Song
import app.siansa.data.cover

/** "Similar songs" for a song (issue #223). */
fun NavController.openSimilarSongs(songId: String) = navigate("similar/songs/${Uri.encode(songId)}")

/** "Similar albums" for the album of a song (any of its songs will do, issue #223). */
fun NavController.openSimilarAlbums(songId: String) = navigate("similar/albums/${Uri.encode(songId)}")

/** The song for an id: from the synced library, otherwise from Navidrome. */
private suspend fun songFor(graph: AppGraph, id: String): Song =
    graph.library.song(id) ?: graph.source.song(id)

/** Songs that sound like one song, ready to play like an album. */
@Composable
fun SimilarSongsScreen(nav: NavController, songId: String) {
    val graph = LocalGraph.current
    val seed by loadState("similar-seed-$songId") { songFor(graph, songId) }
    val songs by loadState("similar-songs-$songId") { graph.similar.songs(songFor(graph, songId)) }
    val song = seed.valueOrNull()
    CollectionPage(
        nav = nav,
        title = song?.let { "Like ${it.title}" } ?: "Similar songs",
        color = rememberCoverColor(song?.cover),
        songs = songs.valueOrNull(),
        error = (songs as? Load.Err)?.message ?: (seed as? Load.Err)?.message,
        cover = { m -> BigCover(song?.cover, song?.albumId, m, RoundedCornerShape(6.dp)) },
        header = {
            CollectionByline(
                null,
                null,
                null,
                listOfNotNull(
                    "Similar songs",
                    song?.artist?.ifBlank { null }?.let { "sounds like $it" },
                    songs.valueOrNull()?.let { "${it.size} songs" },
                ).joinToString(" · "),
            )
        },
    )
}

/** Albums that sound like the album of a song, from the Siansa plugin. */
@Composable
fun SimilarAlbumsScreen(nav: NavController, songId: String) {
    val graph = LocalGraph.current
    val seed by loadState("similar-seed-$songId") { songFor(graph, songId) }
    val albums by loadState("similar-albums-$songId") { graph.similar.albums(songId) }
    val album = seed.valueOrNull()?.album?.ifBlank { null }
    Screen(title = album?.let { "Albums like $it" } ?: "Similar albums", nav = nav) { padding ->
        LoadBox(albums, Modifier.padding(padding)) { found ->
            Box(Modifier.padding(padding)) {
                if (found.isEmpty()) {
                    Text(
                        "No similar albums yet. The Siansa plugin on your AudioMuse server finds them once it has " +
                            "analysed your library; try again later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Space.Page),
                    )
                } else {
                    // A grid of three or a list (issue #289).
                    AlbumsGridOrList(found, page = "similar-albums", onOpen = { nav.navigate("album/${it.id}") }, modifier = Modifier.fillMaxSize().testTag("similar-albums"))
                }
            }
        }
    }
}
