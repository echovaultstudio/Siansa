package app.siansa.mix

import app.siansa.AppGraph
import app.siansa.data.Song
import app.siansa.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * A genre's radio (the owner, 10 Nov): never the whole genre in the queue (some have thousands of songs), but
 * [PICKS] songs picked at random from it, and [PICKS] more of the same genre added as the queue comes to its end,
 * for as long as the queue is still the radio's: once you play something else, or add your own songs after it, it
 * stops adding. A song chosen on the genre's page plays first.
 *
 * The genre's songs are the ones Siansa Beats hears in it when it's there, the songs' genre tags otherwise ([songsOf]).
 * The picks prefer the songs the sound is sure of (the owner, 12 Nov: a country album had crept into Electro House
 * on a faint score): each song's chance is its score squared ([weightedPicks]), so a sure song comes up many times
 * as often as a faint one, and by the tags (no scores) every song is as likely.
 */
class GenreRadio(private val graph: AppGraph) {
    private class Session(val name: String, val ids: List<String>, val weights: Map<String, Float>, val used: MutableSet<String>, var lastId: String) {
        /** The player has the radio's songs (until then, the queue before it is still showing). */
        var seen = false
    }

    @Volatile private var session: Session? = null

    /** Whether a genre radio is adding songs: Keep playing leaves the queue's end to it. */
    val active: Boolean get() = session != null

    /** The genre's name while its radio plays, for the queue and the log. */
    val name: String? get() = session?.name

    init {
        graph.scope.launch {
            graph.player.state.map { Triple(it.left, it.queue.lastOrNull()?.id, it.hasMedia) }.distinctUntilChanged().collect { (left, lastId, hasMedia) ->
                val s = session ?: return@collect
                if (!hasMedia) return@collect
                if (!s.seen) {
                    if (lastId == s.lastId) s.seen = true else return@collect
                }
                // The queue isn't the radio's any more (something else plays, or songs were added after its picks).
                if (lastId != s.lastId) {
                    AppLog.i("Radio", "Genre radio ${s.name}: the queue changed, so no more songs are added")
                    session = null
                    return@collect
                }
                if (left <= WHEN_LEFT) topUp(s)
            }
        }
    }

    /** Every song of genre [name] on the phone: the ones Siansa Beats hears in it, or the songs' genre tags. */
    suspend fun songsOf(name: String): List<Song> = withContext(Dispatchers.IO) {
        val heard = graph.beatsGenres.songIds(name, MOST_SONGS)?.let { ids ->
            graph.library.keepSyncedSongs(graph.library.songs(ids), "Genre").distinctBy { it.id }
        }
        BeatsGenres.pick(name, heard) { graph.library.songsOfGenre(name, MOST_SONGS) }
    }

    /**
     * Starts genre [name]'s radio from its [songs] (the page's list): [first] first when given, then random picks to
     * make [PICKS]. Must be called on the main thread (the player).
     */
    fun start(name: String, songs: List<Song>, first: Song? = null) {
        if (songs.isEmpty()) return
        val used = LinkedHashSet<String>()
        val weights = graph.beatsGenres.weightsOf(name)
        val picks = firstPicks(songs, first, PICKS, Random.Default, weights)
        picks.forEach { used += it.id }
        graph.songRadio.stop()
        session = Session(name, songs.map { it.id }, weights, used, picks.last().id)
        graph.player.play(picks)
        AppLog.i("Radio", "Genre radio $name: ${picks.size} of ${songs.size} songs" + (first?.let { ", from \"${it.title}\"" } ?: "") + scoresLine(picks.map { it.id }, weights))
    }

    /** Stops adding songs (something else was chosen to play). */
    fun stop() {
        session = null
    }

    private var adding = false

    private fun topUp(s: Session) {
        if (adding) return
        adding = true
        graph.scope.launch {
            try {
                val ids = nextPicks(s.ids, s.used, PICKS, Random.Default, s.weights)
                if (ids.isEmpty()) return@launch
                val songs = withContext(Dispatchers.IO) { graph.library.songs(ids) }.associateBy { it.id }
                val next = ids.mapNotNull { songs[it] }
                if (next.isEmpty() || session !== s) return@launch
                next.forEach { s.used += it.id }
                graph.player.addAll(next)
                s.lastId = next.last().id
                AppLog.i("Radio", "Genre radio ${s.name}: added ${next.size} more" + scoresLine(next.map { it.id }, s.weights))
            } finally {
                adding = false
            }
        }
    }

    companion object {
        /** Songs a genre radio queues at a time. */
        const val PICKS = 30
        /** Songs left after the one playing when more are added. */
        const val WHEN_LEFT = 2
        /** The most songs of a genre read for its page and its radio: all of them, in any library there is. */
        const val MOST_SONGS = 50_000

        /** [first] (when given), then random songs of [songs] to make [count], the sure ones ([weights]) more often. */
        fun firstPicks(songs: List<Song>, first: Song?, count: Int, random: Random, weights: Map<String, Float> = emptyMap()): List<Song> {
            val rest = songs.filter { it.id != first?.id }.distinctBy { it.id }
            val byId = rest.associateBy { it.id }
            val picked = weightedPicks(rest.map { it.id }, weights, count, random).mapNotNull { byId[it] }
            return (listOfNotNull(first) + picked).distinctBy { it.id }.take(count)
        }

        /**
         * The next [count] song ids from [ids] not [used] yet, at random, the sure ones ([weights]) more often. Once every
         * song has been played, it starts over, leaving out the last few played so a song doesn't come straight back.
         */
        fun nextPicks(ids: List<String>, used: MutableSet<String>, count: Int, random: Random, weights: Map<String, Float> = emptyMap()): List<String> {
            var fresh = ids.filter { it !in used }
            if (fresh.isEmpty()) {
                val recent = used.toList().takeLast(minOf(ids.size / 2, PICKS * 2)).toSet()
                used.retainAll(recent)
                fresh = ids.filter { it !in used }
            }
            return weightedPicks(fresh, weights, count, random)
        }

        /**
         * [count] of [ids] at random without repeats, each one's chance its score in [weights] squared (a song without
         * a score counts as [NO_SCORE], so by the tags all are alike): a sure song at 0.6 comes up 16 times as often
         * as a faint one at 0.15. Efraimidis–Spirakis: a random key per song, raised to 1/weight, the highest kept.
         */
        fun weightedPicks(ids: List<String>, weights: Map<String, Float>, count: Int, random: Random): List<String> =
            ids.map { id -> id to Math.pow(random.nextDouble().coerceAtLeast(1e-12), 1.0 / weightOf(weights[id])) }
                .sortedByDescending { it.second }
                .take(count)
                .map { it.first }

        /** A song's chance against the others': its score squared, [NO_SCORE] without one. */
        internal fun weightOf(score: Float?): Double {
            val s = (score ?: NO_SCORE).toDouble().coerceIn(0.01, 1.0)
            return s * s
        }

        /** The score a song counts as when the genre came without scores (the tags, or Siansa Beats before 0.36). */
        const val NO_SCORE = 0.5f

        /** For the log: the picks' scores, lowest and highest, when there are any. */
        internal fun scoresLine(ids: List<String>, weights: Map<String, Float>): String {
            val scores = ids.mapNotNull { weights[it] }
            if (scores.isEmpty()) return ""
            return " · scores %.2f to %.2f, %d sure".format(java.util.Locale.ROOT, scores.min(), scores.max(), scores.count { it >= SURE })
        }

        /** A score the sound is sure of (Siansa Beats' own line). */
        const val SURE = 0.4f
    }
}
