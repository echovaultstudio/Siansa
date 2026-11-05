package app.siansa.net

/**
 * Home-network IPv4 addresses (issue #860, pairing with Siansa's TV app): which addresses a TV or phone on the same
 * Wi-Fi can be reached on, never a loopback, link-local or public one.
 */
object LanAddress {
    /** An IPv4 address as a 32-bit number, or null for anything else. */
    fun ipv4(text: String): Long? {
        val parts = text.trim().split('.')
        if (parts.size != 4) return null
        var v = 0L
        for (p in parts) {
            val n = p.toIntOrNull() ?: return null
            if (n !in 0..255 || p.length > 3) return null
            v = (v shl 8) or n.toLong()
        }
        return v
    }

    /** Not 0.x, loopback (127.x), link-local (169.254.x) or multicast and above. */
    fun usableAddress(ip: Long): Boolean {
        val first = (ip ushr 24).toInt()
        val second = ((ip ushr 16) and 0xFF).toInt()
        return first != 0 && first != 127 && !(first == 169 && second == 254) && first < 224
    }

    /** A home-network address: 10/8, 172.16/12, 192.168/16. */
    fun private(ip: Long): Boolean {
        val first = (ip ushr 24).toInt()
        val second = ((ip ushr 16) and 0xFF).toInt()
        return first == 10 || (first == 172 && second in 16..31) || (first == 192 && second == 168)
    }
}
