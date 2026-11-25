package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** After a DJ blend the song keeps its matched speed (the owner, 25 Nov: the glide back to its own was heard). */
class HeldSpeedTest {
    @Test fun aSmallMatchIsHeldAsItIs() {
        assertEquals(1.016f, BeatMatch.held(1.016f), 0f)
        assertEquals(0.985f, BeatMatch.held(0.985f), 0f)
        // Held, nothing changes while it plays: no step is ever sent.
        for (ms in listOf(0L, 5_000L, BeatMatch.EASE_MS / 2, BeatMatch.EASE_MS, BeatMatch.EASE_MS * 2)) {
            assertNull(BeatMatch.easeStep(1.016f, ms, 1.016f, to = BeatMatch.held(1.016f)))
        }
    }

    @Test fun aBigMatchEasesOnlyBackToTheHoldsEdge() {
        val held = BeatMatch.held(1.05f)
        assertEquals(1f + BeatMatch.HOLD_MAX, held, 1e-6f)
        assertEquals(held, BeatMatch.eased(1.05f, BeatMatch.EASE_MS, held), 1e-6f)
        assertEquals(1.05f - (1.05f - held) / 2, BeatMatch.eased(1.05f, BeatMatch.EASE_MS / 2, held), 1e-5f)
        assertEquals(1f - BeatMatch.HOLD_MAX, BeatMatch.held(0.93f), 1e-6f)
        // At the end it lands on the held speed (times the listener's own), not on 1.
        assertEquals(1.25f * held, BeatMatch.easeStep(1.05f, BeatMatch.EASE_MS, 1.25f * 1.03f, base = 1.25f, to = held)!!, 1e-6f)
    }

    /** The next song is matched to the old one as it plays, held: a long mix never needs more than the two limits. */
    @Test fun aChainOfBlendsStaysWithinReach() {
        var held = 1f
        repeat(20) {
            val matched = held * (1f + BeatMatch.MAX_SHIFT.toFloat())  // the biggest match, every time, one way
            assertTrue("$matched", matched <= (1f + BeatMatch.HOLD_MAX) * (1f + BeatMatch.MAX_SHIFT.toFloat()) + 1e-6f)
            held = BeatMatch.held(matched)
        }
        assertEquals(1f + BeatMatch.HOLD_MAX, held, 1e-6f)
    }
}
