package app.musix.ui

import android.content.Context
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.musix.AppGraph
import app.musix.log.AppLog
import android.os.Build
import app.musix.covers.CoverStore
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.decode.Decoder
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Size Musix asks Navidrome for in lists, tiles and backgrounds. One size means one conversion per album. */
const val SMALL_COVER = 300

/**
 * Album covers that may be animated (a cover.gif or animated cover.webp in the album folder).
 *
 * Tested against Navidrome 0.64:
 * - An animated GIF is converted with ffmpeg for every requested size. 300 px works (~5 s); 600 and
 *   1000 px failed and returned no image, and failed conversions are retried on every request.
 * - An animated WebP isn't resized at all: every size returns the original file.
 *
 * So, per album, Musix reads only the first bytes of the original to see what it is (no conversion), and:
 * - animated WebP, or a small GIF (≤ 3 MB): keeps the original once on the phone ([CoverStore]) and
 *   animates that; everything else uses a still made from its first frame;
 * - big GIF: animates Navidrome's 300 px version, which it can convert, and never asks for bigger sizes;
 * - remembers the outcome across app starts.
 * Like Symfonium, covers only move on the album page and in now playing; lists show still frames.
 */
object AnimatedCovers {
    private const val MAX_ORIGINAL_BYTES = 3L * 1024 * 1024

    sealed interface Kind {
        /** A normal picture: fine to load at any size. */
        data object Still : Kind
        /** Animated, kept on the phone by [CoverStore]: animate the original, use its still elsewhere. */
        data object Local : Kind
        /** Animated, and Navidrome can shrink it: animate its [size] px version. */
        data class Animated(val size: Int) : Kind
        /** Animated, but only the 300 px still is safe to load. */
        data object StillOnlySmall : Kind
    }

