package app.siansa.ui

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
import app.siansa.AppGraph
import app.siansa.log.AppLog
import android.os.Build
import app.siansa.covers.CoverStore
import app.siansa.covers.executeCancellable
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

/** Size Siansa asks Navidrome for in lists, tiles and backgrounds. One size means one conversion per album. */
const val SMALL_COVER = 300

/**
 * Album covers that may be animated (a cover.gif or animated cover.webp in the album folder).
 *
 * Tested against Navidrome 0.64:
 * - An animated GIF is converted with ffmpeg for every requested size. 300 px works (~5 s); 600 and
 *   1000 px failed and returned no image, and failed conversions are retried on every request.
 * - An animated WebP isn't resized at all: every size returns the original file.
 *
 * So, per album, Siansa reads only the first bytes of the original to see what it is (no conversion), and:
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
    private const val RECHECKED = "rechecked-736"

    private fun prefs(graph: AppGraph) = graph.app.getSharedPreferences("animated_covers", Context.MODE_PRIVATE)

    private fun loadSaved(graph: AppGraph) {
        if (loaded) return
        loaded = true
        val saved = prefs(graph)
        // Stills saved before issue #736 may only have been a slow first answer from the server: ask once more.
        if (!saved.getBoolean(RECHECKED, false)) {
            saved.edit().apply { saved.all.filterValues { it == "small" }.keys.forEach { remove(it) } }.putBoolean(RECHECKED, true).apply()
        }
        saved.all.forEach { (id, value) ->
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

    /** Keeps [kind] for this cover; [lasting] also saves it for later starts. */
    private fun remember(graph: AppGraph, id: String, kind: Kind, lasting: Boolean = true) {
        known[id] = kind
        if (!lasting) return
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
            decide(graph, albumCoverId)?.let { (kind, lasting) -> kind.also { remember(graph, key, it, lasting) } }
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
    suspend fun preload(context: Context, graph: AppGraph, song: app.siansa.data.Song) {
        val cover = song.coverArt ?: song.albumId?.let { "al-$it" } ?: return
        // The same cover now playing will pick: the song's own picture, or its album's animation when the
        // song's is just that picture again (issue #358).
        val own = bigCoverKey(song.coverArt, song.albumId)
        val ownKind = if (own == null) Kind.Still else kind(graph, own) ?: return
        val albumId = albumCoverKey(song.albumId)
        val (album, kind) = if (own == null) null to ownKind else {
            val albumKind = albumId?.takeIf { it != own && ownKind == Kind.Still }?.let { kind(graph, it) }
            val same = if (albumId != null && (albumKind is Kind.Animated || albumKind == Kind.Local)) CoverMatch.same(context, graph, own, albumId) else null
            pickBigCover(own, ownKind, albumId, albumKind, same)
        }
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

    /** The cover's kind, and whether that holds for later starts too (see [bigAnimationChoice]). */
    private suspend fun decide(graph: AppGraph, id: String): Pair<Kind, Boolean>? {
        val original = peek(graph, id, null) ?: return null
        if (!original.animated) return Kind.Still to true
        if (original.kind == "animated WebP" || original.bytes in 1..MAX_ORIGINAL_BYTES) {
            AppLog.i(
                "Cover",
                "Animated cover for $id: ${original.kind} ${original.size}, ${mb(original.bytes)}. " +
                    if (original.kind == "animated WebP") "Navidrome sends it full size for every request, so it's kept once on the phone"
                    else "Small enough to keep the original",
            )
            return Kind.Local to true
        }
        val small = peek(graph, id, SMALL_COVER)
        // No answer in time, or an error page (a proxy's 504 while Navidrome is still making it): not a real answer.
        val answered = small != null && !small.kind.startsWith("HTTP ")
        val choice = bigAnimationChoice(answered, animated = small?.animated == true, shrunk = small?.shrunk == true)
        when {
            small != null && small.animated && small.shrunk -> {
                AppLog.i(
                    "Cover",
                    "Animated cover for $id: original is ${mb(original.bytes)} (${original.kind}), too big for streaming; " +
                        "using Navidrome's $SMALL_COVER px version (${small.kind}, ${mb(small.bytes)}, ready in ${small.ms} ms)",
                )
            }
            small != null && small.animated ->
                AppLog.i("Cover", "Animated cover for $id: Navidrome doesn't shrink it (${original.kind}, ${mb(original.bytes)}); kept once on the phone")
            else ->
                AppLog.w(
                    "Cover",
                    "Animated cover for $id is ${mb(original.bytes)} (${original.kind}) and Navidrome couldn't send a small animated " +
                        "version (${small?.kind ?: "no answer"}). Showing it still, at $SMALL_COVER px" +
                        if (choice.second) "." else " until Siansa starts again, then it asks again.",
                )
        }
        return choice
    }

    /**
     * What a big animated GIF becomes, from Navidrome's answer for its small version (issue #736), and whether that holds
     * for later starts: its small animation when Navidrome shrinks it, the original kept on the phone when it doesn't, a
     * still when the small one doesn't move. When Navidrome didn't answer in time (it makes the small version slowly the
     * first time), the still is only for this start: the next one asks again, by when Navidrome has it ready.
     */
    internal fun bigAnimationChoice(answered: Boolean, animated: Boolean, shrunk: Boolean): Pair<Kind, Boolean> = when {
        answered && animated && shrunk -> Kind.Animated(SMALL_COVER) to true
        answered && animated -> Kind.Local to true
        answered -> Kind.StillOnlySmall to true
        else -> Kind.StillOnlySmall to false
    }

    private class Peek(val animated: Boolean, val kind: String, val bytes: Long, val ms: Long, val size: String = "", val shrunk: Boolean = false)

    /** Reads only the first bytes of the cover (then drops the connection) to see what kind of image it is. */
    private suspend fun peek(graph: AppGraph, id: String, size: Int?): Peek? = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val client = graph.imageHttp.newBuilder().callTimeout(40, TimeUnit.SECONDS).build()
        val call = client.newCall(Request.Builder().url(graph.source.coverUrl(id, size)).header(CoverStore.PEEK, "1").build())
        runCatching {
            call.executeCancellable { response ->
                val ms = System.currentTimeMillis() - started
                if (!response.isSuccessful) return@executeCancellable Peek(false, "HTTP ${response.code}", 0, ms)
                val length = response.header("Content-Length")?.toLongOrNull() ?: -1
                val head = ByteArray(32)
                var read = 0
                val stream = response.body?.byteStream() ?: return@executeCancellable Peek(false, "empty", 0, ms)
                while (read < head.size) {
                    val n = stream.read(head, read, head.size - read)
                    if (n < 0) break
                    read += n
                }
                call.cancel() // don't let OkHttp download the rest
                val image = CoverStore.sniff(head.copyOf(read))
                if (image == null) {
                    val kind = if (read > 0 && head[0] == '{'.code.toByte()) "an error message" else "unknown"
                    return@executeCancellable Peek(false, kind, length, ms)
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
                                Request.Builder().url(graph.source.coverUrl(coverId, null)).header(CoverStore.WANT_ANIMATION, "1").build()
                            ).execute().close()
                        }.onFailure { AppLog.w("Cover", "Couldn't download the animated cover $coverId: ${it.message}") }
                    }
                    CoverStore.local(coverId)?.original
                } ?: return null
                builder.data(file).diskCachePolicy(CachePolicy.DISABLED)
            }
            is Kind.Animated -> {
                val key = "cover:${app.siansa.covers.CoverVersions.cacheId(coverId)}:${kind.size}"
                builder.data(graph.source.coverUrl(coverId, kind.size)).diskCacheKey(key)
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
 * The cover id the big cover in now playing checks for animation and animates (issue #358). Navidrome gives a
 * song its own id when it has a picture of its own (`mf-…`, only while the server's EnableMediaFileCoverArt is
 * on) or its disc's (`dc-…`, on albums with several discs); those are checked first, so a compilation's or a
 * disc's own cover can show instead of the album's. Anything else (`al-…`, or no id) is the album's cover.
 */
internal fun bigCoverKey(coverId: String?, albumId: String?): String? {
    val own = coverId?.let { CoverStore.canonical(it) }
    if (own != null && (own.startsWith("mf-") || own.startsWith("dc-"))) return coverId
    return albumCoverKey(albumId) ?: coverId
}

/** The album's cover id (`al-…`) for an album id, or null without one. */
internal fun albumCoverKey(albumId: String?): String? = albumId?.let { if (it.startsWith("al-")) it else "al-$it" }

/**
 * Which cover the big cover animates once both are known (issue #358): the song's own, unless it's a still
 * that is the same picture as an animated album cover, in which case the album's animation plays.
 * [same] is null while the two haven't been compared: the song's own is shown meanwhile.
 */
internal fun pickBigCover(
    own: String,
    ownKind: AnimatedCovers.Kind,
    album: String?,
    albumKind: AnimatedCovers.Kind?,
    same: Boolean?,
): Pair<String, AnimatedCovers.Kind> {
    val albumAnimated = albumKind is AnimatedCovers.Kind.Animated || albumKind == AnimatedCovers.Kind.Local
    return if (album != null && album != own && ownKind == AnimatedCovers.Kind.Still && albumAnimated && same == true) {
        album to albumKind!!
    } else {
        own to ownKind
    }
}

/**
 * A big cover (now playing, album page) that's safe for animated albums: the 300 px cover right away,
 * then the large still once the cover is known not to be animated, or the animation when it is. A song
 * with a picture of its own shows it, unless it's just the album's animated cover again (issue #358).
 */
// Lint doesn't follow the assignment from the nullable result; it's on the producer's last line.
@android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
fun BigCover(coverId: String?, albumId: String?, modifier: Modifier, shape: Shape) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val own = bigCoverKey(coverId, albumId)
    val album = albumCoverKey(albumId)
    // Straight away from what's known, so a cover seen before doesn't flicker between the two.
    val initial: Pair<String, AnimatedCovers.Kind>? = when {
        own == null -> null
        else -> AnimatedCovers.knownKind(graph, own)?.let { ownKind ->
            val albumKind = album?.takeIf { it != own }?.let { AnimatedCovers.knownKind(graph, it) }
            val same = album?.let { CoverMatch.knownSame(graph, own, it) }
            pickBigCover(own, ownKind, album, albumKind, same)
        }
    }
    val picked by produceState(initial, own, album) {
        if (own == null) return@produceState
        val ownKind = AnimatedCovers.kind(graph, own) ?: return@produceState
        val albumKind = album?.takeIf { it != own && ownKind == AnimatedCovers.Kind.Still }?.let { AnimatedCovers.kind(graph, it) }
        val albumAnimated = albumKind is AnimatedCovers.Kind.Animated || albumKind == AnimatedCovers.Kind.Local
        val same = if (album != null && albumAnimated) CoverMatch.same(context, graph, own, album) else null
        this.value = pickBigCover(own, ownKind, album, albumKind, same)
    }
    val small = @Composable { Cover(coverId, SMALL_COVER, Modifier.fillMaxSize(), shape) }
    val key = picked?.first
    when (val k = if (own == null) AnimatedCovers.Kind.Still else picked?.second) {
        is AnimatedCovers.Kind.Animated, AnimatedCovers.Kind.Local ->
            if (settings.animatedCovers && key != null) AnimatedCover(key, k, modifier, shape, small)
            // Kept on the phone: its still is already there at full size.
            else if (k == AnimatedCovers.Kind.Local) Cover(coverId, LARGE_COVER, modifier, shape)
            else Box(modifier) { small() }
        AnimatedCovers.Kind.Still -> Cover(coverId, LARGE_COVER, modifier, shape)
        // Not checked yet, can't be checked, or animated without a usable version: the small one is always safe.
        else -> Box(modifier) { small() }
    }
}
