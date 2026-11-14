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
 * beat grid, the genres it hears and their descriptions, and the bad blends marked in Siansa Dev. Asked directly at the
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
 *
 * Users shown together are logins of their own to Siansa Beats: a question about a shown user's songs names that
 * user's server ([whoseSongs]: the settings of the user the songs asked about belong to), not the one in use (the
 * owner, 14 Nov: a Kids album's similar albums were the main login's).
 */
class BeatsApi(
    private val http: OkHttpClient,
    private val whoseSongs: ((ids: List<String>) -> AppSettings)? = null,
    private val settings: () -> AppSettings,
) {
    suspend fun get(pathAndQuery: String): JsonElement =
        send(request(forServer(pathAndQuery, songIdsIn(pathAndQuery))).get().build())

    suspend fun post(path: String, body: String): JsonElement =
        send(request(forServer(path, songIdsIn(path) + songIdsInBody(body))).post(body.toRequestBody(JSON)).build())

    /** What this Siansa Beats can do and how far it is ([Capabilities]), for the songs of the server this app plays from. */
    suspend fun capabilities(): Capabilities {
        val all = Capabilities.parse(send(request(CAPABILITIES).get().build()) as? JsonObject ?: throw ApiException("Siansa Beats sent an unexpected answer"))
        val mine = remember(all).id
        // Its numbers are the default server's: asked again for this app's server when that's another one.
        if (mine == null || mine == all.serverId) return all
        return Capabilities.parse(send(request(withServer(CAPABILITIES, mine)).get().build()) as? JsonObject ?: return all)
    }

    /**
     * The server chosen for one login (null: none to name), when it was looked for, and whether this Siansa Beats
     * takes its own server's name for similar albums and artists too ([SIMILAR_OWN]).
     */
    private data class Chosen(val id: String?, val at: Long, val similar: Boolean)

    /** By login ([key]): the user in use and each user shown beside them. */
    private val chosen = java.util.concurrent.ConcurrentHashMap<String, Chosen>()

    /** Siansa Beats' address is the user in use's; the server is [s]'s. */
    private fun key(s: AppSettings) = listOf(settings().beatsUrl, s.sourceKind.name, s.navidromeUrl, s.navidromeUser).joinToString("|")

    private fun remember(caps: Capabilities, s: AppSettings = settings()): Chosen {
        BeatsSound.heard(caps.sound, caps.soundPaths)
        val id = serverFor(caps.servers, s.sourceKind, s.navidromeUrl, s.navidromeUser)
        if (chosen[key(s)]?.id != id || !chosen.containsKey(key(s))) {
            AppLog.i("Beats", if (id != null) "Siansa Beats is asked for its server \"$id\" for ${s.navidromeUser.ifBlank { "this login" }} (${caps.servers.size} there)"
            else if (caps.servers.isEmpty()) "Siansa Beats ${caps.version} has one server: asked without naming it"
            else "Siansa Beats doesn't read this ${s.sourceKind.label} server: asked for its default one")
        }
        return Chosen(id, System.currentTimeMillis(), atLeast(caps.version, SIMILAR_OWN)).also { chosen[key(s)] = it }
    }

    /**
     * [path] with the server of the songs [about] (the server this app plays from, or a shown user's); looked up once
     * for a login, and again [AGAIN_MS] after a failure.
     */
    private suspend fun forServer(path: String, about: List<String> = emptyList()): String {
        if (!path.startsWith("/api/v1/")) return path
        // A name already there is AudioMuse-AI's own for a server ("Server name in AudioMuse-AI"): left as it is.
        val similar = path.startsWith(SIMILAR)
        if (similar && Regex("[?&]server=").containsMatchIn(path)) return path
        val s = whoseSongs?.invoke(about) ?: settings()
        val kept = chosen[key(s)]?.takeIf { it.id != null || System.currentTimeMillis() - it.at < AGAIN_MS }
        val pick = kept ?: try {
            remember(Capabilities.parse(send(request(CAPABILITIES).get().build()) as? JsonObject ?: JsonObject(emptyMap())), s)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Chosen(null, System.currentTimeMillis(), similar = false).also { chosen[key(s)] = it }
        }
        // Before 0.43 Siansa Beats passed similar albums and artists on to AudioMuse-AI, whose server names are its own.
        if (pick.id == null || (similar && !pick.similar)) return path
        return withServer(path, pick.id)
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
        /** Whether it hears each song's genres (its image has the style model). */
        val genres: Boolean,
        /** AudioMuse-AI's paths it answers from the songs' sound (0.25, [BeatsSound]); empty before, or with none. */
        val sound: Set<String> = emptySet(),
        /** Siansa Beats' own path for each of them (0.44: `/api/similar_tracks` → `/api/v1/sound/similar`); empty before. */
        val soundPaths: Map<String, String> = emptyMap(),
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
                    sound = (feature("sound")?.get("answers") as? kotlinx.serialization.json.JsonArray).orEmpty()
                        .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.toSet() +
                        // It takes listens (0.29): kept with what it answers, so the switch shows without asking again.
                        setOfNotNull(BeatsListens.PATH.takeIf { feature("scrobbling")?.get("available")?.toString() == "true" }),
                    soundPaths = (feature("sound")?.get("paths") as? JsonObject).orEmpty()
                        .mapNotNull { (name, path) -> (path as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { name to it } }.toMap(),
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
        /** From this version Siansa Beats finds similar albums and artists itself, among the named server's songs. */
        const val SIMILAR_OWN = "0.43.0"

        /** Whether [version] ("0.44.1") is [wanted] or newer; an unreadable one isn't. */
        fun atLeast(version: String, wanted: String): Boolean {
            fun parts(v: String) = v.trim().split('.', '-').take(3).map { it.toIntOrNull() ?: return null }
            val have = parts(version) ?: return false
            val need = parts(wanted) ?: return false
            for (i in 0 until 3) {
                val a = have.getOrElse(i) { 0 }; val b = need.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return true
        }

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
            // Its own reason when it gives one ("The sound of none of those songs is known yet"): only a path it
            // doesn't have at all means it's too old. Not "0.20 or newer": a newer one can lack a newer path too
            // (the reports' numbers need 0.33, and the log said 0.20 to someone on 0.32).
            404 -> if (reason.isBlank() || reason == "no details" || reason.equals("not found", ignoreCase = true) || reason.startsWith("<")) {
                "This Siansa Beats doesn't have this yet: update it to the newest version"
            } else {
                "Siansa Beats: $reason"
            }
            else -> "Siansa Beats answered HTTP $http: $reason"
        }
    }
}
