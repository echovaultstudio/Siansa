package app.siansa.tvapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.log.AppLog
import app.siansa.playback.Passthrough
import app.siansa.playback.PassthroughMode
import app.siansa.playback.PassthroughRenderer
import app.siansa.ui.LocalGraph
import app.siansa.ui.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The TV app's settings (issue #860): audio passthrough, the library, and the server it plays from. */
@Composable
fun TvSettings() {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    // What the receiver takes, asked when the page opens (switching the receiver on changes it).
    val takes by produceState<List<String>?>(null) {
        value = withContext(Dispatchers.Default) {
            Passthrough.bitstreamMimes.filter { runCatching { PassthroughRenderer.outputTakes(context, it) }.getOrDefault(false) }
                .map { Passthrough.name(it) }.distinct()
        }
    }
    var confirmSignOut by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = TV_EDGE).padding(bottom = Space.Large),
        verticalArrangement = Arrangement.spacedBy(Space.Large),
    ) {
        Section("Audio passthrough") {
            Note(
                "Dolby and DTS music goes unchanged to your AV receiver or soundbar, which plays it (Dolby Atmos included). " +
                    "These songs play without crossfades, DJ blends, levelling, the equaliser or Siansa's volume. Other music plays as usual.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                for (mode in PassthroughMode.entries) {
                    TvButton(mode.label, null, strong = settings.audioPassthrough == mode, onClick = {
                        if (settings.audioPassthrough != mode) {
                            graph.settings.setAudioPassthrough(mode)
                            AppLog.i("Settings", "Audio passthrough: ${mode.label}")
                        }
                    })
                }
            }
            val list = takes
            Note(
                when {
                    list == null -> "Asking the TV what your receiver takes…"
                    list.isEmpty() -> "Your TV doesn't report a receiver that takes Dolby or DTS right now. Check it's switched on and connected over HDMI (eARC or ARC)."
                    else -> "Your receiver takes: ${list.joinToString(", ")}."
                },
            )
        }
        Section("Library") {
            Note("The library on this TV is synced from your server, as on your phone.")
            TvButton("Sync now", Icons.Rounded.Sync, onClick = { graph.sync.start() })
        }
        Section("Server") {
            Note("Playing from ${AppLog.redact(settings.navidromeUrl)} as ${settings.navidromeUser.ifBlank { "an API key" }}.")
            if (!confirmSignOut) {
                TvButton("Sign out", Icons.AutoMirrored.Rounded.Logout, onClick = { confirmSignOut = true })
            } else {
                Note("Sign out of this server on the TV? Your phone stays signed in.")
                Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                    TvButton("Sign out", Icons.AutoMirrored.Rounded.Logout, strong = true, onClick = {
                        graph.player.stop()
                        graph.settings.save(graph.settings.current.copy(navidromePassword = "", navidromeApiKey = "", navidromeUser = ""))
                        AppLog.i("Tv", "Signed out on the TV")
                        confirmSignOut = false
                    })
                    TvButton("Cancel", null, onClick = { confirmSignOut = false })
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = TEXT_WIDTH), verticalArrangement = Arrangement.spacedBy(Space.Page)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val TEXT_WIDTH = 760.dp
