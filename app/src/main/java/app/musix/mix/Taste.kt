package app.musix.mix

import app.musix.AppGraph
import app.musix.data.PlayEvent
import app.musix.data.normalizeKey
import app.musix.log.AppLog
import app.musix.net.Played
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * What the listener likes, built on the phone from:
 * - Musix's own history (what was played, at what time of day, and what was skipped),
 * - Koito or ListenBrainz top artists and tracks this month (when scrobbling there is set up),
 * - Last.fm top artists and tracks this month (when signed in),
 * - Navidrome's most-played albums.
 * Only small summaries leave the phone (a few artist names in the AI prompt), never the history itself.
 */
data class TasteProfile(
    /** Artist (normalised name) → how much they're played lately, 0..1. */
    val artistWeight: Map<String, Double>,
    /** Favourite artists, most played first, as display names. */
    val topArtists: List<String>,
    /** Artists and genres usually played around this time of day. */
    val hourArtists: List<String>,
    val hourGenres: List<String>,
    /** Songs skipped more than once lately, or skipped in the last few days. */
    val skipped: Set<String>,
    /** Most-played songs lately, as Navidrome song ids. */
    val favouriteSongs: List<String>,
    val sources: List<String>,
    val builtAt: Long,
    val hour: Int,
) {
    fun affinity(artist: String): Double = artistWeight[normalizeKey(artist)] ?: 0.0
}

class Taste(private val graph: AppGraph) {
    private val lock = Mutex()
    private var cached: TasteProfile? = null

    /** The profile, rebuilt every few hours or when the hour of day has moved on. */
    suspend fun profile(force: Boolean = false): TasteProfile = lock.withLock {
        val hour = LocalTime.now().hour
        cached?.let { c ->
            if (!force && c.hour == hour && System.currentTimeMillis() - c.builtAt < 3 * HOUR_MS) return@withLock c
        }
        build(hour).also {
            cached = it
            AppLog.i(
                "Taste",
                "From ${it.sources.joinToString(", ").ifEmpty { "nothing yet" }} · top artists ${it.topArtists.take(5)} · " +
                    "at this hour ${it.hourArtists.take(3)} ${it.hourGenres.take(3)} · ${it.skipped.size} skipped songs",
            )
        }
    }

