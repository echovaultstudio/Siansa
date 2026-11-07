package app.siansa.playback.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Surround sound folded into the mixer's two channels (issue #860). */
class DownmixTest {
    private fun near(expected: Float, actual: Float) = assertTrue("$expected ≠ $actual", abs(expected - actual) < 1e-4f)

    @Test
    fun stereoAndMonoAreReadStraight() {
        val stereo = Downmix.weights(2)
        near(1f, stereo.left[0]); near(0f, stereo.left[1])
        near(0f, stereo.right[0]); near(1f, stereo.right[1])
        val mono = Downmix.weights(1)
        near(1f, mono.left[0]); near(1f, mono.right[0])
    }

    @Test
    fun fivePointOneKeepsTheCentreAndSurroundsButNotTheLfe() {
        // FL FR FC LFE BL BR
        val w = Downmix.weights(6)
        assertTrue("centre in both", w.left[2] > 0f && w.right[2] > 0f)
        near(0f, w.left[3]); near(0f, w.right[3])
        assertTrue("left surround only left", w.left[4] > 0f && w.right[4] == 0f)
        assertTrue("right surround only right", w.right[5] > 0f && w.left[5] == 0f)
        near(0f, w.left[1]); near(0f, w.right[0])
        // The front is louder than the centre and surrounds (−3 dB).
        near(w.left[0] * 0.70710677f, w.left[2])
    }

    @Test
    fun eachSideAddsUpToOneSoNothingClips() {
        for (channels in 1..8) {
            val w = Downmix.weights(channels)
            near(1f, w.left.sum())
            near(1f, w.right.sum())
        }
    }

    @Test
    fun sevenPointOneUsesBothPairsOfSurrounds() {
        // FL FR FC LFE BL BR SL SR
        val w = Downmix.weights(8)
        assertTrue(w.left[4] > 0f && w.left[6] > 0f)
        assertTrue(w.right[5] > 0f && w.right[7] > 0f)
        assertEquals(8, w.left.size)
    }

    @Test
    fun aVoiceOnlyInTheCentreIsHeardInBothEars() {
        val w = Downmix.weights(6)
        val frame = floatArrayOf(0f, 0f, 0.5f, 0f, 0f, 0f)
        val l = frame.indices.sumOf { (frame[it] * w.left[it]).toDouble() }
        val r = frame.indices.sumOf { (frame[it] * w.right[it]).toDouble() }
        assertTrue(l > 0.1 && abs(l - r) < 1e-6)
    }
}
