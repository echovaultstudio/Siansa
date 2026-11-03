package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.AppGraph
import app.siansa.data.OutsideMedia
import app.siansa.data.PodcastFeed
import app.siansa.data.Station
import app.siansa.data.StationRepository
import app.siansa.data.bookLength
import app.siansa.log.AppLog
import app.siansa.net.StreamLinks
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/*
 * Your Stations (issue #353): the music server's radio stations, then the stations and podcasts you added. A station
 * plays live in the player; a podcast opens its episodes.
 */

/** Plays [station] live, a server playlist link first looked into for its stream. */
internal suspend fun playStation(graph: AppGraph, station: Station) {
    val ready = graph.stations.playable(station)
    AppLog.i("Stations", "Playing station \"${station.name}\"" + if (station.fromServer) " from the server" else "")
    graph.player.play(listOf(OutsideMedia.liveSong(ready)))
}

/** An episode's line under its title: its date and length, as far as the feed gives them. */
internal fun episodeLine(publishedAt: Long, durationSec: Int, zone: ZoneId = ZoneId.systemDefault()): String =
    listOfNotNull(
        publishedAt.takeIf { it > 0 }?.let { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.ofEpochMilli(it).atZone(zone)) },
        durationSec.takeIf { it >= 60 }?.let { bookLength(it.toDouble()) },
    ).joinToString(" · ")

/** The line under a station in lists: where it comes from. */
internal fun stationLine(station: Station): String = if (station.fromServer) "Radio · From your server" else "Radio · On this phone"

/** The line under a podcast in lists: its author and how many episodes. */
internal fun podcastLine(feed: PodcastFeed): String = listOfNotNull(
    "Podcast",
    feed.author.takeIf { it.isNotBlank() },
    "${feed.episodes.size} ${if (feed.episodes.size == 1) "episode" else "episodes"}",
).joinToString(" · ")

@Composable
fun StationsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val launcher = rememberPlayLauncher()
    val scope = rememberCoroutineScope()
    val mine by graph.stations.state.collectAsStateWithLifecycle()
    val playing by graph.player.state.collectAsStateWithLifecycle()
    var server by remember { mutableStateOf<List<Station>?>(null) }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        graph.stations.mine()
        // The last list straight away, then the server's own.
        server = graph.stations.knownServerStations()
        if (!graph.connection.current.offline) server = graph.stations.serverStations()
    }
    Screen(
        title = "Your Stations",
        nav = nav,
        actions = { IconButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, "Add a station or podcast") } },
    ) { padding ->
        val own = mine
        if (own == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Screen
        }
        val fromServer = server.orEmpty()
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("stationsList"), contentPadding = PaddingValues(bottom = Space.Section)) {
            if (fromServer.isNotEmpty()) {
                item { SectionTitle("From your server", top = Space.Small) }
                items(fromServer, key = { it.id }) { st ->
                    StationRow(
                        title = st.name, subtitle = "Radio", cover = OutsideMedia.cover(st.image), icon = Icons.Rounded.Radio, seed = st.id,
                        playing = playing.current?.id?.let { OutsideMedia.isLive(it) && it.endsWith(st.streamUrl) } == true,
                        onClick = { scope.launch { playStation(graph, st) } },
                    )
                }
            }
            item { SectionTitle("Radio", top = if (fromServer.isEmpty()) Space.Small else LocalSectionGap.current) }
            if (own.stations.isEmpty()) {
                item { EmptyLine("Add a radio stream with +. Links to .m3u and .pls playlists work too.") }
            }
            items(own.stations, key = { it.id }) { st ->
                StationRow(
                    title = st.name, subtitle = "Radio · ${hostOf(st.link)}", cover = OutsideMedia.cover(st.image), icon = Icons.Rounded.Radio, seed = st.id,
                    playing = playing.current?.id == OutsideMedia.LIVE + st.streamUrl,
                    onClick = { scope.launch { playStation(graph, st) } },
                    onRemove = {
                        scope.launch {
                            graph.stations.removeStation(st.id)
                            launcher.notify("Removed ${st.name}")
                        }
                    },
                )
            }
            item { SectionTitle("Podcasts") }
            if (own.podcasts.isEmpty()) {
                item { EmptyLine("Add a podcast with + and the link to its RSS feed.") }
            }
            items(own.podcasts, key = { it.id }) { feed ->
                StationRow(
                    title = feed.title, subtitle = podcastLine(feed), cover = OutsideMedia.cover(feed.image), icon = Icons.Rounded.Podcasts, seed = feed.id,
                    playing = false,
                    onClick = { nav.navigate("podcast/${feed.id}") },
                    onRemove = {
                        scope.launch {
                            graph.stations.removePodcast(feed.id)
                            launcher.notify("Removed ${feed.title}")
                        }
                    },
                )
            }
        }
    }
    if (adding) {
        AddStationDialog(
            onDismiss = { adding = false },
            onAdded = { added ->
                adding = false
                when (added) {
                    is StationRepository.Added.Radio -> launcher.notify("Added ${added.station.name}")
                    is StationRepository.Added.Podcast -> {
                        launcher.notify("Added ${added.feed.title}")
                        nav.navigate("podcast/${added.feed.id}")
                    }
                }
            },
        )
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Space.Page, vertical = Space.Small),
    )
}

