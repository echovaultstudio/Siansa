package app.musix.net

import app.musix.data.AppJson
import app.musix.data.AppSettings
import app.musix.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import okhttp3.RequestBody.Companion.toRequestBody

/** One group of steering concepts (Genre, Instrument, Vocals, Mood, Production, Tempo, Era), best-understood first. */
data class ConceptCategory(val id: String, val label: String, val terms: List<String>)

/** All the concepts the Sound search can be steered by, and how many can be used at once. */
data class ConceptCatalogue(val categories: List<ConceptCategory>, val maxTerms: Int)

/** AudioMuse's analysis of one song: tempo (BPM), key, and its mood and genre scores (0..1). */
data class TrackFeatures(
    val id: String,
    val artist: String,
    val tempo: Double?,
    val key: String?,
    val scale: String?,
    val moods: Map<String, Double>,
    val genres: Map<String, Double>,
    /** AudioMuse's energy for the song (higher is more energetic), when it sends one (issue #339). */
    val energy: Double? = null,
)

/**
 * One concept to steer the Sound search with (issue #208): [more] or less of it. [weight] is how strongly
 * (AudioMuse takes 1, 2, 3, 5 or 10, and uses 3 when none is sent); null leaves it to AudioMuse (issue #339).
 */
@kotlinx.serialization.Serializable
data class Steer(val term: String, val more: Boolean = true, val weight: Int? = null)

/** One of AudioMuse's mood centres: [mood] (happy, relaxed…), its [index], and the tags that describe it. */
data class MoodCentre(val mood: String, val index: Int, val tags: List<String>)

/** The end of a song path that isn't a song: one of AudioMuse's moods (issue #208). */
enum class PathMood(val id: String, val label: String) {
    HAPPY("happy", "Happy"),
    RELAXED("relaxed", "Relaxed"),
    SAD("sad", "Sad"),
    DANCEABLE("danceable", "Danceable"),
    AGGRESSIVE("aggressive", "Aggressive"),
}

/** A track AudioMuse matched to a text description. For a single server, id is the Navidrome song id. */
data class ClapHit(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val similarity: Double,
)

/**
 * AudioMuse-AI's own API: text-to-audio (CLAP) search and its concept vocabulary.
 * Signs in with the API token when one is set, otherwise with the AudioMuse username and password.
 */
