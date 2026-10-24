package app.siansa.covers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.siansa.AppGraph
import app.siansa.SiansaApp
import app.siansa.data.AppSettings
import app.siansa.log.AppLog
import app.siansa.ui.AnimatedCovers
import app.siansa.ui.LARGE_COVER
import app.siansa.ui.SMALL_COVER
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Downloads every album, artist and playlist cover ahead of time, like Symfonium's automatic image
 * cache: in the background, after a library sync and once a day, only on Wi-Fi and (unless set
 * otherwise) only when the phone is on the same local network as Navidrome, so it never uses mobile data
 * or a slow link from outside. Covers already on the phone are skipped, so later runs only fetch new ones.
 *
 * Each cover is saved at the two sizes the app shows (lists and big covers), straight into the image
 * cache under the keys the screens use. Animated covers go through [CoverStore] as everywhere else.
 */
object CoverDownload {
    data class Progress(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val downloaded: Int = 0,
        val failed: Int = 0,
        val note: String? = null,
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private const val NOW = "cover-download"
    internal const val DAILY = "cover-download-daily"

    /**
     * Any connection: whether it's Wi-Fi at home is decided by [whyNot] when the run starts. Android's own
     * "unmetered" condition was never met with a VPN on top of Wi-Fi (Android counts most VPNs as metered), so
     * the download waited forever (issue #297).
     */
    private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * In the background, as soon as there's a connection (after a sync). Replaces a request still waiting,
     * so an old one can't hold up new ones; a run in progress carries on (see [runShared]).
     */
    fun start(context: Context) {
        if (stoppedByUser) {
            AppLog.i("Covers", "Cover download not started after the sync: it was stopped from Settings")
            return
        }
        val request = OneTimeWorkRequestBuilder<CoverDownloadWorker>().setConstraints(constraints()).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        AppLog.i("Covers", "Cover download requested")
    }

    /**
     * Straight away, from Settings (issue #297): the Download covers now button and switching it on. Says at once
     * why it can't run on this network, instead of waiting for Android to start it.
     */
    fun startNow(context: Context, graph: AppGraph) {
        AppLog.i("Covers", "Cover download requested from Settings")
        stoppedByUser = false
        graph.scope.launch { runShared(context.applicationContext, graph, "from Settings") }
    }

    /** Daily runs on, or off, to match the setting. */
    fun schedule(context: Context, settings: AppSettings) {
        val work = WorkManager.getInstance(context)
        if (!settings.coverDownload) {
            work.cancelUniqueWork(DAILY)
            return
        }
        // The first daily run waits a day: switching it on already starts a run now.
        val request = PeriodicWorkRequestBuilder<CoverDownloadWorker>(1, TimeUnit.DAYS)
            .addTag(DAILY)
            .setInitialDelay(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .build()
        // UPDATE: a daily run scheduled by an older version with the "unmetered" condition takes the new one.
        work.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * Stops a run in progress (the Stop button), straight away (issue #301): the covers downloading now are
     * cancelled mid-file (a half-downloaded cover isn't kept, so the next run fetches it again) and the page
     * shows it stopped at once. Runs after syncs stay off until the user starts one again; the daily run
     * still goes.
     */
    fun stop(context: Context) {
        stoppedByUser = true
        WorkManager.getInstance(context).cancelUniqueWork(NOW)
        job?.takeIf { it.isActive }?.let {
            AppLog.i("Covers", "Cover download stopped from Settings")
            it.cancel()
            update { p -> p.copy(running = false, note = "Stopped after ${p.done} of ${p.total}") }
        }
    }

    /** Stop was pressed: the runs after syncs wait until the user starts one again (or the app restarts). */
    @Volatile var stoppedByUser = false
        private set

    @Volatile private var job: kotlinx.coroutines.Job? = null

    /**
     * The download itself runs in the app's own scope, once at a time. WorkManager only starts it (on
     * Wi-Fi, after syncs, daily) and waits for it, so if Android stops the worker early (as it did after
     * 9 seconds on a Samsung) the download carries on while the app is running instead of being lost.
     */
    internal suspend fun runShared(context: Context, graph: AppGraph, why: String) {
        val current = synchronized(this) {
            // A stopped run isn't active any more but may still be letting go of its last covers: the new run
            // waits for it rather than running beside it (issue #301).
            val previous = job
            previous?.takeIf { it.isActive } ?: graph.scope.launch {
                previous?.join()
                AppLog.i("Covers", "Cover download starting ($why)")
                try {
                    run(context.applicationContext, graph)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    AppLog.i("Covers", "Cover download cancelled after ${_progress.value.done} of ${_progress.value.total}")
                    update { it.copy(running = false, note = "Stopped after ${it.done} of ${it.total}") }
                    throw e
                } catch (e: Exception) {
                    AppLog.e("Covers", "Cover download failed after ${_progress.value.done} of ${_progress.value.total}", e)
                    update { it.copy(running = false, note = "Failed: ${e.message ?: e.javaClass.simpleName}") }
                } finally {
                    update { it.copy(running = false) }
                }
            }.also { job = it }
        }
        current.join()
    }

    /** Why covers can't be downloaded on this network right now, or null when they can. */
    fun whyNot(context: Context, settings: AppSettings): String? {
        if (!settings.navidromeReady) return "Navidrome isn't set up"
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "No network information"
        val nets = cm.allNetworks.mapNotNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
            val link = cm.getLinkProperties(network)
            NetInfo(
                vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                // Wi-Fi or Ethernet, not metered. A VPN reports the network it runs over (Android 9 and later).
                wifi = (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                addresses = link?.linkAddresses.orEmpty().map { Prefix(it.address, it.prefixLength) },
                routes = link?.routes.orEmpty()
                    // Android 10 and later mark routes that lead nowhere (a VPN's blocking rules).
                    .filter { Build.VERSION.SDK_INT < 29 || it.type == RouteInfo.RTN_UNICAST }
                    .mapNotNull { r -> r.destination?.let { Prefix(it.address, it.prefixLength) } },
            )
        }
        networkProblem(nets, settings.coverDownloadWifiOnly)?.let { return it }
        if (!settings.coverDownloadWifiOnly || settings.coverDownloadAnyWifi) return null
        val host = settings.navidromeUrl.toHttpUrlOrNull()?.host ?: return "Navidrome's address isn't valid"
        val addresses = runCatching { InetAddress.getAllByName(host).toList() }.getOrElse { return "Couldn't look up $host" }
        val how = homeRoute(nets, addresses)
        if (how != null) {
            if (lastHow != how) AppLog.i("Covers", "Navidrome ($host) counts as home: $how")
            lastHow = how
            return null
        }
        lastHow = null
        AppLog.i(
            "Covers",
            "Navidrome ($host, ${addresses.joinToString { it.hostAddress.orEmpty() }}) isn't on this Wi-Fi or a VPN over it: " +
                nets.joinToString("; ") { it.describe() },
        )
        return "Not on the same network as Navidrome ($host)"
    }

    @Volatile private var lastHow: String? = null

    /**
     * Why the phone's networks don't allow downloading covers, before asking where Navidrome is (issue #299):
     * with Wi-Fi only, a VPN counts as mobile data, so any VPN stops it, as does having no Wi-Fi. Without
     * Wi-Fi only, any connection will do.
     */
    fun networkProblem(nets: List<NetInfo>, wifiOnly: Boolean): String? = when {
        nets.isEmpty() -> "No connection"
        !wifiOnly -> null
        nets.any { it.vpn } -> "A VPN is on, which counts as mobile data"
        nets.none { it.wifi } -> "Not on Wi-Fi"
        else -> null
    }

    /** An address and prefix length: a network's own address, or where one of its routes leads. */
    data class Prefix(val address: InetAddress, val length: Int) {
        override fun toString() = "${address.hostAddress}/$length"
    }

    /** What [homeRoute] needs to know about one of the phone's networks. */
    data class NetInfo(
        val vpn: Boolean,
        val wifi: Boolean,
        val cellular: Boolean,
        val addresses: List<Prefix>,
        val routes: List<Prefix> = emptyList(),
    ) {
        fun describe() = (if (vpn) "VPN" else if (wifi) "Wi-Fi" else if (cellular) "mobile" else "other") +
            (if (vpn && wifi) " over Wi-Fi" else if (vpn && cellular) " over mobile" else "") +
            " ${addresses.joinToString(",")} routes ${routes.joinToString(",")}"
    }

    /**
     * How Navidrome is reached from home, or null when it isn't. Home is the phone on Wi-Fi or Ethernet with
     * Navidrome either on that network or on a home network that a VPN reaches over that Wi-Fi (issue #205):
     * a VPN route leading to Navidrome's private address (WireGuard's 192.168.1.0/24, or everything).
     * A VPN over mobile data never counts, so covers never use mobile data.
     */
    fun homeRoute(nets: List<NetInfo>, server: List<InetAddress>): String? {
        if (nets.none { it.wifi }) return null
        for (net in nets.filter { it.wifi && !it.vpn }) for (a in net.addresses) for (s in server) {
            if (sameSubnet(a.address, a.length, s)) return "on the same Wi-Fi ($a)"
        }
        // A VPN that says it runs over Wi-Fi, or one that doesn't say and isn't on mobile data.
        val vpns = nets.filter { it.vpn && (it.wifi || !it.cellular) }
        for (vpn in vpns) for (s in server.filter(::isPrivate)) {
            for (r in vpn.routes) {
                val sameFamily = (r.address is Inet4Address) == (s is Inet4Address)
                if (r.length == 0 && sameFamily) return "through the VPN, which carries everything (${s.hostAddress})"
                if (sameSubnet(r.address, r.length, s)) return "through the VPN's route $r"
            }
            for (a in vpn.addresses) if (sameSubnet(a.address, a.length, s)) return "on the VPN's network ($a)"
        }
        return null
    }

    /** A home network's address: 10/8, 172.16/12, 192.168/16, link-local, 100.64/10 (Tailscale) or IPv6 fc00::/7. */
    fun isPrivate(address: InetAddress): Boolean {
        if (address.isSiteLocalAddress || address.isLinkLocalAddress) return true
        val b = address.address
        if (address is Inet4Address) return (b[0].toInt() and 0xFF) == 100 && (b[1].toInt() and 0xC0) == 64
        return (b[0].toInt() and 0xFE) == 0xFC
    }

    internal fun sameSubnet(mine: InetAddress, prefix: Int, other: InetAddress): Boolean {
        if ((mine is Inet4Address) != (other is Inet4Address)) return false
        val a = mine.address
        val b = other.address
        if (prefix <= 0 || prefix > a.size * 8) return false
        var bits = prefix
        for (i in a.indices) {
            if (bits <= 0) break
            val mask = if (bits >= 8) 0xFF else (0xFF shl (8 - bits)) and 0xFF
            if ((a[i].toInt() and mask) != (b[i].toInt() and mask)) return false
            bits -= 8
        }
        return true
    }

    internal fun update(block: (Progress) -> Progress) = _progress.update(block)

    /** Downloads what's missing. Returns normally when done, stopped, or off the home network. */
    internal suspend fun run(context: Context, graph: AppGraph) = withContext(Dispatchers.IO) {
        val settings = graph.settings.current
        whyNot(context, settings)?.let { reason ->
            AppLog.i("Covers", "Not downloading covers: $reason")
            update { it.copy(running = false, note = reason) }
            return@withContext
        }
        val disk = SingletonImageLoader.get(context).diskCache ?: return@withContext
        val main = buildList {
            graph.library.albums("name").forEach { it.coverArt?.let(::add) }
            graph.library.artists().forEach { add(it.coverArt ?: "ar-${it.id}") }
            graph.library.playlists().forEach { it.coverArt?.let(::add) }
        }.distinctBy { CoverStore.canonical(it) }
        val songCovers = songCoversToFetch(graph.library.songCoverIds(), main)
        val ids = main + songCovers
        AppLog.i("Covers", "Downloading covers: ${main.size} albums, artists and playlists and ${songCovers.size} song and disc covers to check")
        update { Progress(running = true, total = ids.size) }
        val started = System.currentTimeMillis()
        val downloaded = AtomicInteger()
        val failed = AtomicInteger()
        val done = AtomicInteger()
        val permits = Semaphore(4)
        val lastShown = java.util.concurrent.atomic.AtomicLong(0)
        var stoppedBecause: String? = null
        coroutineScope {
            for ((index, id) in ids.withIndex()) {
                // Left home or Wi-Fi: stop, and pick up where it left off next time.
                if (index % 50 == 49) {
                    // A brief Wi-Fi or DNS hiccup shouldn't end the run: look again a few seconds later.
                    stoppedBecause = whyNot(context, graph.settings.current)?.let { first ->
                        kotlinx.coroutines.delay(5_000)
                        whyNot(context, graph.settings.current)?.also { AppLog.i("Covers", "Network check failed twice ($first)") }
                    }
                }
                if (stoppedBecause != null) break
                permits.acquire()
                launch {
                    try {
                        when (one(graph, disk, id, listsOnly = index >= main.size)) {
                            Outcome.DOWNLOADED -> downloaded.incrementAndGet()
                            Outcome.FAILED -> failed.incrementAndGet()
                            Outcome.HAD -> Unit
                        }
                    } finally {
                        permits.release()
                        val n = done.incrementAndGet()
                        // A few updates a second are enough for the progress bar; every cover made the page stutter.
                        val now = System.currentTimeMillis()
                        if (now - lastShown.get() > 400 || n == ids.size) {
                            lastShown.set(now)
                            update { it.copy(done = n, downloaded = downloaded.get(), failed = failed.get()) }
                        }
                        if (n % 250 == 0) {
                            AppLog.i(
                                "Covers",
                                "Covers: checked $n of ${ids.size} · ${downloaded.get()} downloaded · ${failed.get()} failed · " +
                                    "kept animated ${"%.0f".format(CoverStore.sizeBytes() / 1_048_576.0)} MB",
                            )
                        }
                    }
                }
            }
        }
        val seconds = (System.currentTimeMillis() - started) / 1000
        val summary = "${downloaded.get()} downloaded, ${done.get() - downloaded.get() - failed.get()} already here" +
            (if (failed.get() > 0) ", ${failed.get()} failed" else "") + " in ${seconds}s"
        AppLog.i("Covers", "Covers: $summary" + (stoppedBecause?.let { ". Stopped: $it" } ?: ""))
        update { it.copy(running = false, note = stoppedBecause?.let { "Stopped: $it" } ?: "Done: $summary") }
    }

    private enum class Outcome { HAD, DOWNLOADED, FAILED }

    /**
     * The song and disc covers to download besides albums', exactly the ids Navidrome gives songs, so
     * every song list shows them from the phone (issue #201). Which picture each one is stays Navidrome's
     * choice (its cover art priority, disc art priority and media file cover art settings). Ids already in
     * [others] are left out.
     */
    internal fun songCoversToFetch(songCovers: List<String>, others: List<String>): List<String> {
        val have = others.mapTo(HashSet()) { CoverStore.canonical(it) }
        return songCovers.filter { it.isNotEmpty() && have.add(CoverStore.canonical(it)) }
    }

    /** [listsOnly]: just the small size song lists use (song and disc covers); now playing starts from it while its big one loads. */
    private suspend fun one(graph: AppGraph, disk: DiskCache, id: String, listsOnly: Boolean = false): Outcome {
        // An animated original kept on the phone answers every size already.
        if (CoverStore.has(id)) return Outcome.HAD
        var outcome = Outcome.HAD
        for (size in if (listsOnly) listOf(SMALL_COVER) else listOf(SMALL_COVER, LARGE_COVER)) {
            if (size == LARGE_COVER) {
                // Big sizes of an animated GIF fail on Navidrome; the screens never ask for them.
                val kind = AnimatedCovers.knownKind(id)
                if (kind != null && kind != AnimatedCovers.Kind.Still) break
                if (CoverStore.has(id)) break
            }
            val key = "cover:$id:$size"
            if (disk.openSnapshot(key)?.use { true } == true) continue
            when (fetch(graph, disk, id, size, key)) {
                null -> return Outcome.FAILED
                true -> outcome = Outcome.DOWNLOADED
                false -> Unit
            }
            // Animated at 300 px: find out what kind before deciding on the big size.
            if (size == SMALL_COVER && animatedSmall.remove(key)) AnimatedCovers.kind(graph, id)
        }
        return outcome
    }

    private val animatedSmall = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /**
     * Saves one size into the image cache. true = downloaded, false = nothing to save, null = failed.
     * Stopping the download cancels the request mid-file; the half-written entry is thrown away.
     */
    private suspend fun fetch(graph: AppGraph, disk: DiskCache, id: String, size: Int, key: String): Boolean? = runCatching {
        graph.imageHttp.newCall(Request.Builder().url(graph.source.coverUrl(id, size)).build()).executeCancellable { response ->
            if (!response.isSuccessful) {
                AppLog.w("Covers", "Cover $id ($size px): HTTP ${response.code}")
                return@executeCancellable if (response.code == 404) false else null
            }
            val body = response.body ?: return@executeCancellable null
            val head = response.peekBody(32).bytes()
            val image = CoverStore.sniff(head)
            if (image == null) {
                // Navidrome answers a missing cover with an error message, not a picture.
                return@executeCancellable false
            }
            if (image.animated) animatedSmall += key
            val editor = disk.openEditor(key) ?: return@executeCancellable false
            try {
                disk.fileSystem.write(editor.data) { writeAll(body.source()) }
                disk.fileSystem.write(editor.metadata) { }
                editor.commit()
            } catch (e: Exception) {
                editor.abort()
                throw e
            }
            true
        }
    }.onFailure {
        // Stopped: not a failure of this cover.
        currentCoroutineContext().ensureActive()
        AppLog.w("Covers", "Cover $id ($size px) failed: ${it.javaClass.simpleName}: ${it.message}")
    }.getOrNull()
}

/**
 * [Call.execute], but cancelling the coroutine cancels the request too, also while its body is being read
 * (issue #301). A blocking OkHttp call doesn't notice a cancelled coroutine: Stop used to wait until the cover
 * in hand had finished, ten minutes for a 130 MB animated one. Call from a thread pool (Dispatchers.IO), which
 * runs the watcher on another thread while this one waits on the network.
 */
internal suspend fun <T> Call.executeCancellable(read: (okhttp3.Response) -> T): T = coroutineScope {
    val call = this@executeCancellable
    val finished = java.util.concurrent.atomic.AtomicBoolean(false)
    // ATOMIC: even when cancelled before it starts, it still runs its finally and cancels the call.
    val watcher = launch(Dispatchers.Default, start = CoroutineStart.ATOMIC) {
        try {
            awaitCancellation()
        } finally {
            if (!finished.get()) call.cancel()
        }
    }
    try {
        call.execute().use(read)
    } catch (e: java.io.IOException) {
        // Cut off because it was stopped: say so, rather than as a network failure.
        ensureActive()
        throw e
    } finally {
        finished.set(true)
        watcher.cancel()
    }
}

class CoverDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as SiansaApp).graph
        if (CoverDownload.stoppedByUser && CoverDownload.DAILY !in tags) {
            AppLog.i("Covers", "Cover download not started: it was stopped from Settings")
            return Result.success()
        }
        return try {
            CoverDownload.runShared(applicationContext, graph, "on Wi-Fi, run ${runAttemptCount + 1}")
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Android stopped the worker; the download itself keeps going while the app runs.
            val reason = if (android.os.Build.VERSION.SDK_INT >= 31) " (reason $stopReason)" else ""
            AppLog.i("Covers", "Android stopped the cover download worker$reason; the download carries on while Siansa is running")
            throw e
        }
    }
}
