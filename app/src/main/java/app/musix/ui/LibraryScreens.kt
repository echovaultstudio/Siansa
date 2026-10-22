package app.musix.ui

import app.musix.data.isFavouriteAlbum
import app.musix.data.Album
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.LocalContentColor
import app.musix.data.extraArtists
import app.musix.data.normalizeKey
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.musix.AppGraph
import app.musix.data.AlbumCategory
import app.musix.data.AlbumDetail
import app.musix.data.category
import app.musix.data.Playlist
import app.musix.data.PlaylistDetail
import app.musix.data.LocalPlaylists
import app.musix.data.PlaylistEditor
import app.musix.data.toPlaylist
import app.musix.data.ListSort
import app.musix.data.Song
import app.musix.data.SongDatesCache
import app.musix.data.isoMillis
import app.musix.data.sortSongs
import app.musix.offline.OfflineItem
import app.musix.offline.OfflineKind
import app.musix.offline.likedOfflineItem
import app.musix.offline.offlineItem
import app.musix.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Songs with a heart, newest first as Navidrome lists them. */
@Composable
fun LikedScreen(nav: NavController) {
    val graph = LocalGraph.current
    val changes by graph.favorites.changes.collectAsStateWithLifecycle()
    // Navidrome is asked again once it has saved a change, not only when the heart is tapped (issue #250).
    val saved by graph.favorites.saved.collectAsStateWithLifecycle()
    var sort by remember { mutableStateOf(graph.listSort.get("liked")) }
    val heard by loadState("heard-at" to saved) { lastHeard(graph) }
    val loaded by loadState(changes to saved) {
        runCatching { graph.subsonic.starredSongs() }
            .map { list ->
                val local = graph.library.songs(list.map { it.id }).associateBy { it.id }
                graph.library.keepSyncedSongs(list.map { local[it.id] ?: it }, "Liked songs")
            }
            .getOrElse { if (graph.library.isSynced) graph.library.starredSongs() else throw it }
            // Songs liked just now show at the top even before Navidrome lists them; unliked ones go.
            .let { graph.favorites.mergeLiked(it) }
            // Newest liked first; songs liked just now (no date from Navidrome yet) at the very top.
            .sortedByDescending { isoMillis(SongDatesCache[it.id]?.starred) ?: Long.MAX_VALUE }
    }
    val songs = when (val l = loaded) {
        is Load.Ok -> Load.Ok(sortSongs(l.value, sort, { it }, { SongDatesCache[it] }, { heard.valueOrNull()?.get(it) }))
        else -> l
    }
    val heart = Color(0xFF4B2BD6) to Color(0xFF8FD3C8)
    CollectionPage(
        nav = nav,
        title = "Liked songs",
        color = lerp(heart.first, Color.Black, 0.2f),
        songs = songs.valueOrNull(),
        error = (songs as? Load.Err)?.message,
        cover = { m ->
            Box(m.clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(heart.first, heart.second))), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(72.dp))
            }
        },
        header = {
            CollectionByline(null, null, null, "${songs.valueOrNull()?.size ?: 0} songs")
            SortChips(sort, defaultLabel = "Recently liked") { sort = it; graph.listSort.set("liked", it) }
        },
        offline = remember { likedOfflineItem() },
    )
}

