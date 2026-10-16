package app.musix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.musix.AppGraph
import app.musix.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Animated album covers (a cover.gif or animated cover.webp in the album folder, as Symfonium shows).
 *
 * Navidrome gives a song its own embedded picture, which is always still, and only uses the folder's
 * animated cover for the album. Older Navidrome versions also flatten animations when a smaller size is
 * asked for. So for the big covers (now playing, album page) Musix checks whether the album's original
 * cover is animated by reading its first few bytes, and if so shows the album cover at full size.
 */
object AnimatedCovers {
    private val known = ConcurrentHashMap<String, Boolean>()

    suspend fun isAnimated(graph: AppGraph, albumCoverId: String): Boolean {
        known[albumCoverId]?.let { return it }
        val animated = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(graph.subsonic.coverUrl(albumCoverId, null)).build()
                graph.http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use false
                    val head = ByteArray(32)
                    val source = response.body?.byteStream() ?: return@use false
                    var read = 0
                    while (read < head.size) {
                        val n = source.read(head, read, head.size - read)
                        if (n < 0) break
                        read += n
                    }
                    isAnimatedHeader(head, read)
                }
            }.onFailure { AppLog.w("Cover", "Couldn't check the cover of $albumCoverId: ${it.message}") }.getOrNull()
        } ?: return false // not remembered, so it's checked again next time
        known[albumCoverId] = animated
        if (animated) AppLog.i("Cover", "Animated cover for $albumCoverId")
        return animated
    }

    /** GIF, or WebP with the animation flag set in its VP8X header. */
    private fun isAnimatedHeader(b: ByteArray, length: Int): Boolean {
        fun text(from: Int, value: String) = length >= from + value.length &&
            value.indices.all { b[from + it] == value[it].code.toByte() }
        if (text(0, "GIF8")) return true
        if (text(0, "RIFF") && text(8, "WEBP") && text(12, "VP8X") && length > 20) {
            return (b[20].toInt() and 0x02) != 0
        }
        return false
    }
}

/**
 * The cover id and size to use for a big cover: the album's animated cover at full size when it has
 * one (and animated covers are on), otherwise [fallbackId] at [LARGE_COVER].
 */
@Composable
fun rememberBigCover(fallbackId: String?, albumId: String?): Pair<String?, Int?> {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val albumCover = albumId?.let { if (it.startsWith("al-")) it else "al-$it" }
    val result by produceState<Pair<String?, Int?>>(fallbackId to LARGE_COVER, fallbackId, albumCover, settings.animatedCovers) {
        value = fallbackId to LARGE_COVER
        if (settings.animatedCovers && albumCover != null && AnimatedCovers.isAnimated(graph, albumCover)) {
            value = albumCover to null
        }
    }
    return result
}
