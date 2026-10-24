package app.siansa.net

import app.siansa.data.AppJson
import app.siansa.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest

/**
 * Last.fm's scrobbling API with the user's own API account (key + shared secret).
 * A session key is fetched once from username and password; the password itself is never stored.
 */
class LastFmApi(private val http: OkHttpClient) {
    data class Keys(val apiKey: String, val secret: String, val session: String)

    /** Signs in and returns the session key to store. */
    suspend fun mobileSession(apiKey: String, secret: String, user: String, password: String): String {
        val root = post(
            mapOf("method" to "auth.getMobileSession", "username" to user, "password" to password, "api_key" to apiKey),
            secret,
        )
        return root.obj("session")?.str("key") ?: throw ApiException("Last.fm sent no session")
    }

    suspend fun nowPlaying(keys: Keys, song: Song) {
        post(trackParams(song) + mapOf("method" to "track.updateNowPlaying", "api_key" to keys.apiKey, "sk" to keys.session), keys.secret)
    }

    suspend fun scrobble(keys: Keys, song: Song, startedAtSec: Long) {
        post(
            trackParams(song) + mapOf(
                "method" to "track.scrobble",
                "timestamp" to startedAtSec.toString(),
                "api_key" to keys.apiKey,
                "sk" to keys.session,
            ),
            keys.secret,
        )
    }

    suspend fun love(keys: Keys, song: Song, loved: Boolean) {
        post(
            mapOf(
                "method" to if (loved) "track.love" else "track.unlove",
                "artist" to song.artist,
                "track" to song.title,
                "api_key" to keys.apiKey,
                "sk" to keys.session,
            ),
            keys.secret,
        )
    }

    private fun trackParams(song: Song): Map<String, String> = buildMap {
        put("artist", song.artist)
        put("track", song.title)
        if (song.album.isNotBlank()) put("album", song.album)
        if (song.durationSec > 0) put("duration", song.durationSec.toString())
        song.track?.let { put("trackNumber", it.toString()) }
    }

    private suspend fun post(params: Map<String, String>, secret: String) = withContext(Dispatchers.IO) {
        // Signature: every parameter sorted by name, name+value joined, then the secret, md5'd.
        val signature = md5(params.toSortedMap().entries.joinToString("") { it.key + it.value } + secret)
        val form = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
            add("api_sig", signature)
            add("format", "json")
        }.build()
        val request = Request.Builder().url("https://ws.audioscrobbler.com/2.0/").post(form).build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching { AppJson.parseToJsonElement(body).jsonObject }.getOrNull()
            json?.str("message")?.let { message ->
                if (json.int("error") != null) throw ApiException("Last.fm: $message")
            }
            if (!response.isSuccessful || json == null) throw ApiException("Last.fm answered HTTP ${response.code}")
            json
        }
    }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
