package app.musix.ui

import androidx.compose.ui.platform.testTag
import android.os.SystemClock
import app.musix.log.AppLog
import app.musix.playback.SeekTracker
import kotlinx.coroutines.delay
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
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation.NavController
import app.musix.data.Song
import app.musix.playback.AudioOutput
import app.musix.playback.Cast
import app.musix.playback.PlayerState
import kotlin.math.max

/** Now playing's colours: white on the cover's colours, with the theme's accent for buttons. */
@Composable
private fun playerColors() = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    surface = Color.Transparent,
    onSurface = Color.White,
    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
)

/**
 * Now playing, in the style of Spotify: the cover blurred into the background, a large cover that
 * opens its album, title and artist with a heart, the controls, where the music plays (bottom left) and
 * the queue (bottom right). The ⋮ menu has everything else. Scrolling down shows the lyrics, the artist
 * and the song's details on cards; they stay out of sight until then (#294), and the lyrics have their own
 * button, bottom left.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(nav: NavController, onClose: () -> Unit) {
    val graph = LocalGraph.current
    val state by graph.player.state.collectAsStateWithLifecycle()
    // The covers either side, ready before a swipe brings them in.
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    LaunchedEffect(state.index, state.queue.size) {
        listOf(state.index + 1, state.index - 1).mapNotNull { state.queue.getOrNull(it) }.forEach { neighbour ->
            runCatching { AnimatedCovers.preload(appContext, graph, neighbour) }
        }
    }
    // Re-read hearts when one changes.
    val heartChanges by graph.favorites.changes.collectAsStateWithLifecycle()
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    // The mini player's lyrics button opens now playing with the lyrics showing (issue #346).
    val lyricsWanted by LyricsShortcut.pending.collectAsStateWithLifecycle()
    LaunchedEffect(lyricsWanted) {
        if (lyricsWanted) {
            lyricsOpen = true
            LyricsShortcut.pending.value = false
        }
    }
    // Settings › Style › Now Playing Style: off brings back the look before the lyrics button (issue #346).
    val style by graph.settings.state.collectAsStateWithLifecycle()
    val classic = !style.nowPlayingLyricsButton
    var menuOpen by remember { mutableStateOf(false) }
    var sleepOpen by rememberSaveable { mutableStateOf(false) }
    val song = state.current
    BackHandler(enabled = queueOpen) { queueOpen = false }
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val openArtist = rememberArtistOpener(beforeOpen = onClose)
    val scroll = rememberScrollState()
    // The cover's colour, for the lyrics card and the lyrics screen.
    val tint = rememberCoverColor(song?.cover)
    // The song's lyrics, for the lyrics button and the preview card. Cleared as the song changes, so the
    // button doesn't stay lit for a song without lyrics.
    val lyrics by loadStateFresh("lyrics-${song?.id}") { song?.let { graph.subsonic.lyrics(it) } }
    val songLyrics = lyrics.valueOrNull()
    val close by rememberUpdatedState(onClose)

    fun openAlbum() {
        val albumId = song?.albumId ?: return
        onClose()
        nav.navigate("album/$albumId")
    }

    fun openDetails() {
        val id = song?.id ?: return
        onClose()
        nav.openSongDetails(id)
    }

    fun openSimilarSongs() {
        val id = song?.id ?: return
        onClose()
        nav.openSimilarSongs(id)
    }

    fun openSimilarAlbums() {
        val id = song?.id ?: return
        onClose()
        nav.openSimilarAlbums(id)
    }

    fun openSongPath() {
        val id = song?.id ?: return
        onClose()
        // From now playing, the path is queued straight after this song once it's ready (#212).
        nav.openSongPath(id, fromNowPlaying = true)
    }

    // Dragging down (at the top of the page) moves the whole player with the finger; let go far or fast
    // enough and it closes. Swiping up scrolls down to the cards under the player.
    var dismissY by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    val cornerMaxPx = with(LocalDensity.current) { 28.dp.toPx() }
    fun settle(speed: Float) {
        if (dismissY > heightPx * 0.22f || (dismissY > 40f && speed > 1400f)) {
            close()
        } else if (dismissY > 0f) {
            val from = dismissY
            scope.launch { animate(from, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> dismissY = v } }
        }
    }
    val dismissOnPull = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (dismissY > 0f && available.y < 0f) {
                    val used = max(available.y, -dismissY)
                    dismissY += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    dismissY += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dismissY > 0f) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    MaterialTheme(colorScheme = playerColors(), typography = MaterialTheme.typography) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
                // One solid card: everything is clipped to it (the enlarged, blurred cover used to show
                // through above it), and its top corners round as it is dragged down.
                .graphicsLayer {
                    translationY = dismissY
                    val corner = playerCardCornerPx(dismissY, cornerMaxPx)
                    shape = RoundedCornerShape(topStart = corner, topEnd = corner)
                    clip = true
                }
                .background(Color.Black)
                .nestedScroll(dismissOnPull),
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

            val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // The player fills the screen; the cards below only come into view when scrolling, so none
                // sits under the phone's own buttons (#294). The classic style leaves the top of the lyrics card
                // peeking in at the bottom, as in Spotify, to show there's more below (issue #346).
                val pageHeight = if (classic) maxHeight - PLAYER_PEEK - navBar else maxHeight
                val pageHeightPx = with(LocalDensity.current) { pageHeight.toPx() }
                val queueButton = @Composable {
                    RoundIcon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        "Queue",
                        Modifier.pointerInput(Unit) {
                            var pulled = 0f
                            detectVerticalDragGestures(onDragStart = { pulled = 0f }) { change, dy ->
                                change.consume()
                                pulled += dy
                                if (pulled < -QUEUE_PULL_PX) queueOpen = true
                            }
                        },
                    ) { queueOpen = true }
                }
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    Column(
                        Modifier
                            .height(pageHeight)
                            .statusBarsPadding()
                            .padding(horizontal = Space.Section)
                            .then(if (classic) Modifier else Modifier.padding(bottom = navBar)),
                    ) {
                        // A gap above the header that suits the screen's height, so the top of the player
                        // isn't pressed against the status bar.
                        Spacer(Modifier.height(playerTopGapDp(LocalConfiguration.current.screenHeightDp).dp))
                        TopRow(song, onClose = onClose, onAlbum = ::openAlbum, onMenu = { menuOpen = true }, onSleep = { sleepOpen = true })

                        Box(Modifier.weight(1f).fillMaxWidth().padding(top = Space.Page, bottom = Space.Medium), contentAlignment = Alignment.Center) {
                            SwipeCover(song, onTap = ::openAlbum)
                        }

                        TitleRow(song, liked = song != null && heartChanges.let { graph.favorites.isLiked(song) }, onArtist = { song?.let(openArtist) }) {
                            song?.let { graph.favorites.toggle(it) }
                        }
                        SeekBar(state)
                        Controls(state)
                        // The lyrics bottom left (greyed out when the song has none), where the music plays in
                        // the middle (with the TV, speaker or headphones' name), and the queue bottom right: tap
                        // it, or swipe up from it (#294).
                        if (classic) {
                            // The classic style (before #294): where the music plays bottom left, the queue bottom right.
                            Row(Modifier.fillMaxWidth().padding(top = Space.Medium, bottom = Space.Small).testTag("classicBottomRow"), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    CastDeviceButton(tint = Color.White.copy(alpha = 0.8f))
                                }
                                queueButton()
                            }
                        } else Row(Modifier.fillMaxWidth().padding(top = Space.Medium, bottom = Space.Small), verticalAlignment = Alignment.CenterVertically) {
                            // The device name gets all the room between the two buttons (issue #311).
                            Box(Modifier.width(SIDE_BUTTON_WIDTH), contentAlignment = Alignment.Center) {
                                LyricsButton(enabled = hasLyrics(songLyrics)) { lyricsOpen = true }
                            }
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                CastDeviceButton(tint = Color.White.copy(alpha = 0.8f))
                            }
                            Box(Modifier.width(SIDE_BUTTON_WIDTH), contentAlignment = Alignment.Center) {
                                queueButton()
                            }
                        }
                    }
                    if (song != null) {
                        LyricsPreviewCard(songLyrics, tint) { lyricsOpen = true }
                        Spacer(Modifier.height(Space.Medium))
                        AboutArtistCard(song) { openArtist(song) }
                        Spacer(Modifier.height(Space.Medium))
                        AboutSongCard(song, onOpenDetails = ::openDetails)
                    }
                    Spacer(Modifier.height(Space.Section + navBar))
                }

                // Once the cards are in view, a small header keeps the song and play/pause at hand.
                val position by rememberPosition()
                AnimatedVisibility(
                    visible = scroll.value > pageHeightPx * 0.6f,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(120)),
                ) {
                    val duration = (song?.durationSec ?: 0) * 1000f
                    PlayerMiniHeader(
                        song = song,
                        isPlaying = state.isPlaying,
                        progress = if (duration > 0f) position / duration else 0f,
                        liked = song != null && heartChanges.let { graph.favorites.isLiked(song) },
                        onHeart = { song?.let { graph.favorites.toggle(it) } },
                        onPlayPause = { graph.player.togglePlay() },
                        onTop = { scope.launch { scroll.animateScrollTo(0) } },
                    )
                }
            }

            // Lyrics, opened from the card or the menu, over the whole player.
            AnimatedVisibility(
                visible = lyricsOpen,
                enter = slideInVertically(tween(230, easing = FastOutSlowInEasing)) { it },
                exit = slideOutVertically(tween(180, easing = FastOutLinearInEasing)) { it },
            ) {
                LyricsScreen(song, tint, onClose = { lyricsOpen = false }, menu = { menuOpen = true }) {
                    Column {
                        SeekBar(state)
                        Controls(state)
                    }
                }
            }

            if (menuOpen && song != null) {
                PlayerMenu(
                    song = song,
                    onDismiss = { menuOpen = false },
                    onOpenAlbum = ::openAlbum,
                    onOpenArtist = { openArtist(song) },
                    onOpenLyrics = { lyricsOpen = true },
                    onOpenDetails = ::openDetails,
                    onOpenSongPath = ::openSongPath,
                    onOpenSimilarSongs = ::openSimilarSongs,
                    onOpenSimilarAlbums = ::openSimilarAlbums,
                    onOpenSleepTimer = { sleepOpen = true },
                    notify = { message -> scope.launch { snack.showSnackbar(message) } },
                )
            }
            if (sleepOpen) SleepTimerSheet(hasSong = song != null) { sleepOpen = false }

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
                            .padding(vertical = Space.Medium),
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
private fun TopRow(song: Song?, onClose: () -> Unit, onAlbum: () -> Unit, onMenu: () -> Unit, onSleep: () -> Unit) {
    val timerRunning = LocalGraph.current.sleepTimer.state.collectAsStateWithLifecycle().value != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close", Modifier.size(32.dp)) }
        if (timerRunning) Spacer(Modifier.size(48.dp))  // spacing: exact (the width of the button it balances)
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
        SleepIndicator(onClick = onSleep)
        IconButton(onClick = onMenu, enabled = song != null) { Icon(Icons.Rounded.MoreVert, "Song options") }
    }
}

/**
 * While a sleep timer runs, the moon with the time left shows in the top row (as Spotify does); it opens the
 * timer. Otherwise the timer is only in the ⋮ menu, so the player stays uncluttered.
 */
