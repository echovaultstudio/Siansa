package app.siansa.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.graphicsLayer
import app.siansa.data.isFavouriteAlbum
import app.siansa.data.Album
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.LocalContentColor
import app.siansa.data.extraArtists
import app.siansa.data.normalizeKey
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
import app.siansa.mix.MixGenerator
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
import app.siansa.AppGraph
import app.siansa.data.AlbumCategory
import app.siansa.data.AlbumDetail
import app.siansa.data.category
import app.siansa.net.AlbumInfo
import app.siansa.data.Playlist
import app.siansa.data.PlaylistDetail
import app.siansa.data.LocalPlaylists
import app.siansa.data.PlaylistEditor
import app.siansa.data.toPlaylist
import app.siansa.data.ListSort
import app.siansa.data.Song
import app.siansa.data.SongDatesCache
import app.siansa.data.isoMillis
import app.siansa.data.sortSongs
import app.siansa.offline.OfflineItem
import app.siansa.offline.OfflineKind
import app.siansa.offline.likedOfflineItem
import app.siansa.offline.offlineItem
import app.siansa.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import app.siansa.data.favouriteArtistList
import app.siansa.data.Artist
import app.siansa.data.PickUps

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
        runCatching { graph.source.starredSongs() }
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
        // The quick-scroll handle says the letter or month of the order shown; liked first by when (beta testers #24).
        scrollLabel = { _, song ->
            FastScroll.songLabel(sort, song, SongDatesCache[song.id], heard.valueOrNull()?.get(song.id))
                ?: FastScroll.monthLabel(isoMillis(SongDatesCache[song.id]?.starred) ?: 0L, "Just now")
        },
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
    // A server playlist opens at once from the copy on the phone, while the server is asked whether it changed
    // (beta #18: playlists the server rewrites every hour). [checking] turns the cog in the title bar.
    var checking by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    val detail by loadState(id to version) {
        val remote = if (onPhone) {
            val local = graph.localPlaylists.get(id) ?: throw IllegalStateException("This playlist isn't on this phone any more")
            PlaylistDetail(local.toPlaylist(), local.songs)
        } else try {
            // First the copy kept on the phone (issue #251), or the server when there's none. After the check
            // found a change, the copy it just kept; after a change made here ([version]), the server again.
            when {
                version == 0 || checked -> graph.playlistCache.kept(id) ?: graph.playlistCache.get(id, force = true)
                else -> graph.playlistCache.get(id, force = true)
            }
        } catch (e: Exception) {
            // Without a connection, a downloaded playlist still opens with its songs on the phone.
            val key = OfflineItem.key(OfflineKind.PLAYLIST, id)
            val item = graph.downloads.list.value.items.firstOrNull { it.key == key } ?: throw e
            PlaylistDetail(Playlist(id, item.name, coverArt = item.cover, owner = item.subtitle), graph.downloads.songsOf(key).orEmpty())
        }
        val local = graph.library.songs(remote.songs.map { it.id }).associateBy { it.id }
        remote.copy(songs = remote.songs.map { local[it.id] ?: it })
    }
    LaunchedEffect(id) {
        if (onPhone) return@LaunchedEffect
        val before = graph.playlistCache.kept(id) ?: return@LaunchedEffect // none kept: it was just read from the server
        checking = true
        val fresh = runCatching { graph.playlistCache.refresh(id) }.getOrNull()
        checking = false
        if (fresh != null && playlistChanged(before, fresh)) {
            AppLog.i("Playlist", "Playlist $id changed on the server: ${before.songs.size} → ${fresh.songs.size} songs")
            checked = true
            version++
        }
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
            if (checking) CheckingCog()
            if (d != null) {
                PlaylistMenuButton(d.playlist, editable, notify = launcher::notify, onChanged = { version++ }, onDeleted = { nav.popBackStack() }, onMoved = { made ->
                    nav.popBackStack()
                    made?.let { nav.navigate("playlist/${it.id}") }
                })
            }
        },
        songMenu = if (editable) removeFromPlaylist else null,
        scrollLabel = { _, song -> FastScroll.songLabel(sort, song, SongDatesCache[song.id], heard.valueOrNull()?.get(song.id)) },
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
            "Sync it once to browse albums and artists. You choose which libraries on your server to include (Settings → Library sync).",
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
        if (album != null && songs.isNotEmpty()) AlbumDetail(album, songs) else graph.source.album(id).also { ServerAlbums.put(id, it) }
    }
    val d = detail.valueOrNull()
    val artistCover by loadState(d?.album?.artistId) { d?.album?.artistId?.let { graph.library.artist(it)?.coverArt } }
    // Disc titles, the version, original year, labels and genres aren't kept in the synced library: ask the
    // server once (issue #504). Without them (offline, older server) discs are "Disc 1", "Disc 2"…
    val serverAlbum by loadState(d) { d?.let { serverAlbumOf(graph, it) } }
    val fromServer = serverAlbum.valueOrNull()
    // Library artists by name, so a name like "Chase & Status" isn't split into two artists.
    val knownArtists by loadState("artist-names") { graph.library.artists().map { normalizeKey(it.name) }.toSet() }
    // Where you stopped on an album you were listening to, from this phone's plays (issue #367). Not while
    // a song of it is the one playing: that one is marked already.
    val player by graph.player.state.collectAsStateWithLifecycle()
    val playingId = player.current?.id
    val pickUp by loadState(d to playingId) { d?.let { PickUps.find(it.songs, graph.history.all(), System.currentTimeMillis()) } }
    val pick = pickUp.valueOrNull()?.takeIf { d != null && d.songs.none { it.id == playingId } }
    CollectionPage(
        nav = nav,
        title = d?.album?.name ?: "Album",
        color = rememberCoverColor(d?.album?.coverArt),
        songs = d?.songs,
        error = (detail as? Load.Err)?.message,
        numbered = true,
        discTitles = fromServer?.discTitles ?: d?.discTitles.orEmpty(),
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
                    albumFacts(d.album, fromServer?.info ?: d.info, d.songs.size, formatDuration(d.songs.sumOf { it.durationSec.toLong() })),
                )
                if (pick != null) {
                    ContinueButton(PickUps.trackLabel(d.songs, pick.continueIndex)) {
                        AppLog.i("Album", "Continue from ${pick.continueIndex + 1} of ${d.songs.size}")
                        graph.player.play(d.songs, pick.continueIndex)
                    }
                }
            }
        },
        lastPlayed = pick?.let { it.lastIndex to "Last played · ${PickUps.whenLabel(it.at)}" },
        offline = d?.album?.offlineItem(),
        // A heart for favourite albums, starred in Navidrome (issue #275).
        actions = { d?.album?.let { FavouriteAlbumButton(it) } },
        // "Find similar albums" beside Shuffle (issue #228), through any of its songs; needs AudioMuse.
        onSimilar = d?.songs?.firstOrNull()?.id?.takeIf { graph.settings.current.audioMuseReady }?.let { id -> { nav.openSimilarAlbums(id) } },
    )
}

