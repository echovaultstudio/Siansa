package app.musix.mix

import app.musix.AppGraph
import app.musix.data.Song
import app.musix.log.AppLog
import app.musix.net.PathMood
import app.musix.playback.leftMs
import app.musix.playback.windDownLength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The sleep timer's wind down (issue #153): as soon as it's switched on, everything after the current song is
 * swapped for an AudioMuse song path from that song to its "relaxed" mood, long enough to fill the timer, so the
 * music gets calmer and quieter step by step as you fall asleep. Without a path, AudioMuse's calm sound search.
 */
class WindDown(private val graph: AppGraph) {
    init {
        graph.scope.launch {
            graph.sleepTimer.state
                // One steer per timer: a new time, or the switch turned back on, steers again.
                .map { s -> s?.takeIf { it.windDown && !it.finishing }?.endsAt }
                .distinctUntilChanged()
                .collectLatest { endsAt -> if (endsAt != null) steer() }
        }
    }

    private suspend fun steer() {
        if (!graph.settings.current.audioMuseReady) {
            AppLog.w("Sleep", "Wind down skipped: AudioMuse isn't set up")
            return
        }
        val current = graph.player.state.value.current
        if (current == null) {
            AppLog.i("Sleep", "Wind down skipped: nothing is playing")
            return
        }
        val left = graph.sleepTimer.state.value?.leftMs(graph.sleepTimer.now()) ?: return
        val calm = calmPath(current, windDownLength(left)) ?: calmSearch(current)
        if (calm.isEmpty()) return
        // The switch may have been turned off while AudioMuse was searching.
        if (graph.sleepTimer.state.value?.windDown != true) return
        AppLog.i("Sleep", "Wind down: ${calm.size} songs from ${current.title} toward calmer ones")
        withContext(Dispatchers.Main) {
            // A song radio would top the queue back up with songs like the old ones.
            graph.songRadio.stop()
            graph.player.replaceUpcoming(calm)
        }
    }

    /** The song path from [seed] to the relaxed mood, without [seed] itself; null when AudioMuse can't make one. */
    private suspend fun calmPath(seed: Song, length: Int): List<Song>? =
        runCatching { graph.songPath.build(seed.id, MOOD_END + PathMood.RELAXED.id, length) }
            .onFailure { AppLog.w("Sleep", "Wind down couldn't build a path from ${seed.id}: ${it.message}") }
            .getOrNull()
            ?.filter { it.id != seed.id }
            ?.takeIf { it.isNotEmpty() }

    private suspend fun calmSearch(seed: Song): List<Song> =
        runCatching { graph.radios.windDown() }
            .onFailure { AppLog.w("Sleep", "Wind down couldn't find calm songs: ${it.message}") }
            .getOrNull()
            ?.filter { it.id != seed.id }
            .orEmpty()
}
