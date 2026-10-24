package app.siansa.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** Ready-made band settings for the equaliser, in dB per band; Custom uses the listener's own. */
enum class EqPreset(val label: String, val gains: List<Float>?) {
    FLAT("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    BASS("Bass boost", listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
    TREBLE("Treble boost", listOf(0f, 0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 6f)),
    VOCAL("Vocal", listOf(-2f, -2f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f)),
    LOUDNESS("Quiet listening", listOf(5f, 4f, 2f, 0f, 0f, 0f, 0f, 1f, 3f, 4f)),
    ROCK("Rock", listOf(5f, 4f, 2f, 0f, -1f, -1f, 1f, 3f, 4f, 5f)),
    POP("Pop", listOf(-1f, 0f, 2f, 3f, 4f, 3f, 1f, 0f, -1f, -1f)),
    JAZZ("Jazz", listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f)),
    CLASSICAL("Classical", listOf(4f, 3f, 2f, 1f, 0f, 0f, 0f, 2f, 3f, 4f)),
    ELECTRONIC("Electronic", listOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 1f, 4f, 5f)),
    CUSTOM("Custom", null),
}

/**
 * The equaliser as the listener set it: ten bands an octave apart and a pre-amp. Saved with the other
 * settings, applied to everything the phone plays (not while casting).
 */
data class EqSettings(
    val enabled: Boolean = false,
    val preset: EqPreset = EqPreset.FLAT,
    val custom: List<Float> = FLAT,
    val preampDb: Float = 0f,
) {
    val gains: List<Float> get() = preset.gains ?: custom

    /** Whether the sound is changed at all. */
    val active: Boolean get() = enabled && (preampDb != 0f || gains.any { it != 0f })

    /** This setting with one band moved, which makes it the Custom preset (starting from the one in use). */
    fun withBand(band: Int, db: Float): EqSettings =
        copy(preset = EqPreset.CUSTOM, custom = gains.toMutableList().also { it[band] = db.coerceIn(-MAX_DB, MAX_DB) })

    companion object {
        /** Centre of each band in Hz. */
        val BANDS = listOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)
        val FLAT = List(BANDS.size) { 0f }
        const val MAX_DB = 12f
        const val MAX_PREAMP_DB = 12f

        /** One octave per band, so neighbouring bands blend smoothly. */
        const val Q = 1.41

        fun bandLabel(hz: Float): String = if (hz >= 1_000f) "${(hz / 1_000f).toInt()} kHz" else "${hz.toInt()} Hz"

        fun parseCustom(text: String?): List<Float> {
            val values = text?.split(',')?.mapNotNull { it.trim().toFloatOrNull() } ?: return FLAT
            return if (values.size == BANDS.size) values.map { it.coerceIn(-MAX_DB, MAX_DB) } else FLAT
        }

        fun formatCustom(values: List<Float>): String = values.joinToString(",")
    }
}

/** One band's filter (a peaking "biquad" from the Audio EQ Cookbook), normalised so a0 = 1. */
class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
    /** The filter's change in level at [hz], in dB. */
    fun responseDb(hz: Double, sampleRate: Int): Double {
        val w = 2 * PI * hz / sampleRate
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val num = hypot(b0 + b1 * c1 + b2 * c2, -(b1 * s1 + b2 * s2))
        val den = hypot(1 + a1 * c1 + a2 * c2, -(a1 * s1 + a2 * s2))
        return 20 * log10(num / den)
    }

    companion object {
        fun peaking(hz: Double, gainDb: Double, q: Double, sampleRate: Int): Biquad {
            val a = 10.0.pow(gainDb / 40)
            val w0 = 2 * PI * hz / sampleRate
            val alpha = sin(w0) / (2 * q)
            val cosW0 = cos(w0)
            val a0 = 1 + alpha / a
            return Biquad(
                b0 = (1 + alpha * a) / a0,
                b1 = -2 * cosW0 / a0,
                b2 = (1 - alpha * a) / a0,
                a1 = -2 * cosW0 / a0,
                a2 = (1 - alpha / a) / a0,
            )
        }
    }
}

/**
 * The filters and level for one equaliser setting at one sample rate.
 *
 * [turnDownDb] is how far the whole sound is turned down before the filters. It matches the curve's
 * loudness ([loudnessDb]): switching the equaliser on keeps the music as loud as before, it only changes
 * its colour. Turning down by the curve's highest point ([peakDb]) instead, as before, made a curve with one
 * +8 dB band about 8 dB quieter in the middle frequencies, heard as a drop whenever it was switched on
 * (issue #159). The few peaks that now go past full scale are caught by the [PeakLimiter].
 */
class EqDesign(
    val filters: List<Biquad>,
    val inputGain: Double,
    val sampleRate: Int,
    val turnDownDb: Double = 0.0,
    val peakDb: Double = 0.0,
    val loudnessDb: Double = 0.0,
) {
    /** The equaliser's change in level at [hz], in dB, pre-amp and turn-down included. */
    fun responseDb(hz: Double): Double = 20 * log10(inputGain) + filters.sumOf { it.responseDb(hz, sampleRate) }

    companion object {
        fun of(settings: EqSettings, sampleRate: Int): EqDesign {
            // Bands too close to the top of what the sample rate can carry are left out. A band at 0 dB
            // stays in (it changes nothing), so moving a slider never changes how many filters there are.
            val filters = EqSettings.BANDS.zip(settings.gains)
                .filter { (hz, _) -> hz < sampleRate * 0.45f }
                .map { (hz, db) -> Biquad.peaking(hz.toDouble(), db.toDouble(), EqSettings.Q, sampleRate) }
            val loudness = loudnessDb(filters, sampleRate)
            // A curve that makes the music louder is turned down by as much; one that makes it quieter is left
            // as it is (the listener turned those bands down on purpose).
            val turnDown = max(0.0, loudness)
            val inputGain = 10.0.pow((settings.preampDb - turnDown) / 20)
            return EqDesign(filters, inputGain, sampleRate, turnDown, peakDb(filters, sampleRate), loudness)
        }

        /** The highest level change across the audible range, checked at 1/12-octave steps. */
        fun peakDb(filters: List<Biquad>, sampleRate: Int): Double {
            if (filters.isEmpty()) return 0.0
            var peak = Double.NEGATIVE_INFINITY
            var hz = 20.0
            val top = minOf(20_000.0, sampleRate * 0.49)
            while (hz <= top) {
                peak = max(peak, filters.sumOf { it.responseDb(hz, sampleRate) })
                hz *= 2.0.pow(1.0 / 12)
            }
            return peak
        }

        /**
         * How much louder (or quieter) the curve makes music, in dB: its power averaged at 1/12-octave steps
         * over [LOUDNESS_LOW]–[LOUDNESS_HIGH] Hz, where most of what we hear as loudness is. Equal steps per
         * octave weigh it like music's own spectrum (roughly pink).
         */
        fun loudnessDb(filters: List<Biquad>, sampleRate: Int): Double {
            if (filters.isEmpty()) return 0.0
            var sum = 0.0
            var count = 0
            var hz = LOUDNESS_LOW
            val top = minOf(LOUDNESS_HIGH, sampleRate * 0.49)
            while (hz <= top) {
                sum += 10.0.pow(filters.sumOf { it.responseDb(hz, sampleRate) } / 10)
                count++
                hz *= 2.0.pow(1.0 / 12)
            }
            return 10 * log10(sum / count)
        }

        const val LOUDNESS_LOW = 100.0
        const val LOUDNESS_HIGH = 6_000.0
    }
}
