package app.musix.ui

import app.musix.playback.CastStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The name shown next to the device button in now playing. */
class CastDeviceLabelTest {
    @Test fun showsTheDeviceWhileCasting() {
        assertEquals("Living Room TV", castDeviceLabel(CastStatus(available = true, device = "Living Room TV")))
    }

    @Test fun saysConnectingWhileItConnects() {
        assertEquals("Connecting…", castDeviceLabel(CastStatus(available = true, connecting = true)))
    }

    @Test fun showsNothingOnThisPhone() {
        assertNull(castDeviceLabel(CastStatus(available = true)))
        assertNull(castDeviceLabel(CastStatus()))
    }
}
