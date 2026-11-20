package app.siansa.playback

import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Smart picks how each DJ blend is made (the owner, 9 Nov), from Siansa Beats' readings of both ends. */
class TechniqueTest {
    // 128 BPM: a beat 468.75 ms, a bar 1875 ms. Both ends locked, sure of their beat.
    private val outro = BeatEnd(bpm = 128.0, beatMs = 300_000.0, barMs = 300_000.0, rhythm = 0.98f, steady = 0.9f, lockMs = 300_000.0)
    private val intro = BeatEnd(bpm = 127.0, beatMs = 1_000.0, barMs = 1_000.0, rhythm = 0.98f, steady = 0.9f, lockMs = 1_000.0)
    private val speed = BeatMatch.speed(outro, intro)!!

    private fun pick(
        out: BeatEnd = outro, inn: BeatEnd = intro, keys: Double? = 1.0, drops: List<Long> = emptyList(),
        mix: Boolean = false, noRoom: Boolean = false, last: Technique? = null, vocals: Int? = null, mixIn: Boolean = false,
    ) = TechniquePicker.pick(out, inn, BeatMatch.speed(out, inn) ?: speed, keys, drops, mix, mixIn, noRoom, last, vocals)

    /** Siansa Beats' vocals (0.60): a long blend only where no two voices meet; both singing to the change, a quick swap. */
    @Test fun theVocalsSteerTheBlend() {
        assertEquals(Technique.LONG_BLEND, pick(inn = intro.copy(bassBars = 16), vocals = 40).technique)
        assertEquals(Technique.BASS_SWAP, pick(inn = intro.copy(bassBars = 16), vocals = 12).technique)
        val quick = pick(vocals = 2)
        assertEquals(Technique.QUICK_SWAP, quick.technique)
        assertEquals("both sing right up to the change: the voices swap within 4 bars", quick.why)
        assertEquals(Technique.BASS_SWAP, pick(vocals = null).technique)  // not known: as before
        val sung = SongVocals(none = false, introFreeBars = 8, outroFreeBars = 6)
        assertEquals(14, TechniquePicker.vocalRoom(sung, sung))
        assertEquals(Int.MAX_VALUE, TechniquePicker.vocalRoom(sung, SongVocals(none = true)))
        assertNull(TechniquePicker.vocalRoom(null, sung))
        assertNull(TechniquePicker.vocalRoom(sung.copy(outroFreeBars = null), sung))
    }

    @Test fun theUsualIsTheBassSwapOverSixteenBars() {
        val usual = pick()
        assertEquals(Technique.BASS_SWAP, usual.technique)
        assertEquals(16, usual.bars)
    }

    @Test fun roomWithoutBassCloseTemposAndKeysThatMixRunLong() {
        val long = pick(inn = intro.copy(bassBars = 16))
        assertEquals(Technique.LONG_BLEND, long.technique)
        assertEquals(32, long.bars)
        // Not with the keys only half mixing, a tempo 4 % off, or a beat only 92 % sure.
        assertEquals(Technique.FILTER_FADE, pick(inn = intro.copy(bassBars = 16), keys = 0.5).technique)
        assertEquals(Technique.BASS_SWAP, pick(inn = intro.copy(bassBars = 16, bpm = 123.0)).technique)
        assertEquals(Technique.BASS_SWAP, pick(inn = intro.copy(bassBars = 16, rhythm = 0.92f)).technique)
    }

    @Test fun quickWhenTheKeysClashTheBeatIsUnsureOrThereIsNoRoom() {
        assertEquals(Technique.QUICK_SWAP, pick(keys = 0.1).technique)
        assertEquals(4, pick(keys = 0.1).bars)
        assertEquals(Technique.QUICK_SWAP, pick(inn = intro.copy(rhythm = 0.85f)).technique)
        assertEquals(Technique.QUICK_SWAP, pick(noRoom = true).technique)
        assertEquals("no room left for a longer blend", pick(noRoom = true).why)
        assertEquals(Technique.BASS_SWAP, pick().technique)
    }

