package app.musix.ui

import app.musix.data.cover
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
    primary = Accent,
    onPrimary = Color(0xFF2A1700),
    surface = Color.Transparent,
    onSurface = Color.White,
    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
)

/**
 * Now playing, in the style of Spotify: the cover blurred into the background, a large cover that
 * opens its album, title and artist with a heart, and the controls below.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(nav: NavController, onClose: () -> Unit) {
    val graph = LocalGraph.current
    val state by graph.player.state.collectAsStateWithLifecycle()
    // Re-read hearts when one changes.
    val heartChanges by graph.favorites.changes.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf(PlayerView.ART) }
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    val song = state.current
    BackHandler(enabled = queueOpen) { queueOpen = false }
    val scope = rememberCoroutineScope()
    val openArtist = rememberArtistOpener(beforeOpen = onClose)

    fun openAlbum() {
        val albumId = song?.albumId ?: return
        onClose()
        nav.navigate("album/$albumId")
    }

    // Dragging down moves the whole player with the finger; let go far or fast enough and it closes.
    var dismissY by remember { mutableFloatStateOf(0f) }
    var upDrag by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    val velocity = remember { VelocityTracker() }

    MaterialTheme(colorScheme = PlayerColors, typography = MaterialTheme.typography) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
                .graphicsLayer { translationY = dismissY }
                .background(Color.Black)
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            total = 0f
                            upDrag = 0f
                            velocity.resetTracking()
                        },
                        onDragEnd = {
                            val speed = velocity.calculateVelocity().y
                            val close = dismissY > heightPx * 0.22f || (dismissY > 40f && speed > 1400f)
                            when {
                                close -> onClose()
                                dismissY > 0f -> {
                                    val from = dismissY
                                    scope.launch { animate(from, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> dismissY = v } }
                                }
                                upDrag < -120f -> queueOpen = true
                            }
                            upDrag = 0f
                        },
                        onDragCancel = {
                            val from = dismissY
                            scope.launch { animate(from, 0f) { v, _ -> dismissY = v } }
                            upDrag = 0f
                        },
                    ) { change, dy ->
                        total += dy
                        velocity.addPosition(change.uptimeMillis, Offset(0f, total))
                        if (dismissY > 0f || (dy > 0f && upDrag >= 0f)) {
                            dismissY = (dismissY + dy).coerceAtLeast(0f)
                        } else {
                            upDrag += dy
                        }
                    }
                },
        ) {
            // The cover, enlarged and blurred, as the background (a darker plain version before Android 12).
            Cover(
                song?.cover,
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
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp),
            ) {
                TopRow(song, onClose = onClose, onAlbum = ::openAlbum)

                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = view,
                        transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) },
                        label = "player-view",
                    ) { v ->
                        when (v) {
                            PlayerView.ART -> SwipeCover(song, onTap = ::openAlbum)
                            PlayerView.LYRICS -> LyricsView(song)
                            PlayerView.QUEUE -> Box(Modifier.fillMaxSize())
                        }
                    }
                }

                TitleRow(song, liked = song != null && heartChanges.let { graph.favorites.isLiked(song) }, onArtist = { song?.let(openArtist) }) {
                    song?.let { graph.favorites.toggle(it) }
                }
                SeekBar(state.durationMs)
                Controls(state)
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    ViewToggle(Icons.Rounded.FormatQuote, "Lyrics", view == PlayerView.LYRICS) {
                        view = if (view == PlayerView.LYRICS) PlayerView.ART else PlayerView.LYRICS
                    }
                    // Song radio: AudioMuse keeps adding songs that sound like this one and share its mood.
                    val radioSeed by graph.songRadio.seed.collectAsStateWithLifecycle()
                    val snack = LocalSnack.current
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (radioSeed != null) Accent.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable(enabled = song != null) {
                                if (radioSeed != null) {
                                    graph.songRadio.stop()
                                    scope.launch { snack.showSnackbar("Song radio off") }
                                } else song?.let { s ->
                                    scope.launch { snack.showSnackbar("Starting song radio from ${s.title}…") }
                                    graph.songRadio.start(s) { message -> scope.launch { snack.showSnackbar(message) } }
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Radio, null, tint = if (radioSeed != null) Accent else Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (radioSeed != null) "Radio on" else "Song radio",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (radioSeed != null) Accent else Color.White.copy(alpha = 0.8f),
                        )
                    }
                    ViewToggle(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", queueOpen) { queueOpen = true }
                }
                UpNextHandle(state) { queueOpen = true }
            }

            // The queue slides up over the player.
            AnimatedVisibility(
                visible = queueOpen,
                enter = slideInVertically(tween(230, easing = FastOutSlowInEasing)) { it },
                exit = slideOutVertically(tween(180, easing = FastOutLinearInEasing)) { it },
            ) {
                // Swipe down anywhere on the queue to close it: the list scrolls back to the top first,
                // then the panel follows the finger down.
                var queueY by remember { mutableFloatStateOf(0f) }
                var panelHeight by remember { mutableFloatStateOf(1f) }
                fun settle(speed: Float) {
                    if (queueY > panelHeight * 0.2f || (queueY > 30f && speed > 1200f)) {
                        queueOpen = false
                    } else if (queueY > 0f) {
                        val from = queueY
                        scope.launch { animate(from, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> queueY = v } }
                    }
                }
                val connection = remember {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (queueY > 0f && available.y < 0f) {
                                val used = max(available.y, -queueY)
                                queueY += used
                                return Offset(0f, used)
                            }
                            return Offset.Zero
                        }

                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                            if (available.y > 0f && source == NestedScrollSource.UserInput) {
                                queueY += available.y
                                return Offset(0f, available.y)
                            }
                            return Offset.Zero
                        }

                        override suspend fun onPreFling(available: Velocity): Velocity {
                            if (queueY > 0f) {
                                settle(available.y)
                                return available
                            }
                            return Velocity.Zero
                        }
                    }
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { panelHeight = it.height.toFloat().coerceAtLeast(1f) }
                        .graphicsLayer { translationY = queueY }
                        .background(Color(0xF2121212))
                        .nestedScroll(connection)
                        .pointerInput(Unit) {
                            // Drags on the parts that don't scroll (the handle and the heading).
                            val tracker = VelocityTracker()
                            var total = 0f
                            detectVerticalDragGestures(
                                onDragStart = { total = 0f; tracker.resetTracking() },
                                onDragEnd = { settle(tracker.calculateVelocity().y) },
                                onDragCancel = { settle(0f) },
                            ) { change, dy ->
                                total += dy
                                tracker.addPosition(change.uptimeMillis, Offset(0f, total))
                                queueY = (queueY + dy).coerceAtLeast(0f)
                            }
                        }
                        .systemBarsPadding(),
                ) {
                    LaunchedEffect(Unit) { queueY = 0f }
                    Box(
                        Modifier
                            .fillMaxWidth()
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

/**
 * The big cover. Swipe it left for the next song and right for the previous one: it follows the finger,
 * slides out, and the new cover slides in from the other side. Tap opens the album.
 */