    private val known = ConcurrentHashMap<String, Kind>()
    /** The last few animations, so going back and forth doesn't decode them again; older ones are let go. */
    private val loads = object : LinkedHashMap<String, Deferred<Drawable?>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Deferred<Drawable?>>) = size > 4
    }
    private val locks = ConcurrentHashMap<String, Mutex>()
    @Volatile private var loaded = false

    private fun prefs(graph: AppGraph) = graph.app.getSharedPreferences("animated_covers", Context.MODE_PRIVATE)

    private fun loadSaved(graph: AppGraph) {
        if (loaded) return
        loaded = true
        prefs(graph).all.forEach { (id, value) ->
            val text = value as? String ?: return@forEach
            known[id] = when {
                text == "still" -> Kind.Still
                text == "small" -> Kind.StillOnlySmall
                text == "local" -> Kind.Local
                text == "anim:0" -> Kind.Local
                text.startsWith("anim:") -> Kind.Animated(text.removePrefix("anim:").toIntOrNull() ?: return@forEach)
                else -> return@forEach
            }
        }
    }

    private fun remember(graph: AppGraph, id: String, kind: Kind) {
        known[id] = kind
        val text = when (kind) {
            Kind.Still -> "still"
            Kind.StillOnlySmall -> "small"
            Kind.Local -> "local"
            is Kind.Animated -> if (kind.size == 0) "local" else "anim:${kind.size}"
        }
        prefs(graph).edit().putString(id, text).apply()
    }

    /** What kind of cover this album has, or null when it couldn't be checked (try again later). */
    suspend fun kind(graph: AppGraph, albumCoverId: String): Kind? {
        loadSaved(graph)
        val key = CoverStore.canonical(albumCoverId)
        known[key]?.let { return it }
        if (CoverStore.has(key)) return Kind.Local.also { remember(graph, key, it) }
        val lock = locks.getOrPut(key) { Mutex() }
        return lock.withLock {
            known[key]?.let { return@withLock it }
            decide(graph, albumCoverId)?.also { remember(graph, key, it) }
        }
    }

    /** Like [knownKind], reading what earlier app starts found first. */
    fun knownKind(graph: AppGraph, albumCoverId: String): Kind? {
        loadSaved(graph)
        return knownKind(albumCoverId)
    }

    /**
     * Gets a song's big cover ready before it's shown (the songs either side in now playing), so
     * swiping to it shows the cover, or its animation, straight away.
     */
    suspend fun preload(context: Context, graph: AppGraph, song: app.musix.data.Song) {
        val cover = song.coverArt ?: song.albumId?.let { "al-$it" } ?: return
        val album = song.albumId?.let { if (it.startsWith("al-")) it else "al-$it" }
        val kind = if (album == null) Kind.Still else kind(graph, album) ?: return
        val loader = SingletonImageLoader.get(context.applicationContext)
        // The small one is shown first while anything else loads.
        loader.enqueue(coverRequest(context.applicationContext, graph, cover, SMALL_COVER))
        when (kind) {
            Kind.Still -> loader.enqueue(coverRequest(context.applicationContext, graph, cover, LARGE_COVER))
            Kind.Local, is Kind.Animated ->
                if (graph.settings.current.animatedCovers && album != null) load(context, graph, album, kind, ANIMATION_PX)
                else if (kind == Kind.Local) loader.enqueue(coverRequest(context.applicationContext, graph, cover, LARGE_COVER))
            Kind.StillOnlySmall -> Unit
        }
    }

    /** Synchronous peek at what's already known, for places that can't wait. */
    fun knownKind(albumCoverId: String): Kind? {
        val key = CoverStore.canonical(albumCoverId)
        return known[key] ?: if (CoverStore.has(key)) Kind.Local else null
    }

    /** Forgets everything (the image cache was cleared). */
    fun forget(graph: AppGraph) {
        known.clear()
        prefs(graph).edit().clear().apply()
    }

    private suspend fun decide(graph: AppGraph, id: String): Kind? {
        val original = peek(graph, id, null) ?: return null
        if (!original.animated) return Kind.Still
        if (original.kind == "animated WebP" || original.bytes in 1..MAX_ORIGINAL_BYTES) {
            AppLog.i(
                "Cover",
                "Animated cover for $id: ${original.kind} ${original.size}, ${mb(original.bytes)}. " +
                    if (original.kind == "animated WebP") "Navidrome sends it full size for every request, so it's kept once on the phone"
                    else "Small enough to keep the original",
            )
            return Kind.Local
        }
        val small = peek(graph, id, SMALL_COVER)
        return when {
            small != null && small.animated && small.shrunk -> {
                AppLog.i(
                    "Cover",
                    "Animated cover for $id: original is ${mb(original.bytes)} (${original.kind}), too big for streaming; " +
                        "using Navidrome's $SMALL_COVER px version (${small.kind}, ${mb(small.bytes)}, ready in ${small.ms} ms)",
                )
                Kind.Animated(SMALL_COVER)
            }
            small != null && small.animated -> {
                AppLog.i("Cover", "Animated cover for $id: Navidrome doesn't shrink it (${original.kind}, ${mb(original.bytes)}); kept once on the phone")
                Kind.Local
            }
            else -> {
                AppLog.w(
                    "Cover",
                    "Animated cover for $id is ${mb(original.bytes)} (${original.kind}) and Navidrome couldn't send a small animated " +
                        "version (${small?.kind ?: "no answer"}). Showing it still, at $SMALL_COVER px.",
                )
                Kind.StillOnlySmall
            }
        }
    }

    private class Peek(val animated: Boolean, val kind: String, val bytes: Long, val ms: Long, val size: String = "", val shrunk: Boolean = false)

    /** Reads only the first bytes of the cover (then drops the connection) to see what kind of image it is. */
    private suspend fun peek(graph: AppGraph, id: String, size: Int?): Peek? = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val client = graph.imageHttp.newBuilder().callTimeout(40, TimeUnit.SECONDS).build()
        val call = client.newCall(Request.Builder().url(graph.subsonic.coverUrl(id, size)).header(CoverStore.PEEK, "1").build())
        runCatching {
            call.execute().use { response ->
                val ms = System.currentTimeMillis() - started
                if (!response.isSuccessful) return@use Peek(false, "HTTP ${response.code}", 0, ms)
                val length = response.header("Content-Length")?.toLongOrNull() ?: -1
                val head = ByteArray(32)
                var read = 0
                val stream = response.body?.byteStream() ?: return@use Peek(false, "empty", 0, ms)
                while (read < head.size) {
                    val n = stream.read(head, read, head.size - read)
                    if (n < 0) break
                    read += n
                }
                call.cancel() // don't let OkHttp download the rest
                val image = CoverStore.sniff(head.copyOf(read))
                if (image == null) {
                    val kind = if (read > 0 && head[0] == '{'.code.toByte()) "an error message" else "unknown"
                    return@use Peek(false, kind, length, ms)
                }
                val shrunk = size != null && image.width in 1..(size * 5 / 4) && image.height in 1..(size * 5 / 4)
                Peek(image.animated, image.kind, length, ms, "${image.width}×${image.height}", shrunk)
            }
        }.onFailure { AppLog.w("Cover", "Couldn't check cover $id (size ${size ?: "original"}): ${it.message}") }.getOrNull()
    }

    /**
     * Loads the animation (once per session) at about [px] pixels: decoding at display size rather than
     * the file's own keeps memory down. The download runs in the app's scope, so leaving the page doesn't
     * throw away a half-finished one. Only the album page and now playing call this.
     */
    suspend fun load(context: Context, graph: AppGraph, coverId: String, kind: Kind, px: Int): Drawable? {
        val key = "${CoverStore.canonical(coverId)}:$kind:$px"
        val job = synchronized(loads) {
            loads.getOrPut(key) { graph.scope.async { loadNow(context.applicationContext, graph, coverId, kind, px) } }
        }
        val d = job.await()
        if (d == null) synchronized(loads) { loads.remove(key) }
        return d
    }

    private suspend fun loadNow(context: Context, graph: AppGraph, coverId: String, kind: Kind, px: Int): Drawable? {
        val builder = ImageRequest.Builder(context)
            .decoderFactory(animatedDecoder())
            .size(px)
            .memoryCacheKey("anim:${CoverStore.canonical(coverId)}:$px")
        when (kind) {
            Kind.Local -> {
                val file = CoverStore.local(coverId)?.original ?: run {
                    // Not kept yet: one request for the original saves it (the interceptor does the work).
                    withContext(Dispatchers.IO) {
                        runCatching {
                            graph.imageHttp.newCall(
                                Request.Builder().url(graph.subsonic.coverUrl(coverId, null)).header(CoverStore.WANT_ANIMATION, "1").build()
                            ).execute().close()
                        }.onFailure { AppLog.w("Cover", "Couldn't download the animated cover $coverId: ${it.message}") }
                    }
                    CoverStore.local(coverId)?.original
                } ?: return null
                builder.data(file).diskCachePolicy(CachePolicy.DISABLED)
            }
            is Kind.Animated -> {
                val key = "cover:$coverId:${kind.size}"
                builder.data(graph.subsonic.coverUrl(coverId, kind.size)).diskCacheKey(key)
                    .httpHeaders(NetworkHeaders.Builder().set(CoverStore.WANT_ANIMATION, "1").build())
            }
            else -> return null
        }
        val result = SingletonImageLoader.get(context).execute(builder.build())
        if (result !is SuccessResult) {
            AppLog.w("Cover", "Couldn't load the animated cover $coverId")
            return null
        }
        val d = result.image.asDrawable(context.resources)
        AppLog.i(
            "Cover",
            "$coverId shown as ${d.javaClass.simpleName} ${d.intrinsicWidth}×${d.intrinsicHeight}, " +
                if (d is Animatable) "animated" else "a still image",
        )
        return d
    }

    private fun animatedDecoder(): Decoder.Factory =
        if (Build.VERSION.SDK_INT >= 28) AnimatedImageDecoder.Factory() else GifDecoder.Factory()

    private fun mb(bytes: Long) = if (bytes <= 0) "size unknown" else "%.1f MB".format(bytes / 1_048_576.0)
}

