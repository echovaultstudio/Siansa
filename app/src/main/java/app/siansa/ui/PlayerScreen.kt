package app.siansa.ui

import app.siansa.data.OutsideMedia
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AddCircleOutline

import androidx.compose.ui.platform.testTag
import android.os.SystemClock
import app.siansa.log.AppLog
import app.siansa.playback.SeekTracker
import kotlinx.coroutines.delay
import app.siansa.data.cover
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clipToBounds
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
import app.siansa.data.Song
import app.siansa.playback.AudioOutput
import app.siansa.playback.Cast
import app.siansa.playback.PlayerState
import kotlin.math.max
import kotlin.math.roundToInt

/** Now playing's colours: white on the cover's colours, with the theme's accent for buttons. */
@Composable
internal fun playerColors() = darkColorScheme(
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
    // The cover's colour, for the page, the cards under the player and the lyrics screen.
    val tint = rememberCoverColor(song?.cover)
    // With Style's Dynamic colours, the seek bar, Play, the liked heart and where it plays take the cover's colour (issues #970, #998).
    val dynamic = rememberDynamicColours(song?.cover, style.dynamicColours)
    // The song's lyrics, for the lyrics button and the preview card. Cleared as the song changes, so the
    // button doesn't stay lit for a song without lyrics.
    val lyrics by loadStateFresh("lyrics-${song?.id}") { song?.let { graph.source.lyrics(it) } }
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

    // A slide from the play button (issue #919): a mix from this song, its song path or songs that sound like it.
    fun slide(action: PlaySlide) {
        val from = song ?: return
        AppLog.i("Player", "Slid from the play button: ${action.label} from ${from.id}")
        when (action) {
            PlaySlide.DJ_MIX -> RadioChooser.startNow(graph, from, app.siansa.mix.ContinuousMode.DJ) { message -> scope.launch { snack.showSnackbar(message) } }
            PlaySlide.STEADY -> RadioChooser.startNow(graph, from, app.siansa.mix.ContinuousMode.STEADY) { message -> scope.launch { snack.showSnackbar(message) } }
            PlaySlide.SONG_PATH -> openSongPath()
            PlaySlide.SONIC_MATCH -> openSimilarSongs()
            PlaySlide.START_A_MIX -> RadioChooser.ask(from)
            PlaySlide.NOTHING -> Unit
        }
    }

    // The DJ mix bar (issues #1033, #1035): with no DJ mix playing ([starting]), letting go on a place starts one from this
    // song, in that place's shape; while one plays, it stops it, or gives it that shape as the queue's Shape the mix does
    // (the next songs change).
    fun djBar(place: DjBarSpot, starting: Boolean) {
        val shape = place.shape
        if (starting) {
            val from = song ?: return
            AppLog.i("Player", "DJ mix bar: start a DJ mix${shape?.let { " as ${it.label}" }.orEmpty()} from ${from.id}")
            RadioChooser.startNow(graph, from, app.siansa.mix.ContinuousMode.DJ, shape) { message -> scope.launch { snack.showSnackbar(message) } }
        } else if (shape == null) {
            AppLog.i("Player", "DJ mix bar: stop the mix")
            graph.songRadio.stop()
            app.siansa.mix.AlbumDjMix.stop()
            scope.launch { snack.showSnackbar("Mix off") }
        } else {
            AppLog.i("Player", "DJ mix bar: ${shape.label}")
            graph.settings.setMixShape(shape)
            graph.songRadio.reshape()
        }
    }

    // Dragging down (at the top of the page) moves the whole player with the finger; let go far or fast
    // enough and it closes. Swiping up scrolls the whole page down to the cards under the player; swiping
    // back up brings the controls back (no small header over the page, #717).
    // Dragging down moves the whole sheet with the finger (#600): the sheet holds where it is and settles it.
    val dismissSheet = LocalPlayerSheet.current
    fun dismissedPx(): Float = (1f - dismissSheet.progress.value) * dismissSheet.heightPx
    val dismissOnPull = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val pulled = dismissedPx()
                if (pulled > 0f && available.y < 0f) {
                    val used = max(available.y, -pulled)
                    dismissSheet.dragBy(used)
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    dismissSheet.dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dismissedPx() > 0f) {
                    dismissSheet.settle(available.y)
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
                // One solid card: everything is clipped to it (the enlarged, blurred cover used to show through).
                .clipToBounds()
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
                        // The cover's colour, deep, down to the cards: one page from the top to the end.
                        1f to pageBottomColor(tint).copy(alpha = 0.9f),
                    )
                )
            )

            val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            // A slide from the play button shows its choice in the middle of the cover (issue #933).
            var slideLabel by remember { mutableStateOf<SlideLabel?>(null) }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // The player fills the screen; the cards below only come into view when scrolling, so none
                // sits under the phone's own buttons (#294). The classic style leaves the top of the lyrics card
                // peeking in at the bottom, as in Spotify, to show there's more below (issue #346).
                val pageHeight = if (classic) maxHeight - PLAYER_PEEK - navBar else maxHeight
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
                        // The top row sits exactly halfway between the status bar and the cover (the owner, 9 Nov).
                        PlayerTop(
                            topGap = playerTopGapDp(LocalConfiguration.current.screenHeightDp).dp,
                            row = { TopRow(song, onClose = onClose, onAlbum = ::openAlbum, onMenu = { menuOpen = true }, onSleep = { sleepOpen = true }) },
                            cover = { SwipeCover(song, onTap = ::openAlbum, slideLabel = slideLabel) },
                            // Siansa Dev: marks the last DJ blend as bad (the owner, 9 Nov).
                            belowCover = { BadBlendButton() },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )

                        // The filled heart of a liked song takes the cover's colour too, with Dynamic colours on (issue #998).
                        WithAccent(dynamic?.controls, dynamic?.onControls) {
                            TitleRow(song, liked = song != null && heartChanges.let { graph.favorites.isLiked(song) }, onArtist = { song?.let(openArtist) }) {
                                song?.let { graph.favorites.toggle(it) }
                            }
                        }
                        // A radio station has no seek bar, only that it's live (issue #353).
                        // While a finger slides from the play button, everything around it dims (issue #963), and going down,
                        // Play on (right under the button) makes way completely.
                        val slideDim by animateFloatAsState(if (slideLabel != null) SLIDING_OTHERS else 1f, label = "pageDim")
                        val castAlpha = if (slideLabel?.way == SlideWay.DOWN) 0f else slideDim
                        var trackY by remember { mutableStateOf<Float?>(null) }
                        var trackWidth by remember { mutableStateOf<Int?>(null) }
                        WithAccent(dynamic?.controls, dynamic?.onControls) {
                            if (OutsideMedia.isLive(song?.id)) LiveBar() else {
                                // As a finger heads up from Play, the seek bar's line opens into the DJ mix bar (issues
                                // #1033, #1035), centred on that line, and closes back into it when the finger lets go,
                                // on a quick spring. It stays within the seek bar's room: nothing moves.
                                val bar = slideLabel?.bar
                                var lastBar by remember { mutableStateOf<DjBarView?>(null) }
                                if (bar != null) lastBar = bar
                                val open by animateFloatAsState(
                                    if (bar != null) 1f else 0f,
                                    spring(dampingRatio = DJ_BAR_SPRING_DAMPING, stiffness = DJ_BAR_SPRING_STIFFNESS),
                                    label = "djBarOpen",
                                )
                                val shownBar = bar ?: lastBar?.takeIf { open > DJ_BAR_CLOSED }
                                Box {
                                    SeekBar(state, Modifier.alpha(slideDim * (1f - open.coerceIn(0f, 1f))), onTrackWidth = { trackWidth = it }) { trackY = it }
                                    if (shownBar != null) {
                                        val now = style.mixShape.takeIf { !shownBar.starting && shownBar.spots.size > 1 }?.let { DjBarSpot.of(it) }
                                        Box(Modifier.matchParentSize()) {
                                            DjBar(
                                                shownBar.spots, shownBar.chosen, now = now, starting = shownBar.starting, open = open,
                                                modifier = Modifier.padding(top = DJ_BAR_CENTRE - DJ_BAR_HEIGHT / 2),
                                            )
                                        }
                                    }
                                }
                            }
                            Controls(
                                state, onSlide = ::slide, onSlideLabel = { slideLabel = it }, upTo = trackY,
                                djBarWidthPx = trackWidth.takeIf { !OutsideMedia.isLive(song?.id) }, onDjBar = ::djBar,
                            )
                        }
                        // The lyrics bottom left (greyed out when the song has none), where the music plays in
                        // the middle (with the TV, speaker or headphones' name), and the queue bottom right: tap
                        // it, or swipe up from it (#294).
                        if (classic) {
                            // The classic style (before #294): where the music plays bottom left, the queue bottom right.
                            Row(Modifier.fillMaxWidth().padding(top = Space.Medium, bottom = Space.Small).testTag("classicBottomRow"), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f).alpha(castAlpha), contentAlignment = Alignment.CenterStart) {
                                    WithAccent(dynamic?.text, dynamic?.onControls) { CastDeviceButton(tint = Color.White.copy(alpha = 0.8f)) }
                                }
                                Box(Modifier.alpha(slideDim)) { queueButton() }
                            }
                        } else Row(Modifier.fillMaxWidth().padding(top = Space.Medium, bottom = Space.Small), verticalAlignment = Alignment.CenterVertically) {
                            // The device name gets all the room between the two buttons (issue #311).
                            Box(Modifier.width(SIDE_BUTTON_WIDTH).alpha(slideDim), contentAlignment = Alignment.Center) {
                                LyricsButton(enabled = hasLyrics(songLyrics)) { lyricsOpen = true }
                            }
                            Box(Modifier.weight(1f).alpha(castAlpha), contentAlignment = Alignment.Center) {
                                WithAccent(dynamic?.text, dynamic?.onControls) { CastDeviceButton(tint = Color.White.copy(alpha = 0.8f)) }
                            }
                            Box(Modifier.width(SIDE_BUTTON_WIDTH).alpha(slideDim), contentAlignment = Alignment.Center) {
                                queueButton()
                            }
                        }
                    }
                    if (song != null) {
                        // With the lyrics button under the controls, the lyrics needn't be on a card below too (issue #434).
                        if (showsLyricsCard(lyricsButton = !classic)) {
                            LyricsPreviewCard(songLyrics, tint) { lyricsOpen = true }
                            Spacer(Modifier.height(Space.Medium))
                        }
                        // The cards take a deep shade of the cover's colour (#717), so they belong to the page.
                        val cards = nowPlayingCardColor(tint)
                        AboutArtistCard(song, cards) { openArtist(song) }
                        Spacer(Modifier.height(Space.Medium))
                        AboutSongCard(song, cards, onOpenDetails = ::openDetails)
                    }
                    Spacer(Modifier.height(Space.Section + navBar))
                }
            }

            // Lyrics, opened from the card or the menu, over the whole player.
            AnimatedVisibility(
                visible = lyricsOpen,
                enter = slideInVertically(tween(230, easing = FastOutSlowInEasing)) { it },
                exit = slideOutVertically(tween(180, easing = FastOutLinearInEasing)) { it },
            ) {
                LyricsScreen(song, tint, onClose = { lyricsOpen = false }, menu = { menuOpen = true }) {
                    WithAccent(dynamic?.controls, dynamic?.onControls) {
                        Column {
                            if (OutsideMedia.isLive(song?.id)) LiveBar() else SeekBar(state)
                            Controls(state, onSlide = ::slide)
                            // The same row as under the player's controls, the lyrics button lit bottom left: one
                            // tap there closes the lyrics again (the owner, 9 Nov).
                            Row(Modifier.fillMaxWidth().padding(top = Space.Medium).testTag("lyricsBottomRow"), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(SIDE_BUTTON_WIDTH), contentAlignment = Alignment.Center) {
                                    LyricsButton(enabled = true, open = true) { lyricsOpen = false }
                                }
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    WithAccent(dynamic?.text, dynamic?.onControls) { CastDeviceButton(tint = Color.White.copy(alpha = 0.8f)) }
                                }
                                Box(Modifier.width(SIDE_BUTTON_WIDTH), contentAlignment = Alignment.Center) {
                                    RoundIcon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue") { queueOpen = true }
                                }
                            }
                        }
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
                // Back closes the queue first, also when it was opened over the lyrics.
                BackHandler(enabled = queueOpen) { queueOpen = false }
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
                        .background(QUEUE_SHEET)
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
                    QueueView(state, ::djBar)
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
private fun SwipeCover(song: Song?, onTap: () -> Unit, slideLabel: SlideLabel? = null) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val currentId by rememberUpdatedState(song?.id)
    val blur by androidx.compose.animation.core.animateDpAsState(slideBlur(slideLabel), label = "coverBlur")
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
                .then(if (blur > 0.dp) Modifier.blur(blur, androidx.compose.ui.draw.BlurredEdgeTreatment(RoundedCornerShape(10.dp))) else Modifier)
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
        SlideCoverLabel(slideLabel, Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)))
    }
}

