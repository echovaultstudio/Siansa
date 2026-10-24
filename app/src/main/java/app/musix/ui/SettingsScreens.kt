package app.musix.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.musix.data.AppSettings
import app.musix.data.ReplayGainMode
import app.musix.data.Users
import app.musix.net.AudioMuseApi
import app.musix.net.OpenRouterApi
import app.musix.net.SubsonicApi
import app.musix.net.EmbyApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import app.musix.net.SourceKind

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
    var openRouterTest by remember { mutableStateOf<String?>(null) }

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
            graph.settings.save(
                graph.settings.current.copy(
                    sourceKind = kind,
                    navidromeUrl = d.navidromeUrl, navidromeUser = d.navidromeUser, navidromePassword = d.navidromePassword,
                    proxyUser = d.proxyUser, proxyPassword = d.proxyPassword,
                    audioMuseUrl = d.audioMuseUrl, audioMuseToken = d.audioMuseToken,
                    audioMuseUser = d.audioMuseUser, audioMusePassword = d.audioMusePassword, audioMuseServer = d.audioMuseServer,
                    openRouterKey = d.openRouterKey, openRouterModel = d.openRouterModel, aiTitles = d.aiTitles,
                )
            )
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
        title = if (firstRun) "Connect your servers" else "Connections",
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
                    else "Welcome! Connect your server to get started. AudioMuse and OpenRouter can be added now or later.",
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
            Field("Server address", draft.navidromeUrl, if (kind == SourceKind.EMBY) "192.168.1.10:8096" else "192.168.1.10:4533") { draft = draft.copy(navidromeUrl = it) }
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
                    val server = if (kind == SourceKind.EMBY) EmbyApi(graph.http, { s }) else SubsonicApi(graph.http) { s }
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
                    if (kind == SourceKind.EMBY) "AudioMuse reads your Emby library for everything smart. If AudioMuse has more than one music server, enter this Emby server's name as it is in AudioMuse."
                    else "Only if AudioMuse has more than one music server: this server's name as it is in AudioMuse. Leave empty for its main one."
                )
                TestRow(audioMuseTest) {
                    runTest({ audioMuseTest = it }) { s ->
                        val hits = AudioMuseApi(graph.http) { s }.textSearch("calm piano", 3)
                        "Text search works (${hits.size} matches for \"calm piano\")"
                    }
                }

                HorizontalDivider()
                Heading("OpenRouter (optional)")
                Hint("Writes the mix titles and sound searches. Only the vibe's guidance, the time and your last 20 plays are sent.")
                SecretField("API key", draft.openRouterKey) { draft = draft.copy(openRouterKey = it) }
                Field("Model", draft.openRouterModel, AppSettings.DEFAULT_MODEL, keyboard = KeyboardType.Text) {
                    draft = draft.copy(openRouterModel = it)
                }
                SwitchRow("AI titles and sound searches", draft.aiTitles) { draft = draft.copy(aiTitles = it) }
                TestRow(openRouterTest) {
                    runTest({ openRouterTest = it }) { s ->
                        val answer = OpenRouterApi(graph.http) { s }.complete("Reply with one word.", "Say hello.")
                        "Model answered: ${answer.trim().take(40)}"
                    }
                }
            }

            problem?.let { ErrorText(it, Modifier) }
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}

@Composable
internal fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Field(
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
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
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
private fun TestRow(result: String?, onTest: () -> Unit) {
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
