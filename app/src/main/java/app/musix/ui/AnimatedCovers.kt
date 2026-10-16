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
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import kotlinx.coroutines.Deferred
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
 * Tested against Navidrome 0.64: for an animated GIF it runs ffmpeg for every requested size. A 1000×1000
 * GIF took ~5 s of CPU at 300 px and failed (error, no image) at 600 and 1000 px, and failed conversions
 * are retried on every request. The original file is sent untouched but can be tens of MB, which on a
 * phone's connection crowds out the song stream. So, per album, Musix:
 * - reads only the first bytes of the original to see if it's animated (cheap, no conversion);
 * - animated and small (≤ 3 MB): shows the original;
 * - animated and big: asks once for the 300 px version, which Navidrome can convert (an animated WebP);
 * - never asks for big sizes of an animated cover, and remembers the outcome across app starts.
 */
object AnimatedCovers {
    private const val MAX_ORIGINAL_BYTES = 3L * 1024 * 1024

    sealed interface Kind {
        /** A normal picture: fine to load at any size. */
        data object Still : Kind
        /** Animated: load at [size] (0 = the original file). */
        data class Animated(val size: Int) : Kind
        /** Animated, but only the 300 px still is safe to load. */
        data object StillOnlySmall : Kind
    }

    private val known = ConcurrentHashMap<String, Kind>()
    private val loads = ConcurrentHashMap<String, Deferred<ImageResult>>()
    private val checking = Mutex()
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
            is Kind.Animated -> "anim:${kind.size}"
        }
        prefs(graph).edit().putString(id, text).apply()
    }

    /** What kind of cover this album has, or null when it couldn't be checked (try again later). */
    suspend fun kind(graph: AppGraph, albumCoverId: String): Kind? {
        loadSaved(graph)
        known[albumCoverId]?.let { return it }
        return checking.withLock {
            known[albumCoverId]?.let { return@withLock it }
            decide(graph, albumCoverId)?.also { remember(graph, albumCoverId, it) }
        }
    }

    /** Synchronous peek at what's already known, for places that can't wait. */
    fun knownKind(albumCoverId: String): Kind? = known[albumCoverId]

    private suspend fun decide(graph: AppGraph, id: String): Kind? {
        val original = peek(graph, id, null) ?: return null
        if (!original.animated) return Kind.Still
        if (original.bytes in 1..MAX_ORIGINAL_BYTES) {
            AppLog.i("Cover", "Animated cover for $id: the original (${original.kind}, ${mb(original.bytes)})")
            return Kind.Animated(0)
        }
        val small = peek(graph, id, SMALL_COVER)
        return if (small != null && small.animated) {
            AppLog.i(
                "Cover",
                "Animated cover for $id: original is ${mb(original.bytes)} (${original.kind}), too big for streaming; " +
                    "using Navidrome's $SMALL_COVER px version (${small.kind}, ${mb(small.bytes)}, ready in ${small.ms} ms)",
            )
            Kind.Animated(SMALL_COVER)
        } else {
            AppLog.w(
                "Cover",
                "Animated cover for $id is ${mb(original.bytes)} (${original.kind}) and Navidrome couldn't send a small animated " +
                    "version (${small?.kind ?: "no answer"}). Showing it still, at $SMALL_COVER px.",
            )
            Kind.StillOnlySmall
        }
    }

    private class Peek(val animated: Boolean, val kind: String, val bytes: Long, val ms: Long)

    /** Reads only the first bytes of the cover (then drops the connection) to see what kind of image it is. */
    private suspend fun peek(graph: AppGraph, id: String, size: Int?): Peek? = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val client = graph.imageHttp.newBuilder().callTimeout(40, TimeUnit.SECONDS).build()
        val call = client.newCall(Request.Builder().url(graph.subsonic.coverUrl(id, size)).build())
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
                val kind = kindOf(head, read)
                Peek(kind == "animated GIF" || kind == "animated WebP", kind, length, ms)
            }
        }.onFailure { AppLog.w("Cover", "Couldn't check cover $id (size ${size ?: "original"}): ${it.message}") }.getOrNull()
    }

    private fun kindOf(b: ByteArray, length: Int): String {
        fun text(from: Int, value: String) = length >= from + value.length &&
            value.indices.all { b[from + it] == value[it].code.toByte() }
        return when {
            text(0, "GIF8") -> "animated GIF"
            text(0, "RIFF") && text(8, "WEBP") && text(12, "VP8X") && length > 20 && (b[20].toInt() and 0x02) != 0 -> "animated WebP"
            text(0, "RIFF") && text(8, "WEBP") -> "still WebP"
            length > 2 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "JPEG"
            text(1, "PNG") -> "PNG"
            text(0, "{") -> "an error message"
            else -> "unknown"
        }
    }

    /**
     * Loads (once per session, through the image cache) and returns the cover as a drawable. The download
     * runs in the image loader's own scope, so leaving the page doesn't throw away a half-finished download.
     */
    suspend fun load(context: Context, graph: AppGraph, coverId: String, size: Int): Drawable? {
        val key = "cover:$coverId:${if (size == 0) "full" else size}"
        val job = loads.getOrPut(key) {
            val request = ImageRequest.Builder(context.applicationContext)
                .data(graph.subsonic.coverUrl(coverId, size.takeIf { it > 0 }))
                .diskCacheKey(key)
                .memoryCacheKey("$key:animated")
                .build()
            SingletonImageLoader.get(context.applicationContext).enqueue(request).job
        }
        val result = job.await()
        if (result !is SuccessResult) {
            loads.remove(key)
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

    private fun mb(bytes: Long) = if (bytes <= 0) "size unknown" else "%.1f MB".format(bytes / 1_048_576.0)
}

/**
 * An animated cover, drawn by Android's own ImageView with its animation started explicitly. Shows
 * [still] until the animation has loaded.
 */
@Composable
fun AnimatedCover(coverId: String, size: Int, modifier: Modifier, shape: Shape, still: @Composable () -> Unit) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    var drawable by remember(coverId, size) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(coverId, size) { drawable = AnimatedCovers.load(context, graph, coverId, size) }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        val d = drawable
        if (d == null) {
            still()
        } else {
            AndroidView(
                factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                update = { view ->
                    if (view.drawable !== d) view.setImageDrawable(d)
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
    val initial: AnimatedCovers.Kind? = if (albumCover == null) AnimatedCovers.Kind.Still else AnimatedCovers.knownKind(albumCover)
    val kind by produceState(initial, albumCover) {
        if (albumCover != null) AnimatedCovers.kind(graph, albumCover)?.let { value = it }
    }
    val small = @Composable { Cover(coverId, SMALL_COVER, Modifier.fillMaxSize(), shape) }
    when (val k = kind) {
        is AnimatedCovers.Kind.Animated ->
            if (settings.animatedCovers && albumCover != null) AnimatedCover(albumCover, k.size, modifier, shape, small)
            else Box(modifier) { small() }
        AnimatedCovers.Kind.Still -> Cover(coverId, LARGE_COVER, modifier, shape)
        // Not checked yet, can't be checked, or animated without a usable version: the small one is always safe.
        else -> Box(modifier) { small() }
    }
}
