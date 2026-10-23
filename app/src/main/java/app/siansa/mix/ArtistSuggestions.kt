package app.siansa.mix

import android.content.Context
import app.siansa.AppGraph
import app.siansa.data.AppJson
import app.siansa.data.normalizeKey
import app.siansa.log.AppLog
import app.siansa.net.DeezerArtist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** An artist you don't have yet, suggested because it's like artists you play. */
@Serializable
data class SuggestedArtist(
    val name: String,
    val photo: String,
    val albums: Int = 0,
    val fans: Int = 0,
    /** Deezer page, to listen or read more. */
    val link: String? = null,
    /** The artists you like that it's similar to, most liked first. */
    val because: List<String> = emptyList(),
)

@Serializable
private data class SavedSuggestions(val date: String, val artists: List<SuggestedArtist>)

/**
 * "Artists you might like": artists outside the library that are similar to the ones you play most.
 *
 * Similar artists come from Navidrome (its Last.fm agent, asked to include artists you don't have), and
 * from Deezer when Navidrome has too few. Deezer also gives each one a photo, its number of albums and
 * fans; artists without a photo are left out. Made once a day and kept on the phone.
 */
class ArtistSuggestions(private val graph: AppGraph) {
    private val lock = Mutex()
    private val prefs by lazy { graph.app.getSharedPreferences(app.siansa.data.Users.scoped("artist_suggestions"), Context.MODE_PRIVATE) }
    @Volatile private var memory: SavedSuggestions? = null

    suspend fun today(): List<SuggestedArtist> = lock.withLock {
        val date = LocalDate.now().toString()
        val saved = memory ?: load()
        if (saved != null && saved.date == date) return saved.artists
        try {
            val artists = build()
            SavedSuggestions(date, artists).also { memory = it; save(it) }
            artists
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Suggest", "Couldn't find new artists: ${e.message}")
            saved?.artists.orEmpty()
        }
    }

    private suspend fun build(): List<SuggestedArtist> {
        if (!graph.library.isSynced) return emptyList()
        val seeds = graph.taste.profile().topArtists.take(SEEDS)
        if (seeds.isEmpty()) return emptyList()

        // Navidrome first: it already knows these artists and asks Last.fm for similar ones.
        val similar = ArrayList<Pair<String, List<String>>>()
        val inLibrary = HashSet<String>()
        var fromNavidrome = 0
        for (seed in seeds) {
            val local = graph.library.artistByName(seed) ?: continue
            val info = runCatching { graph.subsonic.artistInfo(local.id, includeNotPresent = true) }
                .onFailure { AppLog.d("Suggest", "No similar artists for $seed from Navidrome: ${it.message}") }
                .getOrNull() ?: continue
            for (a in info.similar) {
                val known = a.id?.let { graph.library.artist(it) } ?: graph.library.artistByName(a.name)
                if (known != null) inLibrary.add(normalizeKey(a.name))
            }
            similar.add(seed to info.similar.map { it.name })
            fromNavidrome += info.similar.size
        }

        // Deezer as well when Navidrome found little outside the library (no Last.fm agent, say).
        val deezer = HashMap<String, DeezerArtist>()
        var fromDeezer = 0
        if (SuggestionRanking.rank(similar, seeds, inLibrary::contains, LIMIT).size < LIMIT / 2) {
            for (seed in seeds.take(DEEZER_SEEDS)) {
                val related = runCatching { graph.deezer.findArtist(seed)?.let { graph.deezer.related(it.id) } }
                    .onFailure { AppLog.d("Suggest", "No similar artists for $seed from Deezer: ${it.message}") }
                    .getOrNull().orEmpty()
                related.forEach { a ->
                    deezer.putIfAbsent(normalizeKey(a.name), a)
                    if (graph.library.artistByName(a.name) != null) inLibrary.add(normalizeKey(a.name))
                }
                if (related.isNotEmpty()) similar.add(seed to related.map { it.name })
                fromDeezer += related.size
            }
        }

        val ranked = SuggestionRanking.rank(similar, seeds, inLibrary::contains, LOOKUPS)
        val result = ArrayList<SuggestedArtist>()
        val shown = HashSet<Long>()
        for (c in ranked) {
            if (result.size >= LIMIT) break
            val found = deezer[normalizeKey(c.name)] ?: runCatching { graph.deezer.findArtist(c.name) }.getOrNull()
            val photo = found?.photo ?: continue
            // Two spellings can lead to the same Deezer artist; Home needs each one once.
            if (!shown.add(found.id) || result.any { normalizeKey(it.name) == normalizeKey(found.name) }) continue
            if (graph.library.artistByName(found.name) != null) continue
            result.add(SuggestedArtist(found.name, photo, found.albums, found.fans, found.link, c.because))
        }
        AppLog.i(
            "Suggest",
            "${result.size} artists you might like, from ${seeds.size} you play most " +
                "(similar: $fromNavidrome from Navidrome, $fromDeezer from Deezer; ${inLibrary.size} already in the library)",
        )
        return result
    }

    private fun load(): SavedSuggestions? = runCatching {
        prefs.getString(KEY, null)?.let { AppJson.decodeFromString(SavedSuggestions.serializer(), it) }
    }.getOrNull()?.also { memory = it }

    private fun save(s: SavedSuggestions) {
        prefs.edit().putString(KEY, AppJson.encodeToString(SavedSuggestions.serializer(), s)).apply()
    }

    private companion object {
        const val KEY = "today"
        const val SEEDS = 6
        const val DEEZER_SEEDS = 4
        const val LIMIT = 12
        /** Candidates looked up on Deezer for a photo: more than shown, since some have none. */
        const val LOOKUPS = 20
    }
}

/** Ranks similar artists across the artists you like. Pure, so it can be tested. */
object SuggestionRanking {
    data class Candidate(val name: String, val because: List<String>, val score: Double)

    /**
     * [similar]: for each artist you like (most liked first), the artists similar to it, most similar first.
     * Artists you like and artists [inLibrary] (by normalised name) are left out. An artist similar to
     * several you like comes first; then the more you like the artist, and the more similar, the higher.
     */
    fun rank(
        similar: List<Pair<String, List<String>>>,
        seeds: List<String>,
        inLibrary: (String) -> Boolean,
        limit: Int,
    ): List<Candidate> {
        val seedKeys = seeds.map(::normalizeKey).toSet()
        val seedRank = seeds.withIndex().associate { normalizeKey(it.value) to it.index }
        val names = HashMap<String, String>()
        val scores = HashMap<String, Double>()
        val because = HashMap<String, LinkedHashSet<String>>()
        for ((seed, list) in similar) {
            val seedWeight = 1.0 / (1 + 0.25 * (seedRank[normalizeKey(seed)] ?: seeds.size))
            list.withIndex().forEach { (i, name) ->
                val key = normalizeKey(name)
                if (key.isEmpty() || key in seedKeys || inLibrary(key)) return@forEach
                // The same seed listing an artist twice (Navidrome and Deezer) counts once, at its best.
                val reasons = because.getOrPut(key) { LinkedHashSet() }
                val closeness = 1.0 - i.coerceAtMost(19) / 20.0
                if (!reasons.add(seed)) return@forEach
                names.putIfAbsent(key, name)
                scores[key] = (scores[key] ?: 0.0) + seedWeight * closeness
            }
        }
        return scores.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { because[it.key]!!.size }.thenByDescending { it.value })
            .take(limit)
            .map { e ->
                val reasons = because[e.key]!!.sortedBy { seedRank[normalizeKey(it)] ?: Int.MAX_VALUE }
                Candidate(names[e.key]!!, reasons, e.value)
            }
    }
}