private fun hostOf(url: String): String = StationRepository.nameFrom(url)

/**
 * A link to add: a radio stream, a playlist that points at one (.m3u, .m3u8, .pls), or a podcast's RSS feed. The
 * link is looked into before it's added, so a link that doesn't play says so here.
 */
@Composable
fun AddStationDialog(onDismiss: () -> Unit, onAdded: (StationRepository.Added) -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = StreamLinks.clean(link) != null
    fun add() {
        if (!valid || busy) return
        busy = true
        error = null
        scope.launch {
            graph.stations.add(link, name)
                .onSuccess(onAdded)
                .onFailure { error = it.message?.takeIf { m -> m.isNotBlank() } ?: "That link couldn't be added" }
            busy = false
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Add to Your Stations") },
        text = {
            AddStationFields(
                link = link, name = name, busy = busy, error = error,
                onLink = { link = it; error = null }, onName = { name = it }, onDone = ::add,
            )
        },
        confirmButton = { TextButton(onClick = ::add, enabled = valid && !busy) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

/** The add dialog's fields, apart from the dialog so they can be drawn in screenshots. */
@Composable
internal fun AddStationFields(
    link: String,
    name: String,
    busy: Boolean,
    error: String?,
    onLink: (String) -> Unit,
    onName: (String) -> Unit,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
        OutlinedTextField(
            value = link,
            onValueChange = onLink,
            label = { Text("Link") },
            placeholder = { Text("https://…") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text("Name (optional)") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(Space.Small))
                Hint("Checking the link…")
            }
            error != null -> Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            else -> Hint("A radio stream, a .m3u or .pls playlist, or a podcast's RSS feed. Kept on this phone.")
        }
    }
}

/**
 * A station or podcast in a list: its picture (or a coloured tile with [icon]), its name and a line under it. Tap
 * plays or opens it; [onRemove] adds a ⋮ menu with Remove.
 */
@Composable
internal fun StationRow(
    title: String,
    subtitle: String,
    cover: String?,
    icon: ImageVector,
    seed: String,
    playing: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = Space.Page, end = if (onRemove != null) Space.Tight else Space.Page, top = Space.Small, bottom = Space.Small)
            .testTag("stationRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationCover(cover, icon, seed, Modifier.size(56.dp).testTag("stationCover"))
        Spacer(Modifier.width(Space.Medium))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (playing) Accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onRemove != null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menu = false; onRemove() },
                    )
                }
            }
        }
    }
}

/** A station's or podcast's picture, or a coloured tile with [icon] when it has none. */
@Composable
internal fun StationCover(cover: String?, icon: ImageVector, seed: String, modifier: Modifier, big: Boolean = false) {
    val shape = RoundedCornerShape(if (big) 8.dp else 6.dp)
    if (cover != null) {
        Cover(cover, if (big) 300 else 150, modifier, shape)
    } else {
        val (a, b) = tileColors(seed)
        Box(modifier.clip(shape).background(Brush.linearGradient(listOf(a, b))), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(if (big) 48.dp else 26.dp))
        }
    }
}

