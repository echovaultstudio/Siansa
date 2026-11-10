package app.siansa.ui

import app.siansa.data.versionLabels
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import app.siansa.data.AlbumCategory
import kotlinx.coroutines.launch

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
import androidx.compose.foundation.layout.offset
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
import app.siansa.data.splitOwnAlbums
import app.siansa.log.AppLog
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/** An artist's page: their own [albums], and [others] they appear on as the server lists them (beta testers #34). */
private data class ArtistPage(val artist: Artist, val albums: List<Album>, val songs: List<Song>, val others: List<Album> = emptyList())

/**
 * The page for artist [id]: from the synced library, or from the server for an artist who is no album artist
 * (featured, on a compilation). The server lists every album they take part in; only their own are releases,
 * the rest go to "Appears on" (beta testers #34).
 */
private suspend fun artistPage(graph: app.siansa.AppGraph, id: String): ArtistPage {
    val local = graph.library.artist(id)
    if (local != null) return ArtistPage(local, graph.library.albumsOf(local), graph.library.songsOf(local))
    val remote = graph.source.artist(id)
    val albums = graph.library.keepSyncedAlbums(remote.albums, "Artist page")
    val (own, others) = splitOwnAlbums(remote.artist, albums)
    AppLog.d("Artist", "${remote.artist.name} from the server: ${own.size} own albums, ${others.size} appears on")
    return ArtistPage(remote.artist, own.sortedByDescending { it.year ?: 0 }, emptyList(), others)
}

/** Every album [page]'s artist appears on: the server's and those the library's songs credit them on, newest first. */
private suspend fun appearsOnOf(graph: app.siansa.AppGraph, page: ArtistPage): List<Album> =
    (page.others + graph.library.appearsOn(page.artist))
        .distinctBy { it.id }
        .filterNot { album -> page.albums.any { it.id == album.id } }
        .sortedWith(compareByDescending<Album> { it.year ?: 0 }.thenBy { app.siansa.data.normalizeKey(it.name) })

private val HERO = 380.dp

/** The dark band at the foot of the artist's picture, behind the name at rest. */
private val HERO_NAME_BAND = 120.dp

/** The release-type tabs' place in the artist page's grid: after the picture's spacer and the buttons (issue #1029). */
private const val TABS_KEY = "release-type-tabs"
private const val TABS_INDEX = 2

/**
 * An artist with what Navidrome knows about them: photo, biography, top songs, discography grouped by
 * release type and similar artists. The photo fills the top and the page fades in over it as it scrolls; the
 * release types are tabs under the buttons, pinned under the title bar once scrolled to (issue #1029).
 */
