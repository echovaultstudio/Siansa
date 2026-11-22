package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import app.siansa.data.Song
import org.junit.Test

/**
 * Tracks cut from a mixed CD (the owner, 20 Nov: "Giro (Exium remix)" into "System (Mixed)", a 15-bar blend over the
 * CDs' own blends): swapped at the very end, a bar or two, beat 1 on beat 1, and never a gap.
 */
class MixSwapTest {
    private val giroBpm = 134.0
    private val beat = 60_000.0 / giroBpm
    private val bar = 4 * beat

    /** Giro: 2:37, its last locked bar from 156.7 s (a downbeat of Siansa Beats' grid), its bars otherwise only assumed. */
    private val giro = BeatEnd(bpm = giroBpm, beatMs = 156_700.0, barMs = 156_700.0, rhythm = 1f, steady = 1f, lockMs = 156_700.0,
        phraseMs = 156_700.0 - 7 * bar, phraseBars = 8, assumed = true)
    private val system = BeatEnd(bpm = 136.2, beatMs = 0.0, barMs = 0.0, rhythm = 1f, steady = 1f, lockMs = 0.0, phraseMs = 0.0, phraseBars = 8)

    @Test fun theSwapIsInTheLastTwoBarsDownbeatOnDownbeat() {
        val end = 157_000L
        val p = BeatMatch.mixSwap(giro, system, end, outBarFound = false, inBarFound = true, incomingMix = true)!!
        // Starts on a downbeat of Giro's grid: a whole number of bars from its lock.
        val bars = (giro.lockMs!! - p.startMs) / bar
        assertEquals(Math.round(bars).toDouble(), bars, 0.01)
        // Two whole bars to the end, less than three.
        assertTrue("${p.overlapMs}", p.overlapMs >= 2 * bar - 1 && p.overlapMs < 3 * bar)
        assertEquals(2, p.bars)
        // The next song comes in on its first downbeat (its lock, after the CD's last record).
        assertEquals(0L, p.incomingStartMs)
        assertTrue(p.onBars)
        assertEquals("a mixed CD's track: swapped in its last 2 bars, downbeat on downbeat (the CD did the blending)", p.chosen)
        // No gap: the old song plays until the blend's end, where the next song is already in.
        assertEquals(end, p.startMs + p.overlapMs)
    }

    @Test fun theNextCdSliceStartsAtItsLockOnItsDownbeat() {
        val late = system.copy(beatMs = 300.0, barMs = 300.0, lockMs = 17_346.0)
        val p = BeatMatch.mixSwap(giro, late, 157_000L, outBarFound = false, inBarFound = true, incomingMix = true)!!
        assertEquals(17_346L, p.incomingStartMs)
        // A normal next song starts on the downbeat found.
        assertEquals(300L, BeatMatch.mixSwap(giro, late, 157_000L, outBarFound = false, inBarFound = true, incomingMix = false)!!.incomingStartMs)
    }

    @Test fun withoutADownbeatToTrustItsACutOnTheBeat() {
        // The Siansa plugin's readings: no lock, bars only assumed. Beat 3 could fall on beat 1, so no bars are laid
        // on each other: the old song's last beat, the next song on the very next one.
        val out = giro.copy(lockMs = null, steady = 0.9f)
        val inn = system.copy(lockMs = null, steady = 0.9f, barMs = null)
        val p = BeatMatch.mixSwap(out, inn, 157_000L, outBarFound = false, inBarFound = false, incomingMix = true)!!
        assertTrue("${p.overlapMs}", p.overlapMs >= beat * 0.9 && p.overlapMs < 2 * beat)
        assertEquals(0, p.bars)
        // On Giro's beat grid.
        val beats = (out.beatMs - p.startMs) / beat
        assertEquals(Math.round(beats).toDouble(), beats, 0.01)
        assertEquals(0L, p.incomingStartMs)
        assertEquals(157_000L, p.startMs + p.overlapMs)
        assertTrue(p.chosen!!, p.chosen!!.startsWith("a mixed CD's track: cut on the beat at its end"))
    }

    @Test fun aShortEndTakesOneBar() {
        // A bar and a half from the first downbeat to the end: two bars don't fit, one does.
        val early = giro.copy(beatMs = 1_000.0, barMs = 1_000.0, lockMs = 1_000.0)
        val p = BeatMatch.mixSwap(early, system, (1_000 + bar * 1.5).toLong(), outBarFound = true, inBarFound = true, incomingMix = false)!!
        assertEquals(1, p.bars)
        assertEquals(1_000L, p.startMs)
        assertTrue(p.chosen!!, p.chosen!!.contains("its last bar"))
    }

    @Test fun aMixedCdPairIsPlannedThatWay() {
        val song = TrackProfile("a", -10.0, listOf(RampPoint(-18, 2_000)), listOf(RampPoint(-18, 3_000)), 157_000, outro = giro, mixTrack = true)
        val next = TrackProfile("b", -10.0, listOf(RampPoint(-18, 2_000)), listOf(RampPoint(-18, 3_000)), 210_000, intro = system, mixTrack = true)
        val a = Song(id = "a", title = "Giro (Exium remix)", artist = "x", album = "Mixed CD 1", albumId = "cd1")
        val b = a.copy(id = "b", title = "System (Mixed)", album = "Mixed CD 2", albumId = "cd2")
        val plan = Crossfade.plan(CrossfadeMode.SMART, 30_000, a, b, 157_000, 210_000, song, next, beatMatch = true)!!
        assertNotNull(plan.beat)
        assertTrue(plan.why, plan.why.contains("a track from a mixed CD: swapped at its very end"))
        assertTrue("${plan.overlapMs}", plan.overlapMs < 3 * bar)
    }
}
