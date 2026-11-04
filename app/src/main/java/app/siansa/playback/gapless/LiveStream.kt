package app.siansa.playback.gapless

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The last few seconds of the phone's mix as 16-bit samples, by mix frame (issue #114, gapless casting): the mixer
 * writes, and the TV's stream reads from any frame still held, so a stream started again after a skip begins exactly
 * where the new music does.
 */
class PcmRing(val channels: Int, val capacityFrames: Int) {
    private val data = ShortArray(capacityFrames * channels)
    private val lock = ReentrantLock()
    private val grown = lock.newCondition()

    /** The frame after the last one written. */
    @Volatile var end: Long = 0
        private set

    /** The oldest frame still held. */
    val start: Long get() = maxOf(origin, end - capacityFrames)

    /** Where the ring started: nothing before it was ever written. */
    @Volatile var origin: Long = 0
        private set

    /** Starts again empty at mix frame [at]. */
    fun reset(at: Long) = lock.withLock {
        origin = at
        end = at
        grown.signalAll()
    }

    /** Adds [frames] frames from [src] (interleaved). They follow on from [end]. */
    fun write(src: ShortArray, frames: Int) = lock.withLock {
        var left = frames
        var from = 0
        while (left > 0) {
            val at = (end % capacityFrames).toInt()
            val n = minOf(left, capacityFrames - at)
            System.arraycopy(src, from * channels, data, at * channels, n * channels)
            end += n
            from += n
            left -= n
        }
        grown.signalAll()
    }

    /**
     * Copies up to [max] frames from frame [from] into [dst]; returns how many (0 when there are none yet). [from] must
     * be at or after [start]; anything older is gone.
     */
    fun read(from: Long, dst: ShortArray, max: Int): Int = lock.withLock {
        require(from >= start) { "Frame $from is no longer held (oldest $start)" }
        val n = minOf(max.toLong(), end - from).toInt().coerceAtLeast(0)
        var copied = 0
        while (copied < n) {
            val at = ((from + copied) % capacityFrames).toInt()
            val m = minOf(n - copied, capacityFrames - at)
            System.arraycopy(data, at * channels, dst, copied * channels, m * channels)
            copied += m
        }
        n
    }

    /** Waits up to [timeoutMs] until [frames] frames from [from] are held; returns how many are. */
    fun await(from: Long, frames: Int, timeoutMs: Long): Long = lock.withLock {
        var waitNs = timeoutMs * 1_000_000
        while (end - from < frames && waitNs > 0) waitNs = grown.awaitNanos(waitNs)
        end - from
    }

    /** Wakes anyone waiting (to stop). */
    fun poke() = lock.withLock { grown.signalAll() }
}

/**
 * The phone's small web server for gapless casting (issue #114): serves the mix as one endless FLAC stream to the TV,
 * at `http://<phone>:<port>/live/<key>/<n>.flac?from=<mix frame>`. The random [key] keeps other devices on the Wi-Fi
 * from listening in; [n] counts the streams the TV was asked to play, and a newer one ends the one before (the TV
 * starts again after a skip). Each stream starts at the mix frame it was asked for, so it begins with the music that
 * follows the skip, and is encoded on its own, numbered from 0, as a fresh FLAC stream.
 *
 * Plain java.net, no Android, so it is tested on the computer.
 */
