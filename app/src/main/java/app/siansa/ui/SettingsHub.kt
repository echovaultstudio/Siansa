package app.siansa.ui

import androidx.compose.material.icons.rounded.Dashboard
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.People
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
import androidx.compose.ui.platform.testTag
import app.siansa.mix.ContinuousMode
import app.siansa.mix.RadioDrift
import app.siansa.playback.Preload
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.covers.CoverStore
import app.siansa.data.AppSettings
import app.siansa.data.ReplayGainMode
import app.siansa.data.SyncPhase
import app.siansa.data.Users
import app.siansa.log.AppLog
import app.siansa.playback.CrossfadeMode
import app.siansa.playback.EqSettings
import app.siansa.playback.LoudnessSource
import app.siansa.log.logForClipboard
import app.siansa.log.problemsForClipboard
import app.siansa.log.logEntries
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Settings start page: one entry per area, each opening its own page. Just the names (issue #375). */
@Composable
fun SettingsHub(nav: NavController) {
    Screen(title = "Settings", nav = nav) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState())) {
            // Library sync lives under Users now (issue #273): each user's library is their own.
            HubRow(Icons.Rounded.People, "Users") { nav.navigate("settings/users") }
            HubRow(Icons.Rounded.Dns, "Connections") { nav.navigate("settings/connections") }
            HubRow(Icons.Rounded.Equalizer, "Playback") { nav.navigate("settings/playback") }
            // Themes moved into Style, with the now playing, mini player and nav bar looks (issue #346).
            HubRow(Icons.Rounded.Palette, "Style") { nav.navigate("settings/style") }
            HubRow(Icons.Rounded.GraphicEq, "Scrobbling") { nav.navigate("settings/scrobbling") }
            // The daily mixes' times of day; the tab's own vibes are on that tab (issue #339).
            HubRow(Icons.Rounded.Schedule, "Daily Vibe settings") { nav.navigate("vibes") }
            HubRow(Icons.Rounded.Dashboard, "Made for you cards") { nav.navigate("settings/cards") }
            HubRow(Icons.Rounded.DownloadForOffline, "Downloads") { nav.navigate("downloads") }
            HubRow(Icons.Rounded.Storage, "Storage") { nav.navigate("settings/storage") }
            HubRow(Icons.Rounded.Backup, "Backup") { nav.navigate("settings/backup") }
            HubRow(Icons.Rounded.BugReport, "Report a problem") { nav.navigate("settings/report") }
            HubRow(Icons.Rounded.BugReport, "Logs") { nav.navigate("settings/logs") }
            HubRow(Icons.Rounded.NewReleases, "What's new") { nav.navigate("settings/whatsnew") }
        }
    }
}

/** "Syncing… songs", "Last synced 20 Oct 2026, 07:26", "Not synced yet". */
internal fun syncSummary(progress: app.siansa.data.SyncProgress, library: app.siansa.data.LibraryStatus?): String = when {
    progress.running -> "Syncing… " + progress.phase.label
    progress.phase == SyncPhase.FAILED -> "Last sync failed"
    library != null -> "Last synced " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(library.syncedAt))
    else -> "Not synced yet"
}

/** A settings entry: its icon and name, the whole row opening its page (issue #375: no arrow, no summary). */
@Composable
internal fun HubRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.Page, vertical = Space.Page).testTag("hubRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = Space.Page))
    }
}

/** "Crossfade 6 s · levelling track by the Siansa plugin", "No crossfade · levelling album by ReplayGain tags", "… · no levelling". */
internal fun crossfadeSummary(s: AppSettings): String {
    val fade = when (s.crossfade) {
        CrossfadeMode.OFF -> "No crossfade"
        CrossfadeMode.FIXED -> "Crossfade ${s.crossfadeSeconds} s"
        CrossfadeMode.SMART -> "Smart crossfade up to ${s.crossfadeSeconds} s"
    }
    val levelling = if (s.replayGain == ReplayGainMode.OFF) "no levelling" else
        "levelling ${s.replayGain.label.lowercase()} by " + if (s.loudnessSource == LoudnessSource.PLUGIN) "the Siansa plugin" else "ReplayGain tags"
    return "$fade · $levelling"
}