    /**
     * The owner's sessions, 12 Nov: a jump into the last minute forced a 4-bar quick swap whatever room there was, so
     * every blend was one. Now the blend picked is kept: a long blend stays a long blend, a drop swap a drop swap.
     */
    @Test fun movingInTheSongKeepsTheBlendPicked() {
        assertEquals(Technique.LONG_BLEND, pick(inn = intro.copy(bassBars = 16)).technique)
        assertEquals(Technique.LONG_BLEND, pick(inn = intro.copy(bassBars = 16), last = Technique.BASS_SWAP).technique)
        // Only when no room is left is it cut to 4 bars.
        assertEquals(Technique.QUICK_SWAP, pick(inn = intro.copy(bassBars = 16), noRoom = true).technique)
    }

    @Test fun aMixTrackIsShortWhateverElse() {
        val mix = pick(inn = intro.copy(bassBars = 16), mix = true)
        assertEquals(Technique.BASS_SWAP, mix.technique)
        assertEquals(BeatMatch.MIX_SWAP_BARS, mix.bars)
        assertEquals("a track from a mixed CD: swapped at its very end", mix.why)
        val into = pick(inn = intro.copy(bassBars = 16), mixIn = true)
        assertEquals(BeatMatch.MIX_TRACK_BARS, into.bars)
    }

    @Test fun aDropSixteenBarsOrMoreInLandsWhereTheOldSongGoes() {
        val bar = 60_000.0 / 127 * 4
        val drop = (1_000 + 32 * bar).toLong()
        val choice = pick(drops = listOf(drop))
        assertEquals(Technique.DROP_SWAP, choice.technique)
        assertEquals(16, choice.bars)
        assertEquals(drop, choice.dropAtMs)
        assertEquals(8, pick(drops = listOf((1_000 + 12 * bar).toLong())).bars)  // only room for 8 before it
        assertEquals(Technique.BASS_SWAP, pick(drops = listOf((1_000 + 100 * bar).toLong())).technique)  // too far in
        // Not when the energy goes down: then a filter fade.
        assertEquals(Technique.FILTER_FADE, pick(out = outro.copy(energyDb = -1.0), inn = intro.copy(energyDb = -8.0), drops = listOf(drop)).technique)
    }

    @Test fun neverTheSameTwiceRunningWhenAnotherFits() {
        assertEquals(Technique.FILTER_FADE, pick(last = Technique.BASS_SWAP).technique)
        assertEquals(Technique.BASS_SWAP, pick(last = Technique.FILTER_FADE).technique)
        // A forced choice stays, whatever came before.
        assertEquals(Technique.QUICK_SWAP, pick(keys = 0.1, last = Technique.QUICK_SWAP).technique)
    }

    @Test fun keysComeFromAudioMuse() {
        val a = TrackProfile("a", -9.0, emptyList(), emptyList(), key = "A", scale = "minor")
        val c = TrackProfile("c", -9.0, emptyList(), emptyList(), key = "C", scale = "major")  // A minor's relative major
        assertEquals(0.85, TechniquePicker.keys(a, c)!!, 1e-9)
        assertNull(TechniquePicker.keys(a, TrackProfile("x", -9.0, emptyList(), emptyList())))
    }

    // ---- What the plan makes of it ----

    private val song = Song(id = "a", title = "A", artist = "x", album = "y", albumId = "al1")
    private val next = song.copy(id = "b", albumId = "al2")

    private fun profile(id: String, outro: BeatEnd?, intro: BeatEnd?, drops: List<Long> = emptyList()) =
        TrackProfile(id, -10.0, listOf(RampPoint(-18, 2_000)), listOf(RampPoint(-18, 3_000)), 310_000, intro = intro, outro = outro, dropsMs = drops)