class LiveServer(
    private val ring: PcmRing,
    private val sampleRate: Int,
    val key: String,
    private val log: Log,
) {
    /** Where the server's news goes (the app's log), and what the TV does. */
    interface Log {
        fun info(text: String)
        fun warn(text: String)
        /** The TV opened stream [n] from [from], at [address]. */
        fun connected(n: Int, from: Long, address: String) = Unit
    }

    private var server: ServerSocket? = null
    @Volatile private var running = false
    /** The newest stream asked for: older ones end. */
    private val newest = AtomicInteger(0)
    private val clients = java.util.concurrent.ConcurrentHashMap<Int, Socket>()

    /** Frames sent on the newest stream so far, and the longest a write to the TV took (ms). */
    @Volatile var sentFrames = 0L
        private set
    @Volatile var slowestWriteMs = 0L

    val port: Int get() = server?.localPort ?: 0

    /** Listens on [address] (the phone's Wi-Fi address), on any free port. Returns the port. */
    fun start(address: InetAddress, port: Int = 0): Int {
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(address, port))
        server = s
        running = true
        Thread({ accept(s) }, "siansa-cast-server").apply { isDaemon = true; start() }
        log.info("Stream server listening on ${address.hostAddress}:${s.localPort}")
        return s.localPort
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
        clients.values.forEach { runCatching { it.close() } }
        clients.clear()
        ring.poke()
    }

    /** The address of stream [n], starting at mix frame [from], as the TV fetches it from [host]. */
    fun url(host: String, n: Int, from: Long): String = "http://${hostForUrl(host)}:$port/live/$key/$n.flac?from=$from"

    /** Marks stream [n] as the newest before the TV is asked for it, so the one playing ends as soon as it connects. */
    fun expect(n: Int) {
        newest.accumulateAndGet(n) { a, b -> maxOf(a, b) }
    }

    private fun accept(s: ServerSocket) {
        while (running) {
            val socket = try {
                s.accept()
            } catch (e: Exception) {
                if (running) log.warn("Stream server stopped accepting: ${e.message}")
                return
            }
            Thread({ serve(socket) }, "siansa-cast-stream").apply { isDaemon = true; start() }
        }
    }

    private fun serve(socket: Socket) {
        val who = socket.inetAddress?.hostAddress ?: "?"
        var n = -1
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = REQUEST_TIMEOUT_MS
            val input = BufferedInputStream(socket.getInputStream())
            val request = Http.read(input) ?: return
            val out = socket.getOutputStream()
            val route = Http.route(request.path, key)
            when {
                request.method == "OPTIONS" -> {
                    // Chrome asks before a page from the internet fetches from the home network ("private network access").
                    out.write(Http.head(204, "No Content", null))
                    out.flush()
                    return
                }
                route == null -> {
                    log.warn("Stream server: $who asked for something else (${request.method} ${Http.redactPath(request.path, key)})")
                    out.write(Http.head(404, "Not Found", null))
                    out.flush()
                    return
                }
                request.method != "GET" && request.method != "HEAD" -> {
                    out.write(Http.head(405, "Method Not Allowed", null))
                    out.flush()
                    return
                }
            }
            n = route!!.stream
            if (n < newest.get()) {
                log.info("Stream $n asked for by $who, but stream ${newest.get()} is newer: refused")
                out.write(Http.head(410, "Gone", null))
                out.flush()
                return
            }
            newest.accumulateAndGet(n) { a, b -> maxOf(a, b) }
            // An older stream still open ends now: the TV moved on to this one.
            clients.keys.filter { it < n }.forEach { old -> clients.remove(old)?.let { runCatching { it.close() } } }
            clients.put(n, socket)?.let { if (it !== socket) runCatching { it.close() } }
            out.write(Http.head(200, "OK", "audio/flac"))
            if (request.method == "HEAD") {
                out.flush()
                return
            }
            val from = maxOf(route.from ?: ring.end, ring.start)
            log.connected(n, from, who)
            log.info("Stream $n to $who from mix frame $from (asked ${route.from ?: "now"}), FLAC ${sampleRate} Hz 16-bit ${ring.channels} ch" +
                (request.range?.let { ", the TV asked for bytes $it" } ?: ""))
            socket.soTimeout = 0
            stream(n, from, out)
        } catch (e: SocketException) {
            if (running && n >= 0 && n == newest.get()) log.info("Stream $n to $who closed: ${e.message}")
        } catch (e: Exception) {
            log.warn("Stream $n to $who failed: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            if (n >= 0) clients.remove(n, socket)
            runCatching { socket.close() }
        }
    }

    private fun stream(n: Int, from: Long, out: OutputStream) {
        // Uncompressed frames: a steady byte rate, as Chrome on the TV buffers by bytes (see CastGapless).
        val encoder = FlacEncoder(sampleRate, ring.channels, compress = false)
        out.write(encoder.header())
        out.flush()
        val pcm = ShortArray(FlacEncoder.MAX_BLOCK * ring.channels)
        var at = from
        var sent = 0L
        while (running && newest.get() == n) {
            val held = ring.await(at, FlacEncoder.MAX_BLOCK, WAIT_MS)
            if (!running || newest.get() != n) break
            if (at < ring.start) {
                // Fell so far behind that the ring moved on (the TV stopped reading): skip to what's held.
                log.warn("Stream $n fell ${(ring.start - at) * 1000 / sampleRate} ms behind; skipping ahead")
                at = ring.start
                continue
            }
            // A whole frame when there is one; else, once the mixer has gone quiet for a moment (a pause, the end
            // of the music), what there is, so the TV gets every last sample before it waits.
            if (held < FlacEncoder.MIN_BLOCK) continue
            val frames = ring.read(at, pcm, FlacEncoder.MAX_BLOCK)
            if (frames <= 0) continue
            val bytes = encoder.frame(pcm, frames)
            val t0 = System.nanoTime()
            out.write(bytes)
            out.flush()
            val ms = (System.nanoTime() - t0) / 1_000_000
            if (ms > slowestWriteMs) slowestWriteMs = ms
            if (ms > SLOW_WRITE_MS) log.warn("Stream $n: the TV took $ms ms to take ${frames * 1000 / sampleRate} ms of sound (Wi-Fi slow?)")
            at += frames
            sent += frames
            sentFrames = sent
        }
    }

    companion object {
        /** How long a stream waits for a whole frame before sending what there is. */
        const val WAIT_MS = 120L
        const val REQUEST_TIMEOUT_MS = 5_000
        const val SLOW_WRITE_MS = 500L

        /** An IPv6 address goes in brackets in an address. */
        fun hostForUrl(host: String): String = if (':' in host && !host.startsWith("[")) "[$host]" else host

        /** A random key for the stream's address: 16 bytes as hex. */
        fun newKey(random: java.security.SecureRandom = java.security.SecureRandom()): String =
            ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
    }
}

