package app.musix.ui

import app.musix.net.ConceptCategory
import app.musix.net.Steer
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
import app.musix.mix.moodRadios
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
import app.musix.data.RecentSearch
import app.musix.net.AudioMuseApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import app.musix.data.SearchResults
import app.musix.data.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Library search by name, or "Sound" search that describes music and lets AudioMuse find it. */
@Composable
fun SearchScreen(nav: NavController) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var soundMode by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    var soundSongs by remember { mutableStateOf<List<Song>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Recent searches show under the box while you're typing into it, before there's anything to search.
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val history by graph.searchHistory.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.searchHistory.all() }
    val recent = history.orEmpty().filter { it.sound == soundMode }

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
        soundSongs = null
        error = null
        runCatching { focus.requestFocus() }
    }

    /** Keeps what's in the box as a recent search (on the search key, or when a result is opened). */
    fun keepSearch() {
        val q = query
        val sound = soundMode
        scope.launch { graph.searchHistory.add(q, sound) }
    }

    // Name search runs as you type; sound search waits for the search key, since it takes longer.
    LaunchedEffect(query, soundMode) {
        if (soundMode) return@LaunchedEffect
        val q = query.trim()
        if (q.length < 2) {
            results = null
            return@LaunchedEffect
        }
        delay(350)
        busy = true
        error = null
        try {
            results = if (graph.library.isSynced) graph.library.search(q) else graph.subsonic.search(q)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        } finally {
            busy = false
        }
    }

    val aiProblem = rememberAiProblem()

    // Concepts to steer the Sound search with (issues #208, #216): every group AudioMuse offers (genre, instrument,
    // vocals, mood, production, tempo, era); each tap goes more → less → off.
    val catalogue by loadState("sound-concepts-$soundMode") {
        if (soundMode && graph.settings.current.audioMuseReady) runCatching { graph.audioMuse.conceptCatalogue() }.getOrNull() else null
    }
    var steering by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }

    fun runSound() {
        val q = query.trim()
        if (q.length < 3 || aiProblem != null) return
        keepSearch()
        val steer = steering.map { (term, more) -> Steer(term, more) }
        scope.launch {
            busy = true
            error = null
            try {
                soundSongs = graph.generator.soundSearch(q, steering = steer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    // Typing or looking at results: the back arrow in the box, or the phone's back gesture, returns to the
    // search page (genres and radios) instead of leaving Search for Home (issue #203).
    val active = searchIsActive(focused, query, soundSongs != null)
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.Page).focusRequester(focus).onFocusChanged { focused = it.isFocused }.testTag("searchField"),
                placeholder = {
                    Text(if (soundMode) "Describe a sound, e.g. rainy jazz piano" else "Artists, albums, songs")
                },
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (soundMode) runSound() else keepSearch()
                    focusManager.clearFocus()
                }),
            )
            Row(Modifier.padding(horizontal = Space.Page, vertical = Space.Small), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                FilterChip(selected = !soundMode, onClick = { soundMode = false }, label = { Text("Library") })
                FilterChip(selected = soundMode, onClick = { soundMode = true }, label = { Text("Sound") })
            }
            val groups = catalogue.valueOrNull()?.categories.orEmpty()
            if (soundMode && aiProblem == null && groups.isNotEmpty()) {
                val shown = groups.firstOrNull { it.id == group } ?: groups.first()
                ConceptGroupRow(groups, shown.id, steering) { group = it }
                SteeringChips(shown.terms, steering) { term ->
                    val next = nextSteering(steering, term, catalogue.valueOrNull()?.maxTerms ?: 10)
                    if (next == steering) {
                        error = "At most ${catalogue.valueOrNull()?.maxTerms ?: 10} at once. Tap one to take it off first."
                    } else {
                        error = null
                        steering = next
                        if (soundSongs != null) runSound()
                    }
                }
            }
            if (busy) {
                Box(Modifier.fillMaxWidth().padding(Space.Page), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            error?.let { ErrorText(it) }
            when {
                soundMode && aiProblem != null -> AiUnavailable(aiProblem, Modifier.padding(horizontal = Space.Page, vertical = Space.Small))
                focused && query.trim().length < 2 && recent.isNotEmpty() -> RecentSearchList(
                    recent,
                    onPick = { picked ->
                        query = picked.query
                        focusManager.clearFocus()
                        if (soundMode) runSound() else keepSearch()
                    },
                    onRemove = { scope.launch { graph.searchHistory.remove(it) } },
                    onClear = { scope.launch { graph.searchHistory.clear() } },
                )
                soundMode -> SoundResults(soundSongs, onPlay = ::keepSearch)
                query.trim().length < 2 -> BrowseGrid(nav)
                else -> LibraryResults(results, nav, onOpen = ::keepSearch)
            }
        }
    }
}

@Composable
private fun SoundResults(songs: List<Song>?, onPlay: () -> Unit) {
    val graph = LocalGraph.current
    if (songs == null) {
        Text(
            "Describe a mood, genre or instruments and press search. AudioMuse finds matching tracks by how they sound.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Space.Page),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomBarPadding(Space.Section))) {
        item {
            Box(Modifier.padding(horizontal = Space.Page, vertical = Space.Small)) { PlayButtons(songs) }
        }
        itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }) { index, song ->
            SongRow(song = song, onClick = { onPlay(); graph.player.play(songs, index) })
        }
    }
}

