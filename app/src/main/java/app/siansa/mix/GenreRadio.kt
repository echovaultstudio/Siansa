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
 * The genre's songs are Siansa Beats' vote when it's there, the songs' genre tags otherwise ([songsOf]).
 */
class GenreRadio(private val graph: AppGraph) {
    private class Session(val name: String, val ids: List<String>, val used: MutableSet<String>, var lastId: String) {
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

    /** Every song of genre [name] on the phone: Siansa Beats' vote, or the songs' genre tags. */
    suspend fun songsOf(name: String): List<Song> = withContext(Dispatchers.IO) {
        val voted = graph.genreVote.songIds(name, MOST_SONGS)?.let { ids ->
            graph.library.keepSyncedSongs(graph.library.songs(ids), "Genre").distinctBy { it.id }
        }
        GenreVote.pick(name, voted) { graph.library.songsOfGenre(name, MOST_SONGS) }
    }

    /**
     * Starts genre [name]'s radio from its [songs] (the page's list): [first] first when given, then random picks to
     * make [PICKS]. Must be called on the main thread (the player).
     */
    fun start(name: String, songs: List<Song>, first: Song? = null) {
        if (songs.isEmpty()) return
        val used = LinkedHashSet<String>()
        val picks = firstPicks(songs, first, PICKS, Random.Default)
        picks.forEach { used += it.id }
        graph.songRadio.stop()
        session = Session(name, songs.map { it.id }, used, picks.last().id)
        graph.player.play(picks)
        AppLog.i("Radio", "Genre radio $name: ${picks.size} of ${songs.size} songs" + (first?.let { ", from \"${it.title}\"" } ?: ""))
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
                val ids = nextPicks(s.ids, s.used, PICKS, Random.Default)
                if (ids.isEmpty()) return@launch
                val songs = withContext(Dispatchers.IO) { graph.library.songs(ids) }.associateBy { it.id }
                val next = ids.mapNotNull { songs[it] }
                if (next.isEmpty() || session !== s) return@launch
                next.forEach { s.used += it.id }
                graph.player.addAll(next)
                s.lastId = next.last().id
                AppLog.i("Radio", "Genre radio ${s.name}: added ${next.size} more")
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
        /** The most songs of a genre read for its page and its radio. */
        const val MOST_SONGS = 5_000

        /** [first] (when given), then random songs of [songs] to make [count]. */
        fun firstPicks(songs: List<Song>, first: Song?, count: Int, random: Random): List<Song> {
            val rest = songs.filter { it.id != first?.id }.shuffled(random)
            return (listOfNotNull(first) + rest).distinctBy { it.id }.take(count)
        }

        /**
         * The next [count] song ids from [ids] not [used] yet, at random. Once every song has been played, it starts over,
         * leaving out the last few played so a song doesn't come straight back.
         */
        fun nextPicks(ids: List<String>, used: MutableSet<String>, count: Int, random: Random): List<String> {
            var fresh = ids.filter { it !in used }
            if (fresh.isEmpty()) {
                val recent = used.toList().takeLast(minOf(ids.size / 2, PICKS * 2)).toSet()
                used.retainAll(recent)
                fresh = ids.filter { it !in used }
            }
            return fresh.shuffled(random).take(count)
        }
    }
}
