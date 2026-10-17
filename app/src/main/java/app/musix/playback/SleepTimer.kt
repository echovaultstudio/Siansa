package app.musix.playback

import android.os.SystemClock
import app.musix.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil

/** How long the last song fades out before playback pauses. */
const val SLEEP_FADE_MS = 8_000L

/** When the time is up and the song still has longer than this to go, it fades out at once instead. */
const val SLEEP_MAX_FINISH_MS = 10 * 60_000L

/**
 * A running sleep timer.
 * @property endsAt when the time is up ([SystemClock.elapsedRealtime]); null for "end of this song".
 * @property totalMs the length that was chosen (0 for "end of this song").
 * @property windDown steer the queue toward calmer songs during the last part of the timer.
 * @property finishing the time is up and the current song is being finished, fading out at its end.
 * @property cutAt when finishing a very long song: when its fade ends, instead of the song's own end.
 */
data class SleepTimerState(
    val endsAt: Long?,
    val totalMs: Long,
    val windDown: Boolean = false,
    val finishing: Boolean = false,
    val cutAt: Long? = null,
)

/**
 * The sleep timer, shared by the player screen (which sets it) and [PlaybackService] (which fades out
 * and pauses). Both run in the app's one process, so they share this object.
 */
class SleepTimer(private val clock: () -> Long = { SystemClock.elapsedRealtime() }) {
    private val _state = MutableStateFlow<SleepTimerState?>(null)
    val state: StateFlow<SleepTimerState?> = _state.asStateFlow()

    /** Whether the wind-down switch was last on, so the sheet opens the way it was left. */
    @Volatile var windDownChosen = false

    fun now(): Long = clock()

    fun start(minutes: Int, windDown: Boolean) {
        val total = minutes.coerceAtLeast(1) * 60_000L
        windDownChosen = windDown
        _state.value = SleepTimerState(endsAt = clock() + total, totalMs = total, windDown = windDown)
        AppLog.i("Sleep", "Timer set for $minutes min${if (windDown) " with wind down" else ""}")
    }

    /** Stops when the song playing now ends. */
    fun startEndOfSong() {
        _state.value = SleepTimerState(endsAt = null, totalMs = 0, finishing = true)
        AppLog.i("Sleep", "Timer set for the end of this song")
    }

    fun cancel() {
        if (_state.value != null) AppLog.i("Sleep", "Timer turned off")
        _state.value = null
    }

    /**
     * The time is up: finish the current song, which has [songLeftMs] to go (negative when unknown).
     * A song that still has very long to go, or of unknown length, fades out now instead.
     */
    fun timeUp(songLeftMs: Long) {
        val s = _state.value ?: return
        if (s.finishing) return
        val cut = if (songLeftMs < 0 || songLeftMs > SLEEP_MAX_FINISH_MS) clock() + SLEEP_FADE_MS else null
        AppLog.i("Sleep", if (cut == null) "Time is up; finishing this song (${songLeftMs / 1000}s left)" else "Time is up; fading out now")
        _state.value = s.copy(finishing = true, cutAt = cut)
    }

    /** Playback paused at the end: the timer is done. */
    fun done() {
        if (_state.value != null) AppLog.i("Sleep", "Paused by the sleep timer")
        _state.value = null
    }
}

/** Milliseconds until the time is up, or null for "end of this song" and once it's finishing. */
fun SleepTimerState.leftMs(now: Long): Long? = if (finishing || endsAt == null) null else (endsAt - now).coerceAtLeast(0)

/** The time is up and the song should now be finished. */
fun SleepTimerState.isTimeUp(now: Long): Boolean = !finishing && endsAt != null && now >= endsAt

/**
 * When the wind down begins: the last third of the timer, at most 20 minutes and at least 5
 * (or the whole timer when it's shorter than that). Null when wind down is off.
 */
fun SleepTimerState.windDownFrom(): Long? {
    if (!windDown || endsAt == null) return null
    val length = (totalMs / 3).coerceIn(5 * 60_000L, 20 * 60_000L).coerceAtMost(totalMs)
    return endsAt - length
}

/**
 * How loud the finishing song plays (0 to 1) with [leftMs] to go before the pause: full until the
 * last [SLEEP_FADE_MS], then down to silence. Unknown time left (negative) keeps it at full volume.
 */
fun sleepFade(leftMs: Long, fadeMs: Long = SLEEP_FADE_MS): Float = when {
    leftMs < 0 -> 1f
    leftMs >= fadeMs -> 1f
    else -> leftMs.toFloat() / fadeMs
}

/** Time left in the sheet: "45 min", "1 h 5 min", "40 s". */
fun sleepTimeLabel(ms: Long): String {
    if (ms < 60_000) return "${ceil(ms.coerceAtLeast(0) / 1000.0).toInt()} s"
    val minutes = ceil(ms / 60_000.0).toInt()
    if (minutes < 60) return "$minutes min"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "$h h" else "$h h $m min"
}

/** Time left under the moon button in the player: "45m", "1h05", "<1m". */
fun sleepTimeShort(ms: Long): String {
    if (ms < 60_000) return "<1m"
    val minutes = ceil(ms / 60_000.0).toInt()
    if (minutes < 60) return "${minutes}m"
    return "${minutes / 60}h" + (minutes % 60).toString().padStart(2, '0')
}
