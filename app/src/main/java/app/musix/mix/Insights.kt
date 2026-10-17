package app.musix.mix

import app.musix.AppGraph
import app.musix.data.Album
import app.musix.data.Artist
import app.musix.data.PlayEvent
import app.musix.data.Song
import app.musix.data.normalizeKey
import app.musix.log.AppLog
import app.musix.net.HistoryApi
import app.musix.net.Played
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** An album worth coming back to, and when it was last played. */
data class Rediscovery(val album: Album, val lastPlayed: LocalDate?, val plays: Long)

/** One period of listening, Spotify Wrapped style. */
data class ListeningReport(
    val title: String,
    val subtitle: String,
    val minutes: Int,
    val plays: Int,
    val artistCount: Int,
    val trackCount: Int,
    val newArtists: Int?,
    val topArtists: List<RankedArtist>,
    val topTracks: List<RankedTrack>,
    val topAlbums: List<RankedAlbum>,
    /** Plays per hour of the day, 0–23. */
    val byHour: IntArray,
    /** Artists climbing into your rotation compared with the period before, and ones that dropped out. */
    val rising: List<RankedArtist>,
    val fading: List<String>,
    /** Genres you played more and less than the period before (from Musix's own history). */
    val genresUp: List<String>,
    val genresDown: List<String>,
    val source: String,
) {
    val peakHour: Int? get() = byHour.indices.maxByOrNull { byHour[it] }?.takeIf { byHour[it] > 0 }
}

data class RankedArtist(val name: String, val plays: Long, val artist: Artist?)
data class RankedTrack(val title: String, val artist: String, val plays: Long, val song: Song?)
data class RankedAlbum(val title: String, val artist: String, val plays: Long, val album: Album?)

enum class ReportRange(val id: String) {
    THIS_MONTH("month"), LAST_MONTH("last-month"), THIS_YEAR("year"),

    /** This month a year ago, the whole month. */
    YEAR_AGO("year-ago");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: THIS_MONTH
    }
}

/**
 * Rediscover and Your month: built from Koito (when scrobbling there), Navidrome's play counts and
 * Musix's own history. Nothing here needs AudioMuse or AI.
 */
/**
 * The days a report covers ([from] inclusive, [to] exclusive), where the period before it starts (to
 * spot rising and fading artists), and its title.
 */
data class ReportWindow(val from: LocalDate, val to: LocalDate, val prevFrom: LocalDate, val title: String, val subtitle: String)

fun reportWindow(range: ReportRange, today: LocalDate): ReportWindow {
    fun monthName(date: LocalDate) = date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return when (range) {
        ReportRange.THIS_MONTH -> {
            val start = today.withDayOfMonth(1)
            ReportWindow(start, today.plusDays(1), start.minusMonths(1), "Your ${monthName(today)}", "So far this month")
        }
        ReportRange.LAST_MONTH -> {
            val start = today.withDayOfMonth(1).minusMonths(1)
            ReportWindow(start, start.plusMonths(1), start.minusMonths(1), "Your ${monthName(start)}", "${monthName(start)} ${start.year}")
        }
        ReportRange.THIS_YEAR -> {
            val start = today.withDayOfYear(1)
            ReportWindow(start, today.plusDays(1), start.minusYears(1), "Your ${today.year}", "So far this year")
        }
        ReportRange.YEAR_AGO -> {
            val start = today.withDayOfMonth(1).minusYears(1)
            ReportWindow(start, start.plusMonths(1), start.minusMonths(1), "This time last year", "${monthName(start)} ${start.year}")
        }
    }
}

/** How many top songs and albums are asked from Koito, to still have 10 after leaving out ones not in the library. */
private const val TOP_FETCH = 40

/**
 * The first [limit] of [items] (in order) that [find] matches in the library, each with its match.
 * Stops searching once it has enough. Items with no match are left out and logged as a count.
 */
internal suspend fun <T, R : Any> keepInLibrary(items: List<T>, limit: Int, find: suspend (T) -> R?): List<Pair<T, R>> {
    val kept = ArrayList<Pair<T, R>>(limit)
    var skipped = 0
    for (item in items) {
        if (kept.size >= limit) break
        val match = find(item)
        if (match != null) kept += item to match else skipped++
    }
    if (skipped > 0) AppLog.d("Report", "Left out $skipped not in the library")
    return kept
}

