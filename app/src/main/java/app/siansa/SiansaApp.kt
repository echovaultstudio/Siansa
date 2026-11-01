package app.siansa

import android.app.Application
import app.siansa.log.AppLog
import app.siansa.log.Diagnostics
import app.siansa.log.ExitReasons
import app.siansa.mix.MixWorker
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.transitionFactory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import app.siansa.covers.CoverDownload
import app.siansa.data.SyncPhase

class SiansaApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // Which Navidrome user this start is for: their settings, library and history are opened below.
        app.siansa.data.Users.init(this)
        graph = AppGraph(this)
        AppLog.init(this, graph.settings.current.logging)
        AppLog.watchLifecycle(this)
        graph.mixResume.watch()
        // A step count each time the app comes to the front, so the next mix can tell walking from sitting
        // (issue #247): one reading, only while movement is switched on for the vibes.
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                if (graph.settings.current.situation.movement) graph.scope.launch { graph.situation.noteSteps() }
            }
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
        Diagnostics.start(this)
        // Each start: what the app is running on and with, then why it last closed.
        if (AppLog.enabled) AppLog.i("Session", sessionHeader())
        ExitReasons.logNew(this)
        if (AppLog.detailOn) AppLog.d("Phone", Diagnostics.snapshot(this))
        graph.scope.launch {
            graph.library.load()
            graph.library.status.value?.let { AppLog.i("Session", "Library: ${it.artists} artists, ${it.albums} albums, ${it.songs} songs") }
            // The time machine's song index, ready before it's opened (issue #319).
            graph.insights.warmUp()
            if (graph.settings.current.navidromeReady) {
                runCatching { AppLog.i("Server", graph.source.ping()) }
            }
            // A quick check at every start: usually just "nothing changed", or a few albums.
            if (graph.settings.current.navidromeReady) graph.sync.start(quiet = true)
        }
        MixWorker.schedule(this)
        CoverDownload.schedule(this, graph.settings.current)
        // After each library sync, fetch the covers of anything new (when downloading all covers is on).
        graph.scope.launch {
            graph.sync.progress.map { it.phase }.distinctUntilChanged().collect { phase ->
                if (phase == SyncPhase.DONE && graph.settings.current.coverDownload) CoverDownload.start(this@SiansaApp)
            }
        }
    }

    /**
     * Covers are kept on disk as they load, up to the size chosen in Settings › Streaming and data (or without a
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
                // Animated covers shown as stills: their first frame (Android's still decoder fails on some).
                add(app.siansa.covers.FirstFrameDecoder.Factory())
            }
            // Room for a few hundred decoded list covers, so scrolling back shows them at once.
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.30).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(directory)
                    .maxSizeBytes(bytes)
                    .build()
            }
            // Fade in only covers that just came over the network. One read from the phone's own cache
            // appears straight away, as it would if it were still in memory.
            .transitionFactory { target, result ->
                if (result is coil3.request.SuccessResult && result.dataSource == coil3.decode.DataSource.NETWORK) {
                    coil3.transition.CrossfadeTransition(target, result, 110)
                } else {
                    coil3.transition.Transition.Factory.NONE.create(target, result)
                }
            }
            // Every picture that fails to show, and why, so blank covers can be traced in the log.
            .eventListener(object : coil3.EventListener() {
                override fun onError(request: coil3.request.ImageRequest, result: coil3.request.ErrorResult) {
                    val data = request.data.toString()
                    val what = Regex("[?&]id=([^&]+)").find(data)?.groupValues?.get(1)?.let { id ->
                        "cover $id (size ${Regex("[?&]size=(\\d+)").find(data)?.groupValues?.get(1) ?: "original"})"
                    } ?: data.substringAfterLast('/').take(60)
                    val e = result.throwable
                    AppLog.w("Image", "Couldn't show $what: ${e.javaClass.simpleName}: ${e.message}")
                    // A cover that can't be read may be an error message Navidrome sent once and the cache
                    // kept; forgetting it lets the next look fetch it again (issue #162).
                    // Android's ImageDecoder.DecodeException (by name: the class only exists from Android 9).
                    if (e.javaClass.simpleName == "DecodeException") {
                        request.diskCacheKey?.let { key -> runCatching { coil3.SingletonImageLoader.get(this@SiansaApp).diskCache?.remove(key) } }
                    }
                }
            })
            .build()
    }

    /** Version, phone, and the settings that change behaviour (never addresses, logins or keys). */
    private fun sessionHeader(): String {
        val s = graph.settings.current
        val am = getSystemService(android.app.ActivityManager::class.java)
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        val network = if (app.siansa.playback.Streaming.isUnmetered(this)) "Wi-Fi" else "mobile data"
        fun mb(v: Int) = if (v <= 0) "no limit" else "$v MB"
        return listOf(
            "Siansa $version started",
            "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT}) on ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            "memory class ${am?.memoryClass ?: "?"} MB",
            network,
            "Navidrome ${if (s.navidromeReady) "set" else "not set"}, AudioMuse ${if (s.audioMuseReady) "set" else "not set"}, AI ${if (s.aiReady) "on" else "off"}",
            "quality Wi-Fi ${if (s.transcodeWifiKbps > 0) "Opus ${s.transcodeWifiKbps}" else "original"}, mobile ${if (s.transcodeMobileKbps > 0) "Opus ${s.transcodeMobileKbps}" else "original"}",
            "ReplayGain ${s.replayGain.label}",
            "song cache ${mb(s.songCacheMb)}, image cache ${mb(s.imageCacheMb)}",
            "animated covers ${if (s.animatedCovers) "on" else "off"}, download all covers ${if (s.coverDownload) (if (s.coverDownloadAnyWifi) "any Wi-Fi" else "home") else "off"}",
            "scrobbling: " + listOfNotNull("Navidrome".takeIf { s.scrobbleNavidrome }, "Last.fm".takeIf { s.lastFmReady }, "ListenBrainz/Koito".takeIf { s.listenBrainzReady }).joinToString(", ").ifEmpty { "off" },
        ).joinToString(" · ")
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
