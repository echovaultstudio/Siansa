package app.siansa.ui

import androidx.compose.foundation.layout.Box
import app.siansa.data.SyncPhase
import app.siansa.data.LibraryStatus
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.data.MusicFolder
import app.siansa.data.EMBY_GUEST_NOTE
import app.siansa.data.UserEntry
import app.siansa.data.UserFiles
import app.siansa.data.Users
import app.siansa.log.AppLog

/*
 * The source filter (issues #56, #124 and #141), under Sources in the panel behind Home's top-right buttons,
 * laid out like Symfonium's: only downloaded music, and a card per user with an eye to show or hide their
 * music, a Sync button and their libraries to tick. What's hidden is hidden everywhere in the app.
 */

/** The gap between a user's name and their libraries, and between the libraries and the card's edge. */
internal val SOURCE_CARD_GAP = Space.Medium

/** The Sources tab of the Home settings panel. */
@Composable
internal fun SourcesPanel(modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val shown by graph.guests.shown.collectAsStateWithLifecycle()
    val guestStates by graph.guests.state.collectAsStateWithLifecycle()
    val progress by graph.sync.progress.collectAsStateWithLifecycle()
    val users = remember { Users.all(context) }
    var hidingActive by remember { mutableStateOf<UserEntry?>(null) }
    val libraryStatus by graph.library.status.collectAsStateWithLifecycle()
    // A sync started from this sheet shows its progress and result here, like the Library sync page (#220).
    var watching by remember { mutableStateOf(false) }

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.Page)) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(settings.downloadedOnly, role = Role.Switch) { on ->
                        AppLog.i("Sources", "Only downloaded music: $on")
                        graph.settings.setDownloadedOnly(on)
                    }
                    .padding(start = Space.Page, end = Space.Page, top = Space.Page, bottom = Space.Page),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Only downloaded music", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Everything that isn't on your phone is hidden. Handy on a flight.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Space.Medium))
                Switch(checked = settings.downloadedOnly, onCheckedChange = null)
            }
        }

        PanelLabel("Users and libraries")
        Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
            users.forEach { user ->
                val inUse = user.id == Users.active
                val isShown = user.id in shown
                val folders by loadState("folders-${user.id}-$isShown") {
                    if (!isShown || !user.connected) emptyList()
                    else if (inUse) graph.source.musicFolders()
                    else graph.guests.apiOf(user.id).musicFolders()
                }
                val chosenIds = if (inUse) settings.syncFolders else graph.guests.settingsOf(user.id).current.syncFolders
                val all = folders.valueOrNull().orEmpty()
                val chosen = chosenIds.filter { id -> all.any { it.id == id } }.toSet().ifEmpty { all.map { it.id }.toSet() }
                val guest = guestStates[user.id]
                val syncing = if (inUse) progress.running else guest?.running == true
                SourceCard(
                    name = user.label,
                    status = when {
                        !user.connected -> "Not connected yet. Set it up in Settings → Users."
                        inUse -> inUseStatus(syncing, libraryStatus)
                        user.emby -> EMBY_GUEST_NOTE
                        !isShown -> "Hidden"
                        guest?.error != null -> "Couldn't load: ${guest.error}"
                        syncing -> "Loading their music… ${guest?.songs ?: 0} songs"
                        guest?.songs != null -> "Shown · ${guest.songs} songs"
                        else -> "Shown"
                    },
                    shown = isShown,
                    // An Emby user can't be shown next to another yet; one already shown can still be hidden.
                    canToggle = user.connected && (!inUse || shown.size > 1) && (inUse || !user.emby || isShown),
                    syncing = syncing,
                    libraries = if (isShown && all.size > 1) all.map { it.name to (it.id in chosen) } else emptyList(),
                    onToggle = {
                        when {
                            inUse -> hidingActive = user
                            else -> {
                                AppLog.i("Sources", "${if (isShown) "Hiding" else "Showing"} user ${user.id + 1}")
                                graph.guests.setShown(user.id, !isShown)
                            }
                        }
                    },
                    onSync = {
                        if (inUse) {
                            watching = true
                            // A full sync: new music and changed tags are picked up (issue #376).
                            graph.sync.start(full = true)
                        } else {
                            graph.guests.sync(user.id, force = true)
                        }
                    },
                    onLibrary = { index ->
                        val folder: MusicFolder = all[index]
                        val next = if (folder.id in chosen) chosen - folder.id else chosen + folder.id
                        // At least one library stays.
                        if (next.isNotEmpty() && !syncing) {
                            AppLog.i("Sources", "User ${user.id + 1}: ${next.size} of ${all.size} libraries")
                            if (inUse) {
                                // Every library is on the phone: shown or hidden at once (issue #376).
                                chooseLibraries(graph, next)
                            } else {
                                graph.guests.setFolders(user.id, next)
                            }
                        }
                    },
                )
                if (inUse && watching && progress.phase != SyncPhase.IDLE) {
                    Box(Modifier.testTag("source-sync-progress")) { ProgressCard(progress) }
                }
            }
        }
        Text(
            if (users.size > 1) "Shown users' music plays together. Your history, playlists and settings stay with the user in use."
            else "Add another user in Settings → Users to show their music here too.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.Medium, bottom = Space.Page, start = Space.Tight, end = Space.Tight),
        )
    }

    hidingActive?.let { user ->
        val next = UserFiles.afterHidingActive(shown, Users.active)
        val other = next?.let { (id, _) -> users.firstOrNull { it.id == id } }
        if (next == null || other == null) {
            hidingActive = null
        } else {
            AlertDialog(
                onDismissRequest = { hidingActive = null },
                title = { Text("Hide ${user.label}?") },
                text = { Text("Siansa restarts as ${other.label}, with their history, playlists and settings.") },
                confirmButton = {
                    TextButton(onClick = {
                        AppLog.i("Sources", "Hiding the user in use; switching to user ${next.first + 1}")
                        Users.switchTo(context, next.first, next.second)
                    }) { Text("Hide and restart") }
                },
                dismissButton = { TextButton(onClick = { hidingActive = null }) { Text("Cancel") } },
            )
        }
    }
}

