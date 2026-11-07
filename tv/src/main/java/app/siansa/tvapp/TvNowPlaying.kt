package app.siansa.tvapp

import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.produceState
import app.siansa.log.AppLog
import app.siansa.ui.openSongDetails
import app.siansa.ui.openSongPath
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import app.siansa.data.cover
import app.siansa.ui.LocalGraph
import app.siansa.ui.Space
import kotlinx.coroutines.delay

/**
 * The big now playing (issue #860), as simple as Siansa's Chromecast screen (issue #974, the owner's choice): the cover,
 * the song, its artist and album as plain words, a thin line for the time, and Siansa's logo in the corner. No buttons:
 * OK plays and pauses, left and right go to the previous and next song, and down opens the menu with the rest ([menu]).
 * The remote's media keys work everywhere through the media session; rewind and fast forward skip 10 seconds.
 * Lyrics and Up next open on the right from the menu; right then goes into them, and Back closes them.
 */
@Composable
fun TvNowPlaying() {
    val graph = LocalGraph.current
    val state by graph.player.state.collectAsStateWithLifecycle()
    val likes by graph.favorites.changes.collectAsStateWithLifecycle()
    // Read only by the parts that show it, so the rest of the page doesn't redraw twice a second.
    val progress = remember { mutableStateOf(graph.player.progress()) }
    // What the right side shows instead of the cover: the songs up next, or the lyrics.
    var panel by remember { mutableStateOf(Panel.NONE) }
    val queue = panel != Panel.NONE
    LaunchedEffect(Unit) {
        while (true) {
            progress.value = graph.player.progress()
            delay(PROGRESS_TICK_MS)
        }
    }
    val song = state.current
    val liked = song != null && (likes[song.id] ?: song.starred)
    val nav = app.siansa.ui.LocalNav.current
    val radioSeed by graph.songRadio.seed.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var pickPlaylist by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.activity.compose.BackHandler(enabled = queue) { panel = Panel.NONE }
    // The remote rests on the page again once Lyrics or Up next closes.
    val stage = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(queue) { if (!queue) runCatching { stage.requestFocus() } }

    // Holding left or right winds through the song (issue #984): where to, shown on the bar, played from on letting go.
    var windTo by remember(song?.id) { mutableStateOf<Long?>(null) }
    var windForward by remember { mutableStateOf(true) }

    val tint = app.siansa.ui.rememberCoverColor(song?.cover)
    app.siansa.ui.TvRemoteStage(
        onAction = { action ->
            AppLog.d("Tv", "Now playing: ${action.name.lowercase().replace('_', ' ')}")
            when (action) {
                app.siansa.ui.RemoteAction.PLAY_PAUSE -> graph.player.togglePlay()
                app.siansa.ui.RemoteAction.PREVIOUS -> graph.player.previous()
                // With Lyrics or Up next open, right goes into them.
                app.siansa.ui.RemoteAction.NEXT -> if (queue) focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right) else graph.player.next()
                app.siansa.ui.RemoteAction.MENU -> if (song != null) menu = true
            }
        },
        modifier = Modifier.fillMaxSize().clipToBounds(),
        focus = stage,
        onWind = { delta ->
            if (song != null) {
                if (windTo == null) AppLog.d("Tv", "Now playing: winding ${if (delta < 0) "back" else "forward"}")
                windForward = delta >= 0
                windTo = windTarget(windTo ?: graph.player.positionMs(), delta, graph.player.progress().durationMs)
            }
        },
        onWindDone = {
            windTo?.let { to ->
                AppLog.d("Tv", "Now playing: wound to ${minutes(to)}")
                graph.player.seekTo(to)
            }
            windTo = null
        },
    ) {
        TvBackdrop(song?.cover, app.siansa.ui.pageBottomColor(tint))
        Row(
            Modifier.fillMaxSize().padding(horizontal = TV_EDGE * 2, vertical = Space.Large),
            horizontalArrangement = Arrangement.spacedBy(Space.Large * 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Animated covers move here as on the phone (Settings › Playback there: Animated covers), and a song's own
            // picture shows when it has one. Paused, the cover darkens under a pause sign, as on the Chromecast screen.
            if (!queue) {
                Box(Modifier.size(COVER_SIZE), contentAlignment = Alignment.Center) {
                    app.siansa.ui.BigCover(song?.cover, song?.albumId, Modifier.size(COVER_SIZE), RoundedCornerShape(Space.Page))
                    if (song != null && !state.isPlaying) {
                        Box(Modifier.size(COVER_SIZE).background(Color.Black.copy(alpha = PAUSED_SHADE), RoundedCornerShape(Space.Page)))
                        Icon(Icons.Rounded.Pause, "Paused", Modifier.size(PAUSE_SIGN).testTag("tvPaused"), tint = Color.White)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Section)) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    Text(
                        song?.title ?: "Nothing playing",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // The artist and the album as plain words (the owner: no bubbles); the menu goes to them.
                    if (song != null) {
                        Text(song.artist, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (song.album.isNotBlank()) {
                            Text(song.album, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                ProgressBlock(progress, details(state.codec, state.transcodeKbps), windTo, windForward)
            }
            when (panel) {
                Panel.QUEUE -> UpNext(state.queue, state.index, state.auto)
                Panel.LYRICS -> LyricsPanel(song) { progress.value.positionMs }
                Panel.NONE -> Unit
            }
        }
        // What the remote does here, quietly at the foot of the page, and Siansa's logo in the corner.
        Text(
            if (queue) "Right  into the list  ·  Back  closes it  ·  ▼  more" else "OK  play / pause   ·   ◀ ▶  previous / next   ·   ▼  more",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = HINT_ALPHA),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Space.Section).testTag("tvRemoteHint"),
        )
        TvBrand(Modifier.align(Alignment.BottomEnd).padding(end = TV_EDGE * 2, bottom = Space.Section))
    }
    val snack = app.siansa.ui.LocalSnack.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (menu && song != null) {
        val artistId = song.artistId ?: song.artists.firstOrNull()?.id
        val albumId = song.albumId
        val choices = buildList<Pair<String, () -> Unit>> {
            add((if (liked) "Liked ✓" else "Like") to { graph.favorites.toggle(song) })
            add("Shuffle: ${if (state.shuffle) "on" else "off"}" to { graph.player.toggleShuffle() })
            add("Repeat: ${when (state.repeatMode) { Player.REPEAT_MODE_ONE -> "this song"; Player.REPEAT_MODE_ALL -> "all"; else -> "off" }}" to { graph.player.cycleRepeat() })
            add((if (panel == Panel.LYRICS) "Hide the lyrics" else "Lyrics") to { panel = if (panel == Panel.LYRICS) Panel.NONE else Panel.LYRICS })
            add((if (panel == Panel.QUEUE) "Hide up next" else "Up next") to { panel = if (panel == Panel.QUEUE) Panel.NONE else Panel.QUEUE })
            // A mix from this song: AudioMuse's songs that sound like it when set up, Steady or DJ as on the phone; while
            // one plays, this stops it (issue #895).
            add((if (radioSeed != null) "Stop the mix" else "Start a mix") to { if (radioSeed != null) graph.songRadio.stop() else app.siansa.ui.RadioChooser.song.value = song })
            if (artistId != null) add("Go to the artist" to { nav.navigate(TvRoutes.artist(artistId)) })
            if (albumId != null && song.album.isNotBlank()) add("Go to the album" to { nav.navigate(TvRoutes.album(albumId)) })
            // AudioMuse's song path from this song, queued after it once ready (as on the phone's player).
            if (graph.settings.current.audioMuseReady) add("Song path" to { nav.openSongPath(song.id, fromNowPlaying = true) })
            add("Add to playlist" to { pickPlaylist = true })
            add("Similar songs" to { nav.navigate("similar/songs/${android.net.Uri.encode(song.id)}") })
            add("Song details" to { nav.openSongDetails(song.id) })
        }
        TvChoices(song.title, choices, onDismiss = { menu = false })
    }
    // The phone's own playlist picker (issue #895).
    if (pickPlaylist && song != null) {
        app.siansa.ui.AddToPlaylistDialog(listOf(song)) { message ->
            pickPlaylist = false
            if (message != null) scope.launch { snack.showSnackbar(message) }
        }
    }
}

/** Siansa's logo, quietly in the corner (issue #974): the icon and the wordmark, its S in amber, as on the Chromecast screen. */
@Composable
internal fun TvBrand(modifier: Modifier = Modifier) {
    Row(modifier.alpha(BRAND_ALPHA).testTag("tvBrand"), horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalAlignment = Alignment.CenterVertically) {
        // The launcher icon's own drawing (Siansa's frost and amber, in both builds), its middle two thirds.
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(app.siansa.R.drawable.ic_launcher_foreground), null,
            Modifier.size(BRAND_ICON).clipToBounds().graphicsLayer { scaleX = ICON_ZOOM; scaleY = ICON_ZOOM },
        )
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                withStyle(androidx.compose.ui.text.SpanStyle(brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(BRAND_AMBER_TOP, BRAND_AMBER_BOTTOM)))) { append("S") }
                withStyle(androidx.compose.ui.text.SpanStyle(color = BRAND_TEXT)) { append("iansa") }
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/**
 * The time: a thin bar, where the song is and how long it is, and the file's kind between them. While winding
 * ([windTo], issue #984) the bar and the time show where it's wound to, in the accent, with which way in the middle.
 */
@Composable
private fun ProgressBlock(
    progress: androidx.compose.runtime.MutableState<app.siansa.playback.PlayerConnection.Progress>,
    details: String,
    windTo: Long? = null,
    windForward: Boolean = true,
) {
    val now = progress.value
    val at = windTo ?: now.positionMs
    val fraction = if (now.durationMs > 0) (at.toFloat() / now.durationMs).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(PROGRESS_HEIGHT),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = TRACK_ALPHA),
            drawStopIndicator = {},
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(minutes(at), style = MaterialTheme.typography.titleMedium,
                color = if (windTo != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (windTo != null) {
                Icon(
                    if (windForward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                    if (windForward) "Winding forward" else "Winding back",
                    Modifier.size(WIND_SIGN).testTag("tvWinding"),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(details, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(minutes(now.durationMs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class Panel { NONE, QUEUE, LYRICS }

/**
 * The song's lyrics from the server, as on the phone: synced ones follow the song, the line being sung lit and kept in
 * view. Right from the buttons reaches them, and up and down then scroll through them; synced ones follow the song again
 * a few seconds after the last press.
 */
@Composable
private fun LyricsPanel(song: app.siansa.data.Song?, positionMs: () -> Long) {
    val graph = LocalGraph.current
    val lyrics by produceState<app.siansa.data.Lyrics?>(null, song?.id) {
        value = song?.let { s -> runCatching { graph.source.lyrics(s) }.onFailure { AppLog.w("Tv", "Lyrics for ${s.id}: ${it.message}") }.getOrNull() }
    }
    val lines = lyrics?.lines?.filter { it.text.isNotBlank() }.orEmpty()
    val synced = lyrics?.synced == true
    // Only the line being sung is worked out from the time, so the panel redraws when it changes, not with every tick.
    val current by remember(lines, synced) {
        androidx.compose.runtime.derivedStateOf { if (synced) lines.indexOfLast { (it.startMs ?: Long.MAX_VALUE) <= positionMs() } else -1 }
    }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val step = with(androidx.compose.ui.platform.LocalDensity.current) { LYRICS_STEP.toPx() }
    var focused by remember { mutableStateOf(false) }
    // While the remote scrolls them, synced lyrics stop following the song, and start again a few seconds later.
    var scrolledAt by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(scrolledAt) {
        if (scrolledAt == 0L) return@LaunchedEffect
        delay(FOLLOW_AGAIN_MS)
        following = true
    }
    LaunchedEffect(current, following) { if (following && current >= 0) list.animateScrollToItem((current - LINES_ABOVE).coerceAtLeast(0)) }
    Column(Modifier.width(QUEUE_WIDTH).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        Text(
            if (focused) "Lyrics · up and down scroll" else "Lyrics",
            style = MaterialTheme.typography.titleLarge,
            color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (lines.isEmpty()) {
            Text(if (lyrics == null && song != null) "No lyrics for this song on your server." else "", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val shape = RoundedCornerShape(Space.Small)
            LazyColumn(
                state = list,
                contentPadding = PaddingValues(horizontal = Space.Small, vertical = Space.Small),
                verticalArrangement = Arrangement.spacedBy(Space.Small),
                modifier = Modifier
                    .fillMaxSize()
                    .border(LYRICS_RING, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                    .onFocusChanged { focused = it.isFocused }
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val delta = when (e.key) {
                            Key.DirectionDown -> step
                            Key.DirectionUp -> -step
                            else -> return@onKeyEvent false
                        }
                        following = false
                        scrolledAt = android.os.SystemClock.elapsedRealtime()
                        scope.launch { list.animateScrollBy(delta) }
                        true
                    }
                    .focusable(),
            ) {
                itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
                    Text(
                        line.text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            i == current -> MaterialTheme.colorScheme.primary
                            synced && i < current -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

/**
 * The songs after this one: OK plays from that song. Songs Siansa added on its own (AudioMuse's radio, Keep playing)
 * have the same star as on the phone's queue.
 */
@Composable
private fun UpNext(queue: List<app.siansa.data.Song>, index: Int, auto: Set<Int> = emptySet()) {
    val graph = LocalGraph.current
    Column(Modifier.width(QUEUE_WIDTH).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        Text("Up next", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        val after = queue.drop(index + 1)
        if (after.isEmpty()) {
            Text("Nothing after this song.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = Space.Large), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                itemsIndexed(after.take(QUEUE_SHOWN), key = { i, s -> "${index + 1 + i}:${s.id}" }) { i, song ->
                    val shape = RoundedCornerShape(Space.Small)
                    Row(
                        Modifier.fillMaxWidth().tvFocus(shape, onClick = { graph.player.skipTo(index + 1 + i) }, grow = ROW_GROW)
                            .background(MaterialTheme.colorScheme.background, shape).padding(horizontal = Space.Page, vertical = Space.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                            Text(song.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if ((index + 1 + i) in auto) {
                            Icon(
                                Icons.Rounded.AutoAwesome, "Added automatically",
                                Modifier.padding(start = Space.Small).size(AUTO_MARK).testTag("autoAdded"),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "FLAC", "Opus 160 kbit/s", or nothing until the player knows. */
fun details(codec: String?, kbps: Int): String = when {
    kbps > 0 -> "Opus $kbps kbit/s"
    codec != null -> codec
    else -> ""
}

private const val PROGRESS_TICK_MS = 500L
private val COVER_SIZE = 320.dp
private const val TRACK_ALPHA = 0.25f
private const val LINES_ABOVE = 2
/** How far one press of up or down scrolls the lyrics: about three lines. */
private val LYRICS_STEP = 120.dp
private const val FOLLOW_AGAIN_MS = 4_000L
private val LYRICS_RING = 2.dp
private const val ROW_GROW = 1.02f
private const val QUEUE_SHOWN = 100
/** The star on a song Siansa added on its own, a little larger than the phone's for the distance. */
private val AUTO_MARK = 20.dp
private val PROGRESS_HEIGHT = 4.dp
private const val PAUSED_SHADE = 0.45f
private val PAUSE_SIGN = 96.dp
private const val HINT_ALPHA = 0.7f
private const val BRAND_ALPHA = 0.75f
private val BRAND_ICON = 44.dp
/** The launcher icon's drawing fills the middle two thirds of its square. */
private const val ICON_ZOOM = 1.5f
private val BRAND_AMBER_TOP = Color(0xFFFFE7C4)
private val BRAND_AMBER_BOTTOM = Color(0xFFE8A55A)
private val BRAND_TEXT = Color(0xFFEEF2F5)

/** Back or forward [deltaMs] in the song playing, kept inside it. */
fun seekBy(graph: app.siansa.AppGraph, deltaMs: Long) {
    graph.player.seekTo(windTarget(graph.player.positionMs(), deltaMs, graph.player.progress().durationMs))
}

private const val END_MARGIN_MS = 1_000L
private val WIND_SIGN = 28.dp

/** Where winding by [deltaMs] from [fromMs] lands (issue #984): inside the song, short of its end so it doesn't skip. */
internal fun windTarget(fromMs: Long, deltaMs: Long, durationMs: Long): Long {
    val to = (fromMs + deltaMs).coerceAtLeast(0L)
    return if (durationMs > 0) to.coerceAtMost((durationMs - END_MARGIN_MS).coerceAtLeast(0L)) else to
}
/** Leaves the controls room for their five slots in a row beside it (5 × 76 dp and four 8 dp gaps). */
private val QUEUE_WIDTH = 340.dp
