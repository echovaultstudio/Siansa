package app.musix.ui

import app.musix.data.cover
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.musix.AppGraph
import app.musix.data.Song
import app.musix.log.AppLog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

val LocalGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph not provided") }
val LocalNav = staticCompositionLocalOf<NavController> { error("NavController not provided") }
val LocalSnack = staticCompositionLocalOf<SnackbarHostState> { error("Snackbar not provided") }

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

fun <T> Load<T>.valueOrNull(): T? = if (this is Load.Ok<T>) value else null

/** Runs [block] when [key] changes and exposes loading / result / error. */
@Composable
fun <T> loadState(key: Any?, block: suspend () -> T): State<Load<T>> =
    produceState<Load<T>>(Load.Loading, key) {
        val result: Load<T> = try {
            Load.Ok(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Ui", "Loading failed: ${e.message}")
            Load.Err(e.message ?: e.javaClass.simpleName)
        }
        this.value = result
    }

@Composable
fun <T> LoadBox(load: Load<T>, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    when (load) {
        is Load.Loading -> Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is Load.Err -> ErrorText(load.message, modifier)
        is Load.Ok -> content(load.value)
    }
}

@Composable
fun ErrorText(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.padding(16.dp),
    )
}

/** Player position, polled twice a second while visible. */
@Composable
fun rememberPosition(): State<Long> {
    val graph = LocalGraph.current
    return produceState(0L) {
        while (true) {
            value = graph.player.positionMs()
            delay(500)
        }
    }
}

/**
 * Starts playback of a list that takes a moment to build (a radio), with a short note while it loads
 * and a readable message if it fails. [PlayLauncher.progressive] starts at once with a quick list
 * and adds the rest to the queue when it arrives.
 */
class PlayLauncher(
    private val graph: AppGraph,
    private val snack: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    // Songs added after the start keep coming even if you leave the screen, so this part runs app-wide.
    private val appScope: CoroutineScope get() = graph.scope
    fun notify(message: String) {
        scope.launch { snack.showSnackbar(message) }
    }

    operator fun invoke(label: String, build: suspend () -> List<Song>) {
        scope.launch {
            val note = launch { snack.showSnackbar("Starting $label…") }
            try {
                val songs = build()
                note.cancel()
                graph.player.play(songs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                note.cancel()
                AppLog.w("Radio", "$label failed: ${e.message}")
                snack.showSnackbar(e.message ?: "Couldn't start $label")
            }
        }
    }

    fun progressive(label: String, first: suspend () -> List<Song>, more: suspend (List<Song>) -> List<Song>) {
        scope.launch {
            val started = System.currentTimeMillis()
            val firstSongs = try {
                first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snack.showSnackbar(e.message ?: "Couldn't start $label")
                return@launch
            }
            if (firstSongs.isNotEmpty()) graph.player.play(firstSongs)
            AppLog.i("Radio", "$label started with ${firstSongs.size} songs in ${System.currentTimeMillis() - started}ms")
            appScope.launch {
                val rest = runCatching { more(firstSongs) }
                    .onFailure { AppLog.w("Radio", "$label: more songs failed: ${it.message}") }
                    .getOrDefault(emptyList())
                AppLog.i("Radio", "$label added ${rest.size} more after ${System.currentTimeMillis() - started}ms")
                withContext(Dispatchers.Main) {
                    if (firstSongs.isEmpty()) {
                        if (rest.isNotEmpty()) graph.player.play(rest)
                    } else {
                        graph.player.addAll(rest)
                    }
                }
            }
        }
    }
}

@Composable
fun rememberPlayLauncher(): PlayLauncher {
    val graph = LocalGraph.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    return remember(graph, snack, scope) { PlayLauncher(graph, snack, scope) }
}

/**
 * Pixel size for big covers and photos. Navidrome resizes and compresses them (at its cover quality
 * setting), so there's no need to download multi-megabyte originals. The exception is an animated WebP,
 * which Navidrome always sends whole; CoverStore keeps that once and answers with a still of it.
 */
const val LARGE_COVER = 1000

/** The request [Cover] makes, so covers can be loaded ahead of time into the same cache entries. */
fun coverRequest(context: android.content.Context, graph: app.musix.AppGraph, id: String, px: Int?): ImageRequest {
    val key = "cover:$id:${px ?: "full"}"
    return ImageRequest.Builder(context)
        .data(graph.subsonic.coverUrl(id, px))
        .diskCacheKey(key)
        .memoryCacheKey(key)
        .build()
}

/**
 * Album art from Navidrome. size = null would ask for the original file; screens use [LARGE_COVER] instead.
 * The cache key leaves out the per-session login token, so covers are reused from disk across app starts.
 * Always a still picture (the first frame of an animated cover); [BigCover] shows animations.
 */
@Composable
fun Cover(
    coverId: String?,
    size: Int?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    // Small sizes all share one: each size of an animated cover costs Navidrome a conversion.
    val px = size?.let { if (it <= SMALL_COVER) SMALL_COVER else it }
    val request = remember(coverId, px) { coverId?.let { coverRequest(context, graph, it, px) } }
    Box(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A screen with a top bar, optional back arrow and actions. */
@Composable
fun Screen(
    title: String,
    nav: NavController?,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (nav != null) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                    }
                },
                actions = { actions() },
            )
        },
    ) { padding ->
        // Settings-style pages end above the see-through bottom bar.
        Box(Modifier.padding(bottom = LocalBottomBarPadding.current)) { content(padding) }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 10.dp),
    )
}

fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}

/** A song row. Tap plays; the menu queues it, starts a radio from it or opens its album or artist. */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    leading: String? = null,
    highlighted: Boolean = false,
    showCover: Boolean = true,
) {
    val graph = LocalGraph.current
    val nav = LocalNav.current
    val sheet = LocalPlayerSheet.current
    val openArtist = rememberArtistOpener(beforeOpen = { sheet.close() })
    val launch = rememberPlayLauncher()
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(
                leading,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp),
            )
        }
        if (showCover) {
            Cover(song.cover, 150, Modifier.size(48.dp), RoundedCornerShape(6.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (song.durationSec > 0) {
            Text(
                formatDuration(song.durationSec.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = {
                    menu = false
                    graph.player.playNext(song)
                })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = {
                    menu = false
                    graph.player.addToQueue(song)
                })
                val liked = graph.favorites.isLiked(song)
                DropdownMenuItem(text = { Text(if (liked) "Remove from liked songs" else "Add to liked songs") }, onClick = {
                    menu = false
                    graph.favorites.toggle(song)
                })
                DropdownMenuItem(text = { Text("Start song radio") }, onClick = {
                    menu = false
                    graph.songRadio.start(song) { message -> launch.notify(message) }
                })
                song.albumId?.let { albumId ->
                    DropdownMenuItem(text = { Text("Go to album") }, onClick = {
                        menu = false
                        sheet.close()
                        nav.navigate("album/$albumId")
                    })
                }
                if (song.artistId != null || song.artists.isNotEmpty()) {
                    DropdownMenuItem(text = { Text("Go to artist") }, onClick = {
                        menu = false
                        openArtist(song)
                    })
                }
            }
        }
    }
}

/** A square cover with a title and subtitle under it, as in a streaming app's shelves. */
@Composable
fun AlbumTile(
    name: String,
    subtitle: String,
    coverArt: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
) {
    Column(modifier.clickable(onClick = onClick).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Cover(
            coverArt,
            300,
            Modifier.fillMaxWidth().aspectRatio(1f),
            if (round) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(8.dp),
        )
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Two colours picked from a name, so generated tiles keep the same look every day. */
fun tileColors(seed: String): Pair<Color, Color> {
    val palette = listOf(
        0xFF2E8B9A to 0xFF143A52, 0xFFE8572A to 0xFF7A1530, 0xFF4B5FD6 to 0xFF1C1F55,
        0xFFF2B33D to 0xFFD9542B, 0xFFD6338A to 0xFF4A1470, 0xFF5D6B8A to 0xFF1E2433,
        0xFF3A9A5B to 0xFF0F3B2A, 0xFF8E44AD to 0xFF2C0F3D, 0xFFC2416B to 0xFF4D0F2A,
        0xFF00897B to 0xFF0B2E3A,
    )
    val (a, b) = palette[abs(seed.hashCode()) % palette.size]
    return Color(a) to Color(b)
}

/**
 * A generated tile in the style of "Made for you" and radio tiles: a colour gradient with a bold
 * title, an optional cover peeking in the corner, and a label underneath.
 */
@Composable
fun GradientTile(
    title: String,
    colors: Pair<Color, Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    caption: String? = null,
    coverArt: String? = null,
    size: Dp = 160.dp,
    busy: Boolean = false,
) {
    Column(modifier.width(size).clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(colors.first, colors.second))),
        ) {
            if (coverArt != null) {
                Cover(
                    coverArt,
                    300,
                    Modifier.size(size * 0.45f).align(Alignment.BottomEnd).padding(10.dp),
                    RoundedCornerShape(6.dp),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
            if (busy) {
                CircularProgressIndicator(
                    Modifier.size(22.dp).align(Alignment.BottomStart).padding(2.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
        }
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A compact "quick access" tile: small cover on the left, name on the right. */
@Composable
fun QuickTile(title: String, coverArt: String?, colors: Pair<Color, Color>?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (coverArt != null) {
            Cover(coverArt, 150, Modifier.size(56.dp), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
        } else {
            Box(
                Modifier.size(56.dp).background(
                    Brush.linearGradient(listOf(colors?.first ?: Color.Gray, colors?.second ?: Color.DarkGray))
                )
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

/** A horizontal shelf of tiles under a section title. */
@Composable
fun <T> Shelf(title: String, items: List<T>, key: (T) -> Any, content: @Composable (T) -> Unit) {
    if (items.isEmpty()) return
    Column {
        SectionTitle(title)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = key) { content(it) }
        }
    }
}
