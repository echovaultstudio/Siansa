package app.musix.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.time.LocalDate

/** A small JSON file kept in memory once read, written atomically on every change. */
class JsonFile<T : Any>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<T?>(null)
    val state: StateFlow<T?> = _state.asStateFlow()

    suspend fun read(): T = withContext(Dispatchers.IO) {
        mutex.withLock { _state.value ?: load().also { _state.value = it } }
    }

    suspend fun update(change: (T) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = change(_state.value ?: load())
            write(next)
            _state.value = next
            next
        }
    }

    private fun load(): T = try {
        if (file.exists()) AppJson.decodeFromString(serializer, file.readText()) else default()
    } catch (e: Exception) {
        default()
    }

    private fun write(value: T) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(AppJson.encodeToString(serializer, value))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}

class SelectorStore(context: Context) {
    private val json = JsonFile(
        File(context.filesDir, "selectors.json"),
        ListSerializer(Selector.serializer()),
    ) { DefaultSelectors.all }

    val state: StateFlow<List<Selector>?> = json.state

    suspend fun all(): List<Selector> = json.read()

    suspend fun get(id: String): Selector? = all().firstOrNull { it.id == id }

    suspend fun save(selector: Selector) {
        json.update { list ->
            if (list.any { it.id == selector.id }) list.map { if (it.id == selector.id) selector else it }
            else list + selector
        }
    }

    suspend fun delete(id: String) {
        json.update { list -> list.filterNot { it.id == id } }
    }

    suspend fun resetToDefaults() {
        json.update { DefaultSelectors.all }
    }
}

class MixStore(context: Context) {
    private val json = JsonFile(
        File(context.filesDir, "mixes.json"),
        ListSerializer(Mix.serializer()),
    ) { emptyList() }

    val state: StateFlow<List<Mix>?> = json.state

    suspend fun all(): List<Mix> = json.read()

    suspend fun get(id: String): Mix? = all().firstOrNull { it.id == id }

    /** Saves a mix and drops mixes older than two weeks. */
    suspend fun save(mix: Mix) {
        val cutoff = LocalDate.now().minusDays(14).toString()
        json.update { list ->
            (listOf(mix) + list.filterNot { it.id == mix.id }).filter { it.date >= cutoff }
        }
    }

    /** Songs in this selector's mixes from the last [days] days, so mixes don't repeat. */
    suspend fun recentSongIds(selectorId: String, days: Int, excludeId: String): Set<String> {
        val cutoff = LocalDate.now().minusDays(days.toLong()).toString()
        return all()
            .filter { it.selectorId == selectorId && it.id != excludeId && it.date >= cutoff }
            .flatMap { mix -> mix.songs.map { it.id } }
            .toSet()
    }

    suspend fun recentTitles(count: Int): List<String> =
        all().sortedByDescending { it.createdAt }.take(count).map { it.title }
}

class HistoryStore(context: Context) {
    private val json = JsonFile(
        File(context.filesDir, "history.json"),
        ListSerializer(PlayEvent.serializer()),
    ) { emptyList() }

    suspend fun add(event: PlayEvent) {
        json.update { list ->
            val withoutRepeat = if (list.firstOrNull()?.id == event.id) list.drop(1) else list
            (listOf(event) + withoutRepeat).take(500)
        }
    }

    suspend fun recent(count: Int): List<PlayEvent> = json.read().take(count)

    suspend fun playedSince(sinceMs: Long): Set<String> =
        json.read().filter { it.at >= sinceMs }.map { it.id }.toSet()
}

object DefaultSelectors {
    val all = listOf(
        Selector(
            id = "morning",
            name = "Morning",
            startHour = 5,
            endHour = 11,
            guidance = "A gentle start that builds to medium energy. Warm, bright and positive.",
            baseQuery = "warm bright mellow music, soft acoustic and light electronic, calm positive morning",
            avoid = listOf("dark", "aggressive", "heavy", "metal", "night", "sad", "harsh"),
        ),
        Selector(
            id = "day",
            name = "Daytime",
            startHour = 11,
            endHour = 17,
            guidance = "Focused and upbeat with steady energy, good for working or errands.",
            baseQuery = "upbeat steady groove, melodic, positive energy, driving rhythm",
            avoid = listOf("sleep", "drone", "lullaby"),
        ),
        Selector(
            id = "evening",
            name = "Evening",
            startHour = 17,
            endHour = 22,
            guidance = "Relaxed, warm and social, slowly winding down.",
            baseQuery = "relaxed warm evening groove, smooth and laid back, soulful",
            avoid = listOf("aggressive", "harsh", "screaming"),
        ),
        Selector(
            id = "night",
            name = "Night",
            startHour = 22,
            endHour = 5,
            guidance = "Calm, atmospheric and introspective with low energy.",
            baseQuery = "calm atmospheric night music, soft and slow, ambient textures",
            avoid = listOf("party", "upbeat", "aggressive", "energetic"),
        ),
    )
}
