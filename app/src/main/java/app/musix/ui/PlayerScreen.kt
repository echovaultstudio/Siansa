package app.musix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation.NavController
import app.musix.data.Song
import app.musix.playback.PlayerState
import kotlin.math.max

private enum class PlayerView { ART, LYRICS, QUEUE }

@Composable
fun PlayerScreen(nav: NavController) {
    val graph = LocalGraph.current
    val state by graph.player.state.collectAsStateWithLifecycle()
    val position by rememberPosition()
    var view by rememberSaveable { mutableStateOf(PlayerView.ART) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val song = state.current

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.KeyboardArrowDown, "Close") }
            Text(
                "Now playing",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(48.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            when (view) {
                PlayerView.ART -> Cover(
                    song?.coverArt,
                    null,
                    Modifier.fillMaxWidth().aspectRatio(1f),
                    RoundedCornerShape(16.dp),
                )
                PlayerView.LYRICS -> LyricsView(song, position)
                PlayerView.QUEUE -> QueueView(state)
            }
        }

        Text(
            song?.title ?: "Nothing playing",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(song?.artist, song?.album).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(enabled = song?.albumId != null) {
                song?.albumId?.let { nav.navigate("album/$it") }
            },
        )

        val duration = state.durationMs.coerceAtLeast(1L)
        val fraction = dragging ?: (position.toFloat() / duration).coerceIn(0f, 1f)
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { graph.player.seekTo((it * duration).toLong()) }
                dragging = null
            },
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatDuration(((fraction * duration) / 1000).toLong()),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(formatDuration(state.durationMs / 1000), style = MaterialTheme.typography.bodySmall)
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconToggleButton(checked = state.shuffle, onCheckedChange = { graph.player.toggleShuffle() }) {
                Icon(
                    Icons.Rounded.Shuffle,
                    "Shuffle",
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { graph.player.previous() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(36.dp))
            }
            FilledIconButton(onClick = { graph.player.togglePlay() }, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    "Play or pause",
                    Modifier.size(40.dp),
                )
            }
            IconButton(onClick = { graph.player.next() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(36.dp))
            }
            IconToggleButton(
                checked = state.repeatMode != Player.REPEAT_MODE_OFF,
                onCheckedChange = { graph.player.cycleRepeat() },
            ) {
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Repeat",
                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconToggleButton(
                checked = view == PlayerView.LYRICS,
                onCheckedChange = { view = if (it) PlayerView.LYRICS else PlayerView.ART },
            ) {
                Icon(
                    Icons.Rounded.FormatQuote,
                    "Lyrics",
                    tint = if (view == PlayerView.LYRICS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconToggleButton(
                checked = view == PlayerView.QUEUE,
                onCheckedChange = { view = if (it) PlayerView.QUEUE else PlayerView.ART },
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    "Queue",
                    tint = if (view == PlayerView.QUEUE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LyricsView(song: Song?, positionMs: Long) {
    val graph = LocalGraph.current
    val lyrics by loadState(song?.id) { song?.let { graph.subsonic.lyrics(it) } }
    LoadBox(lyrics) { loaded ->
        if (loaded == null || loaded.lines.isEmpty()) {
            Text("No lyrics for this song", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@LoadBox
        }
        if (!loaded.synced) {
            Text(
                loaded.lines.joinToString("\n") { it.text },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            )
            return@LoadBox
        }
        val listState = rememberLazyListState()
        val current = loaded.lines.indexOfLast { (it.startMs ?: Long.MAX_VALUE) <= positionMs }
        LaunchedEffect(current) {
            if (current >= 0) listState.animateScrollToItem(max(0, current - 2))
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 24.dp)) {
            itemsIndexed(loaded.lines) { index, line ->
                val active = index == current
                Text(
                    line.text.ifBlank { "♪" },
                    style = if (active) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { line.startMs?.let { graph.player.seekTo(it) } }
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun QueueView(state: PlayerState) {
    val graph = LocalGraph.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = max(0, state.index - 1))
    if (state.queue.isEmpty()) {
        Text("The queue is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Queue · ${state.queue.size} tracks",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Spacer(Modifier.height(4.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(state.queue, key = { i, s -> "$i-${s.id}" }) { index, song ->
                SongRow(
                    song = song,
                    onClick = { graph.player.skipTo(index) },
                    highlighted = index == state.index,
                )
            }
        }
    }
}
