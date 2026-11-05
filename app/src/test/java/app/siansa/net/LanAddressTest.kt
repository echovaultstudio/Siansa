package app.siansa.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Home-network addresses for pairing with the TV app (issue #860). */
class LanAddressTest {
    @Test fun addresses() {
        assertEquals(0xC0A80117L, LanAddress.ipv4("192.168.1.23"))
        assertNull(LanAddress.ipv4("192.168.1"))
        assertNull(LanAddress.ipv4("fe80::1"))
        assertNull(LanAddress.ipv4("256.1.1.1"))
        assertEquals(true, LanAddress.private(LanAddress.ipv4("172.20.0.1")!!))
        assertEquals(false, LanAddress.private(LanAddress.ipv4("172.32.0.1")!!))
        assertEquals(true, LanAddress.private(LanAddress.ipv4("10.0.0.5")!!))
    }

    @Test fun linkLocalAndLoopbackAreNeverUsable() {
        assertEquals(false, LanAddress.usableAddress(LanAddress.ipv4("169.254.3.4")!!))
        assertEquals(false, LanAddress.usableAddress(LanAddress.ipv4("127.0.0.1")!!))
        assertEquals(false, LanAddress.usableAddress(LanAddress.ipv4("0.1.2.3")!!))
        assertEquals(false, LanAddress.usableAddress(LanAddress.ipv4("239.1.1.1")!!))
        assertEquals(true, LanAddress.usableAddress(LanAddress.ipv4("192.168.1.23")!!))
    }
}
