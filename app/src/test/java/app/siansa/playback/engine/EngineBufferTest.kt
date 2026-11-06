package app.siansa.playback.engine

import org.junit.Assert.assertEquals
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
}
