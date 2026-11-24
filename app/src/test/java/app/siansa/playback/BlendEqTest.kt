package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three-band EQ of a DJ blend and the vocal rule (the owner, 19 Nov: "2 vocal tracks can be mixed but not where both
 * vocals can be heard").
 */
class BlendEqTest {
    /** A voice heard: its song's mids within this of full, while it's still in the mix. */
    private val heardDb = -15.0

    private fun range(a: Float, b: Float) = a..b

    @Test fun withoutTwoVoicesTheMidsAndHighsCrossOverTheBlend() {
        val eq = BlendEq(16)
        assertEquals(DeckFilter.KILL_DB, eq.incomingMidDb(0f), 0.0)
        assertEquals(0.0, eq.incomingMidDb(0.5f), 0.0)
        assertEquals(0.0, eq.outgoingMidDb(0.5f), 0.0)
        assertEquals(DeckFilter.KILL_DB, eq.outgoingMidDb(1f), 0.0)
        assertEquals(BlendEq.HIGH_DIP, eq.incomingHighDb(0f), 0.0)
        assertEquals(0.0, eq.incomingHighDb(0.25f), 0.0)
        assertEquals(0.0, eq.outgoingHighDb(0.75f), 0.0)
        assertEquals(BlendEq.HIGH_DIP, eq.outgoingHighDb(1f), 0.0)
        // The level turns smoothly: halfway through a ramp the amplitude is halfway (about -6 dB), not the dB.
        assertEquals(-6.0, BlendEq.ramp(0.5f, 0f, 1f, -200.0, 0.0).coerceAtLeast(-6.5), 0.5)
        assertTrue(BlendEq.ramp(0.25f, 0f, 1f, DeckFilter.KILL_DB, 0.0) > DeckFilter.KILL_DB)
    }

    @Test fun twoVoicesThatMeetSwapOnABarLineAndAreNeverHeardTogether() {
        // 16 bars: the old song sings to 60 % of the blend, the next from 30 %.
        val out = listOf(range(0f, 0.6f))
        val inc = listOf(range(0.3f, 1f))
        val m = VocalRule.swapAt(out, inc, 16)
        assertNotNull(m)
        assertEquals(0f, (m!! * 16) - Math.round(m * 16), 1e-4f)  // on a bar line
        assertTrue("swapped at $m", m in 0.3f..0.625f)
        neverTogether(BlendEq(16, m), out, inc)
    }

    @Test fun theSwapLosesTheLeastSinging() {
        // The old song's last line ends at bar 8 exactly; the next song starts singing at bar 6: swapping at bar 8 cuts
        // nothing of the old song, and the next song's first two bars are lost while it's still quiet in the blend.
        val m = VocalRule.swapAt(listOf(range(0f, 0.5f)), listOf(range(0.375f, 1f)), 16)
        assertEquals(0.5f, m!!, 1e-4f)
    }

    @Test fun siansaBeatsVocalsAreRead() {
        val text = """{"schema_version": 1, "profiles": [
            {"track_id": "a", "vocals": "separated", "intro_vocal_ms": 32000, "intro_vocal_free_bars": 16, "outro_vocal_free_bars": 40,
             "vocal_sections_ms": [[32000, 64000], [150000, 180000], [5, 1], ["x", 2]], "vocal_heard_ms": [[0, 96000], [104000, 200000]]},
            {"track_id": "b", "vocals": "none"},
            {"track_id": "c"}]}"""
        val (a, b, c) = PluginProfiles.parse(text)
        assertEquals(SongVocals(none = false, sections = listOf(32_000L..64_000L, 150_000L..180_000L), heard = listOf(0L..96_000L, 104_000L..200_000L),
            introFreeBars = 16, outroFreeBars = 40), a.vocals)
        assertTrue(a.vocals!!.knows(150_000, 190_000))
        assertTrue(!a.vocals!!.knows(90_000, 120_000))
        assertEquals(SongVocals(none = true), b.vocals)
        assertNull(c.vocals)
    }

    @Test fun voicesThatDoNotMeetAreLeftToTheBlend() {
        assertNull(VocalRule.swapAt(listOf(range(0f, 0.3f)), listOf(range(0.5f, 1f)), 16))
        assertNull(VocalRule.swapAt(listOf(range(0f, 0.5f)), listOf(range(0.49f, 1f)), 16))  // under half a bar together
        assertNull(VocalRule.swapAt(emptyList(), listOf(range(0f, 1f)), 16))
    }

    @Test fun theSongsSectionsAreLaidOnTheBlend() {
        // The old song from 200 s for 30 s; the next from 1 s at 1.02×.
        val out = SongVocals(none = false, sections = listOf(180_000L..215_000L), heard = listOf(130_000L..240_000L), outroFreeBars = 12)
        val inc = SongVocals(none = false, sections = listOf(8_000L..90_000L), heard = listOf(0L..96_000L), introFreeBars = 8)
        val eq = VocalRule.eq(out, inc, 200_000, 1_000, 30_000, 1.02f, 16)
        assertNotNull(eq.midSwapAt)
        assertTrue(eq.why, eq.why.startsWith("both sing for"))
        neverTogether(eq, VocalRule.fractions(out.sections, 200_000.0, 30_000.0), VocalRule.fractions(inc.sections, 1_000.0, 30_600.0))
        assertEquals("mids swapped at bar ${Math.round(eq.midSwapAt!! * 16) + 1} of 16 (${eq.why})", eq.describe())
    }

    @Test fun unknownOrInstrumentalSongsKeepTheUsualEq() {
        val sung = SongVocals(none = false, sections = listOf(0L..300_000L), heard = listOf(0L..300_000L))
        assertNull(VocalRule.eq(null, sung, 0, 0, 30_000, 1f, 16).midSwapAt)
        assertEquals("one is instrumental", VocalRule.eq(SongVocals(none = true), sung, 0, 0, 30_000, 1f, 16).why)
        // A blend outside the parts that were separated isn't known.
        val ends = SongVocals(none = false, sections = listOf(0L..96_000L), heard = listOf(0L..96_000L))
        assertEquals("the blend is outside the parts separated", VocalRule.eq(ends, sung, 150_000, 0, 30_000, 1f, 16).why)
        assertNotNull(VocalRule.eq(sung, sung, 150_000, 0, 30_000, 1f, 16).midSwapAt)
    }

    @Test fun theDecksFollowThePlan() {
        val out = DeckFilter()
        val inc = DeckFilter()
        val eq = BlendEq(16, 0.5f)
        eq.apply(0f, null, inc)
        assertEquals(DeckFilter.KILL_DB, inc.midDb, 0.0)
        eq.apply(0.75f, out, inc)
        assertEquals(DeckFilter.KILL_DB, out.midDb, 0.0)
        assertEquals(0.0, inc.midDb, 0.0)
    }

    /** At no point of the blend are both voices heard: wherever both sing, one song's mids are well down (or it's gone). */
    private fun neverTogether(eq: BlendEq, out: List<ClosedFloatingPointRange<Float>>, inc: List<ClosedFloatingPointRange<Float>>) {
        for (i in 0..1000) {
            val t = i / 1000f
            val outSings = out.any { t >= it.start && t < it.endInclusive }
            val incSings = inc.any { t >= it.start && t < it.endInclusive }
            if (!outSings || !incSings) continue
            val outHeard = eq.outgoingMidDb(t) > heardDb && Crossfade.fadeOut(t) > 0.1f
            val incHeard = eq.incomingMidDb(t) > heardDb && Crossfade.fadeIn(t) > 0.1f
            assertTrue("both voices heard at ${"%.3f".format(t)}", !(outHeard && incHeard))
        }
    }
}
