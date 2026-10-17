package app.musix.ui

import app.musix.playback.AudioOutput
import app.musix.playback.CastStatus
import app.musix.playback.OutputDevice
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

    @Test fun showsBluetoothHeadphonesOnThePhone() {
        val sony = OutputDevice("Sony Headphones", bluetooth = true)
        assertEquals("Sony Headphones", castDeviceLabel(CastStatus(available = true), sony))
        // Casting wins: the music isn't in the headphones then.
        assertEquals("Living Room TV", castDeviceLabel(CastStatus(available = true, device = "Living Room TV"), sony))
    }

    @Test fun picksTheOutputLikeAndroid() {
        val speaker = 2 // AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        val a2dp = 8 // AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        val wired = 4 // AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        assertNull(AudioOutput.pick(listOf(speaker to "Pixel")))
        assertEquals(OutputDevice("Sony Headphones", true), AudioOutput.pick(listOf(speaker to "Pixel", wired to "x", a2dp to "Sony Headphones")))
        assertEquals(OutputDevice("Headphones", false), AudioOutput.pick(listOf(speaker to "Pixel", wired to "USB-C")))
        assertEquals(OutputDevice("Bluetooth", true), AudioOutput.pick(listOf(a2dp to " ")))
    }

    @Test fun showsTheDlnaDevice() {
        assertEquals("Sonos Kitchen", castDeviceLabel(CastStatus(), null, dlna = "Sonos Kitchen"))
        assertEquals("Connecting…", castDeviceLabel(CastStatus(), null, dlna = "Sonos Kitchen", dlnaConnecting = true))
        // Playing on a DLNA speaker, not in the headphones.
        assertEquals("Sonos Kitchen", castDeviceLabel(CastStatus(), OutputDevice("Sony Headphones", true), dlna = "Sonos Kitchen"))
    }
}
