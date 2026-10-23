package app.musix.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Where to pick up an album you stopped part way through (issue #367): the song you heard last, when you
 * heard it, and the song to carry on from.
 */
data class PickUp(val lastIndex: Int, val at: Long, val continueIndex: Int)

object PickUps {
    /** A marker older than this goes: the album looks as it always did. */
    const val KEEP_MS = 7 * 24 * 3_600_000L

    /** Two plays further apart than this weren't one listen of the album. */
    const val SAME_LISTEN_MS = 6 * 3_600_000L

    /**
     * Where to pick up [songs] (an album, in its order) from this phone's [history] (newest first), or null.
     *
     * Only for an album you were listening to, not a song of it that came up on a radio or shuffle: the
     * play before the last one has to be from the album too. Nothing once you've heard the album to its
     * end, or after [KEEP_MS].
     */
    fun find(songs: List<Song>, history: List<PlayEvent>, now: Long): PickUp? {
        if (songs.size < 2) return null
        val positions = HashMap<String, Int>()
        songs.forEachIndexed { i, s -> positions.putIfAbsent(s.id, i) }
        val i = history.indexOfFirst { it.id in positions }
        if (i < 0) return null
        val last = history[i]
        if (now - last.at > KEEP_MS) return null
        val before = history.getOrNull(i + 1) ?: return null
        if (before.id !in positions || last.at - before.at > SAME_LISTEN_MS) return null
        val at = positions.getValue(last.id)
        val heardOut = heardOut(last, songs[at].durationSec)
        if (heardOut && at == songs.lastIndex) return null
        return PickUp(lastIndex = at, at = last.at, continueIndex = if (heardOut) at + 1 else at)
    }

    /** Heard to (nearly) its end, so carrying on starts at the next song. */
    fun heardOut(play: PlayEvent, durationSec: Int): Boolean {
        val heard = play.listenedMs ?: return false
        return !play.skipped && (durationSec <= 0 || heard >= durationSec * 900L)
    }

    /** "today", "yesterday", "3 days ago". */
    fun whenLabel(at: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val days = ChronoUnit.DAYS.between(dayOf(at, zone), dayOf(now, zone))
        return when {
            days <= 0 -> "today"
            days == 1L -> "yesterday"
            else -> "$days days ago"
        }
    }

    /** The number shown for carrying on: the track number, with the disc first on an album of several. */
    fun trackLabel(songs: List<Song>, index: Int): String {
        val song = songs[index]
        val track = song.track?.toString() ?: "${index + 1}"
        val discs = songs.mapTo(HashSet()) { it.disc ?: 1 }
        return if (discs.size > 1) "${song.disc ?: 1}-$track" else track
    }

    private fun dayOf(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
}
