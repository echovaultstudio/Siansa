package app.musix.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.musix.data.Song
import app.musix.data.cover
import app.musix.mix.songPathLengths
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun NavController.openSongPath(songId: String) = navigate("songpath/${Uri.encode(songId)}")

/**
 * Song path, step one: the chosen song is one end; search for the other end and pick a length.
 * Swap makes the chosen song the end instead of the start.
 */
@Composable
fun SongPathPicker(nav: NavController, songId: String) {
    val graph = LocalGraph.current
    val song by loadState(songId) {
        graph.library.songs(listOf(songId)).firstOrNull() ?: graph.subsonic.song(songId)
    }
    var toHere by rememberSaveable { mutableStateOf(false) }
    var length by rememberSaveable { mutableIntStateOf(20) }
    var query by rememberSaveable { mutableStateOf("") }
    val results by loadState(query) {
        val q = query.trim()
        if (q.length < 2) return@loadState emptyList<Song>()
        delay(250)
        val found = if (graph.library.isSynced) graph.library.search(q).songs else graph.subsonic.search(q).songs
        found.filter { it.id != songId }.take(40)
    }

    fun go(other: Song) {
        val (from, to) = if (toHere) other.id to songId else songId to other.id
        nav.navigate("songpath/${Uri.encode(from)}/${Uri.encode(to)}/$length")
    }

    Screen("Song path", nav) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    "Pick a second song. Musix builds a queue that moves smoothly from the sound of one to the other.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.Page, vertical = Space.Small),
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(start = Space.Page, end = Space.Tight, top = Space.Small), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (toHere) "Ends with" else "Starts with",
                            style = MaterialTheme.typography.labelLarge,
                            color = Accent,
                        )
                        val s = song.valueOrNull()
                        Row(Modifier.padding(top = Space.Small), verticalAlignment = Alignment.CenterVertically) {
                            Cover(s?.cover, 150, Modifier.size(48.dp), RoundedCornerShape(6.dp))
                            Spacer(Modifier.width(Space.Medium))
                            Column {
                                Text(s?.title ?: "…", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    s?.artist.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    IconButton(onClick = { toHere = !toHere }) {
                        Icon(Icons.Rounded.SwapVert, if (toHere) "Start with this song instead" else "End with this song instead")
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.Page, vertical = Space.Medium),
                    horizontalArrangement = Arrangement.spacedBy(Space.Small),
                ) {
                    songPathLengths.forEach { n ->
                        FilterChip(selected = length == n, onClick = { length = n }, label = { Text("$n songs") })
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(if (toHere) "Song to start from" else "Song to end with") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.Page),
                )
                Spacer(Modifier.height(Space.Small))
            }
            val found = results.valueOrNull().orEmpty()
            if (results is Load.Err) item { ErrorText((results as Load.Err).message) }
            if (query.trim().length >= 2 && results is Load.Ok && found.isEmpty()) {
                item {
                    Text(
                        "No songs found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Space.Page),
                    )
                }
            }
            items(found, key = { it.id }) { other ->
                Row(
                    Modifier.fillMaxWidth().clickable { go(other) }.padding(horizontal = Space.Page, vertical = Space.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(other.cover, 150, Modifier.size(48.dp), RoundedCornerShape(6.dp))
                    Spacer(Modifier.width(Space.Medium))
                    Column(Modifier.weight(1f)) {
                        Text(other.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            artistAndAlbum(other).orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Song path, step two: the path itself, ready to play or save as a playlist. */
@Composable
fun SongPathScreen(nav: NavController, fromId: String, toId: String, length: Int) {
    val graph = LocalGraph.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val result by loadState(Triple(fromId, toId, length)) { graph.songPath.build(fromId, toId, length) }
    val songs = result.valueOrNull()
    val from = songs?.firstOrNull()
    val to = songs?.lastOrNull()
    val title = if (from != null && to != null) graph.songPath.name(from, to) else "Finding a path…"
    val colors = tileColors("path-$fromId-$toId")
    var saved by rememberSaveable(fromId, toId, length) { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    CollectionPage(
        nav = nav,
        title = title,
        color = lerp(colors.first, Color.Black, 0.25f),
        songs = songs,
        error = (result as? Load.Err)?.message,
        cover = { m -> GeneratedCover(title, colors, m) },
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CollectionByline(null, null, null, "Song path" + (songs?.let { " · ${it.size} songs" } ?: ""))
                Spacer(Modifier.weight(1f))
                if (songs != null && from != null && to != null) {
                    TextButton(
                        enabled = !saved && !saving,
                        onClick = {
                            saving = true
                            scope.launch {
                                val name = runCatching { graph.songPath.save(from, to, songs) }
                                saving = false
                                name.onSuccess {
                                    saved = true
                                    snack.showSnackbar("Saved as playlist “$it”")
                                }.onFailure {
                                    snack.showSnackbar("Couldn't save the playlist: ${it.message}")
                                }
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null)
                        Spacer(Modifier.width(Space.Tight))
                        Text(if (saved) "Saved" else "Save as playlist", fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
    )
}
