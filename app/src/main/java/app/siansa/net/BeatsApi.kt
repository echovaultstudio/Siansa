package app.siansa.net

import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import app.siansa.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Siansa Beats' API for the app, version 1 (`/api/v1/…`, its beats/openapi.json): each song's loudness, fades, key and
 * beat grid, the genres by vote and their descriptions, and the bad blends marked in Siansa Dev. Asked directly at the
 * address in Settings › Connections, with its API key (or password) as `Authorization: Bearer`.
 *
 * Its paths are the Siansa Beats (dev) plugin's with `/api/v1/` for `/plugins/siansa_beats/api/` ([fromPlugin]), so the
 * rest of the app asks the same way for either and [app.siansa.AppGraph.siansaBeats] picks: Siansa Beats when it's set
 * up, the plugin otherwise.
 */
class BeatsApi(private val http: OkHttpClient, private val settings: () -> AppSettings) {
    suspend fun get(pathAndQuery: String): JsonElement = send(request(pathAndQuery).get().build())

    suspend fun post(path: String, body: String): JsonElement = send(request(path).post(body.toRequestBody(JSON)).build())

    /** What this Siansa Beats can do and how far it is ([Capabilities]). */
    suspend fun capabilities(): Capabilities = Capabilities.parse(get(CAPABILITIES) as? JsonObject ?: throw ApiException("Siansa Beats sent an unexpected answer"))

    private fun request(path: String): Request.Builder {
        val s = settings()
        if (s.beatsUrl.isBlank()) throw ApiException("Siansa Beats isn't set up yet")
        val builder = Request.Builder().url(s.beatsUrl.trimEnd('/') + path)
        if (s.beatsKey.isNotBlank()) builder.header("Authorization", "Bearer ${s.beatsKey}")
        return builder
    }

    private suspend fun send(request: Request): JsonElement = withContext(Dispatchers.IO) {
        val path = request.url.encodedPath
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val element = runCatching { AppJson.parseToJsonElement(body) }.getOrNull()
            if (!response.isSuccessful) {
                val reason = (element as? JsonObject)?.str("error") ?: body.take(120).ifBlank { "no details" }
                AppLog.w("Beats", "$path failed: HTTP ${response.code}: $reason")
                throw ApiException(message(response.code, reason), response.code)
            }
            element ?: throw ApiException("Siansa Beats sent something that isn't JSON. Is the address its port (8090)?")
        }
    }

    /** What this Siansa Beats can answer now, from `GET /api/v1/capabilities`. */
    data class Capabilities(
        val version: String,
        val api: Int,
        /** Songs with a beat grid: a DJ blend needs them. */
        val beatGrids: Int,
        /** Songs measured for loudness and fades. */
        val loudness: Int,
        /** Whether it votes genres from the sound (its image has the style model). */
        val genres: Boolean,
        /** Which music server it reads ("navidrome"). */
        val musicServer: String?,
    ) {
        companion object {
            fun parse(body: JsonObject): Capabilities {
                val features = body["features"] as? JsonObject
                fun feature(name: String) = features?.get(name) as? JsonObject
                return Capabilities(
                    version = body.str("version") ?: "?",
                    api = body.int("api") ?: 0,
                    beatGrids = feature("beat_grids")?.int("songs") ?: 0,
                    loudness = feature("loudness")?.int("songs") ?: 0,
                    genres = feature("genres")?.get("available")?.toString() == "true",
                    musicServer = (body["music_server"] as? JsonObject)?.str("kind"),
                )
            }
        }
    }

    companion object {
        const val CAPABILITIES = "/api/v1/capabilities"
        /** The API version this app speaks. */
        const val API = 1
        private const val PLUGIN = "/plugins/siansa_beats/api/"
        private val JSON = "application/json".toMediaType()

        /** The plugin's [path] (profiles, genres, genre, genre/about, bad-blends) as Siansa Beats' own. */
        fun fromPlugin(path: String): String = if (path.startsWith(PLUGIN)) "/api/v1/" + path.removePrefix(PLUGIN) else path

        /** A failed answer in plain words. */
        fun message(http: Int, reason: String): String = when (http) {
            401, 403 -> "Siansa Beats wants its API key or password: check it in Settings › Connections"
            404 -> "This Siansa Beats is too old for the app: update it to 0.20 or newer"
            else -> "Siansa Beats answered HTTP $http: $reason"
        }
    }
}
