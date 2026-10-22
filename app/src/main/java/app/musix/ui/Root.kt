package app.musix.ui

import app.musix.data.MiniButton
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder

import app.musix.data.NavTab

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import app.musix.data.cover
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.musix.log.AppLog
import kotlinx.coroutines.CancellationException
import app.musix.playback.PlayerState

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/** A bottom-bar tab's look; which tabs show, and in what order, is set in Settings › Style › Nav Bar (issue #346). */
internal fun NavTab.icon(): ImageVector = when (this) {
    NavTab.HOME -> Icons.Rounded.Home
    NavTab.SEARCH -> Icons.Rounded.Search
    NavTab.LIBRARY -> Icons.Rounded.LibraryMusic
    // Describe your aura or tune a vibe (issue #339).
    NavTab.DSCVR -> DscvrStars
    NavTab.SETTINGS -> Icons.Rounded.Settings
}

private fun tabsOf(shown: List<NavTab>): List<Tab> = shown.map { Tab(it.route, it.label, it.icon()) }

/** Every route that's a tab, shown or not, so the bar knows which tab a page belongs to. */
private val allTabRoutes = NavTab.entries.map { it.route }.toSet()

/** Height of the see-through bottom bar (mini player + tabs), so scrolling pages can end above it. */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

/** Bottom padding for a scrolling page: clear of the bottom bar, plus [extra]. */
@Composable
fun bottomBarPadding(extra: Dp = Space.Section): Dp = LocalBottomBarPadding.current + extra

/**
 * Ends a page at the top of the keyboard, so a focused text box scrolls up above it.
 *
 * Not imePadding(): the page has already taken off the bottom bar's height (consumeWindowInsets in
 * [MusixRoot]), so imePadding() leaves out that much, yet the bar sits behind the keyboard too. That left a
 * box near the bottom hidden behind the keyboard (#30 again). WindowInsets.ime itself is the keyboard's
 * full height.
 */
@Composable
fun Modifier.aboveKeyboard(): Modifier = padding(bottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding())

/** True while the on-screen keyboard is showing. */
@Composable
fun keyboardOpen(): Boolean = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

/**
 * Now playing is a sheet over the current page rather than a page of its own, so it slides up and down
 * (and follows the finger when dragged down) with the page underneath still there.
 */
class PlayerSheet {
    var isOpen by mutableStateOf(false)
        private set
    fun open() { isOpen = true }
    fun close() { isOpen = false }
}

val LocalPlayerSheet = staticCompositionLocalOf { PlayerSheet() }

private val SheetEnter = tween<IntOffset>(250, easing = FastOutSlowInEasing)
private val SheetExit = tween<IntOffset>(190, easing = FastOutLinearInEasing)

