package app.siansa.playback

import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's bad blends of 9 Nov: a blend never leans on half the data, an end has to sit on its grid, a DJ mix
 * never pauses, and a next song whose beat comes in late is neither echoed into nor beat-matched.
 */
class DjRulesTest {
    private val outro = BeatEnd(bpm = 123.0, beatMs = 368_000.0, barMs = 368_000.0, rhythm = 0.95f, steady = 0.9f)
    private val intro = BeatEnd(bpm = 122.0, beatMs = 199.0, barMs = 199.0, rhythm = 1f, steady = 1f, lockMs = 199.0)
    private val song = Song(id = "a", title = "Palermo", artist = "x", album = "y", albumId = "al1")
    private val next = song.copy(id = "b", title = "Paraiso 94", albumId = "al2")

    private fun profile(id: String, outro: BeatEnd?, intro: BeatEnd?, tailMs: Long = 2_000, headMs: Long = 3_000) =
        TrackProfile(id, -10.0, listOf(RampPoint(-18, tailMs)), listOf(RampPoint(-18, headMs)), 400_000, intro = intro, outro = outro)

    private fun plan(out: BeatEnd?, into: BeatEnd?, tailMs: Long = 2_000, headMs: Long = 3_000, dj: Boolean = true) =
        Crossfade.plan(CrossfadeMode.SMART, 30_000, song, next, 400_000, 375_000,
            profile("a", out, null, tailMs = tailMs), profile("b", null, into, headMs = headMs), beatMatch = dj)

    @Test fun aGridAtOneEndOnlyIsNeverBeatMatched() {
        // Palermo had no Siansa Beats grid (the plugin read 123 BPM; it's 125): 32 s against Paraiso 94's grid drifted.
        val p = plan(outro, intro)!!
        assertNull(p.beat)
        assertEquals(BeatMatch.CLASH_MS, p.overlapMs)
        assertTrue(p.beatNote, p.beatNote!!.contains("Siansa Beats has no grid for the song going out"))
        // With both grids, it is.
        assertNotNull(plan(outro.copy(bpm = 122.5, lockMs = 360_000.0), intro)!!.beat)
        // Without Siansa Beats at either end (the plugin alone), as before.
        assertNotNull(plan(outro.copy(bpm = 122.5), intro.copy(lockMs = null))!!.beat)
    }

    @Test fun aLockedEndHasToSitOnItsGrid() {
        // Nomansland's outro: 93 % of its beats heard, but only 74 % on its grid (it drifts): no swap on it.
        val loose = outro.copy(bpm = 142.4, rhythm = 0.93f, steady = 0.744f, lockMs = 310_000.0)
        assertFalse(loose.matchable)
        assertTrue(loose.copy(steady = 0.906f).matchable)
        assertTrue(loose.copy(lockMs = null).matchable)  // the plugin's own readings keep their old bar
    }

    @Test fun aDjMixNeverPauses() {
        // A hard ending into a hard start, the ends' beats unknown: a pause outside DJ playback, none in it.
        val cold = Crossfade.coldEnd(0, 0, 0, 125.0, 127.0, bothBeat = false, dj = true)!!
        assertEquals(0L, cold.gapMs)
        assertTrue(cold.why.contains("never pauses"))
        assertNotEquals(0L, Crossfade.coldEnd(0, 0, 0, 125.0, 127.0)!!.gapMs)
        val p = plan(null, null, tailMs = 0, headMs = 0)!!
        assertTrue(p.coldEnd)
        assertEquals(0L, p.gapMs)
    }

    @Test fun aNextSongWhoseBeatComesInLateIsntEchoedIntoOrBeatMatched() {
        // Eleuthera (110 BPM) into Snowflake (84, its beat in at 1:37): an echo out "dropped" an ambient intro.
        val eleuthera = BeatEnd(bpm = 110.0, beatMs = 233_678.0, barMs = 233_678.0, rhythm = 1f, steady = 0.9f, lockMs = 233_678.0)
        val snowflake = BeatEnd(bpm = 84.0, beatMs = 97_128.0, barMs = 97_128.0, rhythm = 0.86f, steady = 0.9f, lockMs = 97_128.0)
        val p = plan(eleuthera, snowflake)!!
        assertNull(p.beat)
        assertNotEquals(Technique.ECHO_OUT, p.technique)
        assertFalse(p.coldEnd)
        // It slides in under the old song for the whole crossfade length: nothing to clash with until its beat.
        assertEquals(30_000L, p.overlapMs)
        // Even at the same tempo it isn't beat-matched from 1:37, skipping its intro.
        assertNull(plan(eleuthera, snowflake.copy(bpm = 110.0))!!.beat)
    }

    @Test fun aLateBeatComingInBeforeTheBlendEndsCutsItShort() {
        val beat = BeatEnd(bpm = 124.0, beatMs = 368_000.0, barMs = 368_000.0, rhythm = 1f, steady = 0.95f, lockMs = 368_000.0)
        val late = BeatEnd(bpm = 90.0, beatMs = 20_000.0, barMs = 20_000.0, rhythm = 0.9f, steady = 0.9f, lockMs = 20_000.0)
        val p = plan(beat, late)!!
        assertNull(p.beat)
        // Gone a bar (2.7 s at 90 BPM) before the next song's beat comes in at 20 s.
        assertEquals((20_000L - 2_667L).toDouble(), p.overlapMs.toDouble(), 2.0)
        assertTrue(p.why, p.why.contains("gone a bar before"))
    }

    @Test fun aTrackOffAMixedCdStillBlendsInsideItsLock() {
        // Before its lock is the DJ's last record, not an ambient intro: matched inside it, as before.
        val mixIn = intro.copy(bpm = 123.0, beatMs = 17_346.0, barMs = 17_346.0, lockMs = 17_346.0)
        val out = outro.copy(lockMs = 360_000.0)
        val p = Crossfade.plan(CrossfadeMode.SMART, 30_000, song, next, 400_000, 375_000,
            profile("a", out, null), TrackProfile("b", -10.0, listOf(RampPoint(-18, 2_000)), listOf(RampPoint(-18, 3_000)), 375_000,
                intro = mixIn, mixTrack = true), beatMatch = true)!!
        assertNotNull(p.beat)
    }

    @Test fun aProfileWithoutSiansaBeatsIsAskedForAgainSooner() {
        val plain = TrackProfile("a", -10.0, emptyList(), emptyList(), 300_000, intro = intro.copy(lockMs = null))
        assertFalse(plain.fromBeats)
        assertEquals(ProfileStore.RETRY_MISSING_MS, ProfileStore.refreshAfter(plain))
        assertTrue(plain.copy(intro = intro).fromBeats)
        assertEquals(ProfileStore.REFRESH_MS, ProfileStore.refreshAfter(plain.copy(intro = intro)))
    }
}
