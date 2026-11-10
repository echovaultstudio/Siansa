package app.siansa.mix

import app.siansa.data.Song
import app.siansa.log.AppLog
import app.siansa.net.PLUGIN_PREFIX
import app.siansa.data.Genre
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A genre radio's songs by Siansa Beats' vote (each song's sound first, its tags second), through the Siansa Beats
 * (dev) plugin on AudioMuse-AI. Without AudioMuse-AI, the plugin, Siansa Beats or its style model, [songIds] is null
 * and the radio plays by the songs' own genre tags, as it always did: Siansa works fully without any of them.
 *
 * After [MISSES] failed requests in a row nothing is asked for [BACK_OFF_MS], so a missing service costs a request now
 * and then, while one slow answer doesn't turn the vote off for minutes.
 *
 * The library's genre list ([genres]) is saved on the phone ([saved]) when it comes, so the search page and Home show
 * it at once and keep showing it while Siansa Beats is slow or away; it's asked for again in the background ([refresh]).
 * Without AudioMuse set up, nothing saved is shown: the tags' genres are, as always.
 */
class GenreVote(
    private val ask: suspend (String) -> JsonElement,
    private val ready: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    /** Where the list is saved: read it (null: none yet) and write it. */
    private val load: () -> String? = { null },
    private val save: (String) -> Unit = {},
) {
    @Volatile private var backOffUntil = 0L
    @Volatile private var misses = 0

    private fun missed(): Boolean {
        if (++misses >= MISSES) {
            backOffUntil = now() + BACK_OFF_MS
            misses = 0
        }
        return true
    }

    /** The ids of [name]'s songs by vote, at most [limit]; null when the vote can't be had (play by the tags). */
    suspend fun songIds(name: String, limit: Int): List<String>? {
        if (!ready() || now() < backOffUntil) return null
        val answer = try {
            ask(PATH + "?name=" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20") + "&limit=$limit") as? JsonObject
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            missed()
            AppLog.i("Radio", "Genre radio $name: no vote from Siansa Beats (${e.message}), so by its tags")
            return null
        } ?: return null
        misses = 0
        val ids = (answer["ids"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: return null
        val styles = (answer["styles"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.substringAfter("---") }.orEmpty()
        val genres = (answer["genres"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
        AppLog.i("Radio", "Genre radio $name: ${ids.size} songs by Siansa Beats' vote" +
            (styles + genres).takeIf { it.isNotEmpty() }?.joinToString(prefix = " (", postfix = ")").orEmpty())
        return ids
    }

    @Volatile private var memory: SavedGenres? = null

    /**
     * The library's genres by vote as last saved, most songs first (all of them, at once, from the phone): null when
     * AudioMuse isn't set up or Siansa Beats has never given enough, so the tags' genres are shown.
     */
    fun saved(): List<Genre>? {
        if (!ready()) return null
        val kept = memory ?: runCatching { load()?.let(::parseSaved) }.getOrNull()?.also { memory = it } ?: return null
        return kept.genres.takeIf { it.size >= MIN_GENRES }
    }

    /** Whether the saved list is older than [REFRESH_MS] (or there's none), so [refresh] is worth asking. */
    fun stale(): Boolean {
        saved() // reads it from the phone the first time
        return (memory?.at ?: 0L) + REFRESH_MS < now()
    }

    /**
     * Asks Siansa Beats for every genre by vote (waiting at most [WAIT_MS]) and saves them: the new list, or null when
     * none came (the saved one, or the tags, stay).
     */
    suspend fun refresh(): List<Genre>? {
        if (!ready() || now() < backOffUntil) return null
        val answer = try {
            withTimeout(WAIT_MS) { ask("$LIST_PATH?limit=$MOST_GENRES") } as? JsonObject
        } catch (e: CancellationException) {
            if (e is kotlinx.coroutines.TimeoutCancellationException) {
                missed()
                AppLog.i("Search", "Genres: Siansa Beats didn't answer within ${WAIT_MS / 1000} s; the saved list stays")
                return null
            }
            throw e
        } catch (e: Exception) {
            missed()
            AppLog.i("Search", "Genres: none from Siansa Beats (${e.message}); the saved list stays")
            return null
        } ?: return null
        misses = 0
        val listed = (answer["genres"] as? JsonArray)?.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val name = (o["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Genre(name, (o["songs"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0)
        }.orEmpty()
        if (listed.size < MIN_GENRES) return null
        val kept = SavedGenres(now(), listed)
        memory = kept
        runCatching { save(writeSaved(kept)) }.onFailure { AppLog.w("Search", "Genres: couldn't save the list: ${it.message}") }
        AppLog.d("Search", "Genres: ${listed.size} by Siansa Beats' vote, saved")
        return listed
    }

    private val abouts = java.util.concurrent.ConcurrentHashMap<String, GenreAbout>()

    /**
     * Genre [name]'s short description, with a little of its history (Siansa Beats, from Wikipedia), for its page: null
     * without Siansa Beats or when it has none; the page shows without it then. Waits at most [WAIT_MS] * 2.
     */
    suspend fun about(name: String): GenreAbout? {
        abouts[name.lowercase()]?.let { return it }
        if (!ready() || now() < backOffUntil) return null
        val answer = try {
            withTimeout(WAIT_MS * 2) { ask(ABOUT_PATH + "?name=" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")) } as? JsonObject
        } catch (e: CancellationException) {
            if (e is kotlinx.coroutines.TimeoutCancellationException) return null
            throw e
        } catch (e: Exception) {
            AppLog.i("Genre", "No description of $name from Siansa Beats: ${e.message}")
            return null
        } ?: return null
        val o = answer["about"] as? JsonObject ?: return null
        fun text(key: String) = (o[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
        val about = GenreAbout(text("summary") ?: return null, text("url"), text("source") ?: "Wikipedia")
        abouts[name.lowercase()] = about
        return about
    }

    companion object {
        const val PATH = PLUGIN_PREFIX + "api/genre"
        const val ABOUT_PATH = PLUGIN_PREFIX + "api/genre/about"
        /** Every genre: the model has 400 styles, and the plugin gives at most this many. */
        const val MOST_GENRES = 400
        /** How long the list may take before the saved one (or the tags) stays: a page never waits longer. */
        const val WAIT_MS = 3_000L
        /** The saved list is asked for again when it's older than this. */
        const val REFRESH_MS = 30 * 60_000L
        /** Failed requests in a row before Siansa Beats is left alone for [BACK_OFF_MS]. */
        const val MISSES = 3
        const val LIST_PATH = PLUGIN_PREFIX + "api/genres"

        /** Fewer genres than this by vote (it has hardly started), and the search page shows the tags' genres. */
        const val MIN_GENRES = 8
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

/** The genre list as saved on the phone, with when it came. */
data class SavedGenres(val at: Long, val genres: List<Genre>)

internal fun writeSaved(saved: SavedGenres): String = JsonObject(mapOf(
    "at" to JsonPrimitive(saved.at),
    "genres" to JsonArray(saved.genres.map { JsonObject(mapOf("name" to JsonPrimitive(it.name), "songs" to JsonPrimitive(it.songCount))) }),
)).toString()

internal fun parseSaved(text: String): SavedGenres? {
    val o = kotlinx.serialization.json.Json.parseToJsonElement(text) as? JsonObject ?: return null
    val at = (o["at"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return null
    val genres = (o["genres"] as? JsonArray)?.mapNotNull { item ->
        val g = item as? JsonObject ?: return@mapNotNull null
        val name = (g["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        Genre(name, (g["songs"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0)
    }.orEmpty()
    return SavedGenres(at, genres)
}

/** A genre's short description and where it's from (Wikipedia, linked, as its licence asks). */
data class GenreAbout(val summary: String, val url: String?, val source: String)
