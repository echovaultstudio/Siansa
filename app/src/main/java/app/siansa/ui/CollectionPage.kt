package app.siansa.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.rounded.AddCircle
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.rememberCoroutineScope
import app.siansa.data.cover
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import app.siansa.data.normalizeKey
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.data.Song
import app.siansa.data.discHeaders
import app.siansa.offline.OfflineItem
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults

/**
 * The page used for albums, playlists, mixes and radios, laid out like Spotify: the cover's colour
 * runs down from the top into the page, the cover shrinks and fades away as you scroll, and the
 * title bar takes on the cover's colour once the cover is gone.
 */
@Composable
fun CollectionPage(
    nav: NavController,
    title: String,
    color: Color,
    songs: List<Song>?,
    error: String? = null,
    cover: @Composable (Modifier) -> Unit,
    header: @Composable ColumnScope.() -> Unit,
    numbered: Boolean = false,
    discTitles: Map<Int, String> = emptyMap(),
    showCovers: Boolean = true,
    // The line under each song's title; by default its artist and album.
    subtitleOf: ((Song) -> String?)? = null,
    // What the download button keeps on the phone; null shows no button (mixes, radios).
    offline: OfflineItem? = null,
    // Buttons at the right of the title bar (a playlist's ⋮ menu).
    actions: @Composable () -> Unit = {},
    // "Find similar albums" beside Shuffle (album pages, issue #228); null shows no button.
    onSimilar: (() -> Unit)? = null,
    // Mixes, radios and cards: a button beside "Add all to queue" that saves the list as a playlist, named [title].
    saveAsPlaylist: Boolean = false,
    // Extra items in a song's ⋮ menu, by its position in [songs].
    songMenu: (@Composable (index: Int, song: Song, close: () -> Unit) -> Unit)? = null,
    // The song heard last on an album you stopped part way (issue #367), by position, with its note.
    lastPlayed: Pair<Int, String>? = null,
    // What the quick-scroll handle says for a song (a letter, a month); by default where it is in the list.
    scrollLabel: ((index: Int, song: Song) -> String?)? = null,
    footer: LazyListScope.() -> Unit = {},
) {
    val graph = LocalGraph.current
    val player by graph.player.state.collectAsStateWithLifecycle()
    // This page as Home's shortcuts open it again (issue #271): its route with the ids filled in.
    val route = remember(nav.currentBackStackEntry) { nav.currentBackStackEntry?.let { routeOf(it.destination.route, it.arguments) } }
    fun notePlayed() {
        val r = route ?: return
        val cover = songs?.firstOrNull()?.cover
        graph.scope.launch { graph.recentPlays.note(r, title, cover) }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val coverSize = (LocalConfiguration.current.screenWidthDp * 0.62f).dp
    val coverPx = with(density) { coverSize.toPx() }
    val scrolled by remember {
        derivedStateOf { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 100_000f }
    }
    val progress = (scrolled / coverPx).coerceIn(0f, 1f)
    val barAlpha = ((scrolled - coverPx * 0.8f) / (coverPx * 0.3f)).coerceIn(0f, 1f)
    val surface = MaterialTheme.colorScheme.surface
    // Albums with several discs get a heading above each disc (its title, or "Disc 2").
    val discs = remember(songs, numbered, discTitles) { if (numbered && songs != null) discHeaders(songs, discTitles) else emptyMap() }
    // Long lists get a handle on the right to move through them quickly (beta testers #24); the songs make
    // room for it.
    val fast = songs != null && showFastScroll(songs.size)
    val positions = remember(songs, discs) { FastScroll.positions(songs?.size ?: 0, headers = 1, headed = discs.keys) }
    val pastHeader by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val rowEnd = if (fast) AlphabetBarWidth else 0.dp

    Box(Modifier.fillMaxSize().background(surface)) {
        if (showCovers && songs != null) {
            val covers = remember(songs) { songs.map { it.cover } }
            PrefetchCovers(listState.firstVisibleItemIndex, listState.layoutInfo.visibleItemsInfo.size, covers, 150)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomBarPadding(Space.Large))) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(0f to color, 0.75f to color.copy(alpha = 0.35f), 1f to surface))
                        .statusBarsPadding()
                        .padding(top = 56.dp, start = Space.Page, end = Space.Page),  // spacing: exact (below the top bar)
                ) {
                    // The cover stays a little behind the scroll, shrinking and fading as it goes.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = scrolled.coerceAtMost(coverPx) * 0.45f
                                val scale = 1f - progress * 0.22f
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - progress * progress
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        cover(Modifier.size(coverSize).shadow(18.dp, RoundedCornerShape(6.dp)))
                    }
                    Spacer(Modifier.height(Space.Page))
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Space.Small))
                    header()
                    Spacer(Modifier.height(Space.Tight))
                    ActionRow(songs.orEmpty(), offline, onSimilar, onPlayed = ::notePlayed, saveAs = title.takeIf { saveAsPlaylist })
                }
            }
            when {
                songs == null && error == null -> item {
                    Box(Modifier.fillMaxWidth().padding(Space.Large), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
                }
                error != null -> item { ErrorText(error) }
                songs != null -> {
                    // The song playing now is always marked, even when the page lists another copy of it (issue #228).
                    val playing = playingIndex(songs, player.current)
                    songs.forEachIndexed { index, song ->
                        discs[index]?.let { header ->
                            item(key = "disc-$index-${header.disc}", contentType = "disc") { Box(Modifier.padding(end = rowEnd)) { DiscHeading(header.label, color) } }
                        }
                        item(key = "$index-${song.id}", contentType = "song") {
                            val now = index == playing
                            val last = lastPlayed?.takeIf { it.first == index }?.second
                            val subtitle = last ?: if (subtitleOf != null) subtitleOf(song) else artistAndAlbum(song)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (now) Selected.copy(alpha = NOW_PLAYING_BAND) else Color.Transparent)
                                    .padding(end = rowEnd)
                                    .then(if (last != null) Modifier.lastPlayedLine(Accent) else Modifier)
                                    .testTag(if (now) "collectionNowPlaying" else if (last != null) "collectionLastPlayed" else "collectionRow"),
                            ) {
                                SongRow(
                                    song = song,
                                    onClick = { graph.player.play(songs, index); notePlayed() },
                                    leading = if (numbered) song.track?.toString() ?: "${index + 1}" else null,
                                    highlighted = now,
                                    showCover = showCovers,
                                    subtitle = subtitle,
                                    nowPlaying = now,
                                    extraMenu = { close -> songMenu?.invoke(index, song, close) },
                                    subtitleAccent = last != null,
                                )
                            }
                        }
                    }
                }
            }
            footer()
        }

        // Shown once the cover has scrolled away, so it never covers the page's buttons. The top of the list is
        // its first song, which keeps the handle in view while you drag.
        if (fast && songs != null) {
            AnimatedVisibility(
                visible = pastHeader,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, bottom = bottomBarPadding(Space.Small)),  // spacing: exact (below the top bar)
            ) {
                ListFastScroll(
                    count = songs.size,
                    positions = positions,
                    firstVisible = { listState.firstVisibleItemIndex },
                    visible = { listState.layoutInfo.visibleItemsInfo.size },
                    scrollTo = { i -> scope.launch { listState.scrollToItem(i) } },
                    labelAt = { i -> songs.getOrNull(i)?.let { song -> scrollLabel?.invoke(i, song) } ?: FastScroll.positionLabel(i, songs.size) },
                )
            }
        }

        FadingTitleBar(nav, title, color, barAlpha, actions)
    }
}

