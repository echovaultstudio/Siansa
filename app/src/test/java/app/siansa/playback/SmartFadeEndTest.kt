package app.siansa.playback

import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smart fades leave out the dead air and the end of a long fade-out (issue #628), but never because of anything
 * mid-song, never a soft ending that holds at one level, and never before three quarters of the song.
 */
class SmartFadeEndTest {
    private val a = Song("a", "Outgoing", albumId = "A", track = 3)
    private val b = Song("b", "Incoming", albumId = "B", track = 1)
    private val minutes4 = 240_000L

    /** An end ramp as the plugin measures it: for each level, how long before the end the song was last that loud. */
    private fun ending(vararg lastAt: Pair<Int, Long>) = TrackProfile("a", -10.0, listOf(RampPoint(-18, 0)), lastAt.map { RampPoint(it.first, it.second) })
    private val quickStart = TrackProfile("b", -10.0, listOf(RampPoint(-18, 500)), emptyList())

    // A hard ending followed by 15 s of silence: the song is loud until 15 s before the file ends.
    private val silentEnd = ending(-60 to 14_900, -48 to 15_000, -36 to 15_000, -24 to 15_000, -18 to 15_100, 0 to 15_200)
    // A 25 s fade-out: steadily quieter, −18 at 25 s before the end, −24 at 18 s, −36 at 9 s, near-silent at 2 s.
    private val fadeOut = ending(-60 to 1_000, -48 to 2_000, -36 to 9_000, -24 to 18_000, -18 to 25_000, 0 to 30_000)
    // A soft coda: drops to −30 dB 40 s before the end and stays there; not a fade-out.
    private val softCoda = ending(-60 to 200, -48 to 300, -36 to 400, -24 to 40_000, -18 to 40_000, 0 to 45_000)
    // A silence in the middle, then the song carries on loud to its end: the ramp only sees the end.
    private val loudToTheEnd = ending(-60 to 0, -48 to 0, -36 to 100, -24 to 200, -18 to 300, 0 to 1_000)

    @Test fun silenceAtTheEndIsLeftOutAndTheNextSongComesStraightIn() {
        val plan = Crossfade.plan(CrossfadeMode.SMART, 8_000, a, b, minutes4, minutes4, silentEnd, quickStart)!!
        assertEquals(15_000L, plan.cutMs)
        assertTrue("a short blend from the hard ending", plan.overlapMs in Crossfade.MIN_OVERLAP_MS..1_000L)
        assertTrue(plan.why, plan.why.contains("15.0 s of silence"))
    }

    @Test fun aLongFadeOutIsCutOnceItsQuietThenBlended() {
        val plan = Crossfade.plan(CrossfadeMode.SMART, 8_000, a, b, minutes4, minutes4, fadeOut, quickStart)!!
        // 2 s of near-silence plus the fade below −24 dB, at most the 8 s setting: 2 + 8 = 10 s left out.
        assertEquals(10_000L, plan.cutMs)
        assertTrue(plan.overlapMs <= 8_000L)
        assertTrue(plan.why, plan.why.contains("of fade-out"))
    }

    @Test fun aShortFadeOutIsCutWhereItsQuiet() {
        val shortFade = ending(-60 to 300, -48 to 500, -36 to 2_500, -24 to 5_000, -18 to 8_000, 0 to 10_000)
        val plan = Crossfade.plan(CrossfadeMode.SMART, 12_000, a, b, minutes4, minutes4, shortFade, quickStart)!!
        assertEquals(5_000L, plan.cutMs)  // to the −24 dB point
        assertEquals(3_000L + 500L, plan.overlapMs)  // −18 to −24 dB of the fade, plus the intro
    }

    @Test fun aSoftEndingThatHoldsAtOneLevelIsPlayed() {
        val (cut, _) = Crossfade.endCut(softCoda, minutes4, 8_000)
        assertEquals(0L, cut)
    }

    @Test fun anythingMidSongIsIgnoredBecauseTheEndIsLoud() {
        assertEquals(0L, Crossfade.endCut(loudToTheEnd, minutes4, 8_000).first)
    }

    @Test fun neverIntoTheFirstThreeQuarters() {
        // A 40 s song with 30 s of silence at the end: at most a quarter (10 s) is left out.
        val long = ending(-60 to 29_900, -48 to 30_000, -36 to 30_000, -24 to 30_000, -18 to 30_000, 0 to 30_100)
        val (cut, why) = Crossfade.endCut(long, 40_000, 8_000)
        assertEquals(10_000L, cut)
        assertTrue(why!!, why.contains("quarter"))
    }

    @Test fun theUsualGapBetweenSongsIsLeftAlone() {
        val gap = ending(-60 to 400, -48 to 600, -36 to 700, -24 to 800, -18 to 900, 0 to 2_000)
        assertEquals(0L, Crossfade.endCut(gap, minutes4, 8_000).first)
    }

    @Test fun onlySmartModeLeavesAnythingOut() {
        assertEquals(0L, Crossfade.plan(CrossfadeMode.FIXED, 8_000, a, b, minutes4, minutes4, silentEnd, quickStart)!!.cutMs)
    }

    @Test fun anIncompleteRampNeverLooksLikeSilence() {
        // Only the −18 level measured: no stand-in from a louder level.
        val sparse = TrackProfile("a", -10.0, emptyList(), listOf(RampPoint(-18, 20_000)))
        assertEquals(0L, Crossfade.endCut(sparse, minutes4, 8_000).first)
    }

    @Test fun withoutTheNextSongsProfileTheSilenceIsStillLeftOut() {
        val plan = Crossfade.plan(CrossfadeMode.SMART, 6_000, a, b, minutes4, minutes4, silentEnd, null)
        assertNotNull(plan)
        assertEquals(15_000L, plan!!.cutMs)
    }
}