@Composable
private fun SleepIndicator(onClick: () -> Unit) {
    val graph = LocalGraph.current
    val timer by graph.sleepTimer.state.collectAsStateWithLifecycle()
    if (timer != null) SleepTimerButton(onClick = onClick)
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
private fun SeekBar(state: PlayerState) {
    val graph = LocalGraph.current
    val tracker = remember { SeekTracker() }
    var shown by remember { mutableStateOf(graph.player.progress().let { SeekTracker.Shown(it.positionMs, it.durationMs) }) }
    // Read the player every frame (its position is estimated locally, so this is cheap) for smooth movement.
    LaunchedEffect(tracker) {
        var lastTrack: String? = null
        while (true) {
            val p = graph.player.progress()
            shown = tracker.update(p.mediaId, p.positionMs, p.durationMs, p.playing, SystemClock.elapsedRealtime())
            if (p.mediaId != lastTrack) {
                AppLog.d("Player", "Seek bar: new song, starts at ${p.positionMs} ms of ${p.durationMs} ms")
                lastTrack = p.mediaId
            }
            // Five times a second is smooth for a thin bar (a 4-minute song moves it about a pixel each time),
            // and between updates the screen is idle: no redrawing every frame (battery, and the emulator
            // tests that wait for the screen to settle, issue #162).
            delay(if (p.playing) 200 else 400)
        }
    }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = shown.durationMs.coerceAtLeast(1L)
    val fraction = dragging ?: shown.fraction
    fun seek(to: Float) {
        val ms = (to * duration).toLong()
        tracker.seekTo(ms, SystemClock.elapsedRealtime())
        shown = SeekTracker.Shown(ms, shown.durationMs)
        AppLog.d("Player", "Seek to $ms ms of $duration ms")
        graph.player.seekTo(ms)
    }
    val thumb by animateDpAsState(if (dragging != null) 16.dp else 11.dp, tween(100), label = "thumb")
    Column(Modifier.padding(top = Space.Medium)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(duration) {
                    detectTapGestures { offset -> seek((offset.x / size.width).coerceIn(0f, 1f)) }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragging = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragging?.let { seek(it) }
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatDuration(((fraction * duration) / 1000).toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            Text(
                "-" + formatDuration(((duration - fraction * duration) / 1000).toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Controls(state: PlayerState) {
    val graph = LocalGraph.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.Small),
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
                tint = OnAccent,
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
            modifier = Modifier.padding(Space.Page),
        )
        Spacer(Modifier.height(Space.Tight))
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(state.queue, key = { i, s -> "$i-${s.id}" }) { index, song ->
                // The song playing now stands out: a band behind it and "Now playing" under its title (#212).
                val playing = index == state.index
                Box(Modifier.fillMaxWidth().background(if (playing) Color.White.copy(alpha = NOW_PLAYING_TINT) else Color.Transparent).testTag(if (playing) "queueNowPlaying" else "queueRow")) {
                    SongRow(
                        song = song,
                        onClick = { graph.player.skipTo(index) },
                        highlighted = playing,
                        swipeToQueue = false,
                        subtitle = if (playing) listOfNotNull("Now playing", artistAndAlbum(song)).joinToString(" · ") else artistAndAlbum(song),
                    )
                }
            }
        }
    }
}


/** In the classic now playing style, how much of the lyrics card shows under the player before scrolling (issue #346). */
private val PLAYER_PEEK = 44.dp

/** The mini player's lyrics button asks now playing to open with the lyrics showing (issue #346). */
object LyricsShortcut {
    val pending = kotlinx.coroutines.flow.MutableStateFlow(false)
    fun open() { pending.value = true }
}

/** How strongly the song playing now is marked in the queue (#212). */
private const val NOW_PLAYING_TINT = 0.14f

/** How far up the queue button has to be pulled to open the queue. */
private const val QUEUE_PULL_PX = 40f

/** The lyrics and queue buttons' width under the controls; the device name gets the rest (issue #311). */
private val SIDE_BUTTON_WIDTH = 72.dp
