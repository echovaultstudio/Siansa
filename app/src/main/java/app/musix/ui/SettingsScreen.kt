package app.musix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.musix.data.AppSettings
import app.musix.data.ReplayGainMode
import app.musix.data.normalizeUrl
import app.musix.net.AudioMuseApi
import app.musix.net.OpenRouterApi
import app.musix.net.SubsonicApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val firstRun = !graph.settings.current.navidromeReady
    var draft by remember { mutableStateOf(graph.settings.current) }
    var navidromeTest by remember { mutableStateOf<String?>(null) }
    var audioMuseTest by remember { mutableStateOf<String?>(null) }
    var openRouterTest by remember { mutableStateOf<String?>(null) }

    // Tests use what's typed, before it's saved.
    fun tested(): AppSettings = draft.copy(
        navidromeUrl = normalizeUrl(draft.navidromeUrl),
        audioMuseUrl = normalizeUrl(draft.audioMuseUrl),
        navidromeUser = draft.navidromeUser.trim(),
        audioMuseToken = draft.audioMuseToken.trim(),
        openRouterKey = draft.openRouterKey.trim(),
        openRouterModel = draft.openRouterModel.trim(),
    )

    fun runTest(set: (String) -> Unit, block: suspend (AppSettings) -> String) {
        set("Testing…")
        scope.launch {
            set(
                try {
                    "✓ " + block(tested())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "✗ " + (e.message ?: e.javaClass.simpleName)
                }
            )
        }
    }

    fun save() {
        graph.settings.save(draft)
        if (firstRun && graph.settings.current.navidromeReady) {
            nav.navigate("home") { popUpTo("settings") { inclusive = true } }
        } else {
            nav.popBackStack()
        }
    }

    Screen(
        title = "Settings",
        nav = if (firstRun) null else nav,
        actions = { IconButton(onClick = { save() }) { Icon(Icons.Rounded.Check, "Save") } },
    ) { padding ->
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (firstRun) {
                Text(
                    "Welcome! Connect your Navidrome server to get started. AudioMuse and OpenRouter can be added now or later.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Heading("Navidrome")
            Field("Server address", draft.navidromeUrl, "http://192.168.1.10:4533") { draft = draft.copy(navidromeUrl = it) }
            Field("Username", draft.navidromeUser) { draft = draft.copy(navidromeUser = it) }
            SecretField("Password", draft.navidromePassword) { draft = draft.copy(navidromePassword = it) }
            TestRow(navidromeTest) {
                runTest({ navidromeTest = it }) { s -> "Connected to " + SubsonicApi(graph.http) { s }.ping() }
            }

            HorizontalDivider()
            Heading("AudioMuse-AI")
            Field("Server address", draft.audioMuseUrl, "http://192.168.1.10:8000") { draft = draft.copy(audioMuseUrl = it) }
            SecretField("API token (if authentication is on)", draft.audioMuseToken) { draft = draft.copy(audioMuseToken = it) }
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
            Field("Model", draft.openRouterModel, AppSettings.DEFAULT_MODEL) { draft = draft.copy(openRouterModel = it) }
            SwitchRow("AI titles and sound searches", draft.aiTitles) { draft = draft.copy(aiTitles = it) }
            TestRow(openRouterTest) {
                runTest({ openRouterTest = it }) { s ->
                    val answer = OpenRouterApi(graph.http) { s }.complete("Reply with one word.", "Say hello.")
                    "Model answered: ${answer.trim().take(40)}"
                }
            }

            HorizontalDivider()
            Heading("Playback")
            Text("ReplayGain", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReplayGainMode.entries.forEach { mode ->
                    FilterChip(
                        selected = draft.replayGain == mode,
                        onClick = { draft = draft.copy(replayGain = mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
            Text("Pre-amp: ${"%+.0f".format(draft.preampDb)} dB", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = draft.preampDb,
                onValueChange = { draft = draft.copy(preampDb = it.roundToInt().toFloat()) },
                valueRange = -12f..6f,
                steps = 17,
            )
            Hint("Tracks can only be turned down, so raise the pre-amp only if quiet tracks still sound too quiet.")

            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Field(label: String, value: String, placeholder: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (placeholder != null) Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
