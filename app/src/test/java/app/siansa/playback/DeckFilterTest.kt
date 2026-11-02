package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** The bass swap of a beat-matched blend (issue #807): the filters, their sweep, and that at rest nothing changes. */
class DeckFilterTest {
    @Test fun theHighPassTakesTheBassAndKeepsTheRest() {
        val f = Biquad.highPass(200.0, 48_000)
        assertTrue(f.responseDb(50.0, 48_000) < -20)
        assertEquals(-3.0, f.responseDb(200.0, 48_000), 0.3)
        assertEquals(0.0, f.responseDb(2_000.0, 48_000), 0.1)
    }

    @Test fun theLowShelfHoldsTheBassBack() {
        val f = Biquad.lowShelf(150.0, -12.0, 48_000)
        assertEquals(-12.0, f.responseDb(20.0, 48_000), 0.5)
        assertEquals(0.0, f.responseDb(4_000.0, 48_000), 0.2)
        assertTrue(f.responseDb(150.0, 48_000) in -9.0..-3.0)
    }

    @Test fun theSwapSweepsTheCutUpAsTheShelfComesIn() {
        assertEquals(0.0, DeckFilter.lowCutAt(0.0), 0.0)
        assertEquals(DeckFilter.LOW_CUT_HZ, DeckFilter.lowCutAt(1.0), 1e-9)
        assertEquals(DeckFilter.LOW_CUT_HZ, DeckFilter.lowCutAt(2.0), 1e-9)  // past the swap: stays
        assertTrue(DeckFilter.lowCutAt(0.25) > DeckFilter.LOW_CUT_HZ * 0.25)  // quick at first
        assertEquals(DeckFilter.SHELF_START_DB, DeckFilter.shelfAt(0.0), 0.0)
        assertEquals(DeckFilter.SHELF_START_DB / 2, DeckFilter.shelfAt(0.5), 1e-9)
        assertEquals(0.0, DeckFilter.shelfAt(1.0), 0.0)
    }

    @Test fun atRestTheSoundPassesUntouchedAndAChangeGlidesIn() {
        val deck = DeckFilter()
        assertTrue(deck.idle)
        assertFalse(deck.prepare(48_000, 2))
        assertEquals(0.5, deck.process(0.5, 0), 0.0)
        // A target set from the fade: the filter moves there a step per buffer, so it can't click.
        deck.lowCutHz = DeckFilter.LOW_CUT_HZ
        assertFalse(deck.idle)
        // The first steps of the sweep are below 20 Hz, as good as off: nothing to apply yet.
        repeat(3) { assertFalse(deck.prepare(48_000, 2)) }
        assertTrue(deck.prepare(48_000, 2))
        var buffers = 4
        while (deck.describe() != "low cut 200 Hz, bass +0 dB") {
            deck.prepare(48_000, 2)
            buffers++
            assertTrue("never got there", buffers < 100)
        }
        assertTrue("took $buffers buffers", buffers in 20..30)
        // At the full cut, a 50 Hz tone is near gone and a 1 kHz tone is kept.
        assertTrue(amplitude(deck, 50.0) < 0.1)
        assertTrue(amplitude(deck, 1_000.0) > 0.9)
        deck.reset()
        assertTrue(deck.idle)
        assertFalse(deck.prepare(48_000, 2))
    }

    /** The steady amplitude of a [hz] sine through [deck], mono. */
    private fun amplitude(deck: DeckFilter, hz: Double): Double {
        deck.prepare(48_000, 2)
        var peak = 0.0
        for (i in 0 until 48_000) {
            val y = deck.process(sin(2 * PI * hz * i / 48_000), 0)
            if (i > 24_000 && y > peak) peak = y
        }
        return peak
    }
}