    private suspend fun build(hour: Int): TasteProfile = coroutineScope {
        val s = graph.settings.current
        val now = System.currentTimeMillis()
        val history = graph.history.all()
        val sources = ArrayList<String>()

        val remote = async {
            if (!s.listenBrainzReady) return@async null
            runCatching { graph.historyApi.listenServerTop(s.listenBrainzUrl, s.listenBrainzToken) }
                .onFailure { AppLog.w("Taste", "Listen history from ${s.listenBrainzUrl.ifBlank { "ListenBrainz" }} failed: ${it.message}") }
                .getOrNull()
        }
        val lastFm = async {
            if (s.lastFmApiKey.isBlank() || s.lastFmUser.isBlank()) return@async null
            runCatching { graph.historyApi.lastFmTop(s.lastFmApiKey, s.lastFmUser) }
                .onFailure { AppLog.w("Taste", "Last.fm history failed: ${it.message}") }
                .getOrNull()
        }
        val navidrome = async {
            runCatching { graph.subsonic.albums("frequent", 40, 0) }
                .onFailure { AppLog.w("Taste", "Navidrome most-played failed: ${it.message}") }
                .getOrNull()
        }

        // Local plays: finished plays count fully, fading over about two months; skips don't count.
        val plays = history.filter { !it.skipped }
        val local = HashMap<String, Pair<String, Double>>()
        plays.forEach { p ->
            val days = (now - p.at) / DAY_MS.toDouble()
            val weight = exp(-days / 45.0)
            val key = normalizeKey(p.artist)
            local[key] = (local[key]?.first ?: p.artist) to ((local[key]?.second ?: 0.0) + weight)
        }
        if (plays.isNotEmpty()) sources += "Musix history"

        val weights = HashMap<String, Double>()
        val names = HashMap<String, String>()
        fun addSource(counts: Map<String, Pair<String, Double>>, share: Double) {
            val max = counts.values.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: return
            counts.forEach { (key, value) ->
                weights[key] = (weights[key] ?: 0.0) + share * value.second / max
                names.putIfAbsent(key, value.first)
            }
        }
        fun List<Played>.byArtist() = groupBy { normalizeKey(it.artist) }
            .mapValues { (_, rows) -> rows.first().artist to rows.sumOf { it.count }.toDouble() }

        addSource(local, 1.0)
        remote.await()?.let { (artists, _) ->
            if (artists.isNotEmpty()) sources += if (s.listenBrainzUrl.contains("listenbrainz.org") || s.listenBrainzUrl.isBlank()) "ListenBrainz" else "Koito"
            addSource(artists.byArtist(), 0.9)
        }
        lastFm.await()?.let { (artists, _) ->
            if (artists.isNotEmpty()) sources += "Last.fm"
            addSource(artists.byArtist(), 0.7)
        }
        navidrome.await()?.let { albums ->
            if (albums.isNotEmpty()) sources += "Navidrome play counts"
            addSource(albums.groupBy { normalizeKey(it.artist) }.mapValues { (_, a) -> a.first().artist to a.sumOf { it.playCount }.toDouble() }, 0.5)
        }
        val top = weights.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1.0
        val artistWeight = weights.mapValues { it.value / top }
        val topArtists = artistWeight.entries.sortedByDescending { it.value }.take(15).mapNotNull { names[it.key] }

        // Habits around this time of day (two hours either side), over the last three months.
        val nearHour = plays.filter { now - it.at < 90 * DAY_MS && hourDistance(hourOf(it), hour) <= 2 }
        val hourArtists = nearHour.groupingBy { it.artist }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key }
        val hourGenres = nearHour.mapNotNull { it.genre?.takeIf(String::isNotBlank) }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key }

        // Skips: twice in a month, or once in the last four days.
        val skips = history.filter { it.skipped && now - it.at < 30 * DAY_MS }
        val skipped = skips.groupingBy { it.id }.eachCount().filter { it.value >= 2 }.keys +
            skips.filter { now - it.at < 4 * DAY_MS }.map { it.id }

        // Favourite songs: Musix's own counts, plus top tracks from Koito / ListenBrainz / Last.fm found in the library.
        val localFavourites = plays.filter { now - it.at < 60 * DAY_MS }
            .groupingBy { it.id }.eachCount().entries.filter { it.value >= 2 }
            .sortedByDescending { it.value }.map { it.key }
        val remoteTracks = (remote.await()?.second.orEmpty() + lastFm.await()?.second.orEmpty())
            .sortedByDescending { it.count }.take(40)
        val matched = matchTracks(remoteTracks)
        val favouriteSongs = (localFavourites + matched).distinct().take(80)

        TasteProfile(
            artistWeight = artistWeight,
            topArtists = topArtists,
            hourArtists = hourArtists,
            hourGenres = hourGenres,
            skipped = skipped.toSet(),
            favouriteSongs = favouriteSongs,
            sources = sources,
            builtAt = now,
            hour = hour,
        )
    }

    /** Finds "artist – title" from a history service in the synced library. */
    private suspend fun matchTracks(tracks: List<Played>): List<String> {
        if (!graph.library.isSynced) return emptyList()
        return tracks.mapNotNull { played ->
            val title = played.title ?: return@mapNotNull null
            val artist = normalizeKey(played.artist)
            runCatching { graph.library.search(title).songs }.getOrDefault(emptyList())
                .firstOrNull { song ->
                    normalizeKey(song.title) == normalizeKey(title) &&
                        (normalizeKey(song.artist).contains(artist) || artist.contains(normalizeKey(song.artist)))
                }?.id
        }
    }

    private fun hourOf(event: PlayEvent): Int = Instant.ofEpochMilli(event.at).atZone(ZoneId.systemDefault()).hour

    private fun hourDistance(a: Int, b: Int): Int = min(abs(a - b), 24 - abs(a - b))

    private companion object {
        const val HOUR_MS = 60L * 60 * 1000
        const val DAY_MS = 24 * HOUR_MS
    }
}
