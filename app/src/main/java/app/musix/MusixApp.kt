package app.musix

import android.app.Application
import app.musix.log.AppLog
import app.musix.log.Diagnostics
import app.musix.mix.MixWorker
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import app.musix.covers.CoverDownload
import app.musix.data.SyncPhase

class MusixApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        AppLog.init(this, graph.settings.current.logging)
        AppLog.setDetailed(graph.settings.current.logDetailed)
        Diagnostics.start(this)
        if (AppLog.detailOn) AppLog.d("Phone", Diagnostics.snapshot(this))
        graph.scope.launch {
            graph.library.load()
            if (graph.settings.current.navidromeReady) {
                runCatching { AppLog.i("Server", graph.subsonic.ping()) }
            }
            // A quick check at every start: usually just "nothing changed", or a few albums.
            if (graph.settings.current.navidromeReady) graph.sync.start(quiet = true)
        }
        MixWorker.schedule(this)
        CoverDownload.schedule(this, graph.settings.current)
        // After each library sync, fetch the covers of anything new (when downloading all covers is on).
        graph.scope.launch {
            graph.sync.progress.map { it.phase }.distinctUntilChanged().collect { phase ->
                if (phase == SyncPhase.DONE && graph.settings.current.coverDownload) CoverDownload.start(this@MusixApp)
            }
        }
    }

    /**
     * Covers are kept on disk as they load, up to the size chosen in Settings → Storage (or without a
     * limit), so scrolling back is instant and works offline. With no limit they're kept in the app's
     * files rather than its cache, so Android doesn't remove them when it runs short of space.
     *
     * Every image is decoded as a still (the first frame of an animated one). Only the album page and now
     * playing ask for an animation, per request (see AnimatedCovers): decoding each list tile of an
     * animated album as an animation is what ran the app out of memory.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val mb = graph.settings.current.imageCacheMb
        val unlimited = mb <= 0
        val bytes = if (unlimited) Long.MAX_VALUE / 4 else mb.coerceAtLeast(50) * 1024L * 1024L
        val directory = imageCacheDir(this, unlimited)
        // Moving between limited and unlimited keeps what's already downloaded.
        val other = imageCacheDir(this, !unlimited)
        if (!directory.exists() && other.exists()) {
            directory.parentFile?.mkdirs()
            if (!other.renameTo(directory)) other.deleteRecursively()
        }
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { graph.imageHttp }))
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.20).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(directory)
                    .maxSizeBytes(bytes)
                    .build()
            }
            .crossfade(110)
            .build()
    }

    // Android asks apps to free memory when the phone runs low; useful context for stutter.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW && level < TRIM_MEMORY_UI_HIDDEN || level >= TRIM_MEMORY_BACKGROUND) {
            AppLog.w("Phone", "Low memory signal (level $level) · ${Diagnostics.snapshot(this)}")
        }
    }

    companion object {
        const val IMAGE_CACHE_DIR = "image_cache"

        /** In the cache folder normally; in the app's own files when there's no limit. */
        fun imageCacheDir(context: android.content.Context, unlimited: Boolean): java.io.File =
            if (unlimited) context.filesDir.resolve(IMAGE_CACHE_DIR) else context.cacheDir.resolve(IMAGE_CACHE_DIR)
    }
}
