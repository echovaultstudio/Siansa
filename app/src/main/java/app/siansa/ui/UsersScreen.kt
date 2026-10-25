package app.siansa.ui

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.siansa.data.UserEntry
import app.siansa.data.UserFiles
import app.siansa.data.Users
import app.siansa.log.AppLog

/**
 * Settings → Users: every Navidrome login on this phone. Tap one to switch to it (the app restarts with
 * their library, history and settings), add another, or remove one that isn't in use.
 */
@Composable
fun UsersScreen(nav: NavController) {
    val context = LocalContext.current
    val graph = LocalGraph.current
    var changes by remember { mutableIntStateOf(0) }
    val users = remember(changes) { Users.all(context) }
    var removing by remember { mutableStateOf<UserEntry?>(null) }
    var naming by remember { mutableStateOf<UserEntry?>(null) }
    val progress by graph.sync.progress.collectAsStateWithLifecycle()
    val library by graph.library.status.collectAsStateWithLifecycle()

    Screen(title = "Users", nav = nav) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                "Each user has their own library, history and settings. Tap a user to switch; Siansa restarts. To show several users' music together, use the filter button on Home. Tap the pencil to give a server a name.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.Page, vertical = Space.Small),
            )
            users.forEach { user ->
                val inUse = user.id == Users.active
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !inUse) { Users.switchTo(context, user.id) }
                        .padding(start = Space.Page, end = Space.Tight, top = Space.Small, bottom = Space.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (inUse) Icons.Rounded.CheckCircle else Icons.Rounded.Person,
                        null,
                        tint = if (inUse) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f).padding(start = Space.Page, top = Space.Small, bottom = Space.Small)) {
                        Text(user.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (inUse) "In use" else "Tap to switch",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (inUse) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (user.server != null) {
                        IconButton(onClick = { naming = user }, modifier = Modifier.testTag("nameServer")) {
                            Icon(Icons.Rounded.Edit, "Name the server of ${user.label}")
                        }
                    }
                    if (!inUse) {
                        IconButton(onClick = { removing = user }) { Icon(Icons.Rounded.Delete, "Remove ${user.label}") }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable { Users.addAndSwitch(context) }.padding(horizontal = Space.Page, vertical = Space.Page),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = Space.Page)) {
                    Text("Add another user", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Another login on this server or a different one. Siansa restarts and asks for it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // The library of the user in use, synced to the phone (issue #273, was on the Settings start page).
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().clickable { nav.navigate("sync") }.padding(horizontal = Space.Page, vertical = Space.Page).testTag("librarySync"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Sync, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = Space.Page)) {
                    Text("Library sync", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        syncSummary(progress, library) + " · for the user in use",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    naming?.let { user ->
        val server = user.server ?: return@let
        // Each user's own name: two logins on one server can be told apart (issue #482).
        var name by remember(user.id) { mutableStateOf(user.serverName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text("Name this server") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    Text(
                        "Shown instead of its address for this user only, like \"Home\" or \"Kids\", so you can tell them apart. It only changes what Siansa shows. Leave it empty to show the address.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.replace("\n", " ").take(UserFiles.SERVER_NAME_MAX) },
                        singleLine = true,
                        placeholder = { Text(server.substringAfter("://").substringBefore('/').substringBefore(':')) },
                        modifier = Modifier.fillMaxWidth().testTag("serverNameField"),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    AppLog.i("Users", "Server of user ${user.id + 1} ${if (UserFiles.cleanServerName(name).isEmpty()) "unnamed" else "named"}")
                    Users.setServerName(context, user.id, name)
                    naming = null
                    changes++
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("Cancel") } },
        )
    }

    removing?.let { user ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${user.label}?") },
            text = { Text("Their library, history and settings are removed from this phone. Nothing changes on the server.") },
            confirmButton = {
                TextButton(onClick = {
                    // Their music leaves the library first, if it was shown.
                    graph.guests.setShown(user.id, false)
                    Users.remove(context, user.id)
                    removing = null
                    changes++
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}
