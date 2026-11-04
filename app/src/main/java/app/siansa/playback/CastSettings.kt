package app.siansa.playback

import android.content.Context
import android.content.SharedPreferences
import app.siansa.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a Chromecast or Google TV gets (issue #114), set under the cog beside it in Play on. */
enum class CastQuality(val key: String, val label: String) {
    /**
     * Songs as they are where the TV plays their format; others (ALAC, Monkey's Audio, WavPack, DSD…) converted to
     * FLAC by the server, and what can't go lossless as Opus 320. The default, as casting has sent originals since #869.
     */
    Lossless("lossless", "Lossless"),

    /** Every song converted to Opus 320 by the server: smaller than lossless, sounds the same to most ears. */
    Opus("opus", "Opus"),

    /** Every song converted to MP3 320 by the server, as before #869: for older Cast devices that can't play Opus. */
    Mp3("mp3", "MP3 320");

    companion object {
        /** The quality saved as [key]; [Lossless] for nothing saved or anything unknown. */
        fun of(key: String?): CastQuality = entries.firstOrNull { it.key == key } ?: Lossless
    }
}

/** How a Chromecast plays (issue #114), set under the cog beside it in Play on. */
enum class CastMode(val key: String, val label: String) {
    /** The TV fetches each song of the queue from the server itself: casting as it always was. The default. */
    Queue("queue", "The TV plays the queue"),

    /**
     * The phone keeps playing (crossfades, DJ blends, levelling) and sends its mix to the TV as one lossless stream
     * over the Wi-Fi ([app.siansa.playback.gapless.CastGapless]). Needs Siansa's own TV screen and the new audio engine.
     */
    Gapless("gapless", "Gapless through this phone");

    companion object {
        /** The mode saved as [key]; [Queue] for nothing saved or anything unknown. */
        fun of(key: String?): CastMode = entries.firstOrNull { it.key == key } ?: Queue
    }
}

/** The Chromecast settings, kept on the phone and shared by every Cast device. */
object CastSettings {
    private var prefs: SharedPreferences? = null

    private val _quality = MutableStateFlow(CastQuality.Lossless)
    val quality: StateFlow<CastQuality> = _quality.asStateFlow()

    private val _mode = MutableStateFlow(CastMode.Queue)
    val mode: StateFlow<CastMode> = _mode.asStateFlow()

    /** Reads the saved settings once: when playback starts and when Play on opens. */
    fun load(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _quality.value = CastQuality.of(p.getString(QUALITY, null))
        _mode.value = CastMode.of(p.getString(MODE, null))
    }

    /** Sets the quality; it applies to songs sent to the TV from now on. */
    fun setQuality(quality: CastQuality) {
        if (quality == _quality.value) return
        _quality.value = quality
        prefs?.edit()?.putString(QUALITY, quality.key)?.apply()
        AppLog.i("Cast", "Cast quality: ${quality.label}")
    }

    /** Sets how the TV plays; it applies from the next cast. */
    fun setMode(mode: CastMode) {
        if (mode == _mode.value) return
        _mode.value = mode
        prefs?.edit()?.putString(MODE, mode.key)?.apply()
        AppLog.i("Cast", "Cast mode: ${mode.label}")
    }

    private const val PREFS = "cast"
    private const val QUALITY = "quality"
    private const val MODE = "mode"
}
