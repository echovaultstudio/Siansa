package app.musix.playback

import android.os.SystemClock
import app.musix.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil

/** How long the last song fades out before playback pauses. */
const val SLEEP_FADE_MS = 8_000L

/**
 * A running sleep timer.
 * @property endsAt when the time is up ([SystemClock.elapsedRealtime]); null for "end of this song".
 * @property totalMs the length that was chosen (0 for "end of this song").
 * @property windDown steer the queue from the song playing toward calmer songs (switched on and off on its own).
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

    /** Whether the wind-down switch was last on, so the sheet opens the way it was left (issue #153). */
    @Volatile var windDownChosen = false
        private set

    private val _windDownAlone = MutableStateFlow(false)
    /** Wind down switched on without a timer running: the queue winds down by itself and ends (issue #330). */
    val windDownAlone: StateFlow<Boolean> = _windDownAlone.asStateFlow()

    /** Whether the music is winding down now, with or without a timer. */
    fun windingDown(): Boolean = _windDownAlone.value || _state.value?.let { it.windDown && !it.finishing && it.endsAt != null } == true

    fun now(): Long = clock()

    fun start(minutes: Int, windDown: Boolean) {
        val total = minutes.coerceAtLeast(1) * 60_000L
        windDownChosen = windDown
        // A timer takes over a wind down that was running on its own, so it fills the time instead.
        _windDownAlone.value = false
        _state.value = SleepTimerState(endsAt = clock() + total, totalMs = total, windDown = windDown)
        AppLog.i("Sleep", "Timer set for $minutes min${if (windDown) " with wind down" else ""}")
    }

    /** Stops when the song playing now ends. */
    fun startEndOfSong() {
        _state.value = SleepTimerState(endsAt = null, totalMs = 0, finishing = true)
        AppLog.i("Sleep", "Timer set for the end of this song")
    }

    /** The wind-down switch: remembered straight away, and applied to a timer that's already running (issue #153). */
    fun setWindDown(on: Boolean) {
        windDownChosen = on
        val s = _state.value
        if (s == null || s.endsAt == null || s.finishing) {
            // No timer to follow: the switch alone winds the queue down (issue #330).
            if (_windDownAlone.value != on) AppLog.i("Sleep", "Wind down turned ${if (on) "on" else "off"} without a timer")
            _windDownAlone.value = on
            return
        }
        _windDownAlone.value = false
        if (s.windDown == on) return
        _state.value = s.copy(windDown = on)
        AppLog.i("Sleep", "Wind down turned ${if (on) "on" else "off"}")
    }

    fun cancel() {
        if (_state.value != null) AppLog.i("Sleep", "Timer turned off")
        _state.value = null
    }

    /**
     * The time is up: fade out now and pause at the chosen time (issue #153). Only a song that ends within the
     * fade anyway (it has [songLeftMs] to go, negative when unknown) is played to its own end.
     */
    fun timeUp(songLeftMs: Long) {
        val s = _state.value ?: return
        if (s.finishing) return
        val cut = if (songLeftMs in 0..SLEEP_FADE_MS) null else clock() + SLEEP_FADE_MS
        AppLog.i("Sleep", if (cut == null) "Time is up; this song ends in ${songLeftMs / 1000}s" else "Time is up; fading out now")
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

/** A typical song's length, to size the wind down's song path. */
private const val WIND_DOWN_SONG_MS = 4 * 60_000L

/**
 * How many songs the wind down's path holds, the song playing included: enough to fill the [leftMs] still on
 * the timer, at least 3 and at most 30 (the longest song path).
 */
fun windDownLength(leftMs: Long): Int = (1 + ceil(leftMs.coerceAtLeast(0) / WIND_DOWN_SONG_MS.toDouble()).toInt()).coerceIn(3, 30)

/**
 * How loud the finishing song plays (0 to 1) with [leftMs] to go before the pause: full until the
 * last [SLEEP_FADE_MS], then down to silence. Unknown time left (negative) keeps it at full volume.
 */
fun sleepFade(leftMs: Long, fadeMs: Long = SLEEP_FADE_MS): Float = when {
    leftMs < 0 -> 1f
    leftMs >= fadeMs -> 1f
    else -> leftMs.toFloat() / fadeMs
}

/** On a TV or speaker the sleep timer pauses this close to the song's end, before the next one starts. */
const val SLEEP_REMOTE_EARLY_MS = 500L

/**
 * Whether the finishing timer should pause now, with [leftMs] to go (negative when unknown). On the phone
 * the player pauses itself at the song's end, so only a cut-short song is paused here; on a TV or speaker
 * the pause comes just before the song ends.
 */
fun sleepPauseNow(onPhone: Boolean, state: SleepTimerState, leftMs: Long): Boolean = when {
    !state.finishing -> false
    onPhone -> state.cutAt != null && leftMs <= 0
    else -> leftMs in 0..SLEEP_REMOTE_EARLY_MS
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
