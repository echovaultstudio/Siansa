package app.musix.ui

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.musix.log.AppLog
import app.musix.offline.SourceFilter

/*
 * Home's source filter (issue #56), under Sources in the panel behind Home's top-right button: play from
 * everything or only downloads, which Navidrome libraries to use, and whose playlists to show.
 */

/** The Sources tab of the Home settings panel. */
@Composable
internal fun SourcesPanel(modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val progress by graph.sync.progress.collectAsStateWithLifecycle()
    val folders by loadState("folders") { graph.subsonic.musicFolders() }
    val owners by loadState("playlist-owners") {
        val playlists = runCatching { graph.subsonic.playlists() }.getOrElse { graph.library.playlists() }
        SourceFilter.owners(playlists, settings.navidromeUser)
    }

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        PanelLabel("Play from")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Everything", selected = !settings.homeDownloadedOnly) { setDownloadedOnly(graph, false) }
            Choice("Downloaded", selected = settings.homeDownloadedOnly) { setDownloadedOnly(graph, true) }
        }
        Spacer(Modifier.size(6.dp))
        Hint("Downloaded shows only music on your phone. When you're offline, Home offers to switch to it.")

        val libraries = folders.valueOrNull().orEmpty()
        if (libraries.size > 1) {
            PanelLabel("Libraries")
            // No choice saved means every library.
            val chosen = settings.syncFolders.ifEmpty { libraries.map { it.id }.toSet() }
            libraries.forEach { folder ->
                val on = folder.id in chosen
                ToggleRow(folder.name, if (on) "Shown" else "Hidden", on, enabled = !progress.running) { show ->
                    val next = if (show) chosen + folder.id else chosen - folder.id
                    // At least one library stays.
                    if (next.isNotEmpty()) {
                        AppLog.i("Home", "Libraries: ${next.size} of ${libraries.size}")
                        graph.settings.setSyncFolders(next)
                        graph.sync.start(quiet = true)
                    }
                }
            }
            Hint("Hidden libraries are left out everywhere in the app.")
        }

        val people = owners.valueOrNull().orEmpty()
        if (people.isNotEmpty()) {
            PanelLabel("Playlists from")
            people.forEach { owner ->
                val me = owner.equals(settings.navidromeUser, ignoreCase = true)
                val shown = owner.lowercase() !in settings.homeHiddenOwners
                ToggleRow(if (me) "You ($owner)" else owner, if (me) "Your playlists" else "Their shared playlists", shown) { show ->
                    val key = owner.lowercase()
                    val next = if (show) settings.homeHiddenOwners - key else settings.homeHiddenOwners + key
                    AppLog.i("Home", "Hidden playlist owners: ${next.size}")
                    graph.settings.setHomeHiddenOwners(next)
                }
            }
        }
        Spacer(Modifier.size(16.dp))
    }
}

private fun setDownloadedOnly(graph: app.musix.AppGraph, on: Boolean) {
    AppLog.i("Home", "Play from ${if (on) "downloads" else "everything"}")
    graph.settings.setHomeDownloadedOnly(on)
}

@Composable
private fun PanelLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) Accent else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp))
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Under the greeting while Home is filtered: what's left out. Tapping opens the panel. */
@Composable
internal fun SourceChips(downloadedOnly: Boolean, hiddenOwners: Int, onClick: () -> Unit) {
    if (!downloadedOnly && hiddenOwners == 0) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (downloadedOnly) SourceChip("Downloaded", Icons.Rounded.DownloadForOffline, onClick)
        if (hiddenOwners > 0) SourceChip("$hiddenOwners ${if (hiddenOwners == 1) "user" else "users"} hidden", null, onClick)
    }
}

@Composable
private fun SourceChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Shown when the phone has no connection: one tap switches Home to the music that plays without one. */
@Composable
internal fun OfflineOffer(onShow: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("offline-offer"),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudOff, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("You're offline", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onShow) { Text("Show downloaded") }
        }
    }
}

/** Whether the phone has a connection, kept up to date as it comes and goes. */
@Composable
internal fun rememberOnline(): Boolean {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(ConnectivityManager::class.java) }
    var online by remember {
        mutableStateOf(cm?.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: true)
    }
    DisposableEffect(cm) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = true }
            override fun onLost(network: Network) { online = false }
        }
        // On the main thread, like every other change to the screen.
        runCatching { cm?.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    return online
}
