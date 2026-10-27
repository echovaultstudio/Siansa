package app.siansa.mix

import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.builtins.serializer
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.Flow
import app.siansa.AppGraph
import app.siansa.data.Album
import app.siansa.data.Artist
import app.siansa.data.PlayEvent
import app.siansa.data.Song
import app.siansa.data.normalizeKey
import app.siansa.log.AppLog
import app.siansa.net.HistoryApi
import app.siansa.net.Played
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withLock
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
    /** Genres you played more and less than the period before (from Siansa's own history). */
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
 * Siansa's own history. Nothing here needs AudioMuse or AI.
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

/**
 * The report window for a time machine month or year (#578): that month or year, up to today for one still going,
 * compared with the month or year before it, titled like the time machine ("May 2025", "2025").
 */
fun timeMachineReportWindow(year: Int, month: Int?, today: LocalDate): ReportWindow {
    val w = timeWindow(year, month)
    val to = if (w.to.isAfter(today)) today.plusDays(1) else w.to
    val prevFrom = if (month == null) w.from.minusYears(1) else w.from.minusMonths(1)
    return ReportWindow(w.from, to, prevFrom, w.label, if (to != w.to) "So far" else w.label)
}

/** How long the current period's Koito data counts as fresh (#592); finished periods are kept for good. */
const val REPORT_FRESH_MS = 15 * 60_000L

/** The key a report window's Koito data is kept under: the server and the three instants that define the window. */
internal fun reportKey(serverUrl: String, fromSec: Long, toSec: Long, prevSec: Long): String =
    "${serverUrl.trimEnd('/')}|$fromSec|$toSec|$prevSec"

