package app.siansa.mix

import app.siansa.data.Song
import app.siansa.log.AppLog
import app.siansa.net.PLUGIN_PREFIX
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A genre radio's songs by Siansa Beats' vote (each song's sound first, its tags second), through the Siansa Beats
 * (dev) plugin on AudioMuse-AI. Without AudioMuse-AI, the plugin, Siansa Beats or its style model, [songIds] is null
 * and the radio plays by the songs' own genre tags, as it always did: Siansa works fully without any of them.
 *
 * After a failed request nothing is asked for [BACK_OFF_MS], so a missing service costs one request now and then.
 */
class GenreVote(
    private val ask: suspend (String) -> JsonElement,
    private val ready: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Volatile private var backOffUntil = 0L

    /** The ids of [name]'s songs by vote, at most [limit]; null when the vote can't be had (play by the tags). */
    suspend fun songIds(name: String, limit: Int): List<String>? {
        if (!ready() || now() < backOffUntil) return null
        val answer = try {
            ask(PATH + "?name=" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20") + "&limit=$limit") as? JsonObject
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            backOffUntil = now() + BACK_OFF_MS
            AppLog.i("Radio", "Genre radio $name: no vote from Siansa Beats (${e.message}), so by its tags")
            return null
        } ?: return null
        val ids = (answer["ids"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: return null
        val styles = (answer["styles"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.substringAfter("---") }.orEmpty()
        val genres = (answer["genres"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
        AppLog.i("Radio", "Genre radio $name: ${ids.size} songs by Siansa Beats' vote" +
            (styles + genres).takeIf { it.isNotEmpty() }?.joinToString(prefix = " (", postfix = ")").orEmpty())
        return ids
    }

    companion object {
        const val PATH = PLUGIN_PREFIX + "api/genre"
        const val BACK_OFF_MS = 10 * 60_000L

        /** Fewer songs than this by vote (Siansa Beats hasn't heard many yet, or they aren't on the phone), and the tags play. */
        const val MIN_VOTED = 30

        /**
         * The songs a genre radio plays: those by vote when there are at least [MIN_VOTED] of them on the phone, else
         * those tagged with the genre ([tagged]), as without Siansa Beats.
         */
        suspend fun pick(name: String, voted: List<Song>?, tagged: suspend () -> List<Song>): List<Song> {
            if (voted != null && voted.size >= MIN_VOTED) return voted
            if (voted != null) AppLog.i("Radio", "Genre radio $name: only ${voted.size} songs by vote on the phone, so by its tags")
            return tagged()
        }
    }
}