/**
 * Now playing's top: the [row] ("Playing from album", the arrow and ⋮) and the [cover]. The cover takes the room that's
 * left under a [topGap], the row and a [Space.Page] gap, down to a [Space.Medium] gap, and is centred in it; the row
 * then sits exactly halfway between the top and the cover's top edge. [belowCover] sits at the bottom of the cover's
 * room, in the middle.
 */
@Composable
internal fun PlayerTop(
    topGap: androidx.compose.ui.unit.Dp,
    row: @Composable () -> Unit,
    cover: @Composable () -> Unit,
    belowCover: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.ui.layout.Layout(
        contents = listOf(row, cover, belowCover),
        modifier = modifier.testTag("playerTop"),
    ) { (rows, covers, belows), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val rowPlaced = rows.map { it.measure(loose) }
        val rowHeight = rowPlaced.maxOfOrNull { it.height } ?: 0
        val gap = topGap.roundToPx()
        val above = Space.Page.roundToPx()
        val under = Space.Medium.roundToPx()
        val room = (height - gap - rowHeight - above - under).coerceAtLeast(0)
        val coverPlaced = covers.map { it.measure(loose.copy(maxHeight = room)) }
        val coverHeight = coverPlaced.maxOfOrNull { it.height } ?: 0
        val coverTop = playerCoverTop(gap, rowHeight, above, room, coverHeight)
        val belowPlaced = belows.map { it.measure(loose) }
        layout(width, height) {
            val rowY = playerTopRowY(coverTop, rowHeight)
            rowPlaced.forEach { it.place(0, rowY) }
            coverPlaced.forEach { it.place((width - it.width) / 2, coverTop) }
            belowPlaced.forEach { it.place((width - it.width) / 2, height - under - it.height) }
        }
    }
}