class Insights(private val graph: AppGraph) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    // ---------------- Rediscover ----------------

    /**
     * Albums you played a lot but not for at least five months: Navidrome's most-played albums with an
     * old "last played" date, plus Koito's favourites from 6–24 months ago that you haven't played since.
     */
    private var rediscoverCache: Pair<Long, List<Rediscovery>>? = null

    suspend fun rediscover(limit: Int = 20): List<Rediscovery> {
        rediscoverCache?.let { (at, list) -> if (System.currentTimeMillis() - at < 6 * 3_600_000L) return list }
        return findRediscoveries(limit).also { rediscoverCache = System.currentTimeMillis() to it }
    }

    private suspend fun findRediscoveries(limit: Int): List<Rediscovery> = coroutineScope {
        val s = graph.settings.current
        val now = Instant.now()
        val cutoff = now.minus(150, ChronoUnit.DAYS)
        val recentlyPlayedIds = graph.history.all().filter { !it.skipped }.map { it.id }.toSet()

        val navidrome = async {
            runCatching { graph.subsonic.albums("frequent", 500, 0) }
                .onFailure { AppLog.w("Rediscover", "Navidrome most-played failed: ${it.message}") }
                .getOrDefault(emptyList())
        }
        val koito = async {
            if (!s.listenBrainzReady || !graph.historyApi.isKoito(s.listenBrainzUrl)) return@async null
            runCatching {
                val old = graph.historyApi.koitoTop(
                    s.listenBrainzUrl, s.listenBrainzToken, "albums",
                    now.minus(730, ChronoUnit.DAYS).epochSecond, cutoff.epochSecond, 100,
                )
                val recent = graph.historyApi.koitoTop(
                    s.listenBrainzUrl, s.listenBrainzToken, "albums", cutoff.epochSecond, now.epochSecond, 500,
                ).map { key(it.artist, it.title.orEmpty()) }.toSet()
                old.filter { key(it.artist, it.title.orEmpty()) !in recent }
            }.onFailure { AppLog.w("Rediscover", "Koito history failed: ${it.message}") }.getOrNull()
        }

        val found = LinkedHashMap<String, Rediscovery>()
        // Navidrome: played at least 4 times, last played before the cutoff.
        navidrome.await()
            .filter { it.playCount >= 4 }
            .mapNotNull { album ->
                val last = album.played?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } ?: return@mapNotNull null
                if (last.isAfter(cutoff)) return@mapNotNull null
                val months = ChronoUnit.DAYS.between(last, now) / 30.0
                // Loved a lot, a while ago, but not so long that it's a different you.
                Triple(album, last, album.playCount * (if (months > 36) 0.6 else 1.0))
            }
            .sortedByDescending { it.third }
            .forEach { (album, last, _) ->
                val local = graph.library.album(album.id) ?: album
                found.putIfAbsent(key(local.artist, local.name), Rediscovery(local, last.atZone(zone).toLocalDate(), album.playCount))
            }
        // Koito: favourites from 6–24 months ago, matched to albums in the library.
        koito.await()?.forEach { played ->
            val title = played.title ?: return@forEach
            val k = key(played.artist, title)
            if (k in found) return@forEach
            val album = matchAlbum(played.artist, title) ?: return@forEach
            found[k] = Rediscovery(album, null, played.count)
        }
        // Not if you've played any of it lately in Musix.
        val result = found.values.filter { r ->
            runCatching { graph.library.songsOf(r.album) }.getOrDefault(emptyList()).none { it.id in recentlyPlayedIds }
        }.take(limit)
        AppLog.i("Rediscover", "${result.size} albums (Navidrome ${navidrome.await().size} checked, Koito ${koito.await()?.size ?: "off"})")
        result
    }

    // ---------------- Your month ----------------

    suspend fun report(range: ReportRange): ListeningReport = coroutineScope {
        val s = graph.settings.current
        val today = LocalDate.now()
        val (from, to, prevFrom, title, subtitle) = reportWindow(range, today)
        val fromSec = from.atStartOfDay(zone).toEpochSecond()
        val toSec = to.atStartOfDay(zone).toEpochSecond()
        val prevSec = prevFrom.atStartOfDay(zone).toEpochSecond()
        val history = graph.history.all()
        val inRange = history.filter { it.at / 1000 in fromSec until toSec && !it.skipped }
        val before = history.filter { it.at / 1000 in prevSec until fromSec && !it.skipped }

        val koito = s.listenBrainzReady && graph.historyApi.isKoito(s.listenBrainzUrl)
        val remote = if (koito) {
            runCatching { koitoReport(s.listenBrainzUrl, s.listenBrainzToken, fromSec, toSec, prevSec) }
                .onFailure { AppLog.w("Report", "Koito failed, using Musix history: ${it.message}") }
                .getOrNull()
        } else null

        val (genresUp, genresDown) = genreShift(inRange, before)
        AppLog.d("Report", "${range.id}: $from to $to")
        val report = if (remote != null) {
            ListeningReport(
                title = title, subtitle = subtitle,
                minutes = remote.totals.minutes, plays = remote.totals.plays,
                artistCount = remote.totals.artists, trackCount = remote.totals.tracks,
                newArtists = remote.totals.newArtists,
                topArtists = remote.artists.take(10).map { RankedArtist(it.artist, it.count, findArtist(it.artist)) },
                // Koito also counts plays from other apps and services: only songs and albums in your library are shown.
                topTracks = keepInLibrary(remote.tracks, 10) { matchSong(it.artist, it.title.orEmpty()) }
                    .map { (p, song) -> RankedTrack(p.title.orEmpty(), p.artist, p.count, song) },
                topAlbums = keepInLibrary(remote.albums, 10) { matchAlbum(it.artist, it.title.orEmpty()) }
                    .map { (p, album) -> RankedAlbum(p.title.orEmpty(), p.artist, p.count, album) },
                byHour = hours(remote.times.map { it * 1000 }),
                rising = rising(remote.artists, remote.previous).map { RankedArtist(it.artist, it.count, findArtist(it.artist)) },
                fading = fading(remote.artists, remote.previous),
                genresUp = genresUp, genresDown = genresDown,
                source = "From Koito, with genres from Musix",
            )
        } else {
            localReport(title, subtitle, inRange, before, genresUp, genresDown)
        }
        AppLog.i("Report", "${report.title}: ${report.plays} plays, ${report.minutes} min, top ${report.topArtists.take(3).map { it.name }} · ${report.source}")
        report
    }

    private class KoitoData(
        val totals: HistoryApi.Totals,
        val artists: List<Played>,
        val tracks: List<Played>,
        val albums: List<Played>,
        val previous: List<Played>,
        val times: List<Long>,
    )

    private suspend fun koitoReport(url: String, token: String, from: Long, to: Long, prev: Long): KoitoData = coroutineScope {
        val api = graph.historyApi
        val totals = async { api.koitoTotals(url, token, from, to) }
        val artists = async { api.koitoTop(url, token, "artists", from, to, 40) }
        // More than the 10 shown, as the ones not in your library are left out.
        val tracks = async { api.koitoTop(url, token, "tracks", from, to, TOP_FETCH) }
        val albums = async { api.koitoTop(url, token, "albums", from, to, TOP_FETCH) }
        val previous = async { api.koitoTop(url, token, "artists", prev, from, 40) }
        val times = async { api.koitoListenTimes(url, token, from, to) }
        KoitoData(totals.await(), artists.await(), tracks.await(), albums.await(), previous.await(), times.await())
    }

    /** The same report from Musix's own history, for when Koito isn't set up. */
    private suspend fun localReport(
        title: String, subtitle: String, plays: List<PlayEvent>, before: List<PlayEvent>,
        genresUp: List<String>, genresDown: List<String>,
    ): ListeningReport {
        val songs = graph.library.songs(plays.map { it.id }.distinct()).associateBy { it.id }
        val artists = plays.groupBy { normalizeKey(it.artist) }
            .map { (_, list) -> Played(list.first().artist, null, list.size.toLong()) }.sortedByDescending { it.count }
        val prevArtists = before.groupBy { normalizeKey(it.artist) }
            .map { (_, list) -> Played(list.first().artist, null, list.size.toLong()) }.sortedByDescending { it.count }
        // Songs since removed from the library are left out.
        val tracks = plays.groupBy { it.id }.entries.filter { songs[it.key] != null }.sortedByDescending { it.value.size }.take(10)
        val albums = plays.mapNotNull { p -> songs[p.id]?.albumId }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(10)
        val minutes = plays.sumOf { p -> p.listenedMs ?: ((songs[p.id]?.durationSec ?: 210) * 1000L) } / 60_000
        return ListeningReport(
            title = title, subtitle = subtitle,
            minutes = minutes.toInt(), plays = plays.size,
            artistCount = artists.size, trackCount = plays.map { it.id }.distinct().size,
            newArtists = null,
            topArtists = artists.take(10).map { RankedArtist(it.artist, it.count, findArtist(it.artist)) },
            topTracks = tracks.map { (id, list) ->
                RankedTrack(list.first().title, list.first().artist, list.size.toLong(), songs[id])
            },
            topAlbums = albums.mapNotNull { (albumId, n) ->
                graph.library.album(albumId)?.let { RankedAlbum(it.name, it.artist, n.toLong(), it) }
            },
            byHour = hours(plays.map { it.at }),
            rising = rising(artists, prevArtists).map { RankedArtist(it.artist, it.count, findArtist(it.artist)) },
            fading = fading(artists, prevArtists),
            genresUp = genresUp, genresDown = genresDown,
            source = "From Musix's own history (set up Koito under Scrobbling for the full picture)",
        )
    }

    private fun hours(timesMs: List<Long>): IntArray {
        val counts = IntArray(24)
        timesMs.forEach { counts[Instant.ofEpochMilli(it).atZone(zone).hour]++ }
        return counts
    }

    /** In this period's top 10 but not the previous period's top 25. */
    private fun rising(now: List<Played>, before: List<Played>): List<Played> {
        val earlier = before.take(25).map { normalizeKey(it.artist) }.toSet()
        return now.take(10).filter { normalizeKey(it.artist) !in earlier }.take(5)
    }

    /** In the previous period's top 10 but nowhere in this period's top 30. */
    private fun fading(now: List<Played>, before: List<Played>): List<String> {
        val current = now.take(30).map { normalizeKey(it.artist) }.toSet()
        return before.take(10).filter { normalizeKey(it.artist) !in current }.take(5).map { it.artist }
    }

    /** Genres whose share of your plays grew or shrank the most against the period before. */
    private fun genreShift(now: List<PlayEvent>, before: List<PlayEvent>): Pair<List<String>, List<String>> {
        fun shares(list: List<PlayEvent>): Map<String, Double> {
            val genres = list.mapNotNull { it.genre?.takeIf(String::isNotBlank) }
            if (genres.size < 10) return emptyMap()
            return genres.groupingBy { it }.eachCount().mapValues { it.value.toDouble() / genres.size }
        }
        val a = shares(now)
        val b = shares(before)
        if (a.isEmpty() || b.isEmpty()) return emptyList<String>() to emptyList()
        val change = (a.keys + b.keys).associateWith { (a[it] ?: 0.0) - (b[it] ?: 0.0) }
        return change.filter { it.value > 0.03 }.entries.sortedByDescending { it.value }.take(3).map { it.key } to
            change.filter { it.value < -0.03 }.entries.sortedBy { it.value }.take(3).map { it.key }
    }

    private suspend fun findArtist(name: String): Artist? = runCatching { graph.library.artistByName(name) }.getOrNull()

    private suspend fun matchAlbum(artist: String, title: String): Album? {
        if (!graph.library.isSynced || title.isBlank()) return null
        val a = normalizeKey(artist)
        return runCatching { graph.library.search(title).albums }.getOrDefault(emptyList()).firstOrNull { album ->
            normalizeKey(album.name) == normalizeKey(title) &&
                (a.isEmpty() || normalizeKey(album.artist).contains(a) || a.contains(normalizeKey(album.artist)))
        }
    }

    private suspend fun matchSong(artist: String, title: String): Song? {
        if (!graph.library.isSynced || title.isBlank()) return null
        val a = normalizeKey(artist)
        fun matches(song: Song) = normalizeKey(song.title) == normalizeKey(title) &&
            (a.isEmpty() || normalizeKey(song.artist).contains(a) || a.contains(normalizeKey(song.artist)))
        // Title and artist together first: a common title ("Ghost") alone has more hits than a search returns.
        val queries = listOfNotNull("$title $artist".takeIf { artist.isNotBlank() }, title)
        for (q in queries) {
            runCatching { graph.library.search(q).songs }.getOrDefault(emptyList()).firstOrNull(::matches)?.let { return it }
        }
        return null
    }

    private fun key(artist: String, title: String) = normalizeKey(artist) + "|" + normalizeKey(title)

}
