package app.musix.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.musix.AppGraph
import app.musix.data.Album
import app.musix.log.AppLog
import app.musix.mix.Rediscovery
import app.musix.mix.moodRadios
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
    // as a list, like Spotify; the rest as a grid.
    var grid by rememberGridMode("section-${section.id}", default = section != HomeSection.Playlists)
    Screen(section.title, nav) { padding ->
        Box(Modifier.padding(padding)) {
            LoadBox(items) { list ->
                if (list.isEmpty()) {
                    Text("Nothing here yet.", Modifier.padding(Space.Page))
                } else {
                    LazyVerticalGrid(
                        columns = gridCells(grid),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = if (grid) Space.Small else 0.dp, end = if (grid) Space.Small else 0.dp, bottom = Space.Section),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) { GridListHeader("${list.size} ${if (list.size == 1) "item" else "items"}", grid) { grid = !grid } }
                        items(list, key = { it.key }) { item -> if (grid) SectionTile(item, nav, launch, graph) else SectionRow(item, nav, launch, graph) }
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
                launch.progressive("${artist.name} radio", { graph.radios.artistStart(artist) }) { graph.radios.artistMore(artist, it) }
            })
        }
        is SectionItem.Radio -> MediaRow(item.radio.name, "Sound radio", null, colors = Color(item.radio.colors.first) to Color(item.radio.colors.second), onClick = { nav.openMoodRadio(item.radio.id) })
        is SectionItem.GenreItem -> MediaRow(item.genre.name, "${item.genre.songCount} songs", null, colors = tileColors(item.genre.name), onClick = { nav.openGenreRadio(item.genre.name) })
    }
}

/** One entry in a full list, whatever the row holds. */
private sealed class SectionItem(val key: String) {
    class AlbumItem(val album: Album, val subtitle: String) : SectionItem("album:${album.id}")
    class PlaylistItem(val playlist: app.musix.data.Playlist) : SectionItem("playlist:${playlist.id}")
    class ArtistItem(val artist: app.musix.data.Artist) : SectionItem("artist:${artist.id}")
    class Radio(val radio: app.musix.mix.MoodRadio) : SectionItem("radio:${radio.id}")
    class GenreItem(val genre: app.musix.data.Genre) : SectionItem("genre:${genre.name}")
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
        HomeSection.Genres -> (if (synced) graph.library.genres(500) else emptyList()).map { SectionItem.GenreItem(it) }
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
                onClick = { launch.progressive("${artist.name} radio", { graph.radios.artistStart(artist) }) { graph.radios.artistMore(artist, it) } },
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
                colors = tileColors(item.genre.name),
                size = maxWidth,
                onClick = { nav.openGenreRadio(item.genre.name) },
            )
        }
    }
}