@Composable
fun MusixRoot() {
    val graph = LocalGraph.current
    val nav = rememberNavController()
    val snack = remember { SnackbarHostState() }
    // Messages from the player, e.g. a song that can't play while offline.
    LaunchedEffect(Unit) { graph.player.notices.collect { snack.showSnackbar(it) } }
    val sheet = remember { PlayerSheet() }
    val player by graph.player.state.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    // The tab the user is "in", which stays lit while they browse deeper (album, artist…).
    var currentTab by rememberSaveable { mutableStateOf("home") }
    val appSettings by graph.settings.state.collectAsStateWithLifecycle()
    val tabs = tabsOf(appSettings.navTabs)

    LaunchedEffect(route) {
        if (route != null) {
            AppLog.i("Nav", route)
            AppLog.screen = route
        }
        if (route != null && route in allTabRoutes) currentTab = route
    }
    LaunchedEffect(Unit) {
        if (!graph.settings.current.navidromeReady) nav.navigate("settings/connections")
    }
    LaunchedEffect(sheet.isOpen) {
        val screen = if (sheet.isOpen) "player" else route
        if (screen != null) {
            if (sheet.isOpen) AppLog.i("Nav", "player")
            AppLog.screen = screen
        }
    }
    // Nothing left to show (playback stopped): close now playing.
    LaunchedEffect(player.hasMedia) { if (!player.hasMedia) sheet.close() }
    // Get the playing song's big cover ready as soon as it starts, so opening now playing shows it at once.
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(player.current?.id) {
        val song = player.current ?: return@LaunchedEffect
        try {
            AnimatedCovers.preload(appContext, graph, song)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Cover", "Preloading the playing song's cover failed: ${e.message}")
        }
    }

    AskForNotifications()

    CompositionLocalProvider(LocalNav provides nav, LocalSnack provides snack, LocalPlayerSheet provides sheet) {
      Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                run {
                    // See-through: the page shows through the top of the bar and fades out behind the tabs.
                    Column(
                        Modifier.background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.3f to DarkColours.surface.copy(alpha = 0.78f),
                                1f to DarkColours.surface.copy(alpha = 0.96f),
                            )
                        )
                    ) {
                        if (player.hasMedia) MiniPlayer(player) { sheet.open() }
                        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentTab == tab.route,
                                    onClick = {
                                        if (tabTapClearsSearch(currentTab, tab.route)) SearchReset.request()
                                        if (tabTapScrollsHomeToTop(route, tab.route)) HomeScrollToTop.request()
                                        if (currentTab == tab.route) nav.backToTabTop(tab.route) else nav.switchTab(tab.route)
                                        currentTab = tab.route
                                    },
                                    icon = { Icon(tab.icon, null) },
                                    label = { Text(tab.label, maxLines = 1, softWrap = false, fontSize = 11.sp, letterSpacing = (-0.2).sp) },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
          CompositionLocalProvider(LocalBottomBarPadding provides padding.calculateBottomPadding()) {
            NavHost(
                navController = nav,
                startDestination = "home",
                // The page runs on under the see-through bottom bar; scrolling pages pad their end with
                // LocalBottomBarPadding so nothing is stuck behind it.
                modifier = Modifier.consumeWindowInsets(padding),
                // Quick cross-fades between pages instead of the default slow ones.
                enterTransition = { fadeIn(tween(140)) },
                exitTransition = { fadeOut(tween(90)) },
                popEnterTransition = { fadeIn(tween(140)) },
                popExitTransition = { fadeOut(tween(90)) },
            ) {
                composable("home") { HomeScreen(nav) }
                composable("search") { SearchScreen(nav) }
                composable("library") { YourLibraryScreen(nav) }
                composable("vibes") { VibesScreen(nav) }
                composable("dscvr") { DscvrScreen(nav) }
                composable("vibe/{id}") { VibeScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("settings/cards") { CardsSettingsScreen(nav) }
                composable("card/{id}") { CardScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("settings") { SettingsHub(nav) }
                composable("settings/connections") { ConnectionsScreen(nav) }
                composable("settings/users") { UsersScreen(nav) }
                composable("settings/playback") { PlaybackSettingsScreen(nav) }
                composable("settings/themes") { ThemesScreen(nav) }
                composable("settings/style") { StyleScreen(nav) }
                composable("settings/style/now-playing") { NowPlayingStyleScreen(nav) }
                composable("settings/style/mini-player") { MiniPlayerStyleScreen(nav) }
                composable("settings/style/nav-bar") { NavBarStyleScreen(nav) }
                composable("settings/equaliser") { EqualiserScreen(nav) }
                composable("settings/crossfade") { CrossfadeScreen(nav) }
                composable("settings/storage") { StorageScreen(nav) }
                composable("downloads") { DownloadsScreen(nav) }
                composable("downloads/add") { DownloadPickerScreen(nav) }
                composable("downloads/songs") { DownloadedSongsScreen(nav) }
                composable("settings/logs") { LogsScreen(nav) }
                composable("settings/backup") { BackupScreen(nav) }
                composable("settings/scrobbling") { ScrobblingScreen(nav) }
                composable("settings/whatsnew") { WhatsNewScreen(nav) }
                composable("home/{section}") { HomeSectionScreen(nav, it.arguments?.getString("section").orEmpty()) }
                composable("liked") { LikedScreen(nav) }
                composable("radio/{kind}/{key}") {
                    RadioScreen(nav, it.arguments?.getString("kind").orEmpty(), it.arguments?.getString("key").orEmpty())
                }
                composable("blend/{id}") { BlendScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("mood/{text}") { MoodScreen(nav, it.arguments?.getString("text").orEmpty()) }
                composable(
                    "songpath/{id}?live={live}",
                    arguments = listOf(androidx.navigation.navArgument("live") { defaultValue = "false" }),
                ) { SongPathPicker(nav, it.arguments?.getString("id").orEmpty(), it.arguments?.getString("live") == "true") }
                composable(
                    "songpath/{from}/{to}/{length}?lyrics={lyrics}&live={live}",
                    arguments = listOf(
                        androidx.navigation.navArgument("lyrics") { defaultValue = "false" },
                        androidx.navigation.navArgument("live") { defaultValue = "false" },
                    ),
                ) {
                    val args = it.arguments
                    SongPathScreen(
                        nav,
                        args?.getString("from").orEmpty(),
                        args?.getString("to").orEmpty(),
                        args?.getString("length")?.toIntOrNull() ?: 20,
                        args?.getString("lyrics") == "true",
                        args?.getString("live") == "true",
                    )
                }
                composable("sync") { SyncScreen(nav) }
                composable("report/{range}") { ReportScreen(nav, it.arguments?.getString("range").orEmpty()) }
                composable("discover/artists") { ArtistDiscoveryScreen(nav) }
                composable("timemachine") { TimeMachineScreen(nav) }
                composable("favourite-albums") { FavouriteAlbumsScreen(nav) }
                composable("fingerprint") { FingerprintScreen(nav) }
                composable("song/{id}/details") { SongDetailsScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("album/{id}") { AlbumScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("similar/songs/{id}") { SimilarSongsScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("similar/albums/{id}") { SimilarAlbumsScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("artist/{id}") { ArtistScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("artist/{id}/appears-on") { AppearsOnScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("playlist/{id}") { PlaylistScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("mix/{id}") { MixScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("selector/{id}") { SelectorEditor(nav, it.arguments?.getString("id").orEmpty()) }
                composable("carousel/{id}") { CarouselEditor(nav, it.arguments?.getString("id").orEmpty()) }
            }
          }
        }

        // "Start song radio" from any menu asks Steady or DJ here (issue #337).
        RadioChooserHost()
        BackHandler(enabled = sheet.isOpen) { sheet.close() }
        AnimatedVisibility(
            visible = sheet.isOpen,
            enter = slideInVertically(SheetEnter) { it },
            exit = slideOutVertically(SheetExit) { it },
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                PlayerScreen(nav, onClose = { sheet.close() })
            }
        }
      }
    }
}

/** Opens another tab where it was left, keeping each tab's own history. */
fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Tapping the tab you're already in goes back to that tab's first screen. */
fun NavHostController.backToTabTop(route: String) {
    if (!popBackStack(route, inclusive = false)) switchTab(route)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniPlayer(state: PlayerState, onOpen: () -> Unit) {
    val graph = LocalGraph.current
    val haptics = LocalHapticFeedback.current
    val song = state.current ?: return
    // Tinted with the cover's colour, like now playing, and darkened so white text stays readable.
    val tone = rememberCoverColor(song.cover)
    val background = lerp(tone, Color.Black, 0.45f)
    Box(Modifier.padding(horizontal = Space.Small, vertical = Space.Tight)) {
      CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(Modifier.clip(RoundedCornerShape(10.dp)).background(background)) {
            // Swipe the song left for the next one, right for the one before (issue #310): it follows the finger,
            // and springs back when let go short of a quarter of the bar.
            val scope = rememberCoroutineScope()
            val drag = remember(song.id) { Animatable(0f) }
            var barWidth by remember { mutableIntStateOf(1) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag("miniPlayer")
                    .onSizeChanged { barWidth = it.width.coerceAtLeast(1) }
                    .pointerInput(song.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val moved = drag.value
                                scope.launch {
                                    when (miniSwipe(moved, barWidth)) {
                                        MiniSwipe.NEXT -> { drag.animateTo(-barWidth.toFloat()); graph.player.next() }
                                        MiniSwipe.PREVIOUS -> { drag.animateTo(barWidth.toFloat()); graph.player.previousTrack() }
                                        MiniSwipe.NONE -> drag.animateTo(0f)
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { drag.animateTo(0f) } },
                        ) { change, dx ->
                            change.consume()
                            scope.launch { drag.snapTo(drag.value + dx) }
                        }
                    }
                    .clickable(onClick = onOpen)
                    .padding(horizontal = Space.Small, vertical = Space.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                  Modifier
                      .weight(1f)
                      .graphicsLayer {
                          translationX = drag.value
                          alpha = 1f - (kotlin.math.abs(drag.value) / barWidth).coerceIn(0f, 0.7f)
                      },
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                Cover(song.cover, 150, Modifier.size(44.dp), RoundedCornerShape(6.dp))
                Spacer(Modifier.width(Space.Medium))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
              }
                // The buttons and their order are set in Settings › Style › Mini Player (issue #346).
                val settings by graph.settings.state.collectAsStateWithLifecycle()
                settings.miniButtons.forEach { button ->
                    MiniPlayerButton(button, state, song, onOpen, onStop = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        graph.player.stop()
                    })
                }
            }
            MiniProgress(state.durationMs)
        }
      }
    }
}

/** One of the mini player's buttons (issue #346); each sits in a 48 dp slot, so they stay evenly spaced. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniPlayerButton(button: MiniButton, state: PlayerState, song: app.musix.data.Song, onOpen: () -> Unit, onStop: () -> Unit) {
    val graph = LocalGraph.current
    when (button) {
        // Tap to play or pause; press and hold to stop and clear the queue.
        MiniButton.PLAY -> Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .combinedClickable(onClick = { graph.player.togglePlay() }, onLongClick = onStop),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause")
        }
        MiniButton.CAST -> CastButton()
        MiniButton.NEXT -> IconButton(onClick = { graph.player.next() }) { Icon(Icons.Rounded.SkipNext, "Next") }
        // Opens now playing with the lyrics showing.
        MiniButton.LYRICS -> IconButton(onClick = { LyricsShortcut.open(); onOpen() }) {
            Icon(Icons.Rounded.ChatBubble, "Lyrics")
        }
        MiniButton.ADD -> {
            val launch = rememberPlayLauncher()
            var picking by remember { mutableStateOf(false) }
            IconButton(onClick = { picking = true }) { Icon(Icons.Rounded.AddCircleOutline, "Add to playlist") }
            if (picking) {
                AddToPlaylistDialog(listOf(song)) { message ->
                    picking = false
                    message?.let { launch.notify(it) }
                }
            }
        }
        MiniButton.LIKE -> {
            val changes by graph.favorites.changes.collectAsStateWithLifecycle()
            val liked = changes.let { graph.favorites.isLiked(song) }
            IconButton(onClick = { graph.favorites.toggle(song) }) {
                Icon(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (liked) "Remove from liked songs" else "Like",
                    tint = if (liked) Accent else LocalContentColor.current,
                )
            }
        }
    }
}

/** Kept separate so the twice-a-second position update only redraws the thin progress line. */
@Composable
private fun MiniProgress(durationMs: Long) {
    val position by rememberPosition()
    val fraction = if (durationMs > 0) (position.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(horizontal = Space.Small).height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = 0.2f))) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(Color.White))
    }
}

@Composable
private fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < 33) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

/** What a swipe on the mini player does (issue #310). */
enum class MiniSwipe { NEXT, PREVIOUS, NONE }

/** A swipe of [moved] px on a bar [width] px wide: past a quarter left is the next song, right the one before. */
fun miniSwipe(moved: Float, width: Int): MiniSwipe = when {
    moved < -width * 0.25f -> MiniSwipe.NEXT
    moved > width * 0.25f -> MiniSwipe.PREVIOUS
    else -> MiniSwipe.NONE
}
