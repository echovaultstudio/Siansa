package app.siansa.ui

import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.AppGraph
import app.siansa.data.Album
import app.siansa.log.AppLog
import app.siansa.mix.Rediscovery
import app.siansa.mix.moodRadios
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Home rows that open a page with all their items when their heading is tapped. */
enum class HomeSection(val id: String, val title: String) {
    Rediscover("rediscover", "Rediscover"),
    Radios("radios", "Radios"),
    Playlists("playlists", "Your playlists"),
    RecentlyPlayed("recent", "Recently played"),
    ArtistRadios("artists", "Artist radios"),
    RecentlyAdded("newest", "Recently added"),
    Genres("genres", "Your genres"),
    RandomPicks("random", "Random picks"),
    ;

    val route get() = "home/$id"

    companion object {
        fun byId(id: String): HomeSection? = entries.firstOrNull { it.id == id }
    }
}

/** How many items a row needs before its heading opens the full list. */
const val HOME_ROW_FULL_LIST_FROM = 5

/** A row opens its full list only when it has more than a handful of items. */
fun homeRowOpensFullList(count: Int): Boolean = count >= HOME_ROW_FULL_LIST_FROM

/** The line under a Rediscover album: how long ago it was last played, or its artist. */
fun rediscoverSubtitle(r: Rediscovery, today: LocalDate = LocalDate.now()): String {
    val ago = r.lastPlayed?.let { ChronoUnit.MONTHS.between(it, today) }
    return when {
        ago != null && ago >= 12 -> "Last played ${ago / 12} year${if (ago >= 24) "s" else ""} ago"
        ago != null -> "Last played $ago months ago"
        else -> r.album.artist
    }
}

