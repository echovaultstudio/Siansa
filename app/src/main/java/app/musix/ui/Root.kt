package app.musix.ui

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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
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
import app.musix.playback.PlayerState

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Rounded.Home),
    Tab("search", "Search", Icons.Rounded.Search),
    Tab("library", "Your Library", Icons.Rounded.LibraryMusic),
    Tab("vibes", "Vibes", Icons.Rounded.GraphicEq),
    Tab("settings", "Settings", Icons.Rounded.Settings),
)

/** Height of the see-through bottom bar (mini player + tabs), so scrolling pages can end above it. */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

/** Bottom padding for a scrolling page: clear of the bottom bar, plus [extra]. */
@Composable
fun bottomBarPadding(extra: Dp = 24.dp): Dp = LocalBottomBarPadding.current + extra

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
    val sheet = remember { PlayerSheet() }
    val player by graph.player.state.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    // The tab the user is "in", which stays lit while they browse deeper (album, artist…).
    var currentTab by rememberSaveable { mutableStateOf("home") }

    LaunchedEffect(route) {
        if (route != null) {
            AppLog.i("Nav", route)
            AppLog.screen = route
        }
        tabs.firstOrNull { it.route == route }?.let { currentTab = it.route }
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
                                0.3f to Brand.Plum.copy(alpha = 0.78f),
                                1f to Brand.Plum.copy(alpha = 0.96f),
                            )
                        )
                    ) {
                        if (player.hasMedia) MiniPlayer(player) { sheet.open() }
                        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentTab == tab.route,
                                    onClick = {
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
                composable("settings") { SettingsHub(nav) }
                composable("settings/connections") { ConnectionsScreen(nav) }
                composable("settings/playback") { PlaybackSettingsScreen(nav) }
                composable("settings/storage") { StorageScreen(nav) }
                composable("settings/logs") { LogsScreen(nav) }
                composable("settings/scrobbling") { ScrobblingScreen(nav) }
                composable("home/{section}") { HomeSectionScreen(nav, it.arguments?.getString("section").orEmpty()) }
                composable("liked") { LikedScreen(nav) }
                composable("radio/{kind}/{key}") {
                    RadioScreen(nav, it.arguments?.getString("kind").orEmpty(), it.arguments?.getString("key").orEmpty())
                }
                composable("mood/{text}") { MoodScreen(nav, it.arguments?.getString("text").orEmpty()) }
                composable("sync") { SyncScreen(nav) }
                composable("report/{range}") { ReportScreen(nav, it.arguments?.getString("range").orEmpty()) }
                composable("album/{id}") { AlbumScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("artist/{id}") { ArtistScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("playlist/{id}") { PlaylistScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("mix/{id}") { MixScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("selector/{id}") { SelectorEditor(nav, it.arguments?.getString("id").orEmpty()) }
            }
          }
        }

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
    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
      CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(Modifier.clip(RoundedCornerShape(10.dp)).background(background)) {
            Row(
                Modifier.fillMaxWidth().testTag("miniPlayer").clickable(onClick = onOpen).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(song.cover, 150, Modifier.size(44.dp), RoundedCornerShape(6.dp))
                Spacer(Modifier.width(12.dp))
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
                // Tap to play or pause; press and hold to stop and clear the queue.
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = { graph.player.togglePlay() },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                graph.player.stop()
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause")
                }
                IconButton(onClick = { graph.player.next() }) {
                    Icon(Icons.Rounded.SkipNext, "Next")
                }
            }
            MiniProgress(state.durationMs)
        }
      }
    }
}

/** Kept separate so the twice-a-second position update only redraws the thin progress line. */
@Composable
private fun MiniProgress(durationMs: Long) {
    val position by rememberPosition()
    val fraction = if (durationMs > 0) (position.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = 0.2f))) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(Color.White))
    }
}

@Composable
private fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < 33) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
