package app.musix.data

import android.content.Context
import app.musix.mix.VibeTuning
import app.musix.net.Steer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

/**
 * A vibe made on Dscvr (issue #339): what you typed (empty for one made from the sliders alone), what the AI
 * made of it ([title], [description], the sound search [query] and the concepts it [steered] by), how it's
 * [tuning], and the songs it found, so opening it again plays the same songs and moving a slider re-runs the
 * search without asking the AI again. [pinned] ones also show in Home's carousel.
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
    val query: String = "",
    val steered: List<Steer> = emptyList(),
    val tuning: VibeTuning = VibeTuning(),
    val ai: Boolean = false,
)

/**
 * [vibe] at the top of [list]. The same words typed again replace the older vibe (keeping its id and pin), as
 * does a vibe with the same id (tuned again); only the [keep] newest unpinned vibes stay, pinned ones always.
 * Vibes made from the sliders alone have no words, so they never replace each other.
 */
fun withVibe(list: List<SavedVibe>, vibe: SavedVibe, keep: Int = SavedVibes.KEEP): List<SavedVibe> {
    val key = vibeKey(vibe.text)
    val same = list.firstOrNull { it.id == vibe.id } ?: list.firstOrNull { key.isNotEmpty() && vibeKey(it.text) == key }
    val merged = if (same != null) vibe.copy(id = same.id, pinned = same.pinned || vibe.pinned) else vibe
    val rest = list.filterNot { it.id == merged.id || (key.isNotEmpty() && vibeKey(it.text) == key) }
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

    /** Saves a vibe (new when [vibe] has no id yet) and gives it back as kept. */
    suspend fun save(vibe: SavedVibe): SavedVibe {
        val made = if (vibe.id.isEmpty()) vibe.copy(id = UUID.randomUUID().toString().take(8)) else vibe
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
