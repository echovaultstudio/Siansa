package app.siansa.net

import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import app.siansa.data.AiProvider
import app.siansa.data.aiCompletionsUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * One chat completion from the AI service set in Settings › Connections (issue #665: OpenRouter, OpenAI, Anthropic,
 * Google Gemini, Mistral, Groq, Ollama or another with the same API). Only small prompts are ever sent.
 */
class ChatApi(
    private val http: OkHttpClient,
    private val settings: () -> AppSettings,
) {
    suspend fun complete(system: String, user: String): String = withContext(Dispatchers.IO) {
        val s = settings()
        val provider = s.aiProvider
        val url = aiCompletionsUrl(provider, s.aiAddress) ?: throw ApiException("${provider.label}: no address set")
        if (provider.needsKey && s.aiKey.isBlank()) throw ApiException("No ${provider.label} key set")
        val request = Request.Builder()
            .url(url)
            .apply { if (s.aiKey.isNotBlank()) header("Authorization", "Bearer ${s.aiKey}") }
            .apply { if (provider == AiProvider.OPENROUTER) header("X-Title", "Siansa") }
            .post(payload(s.aiModel, system, user).toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            answer(provider, response.code, response.isSuccessful, response.body?.string().orEmpty())
        }
    }

    companion object {
        /** The request: the model, the two messages, and a short, lively answer. */
        internal fun payload(model: String, system: String, user: String): String = buildJsonObject {
            put("model", model)
            put("temperature", 0.9)
            put("max_tokens", 300)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", system) }
                addJsonObject { put("role", "user"); put("content", user) }
            }
        }.toString()

        /** The answer's text from a response, or an [ApiException] saying what went wrong, in the provider's name. */
        internal fun answer(provider: AiProvider, code: Int, ok: Boolean, body: String): String {
            val json: JsonObject? = runCatching { AppJson.parseToJsonElement(body).jsonObject }.getOrNull()
            // Most send {"error": {"message": …}}; some a plain {"error": "…"}.
            val error = json?.obj("error")?.str("message") ?: (json?.get("error") as? JsonPrimitive)?.content
            if (!ok || error != null) throw ApiException("${provider.label}: ${error ?: "HTTP $code"}")
            val choice = (json?.get("choices") as? JsonArray)?.firstOrNull() as? JsonObject
            return choice?.obj("message")?.str("content")?.takeIf { it.isNotBlank() }
                ?: throw ApiException("${provider.label} sent an empty answer")
        }
    }
}
