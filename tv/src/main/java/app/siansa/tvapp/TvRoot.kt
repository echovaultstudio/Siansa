package app.siansa.tvapp

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.siansa.log.AppLog
import app.siansa.ui.LocalGraph
import app.siansa.ui.LocalNav
import app.siansa.ui.LocalSnack
import app.siansa.ui.Space
import app.siansa.ui.siansaRoutes
import kotlinx.coroutines.flow.MutableStateFlow

/** The TV app's own pages (issue #860), by route. Album, artist, playlist and mix use the phone's routes, so a phone page
 * that opens one (a mix from Auto Generated Mixes, an album from a radio) opens the TV's. */
object TvRoutes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val NOW_PLAYING = "tv/now-playing"
    const val PASSTHROUGH = "tv/passthrough"
    const val SOURCES = "tv/sources"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{id}"
    const val PLAYLIST = "playlist/{id}"
    const val MIX = "mix/{id}"

    /** The phone's pages the TV puts its own in place of. */
    val own = setOf(HOME, LIBRARY, SEARCH, SETTINGS, ALBUM, ARTIST, PLAYLIST, MIX)

    fun album(id: String) = "album/${Uri.encode(id)}"
    fun artist(id: String) = "artist/${Uri.encode(id)}"
    fun playlist(id: String) = "playlist/${Uri.encode(id)}"
    fun mix(id: String) = "mix/${Uri.encode(id)}"
}

/** The tabs along the top, in order. */
private val TABS = listOf(TvRoutes.HOME to "Home", TvRoutes.LIBRARY to "Library", TvRoutes.SEARCH to "Search", TvRoutes.SETTINGS to "Settings")

/**
 * The TV app's frame: sign-in until a server is set, then the tabs along the top and the page under them. Every page of
 * the phone app is there too (its settings pages, sync, Report a problem…), opened from the TV's own pages. Back goes
 * back a page, then to Home, then leaves the app.
 */
@Composable
fun TvRoot(searches: MutableStateFlow<String?>) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    if (!settings.navidromeReady || signingIn) {
        TvSignIn()
        return
    }
    val nav = rememberNavController()
    val snack = remember { SnackbarHostState() }
    val player by graph.player.state.collectAsStateWithLifecycle()
    val search by searches.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var tab by rememberSaveable { mutableStateOf(TvRoutes.HOME) }

    // Messages from the player (a song that can't play, say), as on the phone.
    LaunchedEffect(Unit) { graph.player.notices.collect { snack.showSnackbar(it) } }
    LaunchedEffect(route) {
        if (route != null) {
            AppLog.i("Nav", "tv: $route")
            AppLog.screen = "tv:$route"
            if (TABS.any { it.first == route }) tab = route
        }
    }
    // Starting something to play (a song, an album, a radio, here or on a phone page) opens Now playing, unless that's
    // switched off under Settings: on a TV the player is what's watched.
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(nav) {
        graph.player.newQueues.collect {
            if (TvPrefs.opensPlayer(context) && nav.currentDestination?.route != TvRoutes.NOW_PLAYING) {
                nav.navigate(TvRoutes.NOW_PLAYING) { launchSingleTop = true }
            }
        }
    }
    // A search from Google TV opens the Search page with it.
    LaunchedEffect(search) { if (search != null) nav.switchTab(TvRoutes.SEARCH) }

    // A link on a phone page (What's new, a service's sign-up) usually has no browser to open in on a TV: say where to go.
    val system = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val links = remember(system) {
        object : UriHandler {
            override fun openUri(uri: String) {
                runCatching { system.openUri(uri) }.onFailure {
                    AppLog.w("Tv", "No browser for $uri: ${it.message}")
                    scope.launch { snack.showSnackbar("Open this on your phone or computer: $uri") }
                }
            }
        }
    }

    CompositionLocalProvider(LocalNav provides nav, LocalSnack provides snack, LocalUriHandler provides links) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (route != TvRoutes.NOW_PLAYING) {
                    TopBar(
                        current = tab,
                        playing = player.hasMedia,
                        moving = player.isPlaying,
                        onTab = { nav.switchTab(it) },
                        onNowPlaying = { nav.navigate(TvRoutes.NOW_PLAYING) { launchSingleTop = true } },
                    )
                }
                NavHost(
                    navController = nav,
                    startDestination = TvRoutes.HOME,
                    modifier = Modifier.fillMaxSize(),
                    // Pages switch at once (issue #947): a fade drew both pages together for a while, which a TV
                    // feels as lag.
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None },
                ) {
                    composable(TvRoutes.HOME) { TvHome() }
                    composable(TvRoutes.LIBRARY) { TvLibrary() }
                    composable(TvRoutes.SEARCH) { TvSearch(search, onDone = { searches.value = null }) }
                    composable(TvRoutes.SETTINGS) { TvSettingsHub() }
                    composable(TvRoutes.PASSTHROUGH) { TvPassthrough() }
                    composable(TvRoutes.SOURCES) { TvSources() }
                    composable(TvRoutes.NOW_PLAYING) { TvNowPlaying() }
                    composable(TvRoutes.ALBUM) { TvAlbum(it.arguments?.getString("id").orEmpty()) }
                    composable(TvRoutes.ARTIST) { TvArtist(it.arguments?.getString("id").orEmpty()) }
                    composable(TvRoutes.PLAYLIST) { TvPlaylist(it.arguments?.getString("id").orEmpty()) }
                    composable(TvRoutes.MIX) { TvMix(it.arguments?.getString("id").orEmpty()) }
                    // The rest of the app's pages, the phone's own, for what the TV doesn't redo (settings, sync…).
                    siansaRoutes(nav, except = TvRoutes.own)
                }
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(Space.Large))
            // "Start song radio" from a song's actions, here or on a phone page, asks Steady or DJ.
            app.siansa.ui.RadioChooserHost()
        }
    }
}

