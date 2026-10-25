package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Bringing the music back from the TV to the phone (issue #115). */
class CastHandBackTest {
    private val id: (String) -> String = { it }
    private val saved = CastSpot(listOf("a", "b", "c"), 1, 42_000L)

    @Test fun keepsTheTvsQueueWhenItIsIntact() {
        val live = CastSpot(listOf("a", "b", "c", "d"), 2, 10_000L)
        assertSame(live, CastHandBack.pick(live, saved, id))
    }

    @Test fun usesTheSavedSpotWhenTheTvForgotTheQueue() {
        assertSame(saved, CastHandBack.pick(CastSpot(emptyList(), 0, 0L), saved, id))
        assertSame(saved, CastHandBack.pick(CastSpot(listOf("", "b"), 0, 5_000L), saved, id))
    }

    @Test fun keepsTheTvsQueueWhenNothingWasSaved() {
        val live = CastSpot(listOf("", "b"), 1, 5_000L)
        assertSame(live, CastHandBack.pick(live, null, id))
        assertSame(live, CastHandBack.pick(live, CastSpot(emptyList(), 0, 0L), id))
    }

    @Test fun afterHoldToStopNothingComesBack() {
        // Issue #491: the TV may still list the cleared queue as casting ends; the phone gets nothing.
        val live = CastSpot(listOf("a", "b"), 1, 5_000L)
        assertEquals(CastSpot(emptyList<String>(), 0, 0L), CastHandBack.afterEnd(live, saved, stopped = true, id = id))
        // Ending casting any other way still brings the music back.
        assertSame(live, CastHandBack.afterEnd(live, saved, stopped = false, id = id))
        assertSame(saved, CastHandBack.afterEnd(CastSpot(emptyList(), 0, 0L), saved, stopped = false, id = id))
    }

    @Test fun leavesOutNamelessSongsAndKeepsTheCurrentOne() {
        val spot = CastHandBack.withoutNameless(CastSpot(listOf("", "a", "", "b", "c"), 3, 7_000L), id)
        assertEquals(CastSpot(listOf("a", "b", "c"), 1, 7_000L), spot)
    }

    @Test fun namelessCurrentSongMovesOnToTheNextFromTheTop() {
        assertEquals(CastSpot(listOf("a", "c"), 1, 0L), CastHandBack.withoutNameless(CastSpot(listOf("a", "", "c"), 1, 7_000L), id))
        assertEquals(CastSpot(listOf("a"), 0, 0L), CastHandBack.withoutNameless(CastSpot(listOf("a", ""), 1, 7_000L), id))
    }

    @Test fun nothingLeftMeansAnEmptyQueue() {
        assertEquals(CastSpot(emptyList<String>(), 0, 0L), CastHandBack.withoutNameless(CastSpot(listOf("", ""), 0, 7_000L), id))
    }
}
