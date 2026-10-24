package app.siansa.scrobble

import org.junit.Assert.assertEquals
import org.junit.Test

/** How much of a song must be heard before it's scrobbled (issue #270). */
class ScrobbleShareTest {
    @Test fun halfByDefault() = assertEquals(90_000L, scrobbleNeededMs(180_000, 50))

    @Test fun theChosenShare() {
        assertEquals(45_000L, scrobbleNeededMs(180_000, 25))
        assertEquals(162_000L, scrobbleNeededMs(180_000, 90))
    }

    @Test fun neverMoreThanFourMinutes() = assertEquals(240_000L, scrobbleNeededMs(600_000, 75))

    @Test fun oddValuesAreKeptInRange() {
        assertEquals(18_000L, scrobbleNeededMs(180_000, 0))
        assertEquals(180_000L, scrobbleNeededMs(180_000, 150))
    }
}
