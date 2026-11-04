package app.siansa.playback.gapless

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * The endless FLAC stream gapless casting sends the TV (issue #114). Each frame is read back by [TestFlacReader], a
 * small FLAC decoder written from the format's description, and must give back exactly the samples sent. (The same
 * encoder's output was also decoded by FFmpeg, bit for bit, while writing it.)
 */
class FlacStreamTest {
    private fun stereo(frames: Int, f: (Int, Int) -> Int) = ShortArray(frames * 2) { i -> f(i / 2, i % 2).coerceIn(-32768, 32767).toShort() }

    private fun music(frames: Int, seed: Long = 1): ShortArray {
        val rnd = java.util.Random(seed)
        return stereo(frames) { i, c ->
            (sin(i * (if (c == 0) 0.0627 else 0.0941)) * 12000 + sin(i * 0.31) * 3000 + rnd.nextGaussian() * 400).toInt()
        }
    }

    private fun roundTrip(pcm: ShortArray, sizes: List<Int>, compress: Boolean = true): FlacEncoder {
        val enc = FlacEncoder(44_100, 2, compress)
        val reader = TestFlacReader(enc.header())
        var at = 0
        for (n in sizes) {
            val frame = enc.frame(pcm, n, at)
            val (first, samples) = reader.frame(frame)
            assertEquals("frame at $at says where it starts", at.toLong(), first)
            assertArrayEquals("frame at $at, $n samples", pcm.copyOfRange(at * 2, (at + n) * 2), samples)
            at += n
        }
        return enc
    }

    @Test fun theHeaderSaysAnEndlessStreamOf16BitStereo() {
        val h = FlacEncoder(44_100, 2).header()
        assertEquals(42, h.size)
        assertEquals("fLaC", String(h, 0, 4, Charsets.US_ASCII))
        val info = TestFlacReader(h)
        assertEquals(44_100, info.rate)
        assertEquals(2, info.channels)
        assertEquals(16, info.bits)
        assertEquals(0L, info.totalSamples)
        assertTrue("last metadata block", h[4].toInt() and 0x80 != 0)
    }

    @Test fun musicComesBackExactlyFrameByFrame() {
        val pcm = music(4096 * 3 + 256 + 1000)
        roundTrip(pcm, listOf(4096, 4096, 256, 1000, 4096))
    }

    @Test fun anyFrameLengthWorksSoAPauseSendsWhatThereIs() {
        val sizes = listOf(16, 17, 255, 256, 1, 4096, 341)
        roundTrip(music(sizes.sum(), seed = 7), sizes)
    }

    @Test fun silenceTakesAlmostNothingAndComesBackSilent() {
        val pcm = ShortArray(4096 * 2)
        val enc = FlacEncoder(44_100, 2)
        val frame = enc.frame(pcm, 4096)
        assertTrue("silence in ${frame.size} bytes", frame.size < 32)
        assertArrayEquals(pcm, TestFlacReader(enc.header()).frame(frame).second)
    }

    @Test fun loudestSamplesAndHardEdgesSurvive() {
        val pcm = stereo(4096) { i, c -> if ((i / 7 + c) % 2 == 0) 32767 else -32768 }
        roundTrip(pcm, listOf(4096))
        val noise = java.util.Random(3).let { r -> stereo(4096) { _, _ -> r.nextInt(65536) - 32768 } }
        roundTrip(noise, listOf(4096))
    }

    @Test fun leftRightSideAndMidSideAllDecode() {
        // Identical channels (mid/side or a side channel wins), one silent, and opposite.
        roundTrip(stereo(4096) { i, _ -> (sin(i * 0.05) * 20000).toInt() }, listOf(4096))
        roundTrip(stereo(4096) { i, c -> if (c == 0) (sin(i * 0.05) * 20000).toInt() else 0 }, listOf(4096))
        roundTrip(stereo(4096) { i, c -> (sin(i * 0.05) * 20000).toInt() * (if (c == 0) 1 else -1) }, listOf(4096))
    }

    @Test fun uncompressedFramesHaveASteadySize() {
        // Chrome buffers by bytes: the stream the TV gets is the same size every second, loud or quiet.
        val enc = FlacEncoder(44_100, 2, compress = false)
        val loud = enc.frame(music(4096), 4096)
        val quiet = enc.frame(ShortArray(4096 * 2), 4096)
        assertTrue(kotlin.math.abs(loud.size - quiet.size) <= 4)
        assertTrue(loud.size >= 4096 * 4)
        roundTrip(music(8192, seed = 9), listOf(4096, 4096), compress = false)
    }

    @Test fun framesAreNumberedBySampleFarIntoTheStream() {
        // Hours in (7.5 h at 44.1 kHz, and FLAC's very last sample number): the number takes more bytes in the header.
        val pcm = music(4096)
        for (start in listOf(1_200_000_000L, (1L shl 36) - 4096)) {
            val enc = FlacEncoder(44_100, 2, firstSample = start)
            val (first, samples) = TestFlacReader(enc.header()).frame(enc.frame(pcm, 4096))
            assertEquals(start, first)
            assertArrayEquals(pcm, samples)
            assertEquals(start + 4096, enc.sampleNumber)
        }
    }

    @Test fun headerSampleNumbersUseFlacsUtf8Coding() {
        fun coded(n: Long) = BitWriter(8).apply { utf8(n) }.toByteArray().joinToString(" ") { "%02X".format(it) }
        assertEquals("00", coded(0))
        assertEquals("7F", coded(0x7F))
        assertEquals("C2 80", coded(0x80))
        assertEquals("DF BF", coded(0x7FF))
        assertEquals("E0 A0 80", coded(0x800))
        assertEquals("EF BF BF", coded(0xFFFF))
        assertEquals("F0 90 80 80", coded(0x10000))
        assertEquals("FE BF BF BF BF BF BF", coded((1L shl 36) - 1))
    }

    @Test fun checksumsAreFlacs() {
        val check = "123456789".toByteArray(Charsets.US_ASCII)
        assertEquals(0xF4, Crc.crc8(check, check.size))
        assertEquals(0xFEE8, Crc.crc16(check, check.size))
    }

    @Test fun rarerSampleRatesAreSaidInTheHeader() {
        assertEquals(9, FlacEncoder.rateCode(44_100))
        assertEquals(10, FlacEncoder.rateCode(48_000))
        assertEquals(12, FlacEncoder.rateCode(11_000))
        assertEquals(13, FlacEncoder.rateCode(11_025))
        val pcm = music(1024)
        for (rate in listOf(48_000, 11_025, 64_000, 352_800)) {
            val enc = FlacEncoder(rate, 2)
            assertArrayEquals("$rate Hz", pcm, TestFlacReader(enc.header()).frame(enc.frame(pcm, 1024)).second)
        }
    }

    @Test fun ditherKeepsSilenceSilentAndStaysWithinAStep() {
        val d = Dither(42)
        val src = FloatArray(1000) { i -> if (i % 3 == 0) 0f else (i - 500) / 600f }
        val dst = ShortArray(1000)
        d.convert(src, 0, dst, 0, 1000)
        for (i in src.indices) {
            if (src[i] == 0f) assertEquals(0, dst[i].toInt())
            else {
                val exact = (src[i] * 32767f).coerceIn(-32768f, 32767f)
                assertTrue("sample $i: ${dst[i]} for $exact", kotlin.math.abs(dst[i] - exact) <= 1.5f)
            }
        }
        val loud = ShortArray(2)
        d.convert(floatArrayOf(4f, -4f), 0, loud, 0, 2)
        assertEquals(32767, loud[0].toInt())
        assertEquals(-32768, loud[1].toInt())
    }
}
