package app.siansa.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.rounded.AddCircle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Pause
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import app.siansa.log.AppLog
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.data.Song
import app.siansa.data.discHeaders
import app.siansa.offline.OfflineItem
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size

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
    // An album's own bar (issue #965): Download, Similar albums, Shuffle and Play spread evenly, no queue button.
    albumBar: Boolean = false,
    // Where Play continues an album you stopped part way (issue #965, was "Continue from" #367): Play shows it.
    resume: PlayResume? = null,
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
    // A slide from an album's shuffle button onto Play (issue #965): DJ mix on the cover, the rest of the page dimmed.
    var djSlide by remember { mutableStateOf<DjSlide?>(null) }
    val slideDim by androidx.compose.animation.core.animateFloatAsState(if (djSlide != null) SLIDE_OTHERS else 1f, label = "slideDim")
    val coverSize = (LocalConfiguration.current.screenWidthDp * 0.62f).dp
    val coverPx = with(density) { coverSize.toPx() }
    val scrolled by remember {
        derivedStateOf { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 100_000f }
    }
    // Room after a short page's last row (issue #723), so every page can scroll its cover away: a single's title comes
    // up under the title bar like a long album's, instead of the cover fading and leaving its space empty (#682). It's
    // worked out once the last row is in view, from the rows alone, so it never changes because of itself.
    var roomPx by remember { mutableFloatStateOf(0f) }
    val loaded = songs != null
    LaunchedEffect(listState, coverPx, loaded) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastRow = info.visibleItemsInfo.firstOrNull { it.index == info.totalItemsCount - 2 }
            if (!loaded || listState.firstVisibleItemIndex != 0 || lastRow == null) null
            else roomBelow(
                maxScrollPx = rowsPastScreen(
                    scrolledPx = listState.firstVisibleItemScrollOffset.toFloat(),
                    lastBottomPx = (lastRow.offset + lastRow.size).toFloat(),
                    viewportEndPx = info.viewportEndOffset.toFloat(),
                    afterPaddingPx = info.afterContentPadding.toFloat(),
                ),
                coverPx = coverPx,
            )
        }.filterNotNull().collect { roomPx = it }
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
                        Box(Modifier.size(coverSize).shadow(18.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))) {
                            // The same name on the cover as now playing's play button slides (the owner, issue #965).
                            val label = djSlide?.let { SlideLabel(PlaySlide.DJ_MIX, it.way, it.armed) }
                            val blur by androidx.compose.animation.core.animateDpAsState(slideBlur(label), label = "slideBlur")
                            cover(Modifier.size(coverSize).then(if (blur > 0.dp) Modifier.blur(blur) else Modifier))
                            SlideCoverLabel(label, Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.height(Space.Page))
                    Column(Modifier.alpha(slideDim)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(Space.Small))
                        header()
                    }
                    Spacer(Modifier.height(Space.Tight))
                    ActionRow(
                        songs.orEmpty(), offline, onSimilar, onPlayed = ::notePlayed, saveAs = title.takeIf { saveAsPlaylist },
                        // An album's shuffle slides right onto Play for a DJ mix of its own songs (issue #965); it needs
                        // AudioMuse, as similar albums does.
                        onDjMix = if (albumBar && onSimilar != null) ({ graph.scope.launch { app.siansa.mix.AlbumDjMix.start(graph, title, songs.orEmpty()) } }) else null,
                        djSlide = djSlide, onDjSlide = { djSlide = it }, dim = slideDim, albumBar = albumBar, resume = resume,
                    )
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
            // Last: the room a short page needs to scroll its cover away (above). Nothing is drawn in it.
            item(key = "room", contentType = "room") {
                Spacer(Modifier.fillMaxWidth().height(with(density) { roomPx.toDp() }).testTag("collectionRoom"))
            }
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
 * How far a page's rows (and the end padding) let it scroll, once its last row is in view: below zero when they don't
 * fill the screen. A single on a tall phone doesn't: its room must fill the screen first, then add the cover's scroll
 * (issue #738; with only the cover's scroll it still couldn't scroll at all).
 */
internal fun rowsPastScreen(scrolledPx: Float, lastBottomPx: Float, viewportEndPx: Float, afterPaddingPx: Float): Float =
    scrolledPx + lastBottomPx + afterPaddingPx - viewportEndPx

/** How far the cover fades out entirely: the title bar is solid once the page has scrolled this far. */
internal const val FULL_FADE = 1.1f

/**
 * The room to add after a page's last row (issue #723) so it can scroll [FULL_FADE] covers: the cover then fades
 * out as the title comes up under the title bar, as on a long album. [maxScrollPx] is how far its rows alone let it
 * scroll ([rowsPastScreen], below zero when they don't fill the screen); a page that scrolls far enough gets none.
 */
internal fun roomBelow(maxScrollPx: Float, coverPx: Float): Float = (coverPx * FULL_FADE - maxScrollPx).coerceAtLeast(0f)

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
            .padding(horizontal = Space.Tight, vertical = Space.Tight)
            .testTag("fadingTitleBar"),
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
    val text = if (start.luminance() > 0.3f) Color(0xFF1A1A1A) else Color.White
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
private fun ActionRow(
    songs: List<Song>,
    offline: OfflineItem?,
    onSimilar: (() -> Unit)?,
    onPlayed: () -> Unit = {},
    saveAs: String? = null,
    onDjMix: (() -> Unit)? = null,
    djSlide: DjSlide? = null,
    onDjSlide: (DjSlide?) -> Unit = {},
    dim: Float = 1f,
    albumBar: Boolean = false,
    resume: PlayResume? = null,
) {
    val graph = LocalGraph.current
    var saving by remember { mutableStateOf(false) }
    // ⏸ while this page's songs play; it pauses them rather than starting over (issue #644).
    val playback = rememberListPlayback(songs)
    // The album's DJ mix playing (issue #965): Play keeps its grooves until something else plays or playback ends.
    val playerState by graph.player.state.collectAsStateWithLifecycle()
    val albumMix by app.siansa.mix.AlbumDjMix.playing.collectAsStateWithLifecycle()
    val mixOn = onDjMix != null && playerState.hasMedia && app.siansa.mix.albumMixPlaying(playerState.queue, songs, albumMix)
    if (saving && saveAs != null) SaveAsPlaylistDialog(saveAs, songs) { saving = false }
    // An album's plus (the owner's layout): the album into a playlist.
    var adding by remember { mutableStateOf(false) }
    val snack = LocalSnack.current
    val snackScope = rememberCoroutineScope()
    if (adding) AddToPlaylistDialog(songs) { message ->
        adding = false
        if (message != null) snackScope.launch { snack.showSnackbar(message) }
    }
    ActionButtons(
        enabled = songs.isNotEmpty(),
        onQueue = { songs.forEach { graph.player.addToQueue(it) } },
        onSave = if (saveAs != null) ({ saving = true }) else null,
        download = { if (offline != null) DownloadButton(offline, songs) },
        onSimilar = onSimilar,
        onShuffle = { graph.player.play(songs, shuffle = true); onPlayed() },
        // Play continues where you stopped, when you did (issue #965); held, it starts from the top.
        onPlay = {
            playback.press {
                val at = resume?.index?.takeIf { it in songs.indices } ?: 0
                if (at > 0) AppLog.i("Album", "Continue from ${at + 1} of ${songs.size}")
                graph.player.play(songs, at)
                onPlayed()
            }
        },
        onPlayFromStart = if (resume != null) ({ AppLog.i("Album", "Play from the start"); graph.player.play(songs); onPlayed() }) else null,
        playing = playback.playing,
        onDjMix = onDjMix?.let { mix -> { mix(); onPlayed() } },
        djSlide = djSlide,
        onDjSlide = onDjSlide,
        dim = dim,
        mixOn = mixOn,
        albumBar = albumBar,
        onAddToPlaylist = if (albumBar && songs.isNotEmpty()) ({ adding = true }) else null,
        resume = resume?.takeIf { !playback.loaded && it.index in songs.indices }?.let { it to songs.size },
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
    playing: Boolean = false,
    // An album's DJ mix (issue #965): sliding from shuffle onto Play starts it.
    onDjMix: (() -> Unit)? = null,
    djSlide: DjSlide? = null,
    onDjSlide: (DjSlide?) -> Unit = {},
    dim: Float = 1f,
    // The album's DJ mix is playing: Play has the record grooves, turning while it plays.
    mixOn: Boolean = false,
    // An album's bar (issue #965): Download, Similar albums, Shuffle and Play spread evenly, no queue button.
    albumBar: Boolean = false,
    // Where Play continues, with the album's length: Play shows a ring for how far you got and "from 10" under it.
    resume: Pair<PlayResume, Int>? = null,
    onPlayFromStart: (() -> Unit)? = null,
    // An album's plus: the whole album into a playlist you pick.
    onAddToPlaylist: (() -> Unit)? = null,
) {
    // How far right of shuffle's middle Play's near edge is: sliding there starts the DJ mix.
    var shuffleX by remember { mutableStateOf<Float?>(null) }
    var playLeft by remember { mutableStateOf<Float?>(null) }
    // Once the finger is on Play, it lights up as now playing's record and turns from the moment the mix plays, still
    // while it's paused (the owner, issue #965).
    val onPlayButton = djSlide?.armed == true
    val playButton: @Composable () -> Unit = {
        Box(Modifier.onGloballyPositioned { playLeft = it.positionInRoot().x }.alpha(if (onPlayButton) 1f else dim)) {
            BigPlayButton(
                enabled, playing = playing, grooves = onPlayButton || mixOn, turning = mixOn && playing,
                progress = resume?.let { (at, of) -> if (of > 0) at.index.toFloat() / of else 0f }?.takeIf { !onPlayButton && !mixOn },
                onLongPlay = onPlayFromStart,
                onPlay = onPlay,
            )
        }
    }
    val shuffleButton: @Composable () -> Unit = {
        if (onDjMix != null) {
            ShuffleDjButton(
                enabled = enabled,
                onShuffle = onShuffle,
                onDjMix = onDjMix,
                onSliding = onDjSlide,
                playLeftPx = shuffleX?.let { x -> playLeft?.let { it - x } },
                modifier = Modifier.onGloballyPositioned { shuffleX = it.positionInRoot().x + it.size.width / 2f },
            )
        } else {
            IconButton(onClick = onShuffle, enabled = enabled, modifier = Modifier.testTag("shuffleAll")) {
                Icon(Icons.Rounded.Shuffle, "Shuffle", tint = Accent, modifier = Modifier.size(30.dp))
            }
        }
    }
    val similarButton: @Composable () -> Unit = {
        // The waveform with an "A", as "Similar albums" in the now playing menu (issue #239).
        if (onSimilar != null) IconButton(onClick = onSimilar, modifier = Modifier.testTag("findSimilarAlbums")) {
            Icon(rememberSimilarAlbumsIcon(), "Find similar albums", tint = Accent, modifier = Modifier.size(28.dp))
        }
    }
    if (albumBar) {
        // The owner's layout (7 Nov): add to a playlist and download on the left, the plus right in line with the details
        // above; similar albums, shuffle and Play on the right; nothing under Play, so the first song comes close under.
        PlayButtonRow {
            if (onAddToPlaylist != null) {
                IconButton(
                    onClick = onAddToPlaylist,
                    enabled = enabled,
                    modifier = Modifier.offset(x = -ICON_INSET).alpha(dim).testTag("albumAddToPlaylist"),  // spacing: exact (the icon in line with the text above)
                ) {
                    Icon(Icons.Rounded.AddCircleOutline, "Add to a playlist", Modifier.size(ICON_SIZE))
                }
            }
            Box(Modifier.offset(x = if (onAddToPlaylist != null) -ICON_INSET else 0.dp).alpha(dim)) { download() }  // spacing: exact
            Spacer(Modifier.weight(1f))
            Box(Modifier.alpha(dim)) { similarButton() }
            Box(Modifier.alpha(if (djSlide != null) 1f else dim)) { shuffleButton() }
            Spacer(Modifier.width(Space.Small))
            playButton()
        }
        return
    }
    PlayButtonRow {
        IconButton(onClick = onQueue, enabled = enabled, modifier = Modifier.alpha(dim).testTag("queueAll")) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add all to queue", Modifier.size(28.dp))
        }
        if (onSave != null) {
            // A filled circle, so it isn't taken for a song's "Add to playlist" (an outlined circle in now playing).
            IconButton(onClick = onSave, enabled = enabled, modifier = Modifier.testTag("saveAsPlaylist")) {
                Icon(Icons.Rounded.AddCircle, "Save as playlist", Modifier.size(28.dp))
            }
        }
        Box(Modifier.alpha(dim)) { download() }
        Spacer(Modifier.weight(1f))
        similarButton()
        Box(Modifier.alpha(dim)) { shuffleButton() }
        Spacer(Modifier.width(Space.Small))
        playButton()
    }
}

/**
 * A row of buttons ending in the big round Play button, with [Space.BelowPlayButton] under it (the spacing
 * policy), so the first song below, marked when it's playing, never touches the button.
 */
@Composable
fun PlayButtonRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().padding(bottom = Space.BelowPlayButton),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

/** The big round Play button of album, playlist, mix and artist pages. Only ever inside a [PlayButtonRow]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BigPlayButton(
    enabled: Boolean,
    playing: Boolean = false,
    grooves: Boolean = false,
    turning: Boolean = false,
    // How far into an album you stopped, 0..1 (issue #965): a ring round the button shows it.
    progress: Float? = null,
    // Held: the album from the top, when Play would continue it.
    onLongPlay: (() -> Unit)? = null,
    onPlay: () -> Unit,
) {
    val ring = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val accent = Accent
    // With a ring (an album you stopped part way), the button shrinks inside the same square, so Play stays the size of
    // the mix pages' (the owner, 7 Nov) and the row doesn't grow.
    Box(
        Modifier
            .size(BIG_PLAY_SIZE)
            .then(
                if (progress == null) Modifier
                else Modifier.drawBehind {
                    // The whole track faint, how far you got in the accent, inside the square.
                    val stroke = BIG_PLAY_RING.toPx()
                    val arc = Size(size.width - stroke, size.height - stroke)
                    val at = Offset(stroke / 2, stroke / 2)
                    drawArc(ring, 0f, 360f, false, at, arc, style = Stroke(stroke))
                    drawArc(accent, -90f, 360f * progress.coerceIn(0f, 1f), false, at, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
    Box(
        Modifier
            .size(if (progress == null) BIG_PLAY_SIZE else BIG_PLAY_SIZE - (BIG_PLAY_RING + BIG_PLAY_RING_OUTSET) * 2)
            .clip(CircleShape)
            .background(if (enabled) Accent else Accent.copy(alpha = 0.4f))
            .combinedClickable(
                enabled = enabled,
                onClick = onPlay,
                onLongClick = onLongPlay,
                onLongClickLabel = onLongPlay?.let { "Play from the start" },
            )
            .testTag("bigPlay"),
        contentAlignment = Alignment.Center,
    ) {
        // Record grooves for an album's DJ mix (issue #965), as now playing's play button has.
        if (grooves) Grooves(playing = turning, Modifier.fillMaxSize().testTag("bigPlayGrooves"))
        // ⏸ while what the page shows is playing (a book's page, issue #626): the same button pauses it.
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            when { playing -> "Pause"; progress != null -> "Continue"; else -> "Play" },
            tint = OnAccent,
            modifier = Modifier.size(32.dp),
        )
    }
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
fun CollectionByline(name: String?, avatarCover: String?, onClick: (() -> Unit)?, details: String, note: String? = null) {
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
        // [note] after the details in the accent: "Continue from 8" on an album you stopped part way (the owner, 7 Nov).
        val accent = Accent
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                append(details)
                if (note != null) {
                    append(" · ")
                    withStyle(androidx.compose.ui.text.SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(note) }
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("collectionDetails"),
        )
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


/** The page around an album's shuffle while it slides onto Play (issue #965): dimmed like now playing's controls. */
private const val SLIDE_OTHERS = 0.3f

/** Where Play continues an album you stopped part way (issue #965): the song's position and its label ("10", "2-3"). */
data class PlayResume(val index: Int, val label: String)

private val BIG_PLAY_SIZE = 56.dp
/** An album bar icon's size, and how far inside its touch area it sits: the plus moves left by that, in line with the text. */
private val ICON_SIZE = 28.dp
private val ICON_INSET = (48.dp - ICON_SIZE) / 2
private val BIG_PLAY_RING = 2.5.dp
private val BIG_PLAY_RING_OUTSET = 1.5.dp