class AudioMuseApi(
    private val http: OkHttpClient,
    private val settings: () -> AppSettings,
) {
    private val loginLock = Mutex()
    private var session: Pair<String, String>? = null // (login used, JWT)

    private suspend fun request(path: String): Request.Builder {
        val s = settings()
        if (s.audioMuseUrl.isBlank()) throw ApiException("AudioMuse isn't set up yet")
        val builder = Request.Builder().url(s.audioMuseUrl + path)
        when {
            s.audioMuseToken.isNotBlank() -> builder.header("Authorization", "Bearer ${s.audioMuseToken}")
            s.audioMuseUser.isNotBlank() -> builder.header("Cookie", "audiomuse_jwt=${login(s, force = false)}")
        }
        return builder
    }

    private suspend fun login(s: AppSettings, force: Boolean): String = loginLock.withLock {
        val key = s.audioMuseUrl + "|" + s.audioMuseUser + "|" + s.audioMusePassword
        session?.let { if (!force && it.first == key) return@withLock it.second }
        val jwt = withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("user", s.audioMuseUser)
                put("password", s.audioMusePassword)
            }.toString()
            val request = Request.Builder()
                .url(s.audioMuseUrl + "/auth")
                .header("X-Requested-With", "XMLHttpRequest")
                .post(payload.toRequestBody(JSON_TYPE))
                .build()
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> Unit
                    401 -> throw ApiException("AudioMuse rejected the username or password")
                    404 -> throw ApiException("AudioMuse has no login switched on, so leave its username and password empty")
                    else -> throw ApiException("AudioMuse login failed (HTTP ${response.code})")
                }
                response.headers("Set-Cookie")
                    .firstNotNullOfOrNull { header ->
                        header.split(';').first().trim().takeIf { it.startsWith("audiomuse_jwt=") }
                            ?.substringAfter('=')
                    }
                    ?: throw ApiException("AudioMuse accepted the login but sent no session")
            }
        }
        session = key to jwt
        jwt
    }

    private suspend fun execute(build: suspend () -> Request): JsonObject =
        executeElement(build) as? JsonObject ?: throw ApiException("AudioMuse sent an unexpected answer")

    private suspend fun executeElement(build: suspend () -> Request): JsonElement {
        val first = runCatching { send(build()) }
        val error = first.exceptionOrNull()
        // A session can expire: sign in again once and retry.
        if (error is Unauthorized && settings().audioMuseToken.isBlank() && settings().audioMuseUser.isNotBlank()) {
            login(settings(), force = true)
            return send(build())
        }
        if (error is Unauthorized) {
            throw ApiException("AudioMuse needs a login: set an API token, or its username and password, in Settings")
        }
        return first.getOrThrow()
    }

    private class Unauthorized : Exception("Unauthorized")

    private suspend fun send(request: Request): JsonElement = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val element = runCatching { AppJson.parseToJsonElement(body) }.getOrNull()
            val json = element as? JsonObject
            if (response.code == 401 || response.code == 403) throw Unauthorized()
            if (!response.isSuccessful) {
                val code = json?.int("error_code")
                val reason = json?.str("error_message") ?: json?.str("error") ?: json?.str("message") ?: body.take(120).ifBlank { "no details" }
                AppLog.w("AudioMuse", "${request.url.encodedPath} failed: HTTP ${response.code}, error ${code ?: "?"}: $reason")
                throw ApiException(AudioMuseErrors.message(code, response.code, reason), code)
            }
            element ?: throw ApiException("AudioMuse sent something that isn't JSON. Is the address its API port?")
        }
    }

    /**
     * A GET to a plugin on the AudioMuse server ([pathAndQuery] after the address, e.g. the Musix plugin's
     * profiles), with the same login as the rest of AudioMuse.
     */
    suspend fun plugin(pathAndQuery: String): JsonElement = executeElement { request(pathAndQuery).get().build() }

    /**
     * CLAP text-to-audio search. [steering] nudges it towards (or away from) concepts from [concepts]: AudioMuse
     * snaps each weight to its own steps, so Musix leaves the weight to the server (issue #208).
     */
    suspend fun textSearch(query: String, limit: Int, steering: List<Steer> = emptyList()): List<ClapHit> {
        val payload = buildJsonObject {
            put("query", query)
            put("limit", limit)
            if (steering.isNotEmpty()) {
                put("steering", kotlinx.serialization.json.buildJsonArray {
                    steering.take(10).forEach { s ->
                        add(buildJsonObject {
                            put("term", s.term)
                            put("direction", if (s.more) "more" else "less")
                            s.weight?.let { put("weight", it) }
                        })
                    }
                })
            }
        }.toString()
        val root = execute { request("/api/clap/search").post(payload.toRequestBody(JSON_TYPE)).build() }
        val hits = root.arr("results").mapNotNull { r ->
            val id = r.str("item_id") ?: return@mapNotNull null
            ClapHit(
                id = id,
                title = r.str("title").orEmpty(),
                artist = r.str("author").orEmpty(),
                album = r.str("album"),
                similarity = r.dbl("similarity") ?: 0.0,
            )
        }
        // What was asked and what came back (issue #229), so a search that finds the wrong music can be traced to
        // the request or to AudioMuse's ranking.
        AppLog.i("Sound", searchSummary(query, steering, hits, warningsOf(root)))
        return hits
    }

    /**
     * Tracks that sound like [itemId] (for a single server, the Navidrome song id). Duplicates, mood and radius
     * follow AudioMuse's own settings (SIMILARITY_ELIMINATE_DUPLICATES_DEFAULT, its mood default,
     * SIMILARITY_RADIUS_DEFAULT): Musix only sends [sameMood] when the listener chose it (issue #208).
     */
    suspend fun similarTracks(itemId: String, count: Int, sameMood: Boolean? = null): List<ClapHit> {
        val url = apiUrl("api/similar_tracks")
            .addQueryParameter("item_id", itemId)
            .addQueryParameter("n", count.toString())
            .apply { sameMood?.let { addQueryParameter("mood_similarity", it.toString()) } }
            .build()
        return neighbours(executeElement { request("").url(url).get().build() })
    }

    /** AudioMuse's mood centres (happy, relaxed…), each with the tags that describe it (issue #208). */
    suspend fun moodCentres(): List<MoodCentre> {
        centres?.let { return it }
        val root = execute { request("/api/mood_centroids").get().build() }
        return parseMoodCentres(root).also { centres = it }
    }

    @Volatile private var centres: List<MoodCentre>? = null

    /** Tracks around one of AudioMuse's mood centres: a radio for that mood (issue #208). */
    suspend fun moodRadio(centre: MoodCentre, count: Int): List<ClapHit> {
        val url = apiUrl("api/similar_tracks")
            .addQueryParameter("mood", centre.mood)
            .addQueryParameter("centroid_index", centre.index.toString())
            .addQueryParameter("n", count.toString())
            .build()
        return neighbours(executeElement { request("").url(url).get().build() })
    }

    /**
     * Song Alchemy: the songs closest to a blend of [blend] (and away from [avoid]), all song ids (issue #208).
     * The count, temperature and distances follow AudioMuse's own settings unless [count] is given.
     */
    suspend fun alchemy(blend: List<String>, avoid: List<String> = emptyList(), count: Int? = null): List<ClapHit> {
        val payload = buildJsonObject {
            put("items", kotlinx.serialization.json.buildJsonArray {
                blend.forEach { id -> add(buildJsonObject { put("id", id); put("op", "ADD"); put("type", "song") }) }
                avoid.forEach { id -> add(buildJsonObject { put("id", id); put("op", "SUBTRACT"); put("type", "song") }) }
            })
            count?.let { put("n", it) }
        }.toString()
        val root = execute { request("/api/alchemy").post(payload.toRequestBody(JSON_TYPE)).build() }
        return neighbours(root["results"] ?: JsonArray(emptyList()))
    }

    private fun apiUrl(path: String) = settings().audioMuseUrl.trimEnd('/').toHttpUrl().newBuilder().addPathSegments(path)

    /** AudioMuse's track rows ({item_id, title, author, album, distance}); closer is a higher similarity. */
    private fun neighbours(root: JsonElement): List<ClapHit> {
        val rows = (root as? JsonArray) ?: (root as? JsonObject)?.let { it["results"] as? JsonArray } ?: JsonArray(emptyList())
        return rows.mapNotNull { element ->
            val r = element as? JsonObject ?: return@mapNotNull null
            val id = r.str("item_id") ?: return@mapNotNull null
            ClapHit(id, r.str("title").orEmpty(), r.str("author").orEmpty(), r.str("album"), r.dbl("distance")?.let { 1 - it } ?: 0.0)
        }
    }

    /**
     * "Your sound": AudioMuse averages the sound of your most-played songs in Navidrome and returns
     * [count] tracks closest to it. The Navidrome login tells it whose play counts to use.
     */
    suspend fun sonicFingerprint(count: Int, navidromeUser: String, navidromePassword: String): List<ClapHit> {
        val payload = buildJsonObject {
            put("n", count)
            if (navidromeUser.isNotBlank()) put("navidrome_user", navidromeUser)
            if (navidromePassword.isNotBlank()) put("navidrome_password", navidromePassword)
        }.toString()
        val root = executeElement { request("/api/sonic_fingerprint/generate").post(payload.toRequestBody(JSON_TYPE)).build() }
        val rows = (root as? JsonArray) ?: JsonArray(emptyList())
        return rows.mapNotNull { element ->
            val r = element as? JsonObject ?: return@mapNotNull null
            val id = r.str("item_id") ?: return@mapNotNull null
            ClapHit(id, r.str("title").orEmpty(), r.str("author").orEmpty(), r.str("album"), r.dbl("distance")?.let { 1 - it } ?: 1.0)
        }
    }

    /**
     * A song path (AudioMuse's `find_path`): tracks that move step by step from one end to the other, in order,
     * both ends included. Each end is a song id, or one end a [PathMood]. [length] is the whole path, both ends
     * counted, and is kept exactly (`path_fix_size`), so the path is as long as the listener picked (issue #208).
     * [lyrics] builds it in the lyrics' meaning (SemGrove) instead of the sound alone.
     */
    suspend fun findPath(
        startId: String?,
        endId: String?,
        length: Int,
        startMood: PathMood? = null,
        endMood: PathMood? = null,
        lyrics: Boolean = false,
    ): List<ClapHit> {
        val url = apiUrl("api/find_path")
            .apply {
                startId?.let { addQueryParameter("start_song_id", it) }
                endId?.let { addQueryParameter("end_song_id", it) }
                startMood?.let { addQueryParameter("start_mood", it.id) }
                endMood?.let { addQueryParameter("end_mood", it.id) }
                if (lyrics) addQueryParameter("path_space", "lyrics")
            }
            .addQueryParameter("max_steps", length.toString())
            .addQueryParameter("path_fix_size", "true")
            .build()
        val root = executeElement { request("").url(url).get().build() }
        val rows = (root as? JsonArray) ?: (root as? JsonObject)?.let { it["path"] as? JsonArray } ?: JsonArray(emptyList())
        return rows.mapNotNull { element ->
            val r = element as? JsonObject ?: return@mapNotNull null
            val id = r.str("item_id") ?: return@mapNotNull null
            ClapHit(id, r.str("title").orEmpty(), r.str("author").orEmpty(), r.str("album"), 1.0)
        }
    }

    /**
     * Every concept the Sound search can be steered by, in AudioMuse's own groups (genre, instrument, vocals,
     * mood, production, tempo, era), each group's best-understood terms first (issue #216).
     */
    suspend fun conceptCatalogue(): ConceptCatalogue {
        catalogue?.let { return it }
        val root = execute { request("/api/clap/concepts").get().build() }
        return parseCatalogue(root).also { if (it.categories.isNotEmpty()) catalogue = it }
    }

    @Volatile private var catalogue: ConceptCatalogue? = null

    /**
     * AudioMuse's analysis of songs (tempo, key, moods, genres) from its read-only export for apps
     * (`/api/sync`), at most 500 at a time, without the large sound data (issue #216).
     */
    suspend fun features(ids: List<String>): List<TrackFeatures> = ids.distinct().chunked(SYNC_BATCH).flatMap { batch ->
        val url = apiUrl("api/sync")
            .addQueryParameter("ids", batch.joinToString(","))
            .addQueryParameter("include_embeddings", "false")
            .addQueryParameter("limit", batch.size.toString())
            .build()
        val root = execute { request("").url(url).get().build() }
        root.arr("tracks").mapNotNull(::parseFeatures)
    }

    /** Sound concepts this library's search understands well, to steer the AI's wording. */
    suspend fun concepts(): List<String> {
        val root = execute { request("/api/clap/concepts").get().build() }
        if (root.bool("available") == false) return emptyList()
        return root.arr("categories")
            .flatMap { category -> category.arr("terms").mapNotNull { it.str("term") } }
            .distinct()
    }

    internal companion object {
        /** AudioMuse's notes about concepts it couldn't use (an unknown term is dropped, not refused). */
        fun warningsOf(root: JsonObject): List<String> =
            (root["steering_warnings"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }

        /** One log line for a sound search: words, concepts, how many came back, the best few with their score. */
        fun searchSummary(query: String, steering: List<Steer>, hits: List<ClapHit>, warnings: List<String>, top: Int = 5): String {
            val steer = steering.joinToString(", ") { (if (it.more) "+" else "−") + it.term + (it.weight?.let { w -> "×$w" } ?: "") }.ifEmpty { "none" }
            val best = hits.take(top).joinToString("; ") { "${it.title} – ${it.artist} (${"%.3f".format(Locale.ROOT, it.similarity)})" }
            return "Search \"$query\" · concepts $steer · ${hits.size} songs" +
                (if (best.isNotEmpty()) " · best: $best" else "") +
                (if (warnings.isNotEmpty()) " · AudioMuse: ${warnings.joinToString("; ")}" else "")
        }

        val JSON_TYPE = "application/json".toMediaType()
        const val SYNC_BATCH = 500

        fun parseCatalogue(root: JsonObject): ConceptCatalogue {
            if (root.bool("available") == false) return ConceptCatalogue(emptyList(), 0)
            val categories = root.arr("categories").mapNotNull { c ->
                val id = c.str("category") ?: return@mapNotNull null
                val terms = c.arr("terms")
                    .mapNotNull { t -> t.str("term")?.let { it to (t.dbl("grounding") ?: 0.0) } }
                    .sortedByDescending { it.second }
                    .map { it.first }
                    .distinct()
                if (terms.isEmpty()) null else ConceptCategory(id, c.str("label") ?: id.replaceFirstChar { it.uppercase() }, terms)
            }
            return ConceptCatalogue(categories, root.int("max_terms") ?: 10)
        }

        /** `"rock:0.53,pop:0.21"` (AudioMuse's packed scores) as a map; anything unreadable is left out. */
        fun parseScores(packed: String?): Map<String, Double> {
            if (packed.isNullOrBlank()) return emptyMap()
            return packed.split(',').mapNotNull { part ->
                val label = part.substringBefore(':').trim()
                val value = part.substringAfter(':', "").trim().toDoubleOrNull()
                if (label.isEmpty() || value == null) null else label to value
            }.toMap()
        }

        fun parseFeatures(o: JsonObject): TrackFeatures? {
            val id = o.str("id") ?: return null
            return TrackFeatures(
                id = id,
                artist = o.str("artist").orEmpty(),
                tempo = o.dbl("tempo"),
                key = o.str("key"),
                scale = o.str("scale"),
                moods = parseScores(o.str("other_features")),
                genres = parseScores(o.str("mood_vector")),
                energy = o.dbl("energy"),
            )
        }

        /** `{"happy": [{"index": 0, "top_tags": ["pop", …]}, …], …}` as a flat list. */
        /** The navigation key of a mood centre ("relaxed-2") and back. */
        fun centreKey(c: MoodCentre) = "${c.mood}-${c.index}"

        /** "Relaxed: piano, ambient": the mood and its first two tags. */
        fun centreTitle(c: MoodCentre) =
            c.mood.replaceFirstChar { it.uppercase() } + (c.tags.take(2).takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = ": ") ?: "")

        fun parseMoodCentres(root: JsonObject): List<MoodCentre> = root.entries.flatMap { (mood, list) ->
            (list as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val index = o.int("index") ?: return@mapNotNull null
                val tags = (o["top_tags"] as? JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
                MoodCentre(mood, index, tags)
            }
        }
    }
}
