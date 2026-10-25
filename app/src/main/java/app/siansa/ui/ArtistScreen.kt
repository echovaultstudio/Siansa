package app.siansa.ui

import app.siansa.data.versionLabels

import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.siansa.data.Album
import app.siansa.data.Artist
import app.siansa.data.SimilarArtist
import app.siansa.mix.SimilarPlugin
import app.siansa.data.Song
import app.siansa.data.category
import app.siansa.data.isTopSongsFresh
import app.siansa.data.quickTopSongs
import app.siansa.log.AppLog
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

private data class ArtistPage(val artist: Artist, val albums: List<Album>, val songs: List<Song>)

private val HERO = 380.dp

/**
 * An artist with what Navidrome knows about them: photo, biography, top songs, discography grouped by
 * release type and similar artists. The photo fills the top and blends away behind the page as it scrolls.
 */
@Composable
fun ArtistScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val launch = rememberPlayLauncher()
    val page by loadState(id) {
        val local = graph.library.artist(id)
        if (local != null) {
            ArtistPage(local, graph.library.albumsOf(local), graph.library.songsOf(local))
        } else {
            val remote = graph.source.artist(id)
            val albums = graph.library.keepSyncedAlbums(remote.albums, "Artist page")
            ArtistPage(remote.artist, albums.sortedByDescending { it.year ?: 0 }, emptyList())
        }
    }
    val info by loadState("info-$id") { graph.source.artistInfo(id) }
    // Artists that sound alike, from the Siansa plugin (issue #223); nothing when it isn't there.
    val soundsAlike by loadState("sounds-alike-$id") {
        val name = snapshotFlow { page.valueOrNull() }.filterNotNull().first().artist.name
        graph.similar.artists(name)
    }
    // Popular songs: the saved list shows at once; Navidrome is asked again only when it's a few days old.
    // Started with the page, not after it, so the request runs alongside the rest. Whatever shows first stays while
    // the page is open (issue #440: the list switching looked clunky); a newer list is saved for the next visit.
    val top by produceState<List<Song>?>(null, id) {
        try {
            val cached = graph.topSongs.cached(id)
            if (cached != null) value = cached.songs
            if (cached != null && isTopSongsFresh(cached, System.currentTimeMillis())) return@produceState
            val loaded = snapshotFlow { page.valueOrNull() }.filterNotNull().first()
            // Nothing saved yet (issue #398): the artist's songs you've played most, straight away, instead of
            // waiting the second or two Navidrome takes to ask Last.fm. Its list replaces them when it comes.
            if (value == null && loaded.songs.isNotEmpty()) {
                val plays = graph.history.all().groupingBy { it.id }.eachCount()
                value = quickTopSongs(loaded.songs, plays)
                AppLog.d("Artist", "Popular songs: none saved, showing ${value?.size} of the artist's own first")
            }
            value = keepFirstShown(value, graph.topSongs.refresh(loaded.artist, loaded.songs))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Artist", "Popular songs failed: ${e.javaClass.simpleName}")
            if (value == null) value = emptyList()
        }
    }
    val gridState = rememberLazyGridState()
    val scrolled by remember {
        derivedStateOf { if (gridState.firstVisibleItemIndex == 0) gridState.firstVisibleItemScrollOffset.toFloat() else 100_000f }
    }
    val p = page.valueOrNull()
    val artistInfo = info.valueOrNull()

    HeroPage(
        title = p?.artist?.name ?: "Artist",
        heroHeight = HERO,
        scrolledPx = scrolled,
        onBack = { nav.popBackStack() },
        color = rememberCoverColor(p?.artist?.coverArt ?: p?.albums?.firstOrNull()?.coverArt),
        overlay = {
            // A soft dark band behind the name, so it reads on any photo, and on a pale placeholder.
            Box(
                Modifier.fillMaxWidth().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)),
                    ),
                ),
            ) {
                Text(
                    p?.artist?.name.orEmpty(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = androidx.compose.ui.graphics.Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Space.Page, end = Space.Page, top = Space.Section, bottom = Space.Medium),
                )
            }
        },
        // A heart to make the artist a favourite, in Navidrome too (issue #397).
        actions = { p?.artist?.let { FavouriteArtistButton(it) } },
        image = { modifier ->
            // Navidrome's artist image through its cover endpoint (ND_ARTISTARTPRIORITY), cached and downloaded
            // like every cover (issue #206).
            Cover(p?.artist?.coverArt ?: "ar-$id", LARGE_COVER, modifier, RectangleShape)
        },
    ) { cardModifier ->
        if (p == null) {
            Box(Modifier.statusBarsPadding().padding(top = HERO)) { LoadBox(page) { } }
            return@HeroPage
        }
        val others by loadState("appears-$id") { graph.library.appearsOn(p.artist) }
        val groups = p.albums.groupBy { it.category }.toList().sortedBy { it.first.ordinal }
        // An album in more than one library shows once for each, with the library's name (issue #376).
        val folderNames by loadState("folder-names") { graph.library.folderNames() }
        val versions = versionLabels(p.albums, folderNames.valueOrNull().orEmpty())
        fun albumLine(album: Album) = listOfNotNull(album.year?.toString(), versions[album.id]).joinToString(" · ")

        // Release-type chips (Albums, Singles, DJ-mixes…): one type at a time, only while the page is open.
        var typeName by rememberReleaseTypeFilter()
        val types = releaseTypeChoices(p.albums)
        val type = chosenReleaseType(typeName, types)
        val shownGroups = onlyReleaseType(groups, type)
        var releasesGrid by rememberGridMode("artist")
        var topExpanded by rememberSaveable(id) { mutableStateOf(false) }

        LazyVerticalGrid(
            state = gridState,
            // Three across, or one release per row in the list (issue #289).
            columns = GridCells.Fixed(GRID_COLUMNS),
            contentPadding = PaddingValues(bottom = bottomBarPadding(Space.Section)),
            modifier = Modifier.fillMaxSize().then(cardModifier),
        ) {
            full { HeroSpacer(HERO) }
            full {
                Column(Modifier.padding(horizontal = Space.Page, vertical = Space.Medium)) {
                    Text(
                        listOfNotNull(
                            "${p.albums.size} ${if (p.albums.size == 1) "release" else "releases"}",
                            if (p.songs.isNotEmpty()) "${p.songs.size} songs" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.Medium))
                    PlayButtonRow {
                        OutlinedButton(onClick = {
                            launch.progressive("${p.artist.name} radio", { graph.radios.artistStart(p.artist) }) {
                                graph.radios.artistMore(p.artist, it)
                            }
                        }) {
                            Icon(Icons.Rounded.Radio, null)
                            Spacer(Modifier.width(Space.Tight))
                            Text("Radio")
                        }
                        Spacer(Modifier.weight(1f))
                        if (p.songs.isNotEmpty()) {
                            IconButton(onClick = { graph.player.play(p.songs, shuffle = true) }) {
                                Icon(Icons.Rounded.Shuffle, "Shuffle", tint = Accent, modifier = Modifier.size(30.dp))
                            }
                            Spacer(Modifier.width(Space.Small))
                            BigPlayButton(enabled = true) { graph.player.play(p.songs) }
                        }
                    }
                }
            }

            val topSongs = top
            if (topSongs == null) {
                // Keeps the section's place while the first answer comes in, so the page doesn't jump.
                full { SectionTitle("Popular") }
                items(TOP_ROWS_SHORT, key = { "top-wait-$it" }, span = { GridItemSpan(maxLineSpan) }) { SongRowPlaceholder() }
            } else if (topSongs.isNotEmpty()) {
                full { SectionTitle("Popular") }
                // Three, and "Show more" for up to six (issue #328).
                itemsIndexed(topSongs.take(topRowsShown(topSongs.size, topExpanded)), key = { i, s -> "top-$i-${s.id}" }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { index, song ->
                    // Fades when Navidrome's list replaces the first one (issue #398), rather than jumping.
                    Box(Modifier.animateItem(placementSpec = null)) {
                        SongRow(song = song, onClick = { graph.player.play(topSongs, index) }, leading = "${index + 1}")
                    }
                }
                if (topSongs.size > TOP_ROWS_SHORT) {
                    full {
                        TextButton(
                            onClick = { topExpanded = !topExpanded },
                            modifier = Modifier.padding(start = Space.Small).testTag("topSongsMore"),
                        ) { Text(if (topExpanded) "Show less" else "Show more") }
                    }
                }
            }

            if (types.isNotEmpty()) {
                full { ReleaseTypeRow(types, type) { typeName = it?.name } }
            }

            // Discography by release type: Albums, EPs, Singles, Live, Compilations, DJ-mixes…
            shownGroups.forEachIndexed { i, (category, albums) ->
                full {
                    Row(verticalAlignment = Alignment.Bottom) {
                        SectionTitle(
                            if (albums.size == 1) category.single else category.plural,
                            Modifier.weight(1f),
                            top = if (i == 0 && types.isNotEmpty()) CHIPS_GAP else SECTION_GAP,
                        )
                        // The grid / list switch beside the first heading (issue #289).
                        if (i == 0) GridListToggle(releasesGrid, onToggle = { releasesGrid = !releasesGrid }, modifier = Modifier.padding(end = Space.Tight))
                    }
                }
                items(albums, key = { "album-${it.id}" }, span = { if (releasesGrid) GridItemSpan(1) else GridItemSpan(maxLineSpan) }) { album ->
                    if (releasesGrid) {
                        AlbumTile(
                            name = album.name,
                            subtitle = albumLine(album),
                            coverArt = album.coverArt,
                            onClick = { nav.navigate("album/${album.id}") },
                            modifier = Modifier.padding(horizontal = Space.Tight),
                        )
                    } else {
                        MediaRow(album.name, albumLine(album), album.coverArt, onClick = { nav.navigate("album/${album.id}") })
                    }
                }
            }

            // Albums by others they're credited on: a row here, all of them on their own page.
            val appearsOn = others.valueOrNull().orEmpty()
            if (appearsOn.isNotEmpty()) {
                full { AppearsOnTitle { nav.navigate("artist/${p.artist.id}/appears-on") } }
                full {
                    LazyRow(contentPadding = PaddingValues(horizontal = Space.Medium), horizontalArrangement = Arrangement.spacedBy(Space.Tight)) {
                        items(appearsOn.take(APPEARS_ON_ROW), key = { "appears-${it.id}" }) { album ->
                            AlbumTile(
                                name = album.name,
                                subtitle = album.artist,
                                coverArt = album.coverArt,
                                onClick = { nav.navigate("album/${album.id}") },
                                modifier = Modifier.width(150.dp),
                            )
                        }
                    }
                }
            }

            artistInfo?.biography?.let { bio -> full { Biography(bio) } }

            val similar = SimilarPlugin.mergeArtists(p.artist.name, soundsAlike.valueOrNull().orEmpty(), artistInfo?.similar.orEmpty())
            if (similar.isNotEmpty()) {
                full { SectionTitle("Fans also like") }
                full { SimilarRow(similar, nav) }
            }
        }
    }
}