@Composable
private fun TopRow(song: Song?, onClose: () -> Unit, onAlbum: () -> Unit, onMenu: () -> Unit, onSleep: () -> Unit) {
    val timerRunning = LocalGraph.current.sleepTimer.state.collectAsStateWithLifecycle().value != null
    Row(Modifier.fillMaxWidth().testTag("playerTopRow"), verticalAlignment = Alignment.CenterVertically) {
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
        // A radio station or podcast episode isn't on the music server, so it can't be liked (issue #353).
        if (song != null && OutsideMedia.isOutside(song.id)) return@Row
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
private fun SeekBar(state: PlayerState, modifier: Modifier = Modifier, onTrackWidth: (Int) -> Unit = {}, onTrackCentre: (Float) -> Unit = {}) {
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
    Column(modifier.padding(top = Space.Medium)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(SEEK_TRACK_HEIGHT)
                // Where its line is, for the slide's up choice to sit on (issue #963), and how wide, for the DJ mix
                // bar's places (issue #1033).
                .onGloballyPositioned {
                    onTrackCentre(it.positionInRoot().y + it.size.height / 2f)
                    onTrackWidth(it.size.width)
                }
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

/**
 * Repeat, previous, play, next and add to playlist. [onSlide]: what a slide from the play button runs (issue #919),
 * null for none; [onSlideLabel] gets what the slide shows, for the cover (issue #933).
 */
@Composable
internal fun Controls(
    state: PlayerState,
    onSlide: ((PlaySlide) -> Unit)? = null,
    onSlideLabel: (SlideLabel?) -> Unit = {},
    upTo: Float? = null,
    // The seek bar's width, where the DJ mix bar comes in (issue #1033); null: no bar here (the lyrics screen).
    djBarWidthPx: Int? = null,
    onDjBar: (DjBarSpot, starting: Boolean) -> Unit = { _, _ -> },
) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val radioSeed by graph.songRadio.seed.collectAsStateWithLifecycle()
    // An album's DJ mix (issue #965) gives the play button its grooves too.
    val albumDj by app.siansa.mix.AlbumDjMix.playing.collectAsStateWithLifecycle()
    // While a finger slides from the play button, the other controls make way: dimmed, and the one on the slide's
    // side out of the way.
    var sliding by remember { mutableStateOf<SlideWay?>(null) }
    val others by animateFloatAsState(if (sliding != null) SLIDING_OTHERS else 1f, label = "controlsDim")
    fun alphaOf(side: SlideWay?) = if (sliding != null && sliding == side) 0f else others
    // A short buzz for repeat and add to playlist (issue #771), as for the mini player's swipe (#763); the phone's
    // touch-feedback setting decides whether it's felt.
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().zIndex(if (sliding != null) 1f else 0f).padding(vertical = Space.Small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Repeat on the left, add to playlist on the right (issue #370); shuffle is in the ⋮ menu.
        Box(Modifier.alpha(others)) { StateButton(
            icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            label = repeatLabel(state.repeatMode),
            on = state.repeatMode != Player.REPEAT_MODE_OFF,
            tag = "repeatButton",
        ) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            graph.player.cycleRepeat()
            AppLog.i("Player", "Repeat: " + repeatLabel(nextRepeat(state.repeatMode)).lowercase())
        } }
        IconButton(onClick = { graph.player.previous() }, modifier = Modifier.size(56.dp).alpha(alphaOf(SlideWay.LEFT))) {
            Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(40.dp))
        }
        // Tap to play or pause; press and hold to stop and clear the queue; slide for a mix (issue #919). A radio
        // station or podcast has nothing to mix from.
        val song = state.current
        val slides = settings.playSlides.let { if (onSlide == null || song == null || OutsideMedia.isOutside(song.id)) it.copy(on = false) else it }
        // The up choice sits on the seek bar's line when there is one (issue #963).
        var playCentre by remember { mutableStateOf<Float?>(null) }
        // Up is the DJ mix bar (issues #1033, #1035): while a DJ mix plays, a radio's shapes and Stop, a list's own mix only
        // Stop; with none playing, the shapes and Start, when up starts a DJ mix and one can start (else up is as set).
        val djRadio = radioSeed != null && graph.songRadio.mode == app.siansa.mix.ContinuousMode.DJ
        val listMix = song != null && song.id in albumDj
        val canStart = slides.on && slides.up == PlaySlide.DJ_MIX &&
            slideUnavailable(PlaySlide.DJ_MIX, settings, graph.connection.current.aiProblem(settings)) == null
        val barPlaces = djBarPlaces(settings.djBarOrder, djRadio, listMix, canStart).takeIf { djBarWidthPx != null }
        PlayButton(
            modifier = Modifier.onGloballyPositioned { playCentre = it.positionInRoot().y + it.size.height / 2f },
            upReachPx = upReach(playCentre, upTo),
            playing = state.isPlaying,
            slides = slides,
            djMix = djRadio || listMix,
            djBar = barPlaces?.spots,
            djBarWidthPx = djBarWidthPx,
            onDjBar = { place -> barPlaces?.let { onDjBar(place, it.starting) } },
            onToggle = { graph.player.togglePlay() },
            onStop = { graph.player.stop() },
            onSlide = { onSlide?.invoke(it) },
            unavailable = { slideUnavailable(it, settings, graph.connection.current.aiProblem(settings)) },
            onSliding = { shown ->
                sliding = shown?.way
                onSlideLabel(shown?.let {
                    val places = barPlaces
                    if (it.bar && places != null) {
                        SlideLabel(PlaySlide.DJ_MIX, it.way, it.armed, bar = DjBarView(places.spots, it.spot, places.starting, it.rise))
                    } else {
                        val slide = slides.of(it.way)
                        SlideLabel(slide, it.way, it.armed, slideUnavailable(slide, settings, graph.connection.current.aiProblem(settings)))
                    }
                })
            },
        )
        IconButton(onClick = { graph.player.next() }, modifier = Modifier.size(56.dp).alpha(alphaOf(SlideWay.RIGHT))) {
            Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(40.dp))
        }
        var picking by remember { mutableStateOf(false) }
        Box(Modifier.alpha(others)) { StateButton(icon = Icons.Rounded.AddCircleOutline, label = "Add to playlist", on = false, tag = "addToPlaylistButton") {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (state.current != null) picking = true
        } }
        if (picking) {
            val launch = rememberPlayLauncher()
            state.current?.let { song ->
                AddToPlaylistDialog(listOf(song)) { message ->
                    picking = false
                    message?.let { launch.notify(it) }
                }
            } ?: run { picking = false }
        }
    }
}

/**
 * A button beside the play controls (issue #370): white, or the accent with a dot under it while [on], so the
 * change shows on any theme, also where the accent is pale. One tap does it: the whole 48 dp square answers.
 */
@Composable
internal fun StateButton(icon: ImageVector, label: String, on: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (on) Accent else Color.White)
        if (on) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Space.Tight)
                    .size(4.dp) // spacing: exact (a dot)
                    .clip(CircleShape)
                    .background(Accent)
                    .testTag("$tag-on"),
            )
        }
    }
}

