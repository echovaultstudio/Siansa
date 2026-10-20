package app.musix.covers

import app.musix.playback.Streaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A VPN counts as mobile data; Wi-Fi only can be switched off (issue #299). */
class VpnIsMobileTest {
    private val wifi = CoverDownload.NetInfo(vpn = false, wifi = true, cellular = false, addresses = emptyList())
    private val mobile = CoverDownload.NetInfo(vpn = false, wifi = false, cellular = true, addresses = emptyList())
    private val vpnOverWifi = CoverDownload.NetInfo(vpn = true, wifi = true, cellular = false, addresses = emptyList())

    @Test fun aVpnIsNeverWifiForStreamingAndSongDownloads() {
        assertTrue(Streaming.countsAsWifi(vpn = false, wifi = true, ethernet = false, notMetered = true))
        assertFalse(Streaming.countsAsWifi(vpn = true, wifi = true, ethernet = false, notMetered = true))
        assertFalse(Streaming.countsAsWifi(vpn = false, wifi = false, ethernet = false, notMetered = false))
        assertTrue(Streaming.countsAsWifi(vpn = false, wifi = false, ethernet = true, notMetered = false))
    }

    @Test fun withWifiOnlyAVpnStopsCoverDownloads() {
        assertNull(CoverDownload.networkProblem(listOf(wifi), wifiOnly = true))
        assertEquals("A VPN is on, which counts as mobile data", CoverDownload.networkProblem(listOf(wifi, vpnOverWifi), wifiOnly = true))
        assertEquals("Not on Wi-Fi", CoverDownload.networkProblem(listOf(mobile), wifiOnly = true))
    }

    @Test fun withoutWifiOnlyAnyConnectionWillDo() {
        assertNull(CoverDownload.networkProblem(listOf(wifi, vpnOverWifi), wifiOnly = false))
        assertNull(CoverDownload.networkProblem(listOf(mobile), wifiOnly = false))
        assertEquals("No connection", CoverDownload.networkProblem(emptyList(), wifiOnly = false))
    }
}
