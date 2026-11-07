package app.siansa.tvapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.foundation.layout.Row
import app.siansa.ui.LocalNav
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import app.siansa.ui.TvTextBox
import app.siansa.ui.TvTextKind
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.data.SearchResults
import app.siansa.ui.LocalGraph
import app.siansa.ui.Space
import kotlinx.coroutines.delay

/**
 * Search the library (issue #860): typed with the TV's on-screen keyboard, or spoken through its microphone button,
 * or arriving from Google TV's own search. Results come from the library on the TV, as the phone's do.
 */
@Composable
fun TvSearch(fromSystem: String?, onDone: () -> Unit) {
    val graph = LocalGraph.current
    val player by graph.player.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(fromSystem.orEmpty()) }
    val field = remember { FocusRequester() }
    LaunchedEffect(fromSystem) {
        if (fromSystem != null) {
            query = fromSystem
            onDone()
        }
    }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    val results by produceState<SearchResults?>(null, query) {
        val terms = query.trim()
        if (terms.length < MIN_LETTERS) { value = null; return@produceState }
        delay(TYPING_PAUSE_MS)
        // The library on the TV once synced; the server until then, so search works straight after signing in.
        value = runCatching { if (graph.library.isSynced) graph.library.search(terms) else graph.source.search(terms) }.getOrNull()
    }

    // Opening Search opens the keyboard (not for a search Google TV handed over, which has its words already).
    val opensKeyboard = remember { fromSystem == null }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.Section)) {
        // Android's own text box, so a phone's keyboard can type and paste into it; results show as it's typed.
        TvTextBox(
            value = query,
            onValueChange = { query = it },
            label = "Search",
            placeholder = "Songs, albums or artists",
            kind = TvTextKind.SEARCH,
            opensOnFocus = opensKeyboard,
            inForm = false,
            modifier = Modifier.padding(horizontal = TV_EDGE).fillMaxWidth(SEARCH_WIDTH).focusRequester(field),
        )
        // With AudioMuse: songs that sound like the words ("rainy sunday morning"), said or typed, as on the phone.
        val settings by graph.settings.state.collectAsStateWithLifecycle()
        val nav = LocalNav.current
        if (settings.audioMuseReady && query.trim().length >= MOOD_LETTERS) {
            Row(Modifier.padding(horizontal = TV_EDGE)) {
                TvButton("Songs that sound like \"${query.trim()}\"", Icons.Rounded.AutoAwesome, onClick = {
                    nav.navigate("mood/${android.net.Uri.encode(query.trim())}")
                })
            }
        }
        val found = results
        when {
            query.trim().length < MIN_LETTERS -> EmptyNote("Type or say what you're looking for.")
            found == null -> Unit
            found.artists.isEmpty() && found.albums.isEmpty() && found.songs.isEmpty() -> EmptyNote("Nothing found for \"${query.trim()}\".")
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Space.Large),
                verticalArrangement = Arrangement.spacedBy(Space.Section),
            ) {
                if (found.artists.isNotEmpty()) item("artists") {
                    TvItemRow(TvRow("Artists", found.artists.take(RESULTS).map { TvItem.OfArtist(it) }))
                }
                if (found.albums.isNotEmpty()) item("albums") {
                    TvItemRow(TvRow("Albums", found.albums.take(RESULTS).map { TvItem.OfAlbum(it) }))
                }
                if (found.songs.isNotEmpty()) item("songs") {
                    val songs = found.songs.take(RESULTS)
                    Column(Modifier.padding(horizontal = TV_EDGE), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                        Text("Songs", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = Space.Page))
                        songs.forEachIndexed { i, song ->
                            SongRow(i + 1, song, playing = player.current?.id == song.id) { graph.player.play(songs, startIndex = i) }
                        }
                    }
                }
            }
        }
    }
}

private const val MIN_LETTERS = 2
private const val MOOD_LETTERS = 3
private const val TYPING_PAUSE_MS = 250L
private const val RESULTS = 30
private const val SEARCH_WIDTH = 0.6f