    private fun plan(out: BeatEnd?, inn: BeatEnd?, drops: List<Long> = emptyList(), maxMs: Long = Blending.SMART_MAX_MS, last: Technique? = null, noRoom: Boolean = false) =
        Crossfade.plan(CrossfadeMode.SMART, maxMs, song, next, 310_000, 300_000, profile("a", out, null), profile("b", null, inn, drops),
            beatMatch = true, blendBars = Blending.SMART_BARS, noRoom = noRoom, lastTechnique = last)

    /** The plan after a jump: the long blend picked stays 32 bars; only with no room is it cut to a 4-bar quick swap. */
    @Test fun aJumpKeepsTheFullBlendAndOnlyNoRoomMakesItQuick() {
        val long = plan(outro, intro.copy(bassBars = 16))!!
        assertEquals(Technique.LONG_BLEND, long.technique)
        val quick = plan(outro, intro.copy(bassBars = 16), noRoom = true)!!
        assertEquals(Technique.QUICK_SWAP, quick.technique)
        assertEquals(4, quick.beat!!.bars)
        assertTrue(quick.why, quick.why.startsWith("smart: quick swap (no room left for a longer blend)"))
        assertTrue("the quick swap is far shorter", quick.overlapMs < long.overlapMs / 4)
    }

    @Test fun theBlendIsMadeAsPicked() {
        val usual = plan(outro, intro)!!
        assertEquals(Technique.BASS_SWAP, usual.technique)
        assertEquals(0f to 0.5f, usual.swapFrom to usual.swapTo)
        assertTrue(usual.why, usual.why.startsWith("smart: bass swap (the usual) · beat-matched"))
        val filter = plan(outro, intro, last = Technique.BASS_SWAP)!!
        assertEquals(Technique.FILTER_FADE, filter.technique)
        assertEquals(Crossfade.FILTER_FADE_HZ, filter.filterHz, 0.0)
        val long = plan(outro, intro.copy(bassBars = 16))!!
        assertEquals(Technique.LONG_BLEND, long.technique)
        assertTrue("${long.beat!!.bars}", long.beat!!.bars >= 28)
        assertEquals(0.375f to 0.625f, long.swapFrom to long.swapTo)
    }

    @Test fun aDropSwapStartsTheNextSongBeforeItsDrop() {
        val bar = 60_000.0 / 127 * 4
        val drop = (1_000 + 32 * bar).toLong()
        val swap = plan(outro, intro, drops = listOf(drop))!!
        assertEquals(Technique.DROP_SWAP, swap.technique)
        val beat = swap.beat!!
        // The next song comes in 16 of its bars before the drop, and the blend lasts those 16 bars.
        assertEquals(drop - 16 * bar, beat.incomingStartMs.toDouble(), 2.0)
        assertEquals(16, beat.bars)
        assertEquals(0.875f to 1f, swap.swapFrom to swap.swapTo)
    }

    @Test fun temposTooFarApartEchoOut() {
        val far = intro.copy(bpm = 174.0)
        val echo = plan(outro, far)!!
        assertEquals(Technique.ECHO_OUT, echo.technique)
        assertNull(echo.beat)
        assertEquals(outro.beatLengthMs, echo.echoBeatMs!!, 0.0)
        // No overlap: the old song's last whole bar (300 s to 301.875 s, where its lock ends), stopped dead on the bar
        // line; the next song drops in at once on its first downbeat (1 s), as the old one stops.
        assertEquals(1_875L, echo.overlapMs)
        assertTrue(echo.coldEnd)
        assertEquals(0L, echo.gapMs)
        assertEquals(310_000L - 301_875L, echo.cutMs)
        assertEquals(1_000L, echo.incomingFromMs)
        assertEquals(Crossfade.ECHO_DROP_IN_MS, echo.fadeInMs)
        // Without Siansa Beats' readings, as before: a short blend.
        val before = plan(outro.copy(lockMs = null), far.copy(lockMs = null))!!
        assertEquals(BeatMatch.CLASH_MS, before.overlapMs)
        assertNull(before.technique)
    }

