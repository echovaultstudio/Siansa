package app.siansa.ui

import app.siansa.log.AppLog

import androidx.compose.material3.OutlinedTextFieldDefaults

import app.siansa.mix.VibeTuning

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.mix.moodRadios
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import app.siansa.data.RecentSearch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.siansa.data.Book
import app.siansa.data.SearchResults
import app.siansa.data.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Library search by name, or "Sound" search that describes music and lets AudioMuse find it. */
@Composable
fun SearchScreen(nav: NavController) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    // One box for everything (issue #373): names as you type, and songs that sound like the words once you pause.
    var soundSongs by remember { mutableStateOf<List<Song>?>(null) }
    var soundBusy by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Books and podcasts from Audiobookshelf, with the music (issue #531); null without a server.
    var books by remember { mutableStateOf<List<Book>?>(null) }
    // Recent searches show under the box while you're typing into it, before there's anything to search.
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val history by graph.searchHistory.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.searchHistory.all() }
    val recent = oneSearchList(history.orEmpty())

    // The Search tab tapped again (issue #189): a new search, with an empty box ready to type in.
    val focus = remember { FocusRequester() }
    val resets by SearchReset.requests.collectAsStateWithLifecycle()
    var handledReset by rememberSaveable { mutableStateOf(resets) }
    LaunchedEffect(resets) {
        val pending = searchResetPending(resets, handledReset)
        handledReset = resets
        if (!pending) return@LaunchedEffect
        query = ""
        results = null
        books = null
        soundSongs = null
        error = null
        runCatching { focus.requestFocus() }
    }

    /** Keeps what's in the box as a recent search (on the search key, or when a result is opened). */
    fun keepSearch() {
        val q = query
        scope.launch { graph.searchHistory.add(q, false) }
    }

    // Names as you type.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = null
            books = null
            return@LaunchedEffect
        }
        delay(NAME_SEARCH_DELAY_MS)
        busy = true
        error = null
        try {
            results = if (graph.library.isSynced) graph.library.search(q) else graph.source.search(q)
            // Books too, after the music so a slow Audiobookshelf never holds the names back.
            books = if (graph.books.ready) runCatching { graph.books.search(q) }.onFailure { AppLog.w("Search", "Books for \"$q\": ${it.message}") }.getOrDefault(emptyList()) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        } finally {
            busy = false
        }
    }

    val aiProblem = rememberAiProblem()

    // Sounds like the words: AudioMuse's text-to-audio (CLAP) search, once typing pauses, as it takes longer.
    LaunchedEffect(query, aiProblem) {
        val q = query.trim()
        soundSongs = null
        if (!soundSearchWanted(q, aiProblem != null)) return@LaunchedEffect
        delay(SOUND_SEARCH_DELAY_MS)
        soundBusy = true
        try {
            soundSongs = graph.generator.soundSearch(q)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Search", "Sound search for \"$q\" failed: ${e.message}")
            soundSongs = emptyList()
        } finally {
            soundBusy = false
        }
    }

    // Typing or looking at results: the back arrow in the box, or the phone's back gesture, returns to the
    // search page (genres and radios) instead of leaving Search for Home (issue #203).
    val active = searchIsActive(focused, query, false)
    fun leaveSearch() {
        query = ""
        results = null
        soundSongs = null
        error = null
        focusManager.clearFocus()
    }
    // Not while now playing is open over it: back closes that first.
    val sheet = LocalPlayerSheet.current
    BackHandler(enabled = active && !sheet.isOpen) { leaveSearch() }

    Screen(title = "Search", nav = null) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            // Rounder, on a slightly lighter grey than the page (issue #373).
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.Page).focusRequester(focus).onFocusChanged { focused = it.isFocused }.testTag("searchField"),
                placeholder = { Text("Artists, albums, songs or a sound") },
                leadingIcon = {
                    if (active) {
                        IconButton(onClick = { leaveSearch() }, modifier = Modifier.testTag("searchBack")) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                    } else {
                        Icon(Icons.Rounded.Search, null)
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(SEARCH_CORNER),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keepSearch()
                    focusManager.clearFocus()
                }),
            )
            Spacer(Modifier.height(Space.Small))
            if (busy) {
                Box(Modifier.fillMaxWidth().padding(Space.Page), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            error?.let { ErrorText(it) }
            when {
                focused && query.trim().length < 2 && recent.isNotEmpty() -> RecentSearchList(
                    recent,
                    onPick = { picked ->
                        query = picked.query
                        focusManager.clearFocus()
                        keepSearch()
                    },
                    onRemove = { scope.launch { graph.searchHistory.remove(it) } },
                    onClear = { scope.launch { graph.searchHistory.clear() } },
                )
                query.trim().length < 2 -> BrowseGrid(nav)
                else -> SearchResultsList(
                    results,
                    books,
                    soundSongs,
                    soundBusy,
                    nav,
                    onOpen = ::keepSearch,
                    query = query.trim(),
                    canSound = aiProblem == null,
                )
            }
        }
    }
}

