package app.siansa.playback

import java.nio.ByteBuffer

/**
 * Louder Dolby songs on the receiver (Settings › Playback on the TV app, issue #930).
 *
 * A song sent to the receiver unchanged can't have its sound changed, but each Dolby Digital Plus frame (Atmos
 * included) says how loud its dialogue is mastered ("dialnorm", −1 to −31 dB), and every Dolby decoder turns the song
 * down by the difference from −31 so that everything plays at the same level. Atmos music is usually marked around −24
 * to −16 dB, so the receiver turns it down 7 to 15 dB, and it plays much quieter than FLACs or the TV's own sound
 * at the same volume.
 *
 * Marking the frames a few dB nearer −31 makes the receiver turn the song down that much less: louder, without the
 * sound itself being touched. It stops at −31 (the receiver's full level), so a song already marked near it gets less.
 * Each frame's checksum is written again for the changed header, as an encoder would write it.
 *
 * Only Dolby Digital Plus (E-AC-3, with or without Atmos): plain Dolby Digital, Dolby TrueHD and DTS are left as they
 * are.
 */
object DolbyLevel {
    /** The choices, in dB louder; 0 is off. */
    val CHOICES: List<Int> = listOf(0, 3, 6, 9, 12)

    /** The quietest marking a frame can have, where the receiver doesn't turn it down at all. */
    private const val FULL = 31

    private const val SYNC_1 = 0x0B
    private const val SYNC_2 = 0x77

    /** What [raise] did: how many frames, and the dialogue level they were marked with before and after (dB, below 0). */
    data class Raised(val frames: Int, val fromDb: Int, val toDb: Int)

    /**
     * Marks every Dolby Digital Plus frame between [buffer]'s position and its limit [db] dB louder (up to the
     * receiver's full level), in place, leaving the position where it was. Null when nothing changed: not E-AC-3, a
     * frame that doesn't fit or isn't one, or already at full level.
     */
    fun raise(buffer: ByteBuffer, db: Int): Raised? {
        if (db <= 0) return null
        var at = buffer.position()
        val end = buffer.limit()
        var frames = 0
        var from = 0
        var to = 0
        while (at + 8 <= end) {
            if (byte(buffer, at) != SYNC_1 || byte(buffer, at + 1) != SYNC_2) break
            val size = ((byte(buffer, at + 2) and 0x07) shl 8 or byte(buffer, at + 3)) * 2 + 2
            val b5 = byte(buffer, at + 5)
            val b6 = byte(buffer, at + 6)
            // Plain Dolby Digital (bsid 10 and under) has another layout and two checksums: left as it is.
            val bsid = b5 ushr 3
            if (bsid !in 11..16 || size < 8 || at + size > end) break
            val dialnorm = (b5 and 0x07) shl 2 or (b6 ushr 6)
            // 0 is reserved (decoders read it as −31): nothing to raise.
            val raised = if (dialnorm == 0) 0 else minOf(FULL, dialnorm + db)
            if (raised != dialnorm && raised != 0) {
                buffer.put(at + 5, ((b5 and 0xF8) or (raised ushr 2)).toByte())
                buffer.put(at + 6, ((b6 and 0x3F) or ((raised and 0x03) shl 6)).toByte())
                writeChecksum(buffer, at, size)
                if (frames == 0) {
                    from = dialnorm
                    to = raised
                }
                frames++
            }
            at += size
        }
        return if (frames == 0) null else Raised(frames, -from, -to)
    }

    /**
     * Writes a Dolby Digital Plus frame's checksum (crc2, its last two bytes): CRC-16 with x¹⁶+x¹⁵+x²+1 over everything
     * after the sync word, as the encoder writes it. One that would read as a sync word flips the spare bit before it
     * instead, as encoders do.
     */
    private fun writeChecksum(buffer: ByteBuffer, frame: Int, size: Int) {
        var crc = crc16(buffer, frame + 2, frame + size - 2)
        if (crc == 0x0B77) {
            buffer.put(frame + size - 3, (byte(buffer, frame + size - 3) xor 0x01).toByte())
            crc = crc16(buffer, frame + 2, frame + size - 2)
        }
        buffer.put(frame + size - 2, (crc ushr 8).toByte())
        buffer.put(frame + size - 1, crc.toByte())
    }

    /** CRC-16 (polynomial 0x8005, starting at 0, most significant bit first) of [buffer] from [from] to before [until]. */
    internal fun crc16(buffer: ByteBuffer, from: Int, until: Int): Int {
        var crc = 0
        for (i in from until until) {
            crc = crc xor (byte(buffer, i) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1 xor 0x8005) and 0xFFFF else (crc shl 1) and 0xFFFF }
        }
        return crc
    }

    private fun byte(buffer: ByteBuffer, at: Int): Int = buffer.get(at).toInt() and 0xFF
}
