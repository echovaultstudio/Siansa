package app.siansa.net

import app.siansa.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The ListenBrainz listen API, which Koito also speaks. [baseUrl] is the API root
 * (https://api.listenbrainz.org/1, or https://koito.example/apis/listenbrainz/1).
 */
class ListenBrainzApi(private val http: OkHttpClient) {

    suspend fun nowPlaying(baseUrl: String, token: String, song: Song) =
        submit(baseUrl, token, "playing_now", song, null)

    suspend fun listen(baseUrl: String, token: String, song: Song, startedAtSec: Long) =
        submit(baseUrl, token, "single", song, startedAtSec)

    suspend fun validate(baseUrl: String, token: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(apiRoot(baseUrl) + "/validate-token")
            .header("Authorization", "Token $token")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) throw ApiException("The token was refused")
            if (!response.isSuccessful) throw ApiException("The server answered HTTP ${response.code}")
            "Token accepted"
        }
    }

    private suspend fun submit(baseUrl: String, token: String, type: String, song: Song, startedAtSec: Long?) =
        withContext(Dispatchers.IO) {
            val payload: JsonObject = buildJsonObject {
                put("listen_type", type)
                putJsonArray("payload") {
                    addJsonObject {
                        if (startedAtSec != null) put("listened_at", startedAtSec)
                        putJsonObject("track_metadata") {
                            put("artist_name", song.artist)
                            put("track_name", song.title)
                            if (song.album.isNotBlank()) put("release_name", song.album)
                            putJsonObject("additional_info") {
                                if (song.durationSec > 0) put("duration_ms", song.durationSec * 1000)
                                song.track?.let { put("tracknumber", it) }
                                put("submission_client", "Siansa")
                                put("media_player", "Siansa")
                            }
                        }
                    }
                }
            }
            val request = Request.Builder()
                .url(apiRoot(baseUrl) + "/submit-listens")
                .header("Authorization", "Token $token")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ApiException("Listen server answered HTTP ${response.code}: ${response.body?.string().orEmpty().take(120)}")
                }
            }
        }

    companion object {
        /** Accepts the API root, a Koito server address or nothing (ListenBrainz itself). */
        fun apiRoot(raw: String): String {
            val url = raw.trim().trimEnd('/')
            return when {
                url.isEmpty() -> "https://api.listenbrainz.org/1"
                url.endsWith("/1") -> url
                url.contains("/apis/listenbrainz") -> "$url/1"
                url.contains("listenbrainz.org") -> "https://api.listenbrainz.org/1"
                else -> "${if (url.startsWith("http")) url else "https://$url"}/apis/listenbrainz/1"
            }
        }
    }
}