/** The TV app's own settings, kept on the TV (not in the phone's settings the two share). */
object TvPrefs {
    private const val FILE = "tv"
    private const val OPENS_PLAYER = "opens_player"

    private fun prefs(context: android.content.Context) = context.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE)

    /** Whether starting something to play opens Now playing. On until switched off. */
    fun opensPlayer(context: android.content.Context): Boolean = prefs(context).getBoolean(OPENS_PLAYER, true)

    fun setOpensPlayer(context: android.content.Context, on: Boolean) = prefs(context).edit().putBoolean(OPENS_PLAYER, on).apply()
}

/**
 * Opens a tab's own page from wherever the remote is: Library from an album opened in it goes back to the Library, not
 * to the album. Back from a tab goes Home.
 */
fun NavController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id)
    launchSingleTop = true
}

@Composable
private fun TopBar(current: String, playing: Boolean, onTab: (String) -> Unit, onNowPlaying: () -> Unit, moving: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TV_EDGE, vertical = Space.Page),
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Siansa's name as in its logo: the S in the accent, the rest white.
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("S") }
                append("iansa")
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(end = Space.Section),
        )
        for ((tab, label) in TABS) {
            val selected = tab == current
            val shape = RoundedCornerShape(percent = 50)
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .tvFocus(shape, onClick = { onTab(tab) })
                    .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background, shape)
                    .padding(horizontal = Space.Page, vertical = Space.Small),
            )
        }
        if (playing) {
            Box(Modifier.weight(1f))
            Row(
                Modifier
                    .tvFocus(RoundedCornerShape(percent = 50), onClick = onNowPlaying)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(percent = 50))
                    .padding(horizontal = Space.Page, vertical = Space.Small),
                horizontalArrangement = Arrangement.spacedBy(Space.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NowPlayingWave(moving, MaterialTheme.colorScheme.primary)
                Text("Now playing", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * The Now playing chip's wave (issue #974): five bars that rise and fall lightly while the music plays, still while
 * it's paused. Only drawn again as they move, never composed again, so it costs the TV next to nothing.
 */
@Composable
internal fun NowPlayingWave(moving: Boolean, color: androidx.compose.ui.graphics.Color) {
    val phase = if (moving) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "wave")
        transition.animateFloat(
            0f, (2 * Math.PI).toFloat(),
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(WAVE_MS, easing = androidx.compose.animation.core.LinearEasing)),
            label = "wavePhase",
        )
    } else null
    androidx.compose.foundation.Canvas(Modifier.size(WAVE_SIZE).testTag(if (moving) "nowPlayingWaveMoving" else "nowPlayingWave")) {
        val bars = WAVE_BARS.size
        val gap = size.width / (bars * 2 - 1)
        val t = phase?.value ?: 0f
        WAVE_BARS.forEachIndexed { i, rest ->
            // Each bar its own step behind the last, a gentle swing around its height at rest.
            val swing = if (phase != null) WAVE_SWING * kotlin.math.sin(t + i * WAVE_STEP) else 0f
            val h = size.height * (rest + swing).coerceIn(0.2f, 1f)
            drawRoundRect(
                color,
                topLeft = androidx.compose.ui.geometry.Offset(i * gap * 2, (size.height - h) / 2),
                size = androidx.compose.ui.geometry.Size(gap, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(gap / 2, gap / 2),
            )
        }
    }
}

/** The wave's bars at rest, as Material's GraphicEq. */
private val WAVE_BARS = listOf(0.45f, 0.8f, 1f, 0.7f, 0.4f)
private const val WAVE_SWING = 0.18f
private const val WAVE_STEP = 1.1f
private const val WAVE_MS = 1_600
private val WAVE_SIZE = 24.dp

