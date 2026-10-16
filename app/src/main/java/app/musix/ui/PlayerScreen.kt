package app.musix.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
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

private val PlayerColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    surface = Color.Transparent,
    onSurface = Color.White,
    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
)

/**
 * Now playing, in the style of Spotify: the cover blurred into the background, a large cover that
 * opens its album, title and artist with a heart, and the controls below.
 */
@Composable
fun PlayerScreen(nav: NavController) {
    val graph = LocalGraph.current
    val state by graph.player.state.collectAsStateWithLifecycle()
    // Re-read hearts when one changes.
    val heartChanges by graph.favorites.changes.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf(PlayerView.ART) }
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    val song = state.current
    BackHandler(enabled = queueOpen) { queueOpen = false }

    fun openAlbum() {
        val albumId = song?.albumId ?: return
        nav.popBackStack()
        nav.navigate("album/$albumId")
    }

    fun openArtist() {
        val artistId = song?.artistId ?: return
        nav.popBackStack()
        nav.navigate("artist/$artistId")
    }

    MaterialTheme(colorScheme = PlayerColors, typography = MaterialTheme.typography) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // The cover, enlarged and blurred, as the background (a darker plain version before Android 12).
            Cover(
                song?.coverArt,
                300,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.4f; scaleY = 1.4f; alpha = if (Build.VERSION.SDK_INT >= 31) 0.85f else 0.45f }
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(70.dp) else Modifier),
                RectangleShape,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f),
                        0.55f to Color.Black.copy(alpha = 0.45f),
                        1f to Color.Black.copy(alpha = 0.9f),
                    )
                )
            )

            // Swipe up anywhere to open the queue; swipe down to close the player.
            var drag by remember { mutableStateOf(0f) }
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                when {
                                    drag < -120f -> queueOpen = true
                                    drag > 180f -> nav.popBackStack()
                                }
                                drag = 0f
                            },
                            onDragCancel = { drag = 0f },
                        ) { _, dy -> drag += dy }
                    }
                    .padding(horizontal = 24.dp),
            ) {
                TopRow(song, onClose = { nav.popBackStack() }, onAlbum = ::openAlbum)

                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(targetState = view, label = "player-view") { v ->
                        when (v) {
                            PlayerView.ART -> Cover(
                                song?.coverArt,
                                null,
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .shadow(24.dp, RoundedCornerShape(10.dp))
                                    .clickable(enabled = song?.albumId != null) { openAlbum() },
                                RoundedCornerShape(10.dp),
                            )
                            PlayerView.LYRICS -> LyricsView(song)
                            PlayerView.QUEUE -> Box(Modifier.fillMaxSize())
                        }
                    }
                }

                TitleRow(song, liked = song != null && heartChanges.let { graph.favorites.isLiked(song) }, onArtist = ::openArtist) {
                    song?.let { graph.favorites.toggle(it) }
                }
                SeekBar(state.durationMs)
                Controls(state)
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    ViewToggle(Icons.Rounded.FormatQuote, "Lyrics", view == PlayerView.LYRICS) {
                        view = if (view == PlayerView.LYRICS) PlayerView.ART else PlayerView.LYRICS
                    }
                    ViewToggle(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", queueOpen) { queueOpen = true }
                }
                UpNextHandle(state) { queueOpen = true }
            }

            // The queue slides up over the player.
            AnimatedVisibility(
                visible = queueOpen,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                var down by remember { mutableStateOf(0f) }
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xF2121212))
                        .systemBarsPadding(),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (down > 100f) queueOpen = false
                                        down = 0f
                                    },
                                    onDragCancel = { down = 0f },
                                ) { _, dy -> down += dy }
                            }
                            .clickable { queueOpen = false }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.5f)))
                    }
                    QueueView(state)
                }
            }
        }
    }
}

