package app.siansa.net

import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Siansa Beats (the Beat This! test bench in the siansa-audiomuse-plugin repository, `beats/`): each song's beat
 * grid at both ends, read by a model rather than the Siansa plugin's own measuring. Its address and optional
 * password are in Settings › Playback › Crossfade, beat-matched blends.
 */
class SiansaBeatsApi(private val http: OkHttpClient, private val settings: () -> AppSettings) {
    val configured: Boolean get() = settings().beatsUrl.isNotBlank()

    /** A GET of [pathAndQuery] (like "/api/grids?ids=…") on Siansa Beats, as JSON. */
    suspend fun get(pathAndQuery: String): JsonElement = withContext(Dispatchers.IO) {
        val s = settings()
        val base = s.beatsUrl.trim().trimEnd('/')
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            throw ApiException("Siansa Beats' address needs to start with http:// or https://")
        }
        val request = Request.Builder().url(base + pathAndQuery).get()
        if (s.beatsPassword.isNotEmpty()) request.header("Authorization", Credentials.basic("siansa", s.beatsPassword))
        http.newCall(request.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) throw ApiException("Siansa Beats wants its password", response.code)
            if (!response.isSuccessful) throw ApiException("Siansa Beats answered HTTP ${response.code}", response.code)
            runCatching { AppJson.parseToJsonElement(body) }
                .getOrElse { throw ApiException("The answer isn't Siansa Beats': check its address") }
        }
    }
}