private fun formatMb(mb: Int): String = if (mb >= 1024) "${"%.0f".format(mb / 1024.0)} GB" else "$mb MB"
internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    else -> "%.0f KB".format(bytes / 1024.0)
}

@Composable
fun PlaybackSettingsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val eq by graph.equaliser.state.collectAsStateWithLifecycle()
    Screen(title = "Playback", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { nav.navigate("settings/equaliser") }.padding(vertical = Space.Tight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Heading("Equaliser")
                    Hint(if (eq.enabled) "On · ${eq.preset.label}" else "Off")
                }
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().clickable { nav.navigate("settings/crossfade") }.padding(vertical = Space.Tight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Heading("Crossfade and levelling")
                    Hint(crossfadeSummary(settings))
                }
            }
            HorizontalDivider()
            Heading("Streaming quality")
            Hint(ServerWords.streamingHint(settings.sourceKind))
            QualityRow("On Wi-Fi", settings.transcodeWifiKbps) { graph.settings.save(graph.settings.current.copy(transcodeWifiKbps = it)) }
            QualityRow("On mobile data", settings.transcodeMobileKbps) { graph.settings.save(graph.settings.current.copy(transcodeMobileKbps = it)) }
            Hint("Siansa always plays the quality chosen here; it never switches on its own. On a slow connection, pick Opus for mobile data.")
            HorizontalDivider()
            Heading("Song radio")
            Hint("A song radio always starts with its song. How far may it wander from it?")
            RadioDriftChips(settings.radioDrift) { graph.settings.setRadioDrift(it) }
            Hint(settings.radioDrift.hint)
            HorizontalDivider()
            Heading("Keep playing")
            Hint("When the queue is about to end, should Siansa add more songs?")
            ContinuousChips(settings.continuousPlay) { graph.settings.setContinuousPlay(it) }
            Hint(continuousHint(settings.continuousPlay, settings.audioMuseReady))
            HorizontalDivider()
            Heading("Wind down")
            Hint("With the sleep timer's Wind down switched on and no time picked, how many calmer songs play before the music ends?")
            WindDownChips(settings.windDownSongs) { graph.settings.save(graph.settings.current.copy(windDownSongs = it)) }
            HorizontalDivider()
            Heading("Load songs ahead")
            Hint("While a song plays, Siansa loads the next ones, one at a time, so they start at once and keep playing if the connection drops. Fewer means less data. Only original files are loaded ahead.")
            PreloadRow("On Wi-Fi", settings.preloadWifi, "preload-wifi") { graph.settings.save(graph.settings.current.copy(preloadWifi = it)) }
            PreloadRow("On mobile data", settings.preloadMobile, "preload-mobile") { graph.settings.save(graph.settings.current.copy(preloadMobile = it)) }
            // Only with an Audiobookshelf server (issue #521): how far the book player's skip buttons jump.
            if (settings.booksReady) {
                HorizontalDivider()
                Heading("Books")
                Hint("How far the book player's skip buttons jump.")
                Text("Back", style = MaterialTheme.typography.bodyMedium)
                SkipChips(settings.bookSkipBackSec, "bookSkipBack") { graph.settings.save(graph.settings.current.copy(bookSkipBackSec = it)) }
                Text("Forward", style = MaterialTheme.typography.bodyMedium)
                SkipChips(settings.bookSkipForwardSec, "bookSkipForward") { graph.settings.save(graph.settings.current.copy(bookSkipForwardSec = it)) }
            }
        }
    }
}

/** 10 to 90 seconds for a book player's skip (issue #521). */
@Composable
internal fun SkipChips(selected: Int, tag: String, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).testTag(tag), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        AppSettings.BOOK_SKIP_CHOICES.forEach { n ->
            FilterChip(
                selected = selected == n,
                onClick = { onPick(n) },
                label = { Text("$n s") },
                modifier = Modifier.testTag("$tag-$n"),
            )
        }
    }
}

/** "Stay close", "A little", "Wander": how far a song radio may go from its song (issue #256). */
@Composable
internal fun RadioDriftChips(selected: RadioDrift, onPick: (RadioDrift) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).testTag("radioDrift"), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        RadioDrift.entries.forEach { drift ->
            FilterChip(
                selected = selected == drift,
                onClick = { onPick(drift) },
                label = { Text(drift.label) },
                modifier = Modifier.testTag("radioDrift-${drift.name}"),
            )
        }
    }
}