/**
 * One user's card: the eye shows or hides their music, Sync loads it again, and their libraries can be
 * ticked on and off (shown only while their music is).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SourceCard(
    name: String,
    status: String,
    shown: Boolean,
    canToggle: Boolean,
    syncing: Boolean,
    libraries: List<Pair<String, Boolean>>,
    onToggle: () -> Unit,
    onSync: () -> Unit,
    onLibrary: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (shown) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("source-card"),
    ) {
        Column(Modifier.padding(start = Space.Tight, end = Space.Medium, top = Space.Tight, bottom = if (libraries.isEmpty()) Space.Tight else SOURCE_CARD_GAP)) {
            Row(Modifier.fillMaxWidth().testTag("source-name"), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggle, enabled = canToggle) {
                    Icon(
                        if (shown) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        if (shown) "Hide $name" else "Show $name",
                        tint = if (shown) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f).alpha(if (shown) 1f else 0.6f).padding(vertical = Space.Small)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                if (shown) {
                    Spacer(Modifier.width(Space.Small))
                    OutlinedButton(onClick = onSync, enabled = !syncing) { Text(if (syncing) "Syncing" else "Sync") }
                }
            }
            if (libraries.isNotEmpty()) {
                Spacer(Modifier.height(SOURCE_CARD_GAP))
                FlowRow(
                    Modifier.padding(start = Space.Medium),
                    horizontalArrangement = Arrangement.spacedBy(Space.Small),
                    verticalArrangement = Arrangement.spacedBy(Space.Small),
                ) {
                    libraries.forEachIndexed { i, (library, on) ->
                        LibraryChip(library, on, enabled = !syncing) { onLibrary(i) }
                    }
                }
            }
        }
    }
}

/** A library to tick on or off, like Symfonium's: a tick and the name in a rounded box. */
@Composable
private fun LibraryChip(name: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .clip(shape)
            .background(if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, if (on) Accent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant, shape)
            .toggleable(on, enabled = enabled, role = Role.Checkbox) { onClick() }
            .height(36.dp)
            .padding(horizontal = Space.Medium)
            .alpha(if (enabled) 1f else 0.6f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (on) {
            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(Space.Small))
        }
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = Space.Tight, top = Space.Section, bottom = Space.Medium),
    )
}