/** A Navidrome playlist, with songs matched to the synced library; yours can be renamed, changed and deleted. */
@Composable
fun PlaylistScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val launcher = rememberPlayLauncher()
    val scope = rememberCoroutineScope()
    // Bumped after each change, to load the playlist again.
    var version by remember { mutableIntStateOf(0) }
    var removing by remember { mutableStateOf(false) }
    val onPhone = LocalPlaylists.isLocal(id)
    val detail by loadState(id to version) {
        val remote = if (onPhone) {
            val local = graph.localPlaylists.get(id) ?: throw IllegalStateException("This playlist isn't on this phone any more")
            PlaylistDetail(local.toPlaylist(), local.songs)
        } else try {
            // Kept on the phone while Navidrome's playlist list says it hasn't changed (issue #251); after a
            // change made here ([version]), read again.
            graph.playlistCache.get(id, force = version > 0)
        } catch (e: Exception) {
            // Without a connection, a downloaded playlist still opens with its songs on the phone.
            val key = OfflineItem.key(OfflineKind.PLAYLIST, id)
            val item = graph.downloads.list.value.items.firstOrNull { it.key == key } ?: throw e
            PlaylistDetail(Playlist(id, item.name, coverArt = item.cover, owner = item.subtitle), graph.downloads.songsOf(key).orEmpty())
        }
        val local = graph.library.songs(remote.songs.map { it.id }).associateBy { it.id }
        remote.copy(songs = remote.songs.map { local[it.id] ?: it })
    }
    // While it loads again after a change, the page keeps showing the playlist as it was.
    var last by remember { mutableStateOf<PlaylistDetail?>(null) }
    LaunchedEffect(detail) { detail.valueOrNull()?.let { last = it } }
    val d = detail.valueOrNull() ?: last
    var sort by remember { mutableStateOf(graph.listSort.get("playlist")) }
    val heard by loadState("heard-at-playlist") { lastHeard(graph) }
    // The playlist's positions in the order shown, so "Remove" still removes the right one when sorted.
    val order = remember(d, sort, heard) {
        d?.let { pl -> sortSongs(pl.songs.indices.toList(), sort, { pl.songs[it] }, { SongDatesCache[it] }, { heard.valueOrNull()?.get(it) }) }
    }
    val editable = d != null && PlaylistEditor.canEdit(d.playlist, graph.settings.current.navidromeUser)
    val removeFromPlaylist: @Composable (Int, Song, () -> Unit) -> Unit = { index, song, close ->
        DropdownMenuItem(text = { Text("Remove from this playlist") }, onClick = {
            close()
            // Positions are only right for the playlist as Navidrome has it now, not while it loads again.
            val playlist = detail.valueOrNull()?.playlist
            if (playlist == null || removing) launcher.notify("One moment, the playlist is still loading")
            else {
                removing = true
                scope.launch {
                    graph.playlists.remove(playlist.id, order?.getOrNull(index) ?: index)
                        .onSuccess { launcher.notify("Removed ${song.title}"); version++ }
                        .onFailure { launcher.notify(it.message?.takeIf { m -> m.isNotBlank() } ?: "Couldn't remove it") }
                    removing = false
                }
            }
        })
    }
    CollectionPage(
        nav = nav,
        title = d?.playlist?.name ?: "Playlist",
        color = rememberCoverColor(d?.playlist?.coverArt),
        songs = d?.let { pl -> order?.map { pl.songs[it] } ?: pl.songs },
        error = (detail as? Load.Err)?.message,
        cover = { m -> Cover(d?.playlist?.coverArt, LARGE_COVER, m, RoundedCornerShape(6.dp)) },
        header = {
            d?.playlist?.comment?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Space.Small))
            }
            CollectionByline(
                if (onPhone) "On this phone" else d?.playlist?.owner,
                null,
                null,
                if (d == null) "" else "Playlist · ${d.songs.size} songs · ${formatDuration(d.songs.sumOf { it.durationSec.toLong() })}",
            )
            SortChips(sort, defaultLabel = "Playlist order") { sort = it; graph.listSort.set("playlist", it) }
        },
        // A phone playlist's songs download one by one (from their ⋮ menu); Downloads keeps Navidrome's playlists.
        offline = if (onPhone) null else d?.playlist?.offlineItem(),
        actions = {
            if (d != null) {
                PlaylistMenuButton(d.playlist, editable, notify = launcher::notify, onChanged = { version++ }, onDeleted = { nav.popBackStack() }, onMoved = { made ->
                    nav.popBackStack()
                    made?.let { nav.navigate("playlist/${it.id}") }
                })
            }
        },
        songMenu = if (editable) removeFromPlaylist else null,
    )
}

@Composable
fun SyncPrompt(nav: NavController) {
    val graph = LocalGraph.current
    Column(
        Modifier.fillMaxWidth().padding(Space.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.Medium),
    ) {
        Text("Your library isn't on the phone yet", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            "Sync it once to browse albums and artists. You choose which Navidrome libraries to include (Settings → Library sync).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = {
            graph.sync.start()
            nav.navigate("sync")
        }) {
            Icon(Icons.Rounded.Sync, null)
            Spacer(Modifier.width(Space.Small))
            Text("Sync library")
        }
    }
}