/** What the repeat button is set to, for screen readers and the log (issue #370). */
internal fun repeatLabel(mode: Int): String = when (mode) {
    Player.REPEAT_MODE_ALL -> "Repeat all"
    Player.REPEAT_MODE_ONE -> "Repeat this song"
    else -> "Repeat off"
}

/** The repeat mode one tap moves to: off → all → this song → off. */
internal fun nextRepeat(mode: Int): Int = when (mode) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}

/** The queue sheet's near-black, a little see-through. */
private val QUEUE_SHEET = Color(0xF2121212)

/** What a queued song swiped away covers the red strip with: the sheet's colour, solid, as its surface is see-through. */
internal val QUEUE_ROW = Color(0xFF121212)

@Composable
private fun QueueView(state: PlayerState, onDjBar: (DjBarSpot, Boolean) -> Unit) {
    val graph = LocalGraph.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = max(0, state.index - 1))
    if (state.queue.isEmpty()) {
        Text("The queue is empty", color = Color.White.copy(alpha = 0.7f))
        return
    }
    var saving by remember { mutableStateOf(false) }
    if (saving) SaveAsPlaylistDialog("Queue · ${java.time.LocalDate.now()}", state.queue) { saving = false }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = Space.Page), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Up next · ${state.queue.size - state.index - 1} of ${state.queue.size}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(vertical = Space.Page),
            )
            // The whole queue as a playlist (beta #19); a filled circle, unlike a song's "Add to playlist".
            IconButton(onClick = { saving = true }, modifier = Modifier.testTag("saveQueueAsPlaylist")) {
                Icon(Icons.Rounded.AddCircle, "Save queue as playlist", tint = Color.White)
            }
        }
        // Shape your mix (issue #649; the owner, 10 Nov): the glide bar of now playing, folded under its heading, with
        // the Surprises bar under it while a DJ radio plays. Its places as the glide bar's: a radio's shapes and Stop,
        // a list's own mix only Stop, or the shapes and Start when a DJ mix can start from this song.
        val radioSeed by graph.songRadio.seed.collectAsStateWithLifecycle()
        val albumDj by app.siansa.mix.AlbumDjMix.playing.collectAsStateWithLifecycle()
        val settings by graph.settings.state.collectAsStateWithLifecycle()
        val song = state.current
        val djRadio = radioSeed != null && graph.songRadio.mode == app.siansa.mix.ContinuousMode.DJ
        val listMix = song != null && song.id in albumDj
        val canStart = song != null && !OutsideMedia.isOutside(song.id) &&
            slideUnavailable(PlaySlide.DJ_MIX, settings, graph.connection.current.aiProblem(settings)) == null
        djBarPlaces(settings.djBarOrder, djRadio, listMix, canStart)?.let { places ->
            var open by rememberSaveable { mutableStateOf(false) }
            MixShapePanel(
                places = places,
                shape = settings.mixShape.takeIf { djRadio },
                surprise = settings.mixSurprise.takeIf { djRadio },
                open = open,
                onOpen = { open = it },
                onPlace = { onDjBar(it, places.starting) },
                onSurprise = { level ->
                    graph.settings.setMixShape(settings.mixShape, level.value)
                    graph.songRadio.reshape()
                },
            )
        }
        Spacer(Modifier.height(Space.Tight))
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(state.queue, key = { i, s -> "$i-${s.id}" }) { index, song ->
                // The song playing now stands out with a band behind it (#212); its cover pauses and plays (#345).
                val playing = index == state.index
                Box(Modifier.fillMaxWidth().background(if (playing) Color.White.copy(alpha = NOW_PLAYING_TINT) else Color.Transparent).testTag(if (playing) "queueNowPlaying" else "queueRow")) {
                    SongRow(
                        song = song,
                        onClick = { graph.player.skipTo(index) },
                        highlighted = playing,
                        swipeToQueue = false,
                        // Right to left, the way a song is swiped into the queue: a red strip takes it out; left to
                        // right: an amber one moves it to play next, unless it's playing or next already (issue #915).
                        swipeToRemove = { graph.player.removeFromQueue(index) },
                        swipeToPlayNext = if (index == state.index || index == state.index + 1) null else ({ graph.player.moveToPlayNext(index) }),
                        swipeRowColour = QUEUE_ROW,
                        subtitle = artistAndAlbum(song),
                        nowPlaying = playing,
                        autoAdded = index in state.auto,
                        extraMenu = { close ->
                            DropdownMenuItem(text = { Text("Remove from queue") }, onClick = {
                                close()
                                graph.player.removeFromQueue(index)
                            })
                            if (index != state.index && index != state.index + 1) {
                                DropdownMenuItem(text = { Text("Play next") }, onClick = {
                                    close()
                                    graph.player.moveToPlayNext(index)
                                })
                            }
                            if (index > 0) {
                                DropdownMenuItem(text = { Text("Remove songs up to here") }, onClick = {
                                    close()
                                    graph.player.removeQueueUpTo(index)
                                })
                            }
                        },
                    )
                }
            }
        }
    }
}


