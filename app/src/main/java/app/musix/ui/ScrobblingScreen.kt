package app.musix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.musix.log.AppLog
import app.musix.net.ListenBrainzApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Where listens go: Navidrome, Last.fm and a ListenBrainz-compatible server such as Koito. */
@Composable
fun ScrobblingScreen(nav: NavController) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val settings by graph.settings.state.collectAsStateWithLifecycle()

    var lfmKey by remember { mutableStateOf(settings.lastFmApiKey) }
    var lfmSecret by remember { mutableStateOf(settings.lastFmSecret) }
    var lfmUser by remember { mutableStateOf(settings.lastFmUser) }
    var lfmPassword by remember { mutableStateOf("") }
    var lfmStatus by remember { mutableStateOf<String?>(null) }

    var lbUrl by remember { mutableStateOf(settings.listenBrainzUrl) }
    var lbToken by remember { mutableStateOf(settings.listenBrainzToken) }
    var lbStatus by remember { mutableStateOf<String?>(null) }

    fun result(set: (String) -> Unit, block: suspend () -> String) {
        set("Working…")
        scope.launch {
            set(
                try {
                    "✓ " + block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.w("Scrobble", "Setup failed: ${e.message}")
                    "✗ " + (e.message ?: e.javaClass.simpleName)
                }
            )
        }
    }

    Screen(title = "Scrobbling", nav = nav) { padding ->
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .aboveKeyboard()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Hint("A song counts as listened after half its length or four minutes, whichever comes first. Listens made offline are sent later.")

            Heading("Navidrome")
            SwitchRow("Record plays in Navidrome", settings.scrobbleNavidrome) {
                graph.settings.save(graph.settings.current.copy(scrobbleNavidrome = it))
            }
            Hint("Keeps play counts and \"recently played\" in Navidrome. If Navidrome itself is linked to Last.fm or ListenBrainz, leave the matching option below off, or plays are counted twice.")

            HorizontalDivider()
            Heading("Last.fm")
            SwitchRow("Scrobble to Last.fm", settings.lastFmEnabled) {
                graph.settings.save(graph.settings.current.copy(lastFmEnabled = it))
            }
            Hint("Uses your own Last.fm API account: create one at last.fm/api/account/create and copy the API key and shared secret here. Hearts are sent to Last.fm as loved tracks.")
            Field("API key", lfmKey) { lfmKey = it }
            Secret("Shared secret", lfmSecret) { lfmSecret = it }
            Field("Last.fm username", lfmUser) { lfmUser = it }
            Secret("Last.fm password (used once to sign in, not stored)", lfmPassword) { lfmPassword = it }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    result({ lfmStatus = it }) {
                        val session = graph.lastFm.mobileSession(lfmKey.trim(), lfmSecret.trim(), lfmUser.trim(), lfmPassword)
                        graph.settings.save(
                            graph.settings.current.copy(
                                lastFmEnabled = true,
                                lastFmApiKey = lfmKey.trim(),
                                lastFmSecret = lfmSecret.trim(),
                                lastFmUser = lfmUser.trim(),
                                lastFmSession = session,
                            )
                        )
                        lfmPassword = ""
                        "Signed in as ${lfmUser.trim()}"
                    }
                }) { Text(if (settings.lastFmSession.isBlank()) "Sign in" else "Sign in again") }
                if (settings.lastFmSession.isNotBlank()) {
                    OutlinedButton(onClick = {
                        graph.settings.save(graph.settings.current.copy(lastFmSession = "", lastFmEnabled = false))
                        lfmStatus = "Signed out"
                    }) { Text("Sign out") }
                }
            }
            val lfmShown = lfmStatus ?: if (settings.lastFmSession.isNotBlank()) "✓ Signed in as ${settings.lastFmUser}" else null
            lfmShown?.let { Status(it) }

            HorizontalDivider()
            Heading("ListenBrainz / Koito")
            SwitchRow("Send listens", settings.listenBrainzEnabled) {
                graph.settings.save(graph.settings.current.copy(listenBrainzEnabled = it))
            }
            Hint("For Koito, enter its address (e.g. https://koito.example.com) and an API key from Koito's settings → API Keys. Leave the address empty for listenbrainz.org.")
            Field("Server address", lbUrl, placeholder = "https://koito.example.com") { lbUrl = it }
            Secret("Token / API key", lbToken) { lbToken = it }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    result({ lbStatus = it }) {
                        val message = graph.listenBrainz.validate(lbUrl, lbToken.trim())
                        graph.settings.save(
                            graph.settings.current.copy(listenBrainzEnabled = true, listenBrainzUrl = lbUrl.trim(), listenBrainzToken = lbToken.trim())
                        )
                        "$message · sending to ${ListenBrainzApi.apiRoot(lbUrl)}"
                    }
                }) { Text("Test and save") }
            }
            lbStatus?.let { Status(it) }
        }
    }
}

@Composable
private fun Status(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (text.startsWith("✗")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Field(label: String, value: String, placeholder: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (placeholder != null) Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Secret(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
