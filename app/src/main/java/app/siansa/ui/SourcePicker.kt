package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.siansa.data.Users
import app.siansa.log.AppLog
import app.siansa.net.SourceKind

/**
 * The first screen of a new setup (issue #113): where your music is. Only sources Siansa can already use can be
 * picked; the rest say "Coming soon". Next come the smart extras, then the connection details.
 */
@Composable
fun SourcePickerScreen(nav: NavController) {
    Screen(title = "Where's your music?", nav = null) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.Small),
        ) {
            Text(
                "Welcome to Siansa! Pick where your music lives. You can add smart extras on the next step.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Space.Page, vertical = Space.Small),
            )
            SourceKind.entries.forEach { kind ->
                SourceRow(kind) {
                    AppLog.i("Setup", "Source picked: ${kind.name}")
                    nav.navigate(extrasRoute(kind))
                }
            }
            SwitchBackButtons()
        }
    }
}

/** One source in the picker: its icon, name and what it covers, dimmed and not tappable until it's ready. */
@Composable
internal fun SourceRow(kind: SourceKind, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = kind.ready, onClick = onPick)
            .alpha(if (kind.ready) 1f else 0.45f)
            .padding(horizontal = Space.Page, vertical = Space.Medium)
            .testTag("source-${kind.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(sourceIcon(kind), null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f).padding(start = Space.Medium)) {
            Text(kind.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            // Two lines for every source, so the rows are one height whether the text wraps or not.
            Text(
                kind.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The picker's icon for a source. Plain icons rather than the projects' logos for now: Emby and Plex logos are
 * trademarks, to be checked before they're shown (issue #113).
 */
internal fun sourceIcon(kind: SourceKind): ImageVector = when (kind) {
    SourceKind.LOCAL -> Icons.Rounded.PhoneAndroid
    else -> Icons.Rounded.Dns
}

internal fun extrasRoute(kind: SourceKind) = "welcome/extras/${kind.name}"

/** Where the connection details open after the extras step: for [kind], with or without the AI fields. */
internal fun connectionsRoute(kind: SourceKind, extras: Boolean) = "settings/connections?source=${kind.name}&extras=$extras"

/**
 * The second step of a new setup (issue #113): AudioMuse-AI and AI titles, which the rest of Siansa works
 * without. Either way the connection details come next.
 */
@Composable
fun SmartExtrasScreen(nav: NavController, sourceName: String) {
    val kind = SourceKind.parse(sourceName)
    Screen(title = "Smart extras", nav = nav) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(
                    "AudioMuse-AI",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = Space.Medium),
                )
            }
            Text(
                "Listens to your music on your server, for mixes made for you, Vibes, Song path and searching by " +
                    "sound. Needs an AudioMuse-AI server. Everything else in Siansa works without it, and you can " +
                    "add it later in Settings." +
                    if (kind.embyLike) " With ${ServerWords.name(kind)}, all of these come from AudioMuse: set it up to read your ${ServerWords.name(kind)} library." else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { AppLog.i("Setup", "Smart extras: yes"); nav.navigate(connectionsRoute(kind, extras = true)) },
                modifier = Modifier.fillMaxWidth().testTag("extrasYes"),
            ) { Text("Add AudioMuse-AI") }
            OutlinedButton(
                onClick = { AppLog.i("Setup", "Smart extras: not now"); nav.navigate(connectionsRoute(kind, extras = false)) },
                modifier = Modifier.fillMaxWidth().testTag("extrasNo"),
            ) { Text("Not now") }
        }
    }
}

/** While a user is being added: a way back to the users already set up. */
@Composable
private fun SwitchBackButtons() {
    val context = LocalContext.current
    val others = remember { Users.all(context).filter { it.id != Users.active && it.connected } }
    others.forEach { user ->
        TextButton(onClick = { Users.switchTo(context, user.id) }, modifier = Modifier.padding(horizontal = Space.Small)) {
            Text("Switch back to ${user.label}")
        }
    }
}
