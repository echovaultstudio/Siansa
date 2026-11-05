package app.siansa.tvapp

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Radio
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
import app.siansa.ui.LocalGraph
import app.siansa.ui.Space
import kotlinx.coroutines.delay

/**
 * The big now playing (issue #860): the cover, the song and its artist large enough to read across the room, the time,
 * the controls, and what's up next. The remote's media keys work everywhere through the media session; here rewind and
 * fast forward also skip back and forward 10 seconds.
 *
 * Kept calm: the cover, the song, the time and two rows of round buttons, the playing controls and then the rest, whose
 * name shows under them as the remote reaches each. Only the time and the lyrics redraw while a song plays.
 */
@Composable
fun TvNowPlaying() {
    val graph = LocalGraph.current
    val state by graph.player.state.collectAsStateWithLifecycle()
    val likes by graph.favorites.changes.collectAsStateWithLifecycle()
    val sleep by graph.sleepTimer.state.collectAsStateWithLifecycle()
    // Read only by the parts that show it, so the rest of the page doesn't redraw twice a second.
    val progress = remember { mutableStateOf(graph.player.progress()) }
    // What the right side shows instead of the cover: the songs up next, or the lyrics.
    var panel by remember { mutableStateOf(Panel.NONE) }
    val queue = panel != Panel.NONE
    var sleepChoices by remember { mutableStateOf(false) }
    // The name of the button the remote is on, in the row of other buttons.
    var focusedName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            progress.value = graph.player.progress()
            delay(PROGRESS_TICK_MS)
        }
    }
    val playButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playButton.requestFocus() } }
    val song = state.current
    val liked = song != null && (likes[song.id] ?: song.starred)
    val nav = app.siansa.ui.LocalNav.current

    val tint = app.siansa.ui.rememberCoverColor(song?.cover)
    Box(Modifier.fillMaxSize().clipToBounds()) {
        TvBackdrop(song?.cover, app.siansa.ui.pageBottomColor(tint))
        Row(
            Modifier.fillMaxSize().padding(horizontal = TV_EDGE * 2, vertical = Space.Large),
            horizontalArrangement = Arrangement.spacedBy(Space.Large * 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Animated covers move here as on the phone (Settings › Playback there: Animated covers), and a song's own
            // picture shows when it has one.
            if (!queue) app.siansa.ui.BigCover(song?.cover, song?.albumId, Modifier.size(COVER_SIZE), RoundedCornerShape(Space.Page))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Section)) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
                    Text(
                        song?.title ?: "Nothing playing",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (song != null) {
                        // The song's artists and album, each opening its page.
                        val artists = song.artists.ifEmpty { listOfNotNull(song.artistId?.let { app.siansa.data.ArtistRef(it, song.artist) }) }
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.Medium),
                            verticalArrangement = Arrangement.spacedBy(Space.Medium),
                        ) {
                            if (artists.isEmpty()) {
                                Text(song.artist, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            for (a in artists.take(MAX_ARTISTS)) TvChip(a.name, onClick = { nav.navigate(TvRoutes.artist(a.id)) }, strong = true)
                            val albumId = song.albumId
                            if (albumId != null && song.album.isNotBlank()) TvChip(song.album, onClick = { nav.navigate(TvRoutes.album(albumId)) })
                        }
                    }
                }
                ProgressBlock(progress, details(state.codec, state.transcodeKbps))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.Section), verticalAlignment = Alignment.CenterVertically) {
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
                // The rest, as round buttons too; the one the remote is on says its name underneath.
                val likeName = if (liked) "Liked" else "Like"
                val sleepName = sleep?.let { sleepLine(it) } ?: "Sleep timer"
                Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    // Wraps onto a second line when Up next or Lyrics leaves less room.
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.Page),
                        verticalArrangement = Arrangement.spacedBy(Space.Page),
                    ) {
                        fun Modifier.named(name: String) = onFocusChanged { f ->
                            if (f.isFocused) focusedName = name else if (focusedName == name) focusedName = null
                        }
                        TvIconButton(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, likeName,
                            onClick = { song?.let { graph.favorites.toggle(it) } }, on = liked, modifier = Modifier.named(LIKE))
                        TvIconButton(Icons.Rounded.Lyrics, "Lyrics", onClick = { panel = if (panel == Panel.LYRICS) Panel.NONE else Panel.LYRICS },
                            on = panel == Panel.LYRICS, modifier = Modifier.named("Lyrics"))
                        TvIconButton(Icons.AutoMirrored.Rounded.QueueMusic, "Up next", onClick = { panel = if (panel == Panel.QUEUE) Panel.NONE else Panel.QUEUE },
                            on = panel == Panel.QUEUE, modifier = Modifier.named("Up next"))
                        // A radio from this song: AudioMuse's songs that sound like it when set up, Steady or DJ as on the phone.
                        TvIconButton(Icons.Rounded.Radio, "Song radio", onClick = { song?.let { app.siansa.ui.RadioChooser.song.value = it } },
                            modifier = Modifier.named("Song radio"))
                        // AudioMuse's song path from this song, queued after it once ready (as on the phone's player).
                        if (graph.settings.current.audioMuseReady) {
                            TvIconButton(Icons.Rounded.AltRoute, "Song path", onClick = { song?.let { nav.openSongPath(it.id, fromNowPlaying = true) } },
                                modifier = Modifier.named("Song path"))
                        }
                        TvIconButton(Icons.Rounded.Bedtime, sleepName, onClick = { sleepChoices = true }, on = sleep != null, modifier = Modifier.named(SLEEP))
                    }
                    // Always there, so the page doesn't move as the name comes and goes.
                    Text(
                        when (focusedName) {
                            LIKE -> likeName
                            SLEEP -> sleepName
                            null -> sleep?.let { sleepLine(it) } ?: ""
                            else -> focusedName.orEmpty()
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when (panel) {
                Panel.QUEUE -> UpNext(state.queue, state.index)
                Panel.LYRICS -> LyricsPanel(song) { progress.value.positionMs }
                Panel.NONE -> Unit
            }
        }
    }
    if (sleepChoices) SleepChoices(onDismiss = { sleepChoices = false })
}