    @Test fun fixedAlwaysSwapsTheBass() {
        val fixed = Crossfade.plan(CrossfadeMode.FIXED, 20_000, song, next, 310_000, 300_000, profile("a", outro, null),
            profile("b", null, intro.copy(bassBars = 16)), beatMatch = true, blendBars = 8)!!
        assertEquals(Technique.BASS_SWAP, fixed.technique)
        assertNotNull(fixed.beat)
        assertTrue(fixed.why, !fixed.why.startsWith("smart:"))
    }

    @Test fun theFilterFadeSweepsUpSlowlyThenFast() {
        assertEquals(0.0, DeckFilter.filterAt(0.0, 2_500.0), 0.0)
        assertEquals(625.0, DeckFilter.filterAt(0.5, 2_500.0), 1e-9)
        assertEquals(2_500.0, DeckFilter.filterAt(1.0, 2_500.0), 0.0)
        assertEquals(0.0, DeckFilter.filterAt(0.7, 0.0), 0.0)
    }

    // ---- After a quiet ending, the next song builds up (the owner, 9 Nov) ----

    @Test fun theFirstChangeIsTheEarliestOfPhraseBassAndDrop() {
        val bar = 60_000.0 / 127 * 4
        val withPhrase = TrackProfile("b", -9.0, emptyList(), emptyList(), intro = intro.copy(phraseMs = 1_000.0, phraseBars = 8))
        assertEquals((1_000 + 8 * bar).toLong(), Crossfade.firstChangeMs(withPhrase))  // its first phrase starts at once: the next one
        val bass = withPhrase.copy(intro = withPhrase.intro!!.copy(bassBars = 4))
        assertEquals((1_000 + 4 * bar).toLong(), Crossfade.firstChangeMs(bass))
        assertEquals(6_000L, Crossfade.firstChangeMs(bass.copy(dropsMs = listOf(6_000L))))
        assertNull(Crossfade.firstChangeMs(TrackProfile("x", -9.0, emptyList(), emptyList())))
        // Up to its first change, within the old song's quiet end and half a minute.
        assertEquals((1_000 + 4 * bar).toLong(), Crossfade.buildMs(bass, 20_000))
        assertEquals(5_000L, Crossfade.buildMs(bass, 5_000))
        assertEquals(12_000L, Crossfade.buildMs(null, 12_000))
        assertEquals(Crossfade.BUILD_MAX_MS, Crossfade.buildMs(null, 45_000))
        assertEquals(0.25f, Crossfade.buildIn(0.5f), 1e-6f)
    }

    @Test fun aWindingDownOutroHasTheNextSongBuildUpUnderIt() {
        // The beat stops at 3:40 of 4:00 and the outro winds down for its last 15 s.
        val outroEnd = TrackProfile("a", -10.0, emptyList(), listOf(RampPoint(-18, 15_000)), beatsUntilMs = 220_000)
        val next = TrackProfile("b", -10.0, emptyList(), emptyList(), intro = intro.copy(bassBars = 4))
        val plan = Crossfade.afterLastBeat(outroEnd, 240_000, 240_000, next)!!
        assertTrue(plan.buildUp)
        assertEquals(Crossfade.firstChangeMs(next)!!, plan.fadeInMs)
        assertTrue(plan.why, plan.why.contains("builds up") && plan.why.contains("to its first change"))
        // An outro that stays loud keeps its quick fade-in.
        val loud = TrackProfile("a", -10.0, emptyList(), listOf(RampPoint(-18, 500)), beatsUntilMs = 220_000)
        assertTrue(!Crossfade.afterLastBeat(loud, 240_000, 240_000, next)!!.buildUp)
    }
}
