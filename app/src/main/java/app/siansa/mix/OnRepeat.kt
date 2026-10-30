package app.siansa.mix

import android.content.Context
import app.siansa.data.JsonFile
import app.siansa.data.Song
import app.siansa.data.Users
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.math.ln

/** A song as the server last counted it for this user: how often it was played, and when last (issue #495). */
data class SongPlays(val song: Song, val playCount: Long, val playedAt: Long?)

/** A song's play count on one day, as Siansa saw it on the server. */
@Serializable
data class CountPoint(val day: Long, val count: Long)

/**
 * On repeat (issue #495): the songs played most in the last [WINDOW_DAYS] days, from the server, from this phone and
 * from Koito / ListenBrainz / Last.fm. Navidrome only knows all-time play counts, so Siansa notes each song's count
 * per day as it sees it; the count 30 days ago taken from today's gives the plays in between.
 */
object OnRepeat {
    const val WINDOW_DAYS = 30
    const val MIN_SONGS = 8
    const val MAX_SONGS = 50
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** [points] with today's [count] added, when it changed; points older than [keepDays] go, but the last before them stays. */
    fun addPoint(points: List<CountPoint>, day: Long, count: Long, keepDays: Int = WINDOW_DAYS + 15): List<CountPoint> {
        val last = points.lastOrNull()
        val added = when {
            last == null || last.count != count && last.day < day -> points + CountPoint(day, count)
            last.day == day && last.count != count -> points.dropLast(1) + CountPoint(day, count)
            else -> points
        }
        val cutoff = day - keepDays
        val older = added.filter { it.day < cutoff }
        return (listOfNotNull(older.lastOrNull()) + added.filter { it.day >= cutoff })
    }

    /** The count on [day]: the last point on or before it, or null when Siansa hadn't seen the song by then. */
    fun countOn(points: List<CountPoint>, day: Long): Long? = points.lastOrNull { it.day <= day }?.count

    /**
     * How much a song from the server counts towards On repeat: its plays in the window when Siansa knows its count
     * from back then; otherwise a little more than one play for having been played in the window at all, more for a
     * song played a lot overall. Null when it wasn't played in the window.
     */
    fun serverScore(plays: SongPlays, countBefore: Long?, now: Long): Double? {
        val at = plays.playedAt ?: return null
        if (now - at > WINDOW_DAYS * DAY_MS || plays.playCount <= 0) return null
        return if (countBefore != null) (plays.playCount - countBefore).coerceAtLeast(1).toDouble()
        else 1.0 + 0.5 * ln(plays.playCount.toDouble())
    }

    /**
     * Song ids, most played first: the highest of what the server ([server], id → plays in the window), this phone
     * ([phone], id → plays in the window) and the history services ([ranked], best first) say. Siansa's own plays
     * reach the server too, so the sources aren't added up. [skipped] songs are left out.
     */
    fun rank(server: Map<String, Double>, phone: Map<String, Int>, ranked: List<String>, skipped: Set<String>): List<String> {
        val score = HashMap<String, Double>()
        fun put(id: String, value: Double) { if (value > (score[id] ?: 0.0)) score[id] = value }
        server.forEach { (id, v) -> put(id, v) }
        phone.forEach { (id, n) -> put(id, n.toDouble()) }
        // The services list a month's tops; the first counts as a few plays, the last as one.
        ranked.forEachIndexed { i, id -> put(id, 1.0 + 3.0 * (ranked.size - i) / ranked.size) }
        return score.entries.filter { it.key !in skipped }
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .map { it.key }.take(MAX_SONGS)
    }
}

/**
 * Each song's play count per day as seen on the server (issue #495), for the plays in On repeat's window. Only
 * changes are kept, and only for about six weeks, so it stays small.
 */
class PlayCountLog(context: Context) {
    private val json = JsonFile(
        File(context.filesDir, Users.scoped("play_counts.json")),
        MapSerializer(String.serializer(), ListSerializer(CountPoint.serializer())),
    ) { emptyMap() }

    /** Notes today's counts of [plays]; songs never played aren't kept. */
    suspend fun record(plays: List<SongPlays>, today: Long = LocalDate.now().toEpochDay()) {
        if (plays.isEmpty()) return
        json.update { all ->
            val next = HashMap(all)
            plays.forEach { p -> if (p.playCount > 0) next[p.song.id] = OnRepeat.addPoint(all[p.song.id].orEmpty(), today, p.playCount) }
            // Songs not seen for longer than the window are dropped.
            next.filterValues { points -> points.isNotEmpty() && points.last().day >= today - OnRepeat.WINDOW_DAYS - 15 }
        }
    }

    /** Each song's latest count and the day it changed to it (custom cards' play rules, issue #678). */
    suspend fun latest(): Map<String, ServerPlays> =
        json.read().mapNotNull { (id, points) -> ServerPlays.of(points)?.let { id to it } }.toMap()

    /** Each song's count on [day] (an epoch day), for the songs Siansa had seen by then. */
    suspend fun countsOn(day: Long): Map<String, Long> =
        json.read().mapNotNull { (id, points) -> OnRepeat.countOn(points, day)?.let { id to it } }.toMap()
}