/** Pixels the animation is decoded at: enough for a phone's now playing, far less memory than 1000+ px frames. */
internal const val ANIMATION_PX = 720

/**
 * An animated cover, drawn by Android's own ImageView with its animation started explicitly. Shows
 * [still] until the animation has loaded.
 */
@Composable
fun AnimatedCover(coverId: String, kind: AnimatedCovers.Kind, modifier: Modifier, shape: Shape, still: @Composable () -> Unit) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    var drawable by remember(coverId, kind) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(coverId, kind) { drawable = AnimatedCovers.load(context, graph, coverId, kind, ANIMATION_PX) }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        val d = drawable
        if (d == null) {
            still()
        } else {
            AndroidView(
                factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                update = { view ->
                    // The same animation can be shown on the album page and in now playing; it only
                    // moves in the view that was last given it, so take it back when recomposed.
                    if (view.drawable !== d || d.callback !== view) {
                        view.setImageDrawable(null)
                        view.setImageDrawable(d)
                    }
                    (d as? Animatable)?.start()
                },
                modifier = Modifier.fillMaxSize(),
            )
            DisposableEffect(d) { onDispose { (d as? Animatable)?.stop() } }
        }
    }
}

/**
 * A big cover (now playing, album page) that's safe for animated albums: the 300 px cover right away,
 * then the large still once the album is known not to be animated, or the animation when it is.
 */
@Composable
fun BigCover(coverId: String?, albumId: String?, modifier: Modifier, shape: Shape) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val albumCover = albumId?.let { if (it.startsWith("al-")) it else "al-$it" }
    val initial: AnimatedCovers.Kind? = if (albumCover == null) AnimatedCovers.Kind.Still else AnimatedCovers.knownKind(graph, albumCover)
    val kind by produceState(initial, albumCover) {
        if (albumCover != null) AnimatedCovers.kind(graph, albumCover)?.let { value = it }
    }
    val small = @Composable { Cover(coverId, SMALL_COVER, Modifier.fillMaxSize(), shape) }
    when (val k = kind) {
        is AnimatedCovers.Kind.Animated, AnimatedCovers.Kind.Local ->
            if (settings.animatedCovers && albumCover != null) AnimatedCover(albumCover, k, modifier, shape, small)
            // Kept on the phone: its still is already there at full size.
            else if (k == AnimatedCovers.Kind.Local) Cover(coverId, LARGE_COVER, modifier, shape)
            else Box(modifier) { small() }
        AnimatedCovers.Kind.Still -> Cover(coverId, LARGE_COVER, modifier, shape)
        // Not checked yet, can't be checked, or animated without a usable version: the small one is always safe.
        else -> Box(modifier) { small() }
    }
}
