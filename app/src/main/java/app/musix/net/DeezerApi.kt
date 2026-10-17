package app.musix.net

import app.musix.data.AppJson
import app.musix.data.normalizeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/** An artist as Deezer's public catalogue knows it. */
data class DeezerArtist(
    val id: Long,
    val name: String,
    /** A real photo (500 px), or null when Deezer only has its blank placeholder. */
    val photo: String?,
    val albums: Int,
    val fans: Int,
    /** The artist's page on deezer.com. */
    val link: String?,
)

/**
 * Deezer's public API (no account or key): artist photos and similar artists for artists that aren't in
 * the library. Navidrome uses the same source for its own artist photos. Only artist names are sent.
 */
class DeezerApi(private val http: OkHttpClient) {
    /** The Deezer artist with this name, or null if Deezer has none by that name. */
    suspend fun findArtist(name: String): DeezerArtist? =
        bestMatch(name, parseArtists(get("search/artist?q=${URLEncoder.encode(name, "UTF-8")}&limit=5")))

    /** Artists Deezer lists as similar to [id], most similar first. */
    suspend fun related(id: Long, limit: Int = 20): List<DeezerArtist> = parseArtists(get("artist/$id/related?limit=$limit"))

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(BASE + path).build()).execute().use { response ->
            if (!response.isSuccessful) throw ApiException("Deezer answered HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    companion object {
        const val BASE = "https://api.deezer.com/"

        /** The artists in a Deezer answer (`{"data": [...]}`); throws when Deezer sent an error instead. */
        fun parseArtists(json: String): List<DeezerArtist> {
            val root = runCatching { AppJson.parseToJsonElement(json).jsonObject }.getOrNull()
                ?: throw ApiException("Deezer sent something unreadable")
            root.obj("error")?.let { throw ApiException("Deezer: ${it.str("message") ?: "error"}") }
            return root.arr("data").mapNotNull { a ->
                val id = a.long("id") ?: return@mapNotNull null
                val name = a.str("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                DeezerArtist(
                    id = id,
                    name = name,
                    photo = (a.str("picture_big") ?: a.str("picture_medium"))?.takeIf(::isRealPhoto),
                    albums = a.int("nb_album") ?: 0,
                    fans = a.int("nb_fan") ?: 0,
                    link = a.str("link"),
                )
            }
        }

        /** Deezer's placeholder for an artist without a photo has no image id: `.../images/artist//500x500-...`. */
        fun isRealPhoto(url: String): Boolean = url.startsWith("https://") && !url.contains("/artist//")

        /** The search result whose name is the one asked for (ignoring case and accents); null if none is. */
        fun bestMatch(name: String, found: List<DeezerArtist>): DeezerArtist? {
            val key = normalizeKey(name)
            return found.firstOrNull { normalizeKey(it.name) == key }
        }
    }
}
