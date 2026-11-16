package app.siansa.playback

import app.siansa.playback.engine.KickListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The grid check of a DJ blend (the owner's DJ plan, siansa-dev #63): kicks heard in the sound, against the song's beat
 * grid. Made-up songs: a kick on every beat, a bassline under it, hi-hats and noise over it.
 */
class GridCheckTest {
    private val rate = 48_000

    /** [seconds] of a 4/4 song at [bpm] whose kicks start at [firstMs], as the engine hands a deck its sound. */
    private fun song(bpm: Double, firstMs: Double, seconds: Double, bass: Boolean = true, seed: Int = 1): FloatArray {
        val n = (seconds * rate).toInt()
        val out = FloatArray(n * 2)
        val beat = 60.0 / bpm
        val random = java.util.Random(seed.toLong())
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            val since = ((t - firstMs / 1000) % beat + beat) % beat
            val k = if (t >= firstMs / 1000) 0.9 * exp(-since / 0.09) * sin(2 * PI * (50 + 80 * exp(-since / 0.02)) * since) else 0.0
            // An offbeat bassline, quieter than the kick, and hi-hats on the off-beats: neither may be heard as a kick.
            val half = ((since - beat / 2) + beat) % beat
            val b = if (bass) 0.25 * sin(2 * PI * 41.2 * t) * (if (half < beat / 3) 1.0 else 0.2) else 0.0
            val h = 0.15 * exp(-half / 0.01) * (random.nextDouble() * 2 - 1)
            val x = (k + b + h + 0.01 * (random.nextDouble() * 2 - 1)).toFloat()
            out[i * 2] = x
            out[i * 2 + 1] = x
        }
        return out
    }

    /** The kicks the listener hears in [stereo], in ms, fed in blocks as the engine does, late by [HEARD_LATE_MS] taken off. */
    private fun heard(stereo: FloatArray, block: Int = 960): List<Double> {
        val listener = KickListener(rate)
        val frames = stereo.size / 2
        var at = 0
        val buffer = FloatArray(block * 2)
        while (at < frames) {
            val n = minOf(block, frames - at)
            System.arraycopy(stereo, at * 2, buffer, 0, n * 2)
            listener.feed(buffer, n, at.toLong())
            at += n
        }
        return listener.take().map { it * 1000.0 / rate - GridCheck.HEARD_LATE_MS }
    }

    private fun grid(bpm: Double, beatMs: Double) = BeatEnd(bpm = bpm, beatMs = beatMs, barMs = beatMs, rhythm = 0.98f, steady = 0.95f)

    @Test fun everyKickIsHeardOnItsBeatAndNothingElse() {
        val kicks = heard(song(128.0, 500.0, 20.0))
        val beat = 60_000.0 / 128
        // The listener learns the song's level over its first 0.4 s, as a blend's first moments: no kick is taken then.
        val expected = generateSequence(500.0) { it + beat }.takeWhile { it < 20_000 - 50 }.toList()
        assertEquals("one kick per beat, no bass or hi-hat taken for one", expected.size.toDouble(), kicks.size.toDouble(), 2.0)
        // Each heard within a few ms of its beat, once the listener's own lateness is taken off.
        val near = kicks.map { t -> expected.minOf { kotlin.math.abs(it - t) } }
        assertTrue("worst ${near.max()} ms", near.max() < 6.0)
        val side = GridCheck.side(kicks, grid(128.0, 500.0))
        assertTrue(side.sure)
        assertFalse(side.words(), side.off)
        assertTrue(side.words(), kotlin.math.abs(side.offMs!!) <= 3)
    }

    @Test fun aGridThirtyMsOffIsFoundAndSaidSo() {
        // The song's kicks are where they are; its grid says each beat comes 30 ms earlier.
        val kicks = heard(song(126.0, 1_000.0, 16.0, seed = 3))
        val side = GridCheck.side(kicks, grid(126.0, 970.0))
        assertTrue(side.off)
        assertEquals(30.0, side.offMs!!.toDouble(), 4.0)
        // And 30 ms the other way.
        assertEquals(-30.0, GridCheck.side(kicks, grid(126.0, 1_030.0)).offMs!!.toDouble(), 4.0)
    }

    @Test fun aGridOfAnotherTempoIsFoundOffToo() {
        // A grid at 125 BPM laid on a song at 126 drifts: its median is far off, or few kicks are near its beats.
        val kicks = heard(song(126.0, 1_000.0, 20.0, seed = 4))
        val side = GridCheck.side(kicks, grid(125.0, 1_000.0))
        assertTrue(side.words(), side.off || !side.sure || kotlin.math.abs(side.offMs!!) > GridCheck.OFF_MS)
    }

    @Test fun quietOrBeatlessSoundTellsNothing() {
        val quiet = FloatArray(rate * 10 * 2) { (java.util.Random(it.toLong()).nextDouble() * 0.002).toFloat() }
        assertEquals(0, heard(quiet).size)
        val side = GridCheck.side(emptyList(), grid(128.0, 0.0))
        assertNull(side.offMs)
        assertFalse(side.off)
        assertEquals("too few kicks heard (0)", side.words())
    }

    @Test fun theLogSaysWhichGridIsOff() {
        val holds = GridCheck.Side(24, 3)
        val off = GridCheck.Side(20, -27)
        assertEquals(
            "Grid check, a into b: a kicks +3 ms off its grid (24 kicks), b kicks -27 ms off its grid (20 kicks): the grid of b is off: analyse it again in Siansa Beats, or mark the blend",
            GridCheck.describe("a", holds, "b", off),
        )
        assertTrue(GridCheck.describe("a", holds, "b", holds).endsWith(": both grids hold"))
        assertTrue(GridCheck.describe("a", holds, "b", GridCheck.Side(3, 1)).endsWith(": not enough kicks to tell"))
        assertTrue(GridCheck.describe("a", off, "b", off).endsWith("the grid of a and b is off: analyse them again in Siansa Beats, or mark the blend"))
    }
}