/** Just enough HTTP/1.1 for the stream server. */
internal object Http {
    data class Request(val method: String, val path: String, val range: String?)
    data class Route(val stream: Int, val from: Long?)

    private const val MAX_LINE = 8_192

    /** Reads the request line and headers; null when the connection closed or sent something else. */
    fun read(input: InputStream): Request? {
        val first = line(input) ?: return null
        val parts = first.split(' ')
        if (parts.size < 3 || !parts[2].startsWith("HTTP/")) return null
        var range: String? = null
        while (true) {
            val h = line(input) ?: return null
            if (h.isEmpty()) break
            val colon = h.indexOf(':')
            if (colon > 0 && h.substring(0, colon).trim().equals("range", ignoreCase = true)) range = h.substring(colon + 1).trim()
        }
        return Request(parts[0].uppercase(), parts[1], range)
    }

    private fun line(input: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < MAX_LINE) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
        }
        return null
    }

    /** `/live/<key>/<n>.flac?from=<frame>` with the right key, else null. */
    fun route(path: String, key: String): Route? {
        val bare = path.substringBefore('?')
        val query = path.substringAfter('?', "")
        val m = Regex("""/live/([0-9a-f]+)/(\d{1,9})\.flac""").matchEntire(bare) ?: return null
        if (!constantTimeEquals(m.groupValues[1], key)) return null
        val from = query.split('&').firstOrNull { it.startsWith("from=") }?.removePrefix("from=")?.toLongOrNull()?.takeIf { it >= 0 }
        return Route(m.groupValues[2].toInt(), from)
    }

    /** The path with the key hidden, for the log. */
    fun redactPath(path: String, key: String): String = if (key.isEmpty()) path else path.replace(key, "…")

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    /**
     * The response's head. No length (the stream never ends) and no byte ranges: the TV plays it as it comes. Allowed
     * from any page (the TV screen is on siansa.app) and from the internet into the home network.
     */
    fun head(code: Int, reason: String, type: String?): ByteArray = buildString {
        append("HTTP/1.1 $code $reason\r\n")
        if (type != null) append("Content-Type: $type\r\n")
        append("Cache-Control: no-cache, no-store\r\n")
        append("Access-Control-Allow-Origin: *\r\n")
        append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
        append("Access-Control-Allow-Headers: *\r\n")
        append("Access-Control-Allow-Private-Network: true\r\n")
        append("Connection: close\r\n")
        if (code != 200) append("Content-Length: 0\r\n")
        append("\r\n")
    }.toByteArray(Charsets.US_ASCII)
}
