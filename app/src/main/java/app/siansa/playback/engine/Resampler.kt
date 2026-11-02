package app.siansa.playback.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The windowed-sinc filter the mixer reads a song through when it plays at another rate than the output's
 * (issue #826): 44.1 kHz on a 48 kHz output, or a touch faster or slower in a beat-matched blend. Each output sample
 * is the song's samples around the wanted position weighted by a sinc ([HALF] either side, a Kaiser window), so the
 * sound is read between its samples without the dulled top and faint distortion of reading in straight lines
 * (Media3's Sonic, siansa-dev#26). Speeding up lowers the cut-off with the ratio, so nothing above the new half
 * sample rate folds back down.
 *
 * The weights are worked out once per cut-off for [PHASES] positions between two samples ([at]); a position in
 * between takes the mix of its two neighbours.
 */
class SincTable private constructor(val cutoff: Double) {
    /** Row j holds the 2·[HALF] weights for a position j/[PHASES] of a sample past a whole one; rows 0..PHASES. */
    private val rows = Array(PHASES + 1) { j -> weights(j.toDouble() / PHASES) }

    private fun weights(frac: Double): DoubleArray {
        val w = DoubleArray(TAPS) { k ->
            val x = (k - HALF + 1) - frac  // the tap's distance from the wanted position, in samples
            cutoff * sinc(cutoff * x) * kaiser(x / HALF)
        }
        // Normalised, so a steady level comes out at the same level at every position.
        val sum = w.sum()
        for (k in w.indices) w[k] /= sum
        return w
    }

    /** The weight of tap [k] (frame floor(pos) − HALF + 1 + k) for a position [frac] (0..1) past a whole sample. */
    fun at(frac: Double, k: Int): Double {
        val p = frac * PHASES
        val j = p.toInt().coerceIn(0, PHASES - 1)
        val t = p - j
        val a = rows[j][k]
        return a + (rows[j + 1][k] - a) * t
    }

    /** All [TAPS] weights for [frac] into [into]: one call per output sample, not one per tap. */
    fun fill(frac: Double, into: DoubleArray) {
        val p = frac * PHASES
        val j = p.toInt().coerceIn(0, PHASES - 1)
        val t = p - j
        val a = rows[j]
        val b = rows[j + 1]
        for (k in 0 until TAPS) into[k] = a[k] + (b[k] - a[k]) * t
    }

    companion object {
        /** Taps either side of the wanted position: the filter's length, and how much a stream keeps behind it. 48 a
         *  side makes its slope about 2.6 kHz wide at 44.1 kHz, so the band stays flat to 19.7 kHz. */
        const val HALF = 48
        const val TAPS = 2 * HALF
        const val PHASES = 512
        /** Where the slope is half way down, of the half sample rate: 21 kHz at 44.1 kHz. */
        const val PASS = 0.955
        /** The Kaiser window's shape: about 90 dB of stop band. */
        const val BETA = 9.0

        private val tables = HashMap<Int, SincTable>()

        /**
         * The table for reading [ratio] source samples per output sample. Slowing down or up-sampling keeps the
         * whole band; speeding up or down-sampling lowers the cut-off by the ratio. Cut-offs are rounded to steps
         * of 1/200, so a blend sweeping its speed doesn't make a table per step.
         */
        fun forRatio(ratio: Double): SincTable {
            val cutoff = (PASS * min(1.0, 1.0 / ratio) * 200).roundToInt()
            return synchronized(tables) { tables.getOrPut(cutoff) { SincTable(cutoff / 200.0) } }
        }

        private fun sinc(x: Double): Double = if (abs(x) < 1e-9) 1.0 else sin(PI * x) / (PI * x)

        private fun kaiser(x: Double): Double {
            if (abs(x) >= 1.0) return 0.0
            return bessel0(BETA * sqrt(1 - x * x)) / bessel0(BETA)
        }

        /** The modified Bessel function of the first kind, order 0 (the Kaiser window's), by its series. */
        private fun bessel0(x: Double): Double {
            var sum = 1.0
            var term = 1.0
            val half = x / 2
            var k = 1
            while (term > 1e-12 * sum) {
                term *= (half / k) * (half / k)
                sum += term
                k++
            }
            return sum
        }

        /** The whole frame at or before [pos], and how far past it [pos] is. */
        fun split(pos: Double): Pair<Long, Double> {
            val whole = floor(pos)
            return whole.toLong() to (pos - whole)
        }
    }
}
