package app.siansa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.data.AppSettings
import app.siansa.data.ReplayGainMode
import app.siansa.data.Users
import app.siansa.log.AppLog
import app.siansa.mix.audioMuseCheck
import app.siansa.net.ApiException
import app.siansa.net.AudioMuseApi
import app.siansa.net.AudiobookshelfApi
import app.siansa.net.SubsonicApi
import app.siansa.net.EmbyApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import app.siansa.net.SourceKind

@Composable
fun ConnectionsScreen(nav: NavController, sourceName: String? = null, extras: Boolean = true) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val firstRun = !graph.settings.current.navidromeReady
    // Picked on the first-run picker (issue #113), else the one in use.
    val kind = sourceName?.let { SourceKind.parse(it) } ?: graph.settings.current.sourceKind
    // From the picker there's a way back to it; a plain first run (an older flow) has none.
    val fromPicker = sourceName != null
    var draft by remember { mutableStateOf(graph.settings.current) }
    var showProxy by remember { mutableStateOf(graph.settings.current.proxyUser.isNotBlank()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var navidromeTest by remember { mutableStateOf<String?>(null) }
    var audioMuseTest by remember { mutableStateOf<String?>(null) }

    // Tests use what's typed, before it's saved.
    fun runTest(set: (String) -> Unit, block: suspend (AppSettings) -> String) {
        set("Testing…")
        scope.launch {
            set(
                try {
                    "✓ " + block(graph.settings.clean(draft))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "✗ " + (e.message ?: e.javaClass.simpleName)
                }
            )
        }
    }

    fun save() {
        try {
            // Only the connection fields come from this page; everything else keeps its current value.
            val d = draft
            graph.settings.save(graph.settings.current.withConnectionsFrom(d, kind))
            if (d.booksReady) AppLog.i("Settings", "Audiobookshelf saved: ${AppLog.redact(d.audiobookshelfUrl)}")
        } catch (e: Exception) {
            problem = e.message
            return
        }
        problem = null
        val ready = graph.settings.current.navidromeReady
        when {
            firstRun && ready -> {
                // First setup: start the library sync and show its progress, with Home underneath.
                graph.sync.start()
                nav.navigate("sync") { popUpTo("home") }
            }
            !firstRun -> nav.popBackStack()
        }
    }

    Screen(
        title = if (firstRun) "Connect your servers" else "Servers and services",
        nav = if (firstRun && !fromPicker) null else nav,
        actions = { IconButton(onClick = { save() }) { Icon(Icons.Rounded.Check, "Save") } },
    ) { padding ->
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .aboveKeyboard()
                .verticalScroll(rememberScrollState())
                .padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            if (firstRun) {
                Text(
                    if (fromPicker) "Enter your server's address and login. Test checks them before you save."
                    else "Welcome! Connect your server to get started. AudioMuse and an AI service can be added now or later.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                // A user being added: a way back to the users already set up.
                val context = LocalContext.current
                val others = remember { Users.all(context).filter { it.id != Users.active && it.connected } }
                others.forEach { user ->
                    TextButton(onClick = { Users.switchTo(context, user.id) }) { Text("Switch back to ${user.label}") }
                }
            }

            Heading("${kind.label} server")
            Hint(kind.detail)
            Field("Server address", draft.navidromeUrl, if (kind.embyLike) "192.168.1.10:8096" else "192.168.1.10:4533") { draft = draft.copy(navidromeUrl = it) }
            Field("Username", draft.navidromeUser, keyboard = KeyboardType.Text) { draft = draft.copy(navidromeUser = it) }
            SecretField("Password", draft.navidromePassword) { draft = draft.copy(navidromePassword = it) }
            if (showProxy) {
                Hint("Only for a reverse proxy that asks for its own login before your server.")
                Field("Proxy username", draft.proxyUser, keyboard = KeyboardType.Text) { draft = draft.copy(proxyUser = it) }
                SecretField("Proxy password", draft.proxyPassword) { draft = draft.copy(proxyPassword = it) }
            } else {
                TextButton(onClick = { showProxy = true }) { Text("Server behind a login page? Add a proxy login") }
            }
            TestRow(navidromeTest) {
                runTest({ navidromeTest = it }) { s ->
                    val server = if (kind.embyLike) EmbyApi(graph.http, { s }, jellyfin = kind == SourceKind.JELLYFIN) else SubsonicApi(graph.http) { s }
                    "Connected to " + server.ping()
                }
            }
            // Smart extras turned down on the first run (issue #113): only the server. They're here later in Settings.
            if (extras || !firstRun) {
                HorizontalDivider()
                Heading("AudioMuse-AI")
                Field("Server address", draft.audioMuseUrl, "192.168.1.10:8000") { draft = draft.copy(audioMuseUrl = it) }
                Hint("Leave the address empty if this user has no AudioMuse.")
                Hint("If AudioMuse has a login, use either an API token or its username and password.")
                SecretField("API token", draft.audioMuseToken) { draft = draft.copy(audioMuseToken = it) }
                Field("Username", draft.audioMuseUser, keyboard = KeyboardType.Text) { draft = draft.copy(audioMuseUser = it) }
                SecretField("Password", draft.audioMusePassword) { draft = draft.copy(audioMusePassword = it) }
                Field("Server name in AudioMuse", draft.audioMuseServer, keyboard = KeyboardType.Text) { draft = draft.copy(audioMuseServer = it) }
                Hint(
                    if (kind.embyLike) "AudioMuse reads your ${ServerWords.name(kind)} library for everything smart. If AudioMuse has more than one music server, enter this ${ServerWords.name(kind)} server's name as it is in AudioMuse. Each user keeps their own."
                    else "Only if AudioMuse has more than one music server: this server's name as it is in AudioMuse. Leave empty for its main one. Each user keeps their own."
                )
                TestRow(audioMuseTest) { runTest({ audioMuseTest = it }) { s -> testAudioMuse(graph, s) } }

                HorizontalDivider()
                Heading("AI (optional)")
                AiFields(graph, draft) { draft = it }
            }

            problem?.let { ErrorText(it, Modifier) }
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}

/**
 * Which settings sections were folded (issue #780): sections start open, and one folded stays folded on later
 * visits, by its tag.
 */
internal object FoldMemory {
    private const val PREFS = "settings_folds"
    private const val KEY = "folded"

    fun isFolded(context: android.content.Context, tag: String): Boolean =
        runCatching { tag in prefs(context).getStringSet(KEY, null).orEmpty() }.getOrDefault(false)

    fun setFolded(context: android.content.Context, tag: String, folded: Boolean) {
        runCatching {
            val now = prefs(context).getStringSet(KEY, null).orEmpty()
            prefs(context).edit().putStringSet(KEY, foldedAfter(now, tag, folded)).apply()
        }
    }

    /** [folded] with [tag] added or taken out. */
    fun foldedAfter(folded: Set<String>, tag: String, fold: Boolean): Set<String> = if (fold) folded + tag else folded - tag

    private fun prefs(context: android.content.Context) = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
}

@Composable
internal fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

/**
 * A settings page's section that folds (issue #776): its heading and a line of what's set, with the arrow
 * Connections uses, turned when open. Open by default, so every setting shows (issue #780); fold one to keep the
 * page short, and it stays folded the next time ([FoldMemory]). The body is only built when open. [summary] says
 * the setting's state while folded ("On · Rock", "6 s"). [startOpen] overrides the remembered state (pictures and
 * tests).
 */
@Composable
internal fun FoldSection(
    title: String,
    summary: String? = null,
    tag: String = title.lowercase().replace(Regex("[^a-z0-9]+"), "-"),
    startOpen: Boolean? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var open by androidx.compose.runtime.saveable.rememberSaveable(tag) {
        mutableStateOf(startOpen ?: !FoldMemory.isFolded(context, tag))
    }
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "fold")
    Column(Modifier.fillMaxWidth().testTag("section-$tag")) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClickLabel = if (open) "Fold" else "Open") {
                    open = !open
                    FoldMemory.setFolded(context, tag, !open)
                }
                .padding(vertical = Space.Small)
                .testTag("section-$tag-header"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Heading(title)
                if (!open && !summary.isNullOrBlank()) Hint(summary)
            }
            Icon(
                androidx.compose.material.icons.Icons.Rounded.ExpandMore,
                if (open) "Fold $title" else "Open $title",
                Modifier.rotate(turn),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.animation.AnimatedVisibility(open) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = Space.Small).testTag("section-$tag-body"),
                verticalArrangement = Arrangement.spacedBy(Space.Medium),
                content = content,
            )
        }
    }
}

@Composable
internal fun Field(
    label: String,
    value: String,
    placeholder: String? = null,
    keyboard: KeyboardType = KeyboardType.Uri,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (placeholder != null) Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show or hide")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun TestRow(result: String?, onTest: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
        OutlinedButton(onClick = onTest) { Text("Test") }
        if (result != null) {
            Text(
                result,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (result.startsWith("✗")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
