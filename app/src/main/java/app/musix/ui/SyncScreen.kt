package app.musix.ui

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
    val folders by loadState("folders") { graph.subsonic.musicFolders() }

    Screen(title = "Library sync", nav = nav) { padding ->
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Musix keeps a copy of your library on the phone, so browsing and search are instant and mixes build quickly. " +
                    "It checks for changes each time the app starts and only fetches albums that are new or changed.",
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
                                        if (next.isNotEmpty()) graph.settings.setSyncFolders(next)
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    enabled = !progress.running,
                                    onCheckedChange = { on ->
                                        val next = if (on) chosen + folder.id else chosen - folder.id
                                        if (next.isNotEmpty()) graph.settings.setSyncFolders(next)
                                    },
                                )
                                Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        Hint("Albums that exist in more than one chosen library are shown once.")
                    }
                }
            }

            if (progress.phase != SyncPhase.IDLE) {
                ProgressCard(progress)
            }

            if (progress.running) {
                OutlinedButton(onClick = { graph.sync.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
            } else {
                Button(onClick = { graph.sync.start() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Sync, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (library == null) "Sync now" else "Check for changes")
                }
                if (library != null) {
                    OutlinedButton(onClick = { graph.sync.start(full = true) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Full sync (fetch everything again)")
                    }
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

@Composable
private fun ProgressCard(p: SyncProgress) {
    val n = NumberFormat.getIntegerInstance()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (p.phase) {
                    SyncPhase.DONE -> Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    SyncPhase.FAILED -> Unit
                    else -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                Spacer(Modifier.width(10.dp))
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
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = color)
        Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterEnd) {
            if (done) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
    Spacer(Modifier.height(0.dp))
}
