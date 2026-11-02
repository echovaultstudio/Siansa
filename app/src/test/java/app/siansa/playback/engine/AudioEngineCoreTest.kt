package app.siansa.playback.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** The new audio engine's mixing (issue #826): reading streams at another rate, cueing them on one clock, summing. */
class AudioEngineCoreTest {
    private fun float(samples: FloatArray): ByteBuffer {
        val b = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) b.putFloat(s)
        b.flip()
        return b
    }

    private fun short(samples: FloatArray): ByteBuffer {
        val b = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) b.putShort((s * 32767).toInt().toShort())
        b.flip()
        return b
    }

    /** The level of a sine at [hz] in [x], in dB of full scale. */
    private fun toneDb(x: DoubleArray, rate: Int, hz: Double): Double {
        var c = 0.0
        var s = 0.0
        for (i in x.indices) {
            val w = 2 * PI * hz * i / rate
            c += x[i] * cos(w)
            s += x[i] * sin(w)
        }
        return 20 * log10(2 * sqrt(c * c + s * s) / x.size + 1e-12)
    }

    private fun renderAll(stream: DeckStream, frames: Int, outRate: Int): FloatArray {
        val out = FloatArray(frames * 2)
        var off = 0
        while (off + 256 <= frames) {
            stream.render(out, off, 256, off.toLong(), outRate)
            off += 256
        }
        return out
    }

    private fun sine(rate: Int, seconds: Double, vararg tones: Pair<Double, Double>) =
        FloatArray((rate * seconds).toInt() * 2) { i -> tones.sumOf { (hz, amp) -> amp * sin(2 * PI * hz * (i / 2) / rate) }.toFloat() }

    @Test fun atTheOutputsRateASongIsCopiedThroughUntouched() {
        val src = FloatArray(4800 * 2) { i -> ((i * 7919) % 2001 - 1000) / 2000f }
        val stream = DeckStream(48_000, 2, DeckStream.Encoding.PCM_FLOAT)
        stream.write(float(src))
        stream.play()
        val out = renderAll(stream, 4608, 48_000)
        // After the 5 ms fade in at the start: sample for sample.
        for (i in 300 * 2 until 4608 * 2) assertEquals(src[i], out[i], 0f)
    }

    @Test fun anotherRateIsReadThroughTheFilterFlatToTwentyKilohertz() {
        for (hz in listOf(1_000.0, 10_000.0, 19_000.0)) {
            val stream = DeckStream(44_100, 2, DeckStream.Encoding.PCM_FLOAT, capacityMs = 3_000)
            stream.write(float(sine(44_100, 1.0, hz to 0.5)))
            stream.endOfStream()
            stream.play()
            val out = renderAll(stream, 24_000, 48_000)
            val x = DoubleArray(12_000) { out[(6_000 + it) * 2].toDouble() }
            assertEquals("$hz Hz", -6.02, toneDb(x, 48_000, hz), 0.1)
            assertTrue("nothing else at $hz Hz", toneDb(x, 48_000, hz + 3_000) < -90)
        }
    }

    @Test fun aSongPlayedFasterGoesUpInPitchWithoutFoldingBack() {
        val stream = DeckStream(44_100, 2, DeckStream.Encoding.PCM_FLOAT, capacityMs = 4_000)
        stream.write(float(sine(44_100, 3.0, 1_000.0 to 0.4, 21_800.0 to 0.4)))
        stream.endOfStream()
        stream.play()
        stream.speed = 1.03f
        val out = renderAll(stream, 88_200, 44_100)
        val x = DoubleArray(44_100) { out[(22_050 + it) * 2].toDouble() }
        assertEquals(-7.96, toneDb(x, 44_100, 1_030.0), 0.1)
        // 21.8 kHz played 3 % faster would be past the top (22.05 kHz) and fold back to 21.6 kHz: it's filtered out.
        assertTrue(toneDb(x, 44_100, 44_100 - 21_800 * 1.03) < -60)
    }

    @Test fun sixteenBitMonoComesOutOnBothSidesAndItsPositionReachesWhatWasWritten() {
        val stream = DeckStream(48_000, 1, DeckStream.Encoding.PCM_16)
        assertTrue(stream.write(short(FloatArray(4_000) { 0.25f })))
        stream.endOfStream()
        stream.play()
        val out = FloatArray(512)
        var mix = 0L
        while (!stream.finished && mix < 10_000) {
            stream.render(out, 0, 256, mix, 48_000)
            mix += 256
        }
        assertTrue(stream.finished)
        // Media3 waits until the position reaches what it wrote: rounded up, it does.
        assertTrue(stream.positionUsAt(mix + 1_000.0) * 48_000 / 1_000_000 >= 4_000)
    }

    @Test fun aCuedStreamSoundsOnTheExactMixFrame() {
        // A click every half second of a 44.1 kHz song from 1.0 s; cued so 2.0 s of the song is on mix frame 96 000.
        val src = FloatArray(44_100 * 4 * 2)
        for (k in 0 until 8) {
            val f = k * 44_100 / 2
            src[f * 2] = 1f
            src[f * 2 + 1] = 1f
        }
        val stream = DeckStream(44_100, 2, DeckStream.Encoding.PCM_FLOAT, capacityMs = 5_000)
        val deck = MixCore.Deck("tail")
        deck.mark(0, 1_000_000)
        deck.add(stream)
        stream.write(float(src))
        stream.play()
        val cued = deck.cueNow(2_000_000, 96_000, 48_000)!!
        assertEquals(44_100L, cued.targetFrame)
        val out = FloatArray((48_000 * 3 + 256) * 2)
        var mix = 0L
        while (mix < 48_000 * 3) {
            stream.render(out, mix.toInt(), 256, mix, 48_000)
            mix += 256
        }
        assertTrue("silent before", (0 until 95_990).all { out[it * 2] == 0f })
        val loudest = (95_900 until 96_100).maxBy { abs(out[it * 2]) }
        assertEquals(96_000, loudest)
        assertEquals(120_000, (119_900 until 120_100).maxBy { abs(out[it * 2]) })  // the next, half a second on at 48 kHz
        assertEquals(2_500_000L, deck.mediaUsAt(120_000.0))
        assertEquals(192_000.0, deck.mixFrameAt(4_000_000, 48_000)!!, 1.0)
    }

    @Test fun theNextSongCuedBeforeItsSoundArrivesStillComesInOnTheGrid() {
        val deck = MixCore.Deck("main")
        deck.cueNext(5_000_000, 10_000, 48_000)  // 5.0 s of the next song on mix frame 10 000
        deck.mark(0, 4_900_000)  // its seek landed at 4.9 s
        val stream = DeckStream(48_000, 2, DeckStream.Encoding.PCM_FLOAT, capacityMs = 3_000)
        deck.add(stream)
        assertEquals(4_800L, deck.lastCued!!.targetFrame)
        stream.play()
        val scratch = FloatArray(512)
        var mix = 0L
        while (mix < 24_000) {  // half a second with nothing loaded yet
            stream.render(scratch, 0, 256, mix, 48_000)
            mix += 256
        }
        val src = FloatArray(48_000 * 2 * 2)
        src[24_000 * 2] = 1f  // a click at 5.4 s
        stream.write(float(src))
        var click = -1L
        while (mix < 40_000) {
            stream.render(scratch, 0, 256, mix, 48_000)
            for (i in 0 until 256) if (scratch[i * 2] > 0.5f) click = mix + i
            mix += 256
        }
        // 5.4 s is 0.4 s after 5.0 s: mix frame 10 000 + 19 200, as if it had played from its cue.
        assertEquals(29_200L, click)
    }

    @Test fun theMixAddsDecksAtTheirVolumeUnderTheCeiling() {
        val core = MixCore(48_000)
        val a = core.deck("main")
        val b = core.deck("tail")
        val sa = DeckStream(48_000, 2, DeckStream.Encoding.PCM_FLOAT, 3_000)
        val sb = DeckStream(48_000, 2, DeckStream.Encoding.PCM_FLOAT, 3_000)
        sa.write(float(FloatArray(48_000 * 2 * 2) { 0.7f }))
        sb.write(float(FloatArray(48_000 * 2 * 2) { 0.7f }))
        sa.play()
        sb.play()
        a.add(sa)
        b.add(sb)
        val out = FloatArray(512)
        var peak = 0f
        for (block in 0 until 100) {
            core.render(out, 256, block * 256L)
            for (v in out) peak = maxOf(peak, abs(v))
        }
        assertTrue("peak $peak", peak <= MixCore.CEILING + 1e-6)
        sb.volume = 0f
        for (block in 100 until 300) core.render(out, 256, block * 256L)
        assertEquals(0.7f, out[256], 0.01f)
    }

    @Test fun aStreamSilencedAtAFrameStopsThereWithoutAFade() {
        val stream = DeckStream(48_000, 2, DeckStream.Encoding.PCM_FLOAT, 3_000)
        stream.write(float(FloatArray(48_000 * 2) { 0.5f }))
        stream.play()
        stream.cutAt(1_000)
        val out = renderAll(stream, 2_048, 48_000)
        assertEquals(0.5f, out[999 * 2], 0.001f)
        assertEquals(0f, out[1_000 * 2], 0f)
    }
}
