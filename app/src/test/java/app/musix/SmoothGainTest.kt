package app.musix

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import app.musix.playback.SmoothGain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** ReplayGain inside the audio pipeline: song starts fade in and level changes glide instead of jumping. */
class SmoothGainTest {
    private val rate = 1000 // 30 ms ramp = 30 frames, easy to count

    private fun ready(): SmoothGain = SmoothGain(rampMs = 30).apply {
        configure(AudioFormat(rate, 1, C.ENCODING_PCM_16BIT))
        flush()
    }

    private fun run(gain: SmoothGain, frames: Int, value: Short = 10_000): ShortArray {
        val input = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.nativeOrder())
        repeat(frames) { input.putShort(value) }
        input.flip()
        gain.queueInput(input)
        val out = gain.output
        return ShortArray(out.remaining() / 2) { out.getShort() }
    }

    @Test fun startFadesInFromSilence() {
        val out = run(ready(), 60)
        assertEquals(60, out.size)
        assertTrue("first frame should be near silent, was ${out[0]}", out[0] < 500)
        assertTrue((1 until 30).all { out[it] >= out[it - 1] })
        assertEquals(10_000, out[40].toInt())
    }

    @Test fun levelChangeGlides() {
        val gain = ready()
        run(gain, 60)
        gain.target = 0.5f
        val out = run(gain, 60)
        assertTrue("no jump on the first frame, was ${out[0]}", out[0] > 9_000)
        assertEquals(5_000, out[45].toInt())
    }

    @Test fun flushFadesInAgain() {
        val gain = ready()
        run(gain, 60)
        gain.flush()
        assertTrue(run(gain, 1)[0] < 500)
    }

    @Test fun fullVolumePassesThroughUntouched() {
        val gain = ready()
        run(gain, 60)
        assertTrue(run(gain, 20, value = -12_345).all { it.toInt() == -12_345 })
    }
}
