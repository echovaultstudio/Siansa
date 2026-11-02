package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sound kept from one player's audio path, and the tail's corrections (issue #818). */
class SoundTapTest {
    private fun feed(tap: SoundTap, frames: Int, nanos: Long, value: (Int) -> Float = { it.toFloat() }) {
        tap.begin(44_100, 2, nanos)
        repeat(frames) { tap.add(value(it) * 2) }  // two channels' worth
    }

    @Test fun keepsTheLastSecondsDecimatedOldestFirst() {
        val tap = SoundTap()
        tap.keep(1)
        assertNull(tap.snapshot())
        var frame = 0
        repeat(20) { i -> feed(tap, 4_410, i * 100_000_000L) { frame++.toFloat() } }
        val s = tap.snapshot()!!
        assertEquals(44_100 / SoundTap.DECIMATION, s.sound.size)  // one second kept of two played
        assertEquals(88_200L, s.endFrame)
        // The last sample is the mean of the last four frames (88196..88199) of one channel.
        assertEquals(88_197.5f, s.sound.last(), 0.01f)
        assertEquals(s.sound.size - 1, s.sound.indices.maxBy { s.sound[it] })
        assertEquals(20, s.marks.size)
        assertEquals(88_200.0, s.framesAt(20 * 100_000_000L), 1.0)
    }

    @Test fun correctionsAreTakenAtTheNextBufferAndCountedForThePosition() {
        val tap = SoundTap()
        feed(tap, 1_000, 0)
        tap.correct(1_500)  // behind: swallow 1500 frames
        assertTrue(tap.pending)
        assertEquals(1_000, tap.takeDrop(1_000))
        assertEquals(500, tap.takeDrop(4_000))
        assertEquals(0, tap.takeDrop(4_000))
        assertFalse(tap.pending)
        assertEquals(1_500L * 1_000_000 / 44_100, tap.mediaAdjustUs())
        tap.correct(-5_000)  // ahead: put in 5000 frames of silence, at most 4096 a buffer
        assertEquals(4_096, tap.takeInsert())
        assertEquals(904, tap.takeInsert())
        assertEquals(0, tap.takeInsert())
        assertEquals((1_500L - 5_000) * 1_000_000 / 44_100, tap.mediaAdjustUs())
        tap.flush()
        assertEquals(0L, tap.mediaAdjustUs())
        assertNull(tap.snapshot())
    }
}