/**
 * The title bar over a page tinted with its cover's colour (albums, playlists, books): just a back button over the
 * colour until the page scrolls, then a solid bar in that colour with the title.
 */
@Composable
internal fun FadingTitleBar(nav: NavController, title: String, color: Color, barAlpha: Float, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = barAlpha))
            .statusBarsPadding()
            .padding(horizontal = Space.Tight, vertical = Space.Tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No shading behind the arrow (issue #228).
        IconButton(onClick = { nav.popBackStack() }) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
        }
        Spacer(Modifier.width(Space.Small))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).graphicsLayer { alpha = barAlpha },
        )
        actions()
    }
}

/** The colours of a disc heading's band: [start] on the left, fading out to the right, with [text] on it. */
internal data class DiscBand(val start: Color, val text: Color)

/**
 * A band in the album's own colour behind each disc heading, so it stands out in a long track list:
 * the cover colour darkened in dark mode, a soft tint of it in light mode. The text is white or near
 * black, whichever reads better on the band.
 */
internal fun discBand(cover: Color, dark: Boolean): DiscBand {
    val start = if (dark) lerp(cover, Color.Black, 0.6f) else lerp(cover, Color.White, 0.75f)
    val text = if (start.luminance() > 0.3f) Color(0xFF1A1426) else Color.White
    return DiscBand(start.copy(alpha = 1f), text)
}

