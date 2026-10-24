package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.covers.CoverDownload
import app.siansa.data.LocalPlaylists
import app.siansa.data.Song
import app.siansa.data.cover
import app.siansa.offline.OfflineItem
import app.siansa.offline.OfflineKind
import app.siansa.offline.OfflinePlan
import app.siansa.offline.OfflineState
import app.siansa.offline.likedOfflineItem
import app.siansa.offline.offlineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The download button on an album or playlist page: an outlined arrow to keep it on the phone, a ring
 * while its songs download, and a filled arrow once they're all here. Tapping a chosen one asks to remove it.
 */
@Composable
fun DownloadButton(item: OfflineItem, songs: List<Song>) {
    val graph = LocalGraph.current
    val ids by graph.downloads.ids.collectAsStateWithLifecycle()
    val chosen = item.key in ids.items
    val songIds = remember(songs) { songs.map { it.id } }
    val state = OfflinePlan.state(songIds, ids.done, ids.waiting)
    var confirm by remember { mutableStateOf(false) }
    IconButton(
        onClick = { if (chosen) confirm = true else graph.downloads.choose(item, songs) },
        enabled = songs.isNotEmpty() || chosen,
    ) {
        when {
            !chosen -> Icon(Icons.Outlined.ArrowCircleDown, "Download", Modifier.size(28.dp))
            state == OfflineState.ALL -> Icon(Icons.Rounded.DownloadForOffline, "Downloaded", tint = Accent, modifier = Modifier.size(28.dp))
            else -> Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { if (songIds.isEmpty()) 0f else songIds.count { it in ids.done }.toFloat() / songIds.size },
                    modifier = Modifier.size(26.dp),
                    color = Accent,
                    strokeWidth = 2.5.dp,
                )
                Icon(Icons.Rounded.DownloadForOffline, "Downloading", tint = Accent.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }
        }
    }
    if (confirm) RemoveDialog(item.name, onDismiss = { confirm = false }) { graph.downloads.drop(item.key) }
}

/** A small filled arrow next to a song that's on the phone. */
@Composable
fun DownloadedMark(songId: String) {
    val graph = LocalGraph.current
    val ids by graph.downloads.ids.collectAsStateWithLifecycle()
    if (songId in ids.done) {
        Icon(Icons.Rounded.DownloadForOffline, "Downloaded", tint = Accent, modifier = Modifier.padding(end = Space.Tight).size(16.dp))
    }
}

/** "Download" in a song's menu, or "Remove download" when the song itself was chosen. */
@Composable
fun SongDownloadMenuItem(song: Song, close: () -> Unit) {
    val graph = LocalGraph.current
    val ids by graph.downloads.ids.collectAsStateWithLifecycle()
    val item = remember(song.id) { song.offlineItem() }
    when {
        item.key in ids.items -> DropdownMenuItem(text = { Text("Remove download") }, onClick = {
            close()
            graph.downloads.drop(item.key)
        })
        song.id !in ids.done && song.id !in ids.waiting -> DropdownMenuItem(text = { Text("Download") }, onClick = {
            close()
            graph.downloads.choose(item, listOf(song))
        })
    }
}

@Composable
private fun RemoveDialog(name: String, onDismiss: () -> Unit, onRemove: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove download?") },
        text = { Text("\"$name\" will need a connection to play again.") },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onRemove()
            }) { Text("Remove") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } },
    )
}

private val downloadQualities = listOf(0, 128, 160, 192, 256, 320)

private val downloadLimits = listOf(0, 1024, 2048, 5120, 10240, 20480, 51200)

/**
 * The Downloads menu: everything kept on the phone, each with a remove button, the way to choose more,
 * and the download settings.
 */
