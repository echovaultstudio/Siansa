package app.siansa.net

import app.siansa.data.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** An artist or track and how often it was played, from a listening-history service. */
@kotlinx.serialization.Serializable
data class Played(val artist: String, val title: String? = null, val count: Long = 0)

/**
 * Reads what you've been listening to most from the services Siansa scrobbles to: Koito (its web API,
 * with the same API key used for scrobbling), ListenBrainz, or Last.fm. Read-only.
 */
class HistoryApi(private val http: OkHttpClient) {
    companion object {
        /** Listens per page, Koito's largest. */
        const val PAGE = 500
        /** Pages asked for at once (#592). */
        const val PARALLEL = 5
        /** More listens than any period is asked for; a year is about 10,000. */
        const val MAX_LISTENS = 50_000

        /** How many pages hold [expected] listens (none when it isn't known), at most [max] listens' worth. */
        fun listenPages(expected: Int?, max: Int = MAX_LISTENS, pageSize: Int = PAGE): Int {
            if (expected == null || expected <= 0) return 0
            return ((minOf(expected, max) + pageSize - 1) / pageSize).coerceAtLeast(1)
        }
    }


    /** Top artists and tracks from Koito or ListenBrainz, for the last month. */
    suspend fun listenServerTop(listenBrainzUrl: String, token: String): Pair<List<Played>, List<Played>> {
        val root = ListenBrainzApi.apiRoot(listenBrainzUrl)
        return if (root.contains("api.listenbrainz.org")) listenBrainzTop(root, token) else koitoTop(root, token)
    }

    private suspend fun koitoTop(apiRoot: String, token: String): Pair<List<Played>, List<Played>> {
        // https://koito.example/apis/listenbrainz/1 → https://koito.example/apis/web/v1
        val web = apiRoot.substringBefore("/apis/listenbrainz") + "/apis/web/v1"
        val artists = get("$web/top/artists?period=month&limit=50", token).arr("items").mapNotNull { row ->
            val item = row.obj("item") ?: return@mapNotNull null
            Played(item.str("name") ?: return@mapNotNull null, null, item.long("listen_count") ?: 0)
        }
        val tracks = get("$web/top/tracks?period=month&limit=100", token).arr("items").mapNotNull { row ->
            val item = row.obj("item") ?: return@mapNotNull null
            val artist = item.arr("artists").firstOrNull()?.str("name") ?: return@mapNotNull null
            Played(artist, item.str("title") ?: return@mapNotNull null, item.long("listen_count") ?: 0)
        }
        return artists to tracks
    }

    private suspend fun listenBrainzTop(apiRoot: String, token: String): Pair<List<Played>, List<Played>> {
        val user = get("$apiRoot/validate-token", token).str("user_name") ?: throw ApiException("ListenBrainz didn't name the user")
        val artists = get("$apiRoot/stats/user/$user/artists?range=month&count=50", token).obj("payload")?.arr("artists").orEmpty()
            .mapNotNull { a -> Played(a.str("artist_name") ?: return@mapNotNull null, null, a.long("listen_count") ?: 0) }
        val tracks = get("$apiRoot/stats/user/$user/recordings?range=month&count=100", token).obj("payload")?.arr("recordings").orEmpty()
            .mapNotNull { r ->
                Played(r.str("artist_name") ?: return@mapNotNull null, r.str("track_name") ?: return@mapNotNull null, r.long("listen_count") ?: 0)
            }
        return artists to tracks
    }

    /** True when the listen server is Koito (which has the richer web API below). */
    fun isKoito(listenBrainzUrl: String): Boolean = !ListenBrainzApi.apiRoot(listenBrainzUrl).contains("api.listenbrainz.org")

    private fun koitoWeb(listenBrainzUrl: String) =
        ListenBrainzApi.apiRoot(listenBrainzUrl).substringBefore("/apis/listenbrainz") + "/apis/web/v1"

    /** Totals for a time range from Koito's summary. */
    @kotlinx.serialization.Serializable
    data class Totals(val minutes: Int, val plays: Int, val artists: Int, val tracks: Int, val albums: Int, val newArtists: Int, val newTracks: Int)

    suspend fun koitoTotals(url: String, token: String, fromSec: Long, toSec: Long): Totals {
        val o = get("${koitoWeb(url)}/summary?from=$fromSec&to=$toSec", token)
        return Totals(
            minutes = o.int("minutes_listened") ?: 0,
            plays = o.int("plays") ?: 0,
            artists = o.int("unique_artists") ?: 0,
            tracks = o.int("unique_tracks") ?: 0,
            albums = o.int("unique_albums") ?: 0,
            newArtists = o.int("new_artists") ?: 0,
            newTracks = o.int("new_tracks") ?: 0,
        )
    }

