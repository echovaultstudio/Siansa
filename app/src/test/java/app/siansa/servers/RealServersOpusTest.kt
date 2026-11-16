package app.siansa.servers

import android.app.Application
import android.net.Uri
import app.siansa.data.AppSettings
import app.siansa.data.Song
import app.siansa.net.EmbyApi
import app.siansa.net.MusicSource
import app.siansa.net.SourceKind
import app.siansa.net.SubsonicApi
import app.siansa.playback.Streaming
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Opus from real Navidrome, Emby and Jellyfin servers (issue #1204), with the library of [RealServersTest]: every
 * song converted to Opus, and converted from 10 seconds in, as a seek asks for it. Skipped unless
 * SIANSA_TEST_SERVERS names the servers (CI's Servers workflow does).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RealServersOpusTest(private val server: String) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun servers() = listOf("navidrome", "emby", "jellyfin")

        /** The length of an Ogg Opus stream in seconds: its last page's sample count, less the samples skipped at its start. */
        fun opusSeconds(bytes: ByteArray): Double {
            fun find(mark: String, from: Int, back: Boolean): Int {
                val m = mark.toByteArray()
                val range = if (back) (bytes.size - m.size downTo 0) else (from..bytes.size - m.size)
                for (i in range) if (m.indices.all { bytes[i + it] == m[it] }) return i
                return -1
            }
            fun little(at: Int, n: Int) = (0 until n).fold(0L) { acc, i -> acc or ((bytes[at + i].toLong() and 0xFF) shl (8 * i)) }
            val head = find("OpusHead", 0, back = false)
            val last = find("OggS", 0, back = true)
            if (head < 0 || last < 0) return -1.0
            return (little(last + 6, 8) - little(head + 10, 2)) / 48_000.0
        }
    }

    private val address = System.getenv("SIANSA_TEST_SERVERS").orEmpty().split(',')
        .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()[server]
    private val http = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
    private val kind = when (server) {
        "emby" -> SourceKind.EMBY
        "jellyfin" -> SourceKind.JELLYFIN
        else -> SourceKind.OPENSUBSONIC
    }

    private fun source(): MusicSource {
        assumeTrue("$server isn't running (set SIANSA_TEST_SERVERS)", address != null)
        val settings = AppSettings(navidromeUrl = address!!, navidromeUser = "max", navidromePassword = "sesame", sourceKind = kind)
        return if (kind.embyLike) EmbyApi(http, { settings }, jellyfin = kind == SourceKind.JELLYFIN) else SubsonicApi(http) { settings }
    }

    /**
     * Asks for [url], once more after a moment when the server answers with an error: Emby 4.8 sometimes refuses to
     * start a conversion right after another one ("Error starting ffmpeg", HTTP 500; seen in CI, not on 4.10 or 4.11).
     */
    private fun <T> answer(url: Uri, what: String, read: (okhttp3.Response) -> T): T {
        repeat(2) { attempt ->
            http.newCall(Request.Builder().url(url.toString()).build()).execute().use { r ->
                if (r.code < 500 || attempt == 1) {
                    assertEquals("$what: answer", 200, r.code)
                    return read(r)
                }
            }
            Thread.sleep(2_000)
        }
        error("unreachable")
    }

    private fun opus(url: Uri, what: String): Double = answer(url, what) { r ->
        val seconds = opusSeconds(r.body!!.bytes())
        assertTrue("$what: Opus in Ogg", seconds > 0)
        seconds
    }

    @Test fun everySongConvertsToOpusAndStartsPartWayIn() = runBlocking {
        val source = source()
        val songs: List<Song> = source.songsPage(0, 500)
        assertTrue("$server: songs", songs.isNotEmpty())
        var converted = 0
        songs.forEach { s ->
            val what = "$server ${s.title} (.${s.suffix})"
            val plain = Uri.parse(source.streamUrl(s.id))
            // A file that is Opus already plays as it is: the servers don't convert it again.
            if (Streaming.keepsOriginal(kind, s.suffix)) return@forEach
            val whole = opus(Streaming.rewrite(plain, 128), what)
            assertEquals("$what: the whole song", s.durationSec.toDouble(), whole, 1.5)
            // As a seek asks for it: from 10 seconds in, and again from 20 (not the first conversion once more).
            listOf(10, 20).forEach { from ->
                val part = opus(Streaming.rewrite(Streaming.startingAt(plain, from * 1000L), 128), "$what from ${from}s")
                assertEquals("$what: from ${from}s", whole - from, part, 1.5)
            }
            converted++
        }
        assertTrue("$server: songs converted", converted >= songs.size - 1)
    }

    private fun bytes(url: Uri, what: String): ByteArray = answer(url, what) { r ->
        // Navidrome's MP3 comes with an estimated length, which can be more than it sends: read what there is.
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        val input = r.body!!.byteStream()
        try { while (true) { val n = input.read(buffer); if (n < 0) break; out.write(buffer, 0, n) } } catch (_: java.io.IOException) { }
        out.toByteArray()
    }

    private fun ByteArray.startsWith(mark: String) = size > mark.length && mark.indices.all { this[it] == mark[it].code.toByte() }

    /**
     * What a TV or speaker is sent (Chromecast, DLNA): MP3 320, Opus 320 and, on Emby and Jellyfin, FLAC, each whole
     * and from 10 seconds in, as a seek on the TV asks for it.
     */
    @Test fun everySongConvertsForATvOrSpeaker() = runBlocking {
        val source = source()
        source.songsPage(0, 500).forEach { s ->
            val what = "$server ${s.title} (.${s.suffix})"
            val plain = Uri.parse(source.streamUrl(s.id))
            val mp3 = Streaming.forCast(plain, s.suffix)
            val whole = bytes(mp3, "$what as MP3")
            // An MP3 starts with its ID3 tag or a frame.
            assertTrue("$what: an MP3", whole.startsWith("ID3") || (whole[0] == 0xFF.toByte() && (whole[1].toInt() and 0xE0) == 0xE0))
            if (Streaming.formatOf(mp3) != null) {
                val part = bytes(Streaming.startingAt(mp3, 10_000), "$what as MP3 from 10s")
                assertTrue("$what: MP3 from 10s is shorter (${part.size} of ${whole.size})", part.size < whole.size * 0.85)
            }
            val opus = Streaming.forCastOpus(plain, s.suffix)
            if (opus != plain) {
                val all = opusSeconds(bytes(opus, "$what as Opus 320"))
                val from = opusSeconds(bytes(Streaming.startingAt(opus, 10_000), "$what as Opus 320 from 10s"))
                assertEquals("$what: Opus 320 from 10s", all - 10, from, 1.5)
            }
            // FLAC of files a TV can't play: Emby and Jellyfin always can; Navidrome needs a "flac" transcoding set up.
            if (kind.embyLike && app.siansa.playback.CastOriginals.convertsToFlac(s.suffix)) {
                val flac = Uri.parse(app.siansa.playback.CastOriginals.flacUrl(plain.toString())!!)
                val all = bytes(flac, "$what as FLAC")
                assertTrue("$what: FLAC", all.startsWith("fLaC"))
                val part = bytes(Streaming.startingAt(flac, 10_000), "$what as FLAC from 10s")
                assertTrue("$what: FLAC from 10s is shorter", part.startsWith("fLaC") && part.size < all.size * 0.85)
            }
        }
    }
}
