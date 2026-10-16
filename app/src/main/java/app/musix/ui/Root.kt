package app.musix.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
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
    Tab("library", "Library", Icons.Rounded.LibraryMusic),
    Tab("vibes", "Vibes", Icons.Rounded.GraphicEq),
)

private val noBottomBar = setOf("player")

@Composable
fun MusixRoot() {
    val graph = LocalGraph.current
    val nav = rememberNavController()
    val snack = remember { SnackbarHostState() }
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

    AskForNotifications()

    CompositionLocalProvider(LocalNav provides nav, LocalSnack provides snack) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                if (route !in noBottomBar) {
                    Column {
                        if (player.hasMedia) MiniPlayer(player) { nav.navigate("player") { launchSingleTop = true } }
                        NavigationBar {
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentTab == tab.route,
                                    onClick = {
                                        if (currentTab == tab.route) nav.backToTabTop(tab.route) else nav.switchTab(tab.route)
                                        currentTab = tab.route
                                    },
                                    icon = { Icon(tab.icon, null) },
                                    label = { Text(tab.label) },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = "home",
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            ) {
                composable("home") { HomeScreen(nav) }
                composable("search") { SearchScreen(nav) }
                composable("library") { LibraryScreen(nav) }
                composable("vibes") { VibesScreen(nav) }
                composable("settings") { SettingsHub(nav) }
                composable("settings/connections") { ConnectionsScreen(nav) }
                composable("settings/playback") { PlaybackSettingsScreen(nav) }
                composable("settings/storage") { StorageScreen(nav) }
                composable("settings/logs") { LogsScreen(nav) }
                composable("settings/scrobbling") { ScrobblingScreen(nav) }
                composable("liked") { LikedScreen(nav) }
                composable("radio/{kind}/{key}") {
                    RadioScreen(nav, it.arguments?.getString("kind").orEmpty(), it.arguments?.getString("key").orEmpty())
                }
                composable("mood/{text}") { MoodScreen(nav, it.arguments?.getString("text").orEmpty()) }
                composable("sync") { SyncScreen(nav) }
                composable("player") { PlayerScreen(nav) }
                composable("album/{id}") { AlbumScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("artist/{id}") { ArtistScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("playlist/{id}") { PlaylistScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("mix/{id}") { MixScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("selector/{id}") { SelectorEditor(nav, it.arguments?.getString("id").orEmpty()) }
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

@Composable
private fun MiniPlayer(state: PlayerState, onOpen: () -> Unit) {
    val graph = LocalGraph.current
    val song = state.current ?: return
    Surface(tonalElevation = 3.dp) {
        Column {
            MiniProgress(state.durationMs)
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(song.coverArt, 150, Modifier.size(44.dp), RoundedCornerShape(6.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { graph.player.togglePlay() }) {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause")
                }
                IconButton(onClick = { graph.player.next() }) {
                    Icon(Icons.Rounded.SkipNext, "Next")
                }
            }
        }
    }
}

/** Kept separate so the twice-a-second position update only redraws the thin progress line. */
@Composable
private fun MiniProgress(durationMs: Long) {
    val position by rememberPosition()
    LinearProgressIndicator(
        progress = { if (durationMs > 0) (position.toFloat() / durationMs).coerceIn(0f, 1f) else 0f },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < 33) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
