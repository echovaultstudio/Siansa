package app.siansa.playback

import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Beat-matched blends (issue #646): matching tempos, planning the blend on the bar, keeping in step. */
class BeatMatchTest {
    /** A steady 128 BPM outro with its last downbeat at 300 s (beats 468.75 ms apart, bars 1875 ms). */
    private val outro = BeatEnd(bpm = 128.0, beatMs = 300_000.0, barMs = 300_000.0, rhythm = 0.9f, steady = 0.9f)
    private val intro = BeatEnd(bpm = 126.0, beatMs = 1_000.0, barMs = 1_000.0, rhythm = 0.9f, steady = 0.9f)
    private val pad = BeatEnd(bpm = 120.0, beatMs = 500.0, barMs = null, rhythm = 0.1f, steady = 0.9f)
    private val song = Song(id = "a", title = "A", artist = "x", album = "y", albumId = "al1")

    @Test fun theNextSongSpeedsUpOrSlowsDownToMatchWithinEightPercent() {
        assertEquals(128.0 / 126.0, BeatMatch.speed(outro, intro)!!.toDouble(), 1e-5)
        assertEquals(1f, BeatMatch.speed(outro, intro.copy(bpm = 128.0)))
        // Double and half time count as the same tempo.
        assertEquals(128.0 / 127.0, BeatMatch.speed(outro, intro.copy(bpm = 254.0))!!.toDouble(), 1e-5)
        assertEquals(128.0 / 130.0, BeatMatch.speed(outro, intro.copy(bpm = 65.0))!!.toDouble(), 1e-5)
        assertNull(BeatMatch.speed(outro, intro.copy(bpm = 110.0)))  // 16 % apart
        assertNull(BeatMatch.speed(outro, intro.copy(bpm = 140.0)))
    }

    @Test fun onlySteadyBeatDrivenEndsCount() {
        assertTrue(BeatMatch.bothRhythmic(outro, intro))
        assertFalse(BeatMatch.bothRhythmic(outro, pad))
        assertFalse(BeatMatch.bothRhythmic(outro, intro.copy(steady = 0.2f)))
        assertFalse(BeatMatch.bothRhythmic(null, intro))
        assertNull(BeatMatch.plan(outro, pad, 310_000, 15_000))
    }

    @Test fun theBlendStartsOnABarAndCoversWholeBars() {
        // The song ends at 310 s; 8 bars (15 s) fit a 16 s setting: the blend starts on the last downbeat at or
        // before 310 - 15 = 295 s on the bar grid from 300 s (bars are 1.875 s): 294.375 s, 8 whole bars and a
        // part-bar, since 15.6 s still fits (issue #759; it used to start a bar later and lose one).
        val plan = BeatMatch.plan(outro, intro, endMs = 310_000, maxMs = 16_000)!!
        assertEquals(294_375L, plan.startMs)
        assertEquals(15_625L, plan.overlapMs)
        assertEquals(8, plan.bars)
        assertTrue(plan.onBars)
        assertEquals(1_000L, plan.incomingStartMs)  // the next song comes in on its first downbeat
        assertEquals(128.0 / 126.0, plan.speed.toDouble(), 1e-5)
        assertEquals("beat-matched 126.0 → 128.0 BPM (+1.6 %), 8 bars (15.6 s), bar on bar", plan.why)
    }

    @Test fun aShorterSettingTakesFewerBarsAndBeatsStandInForUnclearBars() {
        // Aimed at 4, 2 and 1 bars; when the downbeat before doesn't fit the setting, the start moves to the next
        // one, so up to one bar less is blended.
        assertTrue(BeatMatch.plan(outro, intro, 310_000, 8_000)!!.overlapMs in 7_500 - 1_875..7_500L)
        assertTrue(BeatMatch.plan(outro, intro, 310_000, 4_000)!!.overlapMs in 3_750 - 1_875..3_750L)
        assertTrue(BeatMatch.plan(outro, intro, 310_000, 2_000)!!.overlapMs in 469..1_875L)
        assertNull(BeatMatch.plan(outro, intro, 310_000, 1_000))  // not even one bar
        val beats = BeatMatch.plan(outro, intro.copy(barMs = null), 310_000, 16_000)!!
        assertFalse(beats.onBars)
        val onGrid = (beats.startMs - 300_000) / 468.75
        assertEquals(onGrid, Math.rint(onGrid), 0.01)  // on the beat grid, within a ms
        assertTrue(beats.overlapMs in 15_000..15_000 + 469)
        assertEquals(1_000L, beats.incomingStartMs)
        assertTrue(beats.why.endsWith("beat on beat"))
    }

    @Test fun whereTheNextSongShouldBeAndHowFarItIsOff() {
        val plan = BeatMatch.plan(outro, intro, 310_000, 16_000)!!
        assertEquals(plan.incomingStartMs, plan.incomingAt(plan.startMs))
        // 10 s into the blend at +1.6 % the next song is 10.159 s past its start.
        assertEquals(plan.incomingStartMs + 10_159, plan.incomingAt(plan.startMs + 10_000))
        assertEquals(40L, plan.error(plan.startMs + 10_000, plan.incomingStartMs + 10_199))
        assertEquals(-40L, plan.error(plan.startMs + 10_000, plan.incomingStartMs + 10_119))
    }

    @Test fun nudgesAreTinyAndEasingIsAStraightLineBack() {
        assertEquals(1f, BeatMatch.nudge(5))
        assertEquals(0.98f, BeatMatch.nudge(20), 1e-4f)  // ahead: slower
        assertEquals(1.02f, BeatMatch.nudge(-20), 1e-4f)
        // Never more than 3 %: 90 ms out of step is made up in about 3 s, which can't be heard (issue #759).
        assertEquals(0.97f, BeatMatch.nudge(90), 1e-4f)
        assertEquals(1.03f, BeatMatch.nudge(-200), 1e-4f)
        assertEquals(1.016f, BeatMatch.eased(1.016f, 0), 1e-5f)
        assertEquals(1.008f, BeatMatch.eased(1.016f, BeatMatch.EASE_MS / 2), 1e-5f)
        assertEquals(1f, BeatMatch.eased(1.016f, BeatMatch.EASE_MS + 1), 1e-5f)
    }

    @Test fun theSettingSaysWhereItApplies() {
        assertTrue(BeatMatchMode.DJ_RADIO.appliesTo(djRadio = true))
        assertFalse(BeatMatchMode.DJ_RADIO.appliesTo(djRadio = false))
        assertTrue(BeatMatchMode.EVERYWHERE.appliesTo(djRadio = false))
        assertFalse(BeatMatchMode.OFF.appliesTo(djRadio = true))
        assertEquals(BeatMatchMode.DJ_RADIO, BeatMatchMode.parse(null))
    }

    // ---- In a crossfade plan ----

    private fun profile(id: String, outro: BeatEnd?, intro: BeatEnd?, quietTailMs: Long = 0L, headMs: Long = 0L) =
        TrackProfile(id, -10.0, listOf(RampPoint(-18, headMs)), listOf(RampPoint(-18, quietTailMs)), 310_000, intro = intro, outro = outro)

    @Test fun aSmartFadeIsBeatMatchedWhenAskedAndBothEndsKeepABeat() {
        val next = song.copy(id = "b", albumId = "al2")
        val plan = Crossfade.plan(CrossfadeMode.SMART, 16_000, song, next, 310_000, 300_000,
            profile("a", outro, null), profile("b", null, intro), beatMatch = true)
        assertNotNull(plan)
        assertNotNull(plan!!.beat)
        assertEquals(plan.beat!!.overlapMs, plan.overlapMs)
        assertEquals(plan.overlapMs, plan.fadeInMs)
        assertTrue(plan.why, plan.why.startsWith("beat-matched"))
        // A hard ending into a quick start, which smart mode alone plays back to back, still gets the DJ blend.
        assertNull(Crossfade.plan(CrossfadeMode.SMART, 16_000, song, next, 310_000, 300_000, profile("a", outro, null), profile("b", null, intro)))
    }

    @Test fun aBlendHasItsOwnLengthInBarsApartFromTheCrossfadeLength() {
        val next = song.copy(id = "b", albumId = "al2")
        val bar = outro.beatLengthMs * BeatMatch.BEATS_PER_BAR
        // A 6 s crossfade length doesn't shorten an 8-bar blend (issue #759), and 4 or 16 bars give those.
        for (bars in listOf(4, 8, 16)) {
            val plan = Crossfade.plan(CrossfadeMode.SMART, 6_000, song, next, 310_000, 300_000,
                profile("a", outro, null), profile("b", null, intro), beatMatch = true, blendBars = bars)!!
            assertEquals("$bars bars", bars, plan.beat!!.bars)
            assertTrue(plan.why, plan.overlapMs in (bars * bar).toLong()..(bars * bar + bar).toLong())
        }
        assertTrue(BeatMatch.maxMsFor(outro, 8) in (8 * bar + bar - outro.beatLengthMs).toLong()..(9 * bar).toLong())
        // An ordinary fade still keeps to the crossfade length.
        val plain = Crossfade.plan(CrossfadeMode.SMART, 6_000, song, next, 310_000, 300_000,
            profile("a", null, null, quietTailMs = 6_000), profile("b", null, null, headMs = 8_000), beatMatch = true, blendBars = 16)!!
        assertNull(plain.beat)
        assertTrue(plain.why, plain.overlapMs <= 6_000L)
    }

    @Test fun twoBeatsThatCantBeMatchedGetAShortBlendNotALongClash() {
        val next = song.copy(id = "b", albumId = "al2")
        val far = intro.copy(bpm = 110.0)
        val a = profile("a", outro, null, quietTailMs = 6_000)
        val b = profile("b", null, far, headMs = 2_000)
        val plan = Crossfade.plan(CrossfadeMode.SMART, 16_000, song, next, 310_000, 300_000, a, b, beatMatch = true)!!
        assertNull(plan.beat)
        assertEquals(BeatMatch.CLASH_MS, plan.overlapMs)
        assertTrue(plan.why, plan.why.contains("beats would clash"))
        // The same without beat-matching on: the beats would clash just the same, so the short blend too.
        assertEquals(BeatMatch.CLASH_MS, Crossfade.plan(CrossfadeMode.SMART, 16_000, song, next, 310_000, 300_000, a, b)!!.overlapMs)
        // A beatless end keeps the smart blend as it was.
        val calm = Crossfade.plan(CrossfadeMode.SMART, 16_000, song, next, 310_000, 300_000, a, profile("b", null, pad, headMs = 2_000))!!
        assertEquals(8_000L, calm.overlapMs)
        assertFalse(calm.why.contains("clash"))
        assertTrue(Crossfade.whyNoFade(CrossfadeMode.SMART, 16_000, song, next, profile("a", outro, null), profile("b", null, far), beatMatch = true)!!.contains("too far apart"))
    }

    @Test fun theProfileAnswerCarriesTheGrids() {
        val profiles = PluginProfiles.parse(
            """{"schema_version":1,"profiles":[{"track_id":"a","ref_lufs":-9.5,"start_ramp":"","end_ramp":"","duration_ms":310000,""" +
                """"intro_bpm":126.0,"intro_beat_ms":1000.0,"intro_bar_ms":1000.0,"intro_rhythm":0.9,"intro_steady":0.88,""" +
                """"outro_bpm":128.0,"outro_beat_ms":300000.0,"outro_rhythm":0.7,"outro_steady":0.9},""" +
                """{"track_id":"b","ref_lufs":-12,"start_ramp":"","end_ramp":"","duration_ms":200000}],"pending":[]}""",
        )
        val a = profiles.first { it.trackId == "a" }
        assertEquals(126.0, a.intro!!.bpm, 1e-9)
        assertEquals(1_000.0, a.intro!!.barMs!!, 1e-9)
        assertEquals(128.0, a.outro!!.bpm, 1e-9)
        assertNull(a.outro!!.barMs)
        assertTrue(a.outro!!.matchable)
        val b = profiles.first { it.trackId == "b" }
        assertNull(b.intro)
        assertNull(b.outro)
    }

    @Test fun theLogSaysWhatWasMeasuredAndWhyABlendWasntBeatMatched() {
        // Issue #744: a report sent after a blend that sounded off shows which case it was.
        assertEquals("128.0 BPM, beat 0.90, steady 0.90, bars found, no phrases", outro.describe())
        assertEquals("128.0 BPM, beat 0.90, steady 0.90, 8-bar phrases found", outro.copy(phraseMs = 300_000.0, phraseBars = 8).describe())
        assertEquals("120.0 BPM, beat 0.10, steady 0.90, no bars found", pad.describe())
        val matched = BeatMatch.note(outro, intro, applies = true)
        assertEquals("out: 128.0 BPM, beat 0.90, steady 0.90, bars found, no phrases · in: 126.0 BPM, beat 0.90, steady 0.90, bars found, no phrases", matched)
        assertTrue(BeatMatch.note(null, intro, true).endsWith("not beat-matched: the plugin hasn't measured the beat at the end of the song going out"))
        assertTrue(BeatMatch.note(outro, null, true).endsWith("not beat-matched: the plugin hasn't measured the beat at the start of the next song"))
        // The pad is steady but has no bars: steady alone isn't enough for a quiet intro to be laid on the beat.
        assertTrue(BeatMatch.note(outro, pad, true).endsWith("not beat-matched: the start of the next song isn't beat-driven and steady enough (steady, but its bars aren't clear)"))
        assertTrue(BeatMatch.note(pad, intro, true).endsWith("not beat-matched: the end of the song going out isn't beat-driven and steady enough"))
        assertTrue(BeatMatch.note(outro, intro.copy(bpm = 110.0), true).endsWith("not beat-matched: 128.0 and 110.0 BPM are too far apart"))
        assertTrue(BeatMatch.note(outro, intro, applies = false).endsWith("not beat-matched: beat-matching is set for DJ radio only"))
    }

    @Test fun aSmartPlanCarriesTheBeatNote() {
        val next = song.copy(id = "b", albumId = "al2")
        // A few seconds of quiet tail and intro, so the blend that isn't beat-matched still has a length.
        val a = profile("a", outro, null, quietTailMs = 3_000)
        val b = profile("b", null, intro, headMs = 2_000)
        val matched = Crossfade.plan(CrossfadeMode.SMART, 16_000, song, next, 310_000, 300_000, a, b, beatMatch = true)!!
        assertNotNull(matched.beat)
        assertFalse(matched.beatNote!!, matched.beatNote!!.contains("not beat-matched"))
        val elsewhere = Crossfade.plan(CrossfadeMode.SMART, 16_000, song, next, 310_000, 300_000, a, b, beatMatch = false)!!
        assertTrue(elsewhere.beatNote!!, elsewhere.beatNote!!.endsWith("beat-matching is set for DJ radio only"))
        // Fixed crossfades don't look at beats at all.
        assertNull(Crossfade.plan(CrossfadeMode.FIXED, 16_000, song, next, 310_000, 300_000, a, b)!!.beatNote)
    }

    @Test fun oneReadingOffDoesntMoveTheSong() {
        // The position arrives in pieces: one reading 90 ms off, the next back in step. The middle of the last
        // five decides, so a lone reading can't start a jump (issue #759).
        val s = BeatMatch.SmoothedError()
        assertNull(s.add(4)); assertNull(s.add(-6)); assertNull(s.add(91)); assertNull(s.add(2))
        assertEquals(2L, s.add(-3))
        assertEquals(2L, s.add(95))
        assertEquals(91L, s.add(93))   // three of the last five say it really is off
    }

    @Test fun wholeBarsForgiveATenthOfABeat() {
        assertEquals(4, BeatMatch.wholeBars(7_679.0, 1_920.0))  // 125 BPM: 4 bars, not 3.99
        assertEquals(3, BeatMatch.wholeBars(7_600.0, 1_920.0))
    }

    @Test fun aQuietSteadyIntroWithClearBarsCanBeLaidOnTheBeat() {
        val quiet = intro.copy(rhythm = 0.1f, steady = 0.8f)
        assertTrue(quiet.matchableAsIntro)
        assertFalse(quiet.copy(barMs = null).matchableAsIntro)
        assertFalse(quiet.copy(steady = 0.3f).matchableAsIntro)
        assertNotNull(BeatMatch.plan(outro, quiet, 310_000, 16_000))
        // Not a clash, though: without beat-matching, a drum outro into a quiet build-up keeps the long smart blend.
        assertFalse(BeatMatch.bothRhythmic(outro, quiet))
        val next = song.copy(id = "b", albumId = "al2")
        val plain = Crossfade.plan(CrossfadeMode.SMART, 16_000, song, next, 310_000, 300_000, profile("a", outro, null, quietTailMs = 6_000), profile("b", null, quiet, headMs = 8_000), beatMatch = false)!!
        assertEquals(14_000L, plain.overlapMs)
        assertNotNull(Crossfade.plan(CrossfadeMode.SMART, 16_000, song, next, 310_000, 300_000, profile("a", outro, null, quietTailMs = 6_000), profile("b", null, quiet, headMs = 8_000), beatMatch = true)!!.beat)
        assertTrue(BeatMatch.note(outro, quiet.copy(barMs = null), true).endsWith("(steady, but its bars aren't clear)"))
        assertTrue(BeatMatch.note(outro, quiet.copy(steady = 0.3f), true).endsWith("isn't beat-driven and steady enough"))
    }

    @Test fun keepingInStepIsMeasured() {
        val stats = BeatMatch.StepStats()
        assertEquals("beats not checked", stats.describe())
        listOf(4L, -8L, 12L, -20L).forEach(stats::add)
        assertEquals("beats typically 11 ms apart, at worst 20 ms (4 checks)", stats.describe())
    }

    // ---- Phrase on phrase (issue #757) ----

    /** The outro's last phrase starts at 300 s (8 bars of 1.875 s: 15 s); the intro's first phrase at 1.875 s. */
    private val phrasedOutro = outro.copy(phraseMs = 300_000.0, phraseBars = 8)
    private val phrasedIntro = intro.copy(phraseMs = 1_875.0, phraseBars = 8)

    @Test fun theNextSongsFirstPhraseStartsOnAPhraseOfTheSongGoingOut() {
        // The song ends 5 s into its last phrase: that part-phrase is left out, and one whole phrase is blended.
        val plan = BeatMatch.plan(phrasedOutro, phrasedIntro, endMs = 305_000, maxMs = 16_000)!!
        assertEquals(8, plan.phraseBars)
        assertEquals(285_000L, plan.startMs)
        assertEquals(15_000L, plan.overlapMs)
        assertEquals(5_000L, plan.cutMs)
        assertEquals(1_875L, plan.incomingStartMs)
        assertEquals(8, plan.bars)
        assertEquals("beat-matched 126.0 → 128.0 BPM (+1.6 %), 8 bars (15.0 s), phrase on phrase (8-bar phrases), let go at the end of its last phrase (5.0 s left out)", plan.why)
    }

    @Test fun aLongerLastPartPhraseIsPlayedToTheEnd() {
        // 10 s into the last phrase (more than half of it): the blend starts on that phrase and runs to the end.
        val plan = BeatMatch.plan(phrasedOutro, phrasedIntro, endMs = 310_000, maxMs = 16_000)!!
        assertEquals(300_000L, plan.startMs)
        assertEquals(10_000L, plan.overlapMs)
        assertEquals(0L, plan.cutMs)
        // A longer setting takes a whole phrase before it too.
        val longer = BeatMatch.plan(phrasedOutro, phrasedIntro, endMs = 310_000, maxMs = 30_000)!!
        assertEquals(285_000L, longer.startMs)
        assertEquals(25_000L, longer.overlapMs)
    }

    @Test fun aShortSettingBlendsHalfAPhraseAndALongOneTwo() {
        assertEquals(292_500L, BeatMatch.plan(phrasedOutro, phrasedIntro, 305_000, 8_000)!!.startMs)
        assertEquals(7_500L, BeatMatch.plan(phrasedOutro, phrasedIntro, 305_000, 8_000)!!.overlapMs)
        val two = BeatMatch.plan(phrasedOutro, phrasedIntro, 305_000, 32_000)!!
        assertEquals(270_000L, two.startMs)
        assertEquals(30_000L, two.overlapMs)
        // Not even half a phrase: back to bar on bar.
        assertNull(BeatMatch.plan(phrasedOutro, phrasedIntro, 305_000, 4_000)!!.phraseBars)
    }

    @Test fun withoutPhrasesAtBothEndsItsBarOnBar() {
        assertNull(BeatMatch.plan(phrasedOutro, intro, 310_000, 16_000)!!.phraseBars)
        assertNull(BeatMatch.plan(outro, phrasedIntro, 310_000, 16_000)!!.phraseBars)
        assertEquals(294_375L, BeatMatch.plan(outro, phrasedIntro, 310_000, 16_000)!!.startMs)  // the bar plan: 8 whole bars and a part-bar
    }

    @Test fun aSmartPlanLetsTheSongGoAtTheEndOfItsLastPhrase() {
        val next = song.copy(id = "b", albumId = "al2")
        val a = profile("a", phrasedOutro, null, quietTailMs = 3_000)
        val b = profile("b", null, phrasedIntro, headMs = 2_000)
        val plan = Crossfade.plan(CrossfadeMode.SMART, 16_000, song, next, 305_000, 300_000, a, b, beatMatch = true)!!
        assertEquals(5_000L, plan.cutMs)
        assertEquals(15_000L, plan.overlapMs)
        assertTrue(plan.why, plan.why.contains("phrase on phrase"))
        // Into a short song the blend is held to a third of it and isn't beat-matched: the part-phrase isn't left
        // out then, as it would only lose the ending.
        val short = Crossfade.plan(CrossfadeMode.SMART, 16_000, song, next, 305_000, 40_000, a, b, beatMatch = true)!!
        assertNull(short.beat)
        assertEquals(0L, short.cutMs)
        assertEquals(13_333L, short.overlapMs)
        assertFalse(short.why, short.why.contains("phrase"))
    }

    @Test fun mixedPhraseLengthsUseTheLongerOne() {
        // 4-bar phrases going out, 8-bar coming in: the blend is a whole 8-bar unit, so both end together.
        val fours = outro.copy(phraseMs = 300_000.0, phraseBars = 4)
        val plan = BeatMatch.plan(fours, phrasedIntro, 305_000, 16_000)!!
        assertEquals(8, plan.phraseBars)
        assertEquals(285_000L, plan.startMs)
    }

    @Test fun aJumpAllowsForHowLongJumpingTakesOnThisPhone() {
        // Issue #747: sent 0 ms ahead, the next song settled 185 ms behind: getting there took 185 ms. Averaged with
        // the 150 ms Siansa starts from, the next jump is sent 167 ms ahead.
        assertEquals(167L, BeatMatch.learnedLag(BeatMatch.DEFAULT_SEEK_LAG_MS, leadMs = 0, errorMs = -185))
        // Sent 167 ms ahead and landed 10 ms ahead: it took 157 ms.
        assertEquals(162L, BeatMatch.learnedLag(167, leadMs = 167, errorMs = 10))
        // A stall (the song buffering for seconds) isn't learned as the phone's usual delay: what was known stays.
        assertEquals(167L, BeatMatch.learnedLag(167, leadMs = 150, errorMs = -2_000))
        assertEquals(167L, BeatMatch.learnedLag(167, leadMs = 0, errorMs = -(BeatMatch.MAX_SEEK_LAG_MS + 1)))
        // A jump that landed early can't make the delay negative.
        assertEquals(0L, BeatMatch.learnedLag(0, leadMs = 0, errorMs = 300))
    }
}
