package app.musix.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
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
import app.musix.data.UserEntry
import app.musix.data.Users

/**
 * Settings → Users: every Navidrome login on this phone. Tap one to switch to it (the app restarts with
 * their library, history and settings), add another, or remove one that isn't in use.
 */
@Composable
fun UsersScreen(nav: NavController) {
    val context = LocalContext.current
    var changes by remember { mutableIntStateOf(0) }
    val users = remember(changes) { Users.all(context) }
    var removing by remember { mutableStateOf<UserEntry?>(null) }

    Screen(title = "Users", nav = nav) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                "Each user has their own library, history and settings. Tap a user to switch; Musix restarts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            users.forEach { user ->
                val inUse = user.id == Users.active
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !inUse) { Users.switchTo(context, user.id) }
                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (inUse) Icons.Rounded.CheckCircle else Icons.Rounded.Person,
                        null,
                        tint = if (inUse) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f).padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
                        Text(user.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (inUse) "In use" else "Tap to switch",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (inUse) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!inUse) {
                        IconButton(onClick = { removing = user }) { Icon(Icons.Rounded.Delete, "Remove ${user.label}") }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable { Users.addAndSwitch(context) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text("Add another user", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Another login on this server or a different one. Musix restarts and asks for it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    removing?.let { user ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${user.label}?") },
            text = { Text("Their library, history and settings are removed from this phone. Nothing changes on the server.") },
            confirmButton = {
                TextButton(onClick = {
                    Users.remove(context, user.id)
                    removing = null
                    changes++
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}
