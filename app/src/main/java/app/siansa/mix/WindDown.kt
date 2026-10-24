package app.siansa.mix

import app.siansa.AppGraph
import app.siansa.data.Song
import app.siansa.log.AppLog
import app.siansa.net.PathMood
import app.siansa.playback.leftMs
import app.siansa.playback.windDownLength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * How long a wind down's song path is, the song playing included: enough to fill the timer's [timerLeftMs], or
 * [setting] calm songs after it when the switch is on [alone], without a timer (issue #330). Null when neither applies.
 */
fun windDownSongs(timerLeftMs: Long?, alone: Boolean, setting: Int): Int? = when {
    timerLeftMs != null -> windDownLength(timerLeftMs)
    alone -> setting + 1
    else -> null
}

/**
 * The sleep timer's wind down (issue #153): as soon as it's switched on, everything after the current song is
 * swapped for an AudioMuse song path from that song to its "relaxed" mood, long enough to fill the timer, so the
 * music gets calmer and quieter step by step as you fall asleep. Without a path, AudioMuse's calm sound search.
 * The switch also works without a timer: then it queues the number of calm songs set in Settings › Playback, and the
 * music ends with them. Playing something new while it's on steers again from that song; stopping playback switches
 * it off (issue #330).
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
        graph.scope.launch {
            // The switch on its own, without a timer: wind down straight away (issue #330).
            graph.sleepTimer.windDownAlone.collectLatest { on -> if (on) steer() }
        }
        graph.scope.launch {
            // Playing something new while winding down keeps winding down: the calm songs follow the new song,
            // instead of the rest of the new list (issue #330).
            graph.player.newQueues.collectLatest { first ->
                if (!graph.sleepTimer.windingDown()) return@collectLatest
                AppLog.i("Sleep", "Wind down: something new is playing, steering again from ${first.title}")
                steer(first)
            }
        }
        graph.scope.launch {
            // Stopping playback altogether (the queue emptied) ends the wind down too (issue #330).
            graph.player.state
                .map { it.hasMedia }
                .distinctUntilChanged()
                .drop(1)
                .collect { hasMedia ->
                    if (!hasMedia && graph.sleepTimer.windingDown()) {
                        AppLog.i("Sleep", "Wind down off: playback was stopped")
                        graph.sleepTimer.setWindDown(false)
                    }
                }
        }
    }

    private suspend fun steer(seed: Song? = null) {
        if (!graph.settings.current.audioMuseReady) {
            AppLog.w("Sleep", "Wind down skipped: AudioMuse isn't set up")
            return
        }
        val current = seed ?: graph.player.state.value.current
        if (current == null) {
            AppLog.i("Sleep", "Wind down skipped: nothing is playing")
            return
        }
        val timer = graph.sleepTimer
        val length = windDownSongs(
            timerLeftMs = timer.state.value?.takeIf { it.windDown }?.leftMs(timer.now()),
            alone = timer.windDownAlone.value,
            setting = graph.settings.current.windDownSongs,
        ) ?: return
        val calm = calmPath(current, length) ?: calmSearch(current).take(length - 1)
        if (calm.isEmpty()) return
        // The switch may have been turned off while AudioMuse was searching.
        if (!timer.windingDown()) return
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
