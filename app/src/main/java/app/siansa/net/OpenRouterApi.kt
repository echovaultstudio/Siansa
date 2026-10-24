package app.siansa.net

import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** One chat completion through OpenRouter. Only small prompts are ever sent. */
class OpenRouterApi(
    private val http: OkHttpClient,
    private val settings: () -> AppSettings,
) {
    suspend fun complete(system: String, user: String): String = withContext(Dispatchers.IO) {
        val s = settings()
        if (s.openRouterKey.isBlank()) throw ApiException("No OpenRouter key set")
        val payload = buildJsonObject {
            put("model", s.openRouterModel)
            put("temperature", 0.9)
            put("max_tokens", 300)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", system)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", user)
                }
            }
        }.toString()
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer ${s.openRouterKey}")
            .header("X-Title", "Siansa")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json: JsonObject? = runCatching { AppJson.parseToJsonElement(body).jsonObject }.getOrNull()
            val error = json?.obj("error")?.str("message")
            if (!response.isSuccessful || error != null) {
                throw ApiException("OpenRouter: ${error ?: "HTTP ${response.code}"}")
            }
            val choice = (json?.get("choices") as? JsonArray)?.firstOrNull() as? JsonObject
            choice?.obj("message")?.str("content")?.takeIf { it.isNotBlank() }
                ?: throw ApiException("OpenRouter sent an empty answer")
        }
    }
}
