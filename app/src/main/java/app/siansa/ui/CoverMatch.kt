package app.siansa.ui

import android.content.Context
import android.graphics.Bitmap
import app.siansa.AppGraph
import app.siansa.covers.CoverStore
import app.siansa.log.AppLog
import coil3.SingletonImageLoader
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Whether a song's own cover is the same picture as its album's (issue #358).
 *
 * Navidrome gives a song its own cover id when its file has a picture in it, or its disc's on albums with
 * several discs, while the album prefers a cover file in its folder. Very often the song's picture is just
 * the album cover again, and when the album's cover is animated, now playing should keep the animation. When
 * the song's picture is a different one (a compilation, a disc with its own artwork), now playing shows it.
 *
 * Both are compared at the size lists already load (so nothing new is downloaded), shrunk to 9×8 grey dots:
 * each of the 64 bits says whether a dot is brighter than the one to its right. Pictures that differ in
 * fewer than [SAME_BITS] bits are the same artwork, even after resizing or recompressing. The answer is
 * remembered across app starts.
 */
object CoverMatch {
    /** At most this many of the 64 bits may differ for two covers to count as the same picture. */
    internal const val SAME_BITS = 10

    private const val W = 9
    private const val H = 8

    private val known = ConcurrentHashMap<String, Boolean>()
    @Volatile private var loaded = false

    /** A difference hash of a 9×8 grid of grey values (0–255), row by row. */
    internal fun differenceHash(gray: IntArray): Long {
        require(gray.size == W * H) { "expected ${W * H} values" }
        var hash = 0L
        var bit = 0
        for (y in 0 until H) {
            for (x in 0 until W - 1) {
                if (gray[y * W + x] > gray[y * W + x + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    /** Grey value (0–255) of each ARGB pixel, weighted as the eye sees it. */
    internal fun grey(argb: IntArray): IntArray = IntArray(argb.size) { i ->
        val c = argb[i]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        (r * 299 + g * 587 + b * 114) / 1000
    }

    /** Whether two hashes are the same picture. */
    internal fun samePicture(a: Long, b: Long): Boolean = java.lang.Long.bitCount(a xor b) < SAME_BITS

    private fun key(a: String, b: String) = CoverStore.canonical(a) + "|" + CoverStore.canonical(b)

    private fun prefs(graph: AppGraph) = graph.app.getSharedPreferences("cover_match", Context.MODE_PRIVATE)

    private fun loadSaved(graph: AppGraph) {
        if (loaded) return
        loaded = true
        prefs(graph).all.forEach { (k, v) -> (v as? Boolean)?.let { known[k] = it } }
    }

    /** What's already known about [song] and [album], or null when they haven't been compared yet. */
    fun knownSame(graph: AppGraph, song: String, album: String): Boolean? {
        loadSaved(graph)
        return known[key(song, album)]
    }

    /** Whether [song]'s cover is [album]'s picture; null when either couldn't be loaded (try again later). */
    suspend fun same(context: Context, graph: AppGraph, song: String, album: String): Boolean? {
        knownSame(graph, song, album)?.let { return it }
        val a = hash(context, graph, song) ?: return null
        val b = hash(context, graph, album) ?: return null
        val same = samePicture(a, b)
        val k = key(song, album)
        known[k] = same
        prefs(graph).edit().putBoolean(k, same).apply()
        AppLog.i(
            "Cover",
            "Song cover ${CoverStore.canonical(song)} is ${if (same) "the album's picture" else "a picture of its own"} " +
                "(${java.lang.Long.bitCount(a xor b)} of 64 bits differ from ${CoverStore.canonical(album)})",
        )
        return same
    }

    private suspend fun hash(context: Context, graph: AppGraph, id: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            // The request lists use (cached, and safe for animated albums), decoded as a plain bitmap.
            val request = coverRequest(context, graph, id, SMALL_COVER).newBuilder()
                .memoryCacheKey("cover:${app.siansa.covers.CoverVersions.cacheId(id)}:match")
                .allowHardware(false)
                .build()
            val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@runCatching null
            val small = Bitmap.createScaledBitmap(result.image.toBitmap(), W, H, true)
            val pixels = IntArray(W * H)
            small.getPixels(pixels, 0, W, 0, 0, W, H)
            differenceHash(grey(pixels))
        }.onFailure { AppLog.w("Cover", "Couldn't compare cover $id: ${it.message}") }.getOrNull()
    }
}
