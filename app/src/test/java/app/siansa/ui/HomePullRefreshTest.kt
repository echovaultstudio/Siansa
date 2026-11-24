package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pulling Home down to load it again (the owner, 22 Nov). */
class HomePullRefreshTest {
    @Test fun thePageFollowsTheFingerBetweenRestAndTheLock() {
        assertEquals(0.5f, pulledBy(0f, dy = 60f, lock = 120f))
        assertEquals(1f, pulledBy(0.5f, dy = 200f, lock = 120f)) // pulled past: no further than the logo's space
        assertEquals(0.25f, pulledBy(0.5f, dy = -30f, lock = 120f)) // back up with the finger
        assertEquals(0f, pulledBy(0.25f, dy = -90f, lock = 120f)) // never above where it rests
    }

    @Test fun lettingGoLoadsHomeWhenPulledAllTheWayOrFlicked() {
        assertEquals(true, pullLoads(fraction = 1f, velocity = 0f, flick = 2000f))
        assertEquals(false, pullLoads(fraction = 0.9f, velocity = 0f, flick = 2000f)) // let go short, slowly: back up
        assertEquals(true, pullLoads(fraction = 0.4f, velocity = 2500f, flick = 2000f)) // a short quick swipe loads too
        assertEquals(false, pullLoads(fraction = 0.1f, velocity = 2500f, flick = 2000f)) // barely moved: not meant
        assertEquals(false, pullLoads(fraction = 0.4f, velocity = -2500f, flick = 2000f)) // flicked back up
    }

    @Test fun theHalfCirclesComeToRestOnAHalfTurn() {
        // They turn as the TV's do while a DJ mix plays, and stop where they look as at rest (the owner, 22 Nov).
        assertEquals(180f, restingTurn(10f))
        assertEquals(360f, restingTurn(200f))
        assertEquals(0f, restingTurn(0f))
    }

    @Test fun theLogoStaysInTheRoomThePageGivesIt() {
        // The owner, 22 Nov: "the logo appears in the notice pills". Nothing of it until there's room, never taller than it.
        assertEquals(0f, logoShown(0.1f))
        assertEquals(1f, logoShown(1f))
        assertEquals(0.5f, logoSize(0.5f))
        assertEquals(1f, logoSize(1.7f))
    }

    @Test fun theHalfCirclesGoRoundWithTheFinger() {
        // The owner, 22 Nov: "when pulling down the echo waves should move around also in same speed as the swipe".
        assertEquals(0f, pulledTurn(0f))
        assertEquals(90f, pulledTurn(0.5f))
        assertEquals(180f, pulledTurn(1f)) // a whole pull: half a turn, which looks as at rest, so nothing jumps
        assertEquals(180f, pulledTurn(1.2f)) // pulled past: locked, so loading always carries on from the same place
        assertEquals(180f, pulledTurn(2f))
        assertEquals(false, pullLocked(0.99f))
        assertEquals(true, pullLocked(1f))
        assertEquals(true, pullLocked(1.6f))
        assertEquals(0f, pulledTurn(-0.1f))
    }
}