/** The heading above each disc of a multi-disc album. */
@Composable
private fun DiscHeading(label: String, cover: Color) {
    val band = discBand(cover, dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.Small, end = Space.Small, top = Space.Page, bottom = Space.Tight)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(0f to band.start, 0.6f to band.start.copy(alpha = 0.75f), 1f to band.start.copy(alpha = 0f)))
            .padding(horizontal = Space.Medium, vertical = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Album, null, tint = band.text.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.Medium))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = band.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Queue all and download on the left; find similar albums, shuffle and the big round play button on the right. */
@Composable
private fun ActionRow(songs: List<Song>, offline: OfflineItem?, onSimilar: (() -> Unit)?, onPlayed: () -> Unit = {}, saveAs: String? = null) {
    val graph = LocalGraph.current
    var saving by remember { mutableStateOf(false) }
    if (saving && saveAs != null) SaveAsPlaylistDialog(saveAs, songs) { saving = false }
    ActionButtons(
        enabled = songs.isNotEmpty(),
        onQueue = { songs.forEach { graph.player.addToQueue(it) } },
        onSave = if (saveAs != null) ({ saving = true }) else null,
        download = { if (offline != null) DownloadButton(offline, songs) },
        onSimilar = onSimilar,
        onShuffle = { graph.player.play(songs, shuffle = true); onPlayed() },
        onPlay = { graph.player.play(songs); onPlayed() },
    )
}

/** The buttons of [ActionRow], without the player, so their layout can be checked on its own (SpacingTest). */
@Composable
internal fun ActionButtons(
    enabled: Boolean,
    onQueue: () -> Unit,
    download: @Composable () -> Unit,
    onSimilar: (() -> Unit)?,
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    onSave: (() -> Unit)? = null,
) {
    PlayButtonRow {
        IconButton(onClick = onQueue, enabled = enabled, modifier = Modifier.testTag("queueAll")) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add all to queue", Modifier.size(28.dp))
        }
        if (onSave != null) {
            // A filled circle, so it isn't taken for a song's "Add to playlist" (an outlined circle in now playing).
            IconButton(onClick = onSave, enabled = enabled, modifier = Modifier.testTag("saveAsPlaylist")) {
                Icon(Icons.Rounded.AddCircle, "Save as playlist", Modifier.size(28.dp))
            }
        }
        download()
        Spacer(Modifier.weight(1f))
        if (onSimilar != null) {
            // The waveform with an "A", as "Similar albums" in the now playing menu (issue #239).
            IconButton(onClick = onSimilar, modifier = Modifier.testTag("findSimilarAlbums")) {
                Icon(rememberSimilarAlbumsIcon(), "Find similar albums", tint = Accent, modifier = Modifier.size(28.dp))
            }
        }
        IconButton(onClick = onShuffle, enabled = enabled, modifier = Modifier.testTag("shuffleAll")) {
            Icon(Icons.Rounded.Shuffle, "Shuffle", tint = Accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(Space.Small))
        BigPlayButton(enabled, onPlay)
    }
}

