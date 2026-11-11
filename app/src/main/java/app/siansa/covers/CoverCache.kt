package app.siansa.covers

import app.siansa.log.AppLog
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.CacheStrategy
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.Options

/**
 * Where a cover is kept on the phone, said in one place (the owner, 11 Nov): every screen, the mini player, Now
 * playing, the notification, the car and the colours taken from a cover use the same entries, so a cover fetched for
 * one shows in all of them.
 *
 * On disk a cover is `cover:<its id, with its version>:<the size asked of the server>`; in memory the same, with what
 * it's shown as after it (`@192`, `:palette`, `:notification`…).
 */
object CoverCache {
    /** The sizes covers are asked for at: lists, the big cover, the original file. */
    val SIZES = listOf(app.siansa.ui.SMALL_COVER.toString(), app.siansa.ui.LARGE_COVER.toString(), "full")

    fun diskKey(id: String, px: Int?): String = "cover:${CoverVersions.cacheId(id)}:${px ?: "full"}"

    /** The cover a disk key is of: `cover:dc-1:2@7:300` → `dc-1:2@7`. */
    fun cacheIdOf(diskKey: String): String = diskKey.removePrefix("cover:").substringBeforeLast(':')

    /** Drops what's in memory of the cover kept under [cacheId], at every size and in every shape. */
    fun forgetInMemory(loader: ImageLoader, cacheId: String) {
        val memory = loader.memoryCache ?: return
        val start = "cover:$cacheId:"
        memory.keys.filter { it.key.startsWith(start) }.forEach { memory.remove(it) }
    }

    /** Drops every kept picture of cover [id], on disk and in memory: the next showing fetches it fresh. */
    fun forget(loader: ImageLoader, id: String) {
        val cacheId = CoverVersions.cacheId(id)
        loader.diskCache?.let { disk -> SIZES.forEach { size -> runCatching { disk.remove("cover:$cacheId:$size") } } }
        forgetInMemory(loader, cacheId)
    }

    /**
     * Called wherever cover [id] is about to be shown: when the server's stand-in has been showing for a while
     * ([StandIns.AGAIN_MS]), it's dropped from memory, so this showing asks the server again.
     */
    fun askAgain(loader: ImageLoader, id: String) {
        val cacheId = CoverVersions.cacheId(id)
        if (StandIns.askAgain(cacheId)) forgetInMemory(loader, cacheId)
    }
}

/**
 * What the image loader keeps on disk: every picture, as before, except the server's stand-in ([StandIns]), which
 * is shown and then asked for again.
 */
@OptIn(ExperimentalCoilApi::class)
class KeepRealCovers : CacheStrategy {
    override suspend fun read(cacheResponse: NetworkResponse, networkRequest: NetworkRequest, options: Options): CacheStrategy.ReadResult =
        CacheStrategy.DEFAULT.read(cacheResponse, networkRequest, options)

    override suspend fun write(
        cacheResponse: NetworkResponse?,
        networkRequest: NetworkRequest,
        networkResponse: NetworkResponse,
        options: Options,
    ): CacheStrategy.WriteResult {
        val headers = networkResponse.headers
        if (networkResponse.code in 200..299 && StandIns.isStandIn(networkRequest.url, headers["Cache-Control"], headers["ETag"])) {
            options.diskCacheKey?.let { key ->
                StandIns.shown(CoverCache.cacheIdOf(key))
                AppLog.d("Cover", "The server sent its stand-in for ${CoverCache.cacheIdOf(key)}: shown, not kept, asked for again later")
            }
            return CacheStrategy.WriteResult.DISABLED
        }
        // An id without a version: its ETag is kept, so Sync all can ask whether the picture changed.
        if (StandIns.isCoverArt(networkRequest.url)) options.diskCacheKey?.let { key -> headers["ETag"]?.let { CoverEtags.put(key, it) } }
        return CacheStrategy.DEFAULT.write(cacheResponse, networkRequest, networkResponse, options)
    }
}

/**
 * The server's version (`ETag`) of each kept cover whose id carries none (`al-<id>`, `ar-<id>`): what Sync all sends
 * back to ask "still this one?" (`CoverRefresh`). Kept in memory, saved by [onChanged].
 */
object CoverEtags {
    private val etags = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Volatile
    var onChanged: ((key: String, etag: String?) -> Unit)? = null

    /** Whether a cover kept under [cacheId] carries no version of its own. */
    fun unversioned(cacheId: String): Boolean = !cacheId.contains('_') && !cacheId.contains('@')

    fun put(diskKey: String, etag: String) {
        if (!unversioned(CoverCache.cacheIdOf(diskKey)) || etag.isBlank()) return
        if (etags.put(diskKey, etag) != etag) onChanged?.invoke(diskKey, etag)
    }

    fun get(diskKey: String): String? = etags[diskKey]

    fun remove(diskKey: String) {
        if (etags.remove(diskKey) != null) onChanged?.invoke(diskKey, null)
    }

    fun restore(kept: Map<String, String>) {
        kept.forEach { (key, etag) -> etags.putIfAbsent(key, etag) }
    }

    internal fun forget() = etags.clear()
}
