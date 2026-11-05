package app.siansa.tvapp

import androidx.compose.foundation.background
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.produceState
import app.siansa.log.AppLog
import app.siansa.ui.openSongPath
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import app.siansa.ui.Cover
import app.siansa.ui.LARGE_COVER
import app.siansa.ui.LocalGraph
import app.siansa.ui.Space
import kotlinx.coroutines.delay

/**
 * The big now playing (issue #860): the cover, the song and its artist large enough to read across the room, the time,
 * the controls, and what's up next. The remote's media keys work everywhere through the media session; here rewind and
 * fast forward also skip back and forward 10 seconds.
 */
@Composable
fun TvNowPlaying() {
    val graph = LocalGraph.current
    val state by graph.player.state.collectAsStateWithLifecycle()
    val likes by graph.favorites.changes.collectAsStateWithLifecycle()
    val sleep by graph.sleepTimer.state.collectAsStateWithLifecycle()
    var progress by remember { mutableStateOf(graph.player.progress()) }
    // What the right side shows instead of the cover: the songs up next, or the lyrics.
    var panel by remember { mutableStateOf(Panel.NONE) }
    val queue = panel != Panel.NONE
    var sleepChoices by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            progress = graph.player.progress()
            delay(PROGRESS_TICK_MS)
        }
    }
    val playButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playButton.requestFocus() } }
    val song = state.current
    val liked = song != null && (likes[song.id] ?: song.starred)

    // The cover, enlarged and blurred, as the background, darkened down to its own colour: the phone's player
    // (a darker plain version before Android 12, which can't blur).
    val tint = app.siansa.ui.rememberCoverColor(song?.cover)
    Box(Modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        Cover(
            song?.cover,
            BACKDROP_COVER,
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = BACKDROP_SCALE; scaleY = BACKDROP_SCALE; alpha = if (Build.VERSION.SDK_INT >= 31) 0.85f else 0.45f }
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(BACKDROP_BLUR) else Modifier),
            RectangleShape,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.55f to Color.Black.copy(alpha = 0.5f),
                    1f to app.siansa.ui.pageBottomColor(tint).copy(alpha = 0.9f),
                ),
            ),
        )
        Row(
            Modifier.fillMaxSize().padding(horizontal = TV_EDGE, vertical = Space.Large),
            horizontalArrangement = Arrangement.spacedBy(Space.Large * 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Animated covers move here as on the phone (Settings › Playback there: Animated covers), and a song's own
        // picture shows when it has one.
        if (!queue) app.siansa.ui.BigCover(song?.cover, song?.albumId, Modifier.size(COVER_SIZE), RoundedCornerShape(Space.Page))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Section)) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    Text(
                        song?.title ?: "Nothing playing",
                        style = if (queue) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (song != null) {
                        // The song's artists and album, each opening its page.
                        val nav = app.siansa.ui.LocalNav.current
                        val artists = song.artists.ifEmpty { listOfNotNull(song.artistId?.let { app.siansa.data.ArtistRef(it, song.artist) }) }
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.Small),
                            verticalArrangement = Arrangement.spacedBy(Space.Small),
                        ) {
                            if (artists.isEmpty()) {
                                Text(song.artist, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            for (a in artists.take(MAX_ARTISTS)) TvChip(a.name, onClick = { nav.navigate(TvRoutes.artist(a.id)) }, strong = true)
                            val albumId = song.albumId
                            if (albumId != null && song.album.isNotBlank()) TvChip(song.album, onClick = { nav.navigate(TvRoutes.album(albumId)) })
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    val fraction = if (progress.durationMs > 0) (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) else 0f
                    // Up from the controls: left and right then go back and forward 10 seconds, for remotes without
                    // rewind and fast-forward keys (those work anywhere in the app, TvActivity).
                    var seeking by remember { mutableStateOf(false) }
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (seeking) SEEKING_HEIGHT else PROGRESS_HEIGHT)
                            .onFocusChanged { seeking = it.isFocused }
                            .onKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (e.key) {
                                    Key.DirectionLeft -> { seekBy(graph, -SEEK_MS); progress = graph.player.progress(); true }
                                    Key.DirectionRight -> { seekBy(graph, SEEK_MS); progress = graph.player.progress(); true }
                                    else -> false
                                }
                            }
                            .focusable(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = if (seeking) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(minutes(progress.positionMs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (seeking) "◀  10 s back   ·   10 s forward  ▶" else details(state.codec, state.transcodeKbps),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (seeking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(minutes(progress.durationMs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.Page), verticalAlignment = Alignment.CenterVertically) {
                    TvIconButton(Icons.Rounded.Shuffle, "Shuffle", onClick = { graph.player.toggleShuffle() }, on = state.shuffle)
                    TvIconButton(Icons.Rounded.SkipPrevious, "Previous", onClick = { graph.player.previous() })
                    TvIconButton(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (state.isPlaying) "Pause" else "Play",
                        onClick = { graph.player.togglePlay() },
                        big = true,
                        modifier = Modifier.focusRequester(playButton),
                    )
                    TvIconButton(Icons.Rounded.SkipNext, "Next", onClick = { graph.player.next() })
                    TvIconButton(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat",
                        onClick = { graph.player.cycleRepeat() },
                        on = state.repeatMode != Player.REPEAT_MODE_OFF,
                    )
                }
                // The rest with their names, on a row of their own that scrolls sideways as the remote moves along it.
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = Space.Small),
                    horizontalArrangement = Arrangement.spacedBy(Space.Small),
                ) {
                    TvButton(if (liked) "Liked" else "Like", if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        onClick = { song?.let { graph.favorites.toggle(it) } }, strong = liked)
                    TvButton("Lyrics", Icons.Rounded.Lyrics, onClick = { panel = if (panel == Panel.LYRICS) Panel.NONE else Panel.LYRICS }, strong = panel == Panel.LYRICS)
                    TvButton("Up next", Icons.AutoMirrored.Rounded.QueueMusic, onClick = { panel = if (panel == Panel.QUEUE) Panel.NONE else Panel.QUEUE },
                        strong = panel == Panel.QUEUE)
                    // A radio from this song: AudioMuse's songs that sound like it when set up, Steady or DJ as on the phone.
                    TvButton("Song radio", Icons.Rounded.Radio, onClick = { song?.let { app.siansa.ui.RadioChooser.song.value = it } })
                    // AudioMuse's song path from this song, queued after it once ready (as on the phone's player).
                    if (graph.settings.current.audioMuseReady) {
                        val nav = app.siansa.ui.LocalNav.current
                        TvButton("Song path", null, onClick = { song?.let { nav.openSongPath(it.id, fromNowPlaying = true) } })
                    }
                    TvButton(sleep?.let { sleepLine(it) } ?: "Sleep timer", Icons.Rounded.Bedtime, onClick = { sleepChoices = true }, strong = sleep != null)
                }
                val timer = sleep
                val next = state.queue.getOrNull(state.index + 1)
                val line = listOfNotNull(
                    next?.takeIf { !queue }?.let { "Next: ${it.title} · ${it.artist}" },
                ).joinToString("   ·   ")
                if (line.isNotEmpty()) {
                    Text(line, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            when (panel) {
                Panel.QUEUE -> UpNext(state.queue, state.index)
                Panel.LYRICS -> LyricsPanel(song, progress.positionMs)
                Panel.NONE -> Unit
            }
        }
    }
    if (sleepChoices) SleepChoices(onDismiss = { sleepChoices = false })
}

private enum class Panel { NONE, QUEUE, LYRICS }

/**
 * The song's lyrics from the server, as on the phone: synced ones follow the song, the line being sung lit and kept in
 * view; others are shown to read along, and down scrolls through them.
 */
@Composable
private fun LyricsPanel(song: app.siansa.data.Song?, positionMs: Long) {
    val graph = LocalGraph.current
    val lyrics by produceState<app.siansa.data.Lyrics?>(null, song?.id) {
        value = song?.let { s -> runCatching { graph.source.lyrics(s) }.onFailure { AppLog.w("Tv", "Lyrics for ${s.id}: ${it.message}") }.getOrNull() }
    }
    val lines = lyrics?.lines?.filter { it.text.isNotBlank() }.orEmpty()
    val synced = lyrics?.synced == true
    val current = if (synced) lines.indexOfLast { (it.startMs ?: Long.MAX_VALUE) <= positionMs } else -1
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(current) { if (current >= 0) list.animateScrollToItem((current - LINES_ABOVE).coerceAtLeast(0)) }
    Column(Modifier.width(QUEUE_WIDTH).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        Text("Lyrics", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        if (lines.isEmpty()) {
            Text(if (lyrics == null && song != null) "No lyrics for this song on your server." else "", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(state = list, contentPadding = PaddingValues(bottom = Space.Large), verticalArrangement = Arrangement.spacedBy(Space.Small),
                modifier = if (synced) Modifier else Modifier.focusable()) {
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

/** The songs after this one: OK plays from that song. */
@Composable
private fun UpNext(queue: List<app.siansa.data.Song>, index: Int) {
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
                    Column(
                        Modifier.fillMaxWidth().tvFocus(shape, onClick = { graph.player.skipTo(index + 1 + i) }, grow = ROW_GROW)
                            .background(MaterialTheme.colorScheme.background, shape).padding(horizontal = Space.Page, vertical = Space.Small),
                        verticalArrangement = Arrangement.spacedBy(Space.Tight),
                    ) {
                        Text(song.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** The sleep timer's choices: stop after a while, or at the end of this song. */
@Composable
private fun SleepChoices(onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val timer = graph.sleepTimer
    val choices = buildList<Pair<String, () -> Unit>> {
        if (timer.state.value != null) add("Turn off the sleep timer" to { timer.cancel() })
        for (m in SLEEP_MINUTES) add("$m minutes" to { timer.start(m, windDown = false) })
        add("At the end of this song" to { timer.startEndOfSong() })
    }
    TvChoices("Sleep timer", choices, onDismiss)
}

/** "Stops in 24 min", or "Stops after this song". */
fun sleepLine(timer: app.siansa.playback.SleepTimerState, now: Long = System.currentTimeMillis()): String {
    val ends = timer.endsAt ?: return "Stops after this song"
    val left = ((ends - now) / 60_000L).coerceAtLeast(0)
    return if (left < 1) "Stops in under a minute" else "Stops in $left min"
}

/** "FLAC", "Opus 160 kbit/s", or nothing until the player knows. */
fun details(codec: String?, kbps: Int): String = when {
    kbps > 0 -> "Opus $kbps kbit/s"
    codec != null -> codec
    else -> ""
}

private const val PROGRESS_TICK_MS = 500L
private const val SEEK_MS = 10_000L
private val COVER_SIZE = 300.dp
private const val BACKDROP_COVER = 300
private const val BACKDROP_SCALE = 1.4f
private val BACKDROP_BLUR = 70.dp
private const val LINES_ABOVE = 2
private const val ROW_GROW = 1.02f
private const val QUEUE_SHOWN = 100
private const val MAX_ARTISTS = 4
private val SLEEP_MINUTES = listOf(15, 30, 45, 60, 90)
private val PROGRESS_HEIGHT = 6.dp
private val SEEKING_HEIGHT = 12.dp

/** Back or forward [deltaMs] in the song playing, kept inside it. */
fun seekBy(graph: app.siansa.AppGraph, deltaMs: Long) {
    val duration = graph.player.progress().durationMs
    val to = (graph.player.positionMs() + deltaMs).coerceAtLeast(0L)
    graph.player.seekTo(if (duration > 0) to.coerceAtMost((duration - END_MARGIN_MS).coerceAtLeast(0L)) else to)
}

private const val END_MARGIN_MS = 1_000L
private val QUEUE_WIDTH = 420.dp
