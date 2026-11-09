package app.siansa.playback

import app.siansa.data.AppSettings
import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's rework of 9 Nov (Siansa Dev): Smart decides every change, Fixed takes the listener's length and bars,
 * beat-matched blends only in DJ playback, and Siansa Beats' lock: a blend stays inside where a song's beat locks
 * onto its grid, with a stricter confidence bar, so a mix-CD track no longer trainwrecks.
 */
class SmartBlendTest {
    private val outro = BeatEnd(bpm = 128.0, beatMs = 300_000.0, barMs = 300_000.0, rhythm = 0.95f, steady = 0.9f)
    private val intro = BeatEnd(bpm = 126.0, beatMs = 1_000.0, barMs = 1_000.0, rhythm = 0.95f, steady = 0.9f)
    private val song = Song(id = "a", title = "A", artist = "x", album = "y", albumId = "al1")
    private val next = song.copy(id = "b", albumId = "al2")

    private fun profile(id: String, outro: BeatEnd?, intro: BeatEnd?, mix: Boolean = false) =
        TrackProfile(id, -10.0, listOf(RampPoint(-18, 2_000)), listOf(RampPoint(-18, 3_000)), 310_000, intro = intro, outro = outro, mixTrack = mix)

    @Test fun smartHasNothingToSetAndFixedTakesTheListenersChoices() {
        val s = AppSettings(crossfade = CrossfadeMode.SMART, crossfadeSeconds = 6, blendBars = 4)
        assertEquals(Blending(Blending.SMART_MAX_MS, Blending.SMART_BARS, bassSwap = true, echoTail = s.echoTail, keepKey = s.keepKey), Blending.of(s))
        val fixed = Blending.of(s.copy(crossfade = CrossfadeMode.FIXED))
        assertEquals(6_000L, fixed.maxMs)
        assertEquals(4, fixed.blendBars)
        assertTrue(fixed.bassSwap)  // always, with today's bass swap
    }

    @Test fun beatMatchedOnlyInDjPlayback() {
        val smart = AppSettings(crossfade = CrossfadeMode.SMART)
        assertTrue(Blending.beatMatched(smart, djPlayback = true))
        assertFalse(Blending.beatMatched(smart, djPlayback = false))
        assertTrue(Blending.beatMatched(smart.copy(crossfade = CrossfadeMode.FIXED), djPlayback = true))
        assertFalse(Blending.beatMatched(smart.copy(crossfade = CrossfadeMode.OFF), djPlayback = true))
    }

    @Test fun aLockedEndNeedsEightyPercentOfItsBeats() {
        // (R) Evolution's intro, inside its lock: 56 % of its beats on the grid. The Siansa plugin's readings (no lock)
        // keep the old bar.
        val evolution = intro.copy(bpm = 124.6, rhythm = 0.56f, steady = 0.82f, lockMs = 0.0)
        assertFalse(evolution.matchable)
        assertFalse(evolution.matchableAsIntro)  // a steady intro with bars no longer stands in, once the lock is known
        assertTrue(evolution.copy(lockMs = null).matchableAsIntro)
        assertTrue(evolution.copy(rhythm = 0.85f).matchable)
        val plan = Crossfade.plan(CrossfadeMode.SMART, 20_000, song, next, 310_000, 300_000,
            profile("a", outro, null), profile("b", null, evolution), beatMatch = true)!!
        assertNull(plan.beat)
    }

    @Test fun theSongGoingOutIsLetGoWhereItsLockEnds() {
        // The beat last locks on the bar from 290 s (ends 291.875 s); the DJ's next record plays after it.
        val locked = outro.copy(beatMs = 290_000.0, barMs = 290_000.0, lockMs = 290_000.0)
        val plan = Crossfade.plan(CrossfadeMode.SMART, 20_000, song, next, 310_000, 300_000,
            profile("a", locked, null), profile("b", null, intro), beatMatch = true)!!
        assertNotNull(plan.beat)
        val end = 310_000 - plan.cutMs
        assertEquals(291_875.0, end.toDouble(), 2.0)
        assertTrue(plan.why, plan.why.contains("where its beat stops locking"))
    }

