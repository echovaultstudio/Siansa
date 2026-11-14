package app.siansa.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.AppGraph
import app.siansa.BuildConfig
import app.siansa.net.BeatsApi
import app.siansa.playback.ProfileStore
import app.siansa.data.AiProvider
import app.siansa.data.AppSettings
import app.siansa.data.SettingsRepository
import app.siansa.data.UserEntry
import app.siansa.data.UserFiles
import app.siansa.data.Users
import app.siansa.log.AppLog
import app.siansa.mix.audioMuseCheck
import app.siansa.net.ApiException
import app.siansa.net.AudioMuseApi
import app.siansa.net.AudiobookshelfApi
import app.siansa.net.ChatApi
import app.siansa.net.EmbyApi
import app.siansa.net.SourceKind
import app.siansa.net.SubsonicApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Settings › Connections (issue #665): everything Siansa connects to, on one page, where Users and Connections were
 * two. **Servers**: each music server login (a "user": its own library, history and settings), with its address,
 * login, its name in AudioMuse-AI and its library sync, then Audiobookshelf. **Services**: AudioMuse-AI and the AI
 * service that writes mix titles and sound searches. Each folds open with its arrow, folded to start with; [open]
 * ("server", "books", "audiomuse", "ai") opens one. One Save keeps every change on the page.
 */
@Composable
fun ConnectionsHub(nav: NavController, open: String? = null) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(graph.settings.current) }
    var problem by remember { mutableStateOf<String?>(null) }
    var opened by rememberSaveable { mutableStateOf(setOfNotNull(open)) }
    var changes by remember { mutableIntStateOf(0) }
    val users = remember(changes) { Users.all(context) }
    val inUse = users.firstOrNull { it.id == Users.active }
    var serverName by remember(changes) { mutableStateOf(inUse?.serverName.orEmpty()) }
    var removing by remember { mutableStateOf<UserEntry?>(null) }
    // The other logins' server settings, to change without switching to them first (issue #687).
    val othersSaved = remember(changes) { users.filter { it.id != Users.active }.associate { it.id to SettingsRepository(context, it.id).current } }
    var others by remember(changes) { mutableStateOf(othersSaved) }
    var otherNames by remember(changes) { mutableStateOf(users.filter { it.id != Users.active }.associate { it.id to it.serverName.orEmpty() }) }
    // A server being added (issue #828): only on this page until it's saved, so nothing is added, switched to or
    // restarted before then, and the music plays on.
    var adding by rememberSaveable { mutableStateOf(false) }
    var newServer by remember { mutableStateOf(AppSettings()) }
    var newName by remember { mutableStateOf("") }
    fun toggle(key: String) { opened = if (key in opened) opened - key else opened + key }

    /** Keeps the server being added as another login; false (with [problem] set) when it can't be. */
    fun saveNew(): Boolean {
        if (newServer.navidromeUrl.isBlank() || newServer.navidromeUser.isBlank() && !newServer.hasApiKey) {
            problem = "New server: enter its address and username first"
            return false
        }
        val id = try {
            Users.add(context) { id ->
                val store = SettingsRepository(context, id)
                store.save(store.current.withServerFrom(newServer).copy(sourceKind = newServer.sourceKind))
            }
        } catch (e: Exception) {
            problem = "New server: ${e.message}"
            return false
        }
        if (newName.isNotBlank()) Users.setServerName(context, id, newName)
        AppLog.i("Settings", "Server added as another login: ${newServer.sourceKind.label} ${AppLog.redact(newServer.navidromeUrl)} as ${newServer.navidromeUser}")
        adding = false
        newServer = AppSettings()
        newName = ""
        problem = null
        opened = opened + "user-$id"
        changes++
        return true
    }

    /** Keeps another login's changed server settings and name; false (with [problem] set) when they can't be. */
    fun saveOther(user: UserEntry): Boolean {
        val edited = others[user.id] ?: return true
        val saved = othersSaved[user.id] ?: return true
        if (saved.serverDiffers(edited)) {
            try {
                val store = SettingsRepository(context, user.id)
                store.save(store.current.withServerFrom(edited))
            } catch (e: Exception) {
                problem = "${user.label}: ${e.message}"
                return false
            }
            graph.guests.reloadSettings(user.id)
            AppLog.i("Settings", "Server login changed for another user: ${AppLog.redact(edited.navidromeUrl)} as ${edited.navidromeUser}")
        }
        val name = otherNames[user.id] ?: return true
        if (UserFiles.cleanServerName(name) != user.serverName.orEmpty()) Users.setServerName(context, user.id, name)
        return true
    }

    /**
     * API keys checked with their servers before anything is kept (issue #493): false (with [problem] set) when a
     * server doesn't take keys or doesn't know the key. A key's user fills an empty username.
     */
    suspend fun keysChecked(): Boolean {
        val label = users.associate { it.id to it.label }
        var step = "New server"
        return try {
            if (adding) newServer = keyChecked(graph, newServer, newServer.sourceKind, null)
            for ((id, other) in others) {
                step = label[id].orEmpty()
                others = others + (id to keyChecked(graph, other, other.sourceKind, othersSaved[id]))
            }
            step = ""
            draft = keyChecked(graph, draft, graph.settings.current.sourceKind, graph.settings.current)
            true
        } catch (e: ApiException) {
            problem = if (step.isEmpty()) e.message else "$step: ${e.message}"
            false
        }
    }

    fun saveNow() {
        // A server being added, then the other logins: each save writes the shared AudioMuse and AI settings too, and
        // the page's own come last.
        if (adding && !saveNew()) return
        for (user in users) if (user.id != Users.active && !saveOther(user)) return
        val before = graph.settings.current
        try {
            graph.settings.save(before.withConnectionsFrom(draft))
        } catch (e: Exception) {
            problem = e.message
            return
        }
        problem = null
        val now = graph.settings.current
        if (inUse != null && UserFiles.cleanServerName(serverName) != inUse.serverName.orEmpty()) {
            Users.setServerName(context, inUse.id, serverName)
            changes++
        }
        if (now.audiobookshelfUrl != before.audiobookshelfUrl || now.audiobookshelfUser != before.audiobookshelfUser ||
            now.audiobookshelfToken != before.audiobookshelfToken
        ) {
            AppLog.i("Settings", "Audiobookshelf ${if (now.booksReady) "saved: " + AppLog.redact(now.audiobookshelfUrl) else "cleared"}")
            scope.launch { graph.books.forget() }
        }
        AppLog.i("Settings", "Connections saved · AI: ${if (now.aiReady) "${now.aiProvider.label} ${now.aiModel}" else "off"}")
        nav.popBackStack()
    }

    fun save() {
        scope.launch { if (keysChecked()) saveNow() }
    }

    Screen(
        title = "Connections",
        nav = nav,
        actions = { IconButton(onClick = ::save, modifier = Modifier.testTag("connectionsSave")) { Icon(Icons.Rounded.Check, "Save") } },
    ) { padding ->
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .aboveKeyboard()
                .verticalScroll(rememberScrollState())
                .padding(Space.Page)
                .testTag("connections"),
            verticalArrangement = Arrangement.spacedBy(Space.Small),
        ) {
            Hint("Everything Siansa connects to. Tap a server or service to open its settings; Save keeps every change.")

            SectionHeading("Servers")
            users.forEach { user ->
                val key = if (user.id == Users.active) "server" else "user-${user.id}"
                FoldCard(
                    icon = if (user.id == Users.active) Icons.Rounded.CheckCircle else Icons.Rounded.Dns,
                    title = user.title,
                    subtitle = user.serverLine + if (user.id == Users.active) " · In use" else "",
                    accent = user.id == Users.active,
                    expanded = key in opened,
                    onToggle = { toggle(key) },
                    tag = "fold-$key",
                ) {
                    if (user.id == Users.active) {
                        ServerFields(graph, draft, { draft = it; problem = null }, serverName, { serverName = it }, nav)
                        Button(onClick = ::save, modifier = Modifier.focusRing().fillMaxWidth()) { Text("Save") }
                    } else {
                        Hint("Another login, with its own library, history and settings. Change it here; switch to it to use it (Siansa restarts).")
                        others[user.id]?.let { other ->
                            ServerFields(
                                graph, other, { others = others + (user.id to it); problem = null },
                                otherNames[user.id].orEmpty(), { otherNames = otherNames + (user.id to it) }, nav, inUse = false,
                            )
                        }
                        Button(onClick = ::save, modifier = Modifier.focusRing().fillMaxWidth().testTag("saveUser-${user.id}")) { Text("Save") }
                        Row(Modifier.tvFocusGroup(), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                            Button(onClick = { scope.launch { if (keysChecked() && saveOther(user)) Users.switchTo(context, user.id) } }, modifier = Modifier.focusRing()) { Text("Switch to it") }
                            OutlinedButton(onClick = { removing = user }, modifier = Modifier.focusRing()) { Text("Remove") }
                        }
                    }
                }
            }
            // The TV has no books (issue #860; the owner, 12 Nov): Audiobookshelf is set up on the phone.
            if (!app.siansa.BuildConfig.TV_APP) FoldCard(
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                title = "Audiobookshelf",
                subtitle = absLine(draft.booksReady),
                expanded = "books" in opened,
                onToggle = { toggle("books") },
                tag = "fold-books",
            ) {
                AudiobookshelfFields(graph, draft) { draft = it; problem = null }
                Button(onClick = ::save, modifier = Modifier.focusRing().fillMaxWidth()) { Text("Save") }
            }
            if (adding) {
                FoldCard(
                    icon = Icons.Rounded.Add,
                    title = "New server",
                    subtitle = "Not saved yet",
                    accent = false,
                    expanded = true,
                    onToggle = {},
                    tag = "fold-newServer",
                ) {
                    ServerKindChips(newServer.sourceKind) { newServer = newServer.copy(sourceKind = it); problem = null }
                    ServerFields(graph, newServer, { newServer = it; problem = null }, newName, { newName = it }, nav, inUse = false)
                    NewServerButtons(onSave = { scope.launch { if (keysChecked()) saveNew() } }, onCancel = { adding = false; newServer = AppSettings(); newName = ""; problem = null })
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().clickable { adding = true }.padding(vertical = Space.Medium).testTag("addServer"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start = Space.Page)) {
                        Text("Add a music server", style = MaterialTheme.typography.bodyLarge)
                        Hint("Another login on a server, or another server, with its own library. Nothing is added until you save it, and the music keeps playing. To show several together, use the filter button on Home.")
                    }
                }
            }

            SectionHeading("Services")
            FoldCard(
                icon = Icons.Rounded.GraphicEq,
                title = "AudioMuse-AI",
                subtitle = if (draft.audioMuseUrl.isNotBlank()) "Sound analysis · set up" else "Sound analysis · not set up",
                expanded = "audiomuse" in opened,
                onToggle = { toggle("audiomuse") },
                tag = "fold-audiomuse",
            ) {
                AudioMuseFields(graph, draft) { draft = it; problem = null }
                Button(onClick = ::save, modifier = Modifier.focusRing().fillMaxWidth()) { Text("Save") }
            }
            if (BEATS_OFFERED) {
                FoldCard(
                    icon = Icons.Rounded.Speed,
                    title = "Siansa Beats",
                    subtitle = beatsLine(draft.beatsReady),
                    expanded = "beats" in opened,
                    onToggle = { toggle("beats") },
                    tag = "fold-beats",
                ) {
                    BeatsFields(graph, draft) { draft = it; problem = null }
                    Button(onClick = ::save, modifier = Modifier.focusRing().fillMaxWidth()) { Text("Save") }
                }
            }
            FoldCard(
                icon = Icons.Rounded.AutoAwesome,
                title = "AI",
                subtitle = aiLine(draft),
                expanded = "ai" in opened,
                onToggle = { toggle("ai") },
                tag = "fold-ai",
            ) {
                AiFields(graph, draft) { draft = it; problem = null }
                Button(onClick = ::save, modifier = Modifier.focusRing().fillMaxWidth()) { Text("Save") }
            }
            problem?.let { ErrorText(it, Modifier) }
        }
    }

    removing?.let { user ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${user.label}?") },
            text = { Text("Their library, history and settings are removed from this phone. Nothing changes on the server.") },
            confirmButton = {
                TextButton(modifier = Modifier.focusRing(), onClick = {
                    graph.guests.setShown(user.id, false)
                    Users.remove(context, user.id)
                    removing = null
                    changes++
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }, modifier = Modifier.focusRing()) { Text("Cancel") } },
        )
    }
}

