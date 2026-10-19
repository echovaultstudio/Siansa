package app.musix.log

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** How serious an event is (issue #237). [letter] is what the text log shows ("I/Player: …"). */
enum class Level(val letter: String) {
    TRACE("T"), DEBUG("D"), INFO("I"), WARN("W"), ERROR("E"), FATAL("F");

    companion object {
        fun ofLetter(letter: String): Level = entries.firstOrNull { it.letter == letter } ?: INFO
    }
}

/**
 * One diagnostic event (issue #237): when, how serious, which part of the app ([component], e.g. "Player"),
 * what happened ([event], e.g. "playback.first_audio", or "log" for a plain message), the app run it belongs
 * to ([session]), the operation it belongs to ([trace], e.g. the song being started), the thread, a readable
 * [message], structured [fields] and an [error]. Written as one JSON line by [toJson], redacted on the way.
 */
class LogEvent(
    val timeMs: Long,
    val level: Level,
    val component: String,
    val event: String,
    val session: String,
    val trace: String?,
    val thread: String,
    val message: String,
    val fields: Map<String, Any?> = emptyMap(),
    val error: Throwable? = null,
) {
    /** One line of JSON, every string and field passed through [Redactor]. */
    fun toJson(): String = toJsonObject().toString()

    fun toJsonObject(): JsonObject {
        val out = LinkedHashMap<String, JsonElement>()
        out["timestamp"] = JsonPrimitive(utc(timeMs))
        out["level"] = JsonPrimitive(level.name)
        out["component"] = JsonPrimitive(component)
        out["event"] = JsonPrimitive(event)
        out["session_id"] = JsonPrimitive(session)
        out["trace_id"] = trace?.let { JsonPrimitive(it) } ?: JsonNull
        out["thread"] = JsonPrimitive(thread)
        out["message"] = JsonPrimitive(Redactor.text(message))
        if (fields.isNotEmpty()) out["fields"] = Redactor.json(fields)
        error?.let { out["error"] = errorJson(it) }
        return JsonObject(out)
    }

    /** The line of the readable log: "2026-10-19 19:45:15.048 I/Player: Now playing …". */
    fun toText(localTime: SimpleDateFormat): String {
        val text = Redactor.text(if (error != null) "$message\n${stack(error)}" else message)
        return "${localTime.format(Date(timeMs))} ${level.letter}/$component: $text\n"
    }

    companion object {
        private val iso = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
        }

        /** UTC with milliseconds: "2026-10-19T17:30:12.125Z". */
        fun utc(ms: Long): String = iso.get()!!.format(Date(ms))

        fun stack(error: Throwable): String = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()

        /** An error as its type, message, cause chain and stack, redacted (messages can carry addresses). */
        fun errorJson(error: Throwable): JsonObject {
            val causes = generateSequence(error.cause) { it.cause }.take(5).map {
                JsonPrimitive(Redactor.text("${it.javaClass.name}: ${it.message ?: ""}"))
            }.toList()
            return JsonObject(
                linkedMapOf(
                    "type" to JsonPrimitive(error.javaClass.name),
                    "message" to (error.message?.let { JsonPrimitive(Redactor.text(it)) } ?: JsonNull),
                    "causes" to JsonArray(causes),
                    "stack" to JsonPrimitive(Redactor.text(stack(error)).take(16_000)),
                ),
            )
        }
    }
}

/**
 * Removes passwords, tokens and keys (issue #237), field by field rather than only by searching text: a field
 * whose name says it holds a secret is replaced whatever it holds, nested maps and lists are walked, and
 * every string still goes through the text patterns (addresses with tokens, login headers, keys).
 */
object Redactor {
    const val HIDDEN = "•••"

    /** Field names that hold secrets (compared without case, "-" and "_"). */
    private val secretNames = setOf(
        "password", "passwd", "pass", "p", "token", "t", "s", "salt", "accesstoken", "refreshtoken", "sessionkey", "sk",
        "apikey", "apisig", "key", "secret", "authorization", "proxyauthorization", "cookie", "setcookie", "jwt",
        "audiomusejwt", "credentials",
    )

    fun isSecretName(name: String): Boolean = name.lowercase().replace("-", "").replace("_", "") in secretNames

    private val patterns = listOf(
        Regex("([?&](?:t|s|p|u|sk|token|apiKey|api_key|api_sig|key|secret|password)=)[^&\\s\"]+", RegexOption.IGNORE_CASE),
        Regex("(Authorization:?\\s*(?:Bearer|Basic)\\s+)\\S+", RegexOption.IGNORE_CASE),
        Regex("(audiomuse_jwt=)[^;\\s]+"),
        Regex("(sk-or-[A-Za-z0-9-]{4})[A-Za-z0-9-]+"),
        Regex("(://)[^/@\\s]+:[^/@\\s]+@"),
        Regex("(\"(?:password|token|apiKey|api_key|secret|jwt)\"\\s*:\\s*\")[^\"]*", RegexOption.IGNORE_CASE),
        Regex("(\\b(?:token|password|passwd|apiKey|api_key|secret)=)[^&\\s\"]+", RegexOption.IGNORE_CASE),
    )

    /** A string with the secrets in it replaced. */
    fun text(value: String): String = patterns.fold(value) { acc, regex ->
        regex.replace(acc) { m -> m.groupValues[1] + if (m.groupValues[1] == "://") "" else HIDDEN }
    }

    /** Structured fields as JSON, secrets replaced at any depth. */
    fun json(fields: Map<String, Any?>): JsonObject = JsonObject(fields.entries.associate { (k, v) -> k to value(k, v) })

    private fun value(name: String?, v: Any?): JsonElement = when {
        name != null && isSecretName(name) && v != null -> JsonPrimitive(HIDDEN)
        v == null -> JsonNull
        v is Number -> JsonPrimitive(v)
        v is Boolean -> JsonPrimitive(v)
        v is Enum<*> -> JsonPrimitive(v.name)
        v is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to value(k.toString(), x) })
        v is Iterable<*> -> JsonArray(v.map { value(null, it) })
        v is Array<*> -> JsonArray(v.map { value(null, it) })
        v is Throwable -> LogEvent.errorJson(v)
        else -> JsonPrimitive(text(v.toString()))
    }
}
