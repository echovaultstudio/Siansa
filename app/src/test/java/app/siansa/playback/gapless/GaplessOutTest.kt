package app.siansa.playback.gapless

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The mix on its way to the TV (issue #114): into the ring as 16-bit, at the TV's pace. */
class GaplessOutTest {
    private val rate = 44_100

    @Test fun eachBlockGoesIntoTheRingFromItsMixFrame() {
        val ring = PcmRing(2, rate)
        val clock = TvClock(rate)
        val out = GaplessOut(ring, clock, rate, baseLeadMs = 1_500, name = "TV", sleep = { error("no wait so far ahead") }, now = { 0L })
        val block = FloatArray(512) { if (it % 2 == 0) 0.5f else -0.5f }
        assertTrue(out.write(block, 256, 10_240))
        assertEquals(10_240L, ring.origin)
        assertEquals(10_496L, ring.end)
        val back = ShortArray(512)
        ring.read(10_240, back, 256)
        assertTrue(kotlin.math.abs(back[0] - 16_383) <= 2)
        assertTrue(kotlin.math.abs(back[1] + 16_383) <= 2)
        // A gap in the mix frames (the engine went elsewhere and came back): the ring starts again there.
        out.write(block, 256, 20_000)
        assertEquals(20_000L, ring.origin)
        assertEquals(1, out.restarts)
        // What's heard is the TV's.
        clock.started(1, 20_000, 0)
        assertEquals(20_000.0, out.heard(0), 0.0)
    }

    @Test fun itWaitsWhileTheMixIsTheLeadAheadOfTheTv() {
        val ring = PcmRing(2, rate * 4)
        val clock = TvClock(rate)
        var t = 0L
        var slept = 0L
        clock.started(1, 0, 0)
        clock.report(1, 0.0, moving = true, nanos = 0)
        val out = GaplessOut(ring, clock, rate, baseLeadMs = 1_000, name = "TV", sleep = { ms -> slept += ms; t += ms * 1_000_000 }, now = { t })
        val block = FloatArray(512)
        var at = 0L
        // Two seconds of mix: the first second goes at once, the rest as the TV plays.
        while (at < rate * 2) {
            out.write(block, 256, at)
            at += 256
        }
        assertTrue("waited about a second, not $slept ms", slept in 950..1_050)
    }

    @Test fun stoppedItTakesNoMore() {
        val ring = PcmRing(2, rate)
        val out = GaplessOut(ring, TvClock(rate), rate, 1_500, "TV", sleep = {}, now = { 0L })
        out.stopped = true
        assertFalse(out.write(FloatArray(512), 256, 0))
    }

    @Test fun theLeadForCuesIsWhatIsWrittenAheadNow() {
        val ring = PcmRing(2, rate * 10)
        val clock = TvClock(rate)
        clock.started(1, 0, 0)
        val out = GaplessOut(ring, clock, rate, baseLeadMs = 1_500, name = "TV", sleep = {}, now = { 0L })
        assertEquals(1_500L, out.leadMs)
        // Before the TV starts it gets several seconds: cues are set that much further ahead.
        var at = 0L
        while (at < rate * 5L) { out.write(FloatArray(512), 256, at); at += 256 }
        assertTrue(out.leadMs in 5_000L..5_300L)
    }
}
