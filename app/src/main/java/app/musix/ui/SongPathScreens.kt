package app.musix.ui

import app.musix.net.PathMood
import app.musix.mix.MOOD_END
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Switch
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import app.musix.mix.songPathUpNext
import app.musix.log.AppLog
import androidx.compose.runtime.LaunchedEffect
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

/** [fromNowPlaying]: the song is playing, so the path is queued after it once ready instead of waiting for Play (#212). */
fun NavController.openSongPath(songId: String, fromNowPlaying: Boolean = false) =
    navigate("songpath/${Uri.encode(songId)}?live=$fromNowPlaying")

/**
 * Song path, step one: the chosen song is one end; search for the other end and pick a length.
 * Swap makes the chosen song the end instead of the start.
 */
@Composable
fun SongPathPicker(nav: NavController, songId: String, fromNowPlaying: Boolean = false) {
    val graph = LocalGraph.current
    val song by loadState(songId) {
        graph.library.songs(listOf(songId)).firstOrNull() ?: graph.source.song(songId)
    }
    var toHere by rememberSaveable { mutableStateOf(false) }
    var length by rememberSaveable { mutableIntStateOf(20) }
    var query by rememberSaveable { mutableStateOf("") }
    val results by loadState(query) {
        val q = query.trim()
        if (q.length < 2) return@loadState emptyList<Song>()
        delay(250)
        val found = if (graph.library.isSynced) graph.library.search(q).songs else graph.source.search(q).songs
        found.filter { it.id != songId }.take(40)
    }

    var lyrics by rememberSaveable { mutableStateOf(false) }
    val audioMuse = graph.settings.current.audioMuseReady

    /** The other end: a song id, or MOOD_END + a mood (issue #208). */
    fun go(otherId: String) {
        val (from, to) = if (toHere) otherId to songId else songId to otherId
        nav.navigate("songpath/${Uri.encode(from)}/${Uri.encode(to)}/$length?lyrics=$lyrics&live=$fromNowPlaying")
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
            // AudioMuse can end (or start) a path on a mood, and follow the lyrics' meaning (issue #208).
            if (audioMuse) {
                item {
                    PathMoodPicker(toHere) { mood -> go(MOOD_END + mood.id) }
                    LyricsSwitchRow(lyrics) { lyrics = it }
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
                    Modifier.fillMaxWidth().clickable { go(other.id) }.padding(horizontal = Space.Page, vertical = Space.Small),
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
fun SongPathScreen(nav: NavController, fromId: String, toId: String, length: Int, lyrics: Boolean = false, fromNowPlaying: Boolean = false) {
    val graph = LocalGraph.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val result by loadState(listOf(fromId, toId, length, lyrics)) { graph.songPath.build(fromId, toId, length, lyrics) }
    val songs = result.valueOrNull()
    // Started from now playing (#212): once the path is ready, the song keeps playing and the rest of the path
    // becomes the queue after it, instead of waiting for Play (which started the same song again).
    var queued by rememberSaveable(fromId, toId, length, lyrics) { mutableStateOf(false) }
    LaunchedEffect(songs) {
        if (!fromNowPlaying || queued || songs == null) return@LaunchedEffect
        val upcoming = songPathUpNext(songs.map { it.id }, graph.player.state.value.current?.id) ?: return@LaunchedEffect
        graph.player.replaceUpcoming(songs.subList(songs.size - upcoming, songs.size))
        queued = true
        AppLog.i("SongPath", "Queued $upcoming path songs after the song playing now")
        snack.showSnackbar("Song path queued after this song")
    }
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
                CollectionByline(null, null, null, "Song path" + (songs?.let { " · ${it.size} songs" } ?: "") + if (queued) " · queued" else "")
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

/** "Or end on a mood": AudioMuse's moods as chips, the same gap above and below them (issue #208). */
@Composable
internal fun PathMoodPicker(toHere: Boolean, onMood: (PathMood) -> Unit) {
    Text(
        if (toHere) "Or start from a mood" else "Or end on a mood",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Space.Page).testTag("pathMoodLabel"),
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(start = Space.Page, end = Space.Page, top = Space.Small).testTag("pathMoods"),
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        PathMood.entries.forEach { mood ->
            AssistChip(onClick = { onMood(mood) }, label = { Text(mood.label) })
        }
    }
}

/** Build the path by the lyrics' meaning (AudioMuse's SemGrove) instead of the sound alone. */
@Composable
internal fun LyricsSwitchRow(lyrics: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!lyrics) }.padding(horizontal = Space.Page, vertical = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Follow the lyrics", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("lyricsTitle"))
            Text(
                "Moves by what the songs are about, not only how they sound. Needs lyrics analysed in AudioMuse.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Space.Medium))
        Switch(checked = lyrics, onCheckedChange = onChange)
    }
}
