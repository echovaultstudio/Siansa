package app.siansa.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import app.siansa.data.AiProvider
import app.siansa.data.AppSettings
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
    fun toggle(key: String) { opened = if (key in opened) opened - key else opened + key }

    fun save() {
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
                        Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) { Text("Save") }
                    } else {
                        Hint("Another login, with its own library, history and settings. Switch to it to use it or change its login; Siansa restarts.")
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                            Button(onClick = { Users.switchTo(context, user.id) }) { Text("Switch to it") }
                            OutlinedButton(onClick = { removing = user }) { Text("Remove") }
                        }
                    }
                }
            }
            FoldCard(
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                title = "Audiobookshelf",
                subtitle = absLine(draft.booksReady),
                expanded = "books" in opened,
                onToggle = { toggle("books") },
                tag = "fold-books",
            ) {
                AudiobookshelfFields(graph, draft) { draft = it; problem = null }
                Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            }
            Row(
                Modifier.fillMaxWidth().clickable { Users.addAndSwitch(context) }.padding(vertical = Space.Medium).testTag("addServer"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = Space.Page)) {
                    Text("Add a music server", style = MaterialTheme.typography.bodyLarge)
                    Hint("Another login on a server, or another server, with its own library. Siansa restarts and asks for it. To show several together, use the filter button on Home.")
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
                Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) { Text("Save") }
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
                Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) { Text("Save") }
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
                TextButton(onClick = {
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

/** Runs a Test with what's typed, before it's saved, and shows "✓ …" or "✗ …". */
private fun test(graph: AppGraph, draft: AppSettings, scope: kotlinx.coroutines.CoroutineScope, set: (String) -> Unit, block: suspend (AppSettings) -> String) {
    set("Testing…")
    scope.launch {
        set(
            try {
                "✓ " + block(graph.settings.clean(draft))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "✗ " + (e.message ?: e.javaClass.simpleName)
            },
        )
    }
}

/**
 * The music server in use: its address and login, a proxy login when it's behind one, its name in Siansa and in
 * AudioMuse-AI, and its library sync.
 */
@Composable
private fun ServerFields(graph: AppGraph, draft: AppSettings, onChange: (AppSettings) -> Unit, serverName: String, onServerName: (String) -> Unit, nav: NavController) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    var showProxy by remember { mutableStateOf(draft.proxyUser.isNotBlank()) }
    val kind = draft.sourceKind
    Hint(kind.detail)
    Field("Server address", draft.navidromeUrl, if (kind == SourceKind.EMBY) "192.168.1.10:8096" else "192.168.1.10:4533") { onChange(draft.copy(navidromeUrl = it)) }
    Field("Username", draft.navidromeUser, keyboard = KeyboardType.Text) { onChange(draft.copy(navidromeUser = it)) }
    SecretField("Password", draft.navidromePassword) { onChange(draft.copy(navidromePassword = it)) }
    if (showProxy) {
        Hint("Only for a reverse proxy that asks for its own login before your server.")
        Field("Proxy username", draft.proxyUser, keyboard = KeyboardType.Text) { onChange(draft.copy(proxyUser = it)) }
        SecretField("Proxy password", draft.proxyPassword) { onChange(draft.copy(proxyPassword = it)) }
    } else {
        TextButton(onClick = { showProxy = true }) { Text("Server behind a login page? Add a proxy login") }
    }
    TestRow(result) {
        test(graph, draft, scope, { result = it }) { s ->
            val server = if (kind == SourceKind.EMBY) EmbyApi(graph.http, { s }) else SubsonicApi(graph.http) { s }
            "Connected to " + server.ping()
        }
    }
    Field("Name shown in Siansa", serverName, "Like Home or Kids", keyboard = KeyboardType.Text) { onServerName(it.replace("\n", " ").take(UserFiles.SERVER_NAME_MAX)) }
    Hint("Shown instead of the address, so two logins can be told apart. Leave empty to show the address.")
    Field("Server name in AudioMuse", draft.audioMuseServer, keyboard = KeyboardType.Text) { onChange(draft.copy(audioMuseServer = it)) }
    Hint(
        if (kind == SourceKind.EMBY) "AudioMuse reads your Emby library for everything smart. If AudioMuse has more than one music server, enter this Emby server's name as it is in AudioMuse."
        else "Only if AudioMuse has more than one music server: this server's name as it is in AudioMuse. Leave empty for its main one.",
    )
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
        test(graph, draft, scope, { result = it }) { s ->
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
    TestRow(result) { test(graph, draft, scope, { result = it }) { s -> testAudioMuse(graph, s) } }
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
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
        test(graph, draft, scope, { result = it }) { s ->
            val answer = ChatApi(graph.http) { s }.complete("Reply with one word.", "Say hello.")
            "${s.aiProvider.label} answered: ${answer.trim().take(40)}"
        }
    }
}

/** The model when switching to [provider]: its usual one, or what's typed when it has none (another service). */
internal fun aiModelFor(provider: AiProvider, typed: String): String = provider.defaultModel.ifEmpty { typed }

/** The Audiobookshelf card's line: the kind of server, and whether it's set up. */
internal fun absLine(ready: Boolean): String = if (ready) "Audiobooks and podcasts · set up" else "Audiobooks and podcasts · not set up"