/**
 * An album from the synced library (all merged copies together), or straight from Navidrome before a
 * sync, in the Spotify layout with the cover's colour behind the header.
 */
@Composable
fun AlbumScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    // An album artist that's several artists in one name offers each of them (issue #359).
    val openArtists = rememberArtistsOpener()
    val detail by loadState(id) {
        val album = graph.library.album(id)
        val songs = album?.let { graph.library.songsOf(it) }.orEmpty()
        if (album != null && songs.isNotEmpty()) AlbumDetail(album, songs) else graph.subsonic.album(id)
    }
    val d = detail.valueOrNull()
    val artistCover by loadState(d?.album?.artistId) { d?.album?.artistId?.let { graph.library.artist(it)?.coverArt } }
    // Disc titles aren't kept in the synced library: for an album with several discs, ask Navidrome.
    // Without them (offline, older server) the discs are simply headed "Disc 1", "Disc 2"…
    val discTitles by loadState(d) { d?.let { discTitlesOf(graph, it) }.orEmpty() }
    // Library artists by name, so a name like "Chase & Status" isn't split into two artists.
    val knownArtists by loadState("artist-names") { graph.library.artists().map { normalizeKey(it.name) }.toSet() }
    CollectionPage(
        nav = nav,
        title = d?.album?.name ?: "Album",
        color = rememberCoverColor(d?.album?.coverArt),
        songs = d?.songs,
        error = (detail as? Load.Err)?.message,
        numbered = true,
        discTitles = discTitles.valueOrNull() ?: d?.discTitles.orEmpty(),
        showCovers = false,
        // Only artists beyond the album's own, as Navidrome's artist tags list them.
        subtitleOf = d?.album?.let { album ->
            val known = knownArtists.valueOrNull().orEmpty()
            val subtitle: (Song) -> String? = { song -> extraArtists(song, album.artistId, album.artist) { normalizeKey(it) in known } }
            subtitle
        },
        cover = { m ->
            BigCover(d?.album?.coverArt, d?.album?.coverArt ?: d?.album?.id, m, RoundedCornerShape(6.dp))
        },
        header = {
            if (d != null) {
                CollectionByline(
                    d.album.artist,
                    artistCover.valueOrNull(),
                    d.album.artistId?.let { artistId -> { openArtists { artistsNamedBy(graph, artistId, d.album.artist) } } },
                    listOfNotNull(
                        d.album.category.single,
                        d.album.year?.toString(),
                        d.album.genre,
                        "${d.songs.size} songs",
                        formatDuration(d.songs.sumOf { it.durationSec.toLong() }),
                    ).joinToString(" · "),
                )
            }
        },
        offline = d?.album?.offlineItem(),
        // A heart for favourite albums, starred in Navidrome (issue #275).
        actions = { d?.album?.let { FavouriteAlbumButton(it) } },
        // "Find similar albums" beside Shuffle (issue #228), through any of its songs; needs AudioMuse.
        onSimilar = d?.songs?.firstOrNull()?.id?.takeIf { graph.settings.current.audioMuseReady }?.let { id -> { nav.openSimilarAlbums(id) } },
    )
}

private suspend fun discTitlesOf(graph: AppGraph, detail: AlbumDetail): Map<Int, String> {
    if (detail.discTitles.isNotEmpty()) return detail.discTitles
    if (detail.songs.map { it.disc ?: 1 }.distinct().size < 2) return emptyMap()
    for (id in detail.album.allIds) {
        val titles = try {
            graph.subsonic.album(id).discTitles
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Album", "Disc titles not loaded: ${e.message}")
            return emptyMap()
        }
        if (titles.isNotEmpty()) {
            AppLog.d("Album", "${titles.size} disc titles")
            return titles
        }
    }
    return emptyMap()
}

