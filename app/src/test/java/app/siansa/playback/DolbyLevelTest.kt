package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/** Louder Dolby songs on the receiver (issue #930), on frames made by FFmpeg's encoder with dialnorm −24. */
class DolbyLevelTest {
    private fun frames(): ByteBuffer {
        val bytes = javaClass.getResourceAsStream("/dolby/dialnorm-24.eac3")!!.use { it.readBytes() }
        return ByteBuffer.wrap(bytes)
    }

    /** Each frame's dialogue level (dB) and whether its checksum is right. */
    private fun read(buffer: ByteBuffer): List<Pair<Int, Boolean>> {
        val out = ArrayList<Pair<Int, Boolean>>()
        var at = 0
        while (at + 8 <= buffer.limit()) {
            val b = { i: Int -> buffer.get(at + i).toInt() and 0xFF }
            val size = ((b(2) and 0x07) shl 8 or b(3)) * 2 + 2
            val dialnorm = (b(5) and 0x07) shl 2 or (b(6) ushr 6)
            // A right checksum makes the CRC of everything after the sync word, itself included, come to 0.
            out += -dialnorm to (DolbyLevel.crc16(buffer, at + 2, at + size) == 0)
            at += size
        }
        return out
    }

    @Test
    fun theEncodersOwnChecksumsAreRead() {
        val before = read(frames())
        assertTrue(before.size > 1)
        assertTrue(before.all { it == -24 to true })
    }

    @Test
    fun framesAreMarkedLouderWithTheirChecksumsWrittenAgain() {
        val buffer = frames()
        val raised = DolbyLevel.raise(buffer, 6)!!
        assertEquals(-24, raised.fromDb)
        assertEquals(-30, raised.toDb)
        assertEquals(0, buffer.position())
        val after = read(buffer)
        assertEquals(raised.frames, after.size)
        assertTrue(after.all { it == -30 to true })
    }

    @Test
    fun neverPastTheReceiversFullLevel() {
        val buffer = frames()
        val raised = DolbyLevel.raise(buffer, 12)!!
        assertEquals(-31, raised.toDb)
        assertTrue(read(buffer).all { it == -31 to true })
        // Already at full level: nothing to do.
        assertNull(DolbyLevel.raise(buffer, 3))
    }

    @Test
    fun offAndOtherDataAreLeftAlone() {
        assertNull(DolbyLevel.raise(frames(), 0))
        val notDolby = ByteBuffer.wrap(ByteArray(64) { it.toByte() })
        assertNull(DolbyLevel.raise(notDolby, 6))
    }

    @Test
    fun onlyFromTheBuffersPosition() {
        val buffer = frames()
        val first = read(frames())
        // Starting past the first frame (a batch the output only half took): earlier frames stay as they are.
        val firstSize = ((buffer.get(2).toInt() and 0x07) shl 8 or (buffer.get(3).toInt() and 0xFF)) * 2 + 2
        buffer.position(firstSize)
        val raised = DolbyLevel.raise(buffer, 3)!!
        assertEquals(first.size - 1, raised.frames)
        buffer.position(0)
        val after = read(buffer)
        assertEquals(-24 to true, after.first())
        assertTrue(after.drop(1).all { it == -27 to true })
    }

    /** Issue #1078: the log says for each song why it wasn't played louder; this is what a song is marked with. */
    @Test
    fun whatASongIsMarkedWithIsRead() {
        assertEquals(24, DolbyLevel.marked(frames()))
        val buffer = frames()
        DolbyLevel.raise(buffer, 12)
        assertEquals(31, DolbyLevel.marked(buffer))
        assertNull(DolbyLevel.marked(ByteBuffer.wrap(ByteArray(64) { it.toByte() })))
        assertNull(DolbyLevel.marked(ByteBuffer.wrap(ByteArray(4))))
    }

    @Test
    fun whyASongWasntPlayedLouderIsSaid() {
        // Dolby TrueHD, DTS, plain Dolby Digital.
        assertTrue("only Dolby Digital Plus" in DolbyLevel.whyNot(eac3 = false, marked = null))
        // Already at the receiver's full level, as some Atmos songs are.
        assertTrue("already marked at the receiver's full level" in DolbyLevel.whyNot(eac3 = true, marked = 31))
        assertTrue("already marked at the receiver's full level" in DolbyLevel.whyNot(eac3 = true, marked = 0))
        assertTrue("can read" in DolbyLevel.whyNot(eac3 = true, marked = null))
        assertTrue("−24 dB" in DolbyLevel.whyNot(eac3 = true, marked = 24))
    }
}
