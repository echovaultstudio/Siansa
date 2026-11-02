package app.siansa.playback

import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The silent tail keeping in step (issues #355, #807), and why a change of song has no fade. */
class CrossfadeHandoverTest {
    private val a1 = Song("a1", "One", albumId = "A", track = 1, disc = 1)
    private val a2 = Song("a2", "Two", albumId = "A", track = 2, disc = 1)
    private val b1 = Song("b1", "Other", albumId = "B", track = 1)

    private fun profile(id: String, introMs: Int, tailMs: Int) =
        TrackProfile(id, -10.0, listOf(RampPoint(-18, introMs.toLong())), listOf(RampPoint(-18, tailMs.toLong())))

    @Test fun aTailStillLoadingGetsAMomentBeforeTheBlendIsCalledOff() {
        // Issue #767: the tail jumped to catch up 0.6 s before the blend and was still loading at its start.
        assertTrue(Crossfade.waitForTail(primed = true, loading = true, playing = false, untilMs = 0, stepMs = 0, waitMs = 428))
        assertTrue(Crossfade.waitForTail(true, true, false, -1_400, 0, 428))
        assertFalse(Crossfade.waitForTail(true, true, false, -Crossfade.LOADING_WAIT_MS, 0, 428))  // then on without
        // Playing but out of step: waits up to a beat, as before (issue #759).
        assertTrue(Crossfade.waitForTail(true, false, true, -100, 35, 428))
        assertFalse(Crossfade.waitForTail(true, false, true, -500, 35, 428))
        assertFalse(Crossfade.waitForTail(true, false, true, 0, 15, 428))  // in step: go
        // Not primed (a stream that can't play ahead): no wait.
        assertFalse(Crossfade.waitForTail(false, true, false, 0, 0, 428))
    }

    @Test fun theTailWaitsForItsPositionToBeRealThenJumpsOnce() {
        // Issue #807: the tail's position isn't real for most of a second after it starts; nothing is judged until then.
        val sync = TailSync()
        sync.primed(now = 0)
        assertEquals(TailSync.Action.Wait, sync.step(now = 500, behindMs = 200, mainPositionMs = 100_500))
        // Real, and five readings (one of them wild) say it's 80 ms behind: one jump, sent ahead by the phone's cost.
        listOf(78L, 300L, 82L, 80L).forEach { assertEquals(TailSync.Action.Wait, sync.step(800, it, 101_000)) }
        val jump = sync.step(1_000, 79, 101_000) as TailSync.Action.Jump
        assertEquals(101_000 + TailSync.DEFAULT_LEAD_MS, jump.targetMs)
        assertEquals(1, jump.jumps)
        // Then it settles again. The jump went 100 ahead and left it 30 behind: a jump costs 130 ms on this phone.
        assertEquals(TailSync.Action.Wait, sync.step(1_500, 30, 101_500))
        repeat(4) { sync.step(1_800, 30, 101_800) }
        val speed = sync.step(2_000, 30, 102_000) as TailSync.Action.Speed
        assertEquals(130L, sync.learnedLead)
        assertEquals(30L, speed.behindMs)
        // 30 ms is under the jump threshold: a slight nudge brings it in, never a quarter faster as before.
        assertEquals(1.01f, speed.factor, 0.0001f)
        assertEquals(1, sync.jumpsMade)
    }

    @Test fun aSecondJumpUsesWhatTheFirstTaught() {
        val sync = TailSync(lead = 60)
        sync.primed(0)
        repeat(4) { sync.step(900, 150, 100_900) }
        val first = sync.step(1_000, 150, 101_000) as TailSync.Action.Jump
        assertEquals(101_060L, first.targetMs)
        // Still 60 behind after settling: the jump cost 120, so the second one is sent 120 ahead.
        repeat(4) { sync.step(1_900, 60, 101_900) }
        val second = sync.step(2_000, 60, 102_000) as TailSync.Action.Jump
        assertEquals(102_120L, second.targetMs)
        assertEquals(2, second.jumps)
        // No third: from here the speed does it, even for a big gap.
        repeat(4) { sync.step(2_900, 200, 102_900) }
        val speed = sync.step(3_000, 200, 103_000) as TailSync.Action.Speed
        assertEquals(1f + TailSync.NUDGE_MAX, speed.factor, 0.0001f)
    }

    @Test fun theNudgeIsSlightAndGoesBothWays() {
        assertEquals(1f, TailSync.nudge(0), 0f)
        assertEquals(1f, TailSync.nudge(10), 0f)
        assertEquals(1f, TailSync.nudge(-10), 0f)
        assertEquals(1.01f, TailSync.nudge(30), 0.0001f)
        assertEquals(0.99f, TailSync.nudge(-30), 0.0001f)
        assertEquals(1.015f, TailSync.nudge(200), 0.0001f)
        assertEquals(0.985f, TailSync.nudge(-200), 0.0001f)
    }

    @Test fun theLogSaysWhyThereIsNoFade() {
        assertEquals("crossfade is off", Crossfade.whyNoFade(CrossfadeMode.OFF, 6_000, a1, b1, null, null))
        assertEquals("they run straight on in one album", Crossfade.whyNoFade(CrossfadeMode.SMART, 6_000, a1, a2, null, null))
        val why = Crossfade.whyNoFade(CrossfadeMode.SMART, 8_000, a1, b1, profile("a1", 0, 100), profile("b1", 100, 0))!!
        assertTrue(why, why.startsWith("a hard ending into a quick start (100 ms quiet tail + 100 ms intro)"))
        // A fade that does happen has no reason.
        assertNull(Crossfade.whyNoFade(CrossfadeMode.SMART, 8_000, a1, b1, profile("a1", 0, 3_000), profile("b1", 0, 0)))
    }
}
