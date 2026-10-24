package app.siansa.net

import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Navidrome's own API (the one its web page uses), for what Subsonic can't do: setting a playlist's
 * picture (Navidrome 0.61 and later). It logs in with the same username and password.
 */
class NavidromeNativeApi(
    private val http: OkHttpClient,
    private val settings: () -> AppSettings,
) {
    // The login token, for the user and server it was made for.
    private var token: Pair<String, String>? = null

    private suspend fun login(): String = withContext(Dispatchers.IO) {
        val s = settings()
        val who = s.navidromeUrl + "|" + s.navidromeUser
        token?.let { if (it.first == who) return@withContext it.second }
        val body = buildJsonObject {
            put("username", s.navidromeUser)
            put("password", s.navidromePassword)
        }.toString().toRequestBody(JSON)
        val request = Request.Builder().url(s.navidromeUrl + "/auth/login").post(body).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiException("Navidrome didn't accept the login (HTTP ${response.code})")
            val root = runCatching { AppJson.parseToJsonElement(response.body?.string().orEmpty()).jsonObject }.getOrNull()
            val fresh = root?.str("token") ?: throw ApiException("Navidrome didn't send a login token")
            token = who to fresh
            fresh
        }
    }

    /** Sets the picture of one of your playlists. [mime] is the image's type, such as image/jpeg. */
    suspend fun uploadPlaylistImage(playlistId: String, bytes: ByteArray, mime: String) {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("image", "cover" + extensionOf(mime), bytes.toRequestBody(mime.toMediaType()))
            .build()
        send { Request.Builder().url(base() + "/api/playlist/$playlistId/image").post(form) }
    }

    /** Takes the picture off again, so the playlist shows covers of its songs. */
    suspend fun removePlaylistImage(playlistId: String) {
        send { Request.Builder().url(base() + "/api/playlist/$playlistId/image").delete() }
    }

    private fun base(): String = settings().navidromeUrl

    private suspend fun send(build: () -> Request.Builder) {
        withContext(Dispatchers.IO) {
            if (settings().navidromeUrl.isBlank()) throw ApiException("Navidrome isn't set up yet")
            var retried = false
            while (true) {
                val request = build().header(AUTH_HEADER, "Bearer " + login()).build()
                http.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> return@withContext
                        // An old token: log in again once.
                        response.code == 401 && !retried -> {
                            token = null
                            retried = true
                        }
                        // Navidrome before 0.61 has no such page.
                        response.code == 404 || response.code == 405 ->
                            throw ApiException("Your Navidrome can't take playlist pictures. Navidrome 0.61 or later can.")
                        response.code == 403 -> throw ApiException("Navidrome doesn't let you change this playlist's picture")
                        response.code == 413 -> throw ApiException("That picture is too big for Navidrome")
                        else -> throw ApiException("Navidrome answered HTTP ${response.code}")
                    }
                }
            }
        }
    }

    companion object {
        private const val AUTH_HEADER = "X-ND-Authorization"
        private val JSON = "application/json".toMediaType()

        fun extensionOf(mime: String): String = when (mime.lowercase()) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/gif" -> ".gif"
            else -> ".jpg"
        }
    }
}