/**
 * Shape your mix (issue #649; the owner, 10 Nov), in the queue: a heading that folds open on the glide bar of now
 * playing, tapped instead of slid to ([places]: start, stop or shape the mix), and, while a DJ radio plays ([surprise]
 * not null), the Surprises bar in the same design: how far the mix may stray. Folded, the heading says what's set.
 * [shape]: the mix's shape now (a DJ radio's), ringed on the bar.
 */
@Composable
internal fun MixShapePanel(
    places: DjBarPlaces,
    shape: app.siansa.mix.MixShape?,
    surprise: Int?,
    open: Boolean,
    onOpen: (Boolean) -> Unit,
    onPlace: (DjBarSpot) -> Unit,
    onSurprise: (SurpriseLevel) -> Unit,
) {
    val level = surprise?.let { SurpriseLevel.of(it) }
    Column(Modifier.fillMaxWidth().testTag("mixShape")) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (open) "Fold" else "Unfold") { onOpen(!open) }
                .padding(horizontal = Space.Page, vertical = Space.Medium)
                .testTag("mixShapeHeading"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Shape your mix", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    mixShapeSummary(places, shape, level),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("mixShapeSummary"),
                )
            }
            val turn by animateFloatAsState(if (open) 180f else 0f, label = "mixShapeChevron")
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.rotate(turn))
        }
        AnimatedVisibility(open) {
            Column(Modifier.fillMaxWidth().padding(start = Space.Page, end = Space.Page, bottom = Space.Medium)) {
                val now = shape?.let { DjBarSpot.of(it) }
                DjBar(places.spots, chosen = now?.let { places.spots.indexOf(it) }?.takeIf { it >= 0 }, now = now,
                    starting = places.starting, open = 1f, onTap = onPlace)
                Text(
                    (now ?: places.spots.singleOrNull() ?: DjBarSpot.MIX).hint(places.starting),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = Space.Small).testTag("mixShapeHint"),
                )
                if (level != null) {
                    Text("Surprises", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = Space.Page, bottom = Space.Small).testTag("mixSurpriseLabel"))
                    GlidePill(1f, Modifier.testTag("mixSurprise")) {
                        SurpriseLevel.entries.forEach { l ->
                            GlidePlace(l.icon, "Surprises: ${l.label}", chosen = l == level, now = false, tag = "surprise",
                                onTap = { if (l != level) onSurprise(l) }, modifier = Modifier.weight(1f))
                        }
                    }
                    Text("${level.label}: ${level.hint}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = Space.Small).testTag("mixSurpriseHint"))
                }
            }
        }
    }
}

