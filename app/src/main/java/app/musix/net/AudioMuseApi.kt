package app.musix.net

import app.musix.data.AppJson
import app.musix.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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
                val reason = json?.str("error") ?: json?.str("message") ?: body.take(120).ifBlank { "no details" }
                throw ApiException(
                    when (response.code) {
                        503 -> "AudioMuse's search index isn't loaded yet ($reason)"
                        else -> "AudioMuse answered HTTP ${response.code}: $reason"
                    }
                )
            }
            element ?: throw ApiException("AudioMuse sent something that isn't JSON. Is the address its API port?")
        }
    }

    suspend fun textSearch(query: String, limit: Int): List<ClapHit> {
        val payload = buildJsonObject {
            put("query", query)
            put("limit", limit)
        }.toString()
        val root = execute { request("/api/clap/search").post(payload.toRequestBody(JSON_TYPE)).build() }
        return root.arr("results").mapNotNull { r ->
            val id = r.str("item_id") ?: return@mapNotNull null
            ClapHit(
                id = id,
                title = r.str("title").orEmpty(),
                artist = r.str("author").orEmpty(),
                album = r.str("album"),
                similarity = r.dbl("similarity") ?: 0.0,
            )
        }
    }

    /**
     * Tracks that sound like [itemId] (for a single server, the Navidrome song id). With
     * [sameMood] AudioMuse also keeps the mood (danceable, relaxed, sad…) close, so a radio keeps the vibe.
     */
    suspend fun similarTracks(itemId: String, count: Int, sameMood: Boolean = true): List<ClapHit> {
        val s = settings()
        val url = s.audioMuseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/similar_tracks")
            .addQueryParameter("item_id", itemId)
            .addQueryParameter("n", count.toString())
            .addQueryParameter("eliminate_duplicates", "true")
            .addQueryParameter("mood_similarity", sameMood.toString())
            .build()
        val root = executeElement { request("").url(url).get().build() }
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

    /** Sound concepts this library's search understands well, to steer the AI's wording. */
    suspend fun concepts(): List<String> {
        val root = execute { request("/api/clap/concepts").get().build() }
        if (root.bool("available") == false) return emptyList()
        return root.arr("categories")
            .flatMap { category -> category.arr("terms").mapNotNull { it.str("term") } }
            .distinct()
    }

    private companion object {
        val JSON_TYPE = "application/json".toMediaType()
    }
}
