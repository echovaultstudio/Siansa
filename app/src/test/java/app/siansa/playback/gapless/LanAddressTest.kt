package app.siansa.playback.gapless

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which address the TV reaches the phone on for gapless casting (issue #114), VPN or not. */
class LanAddressTest {
    private fun net(name: String, vararg addresses: Pair<String, Int>, wifi: Boolean = false, ethernet: Boolean = false, vpn: Boolean = false) =
        LanAddress.Candidate(name, name, wifi, ethernet, vpn, addresses.toList())

    private val wifi = net("wlan0", "fe80::1c2:3ff:fe04:506" to 64, "192.168.1.23" to 24, wifi = true)
    private val vpn = net("tun0", "10.8.0.2" to 24, vpn = true)

    @Test fun theWifisIpv4AddressEvenWithAVpnUp() {
        assertEquals("192.168.1.23", LanAddress.pick(listOf(vpn, wifi), tv = null)?.address)
        assertEquals("192.168.1.23", LanAddress.pick(listOf(vpn, wifi), tv = "192.168.1.50")?.address)
    }

    @Test fun neverTheVpnEvenWhenItLooksLikeTheTvsNetwork() {
        val tricky = net("tun0", "192.168.1.200" to 24, wifi = true, vpn = true)
        assertEquals("192.168.1.23", LanAddress.pick(listOf(tricky, wifi), tv = "192.168.1.50")?.address)
        assertNull(LanAddress.pick(listOf(vpn), tv = "192.168.1.50"))
    }

    @Test fun theNetworkTheTvIsOnComesFirst() {
        val guest = net("wlan1", "10.0.0.5" to 24, wifi = true)
        val lan = net("eth0", "192.168.2.9" to 24, ethernet = true)
        assertEquals("192.168.2.9", LanAddress.pick(listOf(guest, lan), tv = "192.168.2.40")?.address)
        // Not known: the Wi-Fi's before Ethernet's.
        assertEquals("10.0.0.5", LanAddress.pick(listOf(lan, guest), tv = null)?.address)
    }

    @Test fun linkLocalAndLoopbackAreNeverPicked() {
        val odd = net("wlan0", "169.254.3.4" to 16, "127.0.0.1" to 8, wifi = true)
        assertNull(LanAddress.pick(listOf(odd), tv = null))
        assertNull(LanAddress.pick(listOf(odd), tv = "169.254.9.9"))
    }

    @Test fun addresses() {
        assertEquals(0xC0A80117L, LanAddress.ipv4("192.168.1.23"))
        assertNull(LanAddress.ipv4("192.168.1"))
        assertNull(LanAddress.ipv4("fe80::1"))
        assertNull(LanAddress.ipv4("256.1.1.1"))
        assertEquals(true, LanAddress.sameSubnet(LanAddress.ipv4("10.1.2.3")!!, LanAddress.ipv4("10.1.9.9")!!, 16))
        assertEquals(false, LanAddress.sameSubnet(LanAddress.ipv4("10.1.2.3")!!, LanAddress.ipv4("10.2.2.3")!!, 16))
        assertEquals(true, LanAddress.private(LanAddress.ipv4("172.20.0.1")!!))
        assertEquals(false, LanAddress.private(LanAddress.ipv4("172.32.0.1")!!))
    }
}
