package app.siansa.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.local.LocalFolder
import app.siansa.local.LocalStatus
import app.siansa.log.AppLog

/*
 * Settings › Servers and services › Files on this phone (issue #352): folders of music on the phone, read with
 * Android's permission for each folder (no storage permission), shown in the library next to the server's music.
 */

/** The fold's line: "Not set up", or the folders and songs. */
internal fun localLine(folders: Int, songs: Int): String = when {
    folders == 0 -> "Music on this phone · not set up"
    else -> "${plural(folders, "folder")} · ${plural(songs, "song")}"
}

/** How a scan or the MusicBrainz lookup stands, in a line; null when there's nothing to say. */
internal fun localStatusLine(status: LocalStatus, songs: Int): String? = when {
    status.scanning -> if (status.total > 0) "Reading files… ${status.read} of ${status.total}" else "Looking for music files…"
    status.error != null -> status.error
    status.lookingUp -> "Looking up MusicBrainz IDs… ${status.looked} of ${status.toLook}"
    status.lookupError != null -> status.lookupError
    songs > 0 -> "${status.matched} of ${plural(songs, "song")} are on your server too"
    else -> null
}

private fun plural(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"

/** The fold's contents, with Android's folder picker. */
@Composable
internal fun LocalMusicFields() {
    val graph = LocalGraph.current
    val data by graph.localMusic.data.collectAsStateWithLifecycle()
    val status by graph.localMusic.status.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) AppLog.i("Local", "No folder picked") else graph.localMusic.addFolder(uri)
    }
    LocalMusicContent(
        folders = data.folders,
        statusLine = localStatusLine(status, data.tracks.size),
        scanning = status.scanning,
        musicBrainz = data.musicBrainz,
        onAdd = { pick.launch(null) },
        onRemove = { graph.localMusic.removeFolder(it) },
        onScan = { graph.localMusic.scan() },
        onMusicBrainz = { graph.localMusic.setMusicBrainz(it) },
    )
}

/** What [LocalMusicFields] shows, without the picker and the scanner (for the screenshot test). */
@Composable
internal fun LocalMusicContent(
    folders: List<LocalFolder>,
    statusLine: String?,
    scanning: Boolean,
    musicBrainz: Boolean,
    onAdd: () -> Unit,
    onRemove: (LocalFolder) -> Unit,
    onScan: () -> Unit,
    onMusicBrainz: (Boolean) -> Unit,
) {
    Hint("Music files on this phone, shown next to your server's. Siansa only reads the folders you pick here.")
    folders.forEach { folder ->
        Row(Modifier.fillMaxWidth().testTag("local-folder"), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
            Text(
                folder.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = Space.Medium),
            )
            TextButton(onClick = { onRemove(folder) }) { Text("Remove") }
        }
    }
    statusLine?.let { Hint(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        OutlinedButton(onClick = onAdd, modifier = Modifier.testTag("local-add")) {
            Icon(Icons.Rounded.Add, null)
            Text("Add a folder", modifier = Modifier.padding(start = Space.Small))
        }
        if (folders.isNotEmpty()) OutlinedButton(onClick = onScan, enabled = !scanning) { Text(if (scanning) "Scanning" else "Scan again") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
        SwitchRow("Look up missing MusicBrainz IDs", musicBrainz, onMusicBrainz)
        Hint("For files without them in their tags: sends the artist and title to musicbrainz.org, once per file. The IDs match songs to your server's, so they can join mixes.")
    }
}