    /** Koito's top artists / tracks / albums ([kind] = "artists", "tracks" or "albums") in a time range. */
    suspend fun koitoTop(url: String, token: String, kind: String, fromSec: Long, toSec: Long, limit: Int): List<Played> =
        get("${koitoWeb(url)}/top/$kind?from=$fromSec&to=$toSec&limit=$limit", token).arr("items").mapNotNull { row ->
            val item = row.obj("item") ?: return@mapNotNull null
            val count = item.long("listen_count") ?: 0
            when (kind) {
                "artists" -> Played(item.str("name") ?: return@mapNotNull null, null, count)
                else -> Played(item.arr("artists").firstOrNull()?.str("name").orEmpty(), item.str("title") ?: return@mapNotNull null, count)
            }
        }

    /**
     * When each listen in a time range happened (epoch seconds), up to [max] listens. With [expected] (the period's
     * play count from the summary) the pages are asked for [PARALLEL] at a time instead of one after the other: a
     * year of 10,000 plays is 20 pages, which took 4 s in a row (#592). Any pages beyond the expected count are
     * read one by one, as before, until the last.
     */
    suspend fun koitoListenTimes(url: String, token: String, fromSec: Long, toSec: Long, expected: Int? = null, max: Int = MAX_LISTENS): List<Long> {
        suspend fun page(n: Int): Pair<List<Long>, Boolean> {
            val o = get("${koitoWeb(url)}/listens?from=$fromSec&to=$toSec&limit=$PAGE&page=$n", token)
            val times = o.arr("items").mapNotNull { row ->
                row.str("time")?.let { t -> runCatching { java.time.OffsetDateTime.parse(t).toEpochSecond() }.getOrNull() }
            }
            return times to (o.bool("has_next_page") == true)
        }
        val times = ArrayList<Long>()
        var next = 1
        var more = true
        val known = listenPages(expected, max)
        if (known > 1) {
            val semaphore = kotlinx.coroutines.sync.Semaphore(PARALLEL)
            val pages = kotlinx.coroutines.coroutineScope {
                (1..known).map { n -> kotlinx.coroutines.async { semaphore.withPermit { page(n) } } }.map { it.await() }
            }
            pages.forEach { times += it.first }
            more = pages.last().second
            next = known + 1
        }
        while (more && times.size < max) {
            val (batch, hasNext) = page(next)
            times += batch
            more = hasNext
            next++
        }
        return times
    }

    /** Top artists and tracks on Last.fm for the last month (public data, only the API key is needed). */
    suspend fun lastFmTop(apiKey: String, user: String): Pair<List<Played>, List<Played>> {
        fun url(method: String) = "https://ws.audioscrobbler.com/2.0/".toHttpUrl().newBuilder()
            .addQueryParameter("method", method)
            .addQueryParameter("user", user)
            .addQueryParameter("period", "1month")
            .addQueryParameter("limit", "100")
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("format", "json")
            .build().toString()
        val artists = get(url("user.getTopArtists"), null).obj("topartists")?.arr("artist").orEmpty()
            .mapNotNull { a -> Played(a.str("name") ?: return@mapNotNull null, null, a.long("playcount") ?: 0) }
        val tracks = get(url("user.getTopTracks"), null).obj("toptracks")?.arr("track").orEmpty()
            .mapNotNull { t ->
                val artist = t.obj("artist")?.str("name") ?: return@mapNotNull null
                Played(artist, t.str("name") ?: return@mapNotNull null, t.long("playcount") ?: 0)
            }
        return artists to tracks
    }

    /**
     * Tracks you played on Last.fm between [fromSec] and [toSec] (epoch seconds), most played first, for
     * the time machine (issue #186). Public data: only the API key and user name are needed.
     */
    suspend fun lastFmTracks(apiKey: String, user: String, fromSec: Long, toSec: Long, limit: Int): List<Played> {
        val url = "https://ws.audioscrobbler.com/2.0/".toHttpUrl().newBuilder()
            .addQueryParameter("method", "user.getWeeklyTrackChart")
            .addQueryParameter("user", user)
            .addQueryParameter("from", fromSec.toString())
            .addQueryParameter("to", toSec.toString())
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("format", "json")
            .build().toString()
        return get(url, null).obj("weeklytrackchart")?.arr("track").orEmpty()
            .mapNotNull { t ->
                val artist = t.obj("artist")?.str("#text") ?: t.obj("artist")?.str("name") ?: return@mapNotNull null
                Played(artist, t.str("name") ?: return@mapNotNull null, t.long("playcount") ?: 0)
            }
            .sortedByDescending { it.count }
            .take(limit)
    }

    private suspend fun get(url: String, token: String?): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply { if (token != null) header("Authorization", "Token $token") }.get().build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException("HTTP ${response.code} from ${request.url.host}")
            runCatching { AppJson.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: throw ApiException("${request.url.host} didn't answer with JSON")
        }
    }
}
