package app.musix.data

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** A search you made: what you typed, and whether it was a Sound search (issue #177). */
@Serializable
data class RecentSearch(val query: String, val sound: Boolean = false)

/**
 * Your recent searches, newest first, shown under the search box when you tap into it. A search counts
 * when you press the search key or open one of its results. The same words again move to the top.
 */
class SearchHistory(context: Context) {
    private val json = JsonFile(
        File(context.filesDir, Users.scoped("recent_searches.json")),
        ListSerializer(RecentSearch.serializer()),
    ) { emptyList() }

    val state: StateFlow<List<RecentSearch>?> = json.state

    suspend fun all(): List<RecentSearch> = json.read()

    suspend fun add(query: String, sound: Boolean) {
        json.update { withSearch(it, query, sound) }
    }

    suspend fun remove(search: RecentSearch) {
        json.update { list -> list.filterNot { it == search } }
    }

    suspend fun clear() {
        json.update { emptyList() }
    }

    companion object {
        /** How many recent searches are kept. */
        const val MAX = 10
        /** Shorter searches aren't kept (search starts at two letters). */
        const val MIN_LENGTH = 2

        /**
         * [list] with [query] added at the top: trimmed, spaces tidied, and an earlier search with the same
         * words (in any case) of the same kind removed. Too-short searches leave the list as it is.
         */
        fun withSearch(list: List<RecentSearch>, query: String, sound: Boolean, max: Int = MAX): List<RecentSearch> {
            val clean = query.trim().replace(Regex("\\s+"), " ")
            if (clean.length < MIN_LENGTH) return list
            val search = RecentSearch(clean, sound)
            val rest = list.filterNot { it.sound == sound && it.query.equals(clean, ignoreCase = true) }
            return (listOf(search) + rest).take(max)
        }
    }
}