/** "AI · OpenRouter · google/gemini-2.5-flash", or why it's off. */
internal fun aiLine(s: AppSettings): String = when {
    !s.aiTitles -> "Titles and sound searches · off"
    s.aiReady -> "${s.aiProvider.label} · ${s.aiModel}"
    else -> "Titles and sound searches · not set up"
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = Space.Medium).testTag("section-$text"))
}

/**
 * A server or service as a card that folds (issue #665): its icon, name and one line about it, with an arrow that
 * turns as it opens. Folded, only the header shows.
 */
@Composable
internal fun FoldCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    tag: String,
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "fold")
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = if (expanded) "Fold" else "Open", onClick = onToggle)
                    .padding(start = Space.Page, end = Space.Small, top = Space.Medium, bottom = Space.Medium)
                    .testTag("$tag-header"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = if (accent) Accent else MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = Space.Page)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (accent) Accent else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.ExpandMore, if (expanded) "Fold $title" else "Open $title", Modifier.rotate(turn), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(start = Space.Page, end = Space.Page, bottom = Space.Page).testTag("$tag-body"),
                    verticalArrangement = Arrangement.spacedBy(Space.Medium),
                    content = content,
                )
            }
        }
    }
}

/**
 * Runs a Test with what's typed, before it's saved, and shows "✓ …" or "✗ …". Each Test and its result is logged
 * (which card, not what's typed), so a report shows which one didn't work and why (the TV app, 0.52.0-dev).
 */
