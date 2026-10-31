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
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.ui.graphics.vector.ImageVector
import app.siansa.data.SyncProgress
import app.siansa.data.BookSync
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
import androidx.compose.runtime.LaunchedEffect
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
import app.siansa.data.Users
import kotlinx.coroutines.launch
import app.siansa.log.AppLog

/*
 * The source filter (issues #56, #124 and #141), under Sources in the panel behind Home's top-right buttons,
 * laid out like Symfonium's: only downloaded music, and a card per user with an eye to show or hide their
 * music, a Sync button and their libraries to tick. What's hidden is hidden everywhere in the app.
 */

/** The eye (or a source's icon) at the start of a card: the name and the sync line start past it. */
internal val SOURCE_ICON = 48.dp

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
    // Users at another address are asked whether they're the same server (a domain and an IP for one Navidrome).
    val sameServerFound by graph.guests.sameServerFound.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.guests.checkSameServers() }
    // The user in use's own music hidden (issue #427): instant, their settings and history stay in use.
    val ownHidden by graph.library.ownHidden.collectAsStateWithLifecycle()
    val libraryStatus by graph.library.status.collectAsStateWithLifecycle()
    // A sync started from this sheet shows its progress and result here, as one small line (#220, #693).
    var watching by remember { mutableStateOf(false) }
    // Audiobookshelf, listed with the music servers (issue #693).
    val bookSync by graph.books.sync.collectAsStateWithLifecycle()
    var watchingBooks by remember { mutableStateOf(false) }
    val anySyncing = progress.running || guestStates.values.any { it.running } || bookSync.running

    fun syncAll() {
        val plan = syncAllPlan(
            inUseShown = !ownHidden,
            guests = users.filter { it.id != Users.active }.associate { it.id to graph.guests.sameServerNow(it.id) },
            shown = shown,
            booksReady = settings.booksReady,
        )
        AppLog.i("Sources", "Sync all: ${plan.describe()}")
        if (plan.inUse) {
            watching = true
            graph.sync.start(full = true)
        }
        plan.guests.forEach { graph.guests.sync(it, force = true) }
        if (plan.books) {
            watchingBooks = true
            graph.scope.launch { graph.books.syncAll() }
        }
    }

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

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PanelLabel("Servers and libraries") }
            FilledTonalButton(
                onClick = ::syncAll,
                enabled = !anySyncing,
                modifier = Modifier.padding(top = Space.Medium).testTag("syncAll"),
            ) { Text("Sync all") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
            users.forEach { user ->
                val inUse = user.id == Users.active
                val isShown = if (inUse) !ownHidden else user.id in shown
                // Only users on the same server show together; others are switched to in Settings › Connections.
                val same = if (inUse) true else remember(sameServerFound) { graph.guests.sameServerNow(user.id) }
                val elsewhere = same == false
                val checking = same == null
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
                    // The user, and under it the kind of server, then how it stands (#571): "max", "OpenSubsonic · In use · …".
                    name = user.title,
                    status = (if (user.connected) user.serverLine + " · " else "") + when {
                        !user.connected -> "Not connected yet. Set it up in Settings → Users."
                        elsewhere -> "on another server. To listen, switch to this user in Settings → Users."
                        checking -> "Checking whether it's your server…"
                        inUse && ownHidden -> "In use · their music is hidden. Their settings and history stay in use."
                        inUse -> inUseStatus(syncing, libraryStatus)
                        !isShown -> "Hidden"
                        guest?.error != null -> "Couldn't load: ${guest.error}"
                        syncing -> "Loading their music… ${guest?.songs ?: 0} songs"
                        guest?.songs != null -> "Shown · ${guest.songs} songs"
                        else -> "Shown"
                    },
                    shown = isShown,
                    canToggle = !elsewhere && !checking && canToggleUser(user.connected, inUse, ownHidden, shown.size),
                    syncing = syncing,
                    libraries = if (isShown && all.size > 1) all.map { it.name to (it.id in chosen) } else emptyList(),
                    onToggle = {
                        when {
                            inUse -> graph.scope.launch { graph.library.setOwnHidden(!ownHidden, settings.syncFolders) }
                            else -> {
                                AppLog.i("Sources", "${if (isShown) "Hiding" else "Showing"} user ${user.id + 1}")
                                graph.guests.setShown(user.id, !isShown)
                                // Hiding the last other user while the user in use's music is hidden shows theirs again.
                                if (isShown && ownHidden && shown.size <= 2) {
                                    graph.scope.launch { graph.library.setOwnHidden(false, settings.syncFolders) }
                                }
                            }
                        }
                    },
                    progress = if (inUse && watching && progress.phase != SyncPhase.IDLE) {
                        { SyncStrip(syncLine(progress), running = progress.running, fraction = progress.fraction, done = progress.phase == SyncPhase.DONE, failed = progress.phase == SyncPhase.FAILED) }
                    } else if (!inUse && isShown && syncing) {
                        { SyncStrip("Loading their music… ${guest?.songs ?: 0} songs", running = true, fraction = null, done = false, failed = false) }
                    } else null,
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
            }
            // Audiobookshelf (issue #693): listed like a server, with Sync; its books aren't hidden from here.
            if (settings.audiobookshelfUrl.isNotBlank()) {
                SourceCard(
                    name = "Audiobookshelf",
                    status = booksStatus(settings.booksReady, bookSync),
                    shown = true,
                    canToggle = false,
                    syncing = bookSync.running,
                    libraries = emptyList(),
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    onToggle = {},
                    onSync = {
                        watchingBooks = true
                        graph.scope.launch { graph.books.syncAll() }
                    },
                    canSync = settings.booksReady,
                    progress = if (watchingBooks && (bookSync.running || bookSync.done || bookSync.error != null)) {
                        { SyncStrip(booksLine(bookSync), running = bookSync.running, fraction = null, done = bookSync.done, failed = bookSync.error != null) }
                    } else null,
                )
            }
        }
        Text(
            if (users.size > 1) "Users on the same server can be shown together. Your history, playlists and settings stay with the user in use."
            else "Add another user in Settings → Users to show their music here too.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.Medium, bottom = Space.Page, start = Space.Tight, end = Space.Tight),
        )
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
    onLibrary: (Int) -> Unit = {},
    // A source that can't be hidden from here (Audiobookshelf) shows its own icon instead of the eye.
    icon: ImageVector? = null,
    canSync: Boolean = true,
    // A small line under the name while it syncs, and once when it's done (issue #693).
    progress: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (shown) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("source-card"),
    ) {
        Column(Modifier.padding(start = Space.Tight, end = Space.Medium, top = Space.Tight, bottom = if (libraries.isEmpty()) Space.Tight else SOURCE_CARD_GAP)) {
            Row(Modifier.fillMaxWidth().testTag("source-name"), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(Modifier.size(SOURCE_ICON), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Accent) }
                } else IconButton(onClick = onToggle, enabled = canToggle) {
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
                    OutlinedButton(onClick = onSync, enabled = !syncing && canSync) { Text(if (syncing) "Syncing" else "Sync") }
                }
            }
            progress?.let {
                it()
                // Room under the line when nothing follows it (with libraries, their gap below does it).
                if (libraries.isEmpty()) Spacer(Modifier.height(Space.Small))
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

/**
 * Whether a user's eye in Sources can be tapped (issue #427): something always shows, so the user in use's own
 * music can only be hidden while another user's is shown ([shownUsers] counts the user in use too).
 */
internal fun canToggleUser(connected: Boolean, inUse: Boolean, ownHidden: Boolean, shownUsers: Int): Boolean =
    connected && (!inUse || ownHidden || shownUsers > 1)

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
internal fun SourceCardForTest(syncLine: String? = null) {
    SourceCard(
        progress = syncLine?.let { { SyncStrip(it, running = false, fraction = null, done = true, failed = false) } },
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

/**
 * One small line under a source while it syncs (issue #693): a thin bar while it runs, then a single tick and the
 * result once it's done, instead of a card that fills the sheet.
 */
@Composable
internal fun SyncStrip(text: String, running: Boolean, fraction: Float?, done: Boolean, failed: Boolean) {
    Column(
        // Lined up under the source's name, past the eye.
        Modifier.fillMaxWidth().padding(start = SOURCE_ICON, end = Space.Tight).testTag("source-sync-progress"),
        verticalArrangement = Arrangement.spacedBy(Space.Tight),
    ) {
        if (running) {
            if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (done) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).testTag("source-sync-done"))
                Spacer(Modifier.width(Space.Tight))
            }
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The music sync on one line (issue #693): the library and the step it's on, or how it ended. */
internal fun syncLine(p: SyncProgress): String {
    val n = java.text.NumberFormat.getIntegerInstance()
    return when (p.phase) {
        SyncPhase.DONE, SyncPhase.FAILED, SyncPhase.CONNECTING, SyncPhase.SAVING, SyncPhase.IDLE ->
            syncHeadline(p) + (p.error?.takeIf { p.phase == SyncPhase.FAILED }?.let { ": $it" } ?: "")
        else -> listOfNotNull(
            p.libraryName?.let { if (p.libraryCount > 1) "$it (${p.libraryIndex} of ${p.libraryCount})" else it },
            when (p.phase) {
                SyncPhase.ARTISTS -> "${n.format(p.artists)} artists"
                SyncPhase.ALBUMS -> "${n.format(p.albums)} albums"
                SyncPhase.SONGS -> if (p.songsExpected > 0) "${n.format(p.songs)} of ${n.format(p.songsExpected)} songs" else "${n.format(p.songs)} songs"
                else -> null
            },
        ).joinToString(" · ").ifEmpty { "Syncing…" }
    }
}

/** Audiobookshelf's line on its card (issue #693). */
internal fun booksStatus(ready: Boolean, sync: BookSync): String = when {
    !ready -> "Not signed in. Set it up in Settings › Connections."
    sync.running -> "Audiobooks and podcasts · syncing…"
    sync.done -> "Audiobooks and podcasts · " + booksCounts(sync)
    else -> "Audiobooks and podcasts"
}

/** Audiobookshelf's sync on one line (issue #693). */
internal fun booksLine(sync: BookSync): String = when {
    sync.running -> "Reading your libraries…"
    sync.error != null -> "Sync failed: ${sync.error}"
    sync.done -> "Synced · " + booksCounts(sync)
    else -> ""
}

private fun booksCounts(sync: BookSync): String {
    val n = java.text.NumberFormat.getIntegerInstance()
    return listOfNotNull(
        "${n.format(sync.books)} ${if (sync.books == 1) "book" else "books"}",
        sync.podcasts.takeIf { it > 0 }?.let { "${n.format(it)} ${if (it == 1) "podcast" else "podcasts"}" },
    ).joinToString(" · ")
}

/** What Sync all syncs (issue #693): the user in use when their music shows, other shown users on the same server, and Audiobookshelf. */
internal data class SyncAllPlan(val inUse: Boolean, val guests: List<Int>, val books: Boolean) {
    fun describe(): String = listOfNotNull(
        "your music".takeIf { inUse },
        guests.takeIf { it.isNotEmpty() }?.let { "${it.size} other ${if (it.size == 1) "user" else "users"}" },
        "Audiobookshelf".takeIf { books },
    ).joinToString(", ").ifEmpty { "nothing shown" }
}

/** [guests]: each other user and whether they're on the same server (null while that's being checked). */
internal fun syncAllPlan(inUseShown: Boolean, guests: Map<Int, Boolean?>, shown: Set<Int>, booksReady: Boolean): SyncAllPlan =
    SyncAllPlan(
        inUse = inUseShown,
        guests = guests.filter { (id, same) -> same == true && id in shown }.keys.sorted(),
        books = booksReady,
    )
