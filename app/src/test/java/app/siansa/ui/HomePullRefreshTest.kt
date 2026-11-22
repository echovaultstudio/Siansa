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

    @Test fun theLogoStaysInTheRoomThePageGivesIt() {
        // The owner, 22 Nov: "the logo appears in the notice pills". Nothing of it until there's room, never taller than it.
        assertEquals(0f, logoShown(0.2f))
        assertEquals(1f, logoShown(1f))
        assertEquals(0.5f, logoSize(0.5f))
        assertEquals(1f, logoSize(1.7f))
    }

    @Test fun theHalfCirclesGoRoundWithTheFinger() {
        // The owner, 22 Nov: "when pulling down the echo waves should move around also in same speed as the swipe".
        assertEquals(0f, pulledTurn(0f))
        assertEquals(90f, pulledTurn(0.5f))
        assertEquals(180f, pulledTurn(1f)) // a whole pull: half a turn, which looks as at rest, so nothing jumps
        assertEquals(216f, pulledTurn(1.2f)) // pulled past: still with the finger
        assertEquals(0f, pulledTurn(-0.1f))
    }
}
