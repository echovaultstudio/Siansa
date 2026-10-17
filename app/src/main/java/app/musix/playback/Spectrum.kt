package app.musix.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Splits audio into its frequencies, for the spectrogram that shows whether a song really is lossless.
 * Each call to [frame] turns [SIZE] samples into the level of every frequency in dB (0 = full scale).
 */
class SpectrumAnalyser(val size: Int = SIZE) {
    init { require(size > 0 && size and (size - 1) == 0) { "size must be a power of two" } }

    private val window = DoubleArray(size) { 0.5 - 0.5 * cos(2 * PI * it / size) }
    // A full-scale sine then reads 0 dB whatever the window does to its level.
    private val scale = 2.0 / window.sum()
    private val re = DoubleArray(size)
    private val im = DoubleArray(size)

    /** dB for each of the size / 2 frequency steps, from 0 Hz up to half the sample rate. */
    fun frame(samples: FloatArray, offset: Int = 0): FloatArray {
        for (i in 0 until size) {
            re[i] = samples[offset + i] * window[i]
            im[i] = 0.0
        }
        fft(re, im)
        return FloatArray(size / 2) { k ->
            val magnitude = sqrt(re[k] * re[k] + im[k] * im[k]) * scale
            (20 * log10(max(magnitude, 1e-9))).toFloat()
        }
    }

    companion object {
        /** 4096 samples: about 11 Hz per step at 44.1 kHz, sharp enough to see an encoder's cut-off. */
        const val SIZE = 4096

        /** In-place radix-2 FFT. */
        fun fft(re: DoubleArray, im: DoubleArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                j = j xor bit
                if (i < j) {
                    var t = re[i]; re[i] = re[j]; re[j] = t
                    t = im[i]; im[i] = im[j]; im[j] = t
                }
            }
            var len = 2
            while (len <= n) {
                val angle = -2 * PI / len
                val wr = cos(angle); val wi = sin(angle)
                var start = 0
                while (start < n) {
                    var cr = 1.0; var ci = 0.0
                    for (k in 0 until len / 2) {
                        val a = start + k; val b = a + len / 2
                        val tr = re[b] * cr - im[b] * ci
                        val ti = re[b] * ci + im[b] * cr
                        re[b] = re[a] - tr; im[b] = im[a] - ti
                        re[a] += tr; im[a] += ti
                        val next = cr * wr - ci * wi
                        ci = cr * wi + ci * wr
                        cr = next
                    }
                    start += len
                }
                len = len shl 1
            }
        }
    }
}

/** What the spectrogram concludes about where the sound stops. */
enum class SourceVerdict { LISTENING, FULL_RANGE, STOPS_NEAR_20K, STOPS_LOW }

/**
 * Finds the highest frequency that carries sound, averaged over the song so far. Lossy formats (MP3, AAC,
 * Opus) throw away everything above a fixed point, which shows as a sharp ceiling: about 16 kHz for a 128k
 * MP3 and 19-20 kHz for a 320k MP3 or Opus. A real lossless file usually reaches 21-22 kHz.
 */
class CutoffDetector(private val sampleRate: Int, private val bins: Int) {
    private val sum = DoubleArray(bins)
    private var frames = 0

    /** Seconds of non-silent audio looked at so far. */
    val seconds: Double get() = frames * bins * 2.0 / sampleRate

    fun add(frameDb: FloatArray) {
        // Silence (between songs, fade-outs) would only drag the average down; skip it.
        if (frameDb.max() < SILENCE_DB) return
        for (k in 0 until bins) sum[k] += 10.0.pow(frameDb[k] / 10.0)
        frames++
    }

    /** The cut-off in Hz, or null before there's enough to go on. */
    fun cutoffHz(): Double? {
        if (seconds < MIN_SECONDS) return null
        val hzPerBin = sampleRate / 2.0 / bins
        val avg = DoubleArray(bins) { 10 * log10(max(sum[it] / frames, 1e-18)) }
        // The level of the busy middle of the sound, as the yardstick.
        val reference = (bin(1_000.0, hzPerBin) until bin(4_000.0, hzPerBin)).map { avg[it] }.average()
        // Smooth over about 300 Hz so a single loud overtone above the cut-off doesn't count.
        val width = max(1, (300 / hzPerBin).toInt())
        var k = bins - 1
        while (k > width) {
            val local = (k - width..k).map { avg[it] }.average()
            if (local > reference - DROP_DB) return k * hzPerBin
            k--
        }
        return 0.0
    }

    fun verdict(): SourceVerdict {
        val cutoff = cutoffHz() ?: return SourceVerdict.LISTENING
        val nyquist = sampleRate / 2.0
        return when {
            cutoff >= minOf(20_800.0, nyquist * 0.93) -> SourceVerdict.FULL_RANGE
            cutoff >= 18_800 -> SourceVerdict.STOPS_NEAR_20K
            else -> SourceVerdict.STOPS_LOW
        }
    }

    private fun bin(hz: Double, hzPerBin: Double) = (hz / hzPerBin).toInt().coerceIn(0, bins - 1)

    companion object {
        const val SILENCE_DB = -70f
        const val MIN_SECONDS = 8.0

        /** How far below the middle of the sound counts as "nothing there". */
        const val DROP_DB = 65.0
    }
}
