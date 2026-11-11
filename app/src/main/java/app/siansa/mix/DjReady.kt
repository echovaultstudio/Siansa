package app.siansa.mix

import app.siansa.net.BeatsSound
import app.siansa.data.AppSettings
import app.siansa.log.AppLog
import app.siansa.net.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a DJ mix can run (the owner, 10 Nov: "only ever possible if the minimum requirements are met"). It needs
 * Siansa Beats, and nothing else (the owner, 11 Nov: "DJ radio is only possible with Beats"):
 * - set up in Settings › Connections and answering with beat grids: the beat grids and fades the blends need;
 * - with the songs' sound (Siansa Beats 0.25): each song's tempo, key and energy put the mix in order, and the tempo
 *   is Beat This!'s. AudioMuse-AI's tempo is never used for a DJ mix (it gave drum and bass as 89 or 110 BPM), and
 *   the Siansa plugin on AudioMuse-AI, which isn't kept up any more, doesn't make one possible.
 * Without it, a DJ mix isn't offered (its buttons say why); Steady radio, shuffle and everything else work as always,
 * with AudioMuse-AI or without.
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
        fun problem(settings: AppSettings, connection: ConnectionState, beats: Boolean?, beatsSound: Boolean = BeatsSound.answers(SONGS_SOUND)): String? = when {
            !settings.beatsReady -> "Needs Siansa Beats"
            connection.offline -> "Not available now"
            beats == null -> "Checking…"
            !beats -> "Needs Siansa Beats"
            !beatsSound -> NEEDS_SOUND
            else -> null
        }

        /** AudioMuse's path Siansa Beats answers the songs' tempo, key and energy at. */
        const val SONGS_SOUND = "/api/sync"
        const val NEEDS_SOUND = "Needs Siansa Beats 0.25"

        /** The longer reason, for a message when a DJ mix is asked for and can't start. */
        fun explain(problem: String): String = when (problem) {
            "Needs Siansa Beats" -> "A DJ mix needs Siansa Beats (Settings › Connections): its beat grids for the blends, and each song's tempo, key and energy to put them in order."
            NEEDS_SOUND -> "A DJ mix needs Siansa Beats 0.25 or newer with the songs' sound: on its page, Setup › AudioMuse-AI takes it over."
            "Checking…" -> "Still checking for Siansa Beats: try again in a moment."
            else -> "A DJ mix needs Siansa Beats, which can't be reached right now."
        }
    }
}
