package app.siansa.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** Switching the equaliser while a song plays (issue #159): no drop in level, no jump. */
class EqualiserProcessorTest {
    private val rate = 44_100
    private val curve = EqSettings(enabled = true, preset = EqPreset.CUSTOM, custom = EqSettings.parseCustom("6,5,4,2,0,0,0,0,8,0"))
    private var eq = curve.copy(enabled = false)
    private var at = 0

    @Suppress("DEPRECATION")
    private fun processor() = EqualiserProcessor { eq }.apply {
        configure(AudioProcessor.AudioFormat(rate, 1, C.ENCODING_PCM_FLOAT))
        flush()
    }

    /** [seconds] of a 1 kHz tone through [p], out as samples. */
    private fun play(p: EqualiserProcessor, seconds: Double): List<Double> {
        val frames = (rate * seconds).toInt()
        val out = ArrayList<Double>(frames)
        var left = frames
        while (left > 0) {
            val n = minOf(1_024, left)
            val input = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder())
            repeat(n) { input.putFloat((0.25 * sin(2 * PI * 1_000 * at++ / rate)).toFloat()) }
            input.flip()
            p.queueInput(input)
            val output = p.output
            while (output.hasRemaining()) out += output.getFloat().toDouble()
            left -= n
        }
        return out
    }

    private fun rmsDb(samples: List<Double>) = 20 * log10(sqrt(samples.sumOf { it * it } / samples.size))

    @Test fun switchingOnKeepsTheMiddleAsLoud() {
        val p = processor()
        val off = play(p, 0.5).takeLast(rate / 5)
        eq = curve
        val on = play(p, 0.5).takeLast(rate / 5)
        val change = rmsDb(on) - rmsDb(off)
        // Before #159 this was about -7.8 dB.
        assertTrue("1 kHz changed by $change dB", change > -3 && change < 0.5)
    }

    @Test fun switchingGlidesWithoutAJump() {
        val p = processor()
        val steady = play(p, 0.3)
        val biggestStep = steady.zipWithNext { a, b -> abs(b - a) }.drop(200).max()
        for (round in 0 until 4) {
            eq = if (round % 2 == 0) curve else curve.copy(enabled = false)
            val around = play(p, 0.1)
            val step = around.zipWithNext { a, b -> abs(b - a) }.max()
            assertTrue("round $round: step $step vs steady $biggestStep", step < biggestStep * 1.5)
        }
    }

    @Test fun theGlideTakesAboutFiftyMilliseconds() {
        assertEquals(50, EqualiserProcessor.GLIDE_MS)
    }
}