@Composable
fun PlayButtons(songs: List<Song>) {
    val graph = LocalGraph.current
    Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        Button(onClick = { graph.player.play(songs) }, enabled = songs.isNotEmpty()) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(Space.Tight))
            Text("Play")
        }
        FilledTonalButton(onClick = { graph.player.play(songs, shuffle = true) }, enabled = songs.isNotEmpty()) {
            Icon(Icons.Rounded.Shuffle, null)
            Spacer(Modifier.width(Space.Tight))
            Text("Shuffle")
        }
    }
}

@Composable
fun MixScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val mix by loadState(id) { graph.mixes.get(id) ?: throw IllegalStateException("This mix is no longer saved") }
    val m = mix.valueOrNull()
    CollectionPage(
        nav = nav,
        title = m?.title ?: "Mix",
        color = lerp(tileColors(m?.selectorId ?: "mix").first, Color.Black, 0.25f),
        songs = m?.songs,
        error = (mix as? Load.Err)?.message,
        cover = { mod -> if (m != null) CoverMosaic(m, mod) else Box(mod) },
        header = {
            if (m != null) {
                if (m.description.isNotBlank()) {
                    Text(m.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Space.Small))
                }
                CollectionByline(null, null, null, "Made for you · ${m.songs.size} songs · sound search: ${m.query}")
            }
        },
    )
}

/** When each song was last heard in Musix, from the play history (issue #257). */
private suspend fun lastHeard(graph: app.musix.AppGraph): Map<String, Long> {
    val at = HashMap<String, Long>()
    graph.history.all().forEach { e -> if ((at[e.id] ?: 0L) < e.at) at[e.id] = e.at }
    return at
}

/** "Recently liked · Last played · Date added · Title · Artist" as chips under a list's title (issue #257). */
@Composable
internal fun SortChips(selected: ListSort, defaultLabel: String, onPick: (ListSort) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = Space.Small).testTag("sortChips"),
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        ListSort.entries.forEach { sort ->
            FilterChip(
                selected = selected == sort,
                onClick = { onPick(sort) },
                label = { Text(if (sort == ListSort.DEFAULT) defaultLabel else sort.label) },
                modifier = Modifier.testTag("sort-${sort.name}"),
            )
        }
    }
}

/** The heart on an album page (issue #275): marks it as a favourite, in Navidrome too. */
@Composable
internal fun FavouriteAlbumButton(album: Album) {
    val graph = LocalGraph.current
    val launcher = rememberPlayLauncher()
    val ids by graph.favouriteAlbums.ids.collectAsStateWithLifecycle()
    val on = isFavouriteAlbum(album, ids)
    IconButton(onClick = { graph.favouriteAlbums.toggle(album) { launcher.notify(it) } }, modifier = Modifier.testTag("favouriteAlbum")) {
        Icon(
            if (on) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (on) "Remove from favourite albums" else "Add to favourite albums",
            tint = if (on) Accent else LocalContentColor.current,
        )
    }
}

/**
 * All favourite albums (issue #289), from the Favourite albums card in Your Library › Favourites: those in the
 * synced library with their merged copies, the rest as Navidrome sent them. As a grid or a list.
 */
@Composable
fun FavouriteAlbumsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val ids by graph.favouriteAlbums.ids.collectAsStateWithLifecycle()
    val albums by loadState("favourite-albums-${ids.hashCode()}") {
        val fresh = graph.favouriteAlbums.refresh()
        // Only those still favourites (a heart taken off on an album page shows at once), the synced copy when there is one.
        fresh.filter { isFavouriteAlbum(it, ids) }
            .map { a -> graph.library.album(a.id) ?: a }
            .distinctBy { it.id }
            .sortedBy { normalizeKey(it.artist) + "|" + normalizeKey(it.name) }
    }
    Screen(title = "Favourite albums", nav = nav) { padding ->
        LoadBox(albums, Modifier.padding(padding)) { list ->
            if (list.isEmpty()) {
                Text(
                    "No favourite albums yet. Tap the heart at the top of an album's page to add it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(padding).padding(Space.Page),
                )
            } else {
                AlbumsGridOrList(
                    list,
                    page = "favourite-albums",
                    onOpen = { nav.navigate("album/${it.id}") },
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    title = "${list.size} ${if (list.size == 1) "album" else "albums"}",
                )
            }
        }
    }
}
