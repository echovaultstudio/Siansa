package app.siansa.log

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * A problem report sent from the app (issue #414): what happened and what was expected, plus the whole
 * diagnostics archive. It goes to the Siansa beta server, which keeps the log privately and opens an issue in
 * the beta testers repo with the words and a report id. The app never holds a GitHub key.
 *
 * The request is the archive (a ZIP) as the body and the words, app and phone details in one header,
 * [header], as base64 JSON: nothing in the URL, and the archive is streamed from disk.
 */
object Report {
    const val URL = "https://siansa.app/report"
    const val MIN_HAPPENED = 10
    const val MIN_EXPECTED = 3
    const val MAX_TEXT = 1000

    /** Tidies what the tester typed: trimmed, runs of blank lines squeezed, and no more than [MAX_TEXT] characters. */
    fun clean(text: String): String =
        text.trim().replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n").take(MAX_TEXT).trim()

    /** What's missing before the report can go, in plain words; null when it's ready. */
    fun problem(happened: String, expected: String): String? = when {
        clean(happened).length < MIN_HAPPENED -> "Say what happened, in a few words."
        clean(expected).length < MIN_EXPECTED -> "Say what you expected to happen."
        else -> null
    }

    /** The words and details the server needs, as base64 (URL-safe) JSON for one HTTP header. */
    fun header(happened: String, expected: String, about: Map<String, Any?>, source: String): String {
        val json = JsonObject(
            linkedMapOf(
                "happened" to JsonPrimitive(clean(happened)),
                "expected" to JsonPrimitive(clean(expected)),
                "app" to JsonPrimitive(about["app_version"]?.toString().orEmpty()),
                "android" to JsonPrimitive(about["android"]?.toString().orEmpty()),
                "device" to JsonPrimitive(about["device"]?.toString().orEmpty()),
                "source" to JsonPrimitive(source),
                "session" to JsonPrimitive(about["session_id"]?.toString().orEmpty()),
            ),
        )
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray())
    }

    /** What the server says back: the report's id, and the issue it opened (which may be missing if GitHub was down). */
    data class Result(val id: String, val issue: Int?, val url: String?)

    fun parse(body: String): Result? = runCatching {
        val o = Json.parseToJsonElement(body).jsonObject
        val id = o["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val url = o["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("https://github.com/") }
        Result(id, o["issue"]?.jsonPrimitive?.intOrNull, url)
    }.getOrNull()

    /** A short explanation for a failed send, for the tester. */
    fun failure(code: Int): String = when (code) {
        413 -> "The log is too big to send. Use Save logs under Settings › Logs and send the file to help@siansa.app."
        429 -> "Too many reports just now. Please try again in an hour."
        in 500..599 -> "The beta server had a problem. Please try again later."
        else -> "The report couldn't be sent (error $code)."
    }
}
