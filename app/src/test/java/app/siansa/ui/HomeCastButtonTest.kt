package app.siansa.ui

import app.siansa.playback.CastStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's cast button shows only when there's something to play on, or something is playing there. */
class HomeCastButtonTest {
    private val none = CastStatus(available = true)

    @Test fun nothingFoundNoButton() {
        assertFalse(homeCastShown(none, dlnaFound = 0, dlnaSelected = false, dlnaConnecting = false))
        assertFalse(homeCastShown(CastStatus(), dlnaFound = 0, dlnaSelected = false, dlnaConnecting = false))
    }

    @Test fun aChromecastOrADlnaSpeakerAroundShowsIt() {
        assertTrue(homeCastShown(none.copy(devicesFound = true), dlnaFound = 0, dlnaSelected = false, dlnaConnecting = false))
        assertTrue(homeCastShown(none, dlnaFound = 1, dlnaSelected = false, dlnaConnecting = false))
    }

    @Test fun castingOrConnectingAlwaysShowsIt() {
        assertTrue(homeCastShown(none.copy(device = "SHIELD"), 0, false, false))
        assertTrue(homeCastShown(none.copy(connecting = true), 0, false, false))
        assertTrue(homeCastShown(none, 0, dlnaSelected = true, dlnaConnecting = false))
        assertTrue(homeCastShown(none, 0, dlnaSelected = false, dlnaConnecting = true))
    }
}
