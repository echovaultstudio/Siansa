package app.siansa.playback.dlna

import androidx.media3.common.Player
import java.net.URI
import java.net.URLDecoder

/**
 * The queue rules a DLNA TV or speaker plays by, kept apart from the player so they're tested without a device
 * (beta testers #23).
 *
 * Songs change without a gap when the device takes the next song ahead (UPnP SetNextAVTransportURI): it then
 * moves on by itself and the app only notices that its song address changed. Devices that refuse it get the
 * next song when the current one stops, as before.
 */
internal object DlnaQueue {
    /** The song after [index] in a queue of [size] with [repeat]; -1 when the queue ends there. */
    fun nextIndex(index: Int, size: Int, repeat: Int): Int = when {
        size <= 0 -> -1
        repeat == Player.REPEAT_MODE_ONE -> index.coerceIn(0, size - 1)
        index + 1 < size -> index + 1
        repeat == Player.REPEAT_MODE_ALL -> 0
        else -> -1
    }

    /**
     * Whether two song addresses ask for the same song. Devices hand addresses back with their parts in another
     * order or encoded differently, so the path and the song's own parameters are compared; the login (u, t, s,
     * api_key) and client details aren't.
     */
    fun sameStream(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        if (a.trim() == b.trim()) return true
        val ka = key(a) ?: return false
        val kb = key(b) ?: return false
        return ka == kb
    }

    /** True when a device's refusal means it can't take the next song ahead at all (not a passing hiccup). */
    fun nextRefused(message: String?): Boolean {
        val m = message ?: return false
        // 401 Invalid action, 602 Optional action not implemented; some just answer HTTP 500 without a fault.
        return "refused" in m && (" 401" in m || " 602" in m || "HTTP 500" in m || "HTTP 501" in m || "HTTP 405" in m)
    }

    /**
     * How long to wait before asking the device again: often near a song's end, so a device that can't take the
     * next song ahead still gets it within a fraction of a second.
     */
    fun pollDelayMs(playing: Boolean, positionMs: Long, durationMs: Long): Long {
        if (!playing || durationMs <= 0) return POLL_MS
        return if (durationMs - positionMs <= NEAR_END_MS) NEAR_END_POLL_MS else POLL_MS
    }

    /**
     * Whether the device is gone: [failures] missed answers in a row, the last answer [sinceAnswerMs] ago. A few
     * misses happen on busy Wi-Fi; but once it hasn't answered for [LOST_AFTER_MS] (and missed at least
     * [MIN_FAILURES]), or missed [MAX_FAILURES] in a row, it's gone. Each try can take up to the request timeouts
     * (4 s to connect, 8 s to answer), so a count alone could take over a minute.
     */
    fun gone(failures: Int, sinceAnswerMs: Long): Boolean =
        failures >= MAX_FAILURES || (failures >= MIN_FAILURES && sinceAnswerMs >= LOST_AFTER_MS)

    const val MIN_FAILURES = 3
    const val MAX_FAILURES = 8
    const val LOST_AFTER_MS = 15_000L

    const val POLL_MS = 1_000L
    const val NEAR_END_POLL_MS = 250L
    const val NEAR_END_MS = 4_000L

    private val IGNORED = setOf("u", "t", "s", "p", "api_key", "c", "v", "f")

    private fun key(url: String): String? = runCatching {
        val uri = URI(url.trim().replace("&amp;", "&"))
        val params = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.mapNotNull { part ->
            val eq = part.indexOf('=')
            val name = decode(if (eq < 0) part else part.substring(0, eq))
            if (name in IGNORED) null else name to decode(if (eq < 0) "" else part.substring(eq + 1))
        }.sortedWith(compareBy({ it.first }, { it.second }))
        "${uri.host?.lowercase()}:${uri.port}${uri.path}?" + params.joinToString("&") { "${it.first}=${it.second}" }
    }.getOrNull()

    private fun decode(text: String): String = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)
}