/**
 * Under Home's greeting while something is filtered: what is, as small chips that open Sources. When the
 * phone is offline with downloads on it, an offer to show only those.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SourceChips(onOpen: () -> Unit) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val connection by graph.connection.state.collectAsStateWithLifecycle()
    val shelf by graph.downloads.shelf.collectAsStateWithLifecycle()
    val chips = sourceChipLabels(settings.downloadedOnly)
    if (chips.isEmpty() && !(connection.offline && !shelf.isEmpty)) return
    Column(Modifier.padding(start = Space.Page, end = Space.Page, top = Space.Tight, bottom = Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
        if (chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                chips.forEach { label ->
                    Surface(onClick = onOpen, shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.padding(horizontal = Space.Medium, vertical = Space.Small), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (label == DOWNLOADED_CHIP) Icons.Rounded.DownloadForOffline else Icons.Rounded.Group,
                                null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.width(Space.Small))
                            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }
        if (connection.offline && !settings.downloadedOnly && !shelf.isEmpty) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().padding(start = Space.Page, end = Space.Small, top = Space.Tight, bottom = Space.Tight), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudOff, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(Space.Medium))
                    Text("You're offline. Show only downloaded music?", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        AppLog.i("Sources", "Only downloaded music: on (offered while offline)")
                        graph.settings.setDownloadedOnly(true)
                    }) { Text("Show") }
                }
            }
        }
    }
}

private const val DOWNLOADED_CHIP = "Only downloaded"

/**
 * The chips under Home's greeting: only "Only downloaded". Several users' music shown together has no chip
 * (beta report 5737de2e); the lit filter button says so instead.
 */
internal fun sourceChipLabels(downloadedOnly: Boolean): List<String> = buildList {
    if (downloadedOnly) add(DOWNLOADED_CHIP)
}

/** Whether Home's filter button is lit: something is hidden, or several users' music is shown. */
internal fun sourceFilterActive(downloadedOnly: Boolean, shownUsers: Int): Boolean = downloadedOnly || shownUsers > 1

/**
 * Home's one settings button (issue #152): opens the panel with Sources and Arrange. Lit while the source
 * filter hides something or shows several users.
 */
@Composable
internal fun SourceFilterButton(onClick: () -> Unit) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val shown by graph.guests.shown.collectAsStateWithLifecycle()
    val active = sourceFilterActive(settings.downloadedOnly, shown.size)
    IconButton(onClick = onClick) {
        Icon(Icons.Rounded.FilterList, "Source filter and Arrange Home", tint = if (active) Accent else MaterialTheme.colorScheme.onSurface)
    }
}

/** A user's card for the spacing check, with two libraries. */
@Composable
internal fun SourceCardForTest() {
    SourceCard(
        name = "anna on music.example.com",
        status = "In use",
        shown = true,
        canToggle = true,
        syncing = false,
        libraries = listOf("albums" to true, "compilations" to false),
        onToggle = {},
        onSync = {},
        onLibrary = {},
    )
}

/**
 * The status line of the user in use: syncing, or what's on the phone and when it was last synced, e.g.
 * "In use · 878 artists · 2,361 albums · 41,942 songs · synced 15:43" (#220).
 */
internal fun inUseStatus(syncing: Boolean, status: LibraryStatus?, now: Long = System.currentTimeMillis()): String {
    if (syncing) return "In use · syncing…"
    if (status == null) return "In use · not synced yet"
    val n = java.text.NumberFormat.getIntegerInstance()
    val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ROOT)
    val sameDay = day.format(java.util.Date(now)) == day.format(java.util.Date(status.syncedAt))
    val time = if (sameDay) java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(status.syncedAt))
    else java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(status.syncedAt))
    return "In use · ${n.format(status.artists)} artists · ${n.format(status.albums)} albums · ${n.format(status.songs)} songs · synced $time"
}