/**
 * The album as the server has it, for what the synced library doesn't keep: disc titles, its version, the year
 * it first came out, labels, every genre (issue #504). Asked once per album while the app runs; null offline
 * or when the server can't be reached, and the page shows what the library has.
 */
private suspend fun serverAlbumOf(graph: AppGraph, detail: AlbumDetail): AlbumDetail? {
    val id = detail.album.id
    ServerAlbums.get(id)?.let { return it }
    if (graph.connection.current.offline) return null
    return try {
        graph.source.album(id).also {
            ServerAlbums.put(id, it)
            AppLog.d("Album", "From the server: ${it.discTitles.size} disc titles, ${it.info}")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.w("Album", "Album details not loaded: ${e.message}")
        null
    }
}

/** Albums as the server last gave them, the most recent few. */
private object ServerAlbums {
    private const val KEEP = 40
    private val map = object : LinkedHashMap<String, AlbumDetail>(KEEP, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AlbumDetail>?) = size > KEEP
    }

    @Synchronized fun get(id: String): AlbumDetail? = map[id]

    @Synchronized fun put(id: String, detail: AlbumDetail) {
        map[id] = detail
    }
}

/**
 * The line under an album's artist (issue #504): its kind, version ("Deluxe edition"), year (with the year it
 * first came out when this is a later release), genres, label, songs and length.
 */