/**
 * The time: a bar, where the song is and how long it is. Up from the controls the remote reaches the bar, and left and
 * right then go back and forward 10 seconds, for remotes without rewind and fast-forward keys (those work anywhere in
 * the app, TvActivity).
 */
@Composable
private fun ProgressBlock(progress: androidx.compose.runtime.MutableState<app.siansa.playback.PlayerConnection.Progress>, details: String) {
    val graph = LocalGraph.current
    val now = progress.value
    val fraction = if (now.durationMs > 0) (now.positionMs.toFloat() / now.durationMs).coerceIn(0f, 1f) else 0f
    var seeking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(if (seeking) SEEKING_HEIGHT else PROGRESS_HEIGHT)
                .onFocusChanged { seeking = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> { seekBy(graph, -SEEK_MS); progress.value = graph.player.progress(); true }
                        Key.DirectionRight -> { seekBy(graph, SEEK_MS); progress.value = graph.player.progress(); true }
                        else -> false
                    }
                }
                .focusable(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = if (seeking) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = TRACK_ALPHA),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(minutes(now.positionMs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (seeking) "◀  10 s back   ·   10 s forward  ▶" else details,
                style = MaterialTheme.typography.titleMedium,
                color = if (seeking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(minutes(now.durationMs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class Panel { NONE, QUEUE, LYRICS }

/** The buttons whose name changes (Like or Liked, the sleep timer's time left), by what they are. */
private const val LIKE = "like"
private const val SLEEP = "sleep"

/**
 * The song's lyrics from the server, as on the phone: synced ones follow the song, the line being sung lit and kept in
 * view; others are shown to read along, and down scrolls through them.
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
private val COVER_SIZE = 320.dp
private const val TRACK_ALPHA = 0.25f
private const val LINES_ABOVE = 2
private const val ROW_GROW = 1.02f
private const val QUEUE_SHOWN = 100
private const val MAX_ARTISTS = 3
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
