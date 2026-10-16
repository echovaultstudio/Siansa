package app.musix.covers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.musix.AppGraph
import app.musix.MusixApp
import app.musix.data.AppSettings
import app.musix.log.AppLog
import app.musix.ui.AnimatedCovers
import app.musix.ui.LARGE_COVER
import app.musix.ui.SMALL_COVER
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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
    private const val DAILY = "cover-download-daily"

    private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()

    /** Runs now (or as soon as the phone is on Wi-Fi). */
    fun start(context: Context) {
        val request = OneTimeWorkRequestBuilder<CoverDownloadWorker>().setConstraints(constraints()).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }

    /** Daily runs on, or off, to match the setting. */
    fun schedule(context: Context, settings: AppSettings) {
        val work = WorkManager.getInstance(context)
        if (!settings.coverDownload) {
            work.cancelUniqueWork(DAILY)
            return
        }
        val request = PeriodicWorkRequestBuilder<CoverDownloadWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build())
            .build()
        work.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NOW)
    }

    /** Why covers can't be downloaded on this network right now, or null when they can. */
    fun whyNot(context: Context, settings: AppSettings): String? {
        if (!settings.navidromeReady) return "Navidrome isn't set up"
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "No network information"
        // Wi-Fi or Ethernet, even when a VPN is on top of it.
        val local = cm.allNetworks.mapNotNull { n -> cm.getNetworkCapabilities(n)?.let { n to it } }.filter { (_, caps) ->
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        if (local.isEmpty()) return "Not on Wi-Fi"
        if (settings.coverDownloadAnyWifi) return null
        val host = settings.navidromeUrl.toHttpUrlOrNull()?.host ?: return "Navidrome's address isn't valid"
        val addresses = runCatching { InetAddress.getAllByName(host).toList() }.getOrElse { return "Couldn't look up $host" }
        val home = local.any { (network, _) ->
            val link = cm.getLinkProperties(network) ?: return@any false
            link.linkAddresses.any { la -> addresses.any { sameSubnet(la.address, la.prefixLength, it) } }
        }
        return if (home) null else "Not on the same network as Navidrome ($host)"
    }

    private fun sameSubnet(mine: InetAddress, prefix: Int, other: InetAddress): Boolean {
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
        val ids = buildList {
            graph.library.albums("name").forEach { it.coverArt?.let(::add) }
            graph.library.artists().forEach { add(it.coverArt ?: "ar-${it.id}") }
            graph.library.playlists().forEach { it.coverArt?.let(::add) }
        }.distinctBy { CoverStore.canonical(it) }
        AppLog.i("Covers", "Downloading covers: ${ids.size} albums, artists and playlists to check")
        update { Progress(running = true, total = ids.size) }
        val started = System.currentTimeMillis()
        val downloaded = AtomicInteger()
        val failed = AtomicInteger()
        val done = AtomicInteger()
        val permits = Semaphore(4)
        var stoppedBecause: String? = null
        coroutineScope {
            for ((index, id) in ids.withIndex()) {
                // Left home or Wi-Fi: stop, and pick up where it left off next time.
                if (index % 50 == 49) whyNot(context, graph.settings.current)?.let { stoppedBecause = it }
                if (stoppedBecause != null) break
                permits.acquire()
                launch {
                    try {
                        when (one(graph, disk, id)) {
                            Outcome.DOWNLOADED -> downloaded.incrementAndGet()
                            Outcome.FAILED -> failed.incrementAndGet()
                            Outcome.HAD -> Unit
                        }
                    } finally {
                        permits.release()
                        val n = done.incrementAndGet()
                        update { it.copy(done = n, downloaded = downloaded.get(), failed = failed.get()) }
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

    private suspend fun one(graph: AppGraph, disk: DiskCache, id: String): Outcome {
        // An animated original kept on the phone answers every size already.
        if (CoverStore.has(id)) return Outcome.HAD
        var outcome = Outcome.HAD
        for (size in listOf(SMALL_COVER, LARGE_COVER)) {
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

    /** Saves one size into the image cache. true = downloaded, false = nothing to save, null = failed. */
    private fun fetch(graph: AppGraph, disk: DiskCache, id: String, size: Int, key: String): Boolean? = runCatching {
        graph.imageHttp.newCall(Request.Builder().url(graph.subsonic.coverUrl(id, size)).build()).execute().use { response ->
            if (!response.isSuccessful) {
                AppLog.d("Covers", "Cover $id ($size px): HTTP ${response.code}")
                return@runCatching if (response.code == 404) false else null
            }
            val body = response.body ?: return@runCatching null
            val head = response.peekBody(32).bytes()
            val image = CoverStore.sniff(head)
            if (image == null) {
                // Navidrome answers a missing cover with an error message, not a picture.
                return@runCatching false
            }
            if (image.animated) animatedSmall += key
            val editor = disk.openEditor(key) ?: return@runCatching false
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
    }.onFailure { AppLog.d("Covers", "Cover $id ($size px) failed: ${it.message}") }.getOrNull()
}

class CoverDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as MusixApp).graph
        return try {
            CoverDownload.run(applicationContext, graph)
            Result.success()
        } catch (e: Exception) {
            AppLog.e("Covers", "Cover download failed", e)
            CoverDownload.update { it.copy(running = false, note = "Failed: ${e.message}") }
            Result.success()
        } finally {
            CoverDownload.update { it.copy(running = false) }
        }
    }
}
