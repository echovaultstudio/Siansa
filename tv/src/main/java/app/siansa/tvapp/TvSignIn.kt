package app.siansa.tvapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.siansa.ui.TvTextBox
import app.siansa.ui.TvTextKind
import androidx.compose.ui.unit.dp
import app.siansa.AppGraph
import app.siansa.log.AppLog
import app.siansa.pairing.Pairing
import app.siansa.ui.LocalGraph
import app.siansa.ui.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Signing in on the TV (issue #860): a QR code the phone's Siansa reads to send the server and login, so nothing has to
 * be typed with the remote. Typing it in stays possible.
 */
@Composable
fun TvSignIn() {
    var typing by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxSize().padding(horizontal = TV_EDGE * 2, vertical = Space.Large),
        horizontalArrangement = Arrangement.spacedBy(Space.Large * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.Section)) {
            Text("Welcome to Siansa", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            signInProblem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium) }
            if (!typing) {
                Step(1, "Scan the code with your phone's camera.")
                Step(2, "Tap \"Open in Siansa\", then \"Sign in\".")
                Step(3, "Your TV signs in to the same server as your phone.")
                TvButton("Type it in instead", Icons.Rounded.Keyboard, onClick = { typing = true })
                // A server or user added from Settings that isn't wanted after all: back to one already signed in.
                val context = androidx.compose.ui.platform.LocalContext.current
                val others = remember { app.siansa.data.Users.all(context).filter { it.id != app.siansa.data.Users.active && it.connected } }
                for (u in others.take(MAX_OTHERS)) {
                    TvButton("Use ${u.label} instead", null, onClick = { app.siansa.data.Users.switchTo(context, u.id) })
                }
            } else {
                TypedSignIn(onBack = { typing = false })
            }
        }
        if (!typing) PairingCode()
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.Page), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(STEP_DOT).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(percent = 50)),
            contentAlignment = Alignment.Center,
        ) {
            Text(n.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * The QR code, with a server of its own behind it for as long as it shows. A login the server turns down (it couldn't
 * be reached) brings a fresh code and says why.
 */
@Composable
private fun PairingCode() {
    val graph = LocalGraph.current
    var round by remember { mutableIntStateOf(0) }
    var url by remember(round) { mutableStateOf<String?>(null) }
    var failed by remember(round) { mutableStateOf(false) }
    val server = remember(round) {
        PairingServer(Pairing.newKey()) { login ->
            graph.scope.launch {
                withContext(Dispatchers.Main) {
                    if (signIn(graph, login) != null) round++
                }
            }
        }
    }
    LaunchedEffect(server) {
        url = withContext(Dispatchers.IO) { runCatching { server.open() }.getOrNull() }
        if (url == null) failed = true else server.serve()
    }
    DisposableEffect(server) { onDispose { server.close() } }

    Box(
        Modifier.size(QR_SIZE).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Space.Page)).padding(Space.Section),
        contentAlignment = Alignment.Center,
    ) {
        val shown = url
        when {
            shown != null -> QrCode(shown, Modifier.fillMaxSize())
            failed -> Text(
                "This TV isn't on a home network the phone can reach. Type it in instead.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Unit
        }
    }
}

/** The server address and login, typed with the remote's on-screen keyboard. */
@Composable
private fun TypedSignIn(onBack: () -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    var address by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.width(FORM_WIDTH), verticalArrangement = Arrangement.spacedBy(Space.Page)) {
        // Android's own text boxes, so a phone's keyboard can type and paste into them.
        TvTextBox(address, { address = it }, "Server address", Modifier.fillMaxWidth(), placeholder = "http://192.168.1.10:4533", kind = TvTextKind.ADDRESS)
        TvTextBox(user, { user = it }, "Username", Modifier.fillMaxWidth())
        TvTextBox(password, { password = it }, "Password", Modifier.fillMaxWidth(), kind = TvTextKind.SECRET)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
            TvButton(if (busy) "Signing in…" else "Sign in", Icons.AutoMirrored.Rounded.Login, strong = true, onClick = {
                if (busy) return@TvButton
                if (address.isBlank() || user.isBlank() || password.isBlank()) {
                    signInProblem = "Fill in the address, username and password."
                    return@TvButton
                }
                busy = true
                scope.launch {
                    signIn(graph, Pairing.Login(url = address.trim(), user = user.trim(), password = password))
                    busy = false
                }
            })
            TvButton("Back", null, onClick = onBack)
        }
    }
}

/** Why the last sign-in didn't work, shown on the sign-in screen. */
private var signInProblem by mutableStateOf<String?>(null)

/**
 * A login being tried: its settings are saved before the server is asked, and the sign-in screen stays up until the
 * server answers (TvRoot), rather than Home showing for a moment.
 */
internal var signingIn by mutableStateOf(false)
    private set

/**
 * Saves [login] as this TV's server and starts the library sync. Returns the problem (also shown on the sign-in
 * screen), or null once signed in (the sign-in screen then gives way to Home).
 */
private suspend fun signIn(graph: AppGraph, login: Pairing.Login): String? {
    signInProblem = null
    signingIn = true
    try {
        return tryLogin(graph, login)
    } finally {
        signingIn = false
    }
}

private suspend fun tryLogin(graph: AppGraph, login: Pairing.Login): String? {
    val before = graph.settings.current
    try {
        graph.settings.save(login.into(before))
    } catch (e: Exception) {
        signInProblem = e.message ?: "That address can't be used."
        return signInProblem
    }
    val reached = withContext(Dispatchers.IO) { runCatching { graph.source.ping() } }
    if (reached.isFailure) {
        AppLog.w("Tv", "Sign-in failed: ${reached.exceptionOrNull()?.message}")
        graph.settings.save(before)
        signInProblem = "Couldn't sign in: ${reached.exceptionOrNull()?.message ?: "the server didn't answer"}."
        return signInProblem
    }
    AppLog.i("Tv", "Signed in: ${reached.getOrNull()}")
    graph.sync.start()
    return null
}

private val STEP_DOT = 36.dp
private const val MAX_OTHERS = 3
private val QR_SIZE = 340.dp
private val FORM_WIDTH = 520.dp
