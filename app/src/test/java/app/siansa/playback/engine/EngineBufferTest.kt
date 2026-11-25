package app.siansa.playback.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The mixer's output buffer: short on a phone, longer on a TV so a stall isn't a silence (issue #941). */
class EngineBufferTest {
    @Test
    fun aTvHoldsMoreThanAPhone() {
        assertEquals(120, AudioEngine.bufferMs(tv = false))
        assertTrue(AudioEngine.bufferMs(tv = true) >= 400)
        // The phone app keeps its short buffer.
        assertEquals(AudioEngine.PHONE_BUFFER_MS, AudioEngine.BUFFER_MS)
    }

    /**
     * After the last sound, silence follows it out (the owner, 24 Nov: a skip's old song played in front of the next
     * one): until all the output held has gone, the limiter's frames and a margin with it. Then it may pause.
     */
    @Test
    fun whatTheOutputHoldsIsPlayedOutBeforeItPauses() {
        val rate = 44_100
        val buffer = rate * 120 / 1_000  // the phone's buffer
        val latency = 220
        assertTrue(AudioEngine.playsOut(idle = 0, buffer, latency, rate))
        assertTrue(AudioEngine.playsOut(idle = buffer, buffer, latency, rate))  // the buffer's worth isn't enough yet
        val enough = buffer + latency + rate * AudioEngine.PLAY_OUT_MARGIN_MS / 1_000
        assertTrue(AudioEngine.playsOut(idle = enough - 1, buffer, latency, rate))
        assertFalse(AudioEngine.playsOut(idle = enough, buffer, latency, rate))
        // A bigger buffer (a TV's, or what a device asks for) takes longer to play out.
        assertTrue(AudioEngine.playsOut(idle = enough, buffer * 4, latency, rate))
    }

    /** With nothing playing the mix gives silence, and the limiter lets go of what it held: nothing waits for the next song. */
    @Test
    fun theMixGivesTheLimitersLastFramesThenSilence() {
        val rate = 44_100
        val core = MixCore(rate)
        val deck = core.deck("main")
        val stream = DeckStream(rate, 2, DeckStream.Encoding.PCM_FLOAT)
        deck.add(stream)
        val loud = java.nio.ByteBuffer.allocate(4096 * 2 * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        repeat(4096 * 2) { loud.putFloat(0.5f) }
        loud.flip()
        stream.write(loud)
        stream.play()
        val out = FloatArray(256 * 2)
        var at = 0L
        repeat(8) { core.render(out, 256, at); at += 256 }
        stream.release()
        var quietBlocks = 0
        var blocks = 0
        // Rendered on with nothing to play: within what the output plays out, the mix is silent and stays so.
        while (blocks < (rate * 200 / 1_000) / 256) {
            core.render(out, 256, at); at += 256; blocks++
            if (out.all { kotlin.math.abs(it) < 1e-6f }) quietBlocks++ else quietBlocks = 0
        }
        assertTrue("The mix is silent well before the output pauses", quietBlocks >= 4)
        assertFalse(core.active)
    }
}
