package app.siansa.ui

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Image
import coil3.compose.asPainter
import coil3.memory.MemoryCache
import coil3.SingletonImageLoader
import app.siansa.data.cover
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
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
import app.siansa.AppGraph
import app.siansa.data.LocalPlaylists
import app.siansa.data.Song
import app.siansa.log.AppLog
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
        // [modifier] (a page's padding) applies once it has loaded too: search's show-all pages started under the
        // title bar (issue #488). Pages pass their Scaffold padding here once, not again inside. Without a modifier
        // the content stays in its parent, so it can still align itself there (the lyrics' "No lyrics").
        is Load.Ok -> if (modifier == Modifier) content(load.value) else Box(modifier) { content(load.value) }
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
            // The list this radio started; if another starts or playback is stopped meanwhile, its later songs are dropped.
            val generation = graph.player.queueGeneration
            AppLog.i("Radio", "$label started with ${firstSongs.size} songs in ${System.currentTimeMillis() - started}ms")
            appScope.launch {
                val rest = runCatching { more(firstSongs) }
                    .onFailure { AppLog.w("Radio", "$label: more songs failed: ${it.message}") }
                    .getOrDefault(emptyList())
                AppLog.i("Radio", "$label added ${rest.size} more after ${System.currentTimeMillis() - started}ms")
                withContext(Dispatchers.Main) {
                    when (radioMore(firstSongs.isEmpty(), rest.isEmpty(), generation, graph.player.queueGeneration)) {
                        RadioMore.PLAY -> graph.player.play(rest)
                        RadioMore.ADD -> graph.player.addAll(rest)
                        RadioMore.DROP -> AppLog.i("Radio", "$label: ${rest.size} more songs dropped, something else plays now (or playback was stopped)")
                        RadioMore.NOTHING -> Unit
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

internal fun coverMemoryKey(id: String, size: Int?) = "cover:${app.siansa.covers.CoverVersions.cacheId(id)}:${coverServerPx(size) ?: "full"}@${coverDecodePx(size) ?: "full"}"

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
fun coverRequest(context: android.content.Context, graph: app.siansa.AppGraph, id: String, size: Int?): ImageRequest {
    val px = coverServerPx(size)
    val builder = ImageRequest.Builder(context).memoryCacheKey(coverMemoryKey(id, size))
    // A phone playlist's own picture, already on the phone.
    if (id.startsWith(LocalPlaylists.FILE_COVER)) builder.data(java.io.File(id.removePrefix(LocalPlaylists.FILE_COVER)))
    else builder.data(graph.source.coverUrl(id, px)).diskCacheKey("cover:${app.siansa.covers.CoverVersions.cacheId(id)}:${px ?: "full"}")
    // A fixed size lets the load start straight away instead of waiting for the layout.
    coverDecodePx(size)?.let { builder.size(it) }
    return builder.build()
}

/**
 * Album art from Navidrome. size = null would ask for the original file; screens use [LARGE_COVER] instead.
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
                if (standIn != null) {
                    Image(
                        painter = remember(standIn) { standIn.asPainter(context) },
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
fun SectionTitle(text: String, modifier: Modifier = Modifier, top: Dp = LocalSectionGap.current) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = Space.Page, end = Space.Page, top = top, bottom = Space.Medium),
    )
}

/** The space above a section's heading: between one section's last row and the next heading. */
val SECTION_GAP = Space.Section

/**
 * The gap above a section's title. Home sets it from Arrange Home's spacing slider (#554); everywhere else it's
 * [SECTION_GAP].
 */
val LocalSectionGap = compositionLocalOf { SECTION_GAP }

/** Home's section gap for a step of the slider (0 tight … 4 airy): the spacing scale's steps, [Space.Page] by default. */
fun homeGap(step: Int): Dp = when (step.coerceIn(0, app.siansa.data.AppSettings.HOME_GAP_MAX)) {
    0 -> Space.Small
    1 -> Space.Medium
    2 -> Space.Page
    3 -> Space.Section
    else -> Space.Large
}

/**
 * The space an [AlbumTile] keeps around itself: a small gap in grids, where it makes the gutters, and none in Home's
 * rows (#560), so an album's cover lines up with the page's edge and the row's gaps as a genre or radio tile does.
 */
val LocalTileInset = compositionLocalOf { Space.Small }

/**
 * Whether the music player's queue is [songs] (#562): the same songs, in any order (shuffled counts), leaving out the
 * ones keep playing or a radio added by itself ([auto]). How a card on Home knows its mix is the one playing.
 */
internal fun queueIsList(queue: List<app.siansa.data.Song>, auto: Set<Int>, songs: List<app.siansa.data.Song>): Boolean {
    if (songs.isEmpty() || queue.isEmpty()) return false
    val own = queue.filterIndexed { i, _ -> i !in auto }
    if (own.size != songs.size) return false
    val ids = own.mapTo(HashSet()) { it.id }
    return songs.all { it.id in ids }
}

/** A card's play button for [songs] (#562): ⏸ while they're the queue and playing; a tap then pauses or plays on. */
data class CardPlay(val loaded: Boolean, val playing: Boolean)

@Composable
fun rememberCardPlay(songs: List<app.siansa.data.Song>): CardPlay {
    val state by LocalGraph.current.player.state.collectAsStateWithLifecycle()
    val loaded = remember(state.queue, state.auto, songs) { queueIsList(state.queue, state.auto, songs) }
    return CardPlay(loaded, loaded && state.isPlaying)
}

/** What the slider's step is called. */
fun homeGapLabel(step: Int): String = HOME_GAP_LABELS[step.coerceIn(0, app.siansa.data.AppSettings.HOME_GAP_MAX)]

/** The spacing steps' names, tight to airy. */
val HOME_GAP_LABELS = listOf("Tight", "Compact", "Normal", "Roomy", "Airy")

/** Room for a song length up to "59:59", so the lengths in a list line up (issue #309). */
internal val DURATION_WIDTH = 36.dp

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
    // The song playing now (queue, album and playlist pages): its cover, or its number when covers are
    // hidden, shows play or pause, and a tap on it pauses or plays on (issue #345).
    nowPlaying: Boolean = false,
    // Added by keep playing or a song radio rather than chosen: a small mark beside it (issue #337).
    autoAdded: Boolean = false,
    // The subtitle in the accent colour, for a note about the song rather than its artist (issue #367).
    subtitleAccent: Boolean = false,
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
                if (nowPlaying && !showCover) {
                    // No cover on this page: the number's place holds the play/pause toggle instead (issue #345).
                    Box(Modifier.width(32.dp), contentAlignment = Alignment.CenterStart) {
                        NowPlayingToggle(Modifier.size(28.dp), RoundedCornerShape(14.dp), overCover = false)
                    }
                } else {
                    Text(
                        leading,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(32.dp),
                    )
                }
            }
            if (showCover) {
                Box(Modifier.size(48.dp)) {
                    Cover(song.cover, 150, Modifier.size(48.dp), RoundedCornerShape(6.dp))
                    if (nowPlaying) NowPlayingToggle(Modifier.size(48.dp), RoundedCornerShape(6.dp), overCover = true)
                }
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
                        color = if (subtitleAccent) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (autoAdded) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    "Added automatically",
                    Modifier.padding(start = Space.Small).size(16.dp).testTag("autoAdded"),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            DownloadedMark(song.id)
            if (song.durationSec > 0) {
                // A gap after a long title, and the same width for every length so they line up (issue #309).
                Text(
                    formatDuration(song.durationSec.toLong()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.padding(start = Space.Small).widthIn(min = DURATION_WIDTH).testTag("songLength"),
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
                        else RadioChooser.ask(song)
                    })
                    if (graph.settings.current.audioMuseReady) {
                        DropdownMenuItem(text = { Text("Song path…") }, onClick = {
                            menu = false
                            sheet.close()
                            nav.openSongPath(song.id)
                        })
                    }
                    DropdownMenuItem(text = { Text("Similar songs") }, onClick = {
                        menu = false
                        sheet.close()
                        nav.openSimilarSongs(song.id)
                    })
                    if (graph.settings.current.audioMuseReady && song.albumId != null) {
                        DropdownMenuItem(text = { Text("Similar albums") }, onClick = {
                            menu = false
                            sheet.close()
                            nav.openSimilarAlbums(song.id)
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

/** What the play/pause toggle on the song playing now says to a screen reader: what a tap will do (issue #345). */
internal fun playPauseLabel(isPlaying: Boolean): String = if (isPlaying) "Pause" else "Play"

/**
 * The play/pause toggle on the song playing now in a list (issue #345): pause while it plays, play while it's
 * paused. Over a cover it dims the cover so the symbol reads on any artwork; in place of a track number it's
 * the plain symbol in the theme's colour.
 */
@Composable
private fun NowPlayingToggle(modifier: Modifier, shape: Shape, overCover: Boolean) {
    val graph = LocalGraph.current
    val state by graph.player.state.collectAsStateWithLifecycle()
    val playing = state.isPlaying
    Box(
        modifier
            .clip(shape)
            .background(if (overCover) Color.Black.copy(alpha = 0.45f) else Color.Transparent)
            .clickable(onClickLabel = playPauseLabel(playing)) {
                AppLog.d("Ui", "Now playing row: ${if (playing) "pause" else "play"}")
                graph.player.togglePlay()
            }
            .testTag("nowPlayingToggle"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            playPauseLabel(playing),
            tint = if (overCover) Color.White else MaterialTheme.colorScheme.primary,
        )
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
    // The name sits a step under the cover, and the subtitle straight under the name, as one block (issue #338):
    // an even gap between all three left the artist floating away from the album name.
    Column(modifier.clickable(onClick = onClick).padding(LocalTileInset.current)) {
        Cover(
            coverArt,
            300,
            Modifier.fillMaxWidth().aspectRatio(1f).testTag("albumTileCover"),
            if (round) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(8.dp),
        )
        Spacer(Modifier.height(Space.Small))
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
                .testTag("gradientTileArt")
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
        // The same step under the tile whichever line comes first (issue #338: Made for you shows only the count).
        if (subtitle != null || caption != null) Spacer(Modifier.height(Space.Small))
        if (subtitle != null) {
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
 * items gets a tappable title that opens the full list (no arrow, issue #268).
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
                    .padding(start = Space.Page, end = Space.Page, top = LocalSectionGap.current, bottom = Space.Medium),
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
                // No arrow beside the title (issue #268); tapping the title still opens the full list.
            }
        } else {
            SectionTitle(title)
        }
        // Every row the same (#560): tiles start at the page's edge, a medium gap apart, with nothing added around them.
        CompositionLocalProvider(LocalTileInset provides 0.dp) {
            LazyRow(contentPadding = PaddingValues(horizontal = Space.Page), horizontalArrangement = Arrangement.spacedBy(Space.Medium), modifier = Modifier.testTag("shelfRow")) {
                items(items, key = key) { content(it) }
            }
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

/** What a radio does with the songs that arrive after it started (beta report 95a30366). */
internal enum class RadioMore { PLAY, ADD, DROP, NOTHING }

/**
 * A radio's later songs: played when it had none to start with, added when its list is still the one playing,
 * and dropped when another list started or playback was stopped meanwhile ([startedAt] vs [now] generations).
 */
internal fun radioMore(startedEmpty: Boolean, restEmpty: Boolean, startedAt: Long, now: Long): RadioMore = when {
    restEmpty -> RadioMore.NOTHING
    startedAt != now -> RadioMore.DROP
    startedEmpty -> RadioMore.PLAY
    else -> RadioMore.ADD
}