/** What Shape your mix says folded: the shape and surprises a DJ radio has, or what its bar does. */
internal fun mixShapeSummary(places: DjBarPlaces, shape: app.siansa.mix.MixShape?, level: SurpriseLevel?): String = when {
    shape != null -> shape.label + (level?.let { " · " + if (it == SurpriseLevel.NONE) "no surprises" else "${it.label.lowercase()} surprises" }.orEmpty())
    places.starting -> "Start a DJ mix from this song"
    else -> "A DJ mix of this list"
}

/** What a surprise setting means, in a word. */
internal fun surpriseLabel(surprise: Int): String = when {
    surprise < 15 -> "None"
    surprise < 40 -> "A few"
    surprise < 70 -> "Some"
    else -> "Lots"
}


/** In the classic now playing style, how much of the lyrics card shows under the player before scrolling (issue #346). */
private val PLAYER_PEEK = 44.dp

/** Whether now playing shows the lyrics card under the player: only without the lyrics button (issue #434). */
internal fun showsLyricsCard(lyricsButton: Boolean): Boolean = !lyricsButton

/** The mini player's lyrics button asks now playing to open with the lyrics showing (issue #346). */
object LyricsShortcut {
    val pending = kotlinx.coroutines.flow.MutableStateFlow(false)
    fun open() { pending.value = true }
}

