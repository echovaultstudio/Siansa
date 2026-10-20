package app.musix.covers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Stopping the cover download cancels the request in hand straight away, even mid-file (issue #301). */
class CancellableCallTest {
    private val server = ServerSocket(0)
    private val client = OkHttpClient()

    @After fun close() {
        server.close()
    }

    /** A cover with no length that arrives a few bytes at a time, like a huge animated one over a slow link. */
    private fun slowCover() = thread(isDaemon = true) {
        runCatching {
            server.accept().use { socket ->
                socket.getInputStream().read(ByteArray(4096))
                val out = socket.getOutputStream()
                out.write("HTTP/1.1 200 OK\r\nContent-Type: image/webp\r\nConnection: close\r\n\r\n".toByteArray())
                while (true) {
                    out.write(ByteArray(16))
                    out.flush()
                    Thread.sleep(50)
                }
            }
        }
    }

    private fun call() = client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/rest/getCoverArt?id=al-1").build())

    @Test fun stoppingCancelsADownloadMidFile() = runBlocking {
        slowCover()
        var reading = false
        var finished = false
        val job = launch(Dispatchers.IO) {
            call().executeCancellable { response ->
                reading = true
                response.body!!.source().readByteArray() // never ends by itself
                finished = true
            }
        }
        withTimeout(5_000) { while (!reading) delay(20) }
        delay(200)
        // Before the fix this waited for the whole file: here, forever.
        withTimeout(2_000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
        assertFalse(finished)
    }

    @Test fun aWholeAnswerIsReadAsUsual() = runBlocking {
        thread(isDaemon = true) {
            server.accept().use { socket ->
                socket.getInputStream().read(ByteArray(4096))
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nhello".toByteArray())
                socket.getOutputStream().flush()
            }
        }
        val text = kotlinx.coroutines.withContext(Dispatchers.IO) { call().executeCancellable { it.body!!.string() } }
        assertEquals("hello", text)
    }
}
