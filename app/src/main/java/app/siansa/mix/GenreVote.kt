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
    /** Where the genres' descriptions are kept on the phone (the owner, 10 Nov: they weren't kept at all). */
    private val loadAbouts: () -> String? = { null },
    private val saveAbouts: (String) -> Unit = {},
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
    fun saved(shown: Set<String> = emptySet()): List<Genre>? {
        if (!ready()) return null
        val kept = memory ?: runCatching { load()?.let(::parseSaved) }.getOrNull()?.also { memory = it } ?: return null
        return kept.genres.takeIf { it.size >= MIN_GENRES }?.let { inLibraries(it, kept.libraries, shown) }
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
    suspend fun refresh(shown: Set<String> = emptySet()): List<Genre>? {
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
        val items = (answer["genres"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val listed = items.mapNotNull(::genreOf)
        if (listed.size < MIN_GENRES) return null
        val kept = SavedGenres(now(), listed, items.mapNotNull { o -> genreOf(o)?.let { g -> librariesOf(o)?.let { g.name to it } } }.toMap())
        memory = kept
        runCatching { save(writeSaved(kept)) }.onFailure { AppLog.w("Search", "Genres: couldn't save the list: ${it.message}") }
        AppLog.d("Search", "Genres: ${listed.size} by Siansa Beats' vote, saved")
        return inLibraries(listed, kept.libraries, shown)
    }

    /** Descriptions kept on the phone, by genre (lower case): one found, or none (Wikipedia has nothing on it). */
    private val abouts = java.util.concurrent.ConcurrentHashMap<String, KeptAbout>()
    @Volatile private var aboutsRead = false

    private fun readAbouts() {
        if (aboutsRead) return
        aboutsRead = true
        runCatching { loadAbouts()?.let(::parseAbouts) }.getOrNull()?.let { abouts.putAll(it) }
    }

    private fun keep(name: String, about: GenreAbout?) {
        abouts[name.lowercase()] = KeptAbout(about, now())
        runCatching { saveAbouts(writeAbouts(abouts)) }.onFailure { AppLog.w("Genre", "Couldn't keep the descriptions: ${it.message}") }
    }

    /** Genre [name]'s description as kept on the phone, at once: null when there's none kept (or it's none). */
    fun keptAbout(name: String): GenreAbout? {
        readAbouts()
        return abouts[name.lowercase()]?.takeIf { it.fresh(now()) }?.about
    }

    /**
     * Genre [name]'s short description, with a little of its history (Siansa Beats, from Wikipedia), for its page: null
     * without Siansa Beats or when it has none; the page shows without it then. Waits at most [WAIT_MS] * 2.
     */
    suspend fun about(name: String): GenreAbout? {
        readAbouts()
        abouts[name.lowercase()]?.takeIf { it.fresh(now()) }?.let { return it.about }
        if (!ready() || now() < backOffUntil) return null
        // Siansa Beats 0.22 answers at once with a kept one; a genre it hasn't looked up yet comes back "pending" after a
        // few seconds, and is there when asked again shortly (the owner, 10 Nov: some genre pages never showed theirs).
        var answer: JsonObject? = null
        for (attempt in 1..ABOUT_TRIES) {
            answer = try {
                withTimeout(WAIT_MS * 2) { ask(ABOUT_PATH + "?name=" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")) } as? JsonObject
            } catch (e: CancellationException) {
                if (e is kotlinx.coroutines.TimeoutCancellationException) null else throw e
            } catch (e: Exception) {
                AppLog.i("Genre", "No description of $name from Siansa Beats: ${e.message}")
                return null
            }
            val pending = (answer?.get("pending") as? JsonPrimitive)?.content == "true"
            if (answer != null && !pending) break
            if (attempt < ABOUT_TRIES) kotlinx.coroutines.delay(ABOUT_AGAIN_MS)
        }
        val status = (answer?.get("status") as? JsonPrimitive)?.content
        val o = answer?.get("about") as? JsonObject
        fun text(key: String) = (o?.get(key) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
        val about = text("summary")?.let { GenreAbout(it, text("url"), text("source") ?: "Wikipedia") }
        when {
            about != null -> {
                keep(name, about)
                AppLog.i("Genre", "$name: its description from ${about.source}, kept on the phone")
            }
            status == "missing" -> {
                keep(name, null)  // asked again after a day, not on every visit
                AppLog.i("Genre", "$name: Wikipedia has nothing on it")
            }
            status == "failed" -> AppLog.i("Genre", "$name: Siansa Beats couldn't ask Wikipedia (it tries again in half an hour)")
            answer == null || status == "pending" -> AppLog.i("Genre", "$name: its description is still being looked up")
            else -> AppLog.i("Genre", "$name: no description from Siansa Beats")
        }
        return about
    }

    companion object {
        const val PATH = PLUGIN_PREFIX + "api/genre"
        const val ABOUT_PATH = PLUGIN_PREFIX + "api/genre/about"
        /** Times a genre's description is asked for while Siansa Beats is still looking it up, [ABOUT_AGAIN_MS] apart. */
        const val ABOUT_TRIES = 5
        const val ABOUT_AGAIN_MS = 2_500L
        /** Every genre: the model has 400 styles, and the plugin gives at most this many. */
        const val MOST_GENRES = 400
        /** How long the list may take before the saved one (or the tags) stays: a page never waits longer. */
        const val WAIT_MS = 3_000L
        /** The saved list is asked for again when it's older than this. */
        const val REFRESH_MS = 30 * 60_000L
        /** Failed requests in a row before Siansa Beats is left alone for [BACK_OFF_MS]. */
        const val MISSES = 3
        const val LIST_PATH = PLUGIN_PREFIX + "api/genres"

        /**
         * [genres] as the libraries [shown] have them (the libraries chosen to sync; empty: all of them): a genre whose
         * songs are all in hidden libraries is left out, and each one's count is its songs in the shown ones. A genre
         * Siansa Beats didn't say the libraries of ([libraries] lacks it: an older Siansa Beats) stays as it is.
         */
        fun inLibraries(genres: List<Genre>, libraries: Map<String, Map<String, Int>>, shown: Set<String>): List<Genre> {
            if (shown.isEmpty() || libraries.isEmpty()) return genres
            return genres.mapNotNull { g ->
                val by = libraries[g.name] ?: return@mapNotNull g
                val n = shown.sumOf { by[it] ?: 0 }
                if (n > 0) Genre(g.name, n) else null
            }.sortedByDescending { it.songCount }
        }

        /** Fewer genres than this by vote (it has hardly started), and the search page shows the tags' genres. */
        const val MIN_GENRES = 8
        const val BACK_OFF_MS = 10 * 60_000L

        /** Fewer songs than this by vote (Siansa Beats hasn't heard many yet, or they aren't on the phone), and the tags play. */
        const val MIN_VOTED = 30

        /**
         * The songs a genre radio plays: those by vote when there are at least [MIN_VOTED] of them on the phone. With
         * fewer, those tagged with the genre ([tagged]), as without Siansa Beats, unless the tags have fewer still: a
         * genre that's only in the list by vote (Yé-Yé: 19 songs by their sound, no song tagged so) showed an empty page
         * (the owner, 10 Nov), so the voted ones play then.
         */
        suspend fun pick(name: String, voted: List<Song>?, tagged: suspend () -> List<Song>): List<Song> {
            if (voted != null && voted.size >= MIN_VOTED) return voted
            val byTags = tagged()
            if (voted == null) return byTags
            if (byTags.size >= voted.size) {
                AppLog.i("Radio", "Genre radio $name: only ${voted.size} songs by vote on the phone, so its ${byTags.size} by tags")
                return byTags
            }
            AppLog.i("Radio", "Genre radio $name: ${voted.size} songs by vote and ${byTags.size} by tags, so by vote")
            return voted
        }
    }
}

/**
 * The genre list as saved on the phone, with when it came and each genre's songs in each library (by the library's id,
 * Siansa Beats 0.20 and its plugin 0.5.3), to leave out genres only in hidden libraries.
 */
data class SavedGenres(val at: Long, val genres: List<Genre>, val libraries: Map<String, Map<String, Int>> = emptyMap())

private fun genreOf(o: JsonObject): Genre? {
    val name = (o["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return null
    return Genre(name, (o["songs"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0)
}

private fun librariesOf(o: JsonObject): Map<String, Int>? = (o["libraries"] as? JsonObject)?.mapNotNull { (id, n) ->
    (n as? JsonPrimitive)?.content?.toIntOrNull()?.let { id to it }
}?.toMap()

internal fun writeSaved(saved: SavedGenres): String = JsonObject(mapOf(
    "at" to JsonPrimitive(saved.at),
    "genres" to JsonArray(saved.genres.map { g ->
        JsonObject(buildMap {
            put("name", JsonPrimitive(g.name))
            put("songs", JsonPrimitive(g.songCount))
            saved.libraries[g.name]?.let { by -> put("libraries", JsonObject(by.mapValues { JsonPrimitive(it.value) })) }
        })
    }),
)).toString()

internal fun parseSaved(text: String): SavedGenres? {
    val o = kotlinx.serialization.json.Json.parseToJsonElement(text) as? JsonObject ?: return null
    val at = (o["at"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return null
    val items = (o["genres"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    val genres = items.mapNotNull(::genreOf)
    val libraries = items.mapNotNull { g -> genreOf(g)?.let { genre -> librariesOf(g)?.let { genre.name to it } } }.toMap()
    return SavedGenres(at, genres, libraries)
}

/** A genre's description as kept on the phone ([about] null: Wikipedia has nothing on it), and when it came. */
data class KeptAbout(val about: GenreAbout?, val at: Long) {
    /** A description is kept [ABOUT_FOUND_MS], "nothing on it" [ABOUT_MISSING_MS]; then it's asked for again. */
    fun fresh(now: Long): Boolean = now - at < if (about != null) ABOUT_FOUND_MS else ABOUT_MISSING_MS

    companion object {
        const val ABOUT_FOUND_MS = 30L * 24 * 60 * 60_000
        const val ABOUT_MISSING_MS = 24L * 60 * 60_000
    }
}

internal fun writeAbouts(kept: Map<String, KeptAbout>): String = JsonObject(kept.mapValues { (_, k) ->
    JsonObject(buildMap {
        put("at", JsonPrimitive(k.at))
        k.about?.let { a ->
            put("summary", JsonPrimitive(a.summary))
            a.url?.let { put("url", JsonPrimitive(it)) }
            put("source", JsonPrimitive(a.source))
        }
    })
}).toString()

internal fun parseAbouts(text: String): Map<String, KeptAbout> {
    val o = kotlinx.serialization.json.Json.parseToJsonElement(text) as? JsonObject ?: return emptyMap()
    return o.mapNotNull { (name, v) ->
        val k = v as? JsonObject ?: return@mapNotNull null
        val at = (k["at"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return@mapNotNull null
        val summary = (k["summary"] as? JsonPrimitive)?.content
        val about = summary?.let { GenreAbout(it, (k["url"] as? JsonPrimitive)?.content, (k["source"] as? JsonPrimitive)?.content ?: "Wikipedia") }
        name to KeptAbout(about, at)
    }.toMap()
}

/** A genre's short description and where it's from (Wikipedia, linked, as its licence asks). */
data class GenreAbout(val summary: String, val url: String?, val source: String)
