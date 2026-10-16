package app.musix.mix

import app.musix.AppGraph
import app.musix.data.Song
import app.musix.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A song radio that keeps going: AudioMuse finds songs that sound like the seed and share its mood,
 * and whenever only a few songs are left, more are found from the song playing at that moment, so
 * the radio drifts naturally while keeping the vibe. Without AudioMuse, Navidrome's similar songs are used.
 * Playing anything else ends the radio.
 */
class SongRadio(private val graph: AppGraph) {
    private val _seed = MutableStateFlow<Song?>(null)
    /** The song the radio started from, or null when no radio is on. */
    val seed: StateFlow<Song?> = _seed.asStateFlow()

    private var watcher: Job? = null
    private val heard = LinkedHashSet<String>()
    private var topping = false

    init {
        graph.player.onNewQueue = { stop() }
    }

    /** Starts from [song]: if it's playing, it keeps playing and the radio follows; otherwise it plays first. */
    fun start(song: Song, onError: (String) -> Unit = {}) {
        stop()
        graph.scope.launch {
            val started = System.currentTimeMillis()
            val similar = runCatching { find(song, 30) }
                .onFailure { AppLog.w("SongRadio", "Couldn't find similar songs: ${it.message}") }
                .getOrDefault(emptyList())
            if (similar.isEmpty()) {
                withContext(Dispatchers.Main) { onError("No similar songs found for ${song.title}") }
                return@launch
            }
            AppLog.i("SongRadio", "Started from ${song.id} \"${song.title}\" with ${similar.size} songs in ${System.currentTimeMillis() - started}ms")
            withContext(Dispatchers.Main) {
                val playingSeed = graph.player.state.value.current?.id == song.id
                if (playingSeed) {
                    graph.player.replaceUpcoming(similar)
                } else {
                    graph.player.play(listOf(song) + similar)
                }
                heard.clear()
                heard += song.id
                heard += similar.map { it.id }
                _seed.value = song
                watch()
            }
        }
    }

    fun stop() {
        if (_seed.value != null) AppLog.i("SongRadio", "Stopped")
        watcher?.cancel()
        watcher = null
        _seed.value = null
    }

    /** Tops the queue up when three or fewer songs are left. */
    private fun watch() {
        watcher?.cancel()
        watcher = graph.scope.launch {
            graph.player.state
                .map { it.queue.size - it.index - 1 to it.current }
                .distinctUntilChanged()
                .collectLatest { (left, current) ->
                    if (left > 3 || current == null || topping || _seed.value == null) return@collectLatest
                    topping = true
                    try {
                        val more = find(current, 20).filter { it.id !in heard }
                        AppLog.i("SongRadio", "Topped up with ${more.size} songs like \"${current.title}\"")
                        heard += more.map { it.id }
                        if (more.isNotEmpty()) withContext(Dispatchers.Main) { graph.player.addAll(more) }
                    } catch (e: Exception) {
                        AppLog.w("SongRadio", "Top-up failed: ${e.message}")
                    } finally {
                        topping = false
                    }
                }
        }
    }

    private suspend fun find(song: Song, count: Int): List<Song> {
        val ids = if (graph.settings.current.audioMuseReady) {
            graph.audioMuse.similarTracks(song.id, count + 10).map { it.id }
        } else {
            graph.subsonic.similarSongs(song.id, count + 10).map { it.id }
        }
        return graph.generator.resolveSongs(ids.filter { it != song.id && it !in heard }).take(count)
    }
}