@Composable
private fun TopRow(song: Song?, onClose: () -> Unit, onAlbum: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close", Modifier.size(32.dp)) }
        Column(Modifier.weight(1f).clickable(enabled = song?.albumId != null, onClick = onAlbum), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PLAYING FROM ALBUM", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            Text(
                song?.album.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun TitleRow(song: Song?, liked: Boolean, onArtist: () -> Unit, onHeart: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                song?.title ?: "Nothing playing",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            Text(
                song?.artist.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(enabled = song?.artistId != null, onClick = onArtist),
            )
        }
        IconButton(onClick = onHeart, enabled = song != null) {
            Icon(
                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (liked) "Remove from liked songs" else "Like",
                tint = if (liked) Color(0xFF1ED760) else Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun SeekBar(durationMs: Long) {
    val graph = LocalGraph.current
    val position by rememberPosition()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1L)
    val fraction = dragging ?: (position.toFloat() / duration).coerceIn(0f, 1f)
    Column(Modifier.padding(top = 8.dp)) {
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { graph.player.seekTo((it * duration).toLong()) }
                dragging = null
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatDuration(((fraction * duration) / 1000).toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            Text(formatDuration(durationMs / 1000), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun Controls(state: PlayerState) {
    val graph = LocalGraph.current
    val green = Color(0xFF1ED760)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { graph.player.toggleShuffle() }) {
            Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (state.shuffle) green else Color.White)
        }
        IconButton(onClick = { graph.player.previous() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(40.dp))
        }
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(Color.White).clickable { graph.player.togglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                "Play or pause",
                tint = Color.Black,
                modifier = Modifier.size(38.dp),
            )
        }
        IconButton(onClick = { graph.player.next() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(40.dp))
        }
        IconButton(onClick = { graph.player.cycleRepeat() }) {
            Icon(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Repeat",
                tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) green else Color.White,
            )
        }
    }
}

@Composable
private fun ViewToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, label, tint = if (active) Color(0xFF1ED760) else Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun LyricsView(song: Song?) {
    val graph = LocalGraph.current
    val position by rememberPosition()
    val lyrics by loadState(song?.id) { song?.let { graph.subsonic.lyrics(it) } }
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.3f)).padding(16.dp),
    ) {
        LoadBox(lyrics) { loaded ->
            if (loaded == null || loaded.lines.isEmpty()) {
                Text("No lyrics for this song", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.Center))
                return@LoadBox
            }
            if (!loaded.synced) {
                Text(
                    loaded.lines.joinToString("\n") { it.text },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                )
                return@LoadBox
            }
            val listState = rememberLazyListState()
            val current = loaded.lines.indexOfLast { (it.startMs ?: Long.MAX_VALUE) <= position }
            LaunchedEffect(current) {
                if (current >= 0) listState.animateScrollToItem(max(0, current - 2))
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 24.dp)) {
                itemsIndexed(loaded.lines) { index, line ->
                    val active = index == current
                    Text(
                        line.text.ifBlank { "♪" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (active) Color.White else Color.White.copy(alpha = if (index < current) 0.55f else 0.35f),
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { line.startMs?.let { graph.player.seekTo(it) } }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueView(state: PlayerState) {
    val graph = LocalGraph.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = max(0, state.index - 1))
    if (state.queue.isEmpty()) {
        Text("The queue is empty", color = Color.White.copy(alpha = 0.7f))
        return
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Up next · ${state.queue.size - state.index - 1} of ${state.queue.size}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
        )
        Spacer(Modifier.height(4.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(state.queue, key = { i, s -> "$i-${s.id}" }) { index, song ->
                SongRow(song = song, onClick = { graph.player.skipTo(index) }, highlighted = index == state.index)
            }
        }
    }
}

/** "Up next" line at the bottom of the player: tap or swipe up to see the whole queue. */
@Composable
private fun UpNextHandle(state: PlayerState, onOpen: () -> Unit) {
    val next = state.queue.getOrNull(state.index + 1)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.KeyboardArrowUp, null, tint = Color.White.copy(alpha = 0.7f))
        Text(
            if (next != null) "Up next: ${next.title} · ${next.artist}" else "Queue",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
