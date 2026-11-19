package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** How a blend rides the EQ (the owner, 19 Nov): a few styles, laid on the bars, never the same twice in a row. */
class EqStyleTest {
    private val kill = DeckFilter.KILL_DB
    private val instant = 0.01f

    private fun EqScore.all() = listOf(outLow, outMid, outHigh, inLow, inMid, inHigh)

    @Test fun eachBookedStyleSwapsTheBassAtOnceOnAPhraseLine() {
        for (bars in listOf(16, 32)) for (style in listOf(EqStyle.HARD_SWAP, EqStyle.TEASE, EqStyle.SPLIT)) {
            val s = EqStyles.score(style, bars)!!
            val swap = s.inLow.moves.single().atBar
            assertEquals("$style over $bars bars swaps on a phrase line", 0f, swap % 4f)
            assertEquals("the move is at once", 0f, s.inLow.moves.single().bars)
            // A hair before the bar: the old bass alone; just after: the new bass alone.
            assertEquals(0.0, s.outLow.at(swap - 0.001f, instant), 0.01)
            assertEquals(kill, s.inLow.at(swap - 0.001f, instant), 0.01)
            assertEquals(kill, s.outLow.at(swap + instant, instant), 0.01)
            assertEquals(0.0, s.inLow.at(swap + instant, instant), 0.01)
        }
    }

    @Test fun neverTwoBasslinesAtOnce() {
        for (bars in listOf(4, 8, 16, 32)) for (style in listOf(EqStyle.HARD_SWAP, EqStyle.TEASE, EqStyle.SPLIT)) {
            val s = EqStyles.score(style, bars)!!
            var t = 0f
            while (t <= bars) {
                assertFalse("$style, $bars bars, at bar $t: both basses up",
                    s.outLow.at(t, instant) > -6 && s.inLow.at(t, instant) > -6)
                t += 0.001f * bars
            }
        }
    }

    @Test fun theBlendEndsOnTheNextSongAlone() {
        for (bars in listOf(4, 16, 32)) for (style in listOf(EqStyle.HARD_SWAP, EqStyle.TEASE, EqStyle.SPLIT)) {
            val s = EqStyles.score(style, bars)!!
            val end = bars.toFloat()
            assertEquals(0.0, s.inLow.at(end, instant), 0.01)
            assertEquals(0.0, s.inMid.at(end, instant), 0.01)
            assertEquals(0.0, s.inHigh.at(end, instant), 0.01)
            assertEquals(kill, s.outLow.at(end, instant), 0.01)
            assertEquals(kill, s.outMid.at(end, instant), 0.01)
            // And starts on the old one: the next song's bass and mids held back.
            assertEquals(0.0, s.outLow.at(0f, instant), 0.01)
            assertEquals(kill, s.inLow.at(0f, instant), 0.01)
            assertEquals(kill, s.inMid.at(0f, instant), 0.01)
            s.all().forEach { lane -> lane.moves.forEach { assertTrue("$style moves inside the blend", it.atBar >= 0f && it.atBar + it.bars <= end) } }
        }
    }

    @Test fun theStylesSoundDifferent() {
        val hard = EqStyles.score(EqStyle.HARD_SWAP, 32)!!
        val tease = EqStyles.score(EqStyle.TEASE, 32)!!
        val split = EqStyles.score(EqStyle.SPLIT, 32)!!
        assertEquals(16f, hard.inLow.moves.single().atBar)
        assertEquals(24f, tease.inLow.moves.single().atBar)  // late, after a long tease on the highs
        assertEquals(20f, split.inMid.moves.single().atBar)  // the mids a phrase after the bass
        assertEquals(0f, split.inMid.moves.single().bars)
        // The tease brings the next song's highs in first, long before its bass.
        assertTrue(tease.inHigh.at(4f, instant) > -1 && tease.inLow.at(20f, instant) <= kill + 0.01)
    }

    @Test fun whereBothWouldSingTheMidsSwapThereAtOnce() {
        for (style in listOf(EqStyle.HARD_SWAP, EqStyle.TEASE, EqStyle.SPLIT)) {
            val s = EqStyles.score(style, 16, vocalSwapBar = 6)!!
            var t = 0f
            while (t <= 16f) {
                assertFalse("$style at bar $t: two voices", s.outMid.at(t, instant) > -6 && s.inMid.at(t, instant) > -6)
                t += 0.01f
            }
            assertEquals(0.0, s.inMid.at(6f + instant, instant), 0.01)
            assertTrue(s.note.contains("one voice"))
        }
    }

    @Test fun aShortBlendStillSwapsInside() {
        val s = EqStyles.score(EqStyle.HARD_SWAP, 4)!!
        assertEquals(2f, s.inLow.moves.single().atBar)
        assertNull(EqStyles.score(EqStyle.GLIDE, 16))
        assertNull(EqStyles.score(EqStyle.HARD_SWAP, 1))
    }

    @Test fun choosingNeverRepeatsAndOnlyLaysWhatCanBeLaid() {
        val random = Random(7)
        var last: EqStyle? = null
        val seen = mutableSetOf<EqStyle>()
        repeat(200) {
            val style = EqStyles.choose(Technique.BASS_SWAP, onBars = true, phrased = true, last = last, random = random)
            assertNotEquals(last, style)
            seen += style
            last = style
        }
        assertEquals(EqStyle.entries.toSet(), seen)
        // Off the bars, phrases unknown, or a technique with its own way out: the glide.
        assertEquals(EqStyle.GLIDE, EqStyles.choose(Technique.BASS_SWAP, onBars = false, phrased = true, last = null))
        assertEquals(EqStyle.GLIDE, EqStyles.choose(Technique.BASS_SWAP, onBars = true, phrased = false, last = null))
        for (own in listOf(Technique.FILTER_FADE, Technique.DROP_SWAP, Technique.ECHO_OUT)) {
            assertEquals(EqStyle.GLIDE, EqStyles.choose(own, onBars = true, phrased = true, last = null))
        }
    }

    /** The filter itself: a booked swap moves a bass tone's level on its frame, not a tick later. */
    @Test fun aBookedSwapLandsOnItsFrame() {
        val rate = 48_000
        val filter = DeckFilter()
        val barFrames = rate * 2.0  // a bar of 2 s (120 BPM)
        val swapFrame = 4 * barFrames
        filter.book(DeckFilter.Booked(Lane(0.0, listOf(Lane.Move(4f, 0f, kill))), Lane(0.0), Lane(0.0), 0.0, barFrames, DeckFilter.INSTANT_MS * rate / 1000))
        filter.prepare(rate, 1)
        val total = (swapFrame + rate / 10).toInt()
        val out = DoubleArray(total)
        for (f in 0 until total) {
            if (f % DeckFilter.FOLLOW_FRAMES == 0) filter.follow(f.toLong())
            out[f] = filter.process(sin(2 * PI * 50 * f / rate), 0)
        }
        // The level of one 50 Hz cycle (20 ms) from frame [from].
        fun level(from: Int): Double {
            val n = rate / 50
            return sqrt((from until from + n).sumOf { out[it] * out[it] } / n) * sqrt(2.0)
        }
        val before = level(swapFrame.toInt() - rate / 50)
        val after = level(swapFrame.toInt() + rate / 100)
        assertTrue("before the bar the bass is whole ($before)", before > 0.9)
        assertTrue("10 ms after the bar it's gone ($after)", after < 0.12)
        filter.book(null)
        assertFalse(filter.isBooked)
    }
}
