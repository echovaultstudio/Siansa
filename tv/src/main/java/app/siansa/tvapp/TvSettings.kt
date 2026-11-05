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
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.SwapVert
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
import app.siansa.data.Users
import app.siansa.log.AppLog
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import app.siansa.playback.Passthrough
import app.siansa.playback.PassthroughMode
import app.siansa.playback.PassthroughRenderer
import app.siansa.ui.LocalGraph
import app.siansa.ui.LocalNav
import app.siansa.ui.Space
import app.siansa.ui.syncSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The TV app's settings (issue #860): the phone's settings that matter on a TV, each opening the phone's own page for it
 * (servers, sync, playback, streaming, mixes, scrobbling, colours, help), so the two never drift apart; and the TV's own
 * (audio passthrough). Downloads, mobile data, Android Auto, the mini player, arranging Home and backups are the
 * phone's and aren't here.
 */
@Composable
fun TvSettingsHub() {
    val graph = LocalGraph.current
    val nav = LocalNav.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val progress by graph.sync.progress.collectAsStateWithLifecycle()
    val library by graph.library.status.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val users = remember { Users.all(context) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = TV_EDGE).padding(top = Space.Small, bottom = Space.Large),
        verticalArrangement = Arrangement.spacedBy(Space.Section),
    ) {
        // Every server and user on this TV, as on the phone: OK switches (the app starts again as them). A new one signs
        // in with the code on the TV, like the first time, and keeps its own library and settings.
        Group("Servers and users") {
            for (u in users) {
                val active = u.id == Users.active
                TvSettingRow(
                    u.label,
                    when {
                        active -> "In use"
                        u.connected -> "Switch to this"
                        else -> "Not signed in yet: switch to sign in"
                    },
                    onClick = { if (!active) { AppLog.i("Tv", "Switching to user ${u.id + 1}"); Users.switchTo(context, u.id) } },
                    icon = if (active) Icons.Rounded.CheckCircle else Icons.Rounded.Person,
                    modifier = if (active) Modifier.initialFocus() else Modifier,
                )
            }
            TvSettingRow("Show music from", "Each user's music and libraries: show or hide them here, as in the phone's Sources",
                onClick = { nav.navigate(TvRoutes.SOURCES) }, icon = Icons.Rounded.LibraryMusic)
            TvSettingRow("Add a server or user", "Sign in with the code on the TV, as the first time. Each keeps its own library.", onClick = {
                val id = Users.add(context) { }
                AppLog.i("Tv", "Added user ${id + 1} on the TV")
                Users.switchTo(context, id)
            }, icon = Icons.Rounded.PersonAdd)
            TvSettingRow("Servers and services", "${AppLog.redact(settings.navidromeUrl)} · ${settings.navidromeUser.ifBlank { "API key" }} · AudioMuse, Last.fm and more",
                onClick = { nav.navigate("settings/connections") }, icon = Icons.Rounded.Dns)
        }
        Group("Music") {
            TvSettingRow("Library sync", syncSummary(progress, library), onClick = { nav.navigate("sync") }, icon = Icons.Rounded.Sync)
            TvSettingRow("Mixes and vibes", "Your daily mixes and when they're made", onClick = { nav.navigate("vibes") }, icon = Icons.Rounded.Schedule)
            TvSettingRow("Scrobbling and history", "Last.fm, ListenBrainz and what you've played", onClick = { nav.navigate("settings/scrobbling") }, icon = Icons.Rounded.GraphicEq)
        }
        Group("Sound") {
            TvSettingRow("Audio passthrough", "${settings.audioPassthrough.label} · Dolby and DTS to your receiver",
                onClick = { nav.navigate(TvRoutes.PASSTHROUGH) }, icon = Icons.Rounded.SpeakerGroup)
            TvSettingRow("Playback", "Crossfades, DJ blends, levelling, the equaliser, keep playing", onClick = { nav.navigate("settings/playback") }, icon = Icons.Rounded.Equalizer)
            TvSettingRow("Streaming quality", if (settings.transcodeWifiKbps > 0) "Opus ${settings.transcodeWifiKbps} kbit/s" else "Original files",
                onClick = { nav.navigate("settings/streaming") }, icon = Icons.Rounded.SwapVert)
        }
        Group("Look") {
            TvSettingRow("Colours", "Siansa's colours on the TV (always dark)", onClick = { nav.navigate("settings/themes") }, icon = Icons.Rounded.Palette)
        }
        Group("Help") {
            TvSwitchRow(
                "Debug logs",
                "Records what the TV app does, to trace problems. Nothing leaves the TV unless you send a report.",
                on = settings.logging,
                onChange = { on ->
                    graph.settings.setLogging(on)
                    AppLog.setEnabled(on)
                    AppLog.i("Help", "Debug logs ${if (on) "on" else "off"} (TV)")
                },
            )
            TvSettingRow("Report a problem", "Make it happen a few times with debug logs on, then send: the log goes with it.",
                onClick = { nav.navigate("settings/report") }, icon = Icons.Rounded.BugReport)
            TvSettingRow("Show the log", null, onClick = { nav.navigate("settings/logs") }, icon = Icons.Rounded.Description)
            TvSettingRow("What's new", null, onClick = { nav.navigate("settings/whatsnew") }, icon = Icons.Rounded.NewReleases)
            TvSettingRow("About", null, onClick = { nav.navigate("settings/about") }, icon = Icons.Rounded.Info)
        }
        Group("This TV") {
            if (!confirmSignOut) {
                TvSettingRow("Sign out", "Sign this TV out of your server. Your phone stays signed in.", onClick = { confirmSignOut = true },
                    icon = Icons.AutoMirrored.Rounded.Logout)
            } else {
                Text("Sign out of this server on the TV? Your phone stays signed in.", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/**
 * Whose music shows on the TV, and which of their libraries (music folders): the phone's Sources panel (Home's menu
 * there), the same switches and the same rules.
 */
@Composable
fun TvSources() {
    Column(Modifier.fillMaxSize().padding(horizontal = TV_EDGE), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
        Text("Show music from", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Note("Pick whose music shows here, and which of their libraries. The user in use keeps their own settings and history.")
        app.siansa.ui.SourcesPanel(Modifier.fillMaxSize())
    }
}

/** Audio passthrough (issue #860): Dolby and DTS music sent unchanged to the AV receiver. */
@Composable
fun TvPassthrough() {
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = TV_EDGE).padding(bottom = Space.Large),
        verticalArrangement = Arrangement.spacedBy(Space.Page),
    ) {
        Text("Audio passthrough", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
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
                list.isEmpty() -> "Your TV doesn't report a receiver that takes Dolby or DTS right now. Check it's switched on and connected over HDMI (eARC or ARC). " +
                    "Dolby and DTS songs are decoded on the TV meanwhile, in stereo."
                else -> "Your receiver takes: ${list.joinToString(", ")}."
            },
        )
        Passthrough.failed?.let { why ->
            Note("Passthrough stopped working earlier, so Dolby and DTS songs are decoded on the TV until Siansa is opened again ($why). Report a problem to send the log.")
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = TEXT_WIDTH), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = Space.Tight))
        content()
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = TEXT_WIDTH))
}

private val TEXT_WIDTH = 860.dp
