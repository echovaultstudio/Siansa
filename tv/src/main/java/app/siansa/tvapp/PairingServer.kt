package app.siansa.tvapp

import app.siansa.log.AppLog
import app.siansa.net.LanAddress
import app.siansa.pairing.Pairing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/**
 * The TV's side of signing in from the phone (issue #860): a small web server on the home network, open only while the
 * sign-in screen shows. It answers the QR code's page (which hands the key to the phone's Siansa) and takes the
 * sealed login at [Pairing.PATH]. Anything not sealed with this screen's key is turned away.
 */
class PairingServer(private val key: ByteArray, private val onLogin: (Pairing.Login) -> Unit) {
    private var socket: ServerSocket? = null

    /** Where the QR code points, or null when the TV has no home-network address. */
    var pageUrl: String? = null
        private set

    /** Opens the server on any free port; returns the page's address, or null. */
    fun open(): String? {
        val address = homeAddress() ?: run {
            AppLog.w("Pairing", "No home-network address to show a sign-in code on")
            return null
        }
        val server = ServerSocket(0, BACKLOG, InetAddress.getByName(address))
        socket = server
        pageUrl = Pairing.pageUrl(address, server.localPort, key)
        AppLog.i("Pairing", "Waiting for a phone at $address:${server.localPort}")
        return pageUrl
    }

    /** Answers requests until [close], or until a phone has signed the TV in. */
    suspend fun serve() = withContext(Dispatchers.IO) {
        val server = socket ?: return@withContext
        while (!server.isClosed) {
            val client = try { server.accept() } catch (_: SocketException) { break }
            val done = runCatching { client.use { answer(it, server.localPort) } }
                .onFailure { AppLog.w("Pairing", "A request to the TV failed: ${it.message}") }
                .getOrDefault(false)
            if (done) close()
        }
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    /** One request; true once a login was taken. */
    private fun answer(client: Socket, port: Int): Boolean {
        client.soTimeout = READ_TIMEOUT_MS
        val input = BufferedInputStream(client.getInputStream())
        val requestLine = readLine(input) ?: return false
        val (method, path) = requestLine.split(' ').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        var length = 0
        while (true) {
            val header = readLine(input) ?: break
            if (header.isEmpty()) break
            if (header.startsWith("Content-Length:", ignoreCase = true)) length = header.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val out = client.getOutputStream()
        return when {
            method == "GET" && (path == "/" || path.startsWith("/?")) -> {
                respond(out, 200, "text/html; charset=utf-8", page(client.localAddress.hostAddress.orEmpty(), port))
                false
            }
            method == "POST" && path == Pairing.PATH -> {
                if (length !in 1..MAX_BODY) { respond(out, 413, "text/plain", "Too large"); return false }
                val body = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = input.read(body, read, length - read)
                    if (n < 0) break
                    read += n
                }
                val login = Pairing.open(String(body, 0, read, Charsets.UTF_8), key)
                if (login == null) {
                    AppLog.w("Pairing", "A sign-in that wasn't sealed with this screen's code was turned away")
                    respond(out, 403, "text/plain", "Not for this TV")
                    false
                } else {
                    respond(out, 200, "text/plain", "Signed in")
                    AppLog.i("Pairing", "Signed in from ${client.inetAddress.hostAddress}: ${AppLog.redact(login.url)}")
                    onLogin(login)
                    true
                }
            }
            else -> { respond(out, 404, "text/plain", "Not found"); false }
        }
    }

    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (line.length < MAX_LINE) {
            val c = input.read()
            if (c < 0) return if (line.isEmpty()) null else line.toString()
            if (c == '\n'.code) return line.toString().trimEnd('\r')
            line.append(c.toChar())
        }
        return line.toString()
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (code) { 200 -> "OK"; 403 -> "Forbidden"; 413 -> "Payload Too Large"; else -> "Not Found" }
        out.write("HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    companion object {
        private const val BACKLOG = 4
        private const val READ_TIMEOUT_MS = 10_000
        private const val MAX_BODY = 16_384
        private const val MAX_LINE = 8_192

        /** The TV's IPv4 address on the home network: Ethernet or Wi-Fi, never a VPN's or a loopback. */
        fun homeAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.name.startsWith("tun") && !it.name.startsWith("ppp") }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .mapNotNull { it.hostAddress }
                .firstOrNull { a -> LanAddress.ipv4(a)?.let { LanAddress.usableAddress(it) && LanAddress.private(it) } == true }
        }.getOrNull()

        /**
         * The page the QR code opens on the phone. The key stays after the `#` in the browser, which never sends it;
         * the page builds the link to Siansa from it. No key ever goes over the network in the clear.
         */
        fun page(address: String, port: Int): String = """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Sign in your TV</title>
<style>
body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#000;color:#EEF2F5;
font-family:system-ui,sans-serif;text-align:center}
main{max-width:22rem;padding:2rem}
h1{font-size:1.6rem;margin:0 0 1rem}
p{color:#9DB4C8;line-height:1.5}
a.b{display:block;margin:2rem 0 1rem;padding:1rem;border-radius:999px;background:#FFB547;color:#000;font-weight:700;text-decoration:none;font-size:1.1rem}
</style></head><body><main>
<h1>Sign in your TV</h1>
<p>Siansa on your phone sends your TV the server and login it uses. Nothing else is shared.</p>
<a class="b" id="go" href="#">Open in Siansa</a>
<p>No Siansa on this phone? Pick “Type it in” on the TV instead.</p>
</main><script>
var k=location.hash.slice(1);
document.getElementById('go').href='${Pairing.SCHEME}://${Pairing.HOST}?tv='+encodeURIComponent('$address:$port')+'&k='+encodeURIComponent(k);
</script></body></html>"""
    }
}
