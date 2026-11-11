package app.siansa.covers

import android.content.Context
import app.siansa.AppGraph
import app.siansa.data.picture
import app.siansa.log.AppLog
import app.siansa.net.SourceKind
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sync all also refreshes the covers kept on the phone (the owner, 11 Nov), so a cover that was missing or has
 * changed on the server shows without clearing the whole picture cache:
 *
 * 1. **Missing**: every kept picture that is the server's stand-in ([StandIns]: Navidrome's blue disc and artist's
 *    star, kept by versions before this one) is removed, and fetched fresh at its next showing. Nothing is asked of
 *    the server for this, so it also runs once by itself after updating.
 * 2. **Changed**: most cover ids carry their version (Navidrome's `al-<id>_<last change>`), so a changed cover is a
 *    new id and comes by itself; Emby's and Jellyfin's are followed by their picture's tag as the library is read
 *    ([CoverVersions.seen]). What's left are Navidrome ids without a version (`al-<id>`, `ar-<id>`): for each one kept,
 *    Navidrome is asked "still this one?" with the version it gave (`If-None-Match`); it answers without the picture
 *    when it is (HTTP 304, checked on Navidrome 0.64.2), and the new picture replaces the kept one when it isn't.
 * 3. Stand-ins showing right now are asked for again at their next showing.
 */
object CoverRefresh {
    /** At least this many albums or artists with the very same picture: taken for a stand-in this app doesn't know yet. */
    const val SHARED_BY = 3

    private const val GIVE_UP_AFTER = 5

    /** A cover on the phone: its id, the size it was asked at, its disk key and how big it is. */
    data class Kept(val id: String, val size: String, val key: String, val bytes: Long)

    data class Result(val kept: Int, val standIns: Int, val asked: Int, val changed: Int) {
        fun describe() = "$kept kept cover(s) checked: $standIns stand-in(s) removed" +
            (if (asked > 0) ", $asked without a version asked about, $changed changed" else "")
    }

    private val running = AtomicBoolean(false)

    /**
     * Checks the kept covers; [askServer]: also step 2 (Sync all). Null when another check is running or there's no
     * cover cache.
     */
    suspend fun run(context: Context, graph: AppGraph, askServer: Boolean): Result? = withContext(Dispatchers.IO) {
        if (!running.compareAndSet(false, true)) return@withContext null
        try {
            val loader = SingletonImageLoader.get(context)
            val disk = loader.diskCache ?: return@withContext null
            val kept = kept(disk, coverIds(graph))
            val subsonic = graph.source.kind == SourceKind.OPENSUBSONIC
            val standIns = standInsAmong(kept, shared = subsonic) { sha(disk, it.key) }
            standIns.forEach { drop(loader, disk, it) }
            if (standIns.isNotEmpty()) {
                AppLog.i("Covers", "Removed ${standIns.size} kept stand-in picture(s), e.g. ${standIns.take(3).joinToString { "${it.id} (${it.size})" }}")
            }
            var asked = 0
            var changed = 0
            if (askServer && subsonic && graph.settings.current.navidromeReady) {
                val unversioned = (kept - standIns.toSet()).filter { CoverEtags.unversioned(CoverCache.cacheIdOf(it.key)) }
                asked = unversioned.size
                changed = askAbout(graph, loader, disk, unversioned)
            }
            StandIns.askAllAgain().forEach { CoverCache.forgetInMemory(loader, it) }
            Result(kept.size, standIns.size, asked, changed).also { AppLog.i("Covers", "Cover refresh: ${it.describe()}") }
        } finally {
            running.set(false)
        }
    }

    /** Every cover id the library shows, with the versionless ones the app makes itself (`al-<album>`, `ar-<artist>`). */
    private suspend fun coverIds(graph: AppGraph): List<String> = buildList {
        graph.library.albums("name").forEach { album ->
            album.coverArt?.let(::add)
            add("al-${album.id}")
        }
        val fresh = graph.artistPhotos.covers.value
        graph.library.artists().forEach { artist ->
            add(fresh[artist.id] ?: artist.picture)
            artist.coverArt?.let(::add)
            add("ar-${artist.id}")
        }
        graph.library.playlists().forEach { it.coverArt?.let(::add) }
        addAll(graph.library.songCoverIds().filter { it.isNotEmpty() })
    }.distinct()

    private fun kept(disk: DiskCache, ids: List<String>): List<Kept> = buildList {
        for (id in ids) for (size in CoverCache.SIZES) {
            val key = "cover:${CoverVersions.cacheId(id)}:$size"
            val bytes = runCatching { disk.openSnapshot(key)?.use { disk.fileSystem.metadata(it.data).size } }.getOrNull() ?: continue
            add(Kept(id, size, key, bytes))
        }
    }

    private fun sha(disk: DiskCache, key: String): String = runCatching {
        disk.openSnapshot(key)?.use { snapshot -> StandIns.sha256(disk.fileSystem.read(snapshot.data) { readByteArray() }) }
    }.getOrNull().orEmpty()

    private fun drop(loader: coil3.ImageLoader, disk: DiskCache, kept: Kept) {
        runCatching { disk.remove(kept.key) }
        CoverEtags.remove(kept.key)
        CoverCache.forgetInMemory(loader, CoverCache.cacheIdOf(kept.key))
    }

    /**
     * The stand-ins among [kept]: the ones known by their fingerprint, and ([shared], Navidrome only) any picture
     * that [SHARED_BY] or more different albums or artists have byte for byte, which is what a stand-in of a version
     * this app doesn't know looks like. Removing a real cover by mistake only costs fetching it again. [sha] is only
     * asked for pictures whose size makes them candidates.
     */
    internal fun standInsAmong(kept: List<Kept>, shared: Boolean, sha: (Kept) -> String): List<Kept> {
        val hashes = HashMap<String, String>()
        fun hash(k: Kept) = hashes.getOrPut(k.key) { sha(k) }
        val found = LinkedHashSet<Kept>()
        val sizes = StandIns.sizes()
        kept.filter { it.bytes in sizes }.forEach { k -> if (StandIns.known(k.bytes) { hash(k) }) found += k }
        if (shared) {
            fun owners(group: List<Kept>) = group.mapTo(HashSet()) { CoverStore.canonical(it.id) }.size
            kept.filter { it !in found && (it.id.startsWith("al-") || it.id.startsWith("ar-")) }
                .groupBy { it.bytes }.values
                .filter { owners(it) >= SHARED_BY }
                .forEach { sameSize ->
                    sameSize.groupBy { hash(it) }
                        .filter { (sha, same) -> sha.isNotEmpty() && owners(same) >= SHARED_BY }
                        .values.forEach { found += it }
                }
        }
        return found.toList()
    }

    /** Asks Navidrome about each kept cover without a version; returns how many changed. */
    private suspend fun askAbout(graph: AppGraph, loader: coil3.ImageLoader, disk: DiskCache, kept: List<Kept>): Int {
        val changed = AtomicInteger()
        val failedInARow = AtomicInteger()
        val permits = Semaphore(4)
        // Left only when every cover asked about has its answer.
        coroutineScope {
            for (k in kept) {
                launch {
                    permits.withPermit {
                        // The server stopped answering: the rest are left for the next Sync all.
                        if (failedInARow.get() >= GIVE_UP_AFTER) return@withPermit
                        when (askOne(graph, disk, k)) {
                            Answer.CHANGED -> {
                                failedInARow.set(0)
                                changed.incrementAndGet()
                                CoverCache.forgetInMemory(loader, CoverCache.cacheIdOf(k.key))
                            }
                            Answer.SAME -> failedInARow.set(0)
                            Answer.FAILED -> failedInARow.incrementAndGet()
                        }
                    }
                }
            }
        }
        if (failedInARow.get() >= GIVE_UP_AFTER) {
            AppLog.w("Covers", "Cover refresh stopped asking: the server didn't answer $GIVE_UP_AFTER times in a row")
        }
        return changed.get()
    }

    private enum class Answer { SAME, CHANGED, FAILED }

    private suspend fun askOne(graph: AppGraph, disk: DiskCache, k: Kept): Answer = runCatching {
        val request = Request.Builder().url(graph.source.coverUrl(k.id, k.size.toIntOrNull()))
        CoverEtags.get(k.key)?.let { request.header("If-None-Match", it) }
        graph.imageHttp.newCall(request.build()).executeCancellable { response ->
            when (whatToDo(response.code, StandIns.isStandIn(response))) {
                Step.KEEP -> Answer.SAME
                Step.FAIL -> Answer.FAILED
                Step.REMOVE -> {
                    // The picture is gone from the server: its stand-in is shown from now on, and never kept.
                    runCatching { disk.remove(k.key) }
                    CoverEtags.remove(k.key)
                    Answer.CHANGED
                }
                Step.COMPARE -> {
                    val bytes = response.body?.bytes() ?: return@executeCancellable Answer.FAILED
                    if (CoverStore.sniff(bytes.copyOf(minOf(bytes.size, 32))) == null) return@executeCancellable Answer.SAME
                    val same = bytes.size.toLong() == k.bytes && StandIns.sha256(bytes) == sha(disk, k.key)
                    if (!same) {
                        val editor = disk.openEditor(k.key) ?: return@executeCancellable Answer.FAILED
                        try {
                            disk.fileSystem.write(editor.data) { write(bytes) }
                            disk.fileSystem.write(editor.metadata) { }
                            editor.commit()
                        } catch (e: Exception) {
                            editor.abort()
                            throw e
                        }
                    }
                    response.header("ETag")?.let { CoverEtags.put(k.key, it) }
                    if (same) Answer.SAME else Answer.CHANGED
                }
            }
        }
    }.onFailure {
        if (it is kotlinx.coroutines.CancellationException) throw it
        AppLog.d("Covers", "Cover ${k.id} (${k.size}) couldn't be asked about: ${it.javaClass.simpleName}: ${it.message}")
    }.getOrDefault(Answer.FAILED)

    internal enum class Step { KEEP, REMOVE, COMPARE, FAIL }

    /** What an answer about a kept cover means: HTTP [code], and whether it's the server's stand-in. */
    internal fun whatToDo(code: Int, standIn: Boolean): Step = when {
        code == 304 -> Step.KEEP
        code in 200..299 && standIn -> Step.REMOVE
        code in 200..299 -> Step.COMPARE
        code == 404 -> Step.KEEP // not this server's any more (another user's music): left as it is
        else -> Step.FAIL
    }
}