/** Off, Steady or DJ: what plays when the queue is about to end (issue #337). */
@Composable
internal fun ContinuousChips(selected: ContinuousMode, onPick: (ContinuousMode) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).testTag("continuousPlay"), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        ContinuousMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onPick(mode) },
                label = { Text(mode.label) },
                modifier = Modifier.testTag("continuousPlay-${mode.name}"),
            )
        }
    }
}

/** The line under the keep playing choices; DJ says when it can't order songs without AudioMuse. */
internal fun continuousHint(mode: ContinuousMode, audioMuseReady: Boolean): String =
    if (mode == ContinuousMode.DJ && !audioMuseReady) "${mode.hint} AudioMuse isn't set up, so songs are added as in Steady." else mode.hint

/** 10, 20 or 30 calm songs for a wind down without a timer (issue #330). */
@Composable
internal fun WindDownChips(selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).testTag("windDownSongs"), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        AppSettings.WIND_DOWN_CHOICES.forEach { n ->
            FilterChip(
                selected = selected == n,
                onClick = { onPick(n) },
                label = { Text("$n songs") },
                modifier = Modifier.testTag("windDownSongs-$n"),
            )
        }
    }
}

/** None to five songs loaded ahead (issue #254). */
@Composable
internal fun PreloadRow(label: String, value: Int, tag: String, onPick: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()).testTag(tag), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        Preload.CHOICES.forEach { n ->
            FilterChip(
                selected = value == n,
                onClick = { onPick(n) },
                label = { Text(if (n == 0) "None" else "$n") },
                // At least touch-size wide, so a one-digit chip gets no invisible touch margin and the gaps stay even.
                modifier = Modifier.widthIn(min = 48.dp).testTag("$tag-$n"),
            )
        }
    }
}

private val qualities = listOf(0, 96, 128, 160, 192, 256, 320)


@Composable
private fun QualityRow(label: String, value: Int, onPick: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
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

private data class StorageUsage(val images: Long, val songs: Long, val downloads: Long, val library: Long, val logs: Long)

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
                downloads = graph.downloads.usedBytes(),
                library = context.getDatabasePath(app.siansa.data.Users.scoped(app.siansa.data.UserFiles.DATABASE)).length(),
                logs = AppLog.sizeBytes(),
            )
        }
    }

    Screen(title = "Storage", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            Heading("Downloads")
            Hint("Music you chose to keep on the phone isn't part of the song cache and is never removed by itself.")
            usage.valueOrNull()?.let { u -> Text("In use: ${formatBytes(u.downloads)}", style = MaterialTheme.typography.bodyLarge) }
            OutlinedButton(onClick = { nav.navigate("downloads") }) { Text("Manage downloads") }

            HorizontalDivider()
            Heading("Song cache")
            Hint("Songs are kept on the phone as they play, and the next song in the queue is fetched ahead, so replays and skips start at once and survive a bad connection. Only original-quality streams are cached.")
            usage.valueOrNull()?.let { u ->
                Text("In use: ${formatBytes(u.songs)} of ${formatMb(settings.songCacheMb)}", style = MaterialTheme.typography.bodyLarge)
            }
            Text("Maximum size", style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
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
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
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
            Hint("When an album folder has an animated cover (cover.gif or cover.webp), it moves on the album page and in now playing; lists show its first frame. Your server may not shrink an animated WebP (Navidrome can't), so it's downloaded once in full and kept on the phone.")  // server-name: ok (an example of a server that can't)

            if (settings.imageCacheMb != imagesAtStart || settings.songCacheMb != songsAtStart) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Space.Medium), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
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

private fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    val component = launch.component ?: return
    context.startActivity(Intent.makeRestartActivityTask(component))
    Runtime.getRuntime().exit(0)
}

