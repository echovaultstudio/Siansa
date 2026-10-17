package app.musix.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.musix.covers.CoverDownload
import app.musix.covers.CoverStore
import app.musix.data.ReplayGainMode
import app.musix.data.SyncPhase
import app.musix.log.AppLog
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Settings start page: one entry per area, each opening its own page. */
@Composable
fun SettingsHub(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val libraryState by graph.library.status.collectAsStateWithLifecycle()
    val progress by graph.sync.progress.collectAsStateWithLifecycle()
    val library = libraryState
    val vibes by graph.selectors.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.selectors.all() }
    Screen(title = "Settings", nav = nav) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState())) {
            HubRow(
                Icons.Rounded.Dns,
                "Connections",
                listOfNotNull(
                    "Navidrome".takeIf { settings.navidromeReady },
                    "AudioMuse".takeIf { settings.audioMuseReady },
                    "OpenRouter".takeIf { settings.openRouterKey.isNotBlank() },
                ).joinToString(" · ").ifEmpty { "Not set up" },
            ) { nav.navigate("settings/connections") }
            HubRow(
                Icons.Rounded.Sync,
                "Library sync",
                when {
                    progress.running -> "Syncing… " + progress.phase.label
                    progress.phase == SyncPhase.FAILED -> "Last sync failed"
                    library != null -> "Last synced " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(library.syncedAt))
                    else -> "Not synced yet"
                },
            ) { nav.navigate("sync") }
            HubRow(
                Icons.Rounded.Equalizer,
                "Playback",
                "ReplayGain ${settings.replayGain.label.lowercase()} · Wi-Fi ${qualityLabel(settings.transcodeWifiKbps)} · mobile ${qualityLabel(settings.transcodeMobileKbps)}",
            ) { nav.navigate("settings/playback") }
            HubRow(
                Icons.Rounded.GraphicEq,
                "Scrobbling",
                listOfNotNull(
                    "Navidrome".takeIf { settings.scrobbleNavidrome },
                    "Last.fm".takeIf { settings.lastFmReady },
                    "ListenBrainz/Koito".takeIf { settings.listenBrainzReady },
                ).joinToString(" · ").ifEmpty { "Off" },
            ) { nav.navigate("settings/scrobbling") }
            HubRow(
                Icons.Rounded.Schedule,
                "Vibes",
                vibes?.let { "${it.size} ${if (it.size == 1) "vibe" else "vibes"} for your daily mixes" } ?: "Times of day for your daily mixes",
            ) { nav.navigate("vibes") }
            HubRow(Icons.Rounded.Storage, "Storage", "Songs up to ${formatMb(settings.songCacheMb)} · images ${imageLimit(settings.imageCacheMb)}" +
                    if (settings.coverDownload) " · all covers downloaded" else "") {
                nav.navigate("settings/storage")
            }
            HubRow(Icons.Rounded.BugReport, "Logs", if (settings.logging) "Logging on" else "Logging off") {
                nav.navigate("settings/logs")
            }
            val context = LocalContext.current
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
            }
            Text(
                "Musix $version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun HubRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
    }
}

private fun imageLimit(mb: Int): String = if (mb <= 0) "without a limit" else "up to ${formatMb(mb)}"

private fun formatMb(mb: Int): String = if (mb >= 1024) "${"%.0f".format(mb / 1024.0)} GB" else "$mb MB"
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    else -> "%.0f KB".format(bytes / 1024.0)
}

@Composable
fun PlaybackSettingsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    Screen(title = "Playback", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Heading("ReplayGain")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReplayGainMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.replayGain == mode,
                        onClick = { graph.settings.save(settings.copy(replayGain = mode)) },
                        label = { Text(mode.label) },
                    )
                }
            }
            Hint("Track evens out every song; Album keeps the loudness differences within an album.")
            var preamp by remember { mutableStateOf(settings.preampDb) }
            Text("Pre-amp: ${"%+.0f".format(preamp)} dB", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = preamp,
                onValueChange = { preamp = it.roundToInt().toFloat() },
                onValueChangeFinished = { graph.settings.save(graph.settings.current.copy(preampDb = preamp)) },
                valueRange = -12f..6f,
                steps = 17,
            )
            Hint("Tracks can only be turned down, so raise the pre-amp only if quiet tracks still sound too quiet. Applies from the next song.")

            HorizontalDivider()
            Heading("Streaming quality")
            Hint("Opus is converted by Navidrome with its own \"opus\" transcoding profile. It saves data on mobile; the original file is best on Wi-Fi. Applies from the next song.")
            QualityRow("On Wi-Fi", settings.transcodeWifiKbps) { graph.settings.save(graph.settings.current.copy(transcodeWifiKbps = it)) }
            QualityRow("On mobile data", settings.transcodeMobileKbps) { graph.settings.save(graph.settings.current.copy(transcodeMobileKbps = it)) }
            Hint("Seeking inside an Opus song can take a moment, since Navidrome converts it as it streams.")
            SwitchRow("Switch to Opus when the connection is too slow", settings.autoQuality) { graph.settings.setAutoQuality(it) }
            Hint("If the original keeps stopping to wait for data (three times in two minutes), Musix carries on in Opus at a bitrate the connection can manage, picking up where you were, and tries the original again after 15 minutes.")
        }
    }
}

