package app.siansa.playback

import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The silent tail keeping in step, and why a change of song has no fade (issue #355). */
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

    @Test fun inStepPlaysAtNormalSpeed() {
        assertEquals(1f, Crossfade.tailSpeed(0), 0f)
        assertEquals(1f, Crossfade.tailSpeed(10), 0f)
        assertEquals(1f, Crossfade.tailSpeed(-10), 0f)
    }

    @Test fun behindCatchesUpAheadWaits() {
        // Silent, so it may hurry (issue #747): 84 ms behind is made up in under half a second.
        assertEquals(1.05f, Crossfade.tailSpeed(20), 0.0001f)
        assertEquals(0.95f, Crossfade.tailSpeed(-20), 0.0001f)
        assertEquals(1.21f, Crossfade.tailSpeed(84), 0.0001f)
        // Never more than a quarter off.
        assertEquals(1.25f, Crossfade.tailSpeed(200), 0.0001f)
        assertEquals(0.75f, Crossfade.tailSpeed(-200), 0.0001f)
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
