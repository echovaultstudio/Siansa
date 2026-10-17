package app.musix.mix

import app.musix.AppGraph
import app.musix.log.AppLog
import app.musix.playback.windDownFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The sleep timer's wind down: during the last part of the timer, everything after the current song
 * is swapped for calm, slow songs found by AudioMuse, so the music gets quieter as you fall asleep.
 */
class WindDown(private val graph: AppGraph) {
    init {
        graph.scope.launch {
            graph.sleepTimer.state
                .map { it?.takeIf { s -> !s.finishing }?.windDownFrom() }
                .distinctUntilChanged()
                .collectLatest { from ->
                    if (from == null) return@collectLatest
                    val wait = from - graph.sleepTimer.now()
                    if (wait > 0) delay(wait)
                    steer()
                }
        }
    }

    private suspend fun steer() {
        if (!graph.settings.current.audioMuseReady) {
            AppLog.w("Sleep", "Wind down skipped: AudioMuse isn't set up")
            return
        }
        if (!graph.player.state.value.isPlaying) {
            AppLog.i("Sleep", "Wind down skipped: nothing is playing")
            return
        }
        val current = graph.player.state.value.current?.id
        val calm = runCatching { graph.radios.windDown() }
            .onFailure { AppLog.w("Sleep", "Wind down couldn't find calm songs: ${it.message}") }
            .getOrNull()
            ?.filter { it.id != current }
            .orEmpty()
        if (calm.isEmpty()) return
        // The timer may have been turned off while AudioMuse was searching.
        if (graph.sleepTimer.state.value?.windDown != true) return
        AppLog.i("Sleep", "Wind down: ${calm.size} calm songs after the current one")
        withContext(Dispatchers.Main) {
            // A song radio would top the queue back up with songs like the old ones.
            graph.songRadio.stop()
            graph.player.replaceUpcoming(calm)
        }
    }
}
