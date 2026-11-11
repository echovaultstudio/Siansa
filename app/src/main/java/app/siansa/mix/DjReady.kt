package app.siansa.mix

import app.siansa.net.soundReady
import app.siansa.data.AppSettings
import app.siansa.log.AppLog
import app.siansa.net.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a DJ mix can run (the owner, 10 Nov: "only ever possible if the minimum requirements are met"). It needs:
 * - AudioMuse-AI, set up and answering: each song's tempo, key and energy put the mix in order;
 * - Siansa Beats answering with beat grids, directly (Settings › Connections) or through its plugin on AudioMuse: the
 *   beat grids and fades the blends need.
 * Without them, a DJ mix isn't offered (its buttons say why); Steady radio, shuffle and everything else work as always.
 */
class DjReady(private val now: () -> Long = System::currentTimeMillis) {
    /** The last check of Siansa Beats (or the plugin and Siansa Beats behind it): true when it works, null before the first. */
    private val _beats = MutableStateFlow<Boolean?>(null)
    val beats: StateFlow<Boolean?> = _beats.asStateFlow()
    @Volatile private var checkedAt = 0L

    /** Whether to check the plugin again now: never checked, or it's been [AGAIN_OK_MS] (or [AGAIN_FAILED_MS] after a no). */
    fun due(): Boolean = _beats.value == null || now() - checkedAt > if (_beats.value == true) AGAIN_OK_MS else AGAIN_FAILED_MS

    fun checked(ok: Boolean, message: String) {
        if (_beats.value != ok) AppLog.i("DJ", if (ok) "DJ mixes can run: $message" else "DJ mixes are off: $message")
        _beats.value = ok
        checkedAt = now()
    }

    /** AudioMuse was set up again (or taken away): what was checked no longer says anything. */
    fun forget() {
        _beats.value = null
        checkedAt = 0L
    }

    companion object {
        const val AGAIN_OK_MS = 30 * 60_000L
        const val AGAIN_FAILED_MS = 2 * 60_000L

        /** Why a DJ mix can't run now, in a few words for its button, or null when it can. */
        fun problem(settings: AppSettings, connection: ConnectionState, beats: Boolean?): String? = when {
            !settings.soundReady -> "Needs AudioMuse"
            connection.offline || connection.audioMuse == false -> "Not available now"
            beats == null -> "Checking…"
            !beats -> "Needs Siansa Beats"
            else -> null
        }

        /** The longer reason, for a message when a DJ mix is asked for and can't start. */
        fun explain(problem: String): String = when (problem) {
            "Needs AudioMuse" -> "A DJ mix needs AudioMuse (Settings › Connections): it puts the songs in order by tempo, key and energy."
            "Needs Siansa Beats" -> "A DJ mix needs Siansa Beats (Settings › Connections), for the beat grids its blends follow."
            "Checking…" -> "Still checking for Siansa Beats: try again in a moment."
            else -> "A DJ mix needs AudioMuse and Siansa Beats, which can't be reached right now."
        }
    }
}
