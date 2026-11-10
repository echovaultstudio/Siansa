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
 *
 * Siansa Beats 0.23 reads several music servers (Navidrome, Emby, Jellyfin, several users of one) and keeps each one's
 * own ids for a song. Every request then names the server this app plays from (`?server=<id>`, [serverFor]: the one
 * of its kind, by its user and address when there are several), so the ids answered are this server's. An older
 * Siansa Beats, or one that doesn't read this server, is asked as before.
 */
class BeatsApi(private val http: OkHttpClient, private val settings: () -> AppSettings) {
    suspend fun get(pathAndQuery: String): JsonElement = send(request(forServer(pathAndQuery)).get().build())

    suspend fun post(path: String, body: String): JsonElement = send(request(forServer(path)).post(body.toRequestBody(JSON)).build())

    /** What this Siansa Beats can do and how far it is ([Capabilities]), for the songs of the server this app plays from. */
    suspend fun capabilities(): Capabilities {
        val all = Capabilities.parse(send(request(CAPABILITIES).get().build()) as? JsonObject ?: throw ApiException("Siansa Beats sent an unexpected answer"))
        val mine = remember(all)
        // Its numbers are the default server's: asked again for this app's server when that's another one.
        if (mine == null || mine == all.serverId) return all
        return Capabilities.parse(send(request(withServer(CAPABILITIES, mine)).get().build()) as? JsonObject ?: return all)
    }

    /** The server chosen for the current settings (null: none to name), and when it was looked for. */
    private data class Chosen(val key: String, val id: String?, val at: Long)

    @Volatile private var chosen: Chosen? = null

    private fun key(s: AppSettings) = listOf(s.beatsUrl, s.sourceKind.name, s.navidromeUrl, s.navidromeUser).joinToString("|")

    private fun remember(caps: Capabilities): String? {
        val s = settings()
        val id = serverFor(caps.servers, s.sourceKind, s.navidromeUrl, s.navidromeUser)
        if (chosen?.let { it.key == key(s) && it.id == id } != true) {
            AppLog.i("Beats", if (id != null) "Siansa Beats is asked for its server \"$id\" (${caps.servers.size} there)"
            else if (caps.servers.isEmpty()) "Siansa Beats ${caps.version} has one server: asked without naming it"
            else "Siansa Beats doesn't read this ${s.sourceKind.label} server: asked for its default one")
        }
        chosen = Chosen(key(s), id, System.currentTimeMillis())
        return id
    }

    /** [path] with the server this app plays from; looked up once for the settings, and again [AGAIN_MS] after a failure. */
    private suspend fun forServer(path: String): String {
        if (!path.startsWith("/api/v1/") || path.startsWith(SIMILAR)) return path  // similar's server is AudioMuse's own name
        val s = settings()
        val kept = chosen?.takeIf { it.key == key(s) && (it.id != null || System.currentTimeMillis() - it.at < AGAIN_MS) }
        val id = if (kept != null) kept.id else try {
            remember(Capabilities.parse(send(request(CAPABILITIES).get().build()) as? JsonObject ?: JsonObject(emptyMap())))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            chosen = Chosen(key(s), null, System.currentTimeMillis())
            null
        }
        return if (id == null) path else withServer(path, id)
    }

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
        /** Which kind of music server these numbers are of ("navidrome", "emby", "jellyfin"). */
        val musicServer: String?,
        /** That server's id there (Siansa Beats 0.23), and every server it reads. */
        val serverId: String? = null,
        val servers: List<MusicServer> = emptyList(),
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
                    serverId = (body["music_server"] as? JsonObject)?.str("id"),
                    servers = (body["servers"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { o ->
                        MusicServer(
                            id = o.str("id") ?: return@mapNotNull null,
                            kind = o.str("kind") ?: return@mapNotNull null,
                            name = o.str("name").orEmpty(),
                            url = o.str("url").orEmpty(),
                            user = o.str("user").orEmpty(),
                            default = o["default"]?.toString() == "true",
                        )
                    },
                )
            }
        }
    }

    /** A music server Siansa Beats reads (0.23): its id there is what `?server=` takes. */
    data class MusicServer(val id: String, val kind: String, val name: String, val url: String, val user: String, val default: Boolean)

    companion object {
        const val CAPABILITIES = "/api/v1/capabilities"
        private const val SIMILAR = "/api/v1/similar/"
        /** How long after Siansa Beats couldn't be asked which servers it reads before it's asked again. */
        const val AGAIN_MS = 60_000L

        /** Siansa Beats' name for a kind of server. */
        fun kindName(kind: SourceKind): String = when (kind) {
            SourceKind.EMBY -> "emby"
            SourceKind.JELLYFIN -> "jellyfin"
            else -> "navidrome"
        }

        private fun place(url: String): String = runCatching {
            val u = java.net.URI(url.trim())
            u.host.orEmpty().lowercase() + ":" + (if (u.port > 0) u.port else if (u.scheme == "https") 443 else 80)
        }.getOrDefault("")

        /**
         * Which of Siansa Beats' [servers] this app plays from: one of its [kind]; of several, the one with this [user]
         * and this address ([url]: the phone may know the server by another address than Siansa Beats does, so the
         * user counts for more), then the default one, then the first. Null when it reads none of this kind (or it's
         * an older Siansa Beats, which lists none).
         */
        fun serverFor(servers: List<MusicServer>, kind: SourceKind, url: String, user: String): String? {
            val mine = servers.filter { it.kind == kindName(kind) }
            return mine.maxByOrNull { s ->
                (if (user.isNotBlank() && s.user.equals(user.trim(), ignoreCase = true)) 4 else 0) +
                    (if (place(url).length > 1 && place(s.url) == place(url)) 2 else 0) + (if (s.default) 1 else 0)
            }?.id
        }

        /** [path] naming the server [id]. */
        fun withServer(path: String, id: String): String =
            path + (if ('?' in path) "&" else "?") + "server=" + java.net.URLEncoder.encode(id, "UTF-8")
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
