package app.siansa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation.NavController
import app.siansa.log.AppLog
import app.siansa.net.ApiException
import app.siansa.net.AudiobookshelfApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Settings › Users › Audiobookshelf (#582): the server's address and login, moved here from Connections, as the
 * books come from a login of their own. Test uses what's typed; Save keeps it and forgets what was read from the
 * old login, so Home and Your Library ask the new one.
 */
@Composable
fun AudiobookshelfScreen(nav: NavController) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(graph.settings.current) }
    var test by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun save() {
        try {
            graph.settings.save(graph.settings.current.withConnectionsFrom(draft))
        } catch (e: Exception) {
            problem = e.message
            return
        }
        problem = null
        AppLog.i("Settings", "Audiobookshelf ${if (draft.booksReady) "saved: " + AppLog.redact(draft.audiobookshelfUrl) else "cleared"}")
        scope.launch { graph.books.forget() }
        nav.popBackStack()
    }

    Screen(
        title = "Audiobookshelf",
        nav = nav,
        actions = { IconButton(onClick = { save() }) { Icon(Icons.Rounded.Check, "Save") } },
    ) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().aboveKeyboard().verticalScroll(rememberScrollState()).padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            Hint("Audiobooks and podcasts from your Audiobookshelf server, next to your music. One login for the whole app; leave it empty for no books.")
            Field("Server address", draft.audiobookshelfUrl, "192.168.1.10:13378") { draft = draft.copy(audiobookshelfUrl = it) }
            Hint("Use either an API token (Audiobookshelf › your user › API token) or the username and password. An admin login can also ask the server to scan for new books, from Settings › Users.")
            SecretField("API token", draft.audiobookshelfToken) { draft = draft.copy(audiobookshelfToken = it) }
            Field("Username", draft.audiobookshelfUser, keyboard = KeyboardType.Text) { draft = draft.copy(audiobookshelfUser = it) }
            SecretField("Password", draft.audiobookshelfPassword) { draft = draft.copy(audiobookshelfPassword = it) }
            TestRow(test) {
                test = "Testing…"
                scope.launch {
                    test = try {
                        val s = graph.settings.clean(draft)
                        if (!s.booksReady) throw ApiException("Enter the server address and a token, or a username and password")
                        val api = AudiobookshelfApi(graph.http) { s }
                        val who = api.ping()
                        val libraries = api.libraries()
                        val books = libraries.count { !it.podcasts }
                        val podcasts = libraries.size - books
                        "✓ Connected to $who · " + listOfNotNull(
                            "$books book ${if (books == 1) "library" else "libraries"}".takeIf { books > 0 },
                            "$podcasts podcast ${if (podcasts == 1) "library" else "libraries"}".takeIf { podcasts > 0 },
                        ).joinToString(", ").ifEmpty { "no libraries this user can see" }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        "✗ " + (e.message ?: e.javaClass.simpleName)
                    }
                }
            }
            problem?.let { ErrorText(it, Modifier) }
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().testTag("absSave")) { Text("Save") }
        }
    }
}