/** The full list behind a Home row, shown as a grid. */
@Composable
fun HomeSectionScreen(nav: NavController, id: String) {
    val section = HomeSection.byId(id)
    if (section == null) {
        AppLog.w("Home", "Unknown home section: $id")
        Screen("Not found", nav) { padding -> ErrorText("This page doesn't exist.", Modifier.padding(padding).padding(Space.Page)) }
        return
    }
    val graph = LocalGraph.current
    val launch = rememberPlayLauncher()
    val version by graph.library.version.collectAsStateWithLifecycle()
    LaunchedEffect(section) { AppLog.i("Home", "Full list: ${section.id}") }

    val items by loadState(version to section) { loadSection(graph, section) }
    // A grid of three or a list, switched at the top and kept per kind of page (issue #289). Playlists start
    // as a list; the rest as a grid.
    var grid by rememberGridMode("section-${section.id}", default = section != HomeSection.Playlists)
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    // The page of all genres is a list to look one up in (the owner, 15 Nov): a box to find one by name, and A to Z
    // down the right edge.
    val finds = section == HomeSection.Genres
    var find by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Screen(section.title, nav) { padding ->
        Box(Modifier.padding(padding)) {
            LoadBox(items) { all ->
                val list = androidx.compose.runtime.remember(all, find) { if (finds) foundByName(all, find) { it.name } else all }
                val starts = androidx.compose.runtime.remember(list, find) {
                    if (!finds || find.isNotBlank()) null
                    else AlphabetIndex.starts(list.map { it.name }).takeIf { AlphabetIndex.shouldShow(list.size, it) }
                }
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                if (finds && all.isNotEmpty()) {
                    FindField(find, "Find a genre…", "genreFind", Modifier.padding(horizontal = Space.Page, vertical = Space.Small)) {
                        find = it
                        scope.launch { gridState.scrollToItem(0) }
                    }
                }
                if (list.isEmpty()) {
                    Text(
                        if (find.isNotBlank()) "No genre found for “${find.trim()}”." else "Nothing here yet.",
                        Modifier.padding(Space.Page).testTag("sectionEmpty"),
                    )
                } else {
                    // Long lists get a handle on the right to move through them quickly (beta testers #24).
                    val fast = starts == null && showFastScroll(list.size)
                    Box(Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = gridCells(grid),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = if (grid) Space.Small else 0.dp,
                                end = (if (grid) Space.Small else 0.dp) + (if (fast || starts != null) AlphabetBarWidth else 0.dp),
                                bottom = Space.Section,
                            ),
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) { GridListHeader("${list.size} ${if (list.size == 1) "item" else "items"}", grid) { grid = !grid } }
                            items(list, key = { it.key }) { item -> if (grid) SectionTile(item, nav, launch, graph) else SectionRow(item, nav, launch, graph) }
                        }
                        if (fast) GridFastScroll(gridState, list.size) { i -> FastScroll.positionLabel(i, list.size) }
                        if (starts != null) {
                            // The grid's first item is the count and the grid or list switch, so positions are one more.
                            val letterAt = androidx.compose.runtime.remember(starts) { starts.entries.sortedBy { it.value }.map { it.value to it.key } }
                            val current by androidx.compose.runtime.remember(letterAt) {
                                androidx.compose.runtime.derivedStateOf { letterAt.lastOrNull { it.first <= gridState.firstVisibleItemIndex - 1 }?.second }
                            }
                            AlphabetBar(
                                present = starts.keys,
                                current = current,
                                onPick = { letter -> AlphabetIndex.target(letter, starts)?.let { scope.launch { gridState.scrollToItem(if (it == 0) 0 else it + 1) } } },
                                modifier = Modifier.align(Alignment.TopEnd).padding(bottom = Space.Small),
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

/** One entry of a full list as a row (issue #289). */
@Composable
private fun SectionRow(item: SectionItem, nav: NavController, launch: PlayLauncher, graph: AppGraph) {
    when (item) {
        is SectionItem.AlbumItem -> MediaRow(item.album.name, item.subtitle, item.album.coverArt, onClick = { nav.navigate("album/${item.album.id}") })
        is SectionItem.PlaylistItem -> PlaylistRow(item.playlist, onClick = { nav.navigate("playlist/${item.playlist.id}") })
        is SectionItem.ArtistItem -> {
            val artist = item.artist
            MediaRow("${artist.name} radio", "With similar artists", artist.coverArt, round = true, onClick = {
                launch.progressivePicks("${artist.name} radio", { graph.radios.artistStart(artist) }) { graph.radios.artistMore(artist, it) }
            })
        }
        is SectionItem.Radio -> MediaRow(item.radio.name, "Sound radio", null, colors = Color(item.radio.colors.first) to Color(item.radio.colors.second), onClick = { nav.openMoodRadio(item.radio.id) })
        is SectionItem.GenreItem -> MediaRow(item.genre.name, "${item.genre.songCount} songs", null, colors = genreColors(item.genre.name), onClick = { nav.openGenreRadio(item.genre.name) })
    }
}

/** One entry in a full list, whatever the row holds. */
private sealed class SectionItem(val key: String, val name: String = "") {
    class AlbumItem(val album: Album, val subtitle: String) : SectionItem("album:${album.id}")
    class PlaylistItem(val playlist: app.siansa.data.Playlist) : SectionItem("playlist:${playlist.id}")
    class ArtistItem(val artist: app.siansa.data.Artist) : SectionItem("artist:${artist.id}")
    class Radio(val radio: app.siansa.mix.MoodRadio) : SectionItem("radio:${radio.id}")
    class GenreItem(val genre: app.siansa.data.Genre) : SectionItem("genre:${genre.name}", genre.name)
}

private suspend fun loadSection(graph: AppGraph, section: HomeSection): List<SectionItem> {
    val synced = graph.library.isSynced
    val list: List<SectionItem> = when (section) {
        HomeSection.Rediscover -> graph.insights.rediscover().map { SectionItem.AlbumItem(it.album, rediscoverSubtitle(it)) }
        HomeSection.Radios -> moodRadios.map { SectionItem.Radio(it) }
        HomeSection.Playlists -> graph.library.refreshPlaylists { graph.source.playlists() }
            .let(graph.playlists.removed::visible)
            .map { SectionItem.PlaylistItem(it) }
        HomeSection.RecentlyPlayed -> recentlyPlayedAlbums(graph, 100).map { SectionItem.AlbumItem(it, it.artist) }
        HomeSection.ArtistRadios -> artistRadioArtists(graph, 60).map { SectionItem.ArtistItem(it) }
        HomeSection.RecentlyAdded -> (
            if (synced) graph.library.albums("newest").take(200)
            else graph.source.albums("newest", 200, 0).distinctBy { it.name + "|" + it.artist }
            ).map { SectionItem.AlbumItem(it, it.artist) }
        // The genres Siansa Beats hears, as last saved, or the songs' tags: every one (Search's All genres opens this).
        // A to Z (the owner, 10 Nov): the row on Home and Search's tiles are most songs first, the page of all is a list to look up.
        // In the A to Z bar's own order (numbers and signs under #, accents with their letter), so each letter is in one run.
        HomeSection.Genres -> AlphabetIndex.sort(if (synced) graph.browseGenres(ALL_GENRES) else emptyList(), { it.name })
            .map { SectionItem.GenreItem(it) }
        HomeSection.RandomPicks -> (
            if (synced) graph.library.randomAlbums(100)
            else graph.source.albums("random", 100, 0).distinctBy { it.name + "|" + it.artist }
            ).map { SectionItem.AlbumItem(it, it.artist) }
    }
    // The same album can come back twice from the server; keys in a grid must be unique.
    return list.distinctBy { it.key }.also { AppLog.d("Home", "Full list ${section.id}: ${it.size} items") }
}

@Composable
private fun SectionTile(item: SectionItem, nav: NavController, launch: PlayLauncher, graph: AppGraph) {
    val fill = Modifier.fillMaxWidth()
    when (item) {
        is SectionItem.AlbumItem -> AlbumTile(item.album.name, item.subtitle, item.album.coverArt, { nav.navigate("album/${item.album.id}") }, fill)
        is SectionItem.PlaylistItem -> {
            val p = item.playlist
            AlbumTile(
                name = p.name,
                subtitle = playlistSubtitle(p),
                coverArt = p.coverArt,
                onClick = { nav.navigate("playlist/${p.id}") },
                modifier = fill,
            )
        }
        is SectionItem.ArtistItem -> {
            val artist = item.artist
            AlbumTile(
                name = "${artist.name} radio",
                subtitle = "With similar artists",
                coverArt = artist.coverArt,
                onClick = { launch.progressivePicks("${artist.name} radio", { graph.radios.artistStart(artist) }) { graph.radios.artistMore(artist, it) } },
                modifier = fill,
                round = true,
            )
        }
        is SectionItem.Radio -> BoxWithConstraints(fill.padding(Space.Small)) {
            GradientTile(
                title = item.radio.name,
                colors = Color(item.radio.colors.first) to Color(item.radio.colors.second),
                size = maxWidth,
                onClick = { nav.openMoodRadio(item.radio.id) },
            )
        }
        is SectionItem.GenreItem -> BoxWithConstraints(fill.padding(Space.Small)) {
            GradientTile(
                title = item.genre.name,
                colors = genreColors(item.genre.name),
                size = maxWidth,
                onClick = { nav.openGenreRadio(item.genre.name) },
            )
        }
    }
}

/**
 * The [items] whose name has what's typed ([find]) in it, in the order they came: every word typed, in any order,
 * whatever the capitals, accents or punctuation ("hip hop" finds "Hip-Hop"). Nothing typed: all of them.
 */
internal fun <T> foundByName(items: List<T>, find: String, name: (T) -> String): List<T> {
    val terms = app.siansa.data.normalizeKey(find).split(' ').filter { it.isNotEmpty() }
    if (terms.isEmpty()) return items
    return items.filter { app.siansa.data.searchMatches(app.siansa.data.normalizeKey(name(it)), terms) }
}
