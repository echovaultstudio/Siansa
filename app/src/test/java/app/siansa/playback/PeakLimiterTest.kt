package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/** The limiter after the equaliser: no clipped peaks, and quiet audio left exactly as it was (#92). */
class PeakLimiterTest {
    private val rate = 44_100

    private fun run(limiter: PeakLimiter, input: List<DoubleArray>): List<DoubleArray> =
        input.map { frame -> frame.copyOf().also { limiter.process(it) } }

    private fun tone(amplitude: Double, seconds: Double = 1.0) =
        List((rate * seconds).toInt()) { i -> sin(2 * PI * 1_000 * i / rate).let { doubleArrayOf(amplitude * it, amplitude * it) } }

    @Test fun audioUnderTheCeilingComesOutUnchangedJustLater() {
        val limiter = PeakLimiter(rate, 2)
        val input = tone(0.9)
        val output = run(limiter, input)
        val delay = limiter.latencyFrames
        for (i in delay until input.size) {
            assertEquals(input[i - delay][0], output[i][0], 0.0)
            assertEquals(input[i - delay][1], output[i][1], 0.0)
        }
    }

    @Test fun peaksNeverGoOverTheCeiling() {
        val limiter = PeakLimiter(rate, 2)
        val output = run(limiter, tone(10.0) + tone(1.3) + tone(0.5))
        assertTrue(output.all { f -> f.all { abs(it) <= 1.0 } })
    }

    @Test fun aBoostedLoudToneStaysCleanInsteadOfClipped() {
        // A -1 dBFS tone with +2 dB of pre-amp goes 1 dB over full scale.
        val loud = tone(1.12, seconds = 2.0)
        val limited = run(PeakLimiter(rate, 2), loud).drop(rate).map { it[0] }
        val clipped = loud.drop(rate).map { it[0].coerceIn(-1.0, 1.0) }
        assertTrue("clipping is distorted", distortion(clipped) > 0.03)
        assertTrue("the limiter keeps it clean", distortion(limited) < 0.001)
    }

    @Test fun bothChannelsAreTurnedDownTogether() {
        val limiter = PeakLimiter(rate, 2)
        val input = List(rate) { i -> doubleArrayOf(if (i == rate / 2) 2.0 else 0.5, 0.5) }
        val output = run(limiter, input)
        val i = rate / 2 + limiter.latencyFrames
        assertEquals(1.0, output[i][0], 1e-9)
        assertEquals(0.25, output[i][1], 1e-9)
    }

    @Test fun theEqualiserPreampNoLongerClipsLoudSongs() {
        val bank = EqFilterBank(EqDesign.of(EqSettings(enabled = true, preampDb = 2f), rate), 2)
        val limiter = PeakLimiter(rate, 2)
        val output = tone(0.89).map { f -> DoubleArray(2) { c -> bank.process(f[c], c) }.also { limiter.process(it) } }
        assertTrue(output.all { f -> f.all { abs(it) <= 1.0 } })
        assertTrue(distortion(output.drop(rate / 2).map { it[0] }) < 0.001)
    }

    /** What's left after taking out the 1 kHz tone, relative to the tone (0.01 = 1 % distortion). */
    private fun distortion(signal: List<Double>): Double {
        var s = 0.0; var c = 0.0
        signal.forEachIndexed { i, x -> s += x * sin(2 * PI * 1_000 * i / rate); c += x * kotlin.math.cos(2 * PI * 1_000 * i / rate) }
        s *= 2.0 / signal.size; c *= 2.0 / signal.size
        var rest = 0.0
        signal.forEachIndexed { i, x -> val r = x - s * sin(2 * PI * 1_000 * i / rate) - c * kotlin.math.cos(2 * PI * 1_000 * i / rate); rest += r * r }
        return sqrt(rest / signal.size) / sqrt((s * s + c * c) / 2)
    }
}
