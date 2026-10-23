package app.musix.mix

import app.musix.AppGraph
import app.musix.data.Song
import app.musix.log.AppLog
import kotlin.random.Random

/**
 * Roll the dice (issue #347): one song from your library you've never played, picked at random. "Never played"
 * means Navidrome has no play for it and it isn't in Musix's own history either.
 */
class Dice(private val graph: AppGraph) {
    /** A song you've never played, or null when none turned up (or the server can't be reached). */
    suspend fun roll(): Song? {
        val heard = graph.history.all().map { it.id }.toHashSet()
        val onlyOnPhone = graph.settings.current.downloadedOnly
        repeat(TRIES) { attempt ->
            val batch = graph.subsonic.randomSongPlays(BATCH)
            val pick = pickNeverPlayed(batch, heard, allowed = { !onlyOnPhone || graph.downloads.isDownloaded(it.id) })
            if (pick != null) {
                AppLog.i("Dice", "Rolled ${pick.id} \"${pick.title}\" (try ${attempt + 1}, ${batch.count { it.second == 0L }} of ${batch.size} never played)")
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
