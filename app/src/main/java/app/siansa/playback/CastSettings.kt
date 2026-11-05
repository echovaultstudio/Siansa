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

/** The Chromecast settings, kept on the phone and shared by every Cast device. */
object CastSettings {
    private var prefs: SharedPreferences? = null

    private val _quality = MutableStateFlow(CastQuality.Lossless)
    val quality: StateFlow<CastQuality> = _quality.asStateFlow()

    /** Reads the saved settings once: when playback starts and when Play on opens. */
    fun load(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _quality.value = CastQuality.of(p.getString(QUALITY, null))
        // Siansa Dev's old "How it plays" (issue #114, removed) may still be saved under "mode": it's never read.
    }

    /** Sets the quality; it applies to songs sent to the TV from now on. */
    fun setQuality(quality: CastQuality) {
        if (quality == _quality.value) return
        _quality.value = quality
        prefs?.edit()?.putString(QUALITY, quality.key)?.apply()
        AppLog.i("Cast", "Cast quality: ${quality.label}")
    }

    private const val PREFS = "cast"
    private const val QUALITY = "quality"
}
