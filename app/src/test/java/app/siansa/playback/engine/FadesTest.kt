package app.siansa.playback.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The listener's fades (issue #1368): a stream comes in and goes over the length it's given, and only what the listener
 * starts or stops gets a length. A stream brought in at a set moment (a crossfade, a DJ blend) or cut over to another
 * copy is never touched.
 */
class FadesTest {
    private val rate = 48_000

    private fun stream(seconds: Double = 2.0): DeckStream {
        val s = DeckStream(rate, 2, DeckStream.Encoding.PCM_FLOAT, 3_000)
        val frames = (rate * seconds).toInt()
        val data = ByteBuffer.allocate(frames * 8).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames * 2) { data.putFloat(0.5f) }
        data.flip()
        s.write(data)
        return s
    }

    /** The level (0 to 1 of the stream's own) at each millisecond, over [ms] from mix frame [from]. */
    private fun levels(s: DeckStream, ms: Int, from: Long = 0): List<Float> {
        val per = rate / 1_000
        val out = FloatArray(per * 2)
        return (0 until ms).map { at ->
            out.fill(0f)
            s.render(out, 0, per, from + at.toLong() * per, rate)
            out[(per - 1) * 2] / 0.5f
        }
    }

    @Test fun withNothingSetAStartOnlyRampsSoItDoesntClick() {
        val s = stream()
        s.play()
        val level = levels(s, 20)
        assertTrue("Whole within ${DeckStream.RAMP_MS} ms", level[5] > 0.99f)
    }

    @Test fun aFadeInTakesItsLength() {
        val s = stream()
        s.fadeInMs = 250.0
        s.play()
        val level = levels(s, 300)
        assertEquals(0.2f, level[49], 0.02f)
        assertEquals(0.5f, level[124], 0.02f)
        assertTrue(level[240] < 1f)
        assertEquals(1f, level[255], 0.001f)
    }

    @Test fun aFadeOutTakesItsLengthAndTheStreamIsHeardUntilItsDone() {
        val s = stream()
        s.fadeOutMs = 150.0
        s.play()
        levels(s, 50)
        s.pause()
        val level = levels(s, 200, from = 50L * rate / 1_000)
        assertEquals(0.5f, level[74], 0.02f)
        assertTrue("Still heard halfway", level[100] > 0.2f)
        assertEquals(0f, level[155], 0.001f)
        assertFalse("Gone once it's faded", s.audible)
    }

    @Test fun letGoItFadesOutTooWithWhatItStillHas() {
        val s = stream()
        s.fadeOutMs = 150.0
        s.play()
        levels(s, 50)
        s.release()
        assertTrue("Heard while it fades", s.audible)
        val level = levels(s, 200, from = 50L * rate / 1_000)
        assertEquals(0.5f, level[74], 0.02f)
        assertEquals(0f, level[155], 0.001f)
    }

    @Test fun aStreamBroughtInAtASetMomentStartsWholeWhateverTheFadeIs() {
        // A crossfade's or a DJ blend's next song: its deck's volume does the fading, not this.
        val s = stream()
        s.fadeInMs = 250.0
        s.cue(DeckStream.Cue(frame = 0.0, mixFrame = 0, outRate = rate))
        s.play()
        val level = levels(s, 10)
        assertEquals(1f, level[0], 0.001f)
        assertEquals(1f, level[9], 0.001f)
    }

    @Test fun aStreamCutOverToAnotherCopyGoesAtOnceWhateverTheFadeIs() {
        val s = stream()
        s.fadeOutMs = 150.0
        s.play()
        levels(s, 50)
        s.cutAt(50L * rate / 1_000 + rate / 100)  // 10 ms on
        val level = levels(s, 30, from = 50L * rate / 1_000)
        assertEquals(1f, level[8], 0.001f)
        assertEquals(0f, level[11], 0.001f)
    }

    @Test fun onlyWhatTheListenerStartsFadesIn() {
        // After a skip, a seek, a stop or a pause: the chosen fade. After a song that played out: just the ramp.
        assertEquals(250.0, DeckOutput.fadeIn(own = true, chosen = 250), 0.0)
        assertEquals(DeckStream.RAMP_MS, DeckOutput.fadeIn(own = false, chosen = 250), 0.0)
        assertEquals(DeckStream.RAMP_MS, DeckOutput.fadeIn(own = true, chosen = 0), 0.0)  // off
    }

    @Test fun aStreamLetGoWithSoundToPlayWasCutShort() {
        assertTrue(DeckOutput.cutShort(buffered = 44_100, rate = 44_100))  // a second left: skipped
        assertTrue(DeckOutput.cutShort(buffered = 44_100L * DeckOutput.PLAYED_OUT_MS / 1_000, rate = 44_100))
        assertFalse(DeckOutput.cutShort(buffered = 0, rate = 44_100))  // played to its end
        assertFalse(DeckOutput.cutShort(buffered = 441, rate = 44_100))  // 10 ms: the last block, as good as out
    }

    @Test fun aNewDeckStartsAsTheListeners() {
        assertTrue(MixCore.Deck("main").cutShort)
    }
}