/** How long typing must pause before names are looked up, and before the slower sound search runs. */
private const val NAME_SEARCH_DELAY_MS = 350L
internal const val SOUND_SEARCH_DELAY_MS = 900L

/** The search box's corner (issue #373): fully round ends at its usual height. */
private val SEARCH_CORNER = 28.dp

/** Whether the words should also be searched by sound: at least three letters, with AudioMuse reachable. */
internal fun soundSearchWanted(query: String, aiDown: Boolean): Boolean = !aiDown && query.trim().length >= 3

/**
 * Recent searches for the one box (issue #373): names and earlier "Sound" searches together, newest first, each
 * wording once.
 */
internal fun oneSearchList(history: List<app.siansa.data.RecentSearch>): List<app.siansa.data.RecentSearch> =
    history.distinctBy { it.query.trim().lowercase() }

/**
 * Everything the words find (issue #373): making a vibe from them, artists, albums and songs by name, then songs
 * that sound like them.
 */
@Composable
private fun SearchResultsList(
    results: SearchResults?,
    books: List<Book>?,
    soundSongs: List<Song>?,
    soundBusy: Boolean,
    nav: NavController,
    onOpen: () -> Unit,
    query: String,
    canSound: Boolean,
) {
    val graph = LocalGraph.current
    if (results == null && soundSongs == null && !soundBusy) return
    // One search box for names and for vibes (issue #339): the same words can make a vibe.
    val vibe = canSound && query.length >= 3
    val byName = results != null && (results.artists.isNotEmpty() || results.albums.isNotEmpty() || results.songs.isNotEmpty())
        || !books.isNullOrEmpty()
    val bySound = !soundSongs.isNullOrEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomBarPadding(Space.Section))) {
        if (vibe) item(key = "make-vibe") { MakeVibeRow(query, nav, onOpen) }
        if (results != null && !byName && !bySound && !soundBusy) {
            item(key = "nothing") {
                Text("Nothing found", modifier = Modifier.padding(Space.Page), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // A few of each kind (beta #21); the section's title opens every match on a page of its own.
        fun more(kind: SearchMore) { onOpen(); nav.navigate(searchMoreRoute(kind, query.trim())) }
        if (results != null && results.artists.isNotEmpty()) {
            item { SearchSectionTitle("Artists") { more(SearchMore.ARTISTS) } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = Space.Medium)) {
                    items(results.artists.take(SEARCH_PREVIEW), key = { it.id }) { artist ->
                        Column(
                            Modifier.width(96.dp).clickable { onOpen(); nav.navigate("artist/${artist.id}") }.padding(Space.Tight),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Cover(artist.coverArt, 150, Modifier.size(80.dp), CircleShape)
                            Text(
                                artist.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (results != null && results.albums.isNotEmpty()) {
            item { SearchSectionTitle("Albums") { more(SearchMore.ALBUMS) } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = Space.Small)) {
                    items(results.albums.take(SEARCH_PREVIEW), key = { it.id }) { album ->
                        Box(Modifier.width(150.dp)) {
                            AlbumTile(album.name, album.artist, album.coverArt, onClick = { onOpen(); nav.navigate("album/${album.id}") })
                        }
                    }
                }
            }
        }
        if (results != null && results.songs.isNotEmpty()) {
            item { SearchSectionTitle("Songs") { more(SearchMore.SONGS) } }
            itemsIndexed(results.songs.take(SEARCH_PREVIEW), key = { i, s -> "name-$i-${s.id}" }) { index, song ->
                SongRow(song = song, onClick = { onOpen(); graph.player.play(results.songs, index) })
            }
        }
        // Books and podcasts from Audiobookshelf, a row each, like albums (issue #531); a tile opens the book's page.
        for ((podcast, title) in listOf(false to "Books", true to "Podcasts")) {
            val hits = books.orEmpty().filter { it.podcast == podcast }
            if (hits.isEmpty()) continue
            item(key = "$title-title") { SectionTitle(title) }
            item(key = "$title-row") {
                LazyRow(contentPadding = PaddingValues(horizontal = Space.Small)) {
                    items(hits.take(SEARCH_PREVIEW), key = { it.id }) { book ->
                        Box(Modifier.width(150.dp).testTag("searchBook-${book.id}")) {
                            AlbumTile(book.title, book.authors.joinToString(), book.cover, onClick = { onOpen(); nav.navigate("book/${book.id}") })
                        }
                    }
                }
            }
        }
        if (canSound && (bySound || soundBusy)) {
            item(key = "sound-title") { SearchSectionTitle("Sounds like “$query”") { more(SearchMore.SOUND) } }
            if (soundBusy && soundSongs == null) {
                item(key = "sound-busy") {
                    Box(Modifier.fillMaxWidth().padding(Space.Page), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
            val songs = soundSongs.orEmpty()
            // Play and Shuffle are on the page the title opens, at the top (they sat halfway down here, beta #21).
            if (songs.isNotEmpty()) {
                itemsIndexed(songs.take(SEARCH_PREVIEW), key = { i, s -> "sound-$i-${s.id}" }) { index, song ->
                    SongRow(song = song, onClick = { onOpen(); graph.player.play(songs, index) })
                }
            }
        }
        item { Spacer(Modifier.size(Space.Small)) }
    }
}

/** "Make a vibe from “…”": the words in the search box made into a vibe on Dscvr's page (issue #339). */
@Composable
private fun MakeVibeRow(query: String, nav: NavController, onOpen: () -> Unit) {
    val graph = LocalGraph.current
    val launch = rememberPlayLauncher()
    val busy = vibeMakingBusy()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy) {
                onOpen()
                makeVibe(graph, nav, launch::notify, query, VibeTuning())
            }
            .padding(horizontal = Space.Page, vertical = Space.Medium)
            .testTag("makeVibe"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) // spacing: exact
        else Icon(DscvrStars, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Space.Medium))
        Text(
            if (busy) "Making your vibe…" else "Make a vibe from “$query”",
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** With an empty search box: radios and genres to browse, like a streaming app's Browse page. */
@Composable
private fun BrowseGrid(nav: NavController) {
    val graph = LocalGraph.current
    val version by graph.library.version.collectAsStateWithLifecycle()
    val genreList by loadState(version to "browse-genres") { if (graph.library.isSynced) graph.library.genres(30) else emptyList() }
    val genres = genreList.valueOrNull().orEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(start = Space.Page, end = Space.Page, bottom = bottomBarPadding(Space.Section)),
        horizontalArrangement = Arrangement.spacedBy(Space.Medium),
        verticalArrangement = Arrangement.spacedBy(Space.Medium),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Radios", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = Space.Small))
        }
        items(moodRadios, key = { "mood-" + it.id }) { radio ->
            BrowseTile(radio.name, Color(radio.colors.first) to Color(radio.colors.second)) { nav.openMoodRadio(radio.id) }
        }
        if (genres.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Genres", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = Space.Small))
            }
            items(genres, key = { "genre-" + it.name }) { genre ->
                BrowseTile(genre.name, tileColors(genre.name)) { nav.openGenreRadio(genre.name) }
            }
        }
    }
}

@Composable
private fun BrowseTile(title: String, colors: Pair<Color, Color>, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(colors.first, colors.second)))
            .clickable(onClick = onClick)
            .padding(Space.Medium),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2)
    }
}

/** The gap between the heading of recent searches and its first row, and between rows (the rows' own padding). */
internal val RECENT_ROW_PAD: Dp = Space.Medium

/**
 * Recent searches (issue #177), newest first: tap one to search it again, × to forget it, or clear them all.
 * Shown under the search box once you tap into it, before you've typed anything.
 */
@Composable
internal fun RecentSearchList(
    searches: List<RecentSearch>,
    onPick: (RecentSearch) -> Unit,
    onRemove: (RecentSearch) -> Unit,
    onClear: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().testTag("recentSearches"), contentPadding = PaddingValues(bottom = bottomBarPadding(Space.Section))) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = Space.Page, end = Space.Tight, top = Space.Small), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent searches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).testTag("recentTitle"))
                TextButton(onClick = onClear) { Text("Clear all") }
            }
        }
        items(searches, key = { (if (it.sound) "s:" else "l:") + it.query }) { search ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(search) }
                    .padding(start = Space.Page, end = Space.Tight)
                    .testTag("recent"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    search.query,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = Space.Page, vertical = RECENT_ROW_PAD),
                )
                IconButton(onClick = { onRemove(search) }) { Icon(Icons.Rounded.Close, "Remove ${search.query}") }
            }
        }
    }
}
