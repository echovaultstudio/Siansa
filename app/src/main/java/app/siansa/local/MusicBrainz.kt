package app.siansa.local

import app.siansa.data.normalizeKey
import app.siansa.log.AppLog
import app.siansa.net.ApiException
import app.siansa.net.arr
import app.siansa.net.int
import app.siansa.net.obj
import app.siansa.net.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** A recording MusicBrainz found for a file (issue #352): its ID and its artists' IDs, in credit order. */
data class MbRecording(val id: String, val title: String, val lengthSec: Int?, val artistIds: List<String>, val artistNames: List<String>)

/**
 * MusicBrainz's web service (issue #352), for files on the phone whose tags have no MusicBrainz IDs: their
 * recording by artist and title (the length picks between versions), and an artist by name.
 *
 * MusicBrainz asks for at most one request a second and a User-Agent naming the app and a contact
 * (https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting): both are kept here, for every caller.
 */
class MusicBrainzApi(
    private val http: OkHttpClient,
    private val userAgent: String,
    private val base: String = "https://musicbrainz.org/ws/2/",
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val gate = Mutex()
    private var last = 0L

    suspend fun recording(artist: String, title: String, lengthSec: Int): MbRecording? {
        val query = "recording:\"${escape(title)}\" AND artist:\"${escape(artist)}\""
        val root = get("recording", query, 10)
        return pickRecording(root, artist, title, lengthSec)
    }

    /** The artist's MusicBrainz ID when exactly one artist has this name, else null. */
    suspend fun artistId(name: String): String? = pickArtist(get("artist", "artist:\"${escape(name)}\"", 5), name)

    private suspend fun get(entity: String, query: String, limit: Int): JsonObject = gate.withLock {
        val wait = last + MIN_GAP_MS - now()
        if (wait > 0) delay(wait)
        last = now()
        val url = (base + entity).toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("fmt", "json")
            .addQueryParameter("limit", limit.toString())
            .build()
        withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(url).header("User-Agent", userAgent).header("Accept", "application/json").build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (r.code == 503) {
                    // Too fast or too busy: the next request waits longer.
                    last = now() + BUSY_BACK_OFF_MS
                    throw ApiException("MusicBrainz is busy (HTTP 503)")
                }
                if (!r.isSuccessful) throw ApiException("MusicBrainz answered HTTP ${r.code}")
                JSON.parseToJsonElement(body) as? JsonObject ?: throw ApiException("MusicBrainz sent an unexpected answer")
            }
        }
    }

    companion object {
        const val MIN_GAP_MS = 1_100L
        const val BUSY_BACK_OFF_MS = 10_000L
        /** How far a recording's length may be from the file's and still be it. */
        const val LENGTH_SLACK_SEC = 7
        const val MIN_SCORE = 80
        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        /** "Siansa/0.52.0 ( https://siansa.app )", as MusicBrainz asks. */
        fun userAgent(version: String): String = "Siansa/$version ( https://siansa.app )"

        /** Lucene's special characters escaped, for a phrase in a search. */
        fun escape(text: String): String = text.replace(Regex("""([+\-&|!(){}\[\]^"~*?:\\/])"""), "\\\\$1")

        /**
         * Of a recording search's answer, the one that is this file: the same title and first artist (by name,
         * accents and case aside), a good score, and the length nearest the file's (within [LENGTH_SLACK_SEC] when
         * both are known). Null when none is sure.
         */
        fun pickRecording(root: JsonObject, artist: String, title: String, lengthSec: Int): MbRecording? {
            val wantTitle = normalizeKey(title)
            val wantArtist = normalizeKey(artist)
            val candidates = root.arr("recordings").mapNotNull { r ->
                val id = r.str("id") ?: return@mapNotNull null
                val score = r.int("score") ?: 0
                val credits = r.arr("artist-credit")
                val names = credits.mapNotNull { it.obj("artist")?.str("name") ?: it.str("name") }
                val credited = credits.joinToString("") { (it.str("name") ?: it.obj("artist")?.str("name").orEmpty()) + it.str("joinphrase").orEmpty() }
                val length = r.int("length")?.let { (it + 500) / 1000 }
                val sameTitle = normalizeKey(r.str("title").orEmpty()) == wantTitle
                val sameArtist = normalizeKey(credited) == wantArtist || names.any { normalizeKey(it) == wantArtist } ||
                    names.firstOrNull()?.let { wantArtist.startsWith(normalizeKey(it)) } == true
                val lengthOk = length == null || lengthSec <= 0 || kotlin.math.abs(length - lengthSec) <= LENGTH_SLACK_SEC
                if (score < MIN_SCORE || !sameTitle || !sameArtist || !lengthOk) return@mapNotNull null
                MbRecording(id, r.str("title").orEmpty(), length, credits.mapNotNull { it.obj("artist")?.str("id") }, names)
            }
            return candidates.minByOrNull { c -> if (c.lengthSec != null && lengthSec > 0) kotlin.math.abs(c.lengthSec - lengthSec) else LENGTH_SLACK_SEC + 1 }
        }

        /** Of an artist search's answer, the ID of the only artist with exactly this name and a full score. */
        fun pickArtist(root: JsonObject, name: String): String? {
            val want = normalizeKey(name)
            val same = root.arr("artists").filter { normalizeKey(it.str("name").orEmpty()) == want && (it.int("score") ?: 0) >= 95 }
            return same.singleOrNull()?.str("id")
        }
    }
}

/**
 * Fills in MusicBrainz IDs for files whose tags have none (issue #352), one request a second, in the background.
 * Each file is looked up once ([LocalTrack.mbLooked]); answers are also kept by artist and title, so the same song
 * in two folders costs one request.
 */
class MusicBrainzLookup(private val api: MusicBrainzApi) {
    private val recordings = HashMap<String, MbRecording?>()
    private val artists = HashMap<String, String?>()

    /** [t] with what MusicBrainz knows, marked as looked up. Throws when MusicBrainz can't be reached. */
    suspend fun lookUp(t: LocalTrack): LocalTrack {
        var recordingId = t.recordingMbid
        var artistIds = t.artistMbids
        if (recordingId == null) {
            val key = normalizeKey(t.artist) + "|" + normalizeKey(t.title) + "|" + t.durationSec / 5
            val found = if (recordings.containsKey(key)) recordings[key] else api.recording(t.artist, t.title, t.durationSec).also { recordings[key] = it }
            if (found != null) {
                recordingId = found.id
                if (artistIds.isEmpty()) artistIds = found.artistIds
            }
        }
        if (artistIds.isEmpty() && t.artists.size == 1) {
            val key = normalizeKey(t.artists.first())
            val id = if (artists.containsKey(key)) artists[key] else api.artistId(t.artists.first()).also { artists[key] = it }
            if (id != null) artistIds = listOf(id)
        }
        AppLog.d("MusicBrainz", "${t.artist} – ${t.title}: recording ${recordingId ?: "not found"}, ${artistIds.size} artist ID(s)")
        return t.copy(
            mbLooked = true,
            mbRecording = recordingId.takeIf { t.recordingMbid == null },
            mbArtists = artistIds.takeIf { t.artistMbids.isEmpty() }.orEmpty(),
        )
    }
}
