package app.musix.ui

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
import androidx.compose.material.icons.rounded.Search
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

    fun runSound() {
        val q = query.trim()
        if (q.length < 3) return
        scope.launch {
            busy = true
            error = null
            try {
                soundSongs = graph.generator.soundSearch(q)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Screen(title = "Search", nav = null) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = {
                    Text(if (soundMode) "Describe a sound, e.g. rainy jazz piano" else "Artists, albums, songs")
                },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (soundMode) runSound() }),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !soundMode, onClick = { soundMode = false }, label = { Text("Library") })
                FilterChip(selected = soundMode, onClick = { soundMode = true }, label = { Text("Sound") })
            }
            if (busy) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            error?.let { ErrorText(it) }
            when {
                soundMode -> SoundResults(soundSongs)
                query.trim().length < 2 -> BrowseGrid(nav)
                else -> LibraryResults(results, nav)
            }
        }
    }
}

@Composable
private fun SoundResults(songs: List<Song>?) {
    val graph = LocalGraph.current
    if (songs == null) {
        Text(
            "Describe a mood, genre or instruments and press search. AudioMuse finds matching tracks by how they sound.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomBarPadding(24.dp))) {
        item {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { PlayButtons(songs) }
        }
        itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }) { index, song ->
            SongRow(song = song, onClick = { graph.player.play(songs, index) })
        }
    }
}

@Composable
private fun LibraryResults(results: SearchResults?, nav: NavController) {
    val graph = LocalGraph.current
    if (results == null) return
    if (results.artists.isEmpty() && results.albums.isEmpty() && results.songs.isEmpty()) {
        Text("Nothing found", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomBarPadding(24.dp))) {
        if (results.artists.isNotEmpty()) {
            item { SectionTitle("Artists") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(results.artists, key = { it.id }) { artist ->
                        Column(
                            Modifier.width(96.dp).clickable { nav.navigate("artist/${artist.id}") }.padding(4.dp),
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
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(results.albums, key = { it.id }) { album ->
                        Box(Modifier.width(150.dp)) {
                            AlbumTile(album.name, album.artist, album.coverArt, onClick = { nav.navigate("album/${album.id}") })
                        }
                    }
                }
            }
        }
        if (results.songs.isNotEmpty()) {
            item { SectionTitle("Songs") }
            itemsIndexed(results.songs, key = { i, s -> "$i-${s.id}" }) { index, song ->
                SongRow(song = song, onClick = { graph.player.play(results.songs, index) })
            }
        }
        item { Spacer(Modifier.size(8.dp)) }
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomBarPadding(24.dp)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { MoodBox(nav, Modifier.padding(top = 4.dp)) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Radios", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }
        items(moodRadios, key = { "mood-" + it.id }) { radio ->
            BrowseTile(radio.name, Color(radio.colors.first) to Color(radio.colors.second)) { nav.openMoodRadio(radio.id) }
        }
        if (genres.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Genres", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
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
            .padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2)
    }
}
