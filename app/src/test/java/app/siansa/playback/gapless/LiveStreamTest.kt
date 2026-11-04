package app.siansa.playback.gapless

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket

/** The phone's stream to the TV (issue #114): the ring of recent mix, and the small web server that streams it. */
class LiveStreamTest {
    private val servers = ArrayList<LiveServer>()

    @After fun stop() = servers.forEach { it.stop() }

    private fun block(frames: Int, from: Int) = ShortArray(frames * 2) { ((from * 2 + it) % 30000).toShort() }

    @Test fun theRingKeepsTheLastSecondsByMixFrame() {
        val ring = PcmRing(2, 1000)
        ring.reset(5_000)
        ring.write(block(700, 0), 700)
        ring.write(block(700, 700), 700)
        assertEquals(6_400L, ring.end)
        assertEquals(5_400L, ring.start)
        val out = ShortArray(600 * 2)
        assertEquals(600, ring.read(5_600, out, 600))
        assertArrayEquals(block(600, 600), out)
        // Only what's there.
        assertEquals(100, ring.read(6_300, out, 600))
        assertEquals(0, ring.read(6_400, out, 600))
    }

    @Test(expected = IllegalArgumentException::class)
    fun framesThatAreGoneCantBeRead() {
        val ring = PcmRing(2, 100)
        ring.write(block(300, 0), 300)
        ring.read(50, ShortArray(20), 10)
    }

    @Test fun waitingForFramesEndsWhenTheyComeOrAfterAWhile() {
        val ring = PcmRing(2, 10_000)
        val t0 = System.nanoTime()
        assertEquals(0L, ring.await(0, 100, 50))
        assertTrue((System.nanoTime() - t0) / 1_000_000 >= 40)
        Thread { Thread.sleep(30); ring.write(block(200, 0), 200) }.start()
        assertEquals(200L, ring.await(0, 100, 2_000))
    }

    private fun server(ring: PcmRing): LiveServer {
        val log = object : LiveServer.Log {
            override fun info(text: String) = Unit
            override fun warn(text: String) = Unit
        }
        return LiveServer(ring, 44_100, "00112233445566778899aabbccddeeff", log).also {
            it.start(InetAddress.getLoopbackAddress())
            servers += it
        }
    }

    private fun get(port: Int, path: String, method: String = "GET"): Pair<String, InputStream> {
        val s = Socket(InetAddress.getLoopbackAddress(), port)
        s.soTimeout = 3_000
        s.getOutputStream().write("$method $path HTTP/1.1\r\nHost: phone\r\nRange: bytes=0-\r\n\r\n".toByteArray())
        val input = s.getInputStream()
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) head.append(input.read().toChar())
        return head.toString() to input
    }

    @Test fun theTvGetsAnEndlessFlacStreamFromWhereItAsked() {
        val ring = PcmRing(2, 44_100 * 10)
        ring.reset(1_000)
        ring.write(block(8192, 0), 8192)
        val srv = server(ring)
        srv.expect(1)
        assertTrue(srv.url("192.168.1.20", 1, 1_000 + 4096).matches(Regex("""http://192\.168\.1\.20:\d+/live/0011[0-9a-f]+/1\.flac\?from=5096""")))
        val (head, body) = get(srv.port, "/live/00112233445566778899aabbccddeeff/1.flac?from=5096")
        assertTrue(head, head.startsWith("HTTP/1.1 200"))
        assertTrue(head.contains("Content-Type: audio/flac"))
        assertFalse("no length: it never ends", head.contains("Content-Length"))
        assertTrue(head.contains("Access-Control-Allow-Origin: *"))
        val data = DataInputStream(body)
        val header = ByteArray(42).also { data.readFully(it) }
        val reader = TestFlacReader(header)
        assertEquals(0L, reader.totalSamples)
        // The first frame: the 4096 frames from 5096, numbered from 0 as a stream of its own.
        val frame = readFrame(data, 4096)
        val (first, samples) = reader.frame(frame)
        assertEquals(0L, first)
        assertArrayEquals(block(4096, 4096), samples)
    }

    /** One uncompressed stereo frame of [n] samples: its size is known. */
    private fun readFrame(data: DataInputStream, n: Int): ByteArray {
        // Header: 2 sync/flags + 2 codes + 1 sample number (0) + 2 block size + 1 CRC = 8 bytes; 2 subframes of
        // 1 + 2n bytes; CRC-16.
        val size = 8 + 2 * (1 + 2 * n) + 2
        return ByteArray(size).also { data.readFully(it) }
    }

    @Test fun aWrongKeyOrPathGetsNothing() {
        val ring = PcmRing(2, 1000)
        val srv = server(ring)
        assertTrue(get(srv.port, "/live/ffffffffffffffffffffffffffffffff/1.flac").first.startsWith("HTTP/1.1 404"))
        assertTrue(get(srv.port, "/").first.startsWith("HTTP/1.1 404"))
        assertTrue(get(srv.port, "/live/00112233445566778899aabbccddeeff/1.mp3").first.startsWith("HTTP/1.1 404"))
    }

    @Test fun chromesQuestionBeforeReachingTheHomeNetworkIsAnswered() {
        val srv = server(PcmRing(2, 1000))
        val head = get(srv.port, "/live/00112233445566778899aabbccddeeff/1.flac", "OPTIONS").first
        assertTrue(head, head.startsWith("HTTP/1.1 204"))
        assertTrue(head.contains("Access-Control-Allow-Private-Network: true"))
    }

    @Test fun aNewerStreamEndsTheOlderOneAndAnOlderOneIsRefused() {
        val ring = PcmRing(2, 44_100)
        ring.write(block(4096, 0), 4096)
        val srv = server(ring)
        val (_, old) = get(srv.port, "/live/00112233445566778899aabbccddeeff/1.flac?from=0")
        DataInputStream(old).readFully(ByteArray(42))
        val (head2, _) = get(srv.port, "/live/00112233445566778899aabbccddeeff/2.flac?from=4096")
        assertTrue(head2.startsWith("HTTP/1.1 200"))
        // The first stream's connection closes: reading it runs out.
        val t0 = System.currentTimeMillis()
        var ended = false
        try {
            while (System.currentTimeMillis() - t0 < 2_000) if (old.read() < 0) { ended = true; break }
        } catch (e: java.io.IOException) {
            ended = true
        }
        assertTrue("the old stream ended", ended)
        assertTrue(get(srv.port, "/live/00112233445566778899aabbccddeeff/1.flac").first.startsWith("HTTP/1.1 410"))
    }

    @Test fun routes() {
        val key = "abcdef0123"
        assertEquals(Http.Route(3, 99L), Http.route("/live/abcdef0123/3.flac?from=99", key))
        assertEquals(Http.Route(3, null), Http.route("/live/abcdef0123/3.flac", key))
        assertEquals(Http.Route(3, null), Http.route("/live/abcdef0123/3.flac?from=-4", key))
        assertNull(Http.route("/live/abcdef0124/3.flac", key))
        assertNull(Http.route("/live/abcdef0123/x.flac", key))
        assertEquals("/live/…/3.flac", Http.redactPath("/live/abcdef0123/3.flac", key))
        assertEquals("[fe80::1]", LiveServer.hostForUrl("fe80::1"))
        assertEquals(32, LiveServer.newKey().length)
    }
}