@Composable
private fun LibraryResults(results: SearchResults?, nav: NavController, onOpen: () -> Unit) {
    val graph = LocalGraph.current
    if (results == null) return
    if (results.artists.isEmpty() && results.albums.isEmpty() && results.songs.isEmpty()) {
        Text("Nothing found", modifier = Modifier.padding(Space.Page), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomBarPadding(Space.Section))) {
        if (results.artists.isNotEmpty()) {
            item { SectionTitle("Artists") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = Space.Medium)) {
                    items(results.artists, key = { it.id }) { artist ->
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
        if (results.albums.isNotEmpty()) {
            item { SectionTitle("Albums") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = Space.Small)) {
                    items(results.albums, key = { it.id }) { album ->
                        Box(Modifier.width(150.dp)) {
                            AlbumTile(album.name, album.artist, album.coverArt, onClick = { onOpen(); nav.navigate("album/${album.id}") })
                        }
                    }
                }
            }
        }
        if (results.songs.isNotEmpty()) {
            item { SectionTitle("Songs") }
            itemsIndexed(results.songs, key = { i, s -> "$i-${s.id}" }) { index, song ->
                SongRow(song = song, onClick = { onOpen(); graph.player.play(results.songs, index) })
            }
        }
        item { Spacer(Modifier.size(Space.Small)) }
    }
}

/** With an empty search box: radios and genres to browse, like a streaming app's Browse page. */
@Composable
private fun BrowseGrid(nav: NavController) {
    val graph = LocalGraph.current
    val version by graph.library.version.collectAsStateWithLifecycle()
    val genreList by loadState(version to "browse-genres") { if (graph.library.isSynced) graph.library.genres(30) else emptyList() }
    val genres = genreList.valueOrNull().orEmpty()
    val centreList by loadState("browse-centres") {
        if (graph.settings.current.audioMuseReady) runCatching { graph.audioMuse.moodCentres() }.getOrDefault(emptyList()) else emptyList()
    }
    val centres = centreList.valueOrNull().orEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(start = Space.Page, end = Space.Page, bottom = bottomBarPadding(Space.Section)),
        horizontalArrangement = Arrangement.spacedBy(Space.Medium),
        verticalArrangement = Arrangement.spacedBy(Space.Medium),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { MoodBox(nav, Modifier.padding(top = Space.Tight)) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Radios", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = Space.Small))
        }
        items(moodRadios, key = { "mood-" + it.id }) { radio ->
            BrowseTile(radio.name, Color(radio.colors.first) to Color(radio.colors.second)) { nav.openMoodRadio(radio.id) }
        }
        // AudioMuse's own mood centres, found in your library's sound (issue #208).
        if (centres.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Moods in your library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = Space.Small))
            }
            items(centres, key = { "centre-" + AudioMuseApi.centreKey(it) }) { centre ->
                BrowseTile(AudioMuseApi.centreTitle(centre), tileColors("centre-" + AudioMuseApi.centreKey(centre))) {
                    nav.openCentreRadio(AudioMuseApi.centreKey(centre))
                }
            }
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

/** One tap on a concept: off → more → less → off (issue #208); no new one beyond [max] at once (#216). */
fun nextSteering(steering: Map<String, Boolean>, term: String, max: Int = Int.MAX_VALUE): Map<String, Boolean> = when (steering[term]) {
    null -> if (steering.size >= max) steering else steering + (term to true)
    true -> steering + (term to false)
    false -> steering - term
}

/**
 * The groups of concepts (Genre, Instrument, Vocals, Mood, Production, Tempo, Era), one shown at a time. A
 * group with concepts in use says how many, so they aren't forgotten while another group shows (issue #216).
 */
@Composable
internal fun ConceptGroupRow(groups: List<ConceptCategory>, selected: String, steering: Map<String, Boolean>, onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.Page).testTag("conceptGroups"),
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        groups.forEach { g ->
            val used = g.terms.count { it in steering }
            FilterChip(
                selected = g.id == selected,
                onClick = { onPick(g.id) },
                label = { Text(if (used > 0) "${g.label} · $used" else g.label) },
            )
        }
    }
}

/**
 * "More piano", "less vocals": the concepts of one group as chips that steer the Sound search. With the group
 * chips above them they form one block, with the same gap above it (the Library / Sound row's padding and the
 * chips' touch margins) as below it: Space.Page (SpacingTest.steeringChipsAreEvenlySpaced).
 */
@Composable
internal fun SteeringChips(terms: List<String>, steering: Map<String, Boolean>, onTap: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = Space.Page, end = Space.Page, bottom = Space.Page).testTag("steering"),
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        terms.forEach { term ->
            val state = steering[term]
            FilterChip(
                selected = state != null,
                onClick = { onTap(term) },
                label = { Text(when (state) { true -> "More $term"; false -> "Less $term"; null -> term }) },
            )
        }
    }
}