private fun test(graph: AppGraph, draft: AppSettings, scope: kotlinx.coroutines.CoroutineScope, set: (String) -> Unit, what: String = "connection", block: suspend (AppSettings) -> String) {
    set("Testing…")
    AppLog.i("Settings", "Testing $what")
    scope.launch {
        val result = try {
            "✓ " + block(graph.settings.clean(draft))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "✗ " + (e.message ?: e.javaClass.simpleName)
        }
        AppLog.i("Settings", "Test of $what: ${AppLog.redact(result)}")
        set(result)
    }
}

/**
 * The music server in use: its address and login, a proxy login when it's behind one, its name in Siansa and in
 * AudioMuse-AI, and its library sync.
 */
@Composable
private fun ServerFields(
    graph: AppGraph,
    draft: AppSettings,
    onChange: (AppSettings) -> Unit,
    serverName: String,
    onServerName: (String) -> Unit,
    nav: NavController,
    // Another login's server (issue #687): no Library sync row, which is for the login in use.
    inUse: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    var showProxy by remember { mutableStateOf(draft.proxyUser.isNotBlank()) }
    val kind = draft.sourceKind
    Hint(kind.detail)
    Field("Server address", draft.navidromeUrl, if (kind.embyLike) "192.168.1.10:8096" else "192.168.1.10:4533") { onChange(draft.copy(navidromeUrl = it)) }
    Field("Username", draft.navidromeUser, keyboard = KeyboardType.Text) { onChange(draft.copy(navidromeUser = it)) }
    SecretField("Password", draft.navidromePassword) { onChange(draft.copy(navidromePassword = it)) }
    if (!kind.embyLike) ApiKeyField(draft, onChange)
    if (showProxy) {
        Hint("Only for a reverse proxy that asks for its own login before your server.")
        Field("Proxy username", draft.proxyUser, keyboard = KeyboardType.Text) { onChange(draft.copy(proxyUser = it)) }
        SecretField("Proxy password", draft.proxyPassword) { onChange(draft.copy(proxyPassword = it)) }
    } else {
        Box(Modifier.tvFocusGroup()) { TextButton(onClick = { showProxy = true }, modifier = Modifier.focusRing()) { Text("Server behind a login page? Add a proxy login") } }
    }
    TestRow(result) {
        test(graph, draft, scope, { result = it }, "the music server") { s -> testServer(graph, s, kind) }
    }
    Field("Name shown in Siansa", serverName, "Like Home or Kids", keyboard = KeyboardType.Text) { onServerName(it.replace("\n", " ").take(UserFiles.SERVER_NAME_MAX)) }
    Hint("Shown instead of the address, so two logins can be told apart. Leave empty to show the address.")
    Field("Server name in AudioMuse", draft.audioMuseServer, keyboard = KeyboardType.Text) { onChange(draft.copy(audioMuseServer = it)) }
    Hint(
        if (kind.embyLike) "AudioMuse reads your ${ServerWords.name(kind)} library for everything smart. If AudioMuse has more than one music server, enter this ${ServerWords.name(kind)} server's name as it is in AudioMuse."
        else "Only if AudioMuse has more than one music server: this server's name as it is in AudioMuse. Leave empty for its main one.",
    )
    if (!inUse) return
    val progress by graph.sync.progress.collectAsStateWithLifecycle()
    val library by graph.library.status.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth().clickable { nav.navigate("sync") }.padding(vertical = Space.Small).testTag("librarySync"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Sync, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = Space.Page)) {
            Text("Library sync", style = MaterialTheme.typography.bodyLarge)
            Text(syncSummary(progress, library), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The OpenSubsonic API key (issue #493), with what it's for. */
@Composable
internal fun ApiKeyField(draft: AppSettings, onChange: (AppSettings) -> Unit) {
    Hint("API key, if your server gives you one, instead of a username and password.")
    Box(Modifier.testTag("apiKey")) { SecretField("API key", draft.navidromeApiKey) { onChange(draft.copy(navidromeApiKey = it)) } }
}

/**
 * The music server's Test with what's typed: whether it answers, and with an API key (issue #493) whether the server
 * takes keys and whose this one is.
 */
internal suspend fun testServer(graph: AppGraph, s: AppSettings, kind: SourceKind): String {
    if (kind.embyLike) return "Connected to " + EmbyApi(graph.http, { s }, jellyfin = kind == SourceKind.JELLYFIN).ping()
    val login = s.copy(sourceKind = kind)
    val api = SubsonicApi(graph.http) { login }
    val connected = "Connected to " + api.ping()
    if (!login.hasApiKey) return connected
    // With a password too, the ping logged in with it: say plainly that the key isn't used.
    if (!api.takesApiKey()) throw ApiException(SubsonicApi.NO_API_KEYS, 42)
    val who = try {
        api.keyUser()
    } catch (e: ApiException) {
        null
    }
    AppLog.i("Settings", "Server test: the API key works" + (who?.let { " (user $it)" } ?: ""))
    return "$connected with the API key" + (who?.let { " as $it" } ?: "")
}

/**
 * A server login checked before it's saved (issue #493). With an API key that's new or changed since [saved]:
 * refused with a plain message when the server doesn't take keys (42) or doesn't know this one (44), and an empty
 * username gets the key's user. Unchanged without a key, or when the server can't be reached or answers otherwise
 * (saving doesn't need the server).
 */
internal suspend fun keyChecked(graph: AppGraph, s: AppSettings, kind: SourceKind, saved: AppSettings?): AppSettings {
    val login = graph.settings.clean(s).copy(sourceKind = kind)
    if (!login.hasApiKey) return s
    val same = saved != null && saved.navidromeApiKey == login.navidromeApiKey && saved.navidromeUrl == login.navidromeUrl
    if (same && login.navidromeUser.isNotBlank()) return s
    val api = SubsonicApi(graph.http) { login }
    try {
        api.ping()
        if (!api.takesApiKey()) throw ApiException(SubsonicApi.NO_API_KEYS, 42)
    } catch (e: ApiException) {
        if (e.code == 42 || e.code == 44) throw e
        AppLog.w("Settings", "API key not checked: ${e.message}")
        return s
    } catch (e: java.io.IOException) {
        AppLog.w("Settings", "API key not checked, the server can't be reached: ${e.message}")
        return s
    }
    if (login.navidromeUser.isNotBlank()) return s
    val who = try {
        api.keyUser()
    } catch (e: ApiException) {
        null
    } catch (e: java.io.IOException) {
        null
    }
    AppLog.i("Settings", "API key checked" + (who?.let { "; its user is $it" } ?: ""))
    return if (who != null) s.copy(navidromeUser = who) else s
}

/** Audiobookshelf: its address and login, Test, and with an admin login a scan for new books. */
@Composable
private fun AudiobookshelfFields(graph: AppGraph, draft: AppSettings, onChange: (AppSettings) -> Unit) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    var scan by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    val saved by graph.settings.state.collectAsStateWithLifecycle()
    Hint("Audiobooks and podcasts from your Audiobookshelf server, next to your music. One login for the whole app; leave the address empty for no books.")
    Field("Server address", draft.audiobookshelfUrl, "192.168.1.10:13378") { onChange(draft.copy(audiobookshelfUrl = it)) }
    Hint("Use either an API token (Audiobookshelf › your user › API token) or the username and password.")
    SecretField("API token", draft.audiobookshelfToken) { onChange(draft.copy(audiobookshelfToken = it)) }
    Field("Username", draft.audiobookshelfUser, keyboard = KeyboardType.Text) { onChange(draft.copy(audiobookshelfUser = it)) }
    SecretField("Password", draft.audiobookshelfPassword) { onChange(draft.copy(audiobookshelfPassword = it)) }
    TestRow(result) {
        test(graph, draft, scope, { result = it }, "Audiobookshelf") { s ->
            if (!s.booksReady) throw ApiException("Enter the server address and a token, or a username and password")
            val api = AudiobookshelfApi(graph.http) { s }
            val who = api.ping()
            val libraries = api.libraries()
            val books = libraries.count { !it.podcasts }
            val podcasts = libraries.size - books
            "Connected to $who · " + listOfNotNull(
                "$books book ${if (books == 1) "library" else "libraries"}".takeIf { books > 0 },
                "$podcasts podcast ${if (podcasts == 1) "library" else "libraries"}".takeIf { podcasts > 0 },
            ).joinToString(", ").ifEmpty { "no libraries this user can see" }
        }
    }
    // A scan with the login that's saved, as it's the server's own job.
    if (saved.booksReady) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !scanning) {
                scanning = true
                scan = "Asking Audiobookshelf to scan…"
                scope.launch {
                    scan = try {
                        if (!graph.audiobookshelf.isAdmin()) {
                            "Only an admin login can ask for a scan. Scan from Audiobookshelf's own page, or log Siansa in as an admin."
                        } else {
                            val n = graph.audiobookshelf.scanLibraries()
                            graph.books.forget()
                            graph.books.refreshShelves(podcasts = false, force = true)
                            graph.books.refreshShelves(podcasts = true, force = true)
                            AppLog.i("Books", "Scan asked for $n libraries; books read again")
                            "Scanning $n ${if (n == 1) "library" else "libraries"} on the server. New books show once it's done."
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLog.w("Books", "Scan not asked: ${e.message}")
                        "Couldn't ask for a scan: ${e.message}"
                    }
                    scanning = false
                }
            }.padding(vertical = Space.Small).testTag("absScan"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Sync, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = Space.Page)) {
                Text("Scan for new books", style = MaterialTheme.typography.bodyLarge)
                Text(scan ?: "Asks Audiobookshelf to look for new and changed books (an admin login only).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** AudioMuse-AI: its address and login, and Test, which also checks AudioMuse knows this server's songs. */
@Composable
internal fun AudioMuseFields(graph: AppGraph, draft: AppSettings, onChange: (AppSettings) -> Unit) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    Hint("The sound analysis behind mixes, radios, similar music and sound search. One for every server; leave the address empty for none.")
    Field("Server address", draft.audioMuseUrl, "192.168.1.10:8000") { onChange(draft.copy(audioMuseUrl = it)) }
    Hint("If AudioMuse has a login, use either an API token or its username and password.")
    SecretField("API token", draft.audioMuseToken) { onChange(draft.copy(audioMuseToken = it)) }
    Field("Username", draft.audioMuseUser, keyboard = KeyboardType.Text) { onChange(draft.copy(audioMuseUser = it)) }
    SecretField("Password", draft.audioMusePassword) { onChange(draft.copy(audioMusePassword = it)) }
    Hint("With several music servers in AudioMuse, each server's own name there is under Servers.")
    TestRow(result) { test(graph, draft, scope, { result = it }, "AudioMuse") { s -> testAudioMuse(graph, s) } }
}

/** Whether Siansa Beats' card shows: in every build now ([app.siansa.data.Offered]). */
internal val BEATS_OFFERED: Boolean = app.siansa.data.Offered.beats

internal fun beatsLine(ready: Boolean): String = if (ready) "Beats, loudness and genres · set up" else "Beats, loudness and genres · not set up"

/**
 * Siansa Beats (Siansa Dev, the owner, 10 Nov): its address and API key, and Test. Once set, the app asks it for each
 * song's loudness, fades, key and beat grid and for the genres, instead of the plugin on AudioMuse.
 */
@Composable
internal fun BeatsFields(graph: AppGraph, draft: AppSettings, onChange: (AppSettings) -> Unit) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    Hint("Measures each song's beats, loudness and key for DJ blends and smooth fades, and sorts songs into genres. Leave the address empty to use the Siansa plugin on AudioMuse-AI instead, or the songs' tags.")
    Field("Server address", draft.beatsUrl, "192.168.1.10:8090") { onChange(draft.copy(beatsUrl = it)) }
    SecretField("API key or password", draft.beatsKey) { onChange(draft.copy(beatsKey = it)) }
    Hint("Only if Siansa Beats asks for one (BEATS_API_KEY or BEATS_PASSWORD).")
    TestRow(result) { test(graph, draft, scope, { result = it }, "Siansa Beats") { s -> testBeats(graph, s) } }
}

/** Siansa Beats' Test: what it can do, said as the DJ check says it. */
internal suspend fun testBeats(graph: AppGraph, s: AppSettings): String {
    if (!s.beatsReady) throw ApiException("Enter Siansa Beats' address first")
    val caps = BeatsApi(graph.http) { s }.capabilities()
    val result = ProfileStore.beatsResult(caps, s.sourceKind)
    if (!result.ok) throw ApiException(result.message)
    return result.message
}

/** AudioMuse's Test: a sound search, and whether its songs are this server's (only known once it's synced). */
internal suspend fun testAudioMuse(graph: AppGraph, s: AppSettings): String {
    val hits = AudioMuseApi(graph.http) { s }.textSearch("calm piano", 10)
    val now = graph.settings.current
    val otherServer = s.sourceKind != now.sourceKind || s.navidromeUrl != now.navidromeUrl || s.navidromeUser != now.navidromeUser
    val match = graph.generator.libraryMatch(hits)
    AppLog.i("Settings", "AudioMuse test: $match, other server=$otherServer")
    val (ok, words) = audioMuseCheck(match, otherServer)
    if (!ok) throw ApiException(words)
    return words
}

/**
 * The AI service that writes mix titles and sound searches (issue #665): which one, its address when it's on your
 * network or another service, the key, the model, the switch, and Test. Picking a provider fills in its usual model.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiFields(graph: AppGraph, draft: AppSettings, onChange: (AppSettings) -> Unit) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    Hint("Optional: writes the mix titles and sound searches. Only the vibe's guidance, the time and your last 20 plays are sent.")
    FlowRow(Modifier.tvFocusGroup(), horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
        AiProvider.entries.forEach { p ->
            FilterChip(
                selected = draft.aiProvider == p,
                onClick = {
                    if (p != draft.aiProvider) {
                        // One service's model means nothing to another: its usual one instead.
                        onChange(draft.copy(aiProvider = p, aiModel = aiModelFor(p, draft.aiModel)))
                        result = null
                    }
                },
                label = { Text(p.label) },
                modifier = Modifier.testTag("ai-${p.name}"),
            )
        }
    }
    Hint(draft.aiProvider.hint)
    if (draft.aiProvider.ownAddress) {
        Field("Address", draft.aiAddress, if (draft.aiProvider == AiProvider.OLLAMA) "192.168.1.10:11434" else "192.168.1.10:1234/v1") { onChange(draft.copy(aiAddress = it)) }
    }
    SecretField(if (draft.aiProvider.needsKey) "API key" else "API key (if it asks for one)", draft.aiKey) { onChange(draft.copy(aiKey = it)) }
    Field("Model", draft.aiModel, draft.aiProvider.defaultModel.ifEmpty { null }, keyboard = KeyboardType.Text) { onChange(draft.copy(aiModel = it)) }
    SwitchRow("AI titles and sound searches", draft.aiTitles) { onChange(draft.copy(aiTitles = it)) }
    TestRow(result) {
        test(graph, draft, scope, { result = it }, "the AI service") { s ->
            val answer = ChatApi(graph.http) { s }.complete("Reply with one word.", "Say hello.")
            "${s.aiProvider.label} answered: ${answer.trim().take(40)}"
        }
    }
}

/** The model when switching to [provider]: its usual one, or what's typed when it has none (another service). */
internal fun aiModelFor(provider: AiProvider, typed: String): String = provider.defaultModel.ifEmpty { typed }

/** The Audiobookshelf card's line: the kind of server, and whether it's set up. */
internal fun absLine(ready: Boolean): String = if (ready) "Audiobooks and podcasts · set up" else "Audiobooks and podcasts · not set up"

/** The new server's type (issue #828): the servers Siansa can play from, as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ServerKindChips(kind: SourceKind, onKind: (SourceKind) -> Unit) {
    Text("Server type", style = MaterialTheme.typography.bodyLarge)
    FlowRow(Modifier.tvFocusGroup().testTag("serverKinds"), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        SourceKind.entries.filter { it.ready }.forEach { k ->
            FilterChip(selected = kind == k, onClick = { onKind(k) }, label = { Text(k.label) }, modifier = Modifier.testTag("serverKind-${k.name}"))
        }
    }
}

/** Save or cancel the server being added (issue #828): switching to it is a separate step, once it's saved. */
@Composable
internal fun NewServerButtons(onSave: () -> Unit, onCancel: () -> Unit) {
    Hint("Saved, it shows above as another login. Switch to it there when you want to use it (Siansa restarts).")
    Row(Modifier.tvFocusGroup(), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        Button(onClick = onSave, modifier = Modifier.focusRing().testTag("saveNewServer")) { Text("Save") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.focusRing()) { Text("Cancel") }
    }
}
