package app.musix.ui

import androidx.compose.ui.platform.testTag

import kotlinx.coroutines.launch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.musix.data.SyncPhase
import app.musix.data.SyncProgress
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

/** Choose which Navidrome libraries to keep on the phone, sync them and watch the progress. */
@Composable
fun SyncScreen(nav: NavController) {
    val graph = LocalGraph.current
    val progress by graph.sync.progress.collectAsStateWithLifecycle()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val library by graph.library.status.collectAsStateWithLifecycle()
    val folders by loadState("folders") { graph.source.musicFolders() }

    Screen(title = "Library sync", nav = nav) { padding ->
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            Text(
                "Musix keeps a copy of your library on the phone, so browsing and search are instant and mixes build quickly. " +
                    "It checks for changes each time the app starts. Sync now fetches everything again, so new music and changed tags show.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel("Libraries")
            LoadBox(folders) { list ->
                Column {
                    if (list.size <= 1) {
                        Text(
                            list.firstOrNull()?.let { "${it.name} (your only library)" } ?: "No libraries found",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        val chosen = settings.syncFolders.ifEmpty { list.map { it.id }.toSet() }
                        list.forEach { folder ->
                            val checked = folder.id in chosen
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !progress.running) {
                                        val next = if (checked) chosen - folder.id else chosen + folder.id
                                        if (next.isNotEmpty()) chooseLibraries(graph, next)
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    enabled = !progress.running,
                                    onCheckedChange = { on ->
                                        val next = if (on) chosen + folder.id else chosen - folder.id
                                        if (next.isNotEmpty()) chooseLibraries(graph, next)
                                    },
                                )
                                Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        Hint("Every library is kept on the phone, so switching one on or off is instant. An album in more than one library shows once for each, with the library's name.")
                    }
                }
            }

            if (progress.phase != SyncPhase.IDLE) {
                ProgressCard(progress)
            }

            if (progress.running) {
                OutlinedButton(onClick = { graph.sync.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
            } else {
                // Always a full sync (issue #376): new music and changed tags are picked up, whatever Navidrome
                // says about its last scan. The check at app start stays a quick one.
                Button(onClick = { graph.sync.start(full = true) }, modifier = Modifier.fillMaxWidth().testTag("syncNow")) {
                    Icon(Icons.Rounded.Sync, null)
                    Spacer(Modifier.width(Space.Small))
                    Text("Sync now")
                }
            }

            library?.let { index ->
                SectionLabel("On this phone")
                val n = NumberFormat.getIntegerInstance()
                Text(
                    "${n.format(index.artists)} artists · ${n.format(index.albums)} albums · ${n.format(index.songs)} songs",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Last synced " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(index.syncedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

/** The sync as it runs and how it ended: artists, albums and songs (also on Home → Source filter, issue #220). */
@Composable
internal fun ProgressCard(p: SyncProgress) {
    val n = NumberFormat.getIntegerInstance()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (p.phase) {
                    SyncPhase.DONE -> Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    SyncPhase.FAILED -> Unit
                    else -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                Spacer(Modifier.width(Space.Medium))
                Text(
                    when (p.phase) {
                        SyncPhase.DONE -> when {
                            p.upToDate -> "Up to date: Navidrome hasn't scanned since the last sync"
                            p.changedAlbums in 1 until p.albums -> "Updated ${p.changedAlbums} new or changed albums"
                            p.changedAlbums == 0 -> "Up to date: no albums changed"
                            else -> "Sync finished"
                        }
                        SyncPhase.FAILED -> "Sync failed"
                        SyncPhase.CONNECTING -> "Connecting to Navidrome…"
                        SyncPhase.SAVING -> "Saving to the phone…"
                        else -> "Syncing " + (p.libraryName ?: "library") +
                            if (p.libraryCount > 1) " (${p.libraryIndex} of ${p.libraryCount})" else ""
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            StepRow(Icons.Rounded.Person, "Artists", n.format(p.artists), active = p.phase == SyncPhase.ARTISTS, done = p.phase.ordinal > SyncPhase.ARTISTS.ordinal)
            StepRow(Icons.Rounded.Album, "Albums", n.format(p.albums), active = p.phase == SyncPhase.ALBUMS, done = p.phase.ordinal > SyncPhase.ALBUMS.ordinal)
            StepRow(
                Icons.Rounded.MusicNote,
                "Songs",
                if (p.songsExpected > 0 && p.phase == SyncPhase.SONGS) "${n.format(p.songs)} of ${n.format(p.songsExpected)}" else n.format(p.songs),
                active = p.phase == SyncPhase.SONGS,
                done = p.phase.ordinal > SyncPhase.SONGS.ordinal,
            )

            if (p.running) {
                val fraction = p.fraction
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            if (p.phase == SyncPhase.DONE && p.merged > 0) {
                Text(
                    "${n.format(p.merged)} duplicate albums from different libraries were merged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            p.error?.let { ErrorText(it, Modifier) }
        }
    }
}

@Composable
private fun StepRow(icon: ImageVector, label: String, value: String, active: Boolean, done: Boolean) {
    val color = when {
        active -> MaterialTheme.colorScheme.primary
        done -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.Medium))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = color)
        Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterEnd) {
            if (done) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
    Spacer(Modifier.height(0.dp))
}

/**
 * Shows the libraries in [chosen] (issue #376): at once when every library is on the phone; otherwise (the first
 * time after updating) a sync fetches the missing ones.
 */
internal fun chooseLibraries(graph: app.musix.AppGraph, chosen: Set<String>) {
    graph.settings.setSyncFolders(chosen)
    graph.scope.launch {
        if (graph.library.keepsEveryLibrary()) graph.library.showFolders(chosen)
        else graph.sync.start(quiet = true)
    }
}
