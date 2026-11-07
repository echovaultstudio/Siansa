package app.siansa.playback.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The echo at the end of a blend in the new audio engine (issue #759, #826). */
class EchoTest {
    private val rate = 44_100

    /**
     * A 1 kHz tone of [level] over the first [burst] blocks (sent while they play), then nothing, for [blocks] blocks
     * in all; the echo's output.
     */
    private fun run(echo: Echo, blocks: Int, level: Float = 1f, burst: Int = 4): FloatArray {
        val all = FloatArray(blocks * 256 * 2)
        for (b in 0 until blocks) {
            val input = FloatArray(512)
            if (b < burst) for (i in 0 until 256) {
                val v = level * kotlin.math.sin(2 * Math.PI * 1_000 * (b * 256 + i) / rate).toFloat()
                input[i * 2] = v
                input[i * 2 + 1] = v
            }
            val out = FloatArray(512)
            echo.process(input, out, 256)
            System.arraycopy(out, 0, all, b * 512, 512)
            if (b == burst - 1) echo.send = 0f
        }
        return all
    }

    @Test fun theSongRepeatsOnTheBeatFainterEachTime() {
        val echo = Echo(rate)
        echo.delayMs = 100.0
        echo.send = 1f
        val out = run(echo, 200)
        val delay = 4_410
        // The burst is 1 024 frames; each repeat is that long, a beat after the one before.
        fun loudest(from: Int) = (from + 300 until from + 1_000).maxOf { kotlin.math.abs(out[it * 2]) }
        val first = loudest(delay)
        val second = loudest(delay * 2)
        assertTrue("first repeat $first", first > 0.5f)
        assertTrue("second repeat $second against $first", second < first * 0.6f && second > first * 0.3f)
        // Nothing before the first repeat: the echo adds only the repeats, never the song itself.
        assertEquals(0f, (0 until delay - 10).maxOf { kotlin.math.abs(out[it * 2]) }, 1e-6f)
    }

    @Test fun nothingSentNothingHeard() {
        val echo = Echo(rate)
        echo.delayMs = 100.0
        val out = run(echo, 50)
        assertEquals(0f, out.maxOf { kotlin.math.abs(it) }, 0f)
        assertFalse(echo.ringing)
    }

    @Test fun theRepeatsDieAwayAndTheEchoStops() {
        val echo = Echo(rate)
        echo.delayMs = 200.0
        echo.send = 1f
        run(echo, 4)
        assertTrue(echo.ringing)
        // About 8 s later the repeats are long gone.
        run(echo, 8 * rate / 256, level = 0f, burst = 0)
        assertFalse(echo.ringing)
    }

    @Test fun clearingStopsItAtOnce() {
        val echo = Echo(rate)
        echo.delayMs = 100.0
        echo.send = 1f
        run(echo, 4)
        echo.clear()
        val out = FloatArray(512)
        repeat(40) { echo.process(null, out, 256) }
        assertEquals(0f, out.maxOf { kotlin.math.abs(it) }, 0f)
        assertFalse(echo.ringing)
    }

    @Test fun theMixKeepsGoingWhileTheEchoRings() {
        val core = MixCore(rate)
        val deck = core.deck("tail")
        val echo = core.echoOf(deck)
        assertFalse(core.active)
        echo.send = 0.5f
        assertTrue(core.active)
    }
}
