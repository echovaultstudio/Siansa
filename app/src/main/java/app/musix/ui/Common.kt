package app.musix.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Image
import coil3.compose.asPainter
import coil3.memory.MemoryCache
import coil3.SingletonImageLoader
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.ui.draw.alpha
import android.os.VibratorManager
import android.os.Vibrator
import android.os.VibrationEffect
import android.content.Context
import android.os.Build
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.navigation.NavController
import app.musix.AppGraph
import app.musix.data.LocalPlaylists
import app.musix.data.Song
import app.musix.log.AppLog
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
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
// Lint can't see the assignment through the generic result; it's on the last line of the producer.
@android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
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

/**
 * Like [loadState], but back to [Load.Loading] as soon as [key] changes, so a page shows the spinner while
 * the new choice loads instead of the old result, which looks like the app hanging (issue #189, time machine).
 */
@android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
fun <T> loadStateFresh(key: Any?, block: suspend () -> T): State<Load<T>> =
    produceState<Load<T>>(Load.Loading, key) {
        this.value = Load.Loading
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
        is Load.Loading -> Box(modifier.fillMaxWidth().padding(Space.Large), contentAlignment = Alignment.Center) {
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
        modifier = modifier.padding(Space.Page),
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

/** What Navidrome is asked for: small sizes all share one, since each size of an animated cover costs it a conversion. */
fun coverServerPx(size: Int?): Int? = size?.let { if (it <= SMALL_COVER) SMALL_COVER else it }

/**
 * The size a cover is decoded at on the phone. List rows (48–56 dp) need about 192 px, not the 300 px
 * file: a quarter of the memory per cover, so several hundred stay in memory and scrolling back is instant.
 */
fun coverDecodePx(size: Int?): Int? = when {
    size == null -> null
    size <= 150 -> 192
    size <= SMALL_COVER -> SMALL_COVER
    else -> size
}

internal fun coverMemoryKey(id: String, size: Int?) = "cover:$id:${coverServerPx(size) ?: "full"}@${coverDecodePx(size) ?: "full"}"

/**
 * The memory keys of the same cover at the other sizes the app shows it, biggest first: what can stand
 * in while the size asked for loads. Opening now playing from a list finds the list's small cover
 * (or the mini player's) there, so the big cover never starts as an empty grey square.
 */
internal fun coverFallbackKeys(id: String, size: Int?): List<String> {
    val own = coverMemoryKey(id, size)
    return listOf(null, LARGE_COVER, SMALL_COVER, 150).map { coverMemoryKey(id, it) }.distinct().filter { it != own }
}

/** The request [Cover] makes, so covers can be loaded ahead of time into the same cache entries. */
fun coverRequest(context: android.content.Context, graph: app.musix.AppGraph, id: String, size: Int?): ImageRequest {
    val px = coverServerPx(size)
    val builder = ImageRequest.Builder(context).memoryCacheKey(coverMemoryKey(id, size))
    // A phone playlist's own picture, already on the phone.
    if (id.startsWith(LocalPlaylists.FILE_COVER)) builder.data(java.io.File(id.removePrefix(LocalPlaylists.FILE_COVER)))
    else builder.data(graph.subsonic.coverUrl(id, px)).diskCacheKey("cover:$id:${px ?: "full"}")
    // A fixed size lets the load start straight away instead of waiting for the layout.
    coverDecodePx(size)?.let { builder.size(it) }
    return builder.build()
}

/**
 * The album a cover id belongs to, from the id alone: Navidrome gives songs their disc's cover
 * (`dc-<album>:<disc>`), and a song without a cover id shows `al-<album>`. Null for anything else
 * (a song's own `mf-…` cover is looked up in the library; artists and playlists have no album).
 */
internal fun albumIdOfCover(coverId: String): String? {
    val own = coverId.substringBefore('_')
    return when {
        own.startsWith("dc-") -> own.removePrefix("dc-").substringBefore(':')
        own.startsWith("al-") -> own.removePrefix("al-")
        else -> null
    }?.takeIf { it.isNotEmpty() }
}

/**
 * Whether the album's cover can stand in while this one loads. The cover download saves album covers
 * under the album's id (`al-<album>_<last change>`), but songs ask for their disc's cover, so without this
 * every song list showed grey squares until each one came over the network, even with all covers downloaded.
 */
internal fun albumCanStandIn(coverId: String, albumCover: String?): Boolean = albumCover != null && albumCover != coverId

/** The album's cover from the phone (memory or the downloaded covers), never the network; null when it isn't there. */
private suspend fun albumStandIn(context: android.content.Context, graph: app.musix.AppGraph, coverId: String, size: Int?): coil3.Image? {
    val album = albumIdOfCover(coverId)?.let { graph.library.albumCover(it) }
        ?: if (coverId.startsWith("mf-")) graph.library.albumCoverOfSongCover(coverId) else null
    if (album == null || !albumCanStandIn(coverId, album)) return null
    val loader = SingletonImageLoader.get(context)
    (listOf(coverMemoryKey(album, size)) + coverFallbackKeys(album, size)).forEach { key ->
        loader.memoryCache?.get(MemoryCache.Key(key))?.image?.let { return it }
    }
    val request = coverRequest(context, graph, album, size).newBuilder().networkCachePolicy(CachePolicy.DISABLED).build()
    return (loader.execute(request) as? SuccessResult)?.image
}

/**
 * Album art from Navidrome. size = null would ask for the original file; screens use [LARGE_COVER] instead.
 * A song's cover that isn't on the phone yet shows its album's meanwhile, so every page gets that by itself.
 * The cache key leaves out the per-session login token, so covers are reused from disk across app starts.
 * Always a still picture (the first frame of an animated cover); [BigCover] shows animations.
 *
 * A cover already in memory is drawn in the same frame, with no placeholder: Coil on its own only looks
 * at its memory cache after the first layout, so even cached covers flashed the placeholder for a frame.
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
    val loader = SingletonImageLoader.get(context)
    val cached = remember(coverId, size) {
        coverId?.let { loader.memoryCache?.get(MemoryCache.Key(coverMemoryKey(it, size)))?.image }
    }
    // Not in memory at this size: show the same cover at another size that is, until this one is ready.
    val standIn = remember(coverId, size, cached) {
        if (cached != null || coverId == null) null
        else coverFallbackKeys(coverId, size).firstNotNullOfOrNull { loader.memoryCache?.get(MemoryCache.Key(it))?.image }
    }
    // A song's or disc's cover not on the phone yet: its album's, if that is, until this one arrives.
    val albumImage by produceState<coil3.Image?>(null, coverId, size) {
        if (cached == null && standIn == null && coverId != null) {
            value = runCatching { albumStandIn(context, graph, coverId, size) }.getOrNull()
        }
    }
    val request = remember(coverId, size) { coverId?.let { coverRequest(context, graph, it, size) } }
    Box(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            cached != null -> Image(
                painter = remember(cached) { cached.asPainter(context) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            request != null -> {
                val shown = standIn ?: albumImage
                if (shown != null) {
                    Image(
                        painter = remember(shown) { shown.asPainter(context) },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Draws over the stand-in once loaded (and nothing until then).
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Loads the covers just beyond what's on screen into memory while a list scrolls, so they're ready
 * (drawn in the same frame) when they come into view, as Symfonium and Glide-based apps do.
 */
@Composable
fun PrefetchCovers(firstVisible: Int, visibleCount: Int, covers: List<String?>, size: Int, ahead: Int = 24) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    LaunchedEffect(firstVisible / 4, covers.size) {
        val loader = SingletonImageLoader.get(context)
        val from = (firstVisible + visibleCount).coerceAtLeast(0)
        val to = (from + ahead).coerceAtMost(covers.size)
        val behind = (firstVisible - 8).coerceAtLeast(0) until firstVisible.coerceAtMost(covers.size)
        (from until to).plus(behind).mapNotNull { covers.getOrNull(it) }.distinct().forEach { id ->
            if (loader.memoryCache?.get(MemoryCache.Key(coverMemoryKey(id, size))) == null) {
                loader.enqueue(coverRequest(context.applicationContext, graph, id, size))
            }
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
fun SectionTitle(text: String, modifier: Modifier = Modifier, top: Dp = SECTION_GAP) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = Space.Page, end = Space.Page, top = top, bottom = Space.Medium),
    )
}

/** The space above a section's heading: between one section's last row and the next heading. */
val SECTION_GAP = Space.Section

fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}

