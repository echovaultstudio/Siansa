package app.musix.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Refresh
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
    val libraryState by graph.library.index.collectAsStateWithLifecycle()
    val progress by graph.sync.progress.collectAsStateWithLifecycle()
    val library = libraryState
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
                        .format(Date(library.cache.syncedAt))
                    else -> "Not synced yet"
                },
            ) { nav.navigate("sync") }
            HubRow(
                Icons.Rounded.Equalizer,
                "Playback",
                "ReplayGain ${settings.replayGain.label.lowercase()}" + if (settings.preampDb != 0f) ", pre-amp ${"%+.0f".format(settings.preampDb)} dB" else "",
            ) { nav.navigate("settings/playback") }
            HubRow(Icons.Rounded.Storage, "Storage", "Image cache up to ${formatMb(settings.imageCacheMb)}") {
                nav.navigate("settings/storage")
            }
            HubRow(Icons.Rounded.BugReport, "Logs", if (settings.logging) "Logging on" else "Logging off") {
                nav.navigate("settings/logs")
            }
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
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().padding(16.dp),
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
        }
    }
}

private val cacheSizes = listOf(100, 250, 500, 1024, 2048, 5120)

/** Image cache size and usage, and what else the app keeps on the phone. */
@Composable
fun StorageScreen(nav: NavController) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val chosenAtStart = remember { settings.imageCacheMb }
    var refresh by remember { mutableIntStateOf(0) }
    val usage by loadState(refresh) {
        withContext(Dispatchers.IO) {
            val loader = SingletonImageLoader.get(context)
            Triple(
                loader.diskCache?.size ?: 0L,
                File(context.filesDir, "library.json").length(),
                AppLog.sizeBytes(),
            )
        }
    }

    Screen(title = "Storage", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Heading("Image cache")
            Hint("Covers and artist photos are saved as they load, so they show instantly next time and without a connection. The oldest are removed when the cache is full.")
            usage.valueOrNull()?.let { u ->
                Text("In use: ${formatBytes(u.first)} of ${formatMb(settings.imageCacheMb)}", style = MaterialTheme.typography.bodyLarge)
            }
            Text("Maximum size", style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cacheSizes.forEach { mb ->
                    FilterChip(
                        selected = settings.imageCacheMb == mb,
                        onClick = { graph.settings.setImageCacheMb(mb) },
                        label = { Text(formatMb(mb)) },
                    )
                }
            }
            if (settings.imageCacheMb != chosenAtStart) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("The new size applies after the app restarts.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { restartApp(context) }) { Text("Restart now") }
                    }
                }
            }
            OutlinedButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val loader = SingletonImageLoader.get(context)
                        loader.memoryCache?.clear()
                        loader.diskCache?.clear()
                    }
                    AppLog.i("Storage", "Image cache cleared")
                    refresh++
                }
            }) { Text("Clear image cache") }

            HorizontalDivider()
            Heading("Other data on this phone")
            usage.valueOrNull()?.let { u ->
                Text("Synced library: ${formatBytes(u.second)}", style = MaterialTheme.typography.bodyMedium)
                Text("Logs: ${formatBytes(u.third)}", style = MaterialTheme.typography.bodyMedium)
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

/** Optional logging for troubleshooting: on/off, a preview, share and clear. */
@Composable
fun LogsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    val lines by loadState(refresh to settings.logging) { withContext(Dispatchers.IO) { AppLog.tail(400).reversed() } }

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
                        "Records what the app does (server requests, sync, mixes, playback, errors and crashes) so problems can be traced. " +
                            "Passwords, tokens and API keys are removed. Nothing is recorded while this is off, and nothing leaves the phone unless you share it.",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { shareLogs(context) }) {
                            Icon(Icons.Rounded.Share, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Share logs")
                        }
                        OutlinedButton(onClick = {
                            AppLog.clear()
                            refresh++
                        }) { Text("Clear") }
                    }
                    Text("Newest first · ${formatBytes(AppLog.sizeBytes())}", style = MaterialTheme.typography.labelMedium)
                }
            }
            val list = lines.valueOrNull().orEmpty()
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