    @Test fun aMixTrackIsBlendedInsideItsLockAndKeptShort() {
        val lockedOut = outro.copy(lockMs = 296_250.0)
        val lockedIn = intro.copy(lockMs = 1_000.0)
        val plan = Crossfade.plan(CrossfadeMode.SMART, 20_000, song, next, 310_000, 300_000,
            profile("a", lockedOut, null, mix = true), profile("b", null, lockedIn), beatMatch = true)!!
        assertNotNull(plan.beat)
        assertTrue("${plan.beat!!.bars}", plan.beat!!.bars <= BeatMatch.MIX_TRACK_BARS)
        // Without the lock (the Siansa plugin's readings), a mix track is never beat-matched: a short blend.
        val unlocked = Crossfade.plan(CrossfadeMode.SMART, 20_000, song, next, 310_000, 300_000,
            profile("a", outro, null, mix = true), profile("b", null, intro), beatMatch = true)!!
        assertNull(unlocked.beat)
        assertEquals(BeatMatch.CLASH_MS, unlocked.overlapMs)
    }

    @Test fun siansaBeatsFieldsAreRead() {
        val json = """{"profiles":[{"track_id":"t","ref_lufs":-9.0,"start_ramp":"","end_ramp":"","duration_ms":200000,
            "intro_bpm":124.6,"intro_beat_ms":12000,"intro_rhythm":0.9,"intro_steady":0.9,"intro_lock_ms":12000,"intro_bass_bars":8,"intro_energy":-6.5,
            "outro_bpm":124.6,"outro_beat_ms":190000,"outro_rhythm":0.9,"outro_steady":0.9,"outro_lock_ms":188000,
            "mix_track":true,"drops_ms":[60000.0,120000],"breakdowns_ms":[]}]}"""
        val p = PluginProfiles.parse(json).single()
        assertEquals(12_000.0, p.intro!!.lockMs!!, 0.0)
        assertEquals(8, p.intro!!.bassBars)
        assertEquals(-6.5, p.intro!!.energyDb!!, 0.0)
        assertEquals(188_000.0, p.outro!!.lockMs!!, 0.0)
        assertTrue(p.mixTrack)
        assertEquals(listOf(60_000L, 120_000L), p.dropsMs)
        assertEquals(emptyList<Long>(), p.breakdownsMs)
        // The Siansa plugin's profile has none of them.
        val plain = PluginProfiles.parse("""{"profiles":[{"track_id":"t","intro_bpm":120,"intro_beat_ms":0,"intro_rhythm":0.9,"intro_steady":0.9}]}""").single()
        assertNull(plain.intro!!.lockMs)
        assertFalse(plain.mixTrack)
    }

    @Test fun theBadBlendReportSaysHowItWasMade() {
        val plan = Crossfade.plan(CrossfadeMode.SMART, 20_000, song, next, 310_000, 300_000,
            profile("a", outro.copy(lockMs = 296_250.0), null), profile("b", null, intro.copy(lockMs = 1_000.0)), beatMatch = true)!!
        val report = LastBlend.of("a", "b", 294_000, plan, dj = true, now = 1_000)
        assertTrue(report.technique, report.technique in listOf("phrase on phrase", "bar on bar"))
        assertEquals(plan.beat!!.bars, report.bars)
        assertEquals(128.0, report.fromBpm!!, 0.0)
        assertEquals(0.95f, report.fromRhythm!!, 0f)
        val json = report.toJson().toString()
        assertTrue(json, json.contains("\"from_id\":\"a\"") && json.contains("\"to_lock_ms\":1000.0") && json.contains("\"at_ms\":294000"))
        assertEquals("fade", LastBlend.of("a", "b", 0, FadePlan(8_000, 8_000, "smart"), dj = true, now = 0).technique)
        assertEquals("short blend", LastBlend.of("a", "b", 0, FadePlan(1_500, 1_500, "smart"), dj = true, now = 0).technique)
    }

    @Test fun theButtonShowsOnlyAroundADjBlendInSiansaDev() {
        val report = LastBlend.of("a", "b", 0, FadePlan(10_000, 10_000, "smart"), dj = true, now = 0)
        // Unit tests build the debug type, not Siansa Dev: never shown there.
        assertFalse(LastBlend.showing(report, 5_000))
        assertFalse(LastBlend.enabled)
    }
}
