package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** Lining the silent tail up by its sound (issue #818): the maths over two players' kept sound. */
class SoundAlignTest {
    private val rate = 44_100
    private val d = SoundTap.DECIMATION
    private val song = FloatArray(rate * 8).also { s ->
        val random = Random(7)
        // Noise with a beat on it: broadband enough to match to the sample, repetitive enough to have a wrong place a bar away.
        for (i in s.indices) s[i] = (random.nextFloat() * 2 - 1) * 0.3f + sin(i * 2 * Math.PI * 55 / rate).toFloat() * 0.3f
    }

    /**
     * What a player's [SoundTap] holds when it has played the song from [fromFrame] since [startNanos], as of [nowNanos],
     * keeping [keptSeconds]. Its output stands at the frame the clock says; the marks are buffer moments on that line,
     * one of them a little late, as a real one can be.
     */
    private fun snapshot(fromFrame: Int, startNanos: Long, nowNanos: Long, keptSeconds: Int): SoundTap.Snapshot {
        val outFrames = ((nowNanos - startNanos) * rate / 1e9).toLong() / d * d
        val kept = minOf(outFrames, keptSeconds.toLong() * rate) / d
        val sound = FloatArray(kept.toInt()) { k ->
            val first = (outFrames - kept * d + k * d).toInt() + fromFrame
            var sum = 0f
            for (j in 0 until d) sum += song[first + j]
            sum / d
        }
        val marks = (0 until 12).map { i ->
            val at = nowNanos - i * 20_000_000L
            val frames = ((at - startNanos) * rate / 1e9).toLong()
            // Buffer 5 came 8 ms late: the output had drained 8 ms worth by then, which the line must not follow.
            if (i == 5) SoundTap.Mark(at + 8_000_000L, frames) else SoundTap.Mark(at, frames)
        }
        return SoundTap.Snapshot(rate, d, sound, outFrames, marks)
    }

    @Test fun findsHowFarBehindTheTailIsToTheSample() {
        val now = 10_000_000_000L
        val mainStart = now - 5_000_000_000L  // the main player has played 5 s from the song's start
        val tailStartFrame = 4 * rate  // the tail joined at 4.0 s of the song...
        val tailStart = now - 800_000_000L  // ...800 ms ago, so it's heard at 4.8 s while the main player is at 5.0 s
        val main = snapshot(0, mainStart, now, 3)
        val tail = snapshot(tailStartFrame, tailStart, now, 1)
        val match = SoundAlign.match(main, tail, now, expectedBehindMs = 150, rangeMs = SoundAlign.RANGE_MS)!!
        val truth = (5.0 - 4.8) * rate
        assertTrue(match.toString(), abs(match.behindFrames - truth) <= d)
        assertEquals(200, match.behindMs)
        assertTrue(match.toString(), match.sure)
        assertTrue(match.toString(), match.peak > 0.95f)
    }

    @Test fun findsATailThatIsAhead() {
        val now = 10_000_000_000L
        val main = snapshot(0, now - 5_000_000_000L, now, 3)
        // The tail started at 4.5 s of the song 600 ms ago: it's heard at 5.1 s, 100 ms ahead of the main player.
        val tail = snapshot((4.5 * rate).toInt(), now - 600_000_000L, now, 1)
        val match = SoundAlign.match(main, tail, now, expectedBehindMs = 0, rangeMs = SoundAlign.RANGE_MS)!!
        assertEquals(-100, match.behindMs)
        assertTrue(match.sure)
    }

    @Test fun aWrongGuessOfWhereToLookStillFindsIt() {
        val now = 10_000_000_000L
        val main = snapshot(0, now - 5_000_000_000L, now, 3)
        val tail = snapshot(4 * rate, now - 800_000_000L, now, 1)
        // The positions said the tail was 900 ms behind; it's 200 ms behind, within the range looked at.
        assertEquals(200, SoundAlign.match(main, tail, now, expectedBehindMs = 900, rangeMs = SoundAlign.RANGE_MS)!!.behindMs)
    }

    @Test fun silenceAndTooLittleSoundGiveNoMatch() {
        val now = 10_000_000_000L
        val main = snapshot(0, now - 5_000_000_000L, now, 3)
        val tail = snapshot(4 * rate, now - 800_000_000L, now, 1)
        val quiet = SoundTap.Snapshot(rate, d, FloatArray(tail.sound.size) { 0.0001f }, tail.endFrame, tail.marks)
        assertNull(SoundAlign.match(main, quiet, now, 0, SoundAlign.RANGE_MS))
        val short = SoundTap.Snapshot(rate, d, tail.sound.copyOf(100), tail.endFrame, tail.marks)
        assertNull(SoundAlign.match(main, short, now, 0, SoundAlign.RANGE_MS))
        assertNull(SoundAlign.match(SoundTap.Snapshot(48_000, d, main.sound, main.endFrame, main.marks), tail, now, 0, SoundAlign.RANGE_MS))
    }

    @Test fun aDifferentSongIsNotAMatch() {
        val now = 10_000_000_000L
        val main = snapshot(0, now - 5_000_000_000L, now, 3)
        val random = Random(3)
        val other = FloatArray(rate) { (random.nextFloat() * 2 - 1) * 0.3f }
        val tail = SoundTap.Snapshot(rate, d, FloatArray(rate / d) { k -> other[k * d] }, (4.8 * rate).toLong() / d * d, main.marks)
        val match = SoundAlign.match(main, tail, now, 0, SoundAlign.RANGE_MS)
        assertNotNull(match)
        assertTrue(match.toString(), !match!!.sure)
    }

    @Test fun theOutputLineIgnoresALateBuffer() {
        val now = 10_000_000_000L
        val s = snapshot(0, now - 5_000_000_000L, now, 3)
        assertEquals(5.0 * rate, s.framesAt(now), 2.0)
        assertEquals(5.5 * rate, s.framesAt(now + 500_000_000L), 2.0)
    }
}
