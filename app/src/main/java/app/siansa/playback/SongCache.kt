package app.siansa.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import app.siansa.data.AppSettings

/**
 * Cache keys leave out the per-session login token in stream URLs, so a song cached yesterday is
 * found again today. Originals and each Opus bitrate are kept apart.
 */
fun songCacheKey(uri: Uri): String {
    val id = uri.getQueryParameter("id") ?: return uri.toString()
    val format = uri.getQueryParameter("format") ?: "raw"
    val rate = uri.getQueryParameter("maxBitRate") ?: ""
    return "song:$id:$format:$rate"
}

/** Whether songs are kept on the phone as they play: Settings › Storage › Song cache, where 0 means Off. */
fun songCacheOn(settings: AppSettings): Boolean = settings.songCacheMb > 0

/**
 * Whether a stream in [format] goes through the song cache: only originals (no format, or raw), and only while the
 * cache is on ([cacheOn]). With it off, every song streams straight from the server and nothing is kept.
 */
fun goesThroughCache(format: String?, cacheOn: Boolean): Boolean = cacheOn && (format == null || format == "raw")

/**
 * Original files go through the song cache, unless it's switched off ([useCache], checked as each song opens, so
 * switching it off applies from the next song). Opus conversions stream directly: their length is only
 * an estimate, which doesn't mix well with caching byte ranges.
 */
@OptIn(UnstableApi::class)
class CachingSwitchDataSource(
    private val cached: CacheDataSource,
    private val direct: DataSource,
    private val useCache: () -> Boolean = { true },
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        cached.addTransferListener(transferListener)
        direct.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (goesThroughCache(dataSpec.uri.getQueryParameter("format"), useCache())) cached else direct
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active?.read(buffer, offset, length) ?: throw IllegalStateException("Not opened")

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }

    @OptIn(UnstableApi::class)
    class Factory(
        private val cached: CacheDataSource.Factory,
        private val direct: DataSource.Factory,
        private val useCache: () -> Boolean = { true },
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            CachingSwitchDataSource(cached.createDataSource(), direct.createDataSource(), useCache)
    }
}
