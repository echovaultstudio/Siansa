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
import coil3.request.allowRgb565
import coil3.request.transitionFactory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import app.siansa.covers.CoverDownload
import app.siansa.data.SyncPhase

/**
 * The image cache size the image loader was built with at start (issue #780): a different size picked since applies
 * only after a restart, and the Downloads page says so for as long as that's true. Null until the loader is built.
 */
@Volatile var imageCacheMbInUse: Int? = null

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
        // The cover colours kept on the phone, so pages seen before have theirs in their first frame (CoverToneStore).
        Thread({ app.siansa.ui.CoverToneStore.load(this) }, "cover-tones").start()
        // A step count each time the app comes to the front, so the next mix can tell walking from sitting
        // (issue #247): one reading, only while movement is switched on for the vibes.
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                if (graph.settings.current.situation.movement) graph.scope.launch { graph.situation.noteSteps() }
                graph.connection.cameToFront()
            }
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
        Diagnostics.start(this)
        // The TV keeps little (StorageBudget): say what's there and make room, away from the main thread.
        if (BuildConfig.TV_APP) graph.scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { app.siansa.data.TvStorage.tidy(this@SiansaApp) { graph.songCache } }
                .onFailure { AppLog.w("Storage", "Couldn't check what's kept on the TV: ${it.message}") }
        }
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
     * Covers are kept on disk as they load, up to the size chosen in Settings › Data (or without a
     * limit), so scrolling back is instant and works offline. With no limit they're kept in the app's
     * files rather than its cache, so Android doesn't remove them when it runs short of space.
     *
     * Every image is decoded as a still (the first frame of an animated one). Only the album page and now
     * playing ask for an animation, per request (see AnimatedCovers): decoding each list tile of an
     * animated album as an animation is what ran the app out of memory.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // The TV app has no page for the size and keeps far less: what its storage budget gives it (StorageBudget).
        val mb = if (BuildConfig.TV_APP) app.siansa.data.StorageBudget.ofTv(this).picturesMb else graph.settings.current.imageCacheMb
        imageCacheMbInUse = mb
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
                // Every picture is kept on the phone except the server's stand-in for one it doesn't have right now.
                add(OkHttpNetworkFetcherFactory(callFactory = { graph.imageHttp }, cacheStrategy = { app.siansa.covers.KeepRealCovers() }))
                // Animated covers shown as stills: their first frame (Android's still decoder fails on some).
                add(app.siansa.covers.FirstFrameDecoder.Factory())
            }
            // Room for a few hundred decoded list covers, so scrolling back shows them at once. Half that on a TV
            // (issue #860): an NVIDIA SHIELD kept warning of low memory, and its covers come from the disk cache quickly.
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, if (BuildConfig.TV_APP) 0.15 else 0.30).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(directory)
                    .maxSizeBytes(bytes)
                    .build()
            }
            .apply {
                if (BuildConfig.TV_APP) {
                    // Lighter covers on a TV (issue #951): a cover without see-through parts takes half the memory
                    // (16 bits a pixel, which a cover on a TV doesn't show), and a few load at a time, so a row of
                    // six doesn't hold up the screen or the music.
                    allowRgb565(true)
                    fetcherCoroutineContext(kotlinx.coroutines.Dispatchers.IO.limitedParallelism(TV_COVER_LOADS))
                    decoderCoroutineContext(kotlinx.coroutines.Dispatchers.Default.limitedParallelism(TV_COVER_DECODES))
                }
            }
            // Fade in only covers that just came over the network, and never on a TV (issue #951). One read from the
            // phone's own cache appears straight away, as it would if it were still in memory.
            .transitionFactory { target, result ->
                if (!BuildConfig.TV_APP && result is coil3.request.SuccessResult && result.dataSource == coil3.decode.DataSource.NETWORK) {
                    coil3.transition.CrossfadeTransition(target, result, 110)
                } else {
                    coil3.transition.Transition.Factory.NONE.create(target, result)
                }
            }
            // Every picture that fails to show, and why, so blank covers can be traced in the log.
            .eventListener(object : coil3.EventListener() {
                override fun onError(request: coil3.request.ImageRequest, result: coil3.request.ErrorResult) {
                    val data = request.data.toString()
                    // Never the address itself: Emby's and Jellyfin's carry the sign-in.
                    val what = app.siansa.covers.CoverAddress.of(data)?.let { "cover ${it.id} (size ${it.size ?: "original"})" }
                        ?: data.substringBefore('?').substringAfterLast('/').take(60)
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
            // How songs blend, so a DJ session's log says what it was set to (0.52.0-dev).
            "crossfade ${s.crossfade.name.lowercase()} ${s.crossfadeSeconds} s, beat-matched ${s.beatMatch.name.lowercase()}, ${s.blendBars} bars, bass swap ${if (s.bassSwap) "on" else "off"}",
            if (BuildConfig.TV_APP) app.siansa.data.StorageBudget.ofTv(this).let { "song cache ${it.songsMb} MB, image cache ${it.picturesMb} MB, animated covers kept ${it.animatedMb} MB" }
            else "song cache ${mb(s.songCacheMb)}, image cache ${mb(s.imageCacheMb)}",
            "animated covers ${if (s.animatedCovers) "on" else "off"}, download all covers ${if (s.coverDownload) (if (s.coverDownloadAnyWifi) "any Wi-Fi" else "home") else "off"}",
            "scrobbling: " + listOfNotNull("Navidrome".takeIf { s.scrobbleNavidrome }, "Siansa Beats".takeIf { with(app.siansa.net.BeatsListens) { s.scrobbleBeats && s.beatsReady } }, "Last.fm".takeIf { s.lastFmReady }, "ListenBrainz/Koito".takeIf { s.listenBrainzReady }).joinToString(", ").ifEmpty { "off" },
        ).joinToString(" · ")
    }

    // Android asks apps to free memory when the phone runs low; useful context for stutter.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW && level < TRIM_MEMORY_UI_HIDDEN || level >= TRIM_MEMORY_BACKGROUND) {
            AppLog.w("Phone", "Low memory signal (level $level) · ${Diagnostics.snapshot(this)}")
        }
        // Give back decoded covers, the biggest thing the app keeps that it can do without ([CoverTrim]).
        val covers = runCatching { coil3.SingletonImageLoader.get(this).memoryCache }.getOrNull() ?: return
        CoverTrim.keep(level, covers.maxSize)?.let { keep -> if (keep == 0L) covers.clear() else covers.trimToSize(keep) }
    }

    companion object {
        const val IMAGE_CACHE_DIR = "image_cache"

        /** In the cache folder normally; in the app's own files when there's no limit. */
        fun imageCacheDir(context: android.content.Context, unlimited: Boolean): java.io.File =
            if (unlimited) context.filesDir.resolve(IMAGE_CACHE_DIR) else context.cacheDir.resolve(IMAGE_CACHE_DIR)
    }
}

/**
 * How much of the cover memory cache to keep when Android asks for memory back (issue #892): all of it goes once the
 * app is out of sight, half while it's on screen and memory runs low or critical, as the covers showing load again
 * from the disk cache otherwise. The NVIDIA SHIELD says "critical" about once a minute while Siansa uses a quarter of
 * what it may, and emptying the whole cache each time made every cover on the TV load again: the lag, and the same
 * covers fetched over and over. Null: leave it as it is.
 */
object CoverTrim {
    fun keep(level: Int, maxSize: Long): Long? = when {
        level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> 0L
        level == android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> maxSize / 2
        level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> maxSize / 2
        else -> null
    }
}

/** Covers a TV loads and decodes at a time (issue #951). */
private const val TV_COVER_LOADS = 4
private const val TV_COVER_DECODES = 2
