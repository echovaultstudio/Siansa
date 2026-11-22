package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pulling Home down to load it again (the owner, 22 Nov). */
class HomePullRefreshTest {
    @Test fun thePageFollowsTheFingerAndStaysDownWhileItLoads() {
        assertEquals(0f, pulledDown(refreshing = false, fraction = 0f))
        assertEquals(0.5f, pulledDown(refreshing = false, fraction = 0.5f))
        assertEquals(1f, pulledDown(refreshing = false, fraction = 1.8f)) // pulled past: no further than the logo's space
        assertEquals(1f, pulledDown(refreshing = true, fraction = 0f)) // let go: held down until it's loaded
    }

    @Test fun theHalfCirclesComeToRestOnAHalfTurn() {
        // They turn as the TV's do while a DJ mix plays, and stop where they look as at rest (the owner, 22 Nov).
        assertEquals(180f, restingTurn(10f))
        assertEquals(360f, restingTurn(200f))
        assertEquals(0f, restingTurn(0f))
    }
}
