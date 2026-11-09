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
        mix: Boolean = false, sinceSeekMs: Long? = null, last: Technique? = null,
    ) = TechniquePicker.pick(out, inn, BeatMatch.speed(out, inn) ?: speed, keys, drops, mix, sinceSeekMs, last)

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

    @Test fun quickWhenTheKeysClashTheBeatIsUnsureOrTheListenerJustMoved() {
        assertEquals(Technique.QUICK_SWAP, pick(keys = 0.1).technique)
        assertEquals(4, pick(keys = 0.1).bars)
        assertEquals(Technique.QUICK_SWAP, pick(inn = intro.copy(rhythm = 0.85f)).technique)
        assertEquals(Technique.QUICK_SWAP, pick(sinceSeekMs = 5_000).technique)
        assertEquals(Technique.BASS_SWAP, pick(sinceSeekMs = 60_000).technique)
    }

    @Test fun aMixTrackIsShortWhateverElse() {
        val mix = pick(inn = intro.copy(bassBars = 16), mix = true)
        assertEquals(Technique.BASS_SWAP, mix.technique)
        assertEquals(BeatMatch.MIX_TRACK_BARS, mix.bars)
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

    private fun plan(out: BeatEnd?, inn: BeatEnd?, drops: List<Long> = emptyList(), maxMs: Long = Blending.SMART_MAX_MS, last: Technique? = null) =
        Crossfade.plan(CrossfadeMode.SMART, maxMs, song, next, 310_000, 300_000, profile("a", out, null), profile("b", null, inn, drops),
            beatMatch = true, blendBars = Blending.SMART_BARS, lastTechnique = last)

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
        assertTrue(echo.overlapMs in 1_875L..2_400L)
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
}
