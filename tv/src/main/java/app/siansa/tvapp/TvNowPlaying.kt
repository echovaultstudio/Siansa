package app.siansa.tvapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import app.siansa.ui.Cover
import app.siansa.ui.LARGE_COVER
import app.siansa.ui.LocalGraph
import app.siansa.ui.Space
import kotlinx.coroutines.delay

/**
 * The big now playing (issue #860): the cover, the song and its artist large enough to read across the room, the
 * time, and the controls. The remote's media keys work everywhere through the media session; here rewind and fast
 * forward also skip back and forward 10 seconds.
 */
@Composable
fun TvNowPlaying() {
    val graph = LocalGraph.current
    val state by graph.player.state.collectAsStateWithLifecycle()
    var progress by remember { mutableStateOf(graph.player.progress()) }
    LaunchedEffect(Unit) {
        while (true) {
            progress = graph.player.progress()
            delay(PROGRESS_TICK_MS)
        }
    }
    val playButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playButton.requestFocus() } }
    val song = state.current

    Row(
        Modifier.fillMaxSize().padding(horizontal = TV_EDGE * 2, vertical = Space.Large),
        horizontalArrangement = Arrangement.spacedBy(Space.Large * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(song?.coverArt, LARGE_COVER, Modifier.fillMaxHeight(COVER_HEIGHT).aspectRatio(1f), RoundedCornerShape(Space.Page))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Section)) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                Text(
                    song?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (song != null) {
                    Text(song.artist, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.album, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                val fraction = if (progress.durationMs > 0) (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(PROGRESS_HEIGHT),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(minutes(progress.positionMs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(details(state.codec, state.transcodeKbps), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(minutes(progress.durationMs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.Section), verticalAlignment = Alignment.CenterVertically) {
                TvIconButton(Icons.Rounded.Shuffle, "Shuffle", onClick = { graph.player.toggleShuffle() }, on = state.shuffle)
                TvIconButton(Icons.Rounded.SkipPrevious, "Previous", onClick = { graph.player.previous() })
                TvIconButton(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play",
                    onClick = { graph.player.togglePlay() },
                    big = true,
                    modifier = Modifier.focusRequester(playButton).onPreviewKeyEvent { e ->
                        // The remote's rewind and fast-forward keys: back and forward 10 s, as on most TV players.
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.MediaRewind -> { graph.player.seekTo((graph.player.positionMs() - SEEK_MS).coerceAtLeast(0)); true }
                            Key.MediaFastForward -> { graph.player.seekTo(graph.player.positionMs() + SEEK_MS); true }
                            else -> false
                        }
                    },
                )
                TvIconButton(Icons.Rounded.SkipNext, "Next", onClick = { graph.player.next() })
                TvIconButton(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat",
                    onClick = { graph.player.cycleRepeat() },
                    on = state.repeatMode != Player.REPEAT_MODE_OFF,
                )
            }
            val next = state.queue.getOrNull(state.index + 1)
            if (next != null) {
                Box(Modifier.padding(top = Space.Page)) {
                    Text("Next: ${next.title} · ${next.artist}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private const val SEEK_MS = 10_000L
private const val COVER_HEIGHT = 0.8f
private val PROGRESS_HEIGHT = 6.dp
