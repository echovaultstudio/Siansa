package app.musix.net

import app.musix.data.AppJson
import app.musix.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
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

/** AudioMuse-AI's own API: text-to-audio (CLAP) search and its concept vocabulary. */
class AudioMuseApi(
    private val http: OkHttpClient,
    private val settings: () -> AppSettings,
) {
    private fun request(path: String): Request.Builder {
        val s = settings()
        if (s.audioMuseUrl.isBlank()) throw ApiException("AudioMuse isn't set up yet")
        val builder = Request.Builder().url(s.audioMuseUrl + path)
        if (s.audioMuseToken.isNotBlank()) builder.header("Authorization", "Bearer ${s.audioMuseToken}")
        return builder
    }

    private suspend fun execute(request: Request): JsonObject = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching { AppJson.parseToJsonElement(body).jsonObject }.getOrNull()
            if (!response.isSuccessful) {
                val reason = json?.str("error") ?: json?.str("message") ?: body.take(120).ifBlank { "no details" }
                throw ApiException(
                    when (response.code) {
                        401, 403 -> "AudioMuse refused the token ($reason)"
                        503 -> "AudioMuse's search index isn't loaded yet ($reason)"
                        else -> "AudioMuse answered HTTP ${response.code}: $reason"
                    }
                )
            }
            json ?: throw ApiException("AudioMuse sent something that isn't JSON")
        }
    }

    suspend fun textSearch(query: String, limit: Int): List<ClapHit> {
        val payload = buildJsonObject {
            put("query", query)
            put("limit", limit)
        }.toString()
        val request = request("/api/clap/search")
            .post(payload.toRequestBody(JSON_TYPE))
            .build()
        return execute(request).arr("results").mapNotNull { r ->
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

    /** Sound concepts this library's search understands well, to steer the AI's wording. */
    suspend fun concepts(): List<String> {
        val root = execute(request("/api/clap/concepts").get().build())
        if (root.bool("available") == false) return emptyList()
        return root.arr("categories")
            .flatMap { category -> category.arr("terms").mapNotNull { it.str("term") } }
            .distinct()
    }

    private companion object {
        val JSON_TYPE = "application/json".toMediaType()
    }
}
