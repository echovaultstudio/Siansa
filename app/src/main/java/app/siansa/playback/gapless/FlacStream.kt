package app.siansa.playback.gapless

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A FLAC encoder for one endless stream (issue #114, gapless casting): what the phone's mix sends the TV. 16-bit,
 * any number of channels (the mix is stereo), each frame as long as the samples at hand ("variable block size", so a
 * pause or the end of the music can send what's left without padding it), coded with FLAC's fixed predictors and
 * Rice-coded residuals, or as constant (silence) or verbatim, whichever is smallest; stereo as left/right, left/side,
 * right/side or mid/side, whichever is smallest.
 *
 * Endless: the stream's header says its length is unknown (0 samples) and has no checksum of the whole, so the TV
 * plays it as it comes, for as long as it comes. [compress] false sends every frame as it is ("verbatim"), at a steady
 * 1.4 Mbit/s for 44.1 kHz stereo: what the TV gets, as Chrome buffers by bytes (see CastGapless). Pure Kotlin, no Android, so it is tested on the computer and the
 * same frames can be checked against FFmpeg's decoder.
 */
class FlacEncoder(val sampleRate: Int, val channels: Int = 2, val compress: Boolean = true, firstSample: Long = 0) {
    init {
        require(channels in 1..8) { "FLAC has 1 to 8 channels" }
        require(sampleRate in 1..655_350) { "FLAC's sample rates go up to 655,350 Hz" }
    }

    /** Samples (per channel) sent so far: the next frame's number. */
    var sampleNumber = firstSample
        private set

    /** "fLaC" and the stream's one metadata block, STREAMINFO: what a player needs before the first frame. */
    fun header(): ByteArray {
        val w = BitWriter(42)
        w.bits(0x664C6143L, 32) // "fLaC"
        w.bits(1, 1) // the last metadata block
        w.bits(0, 7) // STREAMINFO
        w.bits(34, 24)
        w.bits(MIN_BLOCK.toLong(), 16)
        w.bits(MAX_BLOCK.toLong(), 16)
        w.bits(0, 24) // smallest frame: unknown
        w.bits(0, 24) // largest frame: unknown
        w.bits(sampleRate.toLong(), 20)
        w.bits((channels - 1).toLong(), 3)
        w.bits((BITS - 1).toLong(), 5)
        w.bits(0, 36) // total samples: unknown, an endless stream
        repeat(4) { w.bits(0, 32) } // no MD5 of the whole: there is no whole
        return w.toByteArray()
    }

    /**
     * One frame of [frames] samples per channel, from [pcm] (interleaved, 16-bit) starting at sample [offset] (in
     * frames). [frames] is 16 to [MAX_BLOCK]; a shorter frame is allowed only as the last one, which for an endless
     * stream means never, so callers send at least [MIN_BLOCK] where they can.
     */
    fun frame(pcm: ShortArray, frames: Int, offset: Int = 0): ByteArray {
        require(frames in 1..MAX_BLOCK) { "A frame holds 1 to $MAX_BLOCK samples" }
        require((offset + frames) * channels <= pcm.size) { "Not that many samples" }
        val ch = Array(channels) { c -> IntArray(frames) { i -> pcm[(offset + i) * channels + c].toInt() } }
        // Stereo: whichever pair codes smallest. Side (left − right) needs one more bit than the samples.
        var assignment = channels - 1
        var subframes: List<Subframe>
        if (!compress) {
            subframes = ch.map { Subframe.Verbatim(it, BITS) }
        } else if (channels == 2) {
            val left = ch[0]
            val right = ch[1]
            val side = IntArray(frames) { left[it] - right[it] }
            val mid = IntArray(frames) { (left[it] + right[it]) shr 1 }
            val l = Subframe.best(left, BITS)
            val r = Subframe.best(right, BITS)
            val s = Subframe.best(side, BITS + 1)
            val m = Subframe.best(mid, BITS)
            val options = listOf(
                1 to listOf(l, r), // left/right, independent
                8 to listOf(l, s), // left/side
                9 to listOf(s, r), // side/right
                10 to listOf(m, s), // mid/side
            )
            val (a, pick) = options.minBy { (_, p) -> p.sumOf { it.bits } }
            assignment = a
            subframes = pick
        } else {
            subframes = ch.map { Subframe.best(it, BITS) }
        }

        val w = BitWriter(subframes.sumOf { it.bits }.toInt() / 8 + 32)
        // Header
        w.bits(0x3FFE, 14) // sync
        w.bits(0, 1)
        w.bits(1, 1) // variable block size: the frame says its first sample's number
        w.bits(0b0111, 4) // block size: 16 bits at the end of the header
        w.bits(rateCode(sampleRate).toLong(), 4)
        w.bits(assignment.toLong(), 4)
        w.bits(0b100, 3) // 16 bits per sample
        w.bits(0, 1)
        w.utf8(sampleNumber)
        w.bits((frames - 1).toLong(), 16)
        rateTail(sampleRate)?.let { (value, bits) -> w.bits(value.toLong(), bits) }
        w.bits(Crc.crc8(w.bytes(), w.byteLength()).toLong(), 8)
        // Subframes
        subframes.forEach { it.write(w) }
        w.align()
        val crc = Crc.crc16(w.bytes(), w.byteLength())
        w.bits(crc.toLong(), 16)
        sampleNumber += frames
        return w.toByteArray()
    }

    companion object {
        const val BITS = 16
        /** The shortest frame sent but for the very last: the mixer's own block. */
        const val MIN_BLOCK = 16
        /** The longest frame: FLAC's "streamable subset" limit for 48 kHz and below. */
        const val MAX_BLOCK = 4096

        /** The frame header's sample-rate code: common rates have their own, others follow the header. */
        internal fun rateCode(rate: Int): Int = when (rate) {
            88_200 -> 1
            176_400 -> 2
            192_000 -> 3
            8_000 -> 4
            16_000 -> 5
            22_050 -> 6
            24_000 -> 7
            32_000 -> 8
            44_100 -> 9
            48_000 -> 10
            96_000 -> 11
            else -> when {
                rate % 1000 == 0 && rate / 1000 <= 255 -> 12 // kHz, 8 bits
                rate <= 65_535 -> 13 // Hz, 16 bits
                rate % 10 == 0 && rate / 10 <= 65_535 -> 14 // tens of Hz, 16 bits
                else -> 0 // from STREAMINFO
            }
        }

        private fun rateTail(rate: Int): Pair<Int, Int>? = when (rateCode(rate)) {
            12 -> rate / 1000 to 8
            13 -> rate to 16
            14 -> rate / 10 to 16
            else -> null
        }
    }
}

/** One channel of a frame, coded the smallest way found: constant, verbatim, or a fixed predictor with Rice residuals. */
internal sealed class Subframe(val bits: Long) {
    abstract fun write(w: BitWriter)

    class Constant(private val value: Int, private val bps: Int) : Subframe(8L + bps) {
        override fun write(w: BitWriter) {
            w.bits(0, 1)
            w.bits(0, 6)
            w.bits(0, 1)
            w.signed(value, bps)
        }
    }

    class Verbatim(private val samples: IntArray, private val bps: Int) : Subframe(8L + samples.size.toLong() * bps) {
        override fun write(w: BitWriter) {
            w.bits(0, 1)
            w.bits(1, 6)
            w.bits(0, 1)
            samples.forEach { w.signed(it, bps) }
        }
    }

    class Fixed(
        private val samples: IntArray,
        private val bps: Int,
        private val order: Int,
        private val residual: IntArray,
        private val partitionOrder: Int,
        private val params: IntArray,
        bits: Long,
    ) : Subframe(bits) {
        override fun write(w: BitWriter) {
            w.bits(0, 1)
            w.bits((0b001000 or order).toLong(), 6)
            w.bits(0, 1)
            for (i in 0 until order) w.signed(samples[i], bps)
            w.bits(1, 2) // RICE2: 5-bit parameters
            w.bits(partitionOrder.toLong(), 4)
            val parts = 1 shl partitionOrder
            val per = samples.size shr partitionOrder
            var at = order
            for (p in 0 until parts) {
                val end = (p + 1) * per
                val k = params[p]
                w.bits(k.toLong(), 5)
                while (at < end) {
                    w.rice(residual[at], k)
                    at++
                }
            }
        }
    }

    companion object {
        /** The smallest coding of [samples] (each [bps] bits). */
        fun best(samples: IntArray, bps: Int): Subframe {
            val first = samples[0]
            if (samples.all { it == first }) return Constant(first, bps)
            val verbatim = Verbatim(samples, bps)
            var best: Subframe = verbatim
            // The fixed order whose residual is smallest in sum is nearly always the one that codes smallest.
            val maxOrder = minOf(4, samples.size - 1)
            var pickOrder = -1
            var pickSum = Long.MAX_VALUE
            var pickResidual: IntArray? = null
            for (order in 0..maxOrder) {
                val r = residual(samples, order)
                var sum = 0L
                for (i in order until r.size) sum += abs(r[i].toLong())
                if (sum < pickSum) {
                    pickSum = sum
                    pickOrder = order
                    pickResidual = r
                }
            }
            val r = pickResidual ?: return best
            val rice = Rice.best(r, pickOrder, samples.size)
            val bits = 8L + pickOrder.toLong() * bps + 2 + 4 + rice.bits
            if (bits < best.bits) best = Fixed(samples, bps, pickOrder, r, rice.partitionOrder, rice.params, bits)
            return best
        }

        /** FLAC's fixed predictor of [order] (0 to 4): what's left of each sample once its predicted part is taken. */
        fun residual(x: IntArray, order: Int): IntArray {
            val r = IntArray(x.size)
            for (i in order until x.size) {
                r[i] = when (order) {
                    0 -> x[i]
                    1 -> x[i] - x[i - 1]
                    2 -> x[i] - 2 * x[i - 1] + x[i - 2]
                    3 -> x[i] - 3 * x[i - 1] + 3 * x[i - 2] - x[i - 3]
                    else -> x[i] - 4 * x[i - 1] + 6 * x[i - 2] - 4 * x[i - 3] + x[i - 4]
                }
            }
            return r
        }
    }
}

/** Rice coding of a residual: the partitioning and parameters that take fewest bits. */
internal object Rice {
    class Choice(val partitionOrder: Int, val params: IntArray, val bits: Long)

    /** Rice parameters go up to 30 with 5-bit parameters (31 means escape, not used). */
    private const val MAX_PARAM = 30

    fun best(residual: IntArray, order: Int, blockSize: Int): Choice {
        var best: Choice? = null
        var p = 0
        while (p <= 8) {
            if (blockSize % (1 shl p) != 0) break
            val per = blockSize shr p
            if (per <= order) break
            val params = IntArray(1 shl p)
            var bits = 0L
            for (part in 0 until (1 shl p)) {
                val start = if (part == 0) order else part * per
                val end = (part + 1) * per
                val (k, cost) = param(residual, start, end)
                params[part] = k
                bits += 5 + cost
            }
            if (best == null || bits < best.bits) best = Choice(p, params, bits)
            p++
        }
        return best ?: Choice(0, intArrayOf(0), Long.MAX_VALUE / 4)
    }

    /** The best parameter for residual[start until end], and its bits. */
    fun param(residual: IntArray, start: Int, end: Int): Pair<Int, Long> {
        val n = end - start
        if (n <= 0) return 0 to 0L
        var sum = 0L
        for (i in start until end) sum += zigzag(residual[i])
        // The mean's size in bits is a good first guess; its neighbours are tried too.
        val mean = sum / n
        val guess = if (mean <= 0) 0 else (63 - java.lang.Long.numberOfLeadingZeros(mean)).coerceIn(0, MAX_PARAM)
        var bestK = guess
        var bestBits = Long.MAX_VALUE
        for (k in maxOf(0, guess - 1)..minOf(MAX_PARAM, guess + 1)) {
            var bits = n.toLong() * (k + 1)
            for (i in start until end) bits += zigzag(residual[i]) ushr k
            if (bits < bestBits) {
                bestBits = bits
                bestK = k
            }
        }
        return bestK to bestBits
    }

    fun zigzag(v: Int): Long = ((v.toLong() shl 1) xor (v.toLong() shr 63))
}

/** Writes bits, most significant first, into a growing byte array. */
internal class BitWriter(capacity: Int) {
    private var buf = ByteArray(maxOf(16, capacity))
    private var bytes = 0
    private var acc = 0L
    private var accBits = 0

    fun bits(value: Long, count: Int) {
        var left = count
        while (left > 0) {
            // At most 32 bits at a time: with under 8 waiting, the 64-bit accumulator never overflows.
            val n = minOf(left, 32)
            val chunk = (value ushr (left - n)) and ((1L shl n) - 1)
            acc = (acc shl n) or chunk
            accBits += n
            left -= n
            while (accBits >= 8) {
                accBits -= 8
                put(((acc ushr accBits) and 0xFF).toInt())
            }
            acc = acc and ((1L shl accBits) - 1)
        }
    }

    fun signed(value: Int, count: Int) = bits(value.toLong() and ((1L shl count) - 1), count)

    /** [v] Rice-coded with parameter [k]: the quotient in unary (zeros ended by a one), then k low bits. */
    fun rice(v: Int, k: Int) {
        val u = Rice.zigzag(v)
        var q = u ushr k
        while (q >= 32) {
            bits(0, 32)
            q -= 32
        }
        bits(1, q.toInt() + 1)
        if (k > 0) bits(u and ((1L shl k) - 1), k)
    }

    /** FLAC's UTF-8-like coding of a frame's first sample number (up to 36 bits). */
    fun utf8(n: Long) {
        require(n >= 0 && n < (1L shl 36))
        if (n < 0x80) {
            bits(n, 8)
            return
        }
        // Bytes after the first carry 6 bits each; the first carries what's left after its length marker.
        var count = 2
        while (count < 7 && n >= (1L shl (5 * count + 1))) count++
        val lead = ((0xFF shl (8 - count)) and 0xFF).toLong()
        bits(lead or (n ushr (6 * (count - 1))), 8)
        for (i in count - 2 downTo 0) bits(0x80L or ((n ushr (6 * i)) and 0x3F), 8)
    }

    fun align() {
        if (accBits > 0) bits(0, 8 - accBits)
    }

    /** Whole bytes written so far. */
    fun byteLength(): Int = bytes

    fun bytes(): ByteArray = buf

    fun toByteArray(): ByteArray {
        check(accBits == 0) { "Not on a byte boundary" }
        return buf.copyOf(bytes)
    }

    private fun put(b: Int) {
        if (bytes == buf.size) buf = buf.copyOf(buf.size * 2)
        buf[bytes++] = b.toByte()
    }
}

/** FLAC's checksums: CRC-8 (poly 0x07) of a frame header and CRC-16 (poly 0x8005) of a whole frame. */
internal object Crc {
    private val table8 = IntArray(256) { i ->
        var c = i
        repeat(8) { c = if (c and 0x80 != 0) ((c shl 1) xor 0x07) and 0xFF else (c shl 1) and 0xFF }
        c
    }
    private val table16 = IntArray(256) { i ->
        var c = i shl 8
        repeat(8) { c = if (c and 0x8000 != 0) ((c shl 1) xor 0x8005) and 0xFFFF else (c shl 1) and 0xFFFF }
        c
    }

    fun crc8(data: ByteArray, length: Int, from: Int = 0): Int {
        var c = 0
        for (i in from until length) c = table8[c xor (data[i].toInt() and 0xFF)]
        return c
    }

    fun crc16(data: ByteArray, length: Int, from: Int = 0): Int {
        var c = 0
        for (i in from until length) c = ((c shl 8) and 0xFFFF) xor table16[((c ushr 8) xor (data[i].toInt() and 0xFF)) and 0xFF]
        return c
    }
}

/**
 * The mix's float samples as 16-bit for the stream, with a little triangular dither (under one step) so quiet fades
 * don't turn into steps; digital silence stays exactly silent, so a pause sends nothing but zeros.
 */
class Dither(seed: Int = 0x2545F491) {
    private var state = seed.takeIf { it != 0 } ?: 1

    private fun next(): Float {
        // xorshift32: cheap and good enough for noise below one step.
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        state = x
        return (x ushr 8) / 16_777_216f
    }

    /** [count] samples of [src] from [from] into [dst] at [to], each −1..1 float to a 16-bit sample. */
    fun convert(src: FloatArray, from: Int, dst: ShortArray, to: Int, count: Int) {
        for (i in 0 until count) {
            val x = src[from + i]
            dst[to + i] = if (x == 0f) 0 else {
                val v = (x * 32_767f + next() - next()).roundToInt()
                v.coerceIn(-32_768, 32_767).toShort()
            }
        }
    }
}
