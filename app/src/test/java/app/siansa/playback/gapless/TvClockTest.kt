package app.siansa.playback.gapless

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The TV as the clock of gapless casting (issue #114): where it plays, and how far ahead the phone writes. */
class TvClockTest {
    private val rate = 44_100
    private val s = 1_000_000_000L

    @Test fun untilTheTvSaysAnythingItIsHeardWhereItsStreamStarts() {
        val c = TvClock(rate)
        c.started(1, 10_000, 0)
        assertEquals(10_000.0, c.heard(5 * s), 0.0)
        assertFalse(c.hasReport())
    }

    @Test fun whilePlayingItMovesOnByThePhonesClockFromTheLastReport() {
        val c = TvClock(rate)
        c.started(1, 10_000, 0)
        assertTrue(c.report(1, 2.0, moving = true, nanos = 3 * s))
        assertEquals(10_000.0 + 2 * rate, c.heard(3 * s), 0.001)
        assertEquals(10_000.0 + 2.5 * rate, c.heard(3 * s + s / 2), 0.001)
    }

    @Test fun waitingOrPausedItStandsStill() {
        val c = TvClock(rate)
        c.started(1, 0, 0)
        c.report(1, 4.0, moving = false, nanos = s)
        assertEquals(4.0 * rate, c.heard(9 * s), 0.001)
    }

    @Test fun reportsOfAnOldStreamAreIgnored() {
        val c = TvClock(rate)
        c.started(2, 50_000, 0)
        assertFalse(c.report(1, 30.0, true, s))
        assertEquals(50_000.0, c.heard(s), 0.0)
        assertFalse(c.report(2, Double.NaN, true, s))
    }

    @Test fun aTvStandingStillGetsMoreDataSoNeitherWaitsForTheOther() {
        val c = TvClock(rate)
        c.started(1, 0, 0)
        // Not yet a second: as heard.
        assertEquals(0.0, c.paceHeard(s / 2), 0.0)
        // Then on by the clock, up to three seconds more.
        assertEquals(0.5 * rate, c.paceHeard(s + s / 2), 1.0)
        assertEquals(TvClock.STALL_EXTRA_MS / 1000.0 * rate, c.paceHeard(60 * s), 1.0)
        // Playing: as heard.
        c.report(1, 1.0, true, 60 * s)
        assertEquals(c.heard(61 * s), c.paceHeard(61 * s), 0.0)
    }

    @Test fun theMixerWritesUpToTheLeadAheadThenWaits() {
        val lead = rate * 3 / 2L
        assertEquals(0L, Pacer.waitMs(written = 1000, heard = 0.0, leadFrames = lead, rate = rate))
        assertEquals(0L, Pacer.waitMs(written = lead, heard = 0.0, leadFrames = lead, rate = rate))
        assertEquals(1L, Pacer.waitMs(written = lead + 10, heard = 0.0, leadFrames = lead, rate = rate))
        assertEquals(10L, Pacer.waitMs(written = lead + rate / 100, heard = 0.0, leadFrames = lead, rate = rate))
        // Never long: it looks again soon, to stop or to follow a skip.
        assertEquals(Pacer.MAX_WAIT_MS, Pacer.waitMs(written = lead * 10, heard = 0.0, leadFrames = lead, rate = rate))
    }
}