@Composable
fun DownloadsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val list by graph.downloads.list.collectAsStateWithLifecycle()
    val progress by graph.downloads.progress.collectAsStateWithLifecycle()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<OfflineItem?>(null) }
    var removeAll by remember { mutableStateOf(false) }
    var stopAll by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { graph.downloads.refresh() }

    val used = list.done.sumOf { it.bytes }
    val bytesOf = remember(list.done) { list.done.associate { it.song.id to it.bytes } }

    Screen(title = "Downloads", nav = nav) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = bottomBarPadding(Space.Section)),
        ) {
            item {
                Column(Modifier.padding(horizontal = Space.Page, vertical = Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
                    Hint("Music you download stays on the phone and plays without a connection, for example on a plane. It's kept until you remove it here.")
                    // Pause or resume sits next to the totals, right above the progress line it controls.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${list.done.size} songs · ${formatSize(used)}" + if (settings.downloadLimitMb > 0) " of ${formatSize(settings.downloadLimitMb * 1024L * 1024L)}" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (progress.running) {
                            DownloadControl(Icons.Rounded.Pause, "Pause") { graph.downloads.pause() }
                        } else if (list.waiting.isNotEmpty()) {
                            DownloadControl(Icons.Rounded.PlayArrow, "Resume") { graph.downloads.retry() }
                        }
                    }
                    if (progress.running) {
                        val song = progress.current
                        // ✕ stops every download still waiting, after asking (issue #235).
                        ProgressWithStop(if (progress.total > 0) progress.bytes.toFloat() / progress.total else 0f) { stopAll = true }
                        Text(
                            "Downloading${song?.let { " ${it.title}" } ?: ""} · ${list.waiting.size} to go",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else if (list.waiting.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${list.waiting.size} songs to go" + (progress.note?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            StopAllButton { stopAll = true }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                        FilledTonalButton(onClick = { nav.navigate("downloads/add") }) { Text("Add music") }
                        if (list.done.isNotEmpty()) {
                            OutlinedButton(onClick = { graph.player.play(list.done.map { it.song }, shuffle = true) }) {
                                Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(Space.Small))
                                Text("Shuffle all")
                            }
                        }
                    }
                }
            }
            if (list.done.isNotEmpty()) {
                item(key = "all-songs") {
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.navigate("downloads/songs") }.padding(horizontal = Space.Page, vertical = Space.Medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.LibraryMusic, null, tint = Accent) }
                        Spacer(Modifier.width(Space.Medium))
                        Column(Modifier.weight(1f)) {
                            Text("Downloaded songs", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                "Every song on the phone · remove several at once",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (list.items.isEmpty()) {
                item {
                    Text(
                        "Nothing downloaded yet. Tap Add music, or the download button on an album or playlist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Space.Page),
                    )
                }
            }
            items(list.items.sortedByDescending { it.addedAt }, key = { it.key }) { item ->
                val have = item.songIds.count { it in bytesOf }
                val size = item.songIds.sumOf { bytesOf[it] ?: 0L }
                OfflineRow(
                    item = item,
                    detail = listOf(
                        kindLabel(item.kind),
                        if (have == item.songIds.size) "${item.songIds.size} songs" else "$have of ${item.songIds.size} songs",
                        formatSize(size),
                    ).joinToString(" · "),
                    onClick = { open(nav, item) },
                ) {
                    // Phone-only playlists are always kept; deleting the playlist removes its downloads.
                    if (!(item.kind == OfflineKind.PLAYLIST && LocalPlaylists.isLocal(item.id))) {
                        IconButton(onClick = { removing = item }) { Icon(Icons.Rounded.Close, "Remove") }
                    }
                }
            }
            item {
                Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
                    HorizontalDivider()
                    Heading("Settings")
                    SwitchRow("Download on Wi-Fi only", settings.downloadWifiOnly) { on ->
                        graph.settings.setDownloads(wifiOnly = on)
                        graph.downloads.retry()
                    }
                    Hint(
                        if (settings.downloadWifiOnly) "A VPN counts as mobile data, so nothing downloads while one is on."
                        else "Downloads on any connection, including mobile data and a VPN.",
                    )
                    Text("Download quality", style = MaterialTheme.typography.bodyLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                        downloadQualities.forEach { kbps ->
                            FilterChip(
                                selected = settings.downloadKbps == kbps,
                                onClick = { graph.settings.setDownloads(kbps = kbps) },
                                label = { Text(if (kbps == 0) "Original" else "Opus $kbps") },
                            )
                        }
                    }
                    Hint("Opus is converted by Navidrome and takes far less space than the original file. Applies to songs downloaded from now on.")
                    Text("Storage limit", style = MaterialTheme.typography.bodyLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                        downloadLimits.forEach { mb ->
                            FilterChip(
                                selected = settings.downloadLimitMb == mb,
                                onClick = {
                                    graph.settings.setDownloads(limitMb = mb)
                                    graph.downloads.retry()
                                },
                                label = { Text(if (mb == 0) "No limit" else formatSize(mb * 1024L * 1024L)) },
                            )
                        }
                    }
                    Hint("Downloads stop when the limit is reached. Remove something to make room.")
                    if (list.items.isNotEmpty()) {
                        OutlinedButton(onClick = { removeAll = true }) { Text("Remove all downloads") }
                    }
                    CoverDownloadSection()
                }
            }
        }
    }
    removing?.let { item -> RemoveDialog(item.name, onDismiss = { removing = null }) { graph.downloads.drop(item.key) } }
    if (stopAll) {
        AlertDialog(
            onDismissRequest = { stopAll = false },
            title = { Text("Stop all downloads?") },
            text = { Text("The ${list.waiting.size} songs still waiting won't download. Songs already downloaded stay. You can download anything again later.") },
            confirmButton = { TextButton(onClick = { stopAll = false; graph.downloads.stopAll() }) { Text("Stop all") } },
            dismissButton = { TextButton(onClick = { stopAll = false }) { Text("Keep going") } },
        )
    }
    if (removeAll) {
        AlertDialog(
            onDismissRequest = { removeAll = false },
            title = { Text("Remove all downloads?") },
            text = { Text("Everything will need a connection to play again. Playlists kept on this phone download again.") },
            confirmButton = {
                TextButton(onClick = {
                    removeAll = false
                    graph.downloads.removeAll()
                }) { Text("Remove all") }
            },
            dismissButton = { TextButton(onClick = { removeAll = false }) { Text("Keep") } },
        )
    }
}