/** Optional logging for troubleshooting: on/off, a preview, save, share, clear and copy the errors. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
                    val file = AppLog.export(File(context.cacheDir, "logs/siansa-logs.txt"))
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
            contentPadding = PaddingValues(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Tight),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
                    SwitchRow("Keep a log", settings.logging) { on ->
                        graph.settings.setLogging(on)
                        AppLog.setEnabled(on)
                        refresh++
                    }
                    Hint(
                        "Save logs writes a text file (choose Downloads), which you can then upload anywhere. " +
                            "While on, it records everything that can help trace a problem: every request in full (server, IP address, parameters, headers, " +
                            "size and time, covers and song streams included), sync, mixes, playback and buffering, " +
                            "every download and cache read, cover downloads, slow screen frames and freezes (with what the app was doing), " +
                            "audio format, phone memory, temperature and battery saver, errors and crashes. " +
                            "Logs can grow large; it's meant for tracing problems. Passwords, tokens and API keys are removed. " +
                            "Nothing is recorded while this is off, and nothing leaves the phone unless you share it. " +
                            "If the app crashes, its last few hundred steps are kept on the phone so the crash can be looked into. " +
                            "Share sends a ZIP with the log, the same entries as data for tools, and what's in it.",
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.Small),
                        verticalArrangement = Arrangement.spacedBy(Space.Small),
                    ) {
                        Button(onClick = {
                            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.ROOT).format(Date())
                            saver.launch("siansa-logs-$stamp.txt")
                        }) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.width(Space.Small))
                            Text("Save logs")
                        }
                        Button(onClick = { nav.navigate("settings/report") }) { Text("Report a problem") }
                        OutlinedButton(onClick = { scope.launch { shareLogs(context) } }) {
                            Icon(Icons.Rounded.Share, null)
                            Spacer(Modifier.width(Space.Small))
                            Text("Share")
                        }
                        OutlinedButton(onClick = {
                            AppLog.clear()
                            refresh++
                        }) { Text("Clear") }
                        // Every error and warning with its details, ready to paste into a message or an issue.
                        OutlinedButton(onClick = {
                            scope.launch {
                                val text = withContext(Dispatchers.IO) {
                                    problemsForClipboard(logEntries(AppLog.allLines()), AppLog.header)
                                }
                                if (text == null) {
                                    snack.showSnackbar("No errors or warnings in the log")
                                    return@launch
                                }
                                val ok = runCatching {
                                    context.getSystemService(ClipboardManager::class.java)
                                        .setPrimaryClip(ClipData.newPlainText("Siansa problems", text))
                                }.isSuccess
                                AppLog.i("Logs", "Copied problems to the clipboard (${text.length} characters)")
                                snack.showSnackbar(if (ok) "Errors and warnings copied" else "Couldn't copy them")
                            }
                        }) {
                            Icon(Icons.Rounded.ContentCopy, null)
                            Spacer(Modifier.width(Space.Small))
                            Text("Copy problems")
                        }
                        // The whole log, ready to paste; the newest part when it's too big for the clipboard.
                        OutlinedButton(onClick = {
                            scope.launch {
                                val entries = withContext(Dispatchers.IO) { logEntries(AppLog.allLines()) }
                                val text = withContext(Dispatchers.Default) { logForClipboard(entries, AppLog.header) }
                                if (text == null) {
                                    snack.showSnackbar("The log is empty")
                                    return@launch
                                }
                                val ok = runCatching {
                                    context.getSystemService(ClipboardManager::class.java)
                                        .setPrimaryClip(ClipData.newPlainText("Siansa log", text))
                                }.isSuccess
                                val whole = text.lineSequence().first().endsWith(" entries")
                                AppLog.i("Logs", "Copied the log to the clipboard (${text.length} characters${if (whole) "" else ", newest part"})")
                                snack.showSnackbar(
                                    when {
                                        !ok -> "Couldn't copy the log"
                                        whole -> "Whole log copied"
                                        else -> "Log copied: the newest part, as the whole log is too big to copy. Save logs has it all."
                                    }
                                )
                            }
                        }) {
                            Icon(Icons.Rounded.ContentCopy, null)
                            Spacer(Modifier.width(Space.Small))
                            Text("Copy whole log")
                        }
                    }
                    val all = entries.valueOrNull().orEmpty()
                    val errors = all.count { it.level == "E" }
                    val warnings = all.count { it.level == "W" }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
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

/** Shares the diagnostics ZIP (issue #237): log, events, recent steps, last crash and a manifest. */
private suspend fun shareLogs(context: Context) {
    val file = withContext(Dispatchers.IO) { AppLog.exportArchive(File(context.cacheDir, "logs/siansa-diagnostics.zip")) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Siansa logs")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share logs").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
