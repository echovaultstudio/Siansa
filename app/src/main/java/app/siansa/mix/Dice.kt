package app.siansa.mix

import app.siansa.AppGraph
import app.siansa.data.Song
import app.siansa.log.AppLog
import kotlin.random.Random

/**
 * Roll the dice (issue #347): one song from your library you've never played, picked at random. "Never played"
 * means Navidrome has no play for it and it isn't in Siansa's own history either.
 */
class Dice(private val graph: AppGraph) {
    /** A song you've never played, or null when none turned up (or the server can't be reached). */
    suspend fun roll(): Song? {
        val heard = graph.history.all().map { it.id }.toHashSet()
        val onlyOnPhone = graph.settings.current.downloadedOnly
        // With libraries or users hidden, or other users shown, the server's own random songs don't match what's
        // shown (beta report 37b60d15): roll from the synced library instead, where "never played" means not in
        // Siansa's history.
        val local = diceFromLibrary(graph.library.isSynced, graph.library.hidesSome(), graph.guests.shown.value.size)
        repeat(TRIES) { attempt ->
            val batch = if (local) graph.library.randomSongs(BATCH).map { it to 0L }
            else graph.source.randomSongPlays(BATCH).let { got ->
                val shown = graph.library.keepSyncedSongs(got.map { it.first }, "Dice").map { it.id }.toHashSet()
                got.filter { it.first.id in shown }
            }
            val pick = pickNeverPlayed(batch, heard, allowed = { !onlyOnPhone || graph.downloads.isDownloaded(it.id) })
            if (pick != null) {
                AppLog.i("Dice", "Rolled ${pick.id} \"${pick.title}\" (try ${attempt + 1}, ${batch.count { it.second == 0L }} of ${batch.size} never played${if (local) ", from the library shown" else ""})")
                return pick
            }
        }
        AppLog.i("Dice", "No song you haven't played in $TRIES rolls of $BATCH")
        return null
    }

    companion object {
        const val BATCH = 100
        const val TRIES = 4
    }
}

/**
 * One of [candidates] (a song and its play count) that's never been played: no play on the server, not in
 * [heard], and [allowed] (on the phone when only downloaded music shows). Null when there's none.
 */
fun pickNeverPlayed(
    candidates: List<Pair<Song, Long>>,
    heard: Set<String>,
    random: Random = Random,
    allowed: (Song) -> Boolean = { true },
): Song? = candidates
    .filter { (song, plays) -> plays == 0L && song.id !in heard && allowed(song) }
    .map { it.first }
    .takeIf { it.isNotEmpty() }
    ?.random(random)

/**
 * Whether the dice rolls from the synced library rather than the server's random songs (beta report 37b60d15):
 * when some music is hidden or other users' music is shown, only the library knows what's shown.
 */
fun diceFromLibrary(synced: Boolean, hidesSome: Boolean, shownUsers: Int): Boolean = synced && (hidesSome || shownUsers > 1)
