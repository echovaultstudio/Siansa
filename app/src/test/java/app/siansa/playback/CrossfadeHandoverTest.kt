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
        // Issue #807, #813: the tail's position isn't real for seconds after it starts; nothing is judged until then.
        val sync = TailSync()
        sync.primed(now = 0)
        assertEquals(TailSync.Action.Wait, sync.step(now = 500, behindMs = 200, mainPositionMs = 100_500))
        assertEquals(TailSync.Action.Wait, sync.step(now = 2_500, behindMs = 200, mainPositionMs = 102_500))
        // Real, and five readings (one of them wild) say it's 80 ms behind: one jump, sent ahead by the phone's cost.
        listOf(78L, 300L, 82L, 80L).forEach { assertEquals(TailSync.Action.Wait, sync.step(3_000, it, 103_000)) }
        val jump = sync.step(3_200, 79, 103_200) as TailSync.Action.Jump
        assertEquals(103_200 + TailSync.DEFAULT_LEAD_MS, jump.targetMs)
        assertEquals(1, jump.jumps)
        // Then it settles again. The jump went 100 ahead and left it 30 behind: a jump costs 130 ms on this phone.
        assertEquals(TailSync.Action.Wait, sync.step(5_000, 30, 105_000))
        repeat(4) { sync.step(6_200, 30, 106_200) }
        val speed = sync.step(6_400, 30, 106_400) as TailSync.Action.Speed
        assertEquals(130L, sync.learnedLead)
        assertEquals(30L, speed.behindMs)
        // 30 ms is under the jump threshold: a nudge, 3 % faster for exactly the second that makes up 30 ms.
        assertEquals(1.03f, speed.factor, 0.0001f)
        assertEquals(1_000L, TailSync.nudgeFor(30))
        assertEquals(1.03f, (sync.step(7_000, 20, 107_000) as TailSync.Action.Speed).factor, 0.0001f)
        // The nudge over: back at the main player's speed, and judged again only once that has shown.
        assertEquals(TailSync.Action.Wait, sync.step(7_500, 5, 107_500))
        assertEquals(TailSync.Action.Wait, sync.step(10_000, 5, 110_000))
        repeat(4) { sync.step(10_500, 5, 110_500) }
        assertEquals(TailSync.Action.Speed(1f, 5), sync.step(10_600, 5, 110_600))
        assertEquals(1, sync.jumpsMade)
    }

    @Test fun aSecondJumpUsesWhatTheFirstTaught() {
        val sync = TailSync(lead = 60)
        sync.primed(0)
        repeat(4) { sync.step(3_000, 150, 103_000) }
        val first = sync.step(3_100, 150, 103_100) as TailSync.Action.Jump
        assertEquals(103_160L, first.targetMs)
        // Still 60 behind after settling: the jump cost 120, so the second one is sent 120 ahead.
        repeat(4) { sync.step(6_200, 60, 106_200) }
        val second = sync.step(6_300, 60, 106_300) as TailSync.Action.Jump
        assertEquals(106_420L, second.targetMs)
        assertEquals(2, second.jumps)
        // No third: from here a nudge does it, even for a big gap (200 ms takes 6.7 s at 3 %).
        repeat(4) { sync.step(9_400, 200, 109_400) }
        val speed = sync.step(9_500, 200, 109_500) as TailSync.Action.Speed
        assertEquals(1f + TailSync.NUDGE_RATE, speed.factor, 0.0001f)
        assertEquals(6_666L, TailSync.nudgeFor(200))
    }

    @Test fun aNudgeRunsSlowerWhenAhead() {
        val sync = TailSync()
        sync.primed(0)
        repeat(4) { sync.step(3_000, -30, 103_000) }
        val speed = sync.step(3_100, -30, 103_100) as TailSync.Action.Speed
        assertEquals(0.97f, speed.factor, 0.0001f)
        assertEquals(1_000L, TailSync.nudgeFor(-30))
        assertEquals(0L, TailSync.nudgeFor(0))
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
