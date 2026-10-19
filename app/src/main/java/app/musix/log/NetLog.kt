package app.musix.log

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress

/**
 * Every request that goes over the network, in full, while logging is on (issue #180): which server and
 * IP address it went to, the whole address with its parameters, the headers sent, and what came back
 * (status, type, size, protocol, time). Covers and song streams included. Passwords, tokens, salts and
 * other secrets are removed ([AppLog.redact]); cookies and login headers are never written.
 *
 * Runs as a network interceptor, so it sees the connection that was really used. Covers answered from the
 * phone never reach the network and are logged by the app's request log instead.
 */
object NetLog {
    /** Headers that are never written, only that they were sent. */
    private val hidden = setOf("authorization", "proxy-authorization", "cookie", "set-cookie")
    /** Musix's own markers between its interceptors: not sent anywhere that matters. */
    private const val OWN_PREFIX = "x-musix-"

    val interceptor = Interceptor { chain ->
        val request = chain.request()
        if (!AppLog.enabled) return@Interceptor chain.proceed(request)
        val address = (chain.connection()?.route()?.socketAddress as? InetSocketAddress)?.let { ip(it) }
        val protocol = chain.connection()?.protocol()?.toString()
        val started = System.nanoTime()
        try {
            val response = chain.proceed(request)
            val ms = (System.nanoTime() - started) / 1_000_000
            AppLog.d("Net", describe(request, response, address, protocol, ms))
            response
        } catch (e: IOException) {
            val ms = (System.nanoTime() - started) / 1_000_000
            AppLog.d("Net", "${sent(request, address, protocol)}\n  ✗ ${e.javaClass.simpleName}: ${e.message} after ${ms}ms")
            throw e
        }
    }

    private fun ip(a: InetSocketAddress): String {
        val host = a.address?.hostAddress ?: a.hostString
        return if (host.contains(':')) "[$host]:${a.port}" else "$host:${a.port}"
    }

    /** The request line: method, full (redacted) address, the IP it went to, and its headers and body size. */
    fun sent(request: Request, address: String?, protocol: String?): String {
        val to = listOfNotNull(address?.let { "to $it" }, protocol).joinToString(" · ")
        val headers = request.headers.names()
            .filterNot { it.lowercase().startsWith(OWN_PREFIX) }
            .map { name -> if (name.lowercase() in hidden) "$name: •••" else "$name: ${request.headers.values(name).joinToString(", ")}" }
        val body = request.body?.contentLength()?.takeIf { it >= 0 }?.let { "  body ${size(it)}" + (form(request)?.let { f -> " · form ?$f" } ?: "") }
        return AppLog.redact(
            buildString {
                append("→ ${request.method} ${request.url}")
                if (to.isNotEmpty()) append(" ($to)")
                if (headers.isNotEmpty()) append("\n  ").append(headers.joinToString(" · "))
                body?.let { append("\n").append(it) }
            }
        )
    }

    /** A form POST's fields (Navidrome calls with many songs, issue #206), so they're in the log like a GET's. */
    private fun form(request: Request): String? {
        val body = request.body ?: return null
        if (body.contentType()?.subtype != "x-www-form-urlencoded" || body.contentLength() !in 0..64_000) return null
        return runCatching { okio.Buffer().also { body.writeTo(it) }.readUtf8() }.getOrNull()
    }

    /** One entry for a request and its answer. */
    fun describe(request: Request, response: Response, address: String?, protocol: String?, ms: Long): String {
        val length = response.header("Content-Length")?.toLongOrNull()
        val parts = listOfNotNull(
            "${response.code}${response.message.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""}",
            response.header("Content-Type"),
            length?.let { size(it) } ?: "size not sent",
            response.header("Content-Encoding")?.let { "encoding $it" },
            response.header("Content-Range")?.let { "range $it" },
            "in ${ms}ms",
        )
        return sent(request, address, protocol) + "\n  ← " + AppLog.redact(parts.joinToString(" · "))
    }

    fun size(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1_048_576 -> String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0)
        else -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
    }
}
