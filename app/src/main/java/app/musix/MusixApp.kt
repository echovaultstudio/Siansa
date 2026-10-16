package app.musix

import android.app.Application
import android.os.Build
import app.musix.log.AppLog
import app.musix.log.Diagnostics
import app.musix.mix.MixWorker
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.launch

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
            // A quick check at every start: usually just "nothing changed", or a few albums.
            if (graph.settings.current.navidromeReady) graph.sync.start(quiet = true)
        }
        MixWorker.schedule(this)
    }

    /**
     * Covers are kept on disk as they load, up to the size chosen in Settings → Storage, so scrolling
     * back is instant and works offline. Animated GIF/WebP covers play when Navidrome serves the original.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val megabytes = graph.settings.current.imageCacheMb.coerceAtLeast(50)
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { graph.http }))
                if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.20).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve(IMAGE_CACHE_DIR))
                    .maxSizeBytes(megabytes * 1024L * 1024L)
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
    }
}
