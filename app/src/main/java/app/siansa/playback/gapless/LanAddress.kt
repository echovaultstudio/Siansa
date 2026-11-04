package app.siansa.playback.gapless

/**
 * Which of the phone's addresses the TV can reach it on (issue #114, gapless casting): its IPv4 address on the Wi-Fi
 * (or Ethernet) network the TV is on, never a VPN's, even with a VPN up.
 */
object LanAddress {
    /** One of the phone's networks: its kind and its addresses with their prefix length ("192.168.1.20" to 24). */
    data class Candidate<T>(
        val id: T,
        val name: String,
        val wifi: Boolean,
        val ethernet: Boolean,
        val vpn: Boolean,
        val addresses: List<Pair<String, Int>>,
    )

    data class Choice<T>(val network: Candidate<T>, val address: String, val why: String)

    /**
     * The network and address to serve the TV from: the one whose subnet holds the TV's address [tv] when it's known,
     * else the Wi-Fi's (then Ethernet's) private IPv4 address. Never a VPN, a loopback or a link-local address.
     */
    fun <T> pick(candidates: List<Candidate<T>>, tv: String?): Choice<T>? {
        val usable = candidates.filter { !it.vpn && (it.wifi || it.ethernet) }
        val tvIp = tv?.let { ipv4(it) }
        if (tvIp != null) {
            for (c in usable) for ((a, prefix) in c.addresses) {
                val ip = ipv4(a) ?: continue
                if (usableAddress(ip) && sameSubnet(ip, tvIp, prefix)) return Choice(c, a, "on the TV's network")
            }
        }
        for (kind in listOf(true, false)) {
            for (c in usable.filter { it.wifi == kind }) for ((a, _) in c.addresses) {
                val ip = ipv4(a) ?: continue
                if (usableAddress(ip) && private(ip)) return Choice(c, a, if (c.wifi) "the Wi-Fi's" else "the Ethernet's")
            }
        }
        return null
    }

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

    fun sameSubnet(a: Long, b: Long, prefix: Int): Boolean {
        if (prefix !in 1..32) return false
        val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        return (a and mask) == (b and mask)
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