/**
 * A row of buttons ending in the big round Play button, with [Space.BelowPlayButton] under it (the spacing
 * policy), so the first song below, marked when it's playing, never touches the button.
 */
@Composable
fun PlayButtonRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(bottom = Space.BelowPlayButton),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The big round Play button of album, playlist, mix and artist pages. Only ever inside a [PlayButtonRow]. */
@Composable
fun BigPlayButton(enabled: Boolean, onPlay: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (enabled) Accent else Accent.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onPlay)
            .testTag("bigPlay"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.PlayArrow, "Play", tint = OnAccent, modifier = Modifier.size(32.dp))
    }
}

/** How strongly the song playing now is marked in a song list: the queue's band, in the theme's colour. */
private const val NOW_PLAYING_BAND = 0.18f

/**
 * Which of [songs] is playing now, or null. By id, or else the same title, album and disc and track: an album
 * that lives in several libraries lists one copy, which may not be the copy that plays (issue #228).
 */
internal fun playingIndex(songs: List<Song>, current: Song?): Int? {
    if (current == null) return null
    songs.indexOfFirst { it.id == current.id }.takeIf { it >= 0 }?.let { return it }
    val key = listOf(normalizeKey(current.title), normalizeKey(current.album), (current.disc ?: 1).toString(), current.track?.toString())
    return songs.indexOfFirst {
        listOf(normalizeKey(it.title), normalizeKey(it.album), (it.disc ?: 1).toString(), it.track?.toString()) == key
    }.takeIf { it >= 0 }
}

/** A small "meta" line under the title: artist avatar and name, then details. */
@Composable
fun CollectionByline(name: String?, avatarCover: String?, onClick: (() -> Unit)?, details: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
        if (!name.isNullOrBlank()) {
            Row(
                Modifier.clickable(enabled = onClick != null) { onClick?.invoke() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (avatarCover != null) {
                    Cover(avatarCover, 80, Modifier.size(26.dp), CircleShape)
                    Spacer(Modifier.width(Space.Small))
                }
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
        Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A gradient square with a title, for generated lists that have no cover of their own. */
@Composable
fun GeneratedCover(title: String, colors: Pair<Color, Color>, modifier: Modifier) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.linearGradient(listOf(colors.first, colors.second))),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = Color.White,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(Space.Page),
        )
    }
}

/** "album/{id}" with its arguments → "album/42" (issue #271); null when an argument is missing. */
@Suppress("DEPRECATION")
internal fun routeOf(pattern: String?, args: android.os.Bundle?): String? =
    fillRoute(pattern) { name -> args?.get(name)?.toString() }

/** [pattern] with each "{name}" replaced by [value] (encoded for a route), or null when one has no value. */
internal fun fillRoute(pattern: String?, value: (String) -> String?): String? {
    pattern ?: return null
    var missing = false
    val filled = Regex("\\{([^}]+)\\}").replace(pattern) { m ->
        val v = value(m.groupValues[1])
        if (v == null) { missing = true; "" } else java.net.URLEncoder.encode(v, "UTF-8").replace("+", "%20")
    }
    return filled.takeUnless { missing }
}

/** The accent line down the left of the song heard last (issue #367). */
fun Modifier.lastPlayedLine(color: Color): Modifier = drawBehind {
    drawRect(color, size = Size(LAST_PLAYED_LINE.toPx(), size.height))
}

private val LAST_PLAYED_LINE = 3.dp

/**
 * "Continue from 5" under an album's details, to carry on where you stopped (issue #367). The button's
 * touch area reaches [Space.Tight] above what you see, so it looks [Space.Small] under the details, like
 * the artist above them.
 */
@Composable
fun ContinueButton(track: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        modifier = Modifier.padding(top = Space.Tight).testTag("continueFrom"),
    ) {
        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text("Continue from $track")
    }
}
