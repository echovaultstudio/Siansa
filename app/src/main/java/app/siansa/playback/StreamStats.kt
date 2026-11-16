package app.siansa.playback

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import app.siansa.log.AppLog
import okhttp3.Interceptor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * What the song streams actually get from the network, for the log and for the slow-connection
 * fallback: how big each stream is and what bitrate it needs, and how fast data is really arriving.
 */
@OptIn(UnstableApi::class)
object StreamStats {
    /** Kbit/s each song needs to play without stopping (file size ÷ length), by song id. */
    private val needed = ConcurrentHashMap<String, Int>()

    private val bytes = AtomicLong()
    private var windowStart = SystemClock.elapsedRealtime()
    private var windowBytes = 0L
    @Volatile private var lastKbps = -1

    /** Arrival speed over the last few seconds of streaming, or -1 if nothing streamed lately. */
    fun recentKbps(): Int {
        sample()
        return lastKbps
    }

    fun neededKbps(songId: String?): Int? = songId?.let { needed[it] }

    /** Bytes received from the network by song streams since the app started. */
    fun totalBytes(): Long = bytes.get()

    private val formats = ConcurrentHashMap<String, String>()

    /** The format last streamed for this song: "original", or e.g. "opus 160". */
    fun formatOf(songId: String?): String? = songId?.let { formats[it] }

    /** Counts bytes as they arrive from Navidrome (not the song cache). */
    val transferListener = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
            if (isNetwork) bytes.addAndGet(bytesTransferred.toLong())
            sample()
        }
        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    }

    @Synchronized
    private fun sample() {
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - windowStart
        if (elapsed < 5000) return
        val total = bytes.get()
        val got = total - windowBytes
        lastKbps = if (got > 0) (got * 8 / elapsed).toInt() else if (elapsed > 15_000) -1 else lastKbps
        if (got > 0 && AppLog.detailOn) {
            AppLog.d("Net", "Streamed ${got / 1024} KB in ${elapsed / 1000}s = $lastKbps kbit/s")
        }
        windowStart = now
        windowBytes = total
    }

    /** Notes each song stream's size, so the log can say what bitrate it needs. */
    val interceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url.toString()
        // A song stream, from Navidrome (`/rest/stream`), Emby or Jellyfin (`/Audio/<id>/stream`, issue #1204).
        val id = if (request.url.encodedPath.endsWith("/rest/stream") || request.url.encodedPath.contains("/Audio/", ignoreCase = true)) streamSongId(url) else null
        if (id != null) {
            // A ranged answer ("bytes 100-200/34000000") carries the full size after the slash.
            val total = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                ?: response.header("Content-Length")?.toLongOrNull()
            val duration = SongRegistry.get(id)?.durationSec ?: 0
            formats[id] = described(url)
            if (total != null && total > 0 && duration > 0) {
                val kbps = (total * 8 / duration / 1000).toInt()
                if (needed.put(id, kbps) == null) {
                    val format = described(url)
                    AppLog.i("Stream", "Song $id ($format): ${"%.1f".format(total / 1_048_576.0)} MB for ${duration}s, needs about $kbps kbit/s")
                }
            }
        }
        response
    }

    /** "original", or the format and bitrate a stream is converted to ("opus 256"), read from its address. */
    fun described(url: String): String {
        val parts = songCacheKey(url).split(':')
        val format = parts.getOrNull(2) ?: return "original"
        return if (format == "raw") "original" else listOfNotNull(format, parts.getOrNull(3)?.takeIf { it.isNotEmpty() }).joinToString(" ")
    }
}