private enum class PickTab(val label: String) { ALBUMS("Albums"), PLAYLISTS("Playlists"), ARTISTS("Artists") }

/** Tick albums, playlists or artists to keep them on the phone; untick to remove them again. */
@Composable
fun DownloadPickerScreen(nav: NavController) {
    val graph = LocalGraph.current
    val ids by graph.downloads.ids.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(PickTab.ALBUMS) }
    var query by rememberSaveable { mutableStateOf("") }
    val options by loadState(tab) {
        when (tab) {
            PickTab.ALBUMS -> graph.library.albums("name").map { it.offlineItem() }
            PickTab.PLAYLISTS -> graph.library.playlists().ifEmpty { graph.source.playlists() }.map { it.offlineItem() }
            PickTab.ARTISTS -> graph.library.artists().map { it.offlineItem() }
        }
    }
    val shown = remember(options, query) {
        val all = options.valueOrNull().orEmpty()
        val terms = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) all else all.filter { item -> terms.all { "${item.name} ${item.subtitle}".lowercase().contains(it) } }
    }
    val toggle: (OfflineItem) -> Unit = { item -> if (item.key in ids.items) graph.downloads.drop(item.key) else graph.downloads.choose(item) }

    Screen(title = "Add music", nav = nav) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = bottomBarPadding(Space.Section)),
        ) {
            item {
                Column(Modifier.padding(horizontal = Space.Page, vertical = Space.Tight), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                        PickTab.entries.forEach { t ->
                            FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) })
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        placeholder = { Text("Find ${tab.label.lowercase()}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            val visible = shown
            if (visible.size > 1) {
                item(key = "all") {
                    val allChosen = visible.all { it.key in ids.items }
                    Row(Modifier.fillMaxWidth().padding(horizontal = Space.Small), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            if (allChosen) graph.downloads.dropAll(visible.map { it.key })
                            else graph.downloads.chooseAll(visible.filterNot { it.key in ids.items })
                        }) {
                            Text(
                                if (allChosen) "Remove all ${visible.size} ${tab.label.lowercase()}"
                                else "Download all ${visible.size} ${tab.label.lowercase()}",
                            )
                        }
                    }
                }
            }
            if (query.isBlank()) {
                item(key = "liked") {
                    val liked = remember { likedOfflineItem() }
                    OfflineRow(liked, "All your liked songs, kept up to date", onClick = { toggle(liked) }) {
                        Checkbox(checked = liked.key in ids.items, onCheckedChange = { toggle(liked) })
                    }
                }
            }
            when (val o = options) {
                is Load.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(Space.Large), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
                }
                is Load.Err -> item { ErrorText(o.message) }
                is Load.Ok -> {
                    if (o.value.isEmpty()) item {
                        Text(
                            "Nothing here yet. Sync your library first (Settings → Library sync).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Space.Page),
                        )
                    }
                    items(shown, key = { it.key }) { item ->
                        OfflineRow(item, item.subtitle, onClick = { toggle(item) }) {
                            Checkbox(checked = item.key in ids.items, onCheckedChange = { toggle(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineRow(item: OfflineItem, detail: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = Space.Page, end = Space.Tight, top = Space.Small, bottom = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = if (item.kind == OfflineKind.ARTIST) CircleShape else RoundedCornerShape(6.dp)
        if (item.kind == OfflineKind.LIKED) {
            Box(
                Modifier.size(52.dp).clip(shape).background(Brush.linearGradient(listOf(Color(0xFF4B2BD6), Color(0xFF8FD3C8)))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
        } else {
            Cover(item.cover, 150, Modifier.size(52.dp), shape)
        }
        Spacer(Modifier.width(Space.Medium))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

private fun kindLabel(kind: OfflineKind) = when (kind) {
    OfflineKind.ALBUM -> "Album"
    OfflineKind.PLAYLIST -> "Playlist"
    OfflineKind.ARTIST -> "Artist"
    OfflineKind.LIKED -> "Liked songs"
    OfflineKind.SONG -> "Song"
}

private fun open(nav: NavController, item: OfflineItem) = when (item.kind) {
    OfflineKind.ALBUM -> nav.navigate("album/${item.id}")
    OfflineKind.PLAYLIST -> nav.navigate("playlist/${item.id}")
    OfflineKind.ARTIST -> nav.navigate("artist/${item.id}")
    OfflineKind.LIKED -> nav.navigate("liked")
    OfflineKind.SONG -> nav.openSongDetails(item.id)
}

internal fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    else -> "%.0f KB".format(bytes / 1024.0)
}

/** How a downloaded song was saved: "Original" or "Opus 160". */
fun downloadQualityLabel(kbps: Int): String = if (kbps > 0) "Opus $kbps" else "Original"

/**
 * Every downloaded song, wherever it came from (album, playlist, artist, Liked songs or one song), with
 * how much room it takes. Tick songs, or Select all, and remove them together. Removed songs aren't
 * downloaded again when a playlist is checked for new songs.
 */
@Composable
fun DownloadedSongsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val list by graph.downloads.list.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirm by remember { mutableStateOf(false) }
    val songs = remember(list.done, query) {
        val terms = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        list.done
            .filter { d -> terms.all { "${d.song.title} ${d.song.artist} ${d.song.album}".lowercase().contains(it) } }
            .sortedWith(compareBy({ it.song.artist.lowercase() }, { it.song.album.lowercase() }, { it.song.disc ?: 1 }, { it.song.track ?: 0 }))
    }
    // Songs that are gone (removed elsewhere) can't stay ticked.
    LaunchedEffect(list.done) { selected = selected intersect list.done.mapTo(HashSet()) { it.song.id } }
    val selectedBytes = list.done.filter { it.song.id in selected }.sumOf { it.bytes }

    Screen(title = "Downloaded songs", nav = nav) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Column(Modifier.padding(horizontal = Space.Page, vertical = Space.Tight), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                Text(
                    "${list.done.size} songs · ${formatSize(list.done.sumOf { it.bytes })}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("Find a song, artist or album") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val allShown = songs.isNotEmpty() && songs.all { it.song.id in selected }
                    TextButton(onClick = {
                        selected = if (allShown) selected - songs.mapTo(HashSet()) { it.song.id } else selected + songs.map { it.song.id }
                    }, enabled = songs.isNotEmpty()) { Text(if (allShown) "Clear selection" else "Select all") }
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = { confirm = true }, enabled = selected.isNotEmpty()) {
                        Text(if (selected.isEmpty()) "Remove" else "Remove ${selected.size} · ${formatSize(selectedBytes)}")
                    }
                }
            }
            if (list.done.isEmpty()) {
                Text(
                    "No songs on the phone yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Space.Page),
                )
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomBarPadding(Space.Section))) {
                items(songs, key = { it.song.id }) { d ->
                    val ticked = d.song.id in selected
                    val toggle = { selected = if (ticked) selected - d.song.id else selected + d.song.id }
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = toggle).padding(start = Space.Page, end = Space.Tight, top = Space.Small, bottom = Space.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(d.song.cover, 150, Modifier.size(48.dp), RoundedCornerShape(6.dp))
                        Spacer(Modifier.width(Space.Medium))
                        Column(Modifier.weight(1f)) {
                            Text(d.song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(d.song.artist, formatSize(d.bytes), downloadQualityLabel(d.kbps)).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Checkbox(checked = ticked, onCheckedChange = { toggle() })
                    }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(if (selected.size == 1) "Remove 1 song?" else "Remove ${selected.size} songs?") },
            text = { Text("They'll need a connection to play again, and playlists won't download them back. Download the album or playlist again to get them back.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    graph.downloads.removeSongs(selected)
                    selected = emptySet()
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep") } },
        )
    }
}

/** Pause or Resume next to the download totals: a small pill with an icon, the height of the text beside it. */
@Composable
private fun DownloadControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.Medium, vertical = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = Accent)
        Spacer(Modifier.width(Space.Small))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** The progress line with the ✕ that stops every download still waiting (issue #235). */
@Composable
internal fun ProgressWithStop(progress: Float, onStop: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).testTag("downloadProgress"))
        StopAllButton(onStop)
    }
}

@Composable
private fun StopAllButton(onStop: () -> Unit) {
    IconButton(onClick = onStop, modifier = Modifier.testTag("stopAllDownloads")) {
        Icon(Icons.Rounded.Close, "Stop all downloads")
    }
}

/** The Pause control as it shows while downloading, for the spacing test. */
@Composable
internal fun DownloadControlForTest() = DownloadControl(Icons.Rounded.Pause, "Pause") { }

/**
 * Download all covers (issue #302, moved here from Storage), placed in the Settings column under the song
 * download settings: the switch, when it may run, a network check, and its progress with Stop or Download covers now.
 */
@Composable
private fun CoverDownloadSection() {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    HorizontalDivider()
    Heading("Download all covers")
    Hint("Fetches every album, artist and playlist cover in the background, so browsing never waits for one. Runs after each library sync and once a day, and only fetches covers that aren't on the phone yet. Choose No limit in Storage › Image cache so none are removed again.")
    val download by CoverDownload.progress.collectAsStateWithLifecycle()
    // Check the network again once a download finishes.
    var wasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(download.running) {
        if (wasRunning && !download.running) refresh++
        wasRunning = download.running
    }
    val network by loadState(refresh to settings.coverDownloadAnyWifi) {
        withContext(Dispatchers.IO) { CoverDownload.whyNot(context, graph.settings.current) }
    }
    SwitchRow("Download all covers", settings.coverDownload) { on ->
        graph.settings.setCoverDownload(on)
        CoverDownload.schedule(context, graph.settings.current)
        if (on) CoverDownload.startNow(context, graph) else CoverDownload.stop(context)
    }
    SwitchRow("Download on Wi-Fi only", settings.coverDownloadWifiOnly) { on ->
        graph.settings.setCoverDownload(settings.coverDownload, wifiOnly = on)
        refresh++
    }
    if (settings.coverDownloadWifiOnly) {
        SwitchRow("On any Wi-Fi, not just at home", settings.coverDownloadAnyWifi) { on ->
            graph.settings.setCoverDownload(settings.coverDownload, anyWifi = on)
            refresh++
        }
    }
    Hint(
        when {
            !settings.coverDownloadWifiOnly -> "Downloads on any connection, including mobile data and a VPN."
            settings.coverDownloadAnyWifi -> "Downloads on any Wi-Fi network. A VPN counts as mobile data, so nothing downloads while one is on."
            else -> "Home means the phone is on Wi-Fi and reaches Navidrome's address on that network. A VPN counts as mobile data, so nothing downloads while one is on. If Navidrome is reached by a domain name, this can't be told apart; use any Wi-Fi instead."
        },
    )
    Text(
        when (val n = network) {
            is Load.Loading -> "Checking the network…"
            is Load.Ok -> n.value?.let { "Not now: $it." } ?: "Covers can be downloaded on this network now."
            is Load.Err -> "Couldn't check the network: ${n.message}"
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    if (download.running) {
        LinearProgressIndicator(
            progress = { if (download.total > 0) download.done.toFloat() / download.total else 0f },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Checked ${download.done} of ${download.total} · ${download.downloaded} downloaded" +
                if (download.failed > 0) " · ${download.failed} failed" else "",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = { CoverDownload.stop(context) }) { Text("Stop") }
    } else {
        download.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        OutlinedButton(onClick = {
            CoverDownload.startNow(context, graph)
            refresh++
        }) { Text("Download covers now") }
    }
}