private const val APPEARS_ON_ROW = 20

/** "Appears on" with an arrow; tapping it opens every album the artist appears on. */
@Composable
private fun AppearsOnTitle(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(end = Space.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle("Appears on", Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            "Show all",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.Medium),
        )
    }
}

/** Every album the artist appears on without being its album artist, newest first. */
@Composable
fun AppearsOnScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val page by loadState(id) {
        val artist = graph.library.artist(id)
        artist to (artist?.let { graph.library.appearsOn(it) }.orEmpty())
    }
    val loaded = page.valueOrNull()
    Screen(title = loaded?.first?.name?.let { "$it appears on" } ?: "Appears on", nav = nav) { padding ->
        LoadBox(page, Modifier.padding(padding)) { (_, albums) ->
          Box {
            if (albums.isEmpty()) {
                Text(
                    "No albums by other artists yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Space.Page),
                )
            } else {
                // A grid of three or a list (issue #289).
                AlbumsGridOrList(albums, page = "appears-on", onOpen = { nav.navigate("album/${it.id}") }, modifier = Modifier.fillMaxSize())
            }
          }
        }
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** The popular songs to show: the ones already showing stay (issue #440); [fresh] only fills an empty list. */
internal fun keepFirstShown(showing: List<Song>?, fresh: List<Song>): List<Song> =
    if (!showing.isNullOrEmpty()) showing else fresh

/** Popular songs shown on an artist's page, and with Show more (issue #328). */
private const val TOP_ROWS_SHORT = 3
private const val TOP_ROWS_MORE = 6

/** How many popular songs show: three, or up to six once Show more is tapped. */
internal fun topRowsShown(available: Int, expanded: Boolean): Int = minOf(available, if (expanded) TOP_ROWS_MORE else TOP_ROWS_SHORT)

/** A grey stand-in the size of a song row, shown while popular songs load. */
@Composable
private fun SongRowPlaceholder() {
    val shade = MaterialTheme.colorScheme.surfaceVariant
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(start = Space.Page, end = Space.Page),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(Space.Large))
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(shade))
        Spacer(Modifier.width(Space.Medium))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shade))
            Spacer(Modifier.height(Space.Small))
            Box(Modifier.fillMaxWidth(0.4f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(shade))
        }
    }
}