/** How strongly the song playing now is marked in the queue (#212). */
private const val NOW_PLAYING_TINT = 0.14f

/**
 * How the DJ mix bar opens and closes (issue #1035): a quick spring with a touch of give, fluid but snappy; below
 * [DJ_BAR_CLOSED] open it's gone.
 */
private const val DJ_BAR_SPRING_DAMPING = 0.78f
private const val DJ_BAR_SPRING_STIFFNESS = 800f
private const val DJ_BAR_CLOSED = 0.01f

/** How faint the other controls go while a finger slides from the play button (issue #919). */
private const val SLIDING_OTHERS = 0.3f

/**
 * How far above the play button's centre its up choice goes (issue #963): to the seek bar's line ([trackY]) when that's
 * where it would land anyway, so the choice sits centred on it; null (its usual reach) when either isn't known or the
 * bar is too close or too far.
 */
internal fun upReach(playCentreY: Float?, trackY: Float?): Int? {
    if (playCentreY == null || trackY == null) return null
    val reach = playCentreY - trackY
    return reach.takeIf { it in UP_REACH_PX_MIN..UP_REACH_PX_MAX }?.toInt()
}

/** The seek bar is used as the up choice's place only between these distances from the button's centre (px). */
private const val UP_REACH_PX_MIN = 80f
private const val UP_REACH_PX_MAX = 600f

/** How far up the queue button has to be pulled to open the queue. */
private const val QUEUE_PULL_PX = 40f

/** The lyrics and queue buttons' width under the controls; the device name gets the rest (issue #311). */
private val SIDE_BUTTON_WIDTH = 72.dp
