package app.musix.ui

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
import androidx.compose.material.icons.rounded.PlayArrow
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
import app.musix.data.Album
import app.musix.data.Artist
import app.musix.data.SimilarArtist
import app.musix.data.Song
import app.musix.data.category
import app.musix.data.isTopSongsFresh
import app.musix.log.AppLog
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
            val remote = graph.subsonic.artist(id)
            val albums = graph.library.keepSyncedAlbums(remote.albums, "Artist page")
            ArtistPage(remote.artist, albums.sortedByDescending { it.year ?: 0 }, emptyList())
        }
    }
    val info by loadState("info-$id") { graph.subsonic.artistInfo(id) }
    // Popular songs: the saved list shows at once; Navidrome is asked again only when it's a few days old.
    // Started with the page, not after it, so the request runs alongside the rest.
    val top by produceState<List<Song>?>(null, id) {
        try {
            val cached = graph.topSongs.cached(id)
            if (cached != null) value = cached.songs
            if (cached != null && isTopSongsFresh(cached, System.currentTimeMillis())) return@produceState
            val loaded = snapshotFlow { page.valueOrNull() }.filterNotNull().first()
            value = graph.topSongs.refresh(loaded.artist, loaded.songs)
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
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
                )
            }
        },
        image = { modifier ->
            val photo = artistInfo?.imageUrl ?: p?.artist?.imageUrl
            if (photo != null) {
                AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
            } else {
                Cover(p?.artist?.coverArt, LARGE_COVER, modifier, RectangleShape)
            }
        },
    ) { cardModifier ->
        if (p == null) {
            Box(Modifier.statusBarsPadding().padding(top = HERO)) { LoadBox(page) { } }
            return@HeroPage
        }
        val others by loadState("appears-$id") { graph.library.appearsOn(p.artist) }
        val groups = p.albums.groupBy { it.category }.toList().sortedBy { it.first.ordinal }

        // Release-type chips (Albums, Singles, DJ-mixes…): one type at a time, only while the page is open.
        var typeName by rememberReleaseTypeFilter()
        val types = releaseTypeChoices(p.albums)
        val type = chosenReleaseType(typeName, types)
        val shownGroups = onlyReleaseType(groups, type)

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(bottom = bottomBarPadding(24.dp)),
            modifier = Modifier.fillMaxSize().then(cardModifier),
        ) {
            full { HeroSpacer(HERO) }
            full {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        listOfNotNull(
                            "${p.albums.size} ${if (p.albums.size == 1) "release" else "releases"}",
                            if (p.songs.isNotEmpty()) "${p.songs.size} songs" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            launch.progressive("${p.artist.name} radio", { graph.radios.artistStart(p.artist) }) {
                                graph.radios.artistMore(p.artist, it)
                            }
                        }) {
                            Icon(Icons.Rounded.Radio, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Radio")
                        }
                        Spacer(Modifier.weight(1f))
                        if (p.songs.isNotEmpty()) {
                            IconButton(onClick = { graph.player.play(p.songs, shuffle = true) }) {
                                Icon(Icons.Rounded.Shuffle, "Shuffle", tint = Accent, modifier = Modifier.size(30.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.size(56.dp).clip(CircleShape).background(Accent).clickable { graph.player.play(p.songs) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.PlayArrow, "Play", tint = androidx.compose.ui.graphics.Color(0xFF2A1700), modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }

            val topSongs = top
            if (topSongs == null) {
                // Keeps the section's place while the first answer comes in, so the page doesn't jump.
                full { SectionTitle("Popular") }
                items(TOP_ROWS, key = { "top-wait-$it" }, span = { GridItemSpan(maxLineSpan) }) { SongRowPlaceholder() }
            } else if (topSongs.isNotEmpty()) {
                full { SectionTitle("Popular") }
                itemsIndexed(topSongs.take(TOP_ROWS), key = { i, s -> "top-$i-${s.id}" }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { index, song ->
                    SongRow(song = song, onClick = { graph.player.play(topSongs, index) }, leading = "${index + 1}")
                }
            }

            if (types.isNotEmpty()) {
                full { ReleaseTypeRow(types, type) { typeName = it?.name } }
            }

            // Discography by release type: Albums, EPs, Singles, Live, Compilations, DJ-mixes…
            shownGroups.forEachIndexed { i, (category, albums) ->
                full {
                    SectionTitle(
                        if (albums.size == 1) category.single else category.plural,
                        top = if (i == 0 && types.isNotEmpty()) CHIPS_GAP else SECTION_GAP,
                    )
                }
                items(albums, key = { "album-${it.id}" }) { album ->
                    AlbumTile(
                        name = album.name,
                        subtitle = album.year?.toString() ?: "",
                        coverArt = album.coverArt,
                        onClick = { nav.navigate("album/${album.id}") },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            // Albums by others they're credited on: a row here, all of them on their own page.
            val appearsOn = others.valueOrNull().orEmpty()
            if (appearsOn.isNotEmpty()) {
                full { AppearsOnTitle { nav.navigate("artist/${p.artist.id}/appears-on") } }
                full {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

            val similar = artistInfo?.similar.orEmpty()
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle("Appears on", Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            "Show all",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp),
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
          Box(Modifier.padding(padding)) {
            if (albums.isEmpty()) {
                Text(
                    "No albums by other artists yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(albums, key = { it.id }) { album ->
                        AlbumTile(
                            name = album.name,
                            subtitle = listOfNotNull(album.artist.ifBlank { null }, album.year?.toString()).joinToString(" · "),
                            coverArt = album.coverArt,
                            onClick = { nav.navigate("album/${album.id}") },
                        )
                    }
                }
            }
          }
        }
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

private const val TOP_ROWS = 5

/** A grey stand-in the size of a song row, shown while popular songs load. */
@Composable
private fun SongRowPlaceholder() {
    val shade = MaterialTheme.colorScheme.surfaceVariant
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(32.dp))
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(shade))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shade))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.4f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(shade))
        }
    }
}

@Composable
private fun Biography(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp).animateContentSize()) {
        Text(
            "About",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp, bottom = 10.dp),
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
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(matched.valueOrNull() ?: similar.map { it to null }, key = { it.first.id ?: it.first.name }) { (artist, local) ->
            val target = local?.id ?: artist.id
            Column(
                Modifier
                    .width(110.dp)
                    .clickable(enabled = target != null) { target?.let { nav.navigate("artist/$it") } }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val photo = artist.imageUrl ?: local?.imageUrl
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                } else {
                    Cover(artist.coverArt ?: local?.coverArt, 150, Modifier.size(96.dp), CircleShape)
                }
                Spacer(Modifier.height(4.dp))
                Text(artist.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
