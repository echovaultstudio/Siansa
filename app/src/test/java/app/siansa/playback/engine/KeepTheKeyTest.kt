package app.siansa.playback.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Keep the key in the new audio engine (issue #826): a blend's speed change stretches the sound, its pitch kept. */
class KeepTheKeyTest {
    private val rate = 44_100

    private fun tone(hz: Double, seconds: Double, level: Float = 0.5f): ByteBuffer {
        val n = (seconds * rate).toInt()
        val b = ByteBuffer.allocate(n * 8).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            val v = level * kotlin.math.sin(2 * Math.PI * hz * i / rate).toFloat()
            b.putFloat(v)
            b.putFloat(v)
        }
        b.flip()
        return b
    }

    /** Plays [stream] for [blocks] blocks from mix frame [from]; the left channel. */
    private fun play(stream: DeckStream, blocks: Int, from: Long = 0): FloatArray {
        val all = FloatArray(blocks * 256)
        val out = FloatArray(512)
        for (b in 0 until blocks) {
            stream.render(out, 0, 256, from + b * 256L, rate)
            for (i in 0 until 256) all[b * 256 + i] = out[i * 2]
        }
        return all
    }

    /** The tone's frequency in [x], by its rising zero crossings. */
    private fun frequency(x: FloatArray, from: Int, to: Int): Double {
        var first = -1
        var last = -1
        var count = 0
        for (i in from + 1 until to) {
            if (x[i - 1] < 0f && x[i] >= 0f) {
                if (first < 0) first = i else count++
                last = i
            }
        }
        return count * rate.toDouble() / (last - first)
    }

    /** A stream of a 440 Hz tone, played at [speed] with [pitch], after a second at its own speed. */
    private fun sped(speed: Float, pitch: Float): Pair<DeckStream, FloatArray> {
        val stream = DeckStream(rate, 2, DeckStream.Encoding.PCM_FLOAT, 3_000)
        val sound = tone(440.0, 3.0)
        stream.write(sound)
        stream.play()
        val start = play(stream, 40)
        stream.speed = speed
        stream.pitch = pitch
        val rest = play(stream, 160, 40 * 256L)
        return stream to (start + rest)
    }

    @Test fun keepingTheKeyKeepsThePitch() {
        val (stream, x) = sped(1.04f, 1f)
        assertEquals(440.0, frequency(x, 20_000, 40_000), 2.0)
        // It's still 4 % faster: where it is moves on at the speed.
        assertEquals(40 * 256 + 160 * 256 * 1.04, stream.readFrame, 1.0)
    }

    @Test fun withoutItThePitchFollowsTheSpeed() {
        val (_, x) = sped(1.04f, 1.04f)
        assertEquals(457.6, frequency(x, 20_000, 40_000), 2.0)
    }

    @Test fun itStartsAndEndsWithoutAClick() {
        val stream = DeckStream(rate, 2, DeckStream.Encoding.PCM_FLOAT, 3_000)
        stream.write(tone(440.0, 3.0))
        stream.play()
        val a = play(stream, 40)
        stream.speed = 1.03f
        stream.pitch = 1f
        val b = play(stream, 80, 40 * 256L)
        stream.speed = 1f
        val c = play(stream, 60, 120 * 256L)
        val x = a + b + c
        // A 440 Hz tone at 0.5 moves at most 0.031 from one sample to the next; a click jumps much further.
        val biggest = (1 until x.size - 6_000).maxOf { kotlin.math.abs(x[it] - x[it - 1]) }
        assertTrue("biggest step $biggest", biggest < 0.045f)
        // Back at its own speed it's copied sample for sample again.
        val tail = c.copyOfRange(c.size - 2_000, c.size)
        assertTrue(tail.any { kotlin.math.abs(it) > 0.4f })
    }
}
