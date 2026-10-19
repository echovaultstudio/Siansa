package app.musix.covers

import app.musix.covers.CoverDownload.NetInfo
import app.musix.covers.CoverDownload.Prefix
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/** Issue #205: "Download all covers" at home, on the home Wi-Fi or through a VPN to it over Wi-Fi. */
class HomeNetworkTest {
    private fun ip(s: String) = InetAddress.getByName(s)
    private fun p(s: String, length: Int) = Prefix(ip(s), length)
    private val server = listOf(ip("192.168.1.106"))

    private val homeWifi = NetInfo(vpn = false, wifi = true, cellular = false, addresses = listOf(p("192.168.1.20", 24)))
    private val otherWifi = NetInfo(vpn = false, wifi = true, cellular = false, addresses = listOf(p("10.20.0.5", 24)))
    private val mobile = NetInfo(vpn = false, wifi = false, cellular = true, addresses = listOf(p("100.70.3.9", 32)))

    @Test fun onTheHomeWifi() = assertNotNull(CoverDownload.homeRoute(listOf(homeWifi), server))

    @Test fun anotherWifiWithoutAVpnIsNotHome() = assertNull(CoverDownload.homeRoute(listOf(otherWifi), server))

    @Test fun aVpnRouteToTheHomeNetworkOverWifi() {
        val vpn = NetInfo(vpn = true, wifi = true, cellular = false, addresses = listOf(p("10.8.0.2", 32)), routes = listOf(p("192.168.1.0", 24)))
        assertNotNull(CoverDownload.homeRoute(listOf(otherWifi, vpn), server))
    }

    @Test fun aVpnThatCarriesEverythingOverWifi() {
        val vpn = NetInfo(vpn = true, wifi = true, cellular = false, addresses = listOf(p("10.8.0.2", 32)), routes = listOf(p("0.0.0.0", 0)))
        assertNotNull(CoverDownload.homeRoute(listOf(otherWifi, vpn), server))
    }

    @Test fun aVpnThatDoesntSayWhatItRunsOverButWifiIsOn() {
        val vpn = NetInfo(vpn = true, wifi = false, cellular = false, addresses = listOf(p("10.8.0.2", 32)), routes = listOf(p("192.168.1.0", 24)))
        assertNotNull(CoverDownload.homeRoute(listOf(otherWifi, vpn), server))
    }

    @Test fun aVpnOverMobileDataNeverCounts() {
        val vpn = NetInfo(vpn = true, wifi = false, cellular = true, addresses = listOf(p("10.8.0.2", 32)), routes = listOf(p("192.168.1.0", 24)))
        assertNull(CoverDownload.homeRoute(listOf(mobile, vpn), server))
        // Not even with Wi-Fi on but the VPN on mobile data: Navidrome would be reached over mobile data.
        assertNull(CoverDownload.homeRoute(listOf(otherWifi, mobile, vpn), server))
    }

    @Test fun aVpnRouteElsewhereIsNotHome() {
        val vpn = NetInfo(vpn = true, wifi = true, cellular = false, addresses = listOf(p("10.8.0.2", 32)), routes = listOf(p("10.8.0.0", 24)))
        assertNull(CoverDownload.homeRoute(listOf(otherWifi, vpn), server))
    }

    /** A domain name that points to a public address: a VPN carrying everything doesn't make it home. */
    @Test fun aPublicAddressIsntHomeThroughAVpn() {
        val vpn = NetInfo(vpn = true, wifi = true, cellular = false, addresses = listOf(p("10.8.0.2", 32)), routes = listOf(p("0.0.0.0", 0)))
        assertNull(CoverDownload.homeRoute(listOf(otherWifi, vpn), listOf(ip("203.0.113.7"))))
    }

    @Test fun tailscaleAddressesArePrivate() {
        assertTrue(CoverDownload.isPrivate(ip("100.101.5.6")))
        assertTrue(CoverDownload.isPrivate(ip("192.168.1.106")))
        assertTrue(CoverDownload.isPrivate(ip("fd7a:115c:a1e0::1")))
        assertFalse(CoverDownload.isPrivate(ip("203.0.113.7")))
        assertFalse(CoverDownload.isPrivate(ip("100.200.1.1")))
    }
}
