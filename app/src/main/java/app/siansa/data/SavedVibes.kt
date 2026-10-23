package app.siansa.data

import android.content.Context
import app.siansa.mix.VibeTuning
import app.siansa.net.Steer
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
    /** A name of your own (issue #369); null shows the one it was made with. */
    val name: String? = null,
) {
    /** The vibe's name as shown: yours when you gave it one. */
    val shownTitle: String get() = name ?: title
}

/**
 * [vibe] at the top of [list]. The same words typed again replace the older vibe (keeping its id and pin), as
 * does a vibe with the same id (tuned again); only the [keep] newest unpinned vibes stay, pinned ones always.
 * Vibes made from the sliders alone have no words, so they never replace each other. A name given to the older
 * vibe stays (issue #369).
 */
fun withVibe(list: List<SavedVibe>, vibe: SavedVibe, keep: Int = SavedVibes.KEEP): List<SavedVibe> {
    val key = vibeKey(vibe.text)
    val same = list.firstOrNull { it.id == vibe.id } ?: list.firstOrNull { key.isNotEmpty() && vibeKey(it.text) == key }
    val merged = if (same != null) vibe.copy(id = same.id, pinned = same.pinned || vibe.pinned, name = vibe.name ?: same.name) else vibe
    val rest = list.filterNot { it.id == merged.id || (key.isNotEmpty() && vibeKey(it.text) == key) }
    var unpinned = if (merged.pinned) 0 else 1
    return listOf(merged) + rest.filter { v -> v.pinned || unpinned++ < keep }
}

/** [list] with only the [keep] newest unpinned vibes, and every pinned one (issue #369). */
fun keptVibes(list: List<SavedVibe>, keep: Int = SavedVibes.KEEP): List<SavedVibe> {
    var unpinned = 0
    return list.filter { v -> v.pinned || unpinned++ < keep }
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

    /** Every kept vibe, newest first; older ones beyond [KEEP] are let go the first time they're read (issue #369). */
    suspend fun all(): List<SavedVibe> {
        val list = json.read()
        return if (keptVibes(list) != list) json.update(::keptVibes) else list
    }

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

    /** Names the vibe [name]; blank goes back to the one it was made with (issue #369). */
    suspend fun rename(id: String, name: String) {
        val clean = name.trim().take(60).takeIf { it.isNotEmpty() }
        json.update { list -> list.map { if (it.id == id) it.copy(name = clean) else it } }
    }

    suspend fun remove(id: String) {
        json.update { list -> list.filterNot { it.id == id } }
    }

    companion object {
        /** Recent vibes kept besides the pinned ones: the last five made (issue #369). */
        const val KEEP = 5
    }
}