@Composable
private fun SwipeCover(song: Song?, onTap: () -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val currentId by rememberUpdatedState(song?.id)
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)

        suspend fun change(forward: Boolean) {
            val before = currentId
            offset.animateTo(if (forward) -width * 1.15f else width * 1.15f, tween(130, easing = FastOutLinearInEasing))
            if (forward) graph.player.next() else graph.player.previousTrack()
            // Wait for the new song before bringing a cover back in, so the old one doesn't flash.
            withTimeoutOrNull(600) { snapshotFlow { currentId }.first { it != before } }
            offset.snapTo(if (forward) width * 1.15f else -width * 1.15f)
            offset.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        }

        val coverModifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offset.value
                    alpha = 1f - (kotlin.math.abs(offset.value) / width).coerceIn(0f, 1f) * 0.5f
                }
                .shadow(24.dp, RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val v = offset.value
                                when {
                                    v < -width * 0.25f && graph.player.hasNext() -> change(forward = true)
                                    v > width * 0.25f -> change(forward = false)
                                    else -> offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    ) { change, dx ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + dx) }
                    }
                }
                .clickable(enabled = song?.albumId != null, onClick = onTap)
        BigCover(song?.cover, song?.albumId, coverModifier, RoundedCornerShape(10.dp))
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
                modifier = Modifier.clickable(enabled = song?.artistId != null || song?.artists?.isNotEmpty() == true, onClick = onArtist),
            )
        }
        IconButton(onClick = onHeart, enabled = song != null) {
            Icon(
                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (liked) "Remove from liked songs" else "Like",
                tint = if (liked) Accent else Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * A thin line with a small round thumb: amber for what's played, faint for the rest. The thumb grows
 * while you drag, and the time shown follows your finger.
 */
@Composable
private fun SeekBar(durationMs: Long) {
    val graph = LocalGraph.current
    val position by rememberPosition()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1L)
    val fraction = dragging ?: (position.toFloat() / duration).coerceIn(0f, 1f)
    val thumb by animateDpAsState(if (dragging != null) 16.dp else 11.dp, tween(100), label = "thumb")
    Column(Modifier.padding(top = 14.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(duration) {
                    detectTapGestures { offset -> graph.player.seekTo(((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong()) }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragging = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragging?.let { graph.player.seekTo((it * duration).toLong()) }
                            dragging = null
                        },
                        onDragCancel = { dragging = null },
                    ) { change, _ -> dragging = (change.position.x / size.width).coerceIn(0f, 1f) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val width = maxWidth
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.22f)))
            Box(Modifier.width(width * fraction).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Accent))
            Box(
                Modifier
                    .offset(x = (width * fraction - thumb / 2).coerceIn(0.dp, width - thumb))
                    .size(thumb)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatDuration(((fraction * duration) / 1000).toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            Text("-" + formatDuration(((duration - fraction * duration) / 1000).toLong()), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Controls(state: PlayerState) {
    val graph = LocalGraph.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { graph.player.toggleShuffle() }) {
            Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (state.shuffle) Accent else Color.White)
        }
        IconButton(onClick = { graph.player.previous() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(40.dp))
        }
        // Tap to play or pause; press and hold to stop and clear the queue.
        val haptics = LocalHapticFeedback.current
        Box(
            Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Accent)
                .combinedClickable(
                    onClick = { graph.player.togglePlay() },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        graph.player.stop()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                "Play or pause",
                tint = Color(0xFF2A1700),
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
                tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) Accent else Color.White,
            )
        }
    }
}

@Composable
private fun ViewToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, label, tint = if (active) Accent else Color.White.copy(alpha = 0.8f))
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