@Composable
fun ArtistScreen(nav: NavController, id: String) {
    val graph = LocalGraph.current
    val launch = rememberPlayLauncher()
    val page by loadState(id) { artistPage(graph, id) }
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
    // The artist's picture, or their newest release's cover: the page's colour, and its buttons' with Dynamic colours on
    // pages (issue #1028).
    val pictureId = p?.artist?.coverArt ?: p?.albums?.firstOrNull()?.coverArt
    val tint = rememberCoverColor(pictureId)

    // Release types as tabs at the top (issue #1029): All, Albums, EPs, Singles, DJ-mixes… one at a time, only while
    // the page is open. Once scrolled up to the title bar, they stay pinned under it.
    var typeName by rememberReleaseTypeFilter()
    val types = remember(p) { p?.let { releaseTypeChoices(it.albums) }.orEmpty() }
    val type = chosenReleaseType(typeName, types)
    val density = LocalDensity.current
    val barPx = WindowInsets.statusBars.getTop(density) + with(density) { HERO_TITLE_BAR.toPx() }
    val tabsPinned by remember(types.isNotEmpty(), barPx) {
        derivedStateOf {
            if (types.isEmpty()) return@derivedStateOf false
            val tabs = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == TABS_KEY }
            if (tabs == null) gridState.firstVisibleItemIndex > TABS_INDEX else tabs.offset.y < barPx
        }
    }
    val scope = rememberCoroutineScope()
    fun choose(t: AlbumCategory?) {
        typeName = t?.name
        // Pinned: the tabs stay where they are and the chosen releases start right under them.
        if (tabsPinned) scope.launch { gridState.scrollToItem(TABS_INDEX); gridState.scrollBy(-barPx) }
    }

    // With Dynamic colours, only Play takes the picture's colour (the owner, 10 Nov): Radio, Shuffle and the heart stay
    // as they are, so the row doesn't shout.
    val playColours = rememberPageColours(pictureId, pageBackdrop(MaterialTheme.colorScheme.surface, tint), where = "Artist page")
    HeroPage(
        title = p?.artist?.name ?: "Artist",
        heroHeight = HERO,
        scrolledPx = scrolled,
        onBack = { nav.popBackStack() },
        color = tint,
        pinned = if (tabsPinned) ({ ReleaseTypeTabs(types, type, content = androidx.compose.ui.graphics.Color.White) { choose(it) } }) else null,
        overlay = {
            // A soft dark band at the foot of the picture, where the name sits at first (HeroPage draws the name, which
            // rides up with the page into the title bar), so it reads on any photo and on a pale placeholder.
            Box(
                Modifier.fillMaxWidth().height(HERO_NAME_BAND).background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)),
                    ),
                ),
            )
        },
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
        val others by loadState("appears-$id") { appearsOnOf(graph, p) }
        val groups = p.albums.groupBy { it.category }.toList().sortedBy { it.first.ordinal }
        // All of the page, or only the chosen type's releases.
        val everything = type == null
        // An album in more than one library shows once for each, with the library's name (issue #376).
        val folderNames by loadState("folder-names") { graph.library.folderNames() }
        val versions = versionLabels(p.albums, folderNames.valueOrNull().orEmpty())
        fun albumLine(album: Album) = listOfNotNull(album.year?.toString(), versions[album.id]).joinToString(" · ")

        val shownGroups = onlyReleaseType(groups, type)
        var releasesGrid by rememberGridMode("artist")
        var topExpanded by rememberSaveable(id) { mutableStateOf(false) }

        LazyVerticalGrid(
            state = gridState,
            // Three across, or one release per row in the list (issue #289).
            columns = GridCells.Fixed(gridColumns()),
            contentPadding = PaddingValues(bottom = bottomBarPadding(Space.Section)),
            modifier = Modifier.fillMaxSize().then(cardModifier),
        ) {
            full { HeroSpacer(HERO) }
            full {
                Column(Modifier.padding(horizontal = Space.Page, vertical = Space.Medium)) {
                    Text(
                        listOfNotNull(
                            // An artist who only appears on others' albums has no releases to count.
                            if (p.albums.isNotEmpty()) "${p.albums.size} ${if (p.albums.size == 1) "release" else "releases"}" else null,
                            if (p.songs.isNotEmpty()) "${p.songs.size} songs" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.Medium))
                    // The owner's layout (9 Nov): the heart on the left, in line with the text above; radio,
                    // shuffle and Play on the right, like an album's similar albums, shuffle and Play.
                    val playback = rememberListPlayback(p.songs)
                    ArtistButtons(
                        heart = { m -> FavouriteArtistButton(p.artist, m, MaterialTheme.colorScheme.onSurface, ICON_SIZE) },
                        onRadio = {
                            launch.progressivePicks("${p.artist.name} radio", { graph.radios.artistStart(p.artist) }) {
                                graph.radios.artistMore(p.artist, it)
                            }
                        },
                        onShuffle = if (p.songs.isNotEmpty()) ({ graph.player.play(p.songs, shuffle = true) }) else null,
                        playing = playback.playing,
                        onPlay = if (p.songs.isNotEmpty()) ({ playback.press { graph.player.play(p.songs) } }) else null,
                        playColours = playColours,
                    )
                }
            }

            if (types.isNotEmpty()) {
                // Hidden behind the pinned copy under the title bar once scrolled that far.
                item(key = TABS_KEY, span = { GridItemSpan(maxLineSpan) }) {
                    ReleaseTypeTabs(types, type, Modifier.alpha(if (tabsPinned) 0f else 1f)) { choose(it) }
                }
            }

            // Popular songs, on All only: a release type shows just its releases.
            val topSongs = top.takeIf { everything } ?: emptyList()
            if (everything && top == null) {
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

            // Discography by release type: Albums, EPs, Singles, Live, Compilations, DJ-mixes…
            shownGroups.forEachIndexed { i, (category, albums) ->
                full {
                    Row(verticalAlignment = Alignment.Bottom) {
                        SectionTitle(
                            if (albums.size == 1) category.single else category.plural,
                            Modifier.weight(1f),
                            top = if (i == 0 && !everything) TABS_GAP else SECTION_GAP,
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
            val appearsOn = others.valueOrNull().orEmpty().takeIf { everything }.orEmpty()
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

            if (everything) artistInfo?.biography?.let { bio -> full { Biography(bio) } }

            val similar = if (!everything) emptyList() else SimilarPlugin.mergeArtists(p.artist.name, soundsAlike.valueOrNull().orEmpty(), artistInfo?.similar.orEmpty())
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
        val artist = artistPage(graph, id)
        artist.artist to appearsOnOf(graph, artist)
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

/**
 * The artist page's button row: the heart to make the artist a favourite (issue #397) on the left, its drawing in line
 * with the text above; Radio, Shuffle and Play on the right, on one line. Without songs, only the heart and Radio.
 * With Dynamic colours, only Play takes the picture's colours ([playColours]); Radio and Shuffle are the page's text
 * colour, and the heart is red only for a favourite.
 */
@Composable
internal fun ArtistButtons(
    heart: @Composable (Modifier) -> Unit,
    onRadio: () -> Unit,
    onShuffle: (() -> Unit)?,
    playing: Boolean,
    onPlay: (() -> Unit)?,
    playColours: DynamicColours? = null,
) {
    val plain = MaterialTheme.colorScheme.onSurface
    PlayButtonRow {
        heart(Modifier.offset(x = -ICON_INSET))  // spacing: exact (the heart in line with the text above)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRadio, modifier = Modifier.testTag("artistRadio")) {
            Icon(Icons.Rounded.Radio, "Radio", tint = plain, modifier = Modifier.size(ICON_SIZE))
        }
        if (onShuffle != null) {
            IconButton(onClick = onShuffle, modifier = Modifier.testTag("shuffleAll")) {
                Icon(Icons.Rounded.Shuffle, "Shuffle", tint = plain, modifier = Modifier.size(30.dp))
            }
        }
        if (onPlay != null) {
            Spacer(Modifier.width(Space.Small))
            WithAccent(playColours?.controls, playColours?.onControls) {
                BigPlayButton(enabled = true, playing = playing, onPlay = onPlay)
            }
        }
    }
}
