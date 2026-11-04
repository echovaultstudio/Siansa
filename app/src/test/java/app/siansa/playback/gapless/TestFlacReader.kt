package app.siansa.playback.gapless

import org.junit.Assert.assertEquals

/**
 * A small FLAC decoder for the tests, written from the format's description (not from [FlacEncoder]): STREAMINFO, and
 * frames with constant, verbatim and fixed-predictor subframes, Rice residuals and every stereo coding, checking both
 * checksums.
 */
class TestFlacReader(header: ByteArray) {
    val rate: Int
    val channels: Int
    val bits: Int
    val totalSamples: Long

    init {
        assertEquals("fLaC", String(header, 0, 4, Charsets.US_ASCII))
        val r = Bits(header, 4)
        r.read(1)
        assertEquals("STREAMINFO", 0, r.read(7).toInt())
        assertEquals(34, r.read(24).toInt())
        r.read(16); r.read(16); r.read(24); r.read(24)
        rate = r.read(20).toInt()
        channels = r.read(3).toInt() + 1
        bits = r.read(5).toInt() + 1
        totalSamples = r.read(36)
    }

    /** One frame: the number of its first sample, and its samples, interleaved. */
    fun frame(bytes: ByteArray): Pair<Long, ShortArray> {
        val r = Bits(bytes, 0)
        assertEquals("sync", 0x3FFE, r.read(14).toInt())
        assertEquals(0, r.read(1).toInt())
        val variable = r.read(1).toInt() == 1
        val sizeCode = r.read(4).toInt()
        val rateCode = r.read(4).toInt()
        val assignment = r.read(4).toInt()
        val bpsCode = r.read(3).toInt()
        r.read(1)
        val number = utf8(r)
        val size = when (sizeCode) {
            1 -> 192
            in 2..5 -> 576 shl (sizeCode - 2)
            6 -> r.read(8).toInt() + 1
            7 -> r.read(16).toInt() + 1
            in 8..15 -> 256 shl (sizeCode - 8)
            else -> error("block size code $sizeCode")
        }
        val frameRate = when (rateCode) {
            12 -> r.read(8).toInt() * 1000
            13 -> r.read(16).toInt()
            14 -> r.read(16).toInt() * 10
            0 -> rate
            else -> intArrayOf(0, 88_200, 176_400, 192_000, 8_000, 16_000, 22_050, 24_000, 32_000, 44_100, 48_000, 96_000)[rateCode]
        }
        assertEquals("sample rate", rate, frameRate)
        assertEquals("bits", 16, when (bpsCode) { 4 -> 16; 0 -> bits; else -> -1 })
        val headerEnd = r.bytePos()
        assertEquals("header CRC-8", Crc.crc8(bytes, headerEnd), r.read(8).toInt())
        val chCount = if (assignment < 8) assignment + 1 else 2
        assertEquals(channels, chCount)
        val ch = Array(chCount) { c ->
            val extra = when (assignment) { 8 -> if (c == 1) 1 else 0; 9 -> if (c == 0) 1 else 0; 10 -> if (c == 1) 1 else 0; else -> 0 }
            subframe(r, size, 16 + extra)
        }
        r.align()
        val end = r.bytePos()
        assertEquals("frame CRC-16", Crc.crc16(bytes, end), r.read(16).toInt())
        assertEquals("nothing after the frame", bytes.size, r.bytePos())
        val out = ShortArray(size * chCount)
        for (i in 0 until size) {
            val (a, b) = when (assignment) {
                8 -> ch[0][i] to ch[0][i] - ch[1][i]
                9 -> ch[1][i] + ch[0][i] to ch[1][i]
                10 -> {
                    val mid = (ch[0][i] shl 1) or (ch[1][i] and 1)
                    ((mid + ch[1][i]) shr 1) to ((mid - ch[1][i]) shr 1)
                }
                else -> ch[0][i] to (if (chCount > 1) ch[1][i] else 0)
            }
            out[i * chCount] = a.toShort()
            if (chCount > 1) out[i * chCount + 1] = b.toShort()
        }
        return (if (variable) number else number * size) to out
    }

    private fun subframe(r: Bits, n: Int, bps: Int): IntArray {
        assertEquals(0, r.read(1).toInt())
        val type = r.read(6).toInt()
        assertEquals("no wasted bits", 0, r.read(1).toInt())
        return when {
            type == 0 -> IntArray(n).also { it.fill(r.signed(bps)) }
            type == 1 -> IntArray(n) { r.signed(bps) }
            type in 8..12 -> {
                val order = type - 8
                val x = IntArray(n)
                for (i in 0 until order) x[i] = r.signed(bps)
                val res = residual(r, n, order)
                for (i in order until n) {
                    val p = when (order) {
                        0 -> 0
                        1 -> x[i - 1]
                        2 -> 2 * x[i - 1] - x[i - 2]
                        3 -> 3 * x[i - 1] - 3 * x[i - 2] + x[i - 3]
                        else -> 4 * x[i - 1] - 6 * x[i - 2] + 4 * x[i - 3] - x[i - 4]
                    }
                    x[i] = p + res[i]
                }
                x
            }
            else -> error("subframe type $type")
        }
    }

    private fun residual(r: Bits, n: Int, order: Int): IntArray {
        val method = r.read(2).toInt()
        val paramBits = if (method == 0) 4 else 5
        val escape = (1 shl paramBits) - 1
        val po = r.read(4).toInt()
        val per = n shr po
        val res = IntArray(n)
        var at = order
        for (p in 0 until (1 shl po)) {
            val end = (p + 1) * per
            val k = r.read(paramBits).toInt()
            if (k == escape) {
                val raw = r.read(5).toInt()
                while (at < end) res[at++] = r.signed(raw)
            } else {
                while (at < end) {
                    var q = 0L
                    while (r.read(1) == 0L) q++
                    val u = (q shl k) or (if (k > 0) r.read(k) else 0L)
                    res[at++] = ((u ushr 1) xor -(u and 1)).toInt()
                }
            }
        }
        return res
    }

    private fun utf8(r: Bits): Long {
        val first = r.read(8).toInt()
        if (first < 0x80) return first.toLong()
        var count = 0
        while ((first shl count) and 0x80 != 0) count++
        var v = (first and (0xFF ushr (count + 1))).toLong()
        repeat(count - 1) { v = (v shl 6) or (r.read(8) and 0x3F) }
        return v
    }

    private class Bits(private val b: ByteArray, start: Int) {
        private var pos = start * 8L
        fun read(n: Int): Long {
            var v = 0L
            repeat(n) {
                val byte = b[(pos ushr 3).toInt()].toInt()
                v = (v shl 1) or ((byte ushr (7 - (pos and 7).toInt())) and 1).toLong()
                pos++
            }
            return v
        }
        fun signed(n: Int): Int {
            val v = read(n)
            return (if (v and (1L shl (n - 1)) != 0L) v - (1L shl n) else v).toInt()
        }
        fun align() { pos = (pos + 7) / 8 * 8 }
        fun bytePos(): Int = (pos / 8).toInt()
    }
}
