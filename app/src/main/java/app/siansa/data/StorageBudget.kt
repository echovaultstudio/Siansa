package app.siansa.data

import android.content.Context
import android.os.StatFs
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import app.siansa.SiansaApp
import app.siansa.covers.CoverStore
import app.siansa.log.AppLog
import java.io.File

/**
 * How much the TV app keeps on the TV, in megabytes: songs as they play ([songsMb]), covers ([picturesMb]) and
 * animated covers ([animatedMb]).
 *
 * The TV app took the phone's sizes, 2 GB of songs and 500 MB of covers, with no page on the TV to change them, and
 * kept every animated cover it had played (17 to 163 MB each, 1.7 GB of them in the logs of three evenings) for good.
 * An NVIDIA SHIELD has 8 or 16 GB for everything and warned that its storage was low. A TV plays from a server in
 * the same house, so it needs little kept: about 1 GB at most here, and less when the TV has little room, so that
 * [KEEP_FREE_MB] stays free for the TV itself.
 */
data class StorageBudget(val songsMb: Int, val picturesMb: Int, val animatedMb: Int) {
    val totalMb: Int get() = songsMb + picturesMb + animatedMb

    companion object {
        const val SONGS_MB = 512
        const val PICTURES_MB = 200
        const val ANIMATED_MB = 300

        /** What Siansa leaves free on the TV: twice what Android calls low (500 MB at most). */
        const val KEEP_FREE_MB = 1024

        /** The least each cache works with: the song playing and the next loaded ahead, and a few screens of covers. */
        const val SONGS_LEAST_MB = 100
        const val PICTURES_LEAST_MB = 50

        /**
         * The TV's budget with [freeMb] free and [heldMb] already kept by Siansa (which would be free without it):
         * all of it when that leaves [KEEP_FREE_MB] free, the same share of each when it doesn't, down to the least.
         */
        fun forTv(freeMb: Long, heldMb: Long): StorageBudget {
            val room = freeMb + heldMb - KEEP_FREE_MB
            val share = (room.toDouble() / (SONGS_MB + PICTURES_MB + ANIMATED_MB)).coerceIn(0.0, 1.0)
            val songs = (SONGS_MB * share).toInt().coerceAtLeast(SONGS_LEAST_MB)
            val pictures = (PICTURES_MB * share).toInt().coerceAtLeast(PICTURES_LEAST_MB)
            // Animated covers are the one thing the app does without: they get only what the other two leave.
            val animated = (ANIMATED_MB * share).toLong().coerceAtMost(room - songs - pictures).coerceAtLeast(0L).toInt()
            return StorageBudget(songs, pictures, animated)
        }

        @Volatile private var tv: StorageBudget? = null

        /** This TV's budget, worked out once from its free room when the app starts. */
        fun ofTv(context: Context): StorageBudget = tv ?: synchronized(this) {
            tv ?: forTv(TvStorage.freeMb(context), TvStorage.held(context).let { (it.songs + it.pictures + it.animated) / MB }).also { tv = it }
        }

        private const val MB = 1024L * 1024L
    }
}

/**
 * The entries used longest ago to remove so that what's left is at most [maxBytes]. [keep] (the one just written or
 * playing) is never among them, even when it alone is more.
 */
fun <T> oldestToDrop(entries: List<T>, maxBytes: Long, usedAt: (T) -> Long, bytes: (T) -> Long, keep: T? = null): List<T> {
    var held = entries.sumOf(bytes)
    val drop = ArrayList<T>()
    for (entry in entries.sortedBy(usedAt)) {
        if (held <= maxBytes) break
        if (entry == keep) continue
        drop += entry
        held -= bytes(entry)
    }
    return drop
}

/** What the TV app has on the TV now, and bringing it down to its [StorageBudget] when the app starts. */
object TvStorage {
    private const val MB = 1024L * 1024L

    /** Bytes kept: songs, covers, animated covers, the library and the logs. */
    data class Held(val songs: Long, val pictures: Long, val animated: Long, val library: Long, val logs: Long)

    private fun size(file: File): Long = runCatching { file.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)

    fun held(context: Context): Held = Held(
        songs = size(File(context.cacheDir, "song_cache")),
        pictures = size(SiansaApp.imageCacheDir(context, unlimited = false)) + size(SiansaApp.imageCacheDir(context, unlimited = true)),
        animated = size(File(context.filesDir, "covers")),
        library = context.databaseList().sumOf { context.getDatabasePath(it).length() },
        logs = size(File(context.filesDir, "logs")),
    )

    fun freeMb(context: Context): Long = runCatching { StatFs(context.filesDir.path).availableBytes / MB }.getOrDefault(Long.MAX_VALUE / MB)

    private fun totalMb(context: Context): Long = runCatching { StatFs(context.filesDir.path).totalBytes / MB }.getOrDefault(0L)

    /** One line for the log: what Siansa keeps against what it may, and the TV's free room. */
    fun line(held: Held, budget: StorageBudget, freeMb: Long, totalMb: Long): String =
        "Storage: songs ${held.songs / MB} of ${budget.songsMb} MB, covers ${held.pictures / MB} of ${budget.picturesMb} MB, " +
            "animated covers ${held.animated / MB} of ${budget.animatedMb} MB, library ${held.library / MB} MB, logs ${held.logs / MB} MB · " +
            "TV free $freeMb of $totalMb MB"

    /**
     * Removes the songs played longest ago from the song [cache] until it holds at most [maxBytes]. The cache itself
     * only makes room when a song is written, so after its size is lowered it stayed too big until the next song.
     */
    @OptIn(UnstableApi::class)
    fun trimSongs(cache: Cache, maxBytes: Long): Long {
        val spans = cache.keys.flatMap { key -> cache.getCachedSpans(key) }
        val drop = oldestToDrop(spans, maxBytes, { it.lastTouchTimestamp }, { it.length })
        drop.forEach { span -> runCatching { cache.removeSpan(span) } }
        return drop.sumOf { it.length }
    }

    /**
     * When the TV app starts (off the main thread): says what's kept, then brings the songs and the animated covers
     * down to the budget. Covers follow by themselves as the next ones are written.
     */
    @OptIn(UnstableApi::class)
    fun tidy(context: Context, songCache: () -> Cache) {
        val budget = StorageBudget.ofTv(context)
        val before = held(context)
        AppLog.i("Storage", line(before, budget, freeMb(context), totalMb(context)), event = "storage.kept", fields = mapOf(
            "songs_mb" to before.songs / MB, "covers_mb" to before.pictures / MB, "animated_mb" to before.animated / MB,
            "budget_mb" to budget.totalMb, "free_mb" to freeMb(context),
        ))
        val animated = CoverStore.prune()
        val songs = if (before.songs > budget.songsMb * MB) trimSongs(songCache(), budget.songsMb * MB) else 0L
        if (animated > 0 || songs > 0) {
            AppLog.i("Storage", "Made room on the TV: ${songs / MB} MB of songs and ${animated / MB} MB of animated covers removed, the ones played longest ago. " +
                line(held(context), budget, freeMb(context), totalMb(context)))
        }
    }
}
