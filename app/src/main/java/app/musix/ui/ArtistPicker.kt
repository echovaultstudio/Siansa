package app.musix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.musix.AppGraph
import app.musix.data.Artist
import app.musix.data.Song
import app.musix.data.normalizeKey
import app.musix.data.splitArtists
import app.musix.log.AppLog
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch


/**
 * Opening "the artist" of a song. A song with several artists shows a small chooser with each artist's
 * photo in a circle; a song with one artist goes straight to that artist.
 *
 * Returns a function to call with the song; [beforeOpen] runs just before navigating (e.g. to close the player).
 */
@Composable
fun rememberArtistOpener(beforeOpen: () -> Unit = {}): (Song) -> Unit {
    val graph = LocalGraph.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var choices by remember { mutableStateOf<List<Artist>>(emptyList()) }

    fun go(artist: Artist) {
        choices = emptyList()
        beforeOpen()
        nav.navigate("artist/${artist.id}")
    }

    if (choices.size > 1) {
        Dialog(onDismissRequest = { choices = emptyList() }) {
            Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
                Column(Modifier.padding(vertical = 16.dp)) {
                    Text(
                        "Choose artist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    choices.forEach { artist ->
                        Row(
                            Modifier.fillMaxWidth().clickable { go(artist) }.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtistCircle(artist, Modifier.size(48.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(artist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    return { song: Song ->
        scope.launch {
            val artists = artistsOf(graph, song)
            // Navigation and screen state only on the main thread, whatever thread the lookup ended on.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                when {
                    artists.size > 1 -> choices = artists
                    artists.size == 1 -> go(artists.first())
                }
            }
        }
    }
}

/** A small round artist photo: Navidrome's artist image, or the artist's cover art. */
@Composable
fun ArtistCircle(artist: Artist, modifier: Modifier) {
    val photo = artist.imageUrl
    if (photo != null) {
        AsyncImage(
            model = photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        Cover(artist.coverArt ?: "ar-${artist.id}", 150, modifier, CircleShape)
    }
}

/** Every artist on the song, as artists in the library where possible. */
private suspend fun artistsOf(graph: AppGraph, song: Song): List<Artist> {
    var refs = song.artists
    // Songs from the phone's library don't carry the list, so Navidrome is asked: its list has every
    // artist tag, also when the name shown is only the main artist ("Fred again.." on a song with Skrillex).
    if (refs.isEmpty()) {
        refs = runCatching { graph.subsonic.song(song.id).artists }
            .onFailure { AppLog.w("Artist", "Couldn't load the artists of ${song.id}: ${it.message}") }
            .getOrDefault(emptyList())
    }
    val found = refs.map { ref -> graph.library.artist(ref.id) ?: Artist(ref.id, ref.name) }
    AppLog.d("Artist", "Song ${song.id}: ${found.size} artists from Navidrome's list")
    if (found.isNotEmpty()) return found.distinctBy { it.id }

    // No list from Navidrome (older servers, offline): split the name, keeping library artists whose
    // name has a join in it ("Chase & Status") whole, and look each part up.
    val known = graph.library.artists().map { normalizeKey(it.name) }.toSet()
    val byName = splitArtists(song.artist) { normalizeKey(it) in known }
        .mapNotNull { graph.library.artistByName(it) }
        .distinctBy { it.id }
    if (byName.size > 1) return byName
    val single = song.artistId?.let { graph.library.artist(it) ?: Artist(it, song.artist) }
    return listOfNotNull(single ?: byName.firstOrNull())
}
