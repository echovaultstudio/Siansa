package app.siansa.mix

import app.siansa.AppGraph
import app.siansa.data.Song
import app.siansa.log.AppLog
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
 * How far a song radio may wander from the song it started from (issue #256), set in Settings › Playback.
 */
enum class RadioDrift(val label: String, val hint: String) {
    CLOSE("Stay close", "Every song is like the one the radio started from."),
    SOME("A little", "Mostly like the first song, with a few steps away from it."),
    FREE("Wander", "Each new batch follows the song playing, so the radio drifts.");

    companion object {
        fun parse(name: String?): RadioDrift = entries.firstOrNull { it.name == name } ?: SOME
    }
}

/** Where a top-up looks for songs: like the first song, like the one playing, or like the one playing but kept near the first. */
enum class RadioAnchor { SEED, CURRENT, CURRENT_NEAR_SEED }

/** The anchor for the [topUp]th top-up (0 first): "A little" alternates, starting from the first song. */
fun radioAnchor(drift: RadioDrift, topUp: Int): RadioAnchor = when (drift) {
    RadioDrift.CLOSE -> RadioAnchor.SEED
    RadioDrift.SOME -> if (topUp % 2 == 0) RadioAnchor.SEED else RadioAnchor.CURRENT_NEAR_SEED
    RadioDrift.FREE -> RadioAnchor.CURRENT
}

/**
 * Songs like the one playing that are also among the songs like the first one, in the order they're like the one
 * playing, leaving out ones already heard. Null when fewer than [atLeast] are left, so the caller goes back to the
 * first song.
 */
fun nearSeed(likeCurrent: List<String>, likeSeed: Collection<String>, heard: Set<String>, count: Int, atLeast: Int = 5): List<String>? {
    val seedSet = likeSeed.toHashSet()
    val kept = likeCurrent.filter { it in seedSet && it !in heard }.distinct().take(count)
    return kept.takeIf { it.size >= atLeast }
}

/**
 * A song radio that keeps going: AudioMuse finds songs that sound like the seed and share its mood. The
 * seed always plays first. Whenever only a few songs are left, more are found, from the seed or the song
 * playing, as far as [RadioDrift] allows. Without AudioMuse, Navidrome's similar songs are used. Playing
 * anything else ends the radio.
 */
class SongRadio(private val graph: AppGraph) {
    private val _seed = MutableStateFlow<Song?>(null)
    /** The song the radio started from, or null when no radio is on. */
    val seed: StateFlow<Song?> = _seed.asStateFlow()

    /** How the radio picks (issue #337): Steady, songs in the order they're alike; DJ, ordered for smooth changes. */
    @Volatile var mode: ContinuousMode = ContinuousMode.STEADY
        private set

    private var watcher: Job? = null
    private val heard = LinkedHashSet<String>()
    private var topping = false
    private var topUps = 0

    init {
        graph.player.onNewQueue = { stop() }
    }

    /**
     * Starts from [song] in [mode] (Steady or DJ, issue #337): if it's playing, it keeps playing and the radio
     * follows; otherwise it plays first. Only the radio's songs follow it, marked in the queue as added by the
     * radio, and more are added as it nears the end, so the music doesn't stop.
     */
    fun start(song: Song, mode: ContinuousMode = ContinuousMode.STEADY, onError: (String) -> Unit = {}) {
        stop()
        val picked = if (mode == ContinuousMode.OFF) ContinuousMode.STEADY else mode
        graph.scope.launch {
            val started = System.currentTimeMillis()
            val similar = runCatching { arrange(picked, song, find(song, 30)) }
                .onFailure { AppLog.w("SongRadio", "Couldn't find similar songs: ${it.message}") }
                .getOrDefault(emptyList())
            if (similar.isEmpty()) {
                withContext(Dispatchers.Main) { onError("No similar songs found for ${song.title}") }
                return@launch
            }
            AppLog.i(
                "SongRadio",
                "Started (${picked.label}) from ${song.id} \"${song.title}\" with ${similar.size} songs in ${System.currentTimeMillis() - started}ms",
            )
            withContext(Dispatchers.Main) {
                val playingSeed = graph.player.state.value.current?.id == song.id
                if (playingSeed) {
                    graph.player.replaceUpcoming(similar, auto = true)
                } else {
                    // The first song as chosen, then the radio's picks, marked as such in the queue.
                    graph.player.play(listOf(song))
                    graph.player.addAll(similar, auto = true)
                }
                this@SongRadio.mode = picked
                heard.clear()
                topUps = 0
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
                        val seed = _seed.value ?: return@collectLatest
                        val drift = graph.settings.current.radioDrift
                        val anchor = radioAnchor(drift, topUps++)
                        val found = topUp(seed, current, anchor).filter { it.id !in heard }
                        // DJ carries on from the last song queued, for a smooth change into the new ones.
                        val tail = graph.player.state.value.queue.lastOrNull() ?: current
                        val more = arrange(mode, tail, found)
                        AppLog.i(
                            "SongRadio",
                            "Topped up with ${more.size} songs (${drift.label.lowercase()}, ${anchor.name.lowercase()}) " +
                                "like \"${if (anchor == RadioAnchor.SEED) seed.title else current.title}\"",
                        )
                        heard += more.map { it.id }
                        if (more.isNotEmpty()) withContext(Dispatchers.Main) { graph.player.addAll(more, auto = true) }
                    } catch (e: Exception) {
                        AppLog.w("SongRadio", "Top-up failed: ${e.message}")
                    } finally {
                        topping = false
                    }
                }
        }
    }

    /** The next songs, by [anchor]; when the playing song has little left near the first, the first song's. */
    private suspend fun topUp(seed: Song, current: Song, anchor: RadioAnchor): List<Song> = when (anchor) {
        RadioAnchor.CURRENT -> find(current, TOP_UP)
        // Everything like the first song heard already: carry on from the one playing rather than stop.
        RadioAnchor.SEED -> find(seed, TOP_UP).ifEmpty { find(current, TOP_UP) }
        RadioAnchor.CURRENT_NEAR_SEED -> {
            // Wide enough around the first song to still find new ones after a long listen.
            val likeSeed = graph.sonic.similar(seed.id, (heard.size + 200).coerceAtMost(MAX_POOL)).map { it.id }
            val likeCurrent = graph.sonic.similar(current.id, 80).map { it.id }
            val ids = nearSeed(likeCurrent, likeSeed, heard + current.id, TOP_UP)
            if (ids != null) graph.generator.resolveSongs(ids) else find(seed, TOP_UP)
        }
    }

    /** [songs] in DJ order after [after] when the radio is in DJ mode; otherwise as found (most alike first). */
    private suspend fun arrange(mode: ContinuousMode, after: Song, songs: List<Song>): List<Song> =
        if (mode == ContinuousMode.DJ) graph.continuousPlay.djArrange(after, songs) else songs

    private suspend fun find(song: Song, count: Int): List<Song> {
        // Asks for enough to have [count] left after leaving out what's been heard (the seed's list repeats).
        val ids = graph.sonic.similar(song.id, (count + 10 + heard.size).coerceAtMost(MAX_POOL)).map { it.id }
        return graph.generator.resolveSongs(ids.filter { it != song.id && it !in heard }).take(count)
    }

    private companion object {
        const val TOP_UP = 20
        const val MAX_POOL = 500
    }
}