private val qualities = listOf(0, 96, 128, 160, 192, 256, 320)

private fun qualityLabel(kbps: Int) = if (kbps <= 0) "original" else "Opus $kbps"

@Composable
private fun QualityRow(label: String, value: Int, onPick: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        qualities.forEach { kbps ->
            FilterChip(
                selected = value == kbps,
                onClick = { onPick(kbps) },
                label = { Text(if (kbps == 0) "Original" else "Opus $kbps") },
            )
        }
    }
}

private val cacheSizes = listOf(100, 250, 500, 1024, 2048, 5120, 0)
private val songCacheSizes = listOf(512, 1024, 2048, 5120, 10240, 20480)

private data class StorageUsage(val images: Long, val songs: Long, val library: Long, val logs: Long)

/** Image and song cache sizes and usage, and what else the app keeps on the phone. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun StorageScreen(nav: NavController) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val imagesAtStart = remember { settings.imageCacheMb }
    val songsAtStart = remember { settings.songCacheMb }
    var refresh by remember { mutableIntStateOf(0) }
    val usage by loadState(refresh) {
        withContext(Dispatchers.IO) {
            StorageUsage(
                images = (SingletonImageLoader.get(context).diskCache?.size ?: 0L) + CoverStore.sizeBytes(),
                songs = runCatching { graph.songCache.cacheSpace }.getOrDefault(0L),
                library = context.getDatabasePath("library.db").length(),
                logs = AppLog.sizeBytes(),
            )
        }
    }

    Screen(title = "Storage", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Heading("Song cache")
            Hint("Songs are kept on the phone as they play, and the next song in the queue is fetched ahead, so replays and skips start at once and survive a bad connection. Only original-quality streams are cached.")
            usage.valueOrNull()?.let { u ->
                Text("In use: ${formatBytes(u.songs)} of ${formatMb(settings.songCacheMb)}", style = MaterialTheme.typography.bodyLarge)
            }
            Text("Maximum size", style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                songCacheSizes.forEach { mb ->
                    FilterChip(
                        selected = settings.songCacheMb == mb,
                        onClick = { graph.settings.save(graph.settings.current.copy(songCacheMb = mb)) },
                        label = { Text(formatMb(mb)) },
                    )
                }
            }
            OutlinedButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val cache = graph.songCache
                        cache.keys.toList().forEach { key -> runCatching { cache.removeResource(key) } }
                    }
                    AppLog.i("Storage", "Song cache cleared")
                    refresh++
                }
            }) { Text("Clear song cache") }

            HorizontalDivider()
            Heading("Image cache")
            Hint("Covers and artist photos are saved as they load, so they show instantly next time and without a connection. The oldest are removed when the cache is full. With no limit nothing is removed, and Android won't clear them when the phone runs low on space.")
            usage.valueOrNull()?.let { u ->
                Text(
                    if (settings.imageCacheMb <= 0) "In use: ${formatBytes(u.images)}"
                    else "In use: ${formatBytes(u.images)} of ${formatMb(settings.imageCacheMb)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text("Maximum size", style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cacheSizes.forEach { mb ->
                    FilterChip(
                        selected = settings.imageCacheMb == mb,
                        onClick = { graph.settings.setImageCacheMb(mb) },
                        label = { Text(if (mb <= 0) "No limit" else formatMb(mb)) },
                    )
                }
            }
            OutlinedButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val loader = SingletonImageLoader.get(context)
                        loader.memoryCache?.clear()
                        loader.diskCache?.clear()
                        CoverStore.clear()
                        AnimatedCovers.forget(graph)
                    }
                    AppLog.i("Storage", "Image cache cleared")
                    refresh++
                }
            }) { Text("Clear image cache") }

            SwitchRow("Animated covers", settings.animatedCovers) { on -> graph.settings.setAnimatedCovers(on) }
            Hint("When an album folder has an animated cover (cover.gif or cover.webp), it moves on the album page and in now playing; lists show its first frame. Navidrome can't shrink an animated WebP, so it's downloaded once in full and kept on the phone.")

            HorizontalDivider()
            Heading("Download all covers")
            Hint("Fetches every album, artist and playlist cover in the background, so browsing never waits for one. Runs after each library sync and once a day, only on Wi-Fi, and only fetches covers that aren't on the phone yet. Choose No limit above so none are removed again.")
            val download by CoverDownload.progress.collectAsStateWithLifecycle()
            // Show the new space used once a download finishes.
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
                if (on) CoverDownload.start(context) else CoverDownload.stop(context)
            }
            SwitchRow("On any Wi-Fi, not just at home", settings.coverDownloadAnyWifi) { on ->
                graph.settings.setCoverDownload(settings.coverDownload, anyWifi = on)
                refresh++
            }
            Hint(
                if (settings.coverDownloadAnyWifi) "Downloads on any Wi-Fi network."
                else "Home means the phone is on the same local network as Navidrome's address. If Navidrome is reached by a domain name, this can't be told apart; use any Wi-Fi instead.",
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
                    CoverDownload.start(context)
                    refresh++
                }) { Text("Download covers now") }
            }

            if (settings.imageCacheMb != imagesAtStart || settings.songCacheMb != songsAtStart) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("New cache sizes apply after the app restarts.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { restartApp(context) }) { Text("Restart now") }
                    }
                }
            }

            HorizontalDivider()
            Heading("Other data on this phone")
            usage.valueOrNull()?.let { u ->
                Text("Library database: ${formatBytes(u.library)}", style = MaterialTheme.typography.bodyMedium)
                Text("Logs: ${formatBytes(u.logs)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private class LogEntry(val level: String, val lines: List<String>) {
    val problem get() = level == "E" || level == "W"
}

private val entryStart = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} ([A-Z])/""")

/** Groups log lines into entries: a timestamped line plus the lines that continue it (stack traces). */
private fun logEntries(lines: List<String>): List<LogEntry> {
    val out = ArrayList<LogEntry>()
    var level = "I"
    var current = ArrayList<String>()
    for (line in lines) {
        val m = entryStart.find(line)
        if (m != null) {
            if (current.isNotEmpty()) out += LogEntry(level, current)
            current = arrayListOf(line)
            level = m.groupValues[1]
        } else {
            current += line
        }
    }
    if (current.isNotEmpty()) out += LogEntry(level, current)
    return out
}

private fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    val component = launch.component ?: return
    context.startActivity(Intent.makeRestartActivityTask(component))
    Runtime.getRuntime().exit(0)
}

/** Optional logging for troubleshooting: on/off, a preview, share and clear. */
@Composable
fun LogsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    var problemsOnly by remember { mutableStateOf(false) }
    // Whole entries (an error and its stack trace stay together), newest first.
    val entries by loadState(refresh to settings.logging) { withContext(Dispatchers.IO) { logEntries(AppLog.tail(3000)) } }
    val lines = entries.valueOrNull()?.let { all ->
        (if (problemsOnly) all.filter { it.problem } else all.takeLast(400)).reversed().flatMap { it.lines.reversed() }
    }
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    // Lets you pick where to save (e.g. Downloads), then writes the log there.
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val file = AppLog.export(File(context.cacheDir, "logs/musix-logs.txt"))
                    context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                }.isSuccess
            }
            snack.showSnackbar(if (ok) "Logs saved" else "Couldn't save the logs")
        }
    }

    Screen(
        title = "Logs",
        nav = nav,
        actions = { IconButton(onClick = { refresh++ }) { Icon(Icons.Rounded.Refresh, "Refresh") } },
    ) { padding ->
        LazyColumn(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SwitchRow("Keep a log", settings.logging) { on ->
                        graph.settings.setLogging(on)
                        AppLog.setEnabled(on)
                        refresh++
                    }
                    Hint(
                        "Save logs writes a text file (choose Downloads), which you can then upload anywhere. " +
                            "While on, it records everything that can help trace a problem: server requests, sync, mixes, playback and buffering, " +
                            "every download and cache read, cover downloads, slow screen frames and freezes (with what the app was doing), " +
                            "audio format, phone memory, temperature and battery saver, errors and crashes. " +
                            "Passwords, tokens and API keys are removed. Nothing is recorded while this is off, and nothing leaves the phone unless you share it.",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.ROOT).format(Date())
                            saver.launch("musix-logs-$stamp.txt")
                        }) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save logs")
                        }
                        OutlinedButton(onClick = { shareLogs(context) }) {
                            Icon(Icons.Rounded.Share, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                        OutlinedButton(onClick = {
                            AppLog.clear()
                            refresh++
                        }) { Text("Clear") }
                    }
                    val all = entries.valueOrNull().orEmpty()
                    val errors = all.count { it.level == "E" }
                    val warnings = all.count { it.level == "W" }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !problemsOnly, onClick = { problemsOnly = false }, label = { Text("Everything") })
                        FilterChip(
                            selected = problemsOnly,
                            onClick = { problemsOnly = true },
                            label = { Text("Problems ($errors errors, $warnings warnings)") },
                        )
                    }
                    Text("Newest first · ${formatBytes(AppLog.sizeBytes())}", style = MaterialTheme.typography.labelMedium)
                }
            }
            val list = lines.orEmpty()
            if (list.isEmpty()) {
                item { Hint(if (settings.logging) "Nothing logged yet." else "Logging is off.") }
            }
            items(list) { line ->
                Text(
                    line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = when {
                        " E/" in line -> MaterialTheme.colorScheme.error
                        " W/" in line -> MaterialTheme.colorScheme.tertiary
                        " D/" in line -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private fun shareLogs(context: Context) {
    val file = AppLog.export(File(context.cacheDir, "logs/musix-logs.txt"))
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Musix logs")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share logs").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
