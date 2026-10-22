package app.musix.data

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

/**
 * A vibe made on the Vibe tab (issue #339): what you typed, what the AI called it, and the songs it found, so
 * opening it again plays the same songs. [pinned] ones also show in Home's carousel.
 */
@Serializable
data class SavedVibe(
    val id: String,
    val text: String,
    val title: String,
    val description: String,
    val songs: List<Song>,
    val madeAt: Long,
    val pinned: Boolean = false,
)

/**
 * [vibe] at the top of [list]. The same words typed again replace the older vibe (keeping its id and pin), and
 * only the [keep] newest unpinned vibes stay; pinned ones always stay.
 */
fun withVibe(list: List<SavedVibe>, vibe: SavedVibe, keep: Int = SavedVibes.KEEP): List<SavedVibe> {
    val same = list.firstOrNull { vibeKey(it.text) == vibeKey(vibe.text) }
    val merged = if (same != null) vibe.copy(id = same.id, pinned = same.pinned || vibe.pinned) else vibe
    val rest = list.filterNot { it.id == merged.id || vibeKey(it.text) == vibeKey(vibe.text) }
    var unpinned = if (merged.pinned) 0 else 1
    return listOf(merged) + rest.filter { v -> v.pinned || unpinned++ < keep }
}

/** Typed words compared without case or extra spaces: "Rainy  Sunday" is "rainy sunday". */
fun vibeKey(text: String): String = text.trim().lowercase().replace(Regex("\\s+"), " ")

/** The pinned vibes, newest first, for Home's carousel. */
fun pinnedVibes(list: List<SavedVibe>): List<SavedVibe> = list.filter { it.pinned && it.songs.isNotEmpty() }

class SavedVibes(context: Context) {
    private val json = JsonFile(
        File(context.filesDir, Users.scoped("vibes.json")),
        ListSerializer(SavedVibe.serializer()),
    ) { emptyList() }

    val state: StateFlow<List<SavedVibe>?> = json.state

    suspend fun all(): List<SavedVibe> = json.read()

    suspend fun get(id: String): SavedVibe? = all().firstOrNull { it.id == id }

    /** Saves a newly made vibe and gives it back with its id (an earlier one with the same words keeps its id). */
    suspend fun save(text: String, title: String, description: String, songs: List<Song>): SavedVibe {
        val made = SavedVibe(UUID.randomUUID().toString().take(8), text.trim(), title, description, songs, System.currentTimeMillis())
        val list = json.update { withVibe(it, made) }
        return list.first()
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        json.update { list -> list.map { if (it.id == id) it.copy(pinned = pinned) else it } }
    }

    suspend fun remove(id: String) {
        json.update { list -> list.filterNot { it.id == id } }
    }

    companion object {
        /** Recent vibes kept besides the pinned ones. */
        const val KEEP = 20
    }
}