/** A podcast's page: its picture and name, then its episodes, newest first. Tap one to play it. */
@Composable
fun PodcastScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val launcher = rememberPlayLauncher()
    val scope = rememberCoroutineScope()
    val mine by graph.stations.state.collectAsStateWithLifecycle()
    val playing by graph.player.state.collectAsStateWithLifecycle()
    var checking by remember { mutableStateOf(false) }
    var refreshes by remember { mutableIntStateOf(0) }
    LaunchedEffect(id, refreshes) {
        val feed = graph.stations.podcast(id) ?: return@LaunchedEffect
        // Read again for new episodes when asked, or when it was last read over an hour ago.
        if (graph.connection.current.offline) return@LaunchedEffect
        if (refreshes == 0 && System.currentTimeMillis() - feed.checkedAt < REFRESH_AFTER_MS) return@LaunchedEffect
        checking = true
        graph.stations.refresh(id)
        checking = false
    }
    val feed = mine?.podcasts?.firstOrNull { it.id == id }
    var menu by remember { mutableStateOf(false) }
    Screen(
        title = feed?.title ?: "Podcast",
        nav = nav,
        actions = {
            if (checking) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else {
                IconButton(onClick = { refreshes++ }, enabled = feed != null) { Icon(Icons.Rounded.Refresh, "Check for new episodes") }
            }
            Box {
                IconButton(onClick = { menu = true }, enabled = feed != null) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Remove podcast") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = {
                            menu = false
                            val f = feed ?: return@DropdownMenuItem
                            scope.launch {
                                graph.stations.removePodcast(f.id)
                                launcher.notify("Removed ${f.title}")
                                nav.popBackStack()
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        if (feed == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (mine == null) CircularProgressIndicator() else EmptyLine("This podcast isn't in Your Stations any more.")
            }
            return@Screen
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = Space.Section)) {
            item { PodcastHeader(feed.title, feed.author, OutsideMedia.cover(feed.image), feed.id, feed.episodes.size) }
            if (feed.episodes.isEmpty()) item { EmptyLine("No episodes in this feed yet.") }
            items(feed.episodes, key = { it.id }) { ep ->
                EpisodeRow(
                    ep.title,
                    episodeLine(ep.publishedAt, ep.durationSec),
                    playing = playing.current?.id == OutsideMedia.EPISODE + ep.url,
                ) {
                    AppLog.i("Stations", "Playing an episode of \"${feed.title}\"")
                    graph.player.play(listOf(OutsideMedia.episodeSong(feed, ep)))
                }
            }
        }
    }
}

/** A podcast's picture, name, author and how many episodes, at the top of its page. */
@Composable
internal fun PodcastHeader(title: String, author: String, cover: String?, seed: String, episodes: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = Space.Page, end = Space.Page, top = Space.Small, bottom = Space.Section).testTag("podcastHeader"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationCover(cover, Icons.Rounded.Podcasts, seed, Modifier.size(112.dp), big = true)
        Spacer(Modifier.width(Space.Page))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (author.isNotBlank()) {
                Text(author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("$episodes ${if (episodes == 1) "episode" else "episodes"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** An episode: its title (two lines at most) and its date and length. */
@Composable
internal fun EpisodeRow(title: String, line: String, playing: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.Page, vertical = Space.Small).testTag("episodeRow"),
        verticalArrangement = Arrangement.spacedBy(Space.Tight),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (playing) Accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (line.isNotEmpty()) {
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Now playing's line under the cover for a radio station: no seek bar, only that it's live (issue #353). */
@Composable
internal fun LiveBar() {
    val content = LocalContentColor.current
    Column(Modifier.padding(top = Space.Medium).testTag("liveBar")) {
        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(content.copy(alpha = 0.22f)))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Accent))
            Spacer(Modifier.width(Space.Small))
            Text("Live", style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.7f))
        }
    }
}

/** A podcast is read again on opening its page after this long. */
private const val REFRESH_AFTER_MS = 60 * 60 * 1000L