/** A song's usual line under its title: "artist · album". */
fun artistAndAlbum(song: Song): String? =
    listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }

/**
 * A song row. Tap plays; swipe it from right to left to add it to the queue; the menu queues it, starts a
 * radio from it, opens its album or artist, or shows its details.
 */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    leading: String? = null,
    highlighted: Boolean = false,
    showCover: Boolean = true,
    // The line under the title; null shows none.
    subtitle: String? = artistAndAlbum(song),
    // Swipe from right to left adds the song to the queue (not in the queue itself).
    swipeToQueue: Boolean = true,
    // Extra items at the end of the ⋮ menu (such as removing it from a playlist); call close() when picked.
    extraMenu: @Composable (close: () -> Unit) -> Unit = {},
) {
    val graph = LocalGraph.current
    val nav = LocalNav.current
    val sheet = LocalPlayerSheet.current
    val openArtist = rememberArtistOpener(beforeOpen = { sheet.close() })
    val launch = rememberPlayLauncher()
    var menu by remember { mutableStateOf(false) }
    var pickPlaylist by remember { mutableStateOf(false) }
    // Offline, songs that aren't downloaded are dimmed: they can't play until the connection is back.
    val connection by graph.connection.state.collectAsStateWithLifecycle()
    val unavailable = connection.offline && !graph.downloads.isDownloaded(song.id)
    SwipeToQueue(song, enabled = swipeToQueue, onQueued = { launch.notify("Added to the queue") }) { rowBackground ->
        Row(
            Modifier
                .fillMaxWidth()
                .then(rowBackground)
                .alpha(if (unavailable) 0.38f else 1f)
                .clickable(onClick = onClick)
                .padding(start = Space.Page, end = Space.Tight, top = Space.Small, bottom = Space.Small),
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
                Spacer(Modifier.width(Space.Medium))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            DownloadedMark(song.id)
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
                    DropdownMenuItem(text = { Text("Add to playlist") }, onClick = {
                        menu = false
                        pickPlaylist = true
                    })
                    DropdownMenuItem(text = { Text("Start song radio") }, onClick = {
                        menu = false
                        if (graph.connection.current.offline) launch.notify("You're offline: song radio needs the server")
                        else graph.songRadio.start(song) { message -> launch.notify(message) }
                    })
                    if (graph.settings.current.audioMuseReady) {
                        DropdownMenuItem(text = { Text("Song path…") }, onClick = {
                            menu = false
                            sheet.close()
                            nav.openSongPath(song.id)
                        })
                    }
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
                    DropdownMenuItem(text = { Text("Show details") }, onClick = {
                        menu = false
                        sheet.close()
                        nav.openSongDetails(song.id)
                    })
                    SongDownloadMenuItem(song) { menu = false }
                    extraMenu { menu = false }
                }
            }
        }
    }
    if (pickPlaylist) {
        AddToPlaylistDialog(listOf(song)) { message ->
            pickPlaylist = false
            message?.let { launch.notify(it) }
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
    Column(modifier.clickable(onClick = onClick).padding(Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
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
                    Modifier.size(size * 0.45f).align(Alignment.BottomEnd).padding(Space.Small),
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
                modifier = Modifier.align(Alignment.TopStart).padding(Space.Medium),
            )
            if (busy) {
                CircularProgressIndicator(
                    Modifier.size(22.dp).align(Alignment.BottomStart).padding(2.dp),  // spacing: exact (the badge's inset)
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
        }
        if (subtitle != null) {
            Spacer(Modifier.height(Space.Small))
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
fun QuickTile(
    title: String,
    coverArt: String?,
    colors: Pair<Color, Color>?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
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
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Space.Medium),
        )
    }
}

/**
 * A horizontal shelf of tiles under a section title. With [onOpenAll], a shelf with more than a handful of
 * items gets a tappable title with an arrow that opens the full list.
 */
@Composable
fun <T> Shelf(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    onOpenAll: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    Column {
        if (onOpenAll != null && homeRowOpensFullList(items.size)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Show all", onClick = onOpenAll)
                    .padding(start = Space.Page, end = Space.Medium, top = Space.Section, bottom = Space.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.padding(start = Space.Tight).size(26.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SectionTitle(title)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = Space.Page), horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
            items(items, key = key) { content(it) }
        }
    }
}

/**
 * Spotify's swipe to queue: drag a song row from right to left past a third of its width and let go, and
 * the song is added to the queue (after the songs queued before it). The row slides back; an amber strip
 * with the queue icon shows behind it while dragging. [content] gets a background for the row, solid
 * only while it's dragged so the row looks as usual otherwise.
 */
@Composable
fun SwipeToQueue(song: Song, enabled: Boolean, onQueued: () -> Unit, content: @Composable (Modifier) -> Unit) {
    if (!enabled) {
        content(Modifier)
        return
    }
    val graph = LocalGraph.current
    val context = LocalContext.current
    val current by rememberUpdatedState(song)
    val queued by rememberUpdatedState(onQueued)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                // A firmer buzz when the song is added.
                QueueHaptics.vibrate(context, QueueHaptics.Kind.ADDED)
                AppLog.i("Queue", "Swiped ${current.id} into the queue")
                graph.player.addToQueue(current)
                queued()
            }
            // Never stays swiped away: the row slides back.
            false
        },
        positionalThreshold = { width -> width * SWIPE_TO_QUEUE_FRACTION },
    )
    val swiping = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
    // A clear tap the moment the amber "Add to queue" strip shows, as the swipe starts.
    LaunchedEffect(swiping) {
        if (swiping) QueueHaptics.vibrate(context, QueueHaptics.Kind.START)
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().background(if (swiping) Accent else Color.Transparent).padding(horizontal = Space.Section),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (swiping) {
                    Text("Add to queue", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = OnAccent)
                    Spacer(Modifier.width(Space.Medium))
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = OnAccent)
                }
            }
        },
    ) {
        content(if (swiping) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier)
    }
}