@Composable
private fun Biography(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = Space.Page).animateContentSize()) {
        Text(
            "About",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = Space.Section, bottom = Space.Medium),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { expanded = !expanded },
        )
        if (text.length > 240) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Read more") }
        }
    }
}

@Composable
private fun SimilarRow(similar: List<SimilarArtist>, nav: NavController) {
    val graph = LocalGraph.current
    // Prefer the artist as synced on the phone; Navidrome only lists artists you have.
    val matched by loadState(similar) {
        similar.map { a -> a to (a.id?.let { graph.library.artist(it) } ?: graph.library.artistByName(a.name)) }
    }
    LazyRow(contentPadding = PaddingValues(horizontal = Space.Medium), horizontalArrangement = Arrangement.spacedBy(Space.Tight)) {
        items(matched.valueOrNull() ?: similar.map { it to null }, key = { it.first.id ?: it.first.name }) { (artist, local) ->
            val target = local?.id ?: artist.id
            Column(
                Modifier
                    .width(110.dp)
                    .clickable(enabled = target != null) { target?.let { nav.navigate("artist/$it") } }
                    .padding(Space.Tight),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // In the library: Navidrome's artist image through its cover endpoint (issue #206). Not in the
                // library: only the picture the similar-artists list links to.
                val coverId = local?.let { it.coverArt ?: "ar-${it.id}" } ?: artist.id?.let { artist.coverArt ?: "ar-$it" }
                val photo = if (coverId == null) artist.imageUrl else null
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                } else {
                    Cover(coverId, 150, Modifier.size(96.dp), CircleShape)
                }
                Spacer(Modifier.height(Space.Tight))
                Text(artist.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
