package app.musix.ui

import app.musix.data.CombinedArtists

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
    val open = rememberArtistsOpener(beforeOpen)
    return { song: Song -> open { artistsOf(graph, song) } }
}

/**
 * Opens one of the artists [find] returns: straight to it when there's one, with the chooser when there are
 * several (a song's artists, or the artists a combined album artist names, issue #359).
 */
@Composable
fun rememberArtistsOpener(beforeOpen: () -> Unit = {}): (find: suspend () -> List<Artist>) -> Unit {
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
                Column(Modifier.padding(vertical = Space.Page)) {
                    Text(
                        "Choose artist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = Space.Page, vertical = Space.Small),
                    )
                    choices.forEach { artist ->
                        Row(
                            Modifier.fillMaxWidth().clickable { go(artist) }.padding(horizontal = Space.Page, vertical = Space.Small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtistCircle(artist, Modifier.size(48.dp))
                            Spacer(Modifier.width(Space.Medium))
                            Text(artist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    return { find: suspend () -> List<Artist> ->
        scope.launch {
            val artists = find()
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

/** A small round artist photo: Navidrome's artist image through its cover endpoint (issue #206). */
@Composable
fun ArtistCircle(artist: Artist, modifier: Modifier) {
    Cover(artist.coverArt ?: "ar-${artist.id}", 150, modifier, CircleShape)
}

/** The artist with [id], or the artists it names when it's several artists in one name (issue #359). */
internal suspend fun artistsNamedBy(graph: AppGraph, id: String, name: String): List<Artist> =
    CombinedArtists.expand(listOf(graph.library.artist(id) ?: Artist(id, name)), graph.library.combinedArtists())

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
    val combined = graph.library.combinedArtists()
    // A name that's several artists in one (tagged without the server's separator) offers each of them (issue #359).
    val found = CombinedArtists.expand(refs.map { ref -> graph.library.artist(ref.id) ?: Artist(ref.id, ref.name) }, combined)
    AppLog.d("Artist", "Song ${song.id}: ${found.size} artists from Navidrome's list")
    if (found.isNotEmpty()) return found

    // No list from Navidrome (older servers, offline): split the name, keeping library artists whose
    // name has a join in it ("Chase & Status") whole, and look each part up.
    val known = graph.library.artists().map { normalizeKey(it.name) }.toSet()
    val byName = splitArtists(song.artist) { normalizeKey(it) in known }
        .mapNotNull { graph.library.artistByName(it) }
        .distinctBy { it.id }
    if (byName.size > 1) return byName
    val single = song.artistId?.let { graph.library.artist(it) ?: Artist(it, song.artist) }
    return CombinedArtists.expand(listOfNotNull(single ?: byName.firstOrNull()), combined)
}
