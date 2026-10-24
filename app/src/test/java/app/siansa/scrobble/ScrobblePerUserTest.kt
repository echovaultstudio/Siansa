package app.siansa.scrobble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scrobbling on or off for each user's songs (issue #356). */
class ScrobblePerUserTest {
    @Test fun everyoneIsScrobbledUntilTurnedOff() {
        assertTrue(scrobblesFrom(0, emptySet()))
        assertTrue(scrobblesFrom(2, emptySet()))
    }

    @Test fun aUserTurnedOffIsLeftOutOthersStay() {
        val off = setOf(2)
        assertFalse(scrobblesFrom(2, off))
        assertTrue(scrobblesFrom(0, off))
        assertTrue(scrobblesFrom(1, off))
    }
}