/** Whether [window] has ended by [today]: a finished month or year, whose data can't change any more (#592). */
internal fun reportIsFinished(window: ReportWindow, today: LocalDate): Boolean = !window.to.isAfter(today)

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
    private companion object {
        const val KOITO = "Koito"
        const val LAST_FM = "Last.fm"
    }

    private val zone: ZoneId get() = ZoneId.systemDefault()

    // ---------------- Rediscover ----------------

    /**
     * Albums you played a lot but not for at least five months: Navidrome's most-played albums with an
     * old "last played" date, plus Koito's favourites from 6–24 months ago that you haven't played since.
     */
    private var rediscoverCache: Pair<Long, List<Rediscovery>>? = null

    suspend fun rediscover(limit: Int = 20): List<Rediscovery> {
        val cached = rediscoverCache?.takeIf { (at, _) -> System.currentTimeMillis() - at < 6 * 3_600_000L }?.second
        val list = cached ?: findRediscoveries(limit).also { rediscoverCache = System.currentTimeMillis() to it }
        // With "Only downloaded music" on, only albums on the phone.
        val only = graph.library.onlyDownloaded() ?: return list
        return list.filter { only.albums(listOf(it.album)).isNotEmpty() }
    }

    private suspend fun findRediscoveries(limit: Int): List<Rediscovery> = coroutineScope {
        val s = graph.settings.current
        val now = Instant.now()
        val cutoff = now.minus(150, ChronoUnit.DAYS)
        val recentlyPlayedIds = graph.history.all().filter { !it.skipped }.map { it.id }.toSet()

        val navidrome = async {
            runCatching { graph.source.albums("frequent", 500, 0) }
                .onFailure { AppLog.w("Rediscover", "Navidrome most-played failed: ${it.message}") }
                .getOrDefault(emptyList())
                .let { graph.library.keepSyncedAlbums(it, "Rediscover") }
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
        // Not if you've played any of it lately in Siansa.
        val result = found.values.filter { r ->
            runCatching { graph.library.songsOf(r.album) }.getOrDefault(emptyList()).none { it.id in recentlyPlayedIds }
        }.take(limit)
        AppLog.i("Rediscover", "${result.size} albums (Navidrome ${navidrome.await().size} checked, Koito ${koito.await()?.size ?: "off"})")
        result
    }

    // ---------------- Your month ----------------

    suspend fun report(range: ReportRange): ListeningReport = report(reportWindow(range, LocalDate.now()), range.id)

    /** The report for any window: Your month's ranges, or a time machine month or year (#578). */
    suspend fun report(window: ReportWindow, tag: String): ListeningReport = reportUpdates(window, tag).last()

    /**
     * The report for [window] as it comes in (#592): what the phone kept first, then a fresh one when that's stale.
     * A finished month or year never changes, so its Koito data is kept on the phone for good and the report shows
     * at once; the current one is asked again after [REPORT_FRESH_MS], the kept one showing meanwhile so the page
     * isn't blank. The library may change in between, so the plays are matched against it again each time, from
     * the song and album indexes (a few ms).
     */
    fun reportUpdates(window: ReportWindow, tag: String): Flow<ListeningReport> = channelFlow {
        val s = graph.settings.current
        val (from, to, prevFrom, title, subtitle) = window
        val fromSec = from.atStartOfDay(zone).toEpochSecond()
        val toSec = to.atStartOfDay(zone).toEpochSecond()
        val prevSec = prevFrom.atStartOfDay(zone).toEpochSecond()
        val history = graph.history.all()
        val inRange = history.filter { it.at / 1000 in fromSec until toSec && !it.skipped }
        val before = history.filter { it.at / 1000 in prevSec until fromSec && !it.skipped }
        val (genresUp, genresDown) = genreShift(inRange, before)
        AppLog.d("Report", "$tag: $from to $to")

        suspend fun build(remote: KoitoData?): ListeningReport {
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
                    source = "From Koito, with genres from Siansa",
                )
            } else {
                localReport(title, subtitle, inRange, before, genresUp, genresDown)
            }
            AppLog.i("Report", "${report.title}: ${report.plays} plays, ${report.minutes} min, top ${report.topArtists.take(3).map { it.name }} · ${report.source}")
            return report
        }

        val koito = s.listenBrainzReady && graph.historyApi.isKoito(s.listenBrainzUrl)
        if (!koito) {
            send(build(null))
            return@channelFlow
        }
        val key = reportKey(s.listenBrainzUrl, fromSec, toSec, prevSec)
        val finished = reportIsFinished(window, LocalDate.now(zone))
        val kept = keptReport(key, finished)
        if (kept != null) {
            AppLog.d("Report", "$tag: Koito data from the phone${if (kept.fresh) "" else ", asking again"}")
            send(build(kept.data))
            if (kept.fresh) return@channelFlow
        }
        val started = System.currentTimeMillis()
        val remote = runCatching { koitoReport(s.listenBrainzUrl, s.listenBrainzToken, fromSec, toSec, prevSec) }
            .onFailure { AppLog.w("Report", "Koito failed${if (kept != null) ", keeping what the phone had" else ", using Siansa history"}: ${it.message}") }
            .getOrNull()
        if (remote == null) {
            if (kept == null) send(build(null))
            return@channelFlow
        }
        AppLog.d("Report", "$tag: Koito answered in ${System.currentTimeMillis() - started}ms (${remote.times.size} listens)")
        keepReport(key, finished, remote)
        send(build(remote))
    }

    /** A kept report's Koito data, and whether it's fresh enough to show without asking Koito again. */
    private class Kept(val data: KoitoData, val fresh: Boolean)

    // Koito's data per report window (#592). Finished windows never change, so they're kept on the phone for good;
    // the current one for REPORT_FRESH_MS, then shown while a fresh one is fetched.
    private val recentReports = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, KoitoData>>()
    private val keptReports = app.siansa.data.JsonFile(
        java.io.File(graph.app.filesDir, app.siansa.data.Users.scoped("report_koito.json")),
        kotlinx.serialization.builtins.MapSerializer(String.serializer(), KoitoData.serializer()),
    ) { emptyMap() }

    private suspend fun keptReport(key: String, finished: Boolean): Kept? {
        if (finished) keptReports.read()[key]?.let { return Kept(it, fresh = true) }
        val recent = recentReports[key] ?: return null
        return Kept(recent.second, fresh = System.currentTimeMillis() - recent.first < REPORT_FRESH_MS)
    }

    private suspend fun keepReport(key: String, finished: Boolean, data: KoitoData) {
        if (finished) keptReports.update { it + (key to data) } else recentReports[key] = System.currentTimeMillis() to data
    }

    @kotlinx.serialization.Serializable
    internal class KoitoData(
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
        // The listens, every page at once (#592), once the summary has said how many there are.
        val times = async { api.koitoListenTimes(url, token, from, to, expected = totals.await().plays) }
        KoitoData(totals.await(), artists.await(), tracks.await(), albums.await(), previous.await(), times.await())
    }

    /** The same report from Siansa's own history, for when Koito isn't set up. */
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
            source = "From Siansa's own history (set up Koito under Scrobbling for the full picture)",
        )
    }

    // ---------------- Time machine (issue #186) ----------------

    /** Every song by title, built once per library version ([SongIndex]). */
    private var cachedIndex: Pair<Int, SongIndex>? = null
    private val indexLock = kotlinx.coroutines.sync.Mutex()

    private suspend fun songIndex(): SongIndex = indexLock.withLock {
        val version = graph.library.version.value
        cachedIndex?.takeIf { it.first == version }?.second ?: run {
            val started = System.currentTimeMillis()
            val index = SongIndex(graph.library.songTitles().map { SongIndex.Entry(it.id, it.title, it.artist) })
            AppLog.d("TimeMachine", "Song index: ${index.size} songs in ${System.currentTimeMillis() - started}ms")
            cachedIndex = version to index
            index
        }
    }

    /** Every album by name, built once per library version, for a report's top albums (#592). */
    private var cachedAlbums: Pair<Int, Map<String, List<Album>>>? = null

    private suspend fun albumIndex(): Map<String, List<Album>> = indexLock.withLock {
        val version = graph.library.version.value
        cachedAlbums?.takeIf { it.first == version }?.second ?: run {
            val index = graph.library.albums("name").groupBy { normalizeKey(it.name) }.filterKeys { it.isNotEmpty() }
            cachedAlbums = version to index
            index
        }
    }

    /** Builds the song index in the background once the library is loaded, so the time machine needn't (issue #319). */
    fun warmUp() {
        graph.scope.launch {
            // Again after each change to the library, a moment after it settles.
            graph.library.version.collectLatest {
                kotlinx.coroutines.delay(2_000)
                runCatching { songIndex() }
            }
        }
    }

    // Plays per service and period (issue #319). Finished months and years are kept on the phone for good: they
    // never change. The current one is asked again after 15 minutes. The library may change in between; the plays
    // are matched against it again each time, which takes a few ms with the index built.
    private val recentPlays = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<Played>>>()
    private val keptPlays = app.siansa.data.JsonFile(
        java.io.File(graph.app.filesDir, app.siansa.data.Users.scoped("time_machine_plays.json")),
        kotlinx.serialization.builtins.MapSerializer(String.serializer(), kotlinx.serialization.builtins.ListSerializer(Played.serializer())),
    ) { emptyMap() }

    private fun playsKey(source: String, year: Int, month: Int?) = "$source|$year-${month ?: "all"}"

    private suspend fun kept(source: String, year: Int, month: Int?, finished: Boolean): List<Played>? {
        val key = playsKey(source, year, month)
        if (finished) keptPlays.read()[key]?.let { return it }
        return recentPlays[key]?.takeIf { System.currentTimeMillis() - it.first < 15 * 60_000L }?.second
    }

    private suspend fun keep(source: String, year: Int, month: Int?, finished: Boolean, plays: List<Played>) {
        val key = playsKey(source, year, month)
        if (finished) keptPlays.update { it + (key to plays) } else recentPlays[key] = System.currentTimeMillis() to plays
    }

    /** The finished list for [month] of [year]; [timeMachineUpdates] shows it as it comes in. */
    suspend fun timeMachine(year: Int, month: Int?, limit: Int = 50): TimeMachine = timeMachineUpdates(year, month, limit).last()

    /**
     * Your most played songs in [month] of [year] (or the whole year when [month] is null), as they come in
     * (issue #319): what's already on the phone and the first service to answer show at once, then each further
     * service's plays are added (the higher count for each song). From Koito and Last.fm, whichever are set up,
     * else Siansa's own history. Songs not in your library are listed without a song (issue #234), up to [limit] in it.
     */
    fun timeMachineUpdates(year: Int, month: Int?, limit: Int = 50): Flow<TimeMachine> = channelFlow {
        val started = System.currentTimeMillis()
        val s = graph.settings.current
        val window = timeWindow(year, month)
        val fromSec = window.from.atStartOfDay(zone).toEpochSecond()
        val toSec = window.to.atStartOfDay(zone).toEpochSecond()
        // More than shown, as songs not in your library are left out.
        val fetch = limit * 3
        val koitoOn = s.listenBrainzReady && graph.historyApi.isKoito(s.listenBrainzUrl)
        val lastFmOn = s.lastFmEnabled && s.lastFmApiKey.isNotBlank() && s.lastFmUser.isNotBlank()
        val sources = listOfNotNull(KOITO.takeIf { koitoOn }, LAST_FM.takeIf { lastFmOn })
        val finished = periodFinished(window, LocalDate.now())
        val times = java.util.concurrent.ConcurrentHashMap<String, String>()
        if (sources.isEmpty()) {
            send(fromHistory(window, fromSec, toSec, limit, "Set up Koito or Last.fm under Scrobbling for your full history", complete = true))
            return@channelFlow
        }
        val got = java.util.concurrent.ConcurrentHashMap<String, List<Played>>()
        val failed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        sources.forEach { src -> kept(src, year, month, finished)?.let { got[src] = it; times[src] = "from the phone" } }
        val emitLock = kotlinx.coroutines.sync.Mutex()
        suspend fun show(final: Boolean) = emitLock.withLock {
            val used = sources.filter { got.containsKey(it) }
            val waiting = if (final) emptyList() else sources.filter { !got.containsKey(it) && it !in failed }
            if (used.isEmpty()) {
                if (final) send(fromHistory(window, fromSec, toSec, limit, "Koito and Last.fm couldn't be reached", complete = false))
                return@withLock
            }
            send(matched(window, mergePlays(*used.map { got.getValue(it) }.toTypedArray()), limit, used, waiting, complete = failed.isEmpty()))
        }
        val missing = sources.filter { !got.containsKey(it) }
        if (missing.isNotEmpty()) {
            // What's already on the phone first.
            if (got.isNotEmpty()) show(final = false)
            coroutineScope {
                missing.forEach { src ->
                    launch {
                        val askStarted = System.currentTimeMillis()
                        val plays = runCatching {
                            if (src == KOITO) graph.historyApi.koitoTop(s.listenBrainzUrl, s.listenBrainzToken, "tracks", fromSec, toSec, fetch)
                            else graph.historyApi.lastFmTracks(s.lastFmApiKey, s.lastFmUser, fromSec, toSec, fetch)
                        }.onFailure { AppLog.w("TimeMachine", "$src failed: ${it.message}") }.getOrNull()
                        times[src] = "${System.currentTimeMillis() - askStarted}ms"
                        if (plays == null) failed += src else {
                            got[src] = plays
                            keep(src, year, month, finished, plays)
                        }
                        // Another service still on its way: show this one's plays now.
                        if (plays != null && missing.any { !got.containsKey(it) && it !in failed }) show(final = false)
                    }
                }
            }
        }
        show(final = true)
        AppLog.i(
            "TimeMachine",
            "${window.label} in ${System.currentTimeMillis() - started}ms · " + sources.joinToString { "$it ${times[it] ?: "-"}" } +
                if (failed.isNotEmpty()) " · failed: ${failed.joinToString()}" else "",
        )
    }

    /** [plays] matched against the library: the songs in it, and the ones not (issue #234). */
    private suspend fun matched(window: TimeWindow, plays: List<Played>, limit: Int, used: List<String>, pending: List<String>, complete: Boolean): TimeMachine {
        val started = System.currentTimeMillis()
        // All of them against one index of the library, then the matched songs in one read.
        val index = songIndex()
        val only = graph.library.onlyDownloaded()
        val allowed = only?.shelf?.songs
        // With "downloaded only" on, songs in the library but not on the phone are left out, not marked missing.
        val shown = if (allowed == null) plays else plays.filter { p -> index.find(p.artist, p.title.orEmpty())?.let { it in allowed } ?: true }
        val seen = HashSet<String>()
        val ranked = rankWithMissing(shown, limit) { p -> index.find(p.artist, p.title.orEmpty()) }
            .filter { (_, id) -> id == null || seen.add(id) }
        val songs = graph.library.songs(ranked.mapNotNull { it.second }).associateBy { it.id }
        val tracks = ranked.mapNotNull { (p, id) ->
            val song = id?.let { songs[it] ?: return@mapNotNull null }
            RankedTrack(p.title.orEmpty(), p.artist, p.count, song)
        }
        val tm = TimeMachine(window.label, tracks, timeMachineSource(used, pending), complete = complete, pending = pending)
        AppLog.d("TimeMachine", "${window.label}: ${tm.tracks.size - tm.missing} songs in the library, ${tm.missing} not, matched in ${System.currentTimeMillis() - started}ms · ${tm.source}")
        return tm
    }

    /** From Siansa's own history, when Koito and Last.fm aren't set up or couldn't be reached. */
    private suspend fun fromHistory(window: TimeWindow, fromSec: Long, toSec: Long, limit: Int, why: String, complete: Boolean): TimeMachine {
        val events = graph.history.all().filter { it.at / 1000 in fromSec until toSec && !it.skipped }
        val songs = graph.library.songs(events.map { it.id }.distinct()).associateBy { it.id }
        val tracks = events.groupBy { it.id }.entries.filter { songs[it.key] != null }
            .sortedByDescending { it.value.size }.take(limit)
            .map { (id, list) -> RankedTrack(list.first().title, list.first().artist, list.size.toLong(), songs[id]) }
        return TimeMachine(window.label, tracks, "From Siansa's own history. $why", complete = complete)
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

    /** The library's album with this title by this artist, from the album index (#592: one query per album before). */
    private suspend fun matchAlbum(artist: String, title: String): Album? {
        if (!graph.library.isSynced || title.isBlank()) return null
        val a = normalizeKey(artist)
        return albumIndex()[normalizeKey(title)]?.firstOrNull { album ->
            a.isEmpty() || normalizeKey(album.artist).contains(a) || a.contains(normalizeKey(album.artist))
        }
    }

    /** The library's song with this title by this artist, from the song index (#592: two searches per song before). */
    private suspend fun matchSong(artist: String, title: String): Song? {
        if (!graph.library.isSynced || title.isBlank()) return null
        val id = songIndex().find(artist, title) ?: return null
        return runCatching { graph.library.song(id) }.getOrNull()
    }

    private fun key(artist: String, title: String) = normalizeKey(artist) + "|" + normalizeKey(title)

}