fun albumFacts(album: Album, info: AlbumInfo, songCount: Int, length: String): String {
    val year = when {
        info.originalYear != null && album.year != null && info.originalYear < album.year -> "${info.originalYear} · reissued ${album.year}"
        else -> (album.year ?: info.originalYear)?.toString()
    }
    val genres = info.genres.take(3).joinToString(", ").ifEmpty { album.genre.orEmpty() }
    return listOfNotNull(
        album.category.single,
        info.version,
        year,
        genres.takeIf { it.isNotEmpty() },
        info.labels.firstOrNull(),
        "$songCount songs",
        length,
    ).joinToString(" · ")
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
    // Bumped when On repeat changed on the server, to show it again (issue #495).
    var version by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    val mix by loadState(id to version) { graph.mixes.get(id) ?: throw IllegalStateException("This mix is no longer saved") }
    // Today's On repeat opens at once from the phone, then asks the server what you played most in the last 30
    // days, like a playlist (issue #495); the cog turns while it does.
    LaunchedEffect(id) {
        val shown = graph.mixes.get(id) ?: return@LaunchedEffect
        if (shown.selectorId != MixGenerator.Personal.REPEAT.id || shown.date != java.time.LocalDate.now().toString()) return@LaunchedEffect
        checking = true
        val fresh = runCatching { graph.generator.ensurePersonal(MixGenerator.Personal.REPEAT, force = true) }.getOrNull()
        checking = false
        if (fresh != null && fresh.songs.map { it.id } != shown.songs.map { it.id }) {
            AppLog.i("Mix", "On repeat changed: ${shown.songs.size} → ${fresh.songs.size} songs")
            version++
        }
    }
    val m = mix.valueOrNull()
    CollectionPage(
        nav = nav,
        title = m?.title ?: "Mix",
        color = lerp(tileColors(m?.selectorId ?: "mix").first, Color.Black, 0.25f),
        songs = m?.songs,
        error = (mix as? Load.Err)?.message,
        cover = { mod -> if (m != null) CoverMosaic(m, mod) else Box(mod) },
        saveAsPlaylist = true,
        actions = { if (checking) CheckingCog() },
        header = {
            if (m != null) {
                if (m.description.isNotBlank()) {
                    Text(m.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Space.Small))
                }
                // Mixes from your listening (On repeat) have no sound search to name.
                CollectionByline(null, null, null, "Made for you · ${m.songs.size} songs" + if (m.query.isNotBlank()) " · sound search: ${m.query}" else "")
            }
        },
    )
}

/** When each song was last heard in Siansa, from the play history (issue #257). */
private suspend fun lastHeard(graph: app.siansa.AppGraph): Map<String, Long> {
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
 * The heart at the top of an artist's page (issue #397): marks it as a favourite, in Navidrome too. White over
 * the artist's picture, like the back arrow beside it; the accent colour when on.
 */
@Composable
internal fun FavouriteArtistButton(artist: Artist) {
    val graph = LocalGraph.current
    val launcher = rememberPlayLauncher()
    val ids by graph.favouriteArtists.ids.collectAsStateWithLifecycle()
    val on = artist.id in ids
    IconButton(onClick = { graph.favouriteArtists.toggle(artist) { launcher.notify(it) } }, modifier = Modifier.testTag("favouriteArtist")) {
        Icon(
            if (on) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (on) "Remove from favourite artists" else "Add to favourite artists",
            tint = if (on) Accent else Color.White,
        )
    }
}

/**
 * All favourite artists (issue #397), from the Favourite artists card in Your Library › Favourites, with round
 * pictures, as a grid or a list.
 */
@Composable
fun FavouriteArtistsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val ids by graph.favouriteArtists.ids.collectAsStateWithLifecycle()
    val artists by loadState("favourite-artists-${ids.hashCode()}") {
        val fresh = graph.favouriteArtists.refresh()
        val local = fresh.mapNotNull { a -> graph.library.artist(a.id)?.let { a.id to it } }.toMap()
        favouriteArtistList(fresh, ids, local)
    }
    Screen(title = "Favourite artists", nav = nav) { padding ->
        LoadBox(artists, Modifier.padding(padding)) { list ->
            if (list.isEmpty()) {
                Text(
                    "No favourite artists yet. Tap the heart at the top of an artist's page to add them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Space.Page),
                )
            } else {
                ArtistsGridOrList(
                    list,
                    page = "favourite-artists",
                    onOpen = { nav.navigate("artist/${it.id}") },
                    modifier = Modifier.fillMaxSize(),
                    title = "${list.size} ${if (list.size == 1) "artist" else "artists"}",
                )
            }
        }
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
                    modifier = Modifier.padding(Space.Page),
                )
            } else {
                AlbumsGridOrList(
                    list,
                    page = "favourite-albums",
                    onOpen = { nav.navigate("album/${it.id}") },
                    modifier = Modifier.fillMaxSize(),
                    title = "${list.size} ${if (list.size == 1) "album" else "albums"}",
                )
            }
        }
    }
}

/** Whether a playlist read again differs from the copy shown: its songs, their order, or its name. */
internal fun playlistChanged(before: PlaylistDetail, now: PlaylistDetail): Boolean =
    before.songs.map { it.id } != now.songs.map { it.id } || before.playlist.name != now.playlist.name

/** A slowly turning cog in the title bar while the server is asked whether a playlist changed (beta #18). */
@Composable
internal fun CheckingCog() {
    val turn = rememberInfiniteTransition(label = "checking")
    val angle by turn.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = LinearEasing)),
        label = "cog",
    )
    Box(Modifier.size(48.dp).testTag("checkingPlaylist"), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.Settings, "Checking for changes",
            Modifier.size(22.dp).graphicsLayer { rotationZ = angle },
            tint = LocalContentColor.current.copy(alpha = 0.8f),
        )
    }
}