/**
 * The vibrations of swipe to queue. They use the phone's vibration motor directly (predefined click
 * effects, or a short pulse on older phones), which is clearly stronger than a touch-feedback tick,
 * but they're skipped when touch vibration is turned off in the phone's settings.
 */
object QueueHaptics {
    enum class Kind { START, ADDED }

    /** What to play: a predefined effect id (Android 10+), else a pulse of [pulseMs]. */
    data class Effect(val predefined: Int?, val pulseMs: Long)

    @android.annotation.SuppressLint("InlinedApi") // the effect ids are only used on Android 10+
    fun effect(kind: Kind, sdk: Int): Effect = when (kind) {
        // As the amber strip appears: a crisp click.
        Kind.START -> if (sdk >= 29) Effect(VibrationEffect.EFFECT_CLICK, 0) else Effect(null, 25)
        // Song added: a heavier click.
        Kind.ADDED -> if (sdk >= 29) Effect(VibrationEffect.EFFECT_HEAVY_CLICK, 0) else Effect(null, 45)
    }

    fun vibrate(context: Context, kind: Kind) {
        val touchFeedback = runCatching {
            android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED, 1)
        }.getOrDefault(1)
        if (touchFeedback == 0) return
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        val e = effect(kind, Build.VERSION.SDK_INT)
        runCatching {
            if (Build.VERSION.SDK_INT >= 29 && e.predefined != null) {
                vibrator.vibrate(VibrationEffect.createPredefined(e.predefined))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(e.pulseMs, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }.onFailure { AppLog.w("Queue", "Vibration failed: ${it.javaClass.simpleName}") }
    }
}

/** How far a song row has to be dragged (part of its width) to be added to the queue. */
const val SWIPE_TO_QUEUE_FRACTION = 0.35f

/**
 * Shown instead of an AI tool (mixes, mood playlists, sound search) while AudioMuse can't be used: a sad
 * face, "AI backend is currently not available :(" and why ("You're offline."). Null [problem] shows nothing.
 */
@Composable
fun AiUnavailable(problem: String?, modifier: Modifier = Modifier, onDark: Boolean = false) {
    if (problem == null) return
    val text = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val subtle = if (onDark) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth().padding(vertical = Space.Small), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.SentimentDissatisfied, null, tint = subtle, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(Space.Medium))
        Column {
            Text("AI backend is currently not available :(", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = text)
            Text(problem, style = MaterialTheme.typography.bodySmall, color = subtle)
        }
    }
}

/** Why the AI tools can't be used right now, or null when they can; follows the connection as it changes. */
@Composable
fun rememberAiProblem(): String? {
    val graph = LocalGraph.current
    val connection by graph.connection.state.collectAsStateWithLifecycle()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    return connection.aiProblem(settings)
}
