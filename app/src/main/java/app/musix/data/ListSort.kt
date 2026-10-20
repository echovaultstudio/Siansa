package app.musix.data

import android.content.Context
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

/** When a song was liked, added and last played, as Navidrome says (ISO dates), kept while the app runs. */
data class SongDates(val starred: String? = null, val created: String? = null, val played: String? = null)

/**
 * The dates of every song Navidrome has sent since the app started (issue #257), so lists can be sorted by
 * them without asking again. Filled in as songs are read.
 */
object SongDatesCache {
    private val dates = ConcurrentHashMap<String, SongDates>()

    fun remember(id: String, dates: SongDates) {
        if (id.isNotEmpty() && (dates.starred != null || dates.created != null || dates.played != null)) this.dates[id] = dates
    }

    operator fun get(id: String): SongDates? = dates[id]
}

/** How a list of songs is sorted (issue #257). [DEFAULT] is the list's own order: newest liked, or the playlist's. */
enum class ListSort(val label: String) {
    DEFAULT("Default"),
    PLAYED("Last played"),
    ADDED("Date added"),
    TITLE("Title"),
    ARTIST("Artist");

    companion object {
        fun parse(name: String?): ListSort = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** An ISO date from Navidrome as milliseconds, or null. */
internal fun isoMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
}

/**
 * [songs] in [sort] order, the list's own order breaking ties. [dates] gives Navidrome's dates and
 * [heardAt] when the song was last heard in Musix; last played takes the later of the two. Songs without a
 * date go last.
 */
fun <T> sortSongs(items: List<T>, sort: ListSort, songOf: (T) -> Song, dates: (String) -> SongDates?, heardAt: (String) -> Long?): List<T> {
    if (sort == ListSort.DEFAULT) return items
    val indexed = items.withIndex()
    fun newestFirst(key: (Song) -> Long?) = indexed.sortedWith(
        compareBy<IndexedValue<T>> { key(songOf(it.value)) == null }
            .thenByDescending { key(songOf(it.value)) ?: 0L }
            .thenBy { it.index },
    )
    val sorted = when (sort) {
        ListSort.PLAYED -> newestFirst { s -> listOfNotNull(isoMillis(dates(s.id)?.played), heardAt(s.id)).maxOrNull() }
        ListSort.ADDED -> newestFirst { s -> isoMillis(dates(s.id)?.created) }
        ListSort.TITLE -> indexed.sortedWith(compareBy<IndexedValue<T>, String>(String.CASE_INSENSITIVE_ORDER) { songOf(it.value).title }.thenBy { it.index })
        ListSort.ARTIST -> indexed.sortedWith(
            compareBy<IndexedValue<T>, String>(String.CASE_INSENSITIVE_ORDER) { songOf(it.value).artist }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { songOf(it.value).album }
                .thenBy { songOf(it.value).disc ?: 0 }
                .thenBy { songOf(it.value).track ?: 0 }
                .thenBy { it.index },
        )
        ListSort.DEFAULT -> indexed.toList()
    }
    return sorted.map { it.value }
}

/** The sort picked for each kind of list ("liked", "playlist"), kept on the phone. */
class ListSortStore(context: Context) {
    private val prefs = context.getSharedPreferences("list_sort", Context.MODE_PRIVATE)

    fun get(list: String): ListSort = ListSort.parse(prefs.getString(list, null))

    fun set(list: String, sort: ListSort) {
        prefs.edit().putString(list, sort.name).apply()
    }
}
