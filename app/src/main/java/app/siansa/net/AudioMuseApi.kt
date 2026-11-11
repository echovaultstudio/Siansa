package app.siansa.net

import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import app.siansa.log.AppLog
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
    /**
     * The server's name in AudioMuse for a request about [ids] (songs): the name kept by the user whose music
     * they are, so each music server is named however many are shown; with no ids, the music shown's.
     * Without it, the settings' own name.
     */
    private val serverFor: ((ids: List<String>) -> String)? = null,
    /**
     * Siansa Beats' answer to one of AudioMuse's own paths ([BeatsSound]): a GET of the path and query, or a POST of
     * the body. Asked first for the paths it answers; AudioMuse itself when it can't, or for the rest.
     */
    private val beats: (suspend (pathAndQuery: String, body: String?) -> JsonElement)? = null,
    private val settings: () -> AppSettings,
) {
    /**
     * What songs sound like, asked of Siansa Beats when it answers [path] (the owner, 11 Nov), else of AudioMuse:
     * [query] for a GET, [payload] (a JSON object) for a POST, about the songs [about]. When Siansa Beats fails and
     * AudioMuse is set up, AudioMuse is asked; without AudioMuse, Siansa Beats' reason is the answer.
     */
    private suspend fun sound(
        path: String,
        query: List<Pair<String, String?>> = emptyList(),
        payload: String? = null,
        about: List<String> = emptyList(),
        /** Siansa Beats' answer or none: AudioMuse isn't asked (a DJ mix's tempos are never AudioMuse's). */
        beatsOnly: Boolean = false,
    ): JsonElement {
        val s = settings()
        val asked = query.filter { it.second != null }
        if (beatsOnly && !(beats != null && s.beatsReady && BeatsSound.answers(path))) {
            throw ApiException("Siansa Beats doesn't have the songs' sound yet (it needs 0.25)")
        }
        if (beats != null && s.beatsReady && BeatsSound.answers(path)) {
            val pathAndQuery = path + if (asked.isEmpty()) "" else "?" + asked.joinToString("&") { (k, v) ->
                java.net.URLEncoder.encode(k, "UTF-8") + "=" + java.net.URLEncoder.encode(v, "UTF-8")
            }
            try {
                return beats.invoke(BeatsSound.path(pathAndQuery), payload)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (beatsOnly || !s.audioMuseReady) throw e
                AppLog.w("AudioMuse", "Siansa Beats couldn't answer $path (${e.message}); asking AudioMuse")
            }
        }
        if (payload != null) {
            return executeElement { request(path, about).post(serverInBody(payload, server(about)).toRequestBody(JSON_TYPE)).build() }
        }
        val url = apiUrl(path.removePrefix("/")).apply { asked.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        return executeElement { request("", about, at = url).get().build() }
    }

    private fun server(ids: List<String>): String =
        serverFor?.invoke(ids.filter { it.isNotBlank() }) ?: settings().audioMuseServer
    private val loginLock = Mutex()
    private var session: Pair<String, String>? = null // (login used, JWT)

    /** A request to [path] (or the full address [at]), naming the server of the songs [about] (see [serverFor]). */
    private suspend fun request(path: String, about: List<String> = emptyList(), at: okhttp3.HttpUrl? = null): Request.Builder {
        val s = settings()
        if (s.audioMuseUrl.isBlank()) throw ApiException("AudioMuse isn't set up yet")
        val builder = Request.Builder().url(serverInUrl(at?.toString() ?: (s.audioMuseUrl + path), server(about)))
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
                val path = request.url.encodedPath
                val message = if (path.contains("/plugins/")) AudioMuseErrors.pluginMessage(code, response.code, reason, body) else AudioMuseErrors.message(code, response.code, reason)
                // A web page as the reason is noise in the log: say what it means instead.
                AppLog.w("AudioMuse", "$path failed: HTTP ${response.code}, error ${code ?: "?"}: ${if (AudioMuseErrors.isWebPage(body)) "a web page, not the API (the plugin isn't installed)" else reason}")
                throw AudioMuseHttpException(message, code, response.code)
            }
            element ?: throw ApiException("AudioMuse sent something that isn't JSON. Is the address its API port?")
        }
    }

    /**
     * A GET to a plugin on the AudioMuse server ([pathAndQuery] after the address, e.g. the Siansa plugin's
     * profiles), with the same login as the rest of AudioMuse.
     */
    suspend fun plugin(pathAndQuery: String): JsonElement {
        val path = if (pathAndQuery.startsWith(PLUGIN_PREFIX) && usesLegacyPlugin()) {
            LEGACY_PLUGIN_PREFIX + pathAndQuery.removePrefix(PLUGIN_PREFIX)
        } else pathAndQuery
        return executeElement { request(path, songIdsIn(pathAndQuery)).get().build() }
    }

    /** POSTs [body] (JSON) to a plugin's [path] on the AudioMuse server (Siansa Dev's bad blends). */
    suspend fun pluginPost(path: String, body: String): JsonElement =
        executeElement { request(path).post(body.toRequestBody(JSON_TYPE)).build() }

    /** Per AudioMuse address: whether only the plugin's earlier version (before the rename, issue #360) is installed. */
    private val legacyPlugin = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /**
     * The plugin was renamed with the app (issue #360). Until the new one is installed, an AudioMuse server
     * with the earlier one keeps working: when the new plugin isn't there (HTTP 404) but the earlier one is,
     * its address is used. Worked out once per server; a failure to reach either isn't remembered.
     */
    private suspend fun usesLegacyPlugin(): Boolean {
        val server = settings().audioMuseUrl
        legacyPlugin[server]?.let { return it }
        suspend fun has(prefix: String): Boolean? = try {
            executeElement { request(prefix + "api/health").get().build() }
            true
        } catch (e: AudioMuseHttpException) {
            if (e.http == 404) false else null
        } catch (e: Exception) {
            null
        }
        val legacy = when (has(PLUGIN_PREFIX)) {
            true -> false
            false -> has(LEGACY_PLUGIN_PREFIX) ?: return false
            null -> return false
        }
        if (legacy) AppLog.w("AudioMuse", "Only the plugin's earlier version is installed; using it until the Siansa plugin is")
        legacyPlugin[server] = legacy
        return legacy
    }

    /**
     * CLAP text-to-audio search. [steering] nudges it towards (or away from) concepts from [concepts]: AudioMuse
     * snaps each weight to its own steps, so Siansa leaves the weight to the server (issue #208).
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
        val root = sound("/api/clap/search", payload = payload) as? JsonObject ?: throw ApiException("AudioMuse sent an unexpected answer")
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
     * SIMILARITY_RADIUS_DEFAULT): Siansa only sends [sameMood] when the listener chose it (issue #208).
     */
    suspend fun similarTracks(itemId: String, count: Int, sameMood: Boolean? = null): List<ClapHit> {
        return neighbours(sound(
            "/api/similar_tracks",
            listOf("item_id" to itemId, "n" to count.toString(), "mood_similarity" to sameMood?.toString()),
            about = listOf(itemId),
        ))
    }

    /** AudioMuse's mood centres (happy, relaxed…), each with the tags that describe it (issue #208). */
    suspend fun moodCentres(): List<MoodCentre> {
        centres?.let { return it }
        val root = sound("/api/mood_centroids") as? JsonObject ?: throw ApiException("AudioMuse sent an unexpected answer")
        return parseMoodCentres(root).also { centres = it }
    }

    @Volatile private var centres: List<MoodCentre>? = null

    /** Tracks around one of AudioMuse's mood centres: a radio for that mood (issue #208). */
    suspend fun moodRadio(centre: MoodCentre, count: Int): List<ClapHit> {
        return neighbours(sound(
            "/api/similar_tracks",
            listOf("mood" to centre.mood, "centroid_index" to centre.index.toString(), "n" to count.toString()),
        ))
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
        val root = sound("/api/alchemy", payload = payload, about = blend + avoid) as? JsonObject
            ?: throw ApiException("AudioMuse sent an unexpected answer")
        return neighbours(root["results"] ?: JsonArray(emptyList()))
    }

    private fun apiUrl(path: String): okhttp3.HttpUrl.Builder {
        val base = settings().audioMuseUrl.trimEnd('/')
        if (base.isBlank()) throw ApiException("AudioMuse isn't set up yet")
        return base.toHttpUrl().newBuilder().addPathSegments(path)
    }

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
        // Siansa Beats reads the most played songs with its own sign-in to the music server: it gets no login.
        val forBeats = buildJsonObject { put("n", count) }.toString()
        val root = if (settings().beatsReady && BeatsSound.answers("/api/sonic_fingerprint/generate")) {
            runCatching { sound("/api/sonic_fingerprint/generate", payload = forBeats) }.getOrElse { e ->
                if (e is kotlinx.coroutines.CancellationException || !settings().audioMuseReady) throw e
                executeElement { request("/api/sonic_fingerprint/generate").post(serverInBody(payload, server(emptyList())).toRequestBody(JSON_TYPE)).build() }
            }
        } else {
            executeElement { request("/api/sonic_fingerprint/generate").post(serverInBody(payload, server(emptyList())).toRequestBody(JSON_TYPE)).build() }
        }
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
        val asked = listOf(
            "start_song_id" to startId, "end_song_id" to endId, "start_mood" to startMood?.id, "end_mood" to endMood?.id,
            "max_steps" to length.toString(), "path_fix_size" to "true",
        )
        // A path through the lyrics' meaning is AudioMuse's own: Siansa Beats has the sound alone.
        val root = if (lyrics) {
            val url = apiUrl("api/find_path").apply { (asked + ("path_space" to "lyrics")).forEach { (k, v) -> v?.let { addQueryParameter(k, it) } } }.build()
            executeElement { request("", listOfNotNull(startId, endId), at = url).get().build() }
        } else {
            sound("/api/find_path", asked, about = listOfNotNull(startId, endId))
        }
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
        val root = sound("/api/clap/concepts") as? JsonObject ?: throw ApiException("AudioMuse sent an unexpected answer")
        return parseCatalogue(root).also { if (it.categories.isNotEmpty()) catalogue = it }
    }

    @Volatile private var catalogue: ConceptCatalogue? = null

    /**
     * AudioMuse's analysis of songs (tempo, key, moods, genres) from its read-only export for apps
     * (`/api/sync`), at most 500 at a time, without the large sound data (issue #216).
     */
    suspend fun features(ids: List<String>, beatsOnly: Boolean = false): List<TrackFeatures> = syncBatches(ids.distinct()).flatMap { batch ->
        val root = sound(
            "/api/sync",
            listOf("ids" to batch.joinToString(","), "include_embeddings" to "false", "limit" to batch.size.toString()),
            about = batch,
            beatsOnly = beatsOnly,
        ) as? JsonObject ?: throw ApiException("AudioMuse sent an unexpected answer")
        root.arr("tracks").mapNotNull(::parseFeatures)
    }

    /** Sound concepts this library's search understands well, to steer the AI's wording. */
    suspend fun concepts(): List<String> {
        val root = sound("/api/clap/concepts") as? JsonObject ?: throw ApiException("AudioMuse sent an unexpected answer")
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

        /**
         * AudioMuse's web server turns away a request line over about 4 KB (HTTP 400, an HTML page): 251 songs'
         * ids in one /api/sync asked for 6 KB, and a playlist's DJ mix lost its tempo and key and was only shuffled
         * (the owner's log, 8 Nov). Batches of at most [SYNC_BATCH] ids whose list stays within [budget] bytes,
         * leaving room for the address and the other parameters.
         */
        fun syncBatches(ids: List<String>, budget: Int = SYNC_IDS_BUDGET): List<List<String>> {
            val out = ArrayList<List<String>>()
            var current = ArrayList<String>()
            var length = 0  // of the ids as sent: each encoded, "%2C" between them
            for (id in ids) {
                val size = java.net.URLEncoder.encode(id, "UTF-8").length
                val added = if (current.isEmpty()) size else size + 3
                if (current.isNotEmpty() && (length + added > budget || current.size >= SYNC_BATCH)) {
                    out += current
                    current = ArrayList()
                    length = 0
                }
                length += if (current.isEmpty()) size else size + 3
                current += id
            }
            if (current.isNotEmpty()) out += current
            return out
        }

        /** The ids' share of a 4 KB request line, after the address and the other parameters. */
        const val SYNC_IDS_BUDGET = 3_000

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

/**
 * Where the plugin's API is on an AudioMuse server. Siansa Dev (the owner, 9 Nov): Siansa Beats (dev), which does
 * all the Siansa plugin does, with Siansa Beats' beat grids, and is the one plugin going forward.
 */
const val PLUGIN_PREFIX = "/plugins/siansa_beats/"

/** The Siansa plugin, used while Siansa Beats (dev) isn't installed (the same API). */
const val LEGACY_PLUGIN_PREFIX = "/plugins/siansa_analysis/"

/**
 * AudioMuse 3 serves several media servers; each request names which one with `server` (its name in AudioMuse),
 * in the address and, for JSON requests, in the body too, so ids come back as that server's. Nothing is added
 * for AudioMuse's default server ([server] empty).
 */
/** The song (or album) ids a plugin address is about: `ids=a,b`, `track_id=`, `album_id=`, `item_id=` (similar albums…). */
internal fun songIdsIn(pathAndQuery: String): List<String> =
    pathAndQuery.substringAfter('?', "").split('&').mapNotNull { part ->
        val name = part.substringBefore('=')
        if (name !in SONG_ID_PARAMS) null
        else java.net.URLDecoder.decode(part.substringAfter('=', ""), "UTF-8").split(',')
    }.flatten().filter { it.isNotBlank() }

private val SONG_ID_PARAMS = setOf("ids", "track_id", "item_id", "album_id", "id")

internal fun serverInUrl(url: String, server: String): String {
    if (server.isBlank()) return url
    val parsed = url.toHttpUrlOrNull() ?: return url
    return parsed.newBuilder().setQueryParameter("server", server).build().toString()
}

/** [payload] (a JSON object) with `server` set, for AudioMuse's [server]; unchanged when empty or not an object. */
internal fun serverInBody(payload: String, server: String): String {
    if (server.isBlank()) return payload
    val obj = runCatching { AppJson.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return payload
    return JsonObject(obj + ("server" to JsonPrimitive(server))).toString()
}
