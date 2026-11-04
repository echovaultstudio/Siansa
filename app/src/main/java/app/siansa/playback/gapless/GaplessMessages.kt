package app.siansa.playback.gapless

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * What the phone and Siansa's TV screen (cast-receiver/index.html) say to each other while casting gaplessly (issue
 * #114), as JSON on [NAMESPACE]. The TV only plays the phone's stream, so it learns the song from the phone; the phone
 * learns from the TV where the stream is and what its remote asks for.
 *
 * Phone to TV: `now` (the song, where it is, playing or paused).
 * TV to phone: `clock` (stream n's time and whether it moves), `command` (the remote: next, prev, toggle, play,
 * pause, seek to a point of the song), `error` (stream n can't play).
 */
object GaplessMessages {
    const val NAMESPACE = "urn:x-cast:app.siansa.live"

    /** Marks the TV's stream as the phone's own (LOAD customData), so the TV screen plays it as the live mix. */
    const val LIVE_KEY = "siansaLive"
    const val STREAM_KEY = "stream"

    /** What the TV shows for the song now playing on the phone. */
    data class Now(
        val title: String,
        val artist: String,
        val album: String,
        val cover: String?,
        val positionMs: Long,
        val durationMs: Long,
        val playing: Boolean,
    )

    fun now(n: Now): String = buildJsonObject {
        put("type", "now")
        put("title", n.title)
        put("artist", n.artist)
        put("album", n.album)
        n.cover?.let { put("cover", it) }
        put("positionMs", n.positionMs.coerceAtLeast(0))
        put("durationMs", n.durationMs.coerceAtLeast(0))
        put("playing", n.playing)
    }.toString()

    /** The LOAD request's customData for stream [n]. */
    fun loadData(n: Int): String = buildJsonObject {
        put(LIVE_KEY, true)
        put(STREAM_KEY, n)
    }.toString()

    sealed class FromTv {
        /** Stream [stream] is at [timeSec] and is [moving] (playing, not waiting for data). */
        data class Clock(val stream: Int, val timeSec: Double, val moving: Boolean) : FromTv()
        /** The TV's remote: [action] is next, prev, toggle, play, pause or seek (to [toMs] of the song). */
        data class Command(val action: String, val toMs: Long?) : FromTv()
        /** Stream [stream] failed to play. */
        data class Error(val stream: Int, val code: String, val message: String) : FromTv()
        data class Other(val text: String) : FromTv()
    }

    val ACTIONS = setOf("next", "prev", "toggle", "play", "pause", "seek")

    fun parse(text: String): FromTv {
        val o: JsonObject = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return FromTv.Other(text)
        fun str(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun prim(k: String) = o[k] as? JsonPrimitive
        return when (str("type")) {
            "clock" -> {
                val stream = prim("stream")?.intOrNull
                val time = prim("time")?.doubleOrNull
                if (stream == null || time == null || time.isNaN() || time < 0) FromTv.Other(text)
                else FromTv.Clock(stream, time, prim("moving")?.booleanOrNull ?: false)
            }
            "command" -> {
                val action = str("action")
                if (action == null || action !in ACTIONS) FromTv.Other(text)
                else FromTv.Command(action, prim("toMs")?.longOrNull?.coerceAtLeast(0))
            }
            "error" -> FromTv.Error(prim("stream")?.intOrNull ?: -1, prim("code")?.content ?: "?", str("message").orEmpty())
            else -> FromTv.Other(text)
        }
    }
}
