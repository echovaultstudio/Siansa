package app.siansa.mix

import app.siansa.data.normalizeKey
import app.siansa.net.Played
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The time machine (issue #186): your most played songs in any month or year, from Koito, Last.fm or
 * both. Songs that aren't in your library are listed too, with no [RankedTrack.song] (issue #234).
 */
data class TimeMachine(
    /** "March 2019", or "2019" for a whole year. */
    val label: String,
    val tracks: List<RankedTrack>,
    /** Where the plays came from, shown under the list. */
    val source: String,
    /** False when a service that's set up couldn't be reached, so this isn't the full picture. */
    val complete: Boolean = true,
    /** Services still being asked; their plays are added when they answer (issue #319). */
    val pending: List<String> = emptyList(),
) {
    /** The songs that can play, in order: the ones in your library. */
    val songs get() = tracks.mapNotNull { it.song }
    val missing get() = tracks.count { it.song == null }
}

/** The days a time machine covers: [from] inclusive, [to] exclusive. */
data class TimeWindow(val from: LocalDate, val to: LocalDate, val label: String)

/** The earliest year offered: Last.fm started counting in 2002. */
const val TIME_MACHINE_FIRST_YEAR = 2002

/** [month] 1–12, or null for the whole of [year]. */
fun timeWindow(year: Int, month: Int?, locale: Locale = Locale.getDefault()): TimeWindow {
    val from = LocalDate.of(year, month ?: 1, 1)
    val to = if (month == null) from.plusYears(1) else from.plusMonths(1)
    val label = if (month == null) "$year" else "${from.month.getDisplayName(TextStyle.FULL, locale)} $year"
    return TimeWindow(from, to, label)
}

/** The name of a time machine's playlist ("Create memory"), as the issue asks: "Time Machine - March 2019". */
fun memoryName(label: String) = "Time Machine - $label"

/** Months of [year] you can travel to by [today]: none in the future. */
fun monthsAvailable(year: Int, today: LocalDate): IntRange = when {
    year < today.year -> 1..12
    year == today.year -> 1..today.monthValue
    else -> IntRange.EMPTY
}

/**
 * One list from several services' plays, most played first. The same listen is often sent to both Koito
 * and Last.fm, so a song's count is the higher of the two, never the sum. Songs are matched by artist and
 * title in any case and spacing; the first service's spelling is kept.
 */
fun mergePlays(vararg lists: List<Played>): List<Played> {
    val best = LinkedHashMap<String, Played>()
    for (list in lists) for (p in list) {
        val title = p.title ?: continue
        val key = normalizeKey(p.artist) + "|" + normalizeKey(title)
        val seen = best[key]
        if (seen == null || p.count > seen.count) best[key] = (seen ?: p).copy(count = maxOf(p.count, seen?.count ?: 0))
    }
    return best.values.sortedByDescending { it.count }
}

/**
 * [plays] in order, each with its library match or null when it isn't in the library (issue #234), until
 * [limit] of them are in the library. So hiding the missing ones still leaves as many songs as before.
 */
internal suspend fun <T, R : Any> rankWithMissing(plays: List<T>, limit: Int, find: suspend (T) -> R?): List<Pair<T, R?>> {
    val ranked = ArrayList<Pair<T, R?>>()
    var found = 0
    for (p in plays) {
        if (found >= limit) break
        val match = find(p)
        if (match != null) found++
        ranked += p to match
    }
    return ranked
}

/** A month or year that has ended: its plays never change, so they're kept on the phone for good (issue #319). */
fun periodFinished(window: TimeWindow, today: LocalDate): Boolean = !window.to.isAfter(today)

/** The line under the list: where the plays came from, and what's still being added (issue #319). */
fun timeMachineSource(used: List<String>, pending: List<String>): String =
    "From ${used.joinToString(" and ")}" + if (pending.isEmpty()) "" else " · adding plays from ${pending.joinToString(" and ")}…"
