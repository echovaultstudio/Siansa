package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The icon's motion once a swipe is done (issue #915): it moves, and ends where it started. */
class SwipeMotionTest {
    @Test fun everyMotionStartsAndEndsAtRest() {
        for (motion in SwipeMotion.entries) {
            for (p in listOf(0f, 1f)) {
                val (scale, turn, move) = motion.at(p)
                assertEquals("$motion scale at $p", 1f, scale, 0.001f)
                assertEquals("$motion turn at $p", 0f, turn, 0.001f)
                assertEquals("$motion across at $p", 0f, move.first, 0.001f)
                assertEquals("$motion down at $p", 0f, move.second, 0.001f)
            }
        }
    }

    @Test fun theBinShakesAndTheOthersMoveTheirWay() {
        val turns = (1..9).map { SwipeMotion.REMOVED.at(it / 10f).second }
        assertTrue("the bin turns both ways: $turns", turns.any { it > 3f } && turns.any { it < -3f })
        assertTrue("added pops up", SwipeMotion.ADDED.at(0.5f).third.second < 0f && SwipeMotion.ADDED.at(0.5f).first > 1.2f)
        assertTrue("play next nudges forward", SwipeMotion.NEXT.at(0.5f).third.first > 0f)
    }

    @Test fun progressOutsideTheMotionIsAtRest() {
        assertEquals(SwipeMotion.REMOVED.at(1f), SwipeMotion.REMOVED.at(1.5f))
        assertEquals(SwipeMotion.ADDED.at(0f), SwipeMotion.ADDED.at(-0.2f))
    }
}
