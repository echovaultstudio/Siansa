package app.siansa.ui

import androidx.compose.material.icons.rounded.AutoAwesome
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.siansa.data.LocalPlaylists
import app.siansa.data.Users
import app.siansa.data.Playlist
import app.siansa.data.PlaylistEditor
import app.siansa.data.Song
import app.siansa.data.toPlaylist
import app.siansa.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Navidrome takes pictures up to 10 MB. */
private const val MAX_PICTURE_BYTES = 10 * 1024 * 1024

/**
 * Picks one of your playlists, or makes a new one, and adds [songs] to its end.
 * [onDone] gets the message to show, or null when nothing was done.
 */
@Composable
fun AddToPlaylistDialog(songs: List<Song>, onDone: (String?) -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    var naming by remember { mutableStateOf(false) }
    // One tap at a time, so a double tap doesn't add the songs twice.
    var busy by remember { mutableStateOf(false) }
    // Whose songs these are: a server playlist only takes its own user's songs (a phone playlist takes any).
    val songOwners = remember(songs) { songs.mapTo(HashSet()) { graph.guests.ownerOf(it.id) ?: Users.active } }
    val serverTakes = songOwners.all { it == Users.active }
    val playlists by loadState("editable-playlists") {
        val server = if (graph.connection.current.offline) graph.library.playlists()
        else runCatching { graph.source.playlists() }.getOrElse { graph.library.playlists() }
        val mine = editablePlaylists(graph.playlists.removed.visible(server).filter { graph.guests.ownerOf(it.id) == null }, graph.settings.current.navidromeUser)
        // Other shown users' playlists they can edit, for their own songs.
        val theirs = graph.library.playlists().filter { p ->
            val owner = graph.guests.ownerOf(p.id) ?: return@filter false
            PlaylistEditor.canEdit(p, graph.guests.settingsOf(owner).current.navidromeUser)
        }.sortedBy { it.name.lowercase() }
        val phone = graph.localPlaylists.all().map { it.toPlaylist() }.sortedBy { it.name.lowercase() }
        (mine + theirs + phone).map { p ->
            val owner = if (LocalPlaylists.isLocal(p.id)) null else graph.guests.ownerOf(p.id) ?: Users.active
            p to PlaylistEditor.takes(owner, songOwners)
        }.sortedBy { (_, ok) -> !ok }
    }
    if (naming) {
        NewPlaylistDialog(
            serverAllowed = serverTakes,
            onDismiss = { naming = false },
            onCreate = { name, onPhone ->
                if (!busy) {
                    busy = true
                    scope.launch {
                        val result = graph.playlists.create(name, songs, onPhone)
                        onDone(result.fold({ "Added to $name" }, { it.message ?: "Couldn't make the playlist" }))
                    }
                }
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("Add to playlist") },
        text = {
            Column {
                PickerRow(onClick = { naming = true }) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, null)
                    }
                    Spacer(Modifier.width(Space.Medium))
                    Text("New playlist", style = MaterialTheme.typography.bodyLarge)
                }
                LoadBox(playlists) { list ->
                    if (list.isEmpty()) {
                        Text("You have no playlists of your own yet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.Small))
                    } else {
                        LazyColumn(Modifier.height(320.dp)) {
                            items(list, key = { it.first.id }) { (playlist, takes) ->
                                PickerRow(enabled = takes, onClick = {
                                    if (!busy && takes) {
                                        busy = true
                                        scope.launch {
                                            val result = graph.playlists.add(playlist.id, songs)
                                            val failure = result.exceptionOrNull()?.message?.takeIf { it == PlaylistEditor.OFFLINE }
                                            onDone(if (result.isSuccess) "Added to ${playlist.name}" else failure ?: "Couldn't add it to ${playlist.name}")
                                        }
                                    }
                                }) {
                                    Cover(playlist.coverArt, 150, Modifier.size(40.dp), RoundedCornerShape(4.dp))
                                    Spacer(Modifier.width(Space.Medium))
                                    Column(Modifier.weight(1f)) {
                                        Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            when {
                                                !takes -> "Only for songs from ${playlistOwnerName(playlist)}"
                                                LocalPlaylists.isLocal(playlist.id) -> "${playlist.songCount} songs · On this phone"
                                                else -> "${playlist.songCount} songs"
                                            },
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

@Composable
private fun PickerRow(onClick: () -> Unit, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f).padding(vertical = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Whose a server playlist is, for "Only for songs from …": its owner on the server, when it says. */
internal fun playlistOwnerName(playlist: Playlist): String = playlist.owner?.takeIf { it.isNotBlank() } ?: "its owner"

/** Asks for a playlist's name; [onConfirm] gets it trimmed, and only when it isn't empty. */
@Composable
fun PlaylistNameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) =
    NameDialog(title, initial, confirm, askWhere = false, onDismiss = onDismiss, onConfirm = { name, _ -> onConfirm(name) })

/** Asks for a new playlist's name and where to keep it: on Navidrome (the default), or on this phone only. */
@Composable
fun NewPlaylistDialog(onDismiss: () -> Unit, serverAllowed: Boolean = true, onCreate: (name: String, onPhone: Boolean) -> Unit) =
    NameDialog("New playlist", "", "Create", askWhere = true, serverAllowed = serverAllowed, onDismiss = onDismiss, onConfirm = onCreate)

/**
 * "Save as playlist" on a mix, radio or card: its songs as a new playlist, named like the mix to start with, on
 * your server or on this phone. With another shown user's songs in it, only a phone playlist can hold them.
 */
@Composable
fun SaveAsPlaylistDialog(title: String, songs: List<Song>, onDone: () -> Unit) {
    val graph = LocalGraph.current
    val launcher = rememberPlayLauncher()
    val scope = rememberCoroutineScope()
    val serverTakes = remember(songs) { songs.all { graph.guests.ownerOf(it.id) == null } }
    var busy by remember { mutableStateOf(false) }
    NameDialog(
        "Save as playlist", title, "Save", askWhere = true, serverAllowed = serverTakes,
        onDismiss = onDone,
        onConfirm = { name, onPhone ->
            if (!busy) {
                busy = true
                scope.launch {
                    val result = graph.playlists.create(name, songs, onPhone)
                    result.onSuccess { AppLog.i("Playlist", "Saved \"$name\" with ${songs.size} songs" + if (onPhone) " on the phone" else "") }
                    launcher.notify(result.fold({ "Saved as playlist “$name”" }, { it.message ?: "Couldn't save the playlist" }))
                    onDone()
                }
            }
        },
    )
}

/** "Create memory" (issue #186): the playlist's name, ready filled in, and where to keep it. */
@Composable
fun MemoryDialog(initial: String, onDismiss: () -> Unit, onCreate: (name: String, onPhone: Boolean) -> Unit) =
    NameDialog("Create memory", initial, "Create", askWhere = true, onDismiss = onDismiss, onConfirm = onCreate)

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    askWhere: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit,
    /** False when the songs include another user's: only a phone playlist can hold them. */
    serverAllowed: Boolean = true,
) {
    val graph = LocalGraph.current
    var text by remember { mutableStateOf(initial) }
    // Offline, or with another user's songs, only a phone playlist can be made, so that's the one picked.
    var onPhone by remember { mutableStateOf(graph.connection.current.offline || !serverAllowed) }
    val name = PlaylistEditor.cleanName(text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(PlaylistEditor.MAX_NAME) },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { name?.let { onConfirm(it, onPhone) } }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (askWhere) {
                    Spacer(Modifier.height(Space.Medium))
                    WhereOption(
                        "On your server",
                        if (serverAllowed) "Syncs with your server and your other apps." else "Not for another user's songs. Keep it on this phone instead.",
                        selected = !onPhone, enabled = serverAllowed,
                    ) { onPhone = false }
                    WhereOption("On this phone only", "Its songs download to this phone so it works offline. Only this phone has it.", selected = onPhone) { onPhone = true }
                }
            }
        },
        confirmButton = { TextButton(onClick = { name?.let { onConfirm(it, onPhone) } }, enabled = name != null) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun WhereOption(label: String, detail: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f).padding(vertical = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Space.Small))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The ⋮ button on a playlist's page: rename and change or remove the picture (when it's [editable]),
 * send a phone playlist to Navidrome, and remove it from Siansa, which keeps a Navidrome playlist on the
 * server. [onChanged] runs after a change (to load the page again), [onDeleted] after it's gone from
 * Siansa, and [onMoved] with the Navidrome playlist a phone playlist became.
 */
@Composable
fun PlaylistMenuButton(
    playlist: Playlist,
    editable: Boolean,
    notify: (String) -> Unit,
    onChanged: () -> Unit,
    onDeleted: () -> Unit,
    onMoved: (Playlist?) -> Unit,
) {
    val onPhone = LocalPlaylists.isLocal(playlist.id)
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    fun report(result: Result<Unit>, done: String, failed: String) {
        result.onSuccess { notify(done); onChanged() }
            .onFailure { notify(it.message?.takeIf { m -> m.isNotBlank() } ?: failed) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picture = withContext(Dispatchers.IO) {
                runCatching {
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("The picture couldn't be read")
                    mime to bytes
                }
            }.getOrElse {
                AppLog.w("Playlist", "Picture not read: ${it.message}")
                notify("That picture couldn't be read")
                return@launch
            }
            if (picture.second.size > MAX_PICTURE_BYTES) {
                notify("That picture is too big. Pick one under 10 MB.")
                return@launch
            }
            report(graph.playlists.setPicture(playlist.id, picture.second, picture.first), "Picture changed", "Couldn't change the picture")
        }
    }

    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Playlist options", tint = Color.White) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (editable) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                    onClick = { menu = false; renaming = true },
                )
                DropdownMenuItem(
                    text = { Text("Change picture") },
                    leadingIcon = { Icon(Icons.Rounded.Image, null) },
                    onClick = {
                        menu = false
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                DropdownMenuItem(
                    text = { Text("Remove picture") },
                    leadingIcon = { Icon(Icons.Rounded.HideImage, null) },
                    onClick = {
                        menu = false
                        scope.launch { report(graph.playlists.removePicture(playlist.id), "Picture removed", "Couldn't remove the picture") }
                    },
                )
            }
            if (onPhone) {
                DropdownMenuItem(
                    text = { Text("Send to your server") },
                    leadingIcon = { Icon(Icons.Rounded.CloudUpload, null) },
                    onClick = {
                        menu = false
                        scope.launch {
                            graph.playlists.moveToServer(playlist.id)
                                .onSuccess { notify("Sent to your server"); onMoved(it) }
                                .onFailure { notify(it.message?.takeIf { m -> m.isNotBlank() } ?: "Couldn't send it") }
                        }
                    },
                )
            }
            // Song Alchemy (issue #208): new songs that sound like the whole playlist blended together.
            if (graph.settings.current.audioMuseReady) {
                val nav = LocalNav.current
                DropdownMenuItem(
                    text = { Text("More like this playlist") },
                    leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                    onClick = { menu = false; nav.navigate("blend/${Uri.encode(playlist.id)}") },
                )
            }
            DropdownMenuItem(
                text = { Text(if (onPhone) "Delete playlist" else "Remove from Siansa") },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { menu = false; deleting = true },
            )
        }
    }

    if (renaming) {
        PlaylistNameDialog(
            title = "Rename playlist",
            initial = playlist.name,
            confirm = "Save",
            onDismiss = { renaming = false },
            onConfirm = { name ->
                renaming = false
                if (name != playlist.name) scope.launch { report(graph.playlists.rename(playlist.id, name), "Renamed to $name", "Couldn't rename it") }
            },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(if (onPhone) "Delete “${playlist.name}”?" else "Remove “${playlist.name}”?") },
            text = { Text(if (onPhone) "It's only on this phone, so it's gone for good. The songs stay in your library." else "It stays on your server. To get it back, use + then Import from your server in Your Library.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch {
                        graph.playlists.removeFromApp(playlist.id)
                        notify(if (onPhone) "Deleted ${playlist.name}" else "Removed ${playlist.name}")
                        onDeleted()
                    }
                }) { Text(if (onPhone) "Delete" else "Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Navidrome playlists you removed from Siansa, to bring back. [onDone] gets the message to show, or null.
 */
@Composable
fun ImportPlaylistsDialog(onDone: (String?) -> Unit) {
    val graph = LocalGraph.current
    val removed by loadState("removed-playlists") {
        val ids = graph.playlists.removed.ids()
        graph.source.playlists().filter { it.id in ids }.sortedBy { it.name.lowercase() }
    }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("Import from your server") },
        text = {
            LoadBox(removed) { list ->
                if (list.isEmpty()) {
                    Text("Every playlist on your server is already in Siansa. New ones show up by themselves.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(Modifier.height(320.dp)) {
                        items(list, key = { it.id }) { playlist ->
                            PickerRow(onClick = {
                                graph.playlists.restore(playlist.id)
                                onDone("Imported ${playlist.name}")
                            }) {
                                Cover(playlist.coverArt, 150, Modifier.size(40.dp), RoundedCornerShape(4.dp))
                                Spacer(Modifier.width(Space.Medium))
                                Column(Modifier.weight(1f)) {
                                    Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(playlistSubtitle(playlist, fallbackOwner = ServerWords.name(LocalGraph.current.settings.current.sourceKind)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Close") } },
    )
}
