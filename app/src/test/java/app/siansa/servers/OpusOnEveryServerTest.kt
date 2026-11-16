package app.siansa.servers

import android.app.Application
import android.net.Uri
import app.siansa.net.SourceKind
import app.siansa.playback.StreamStats
import app.siansa.playback.Streaming
import app.siansa.playback.TvSeek
import app.siansa.playback.goesThroughCache
import app.siansa.playback.songCacheKey
import app.siansa.playback.streamSongId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Opus on every server (issue #1204): Emby and Jellyfin songs streamed as their original file whatever the quality
 * chosen, as only Navidrome's `/rest/stream` was converted. Each server's own address for a conversion, and for one
 * starting part-way in (for seeking).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OpusOnEveryServerTest {
    private fun original(server: TestServer, login: Int = 1) = Uri.parse(server.source(login).streamUrl(server.songId))

    @Test fun everyServerConvertsToOpus() {
        EveryServer.all.forEach { server ->
            val plain = original(server)
            assertNull("${server.name}: the original", Streaming.formatOf(plain))
            assertEquals(server.name, "original", Streaming.describe(plain))
            val opus = Streaming.rewrite(plain, 192)
            assertEquals(server.name, "opus", Streaming.formatOf(opus))
            assertEquals(server.name, 192, Streaming.kbpsOf(opus))
            assertEquals(server.name, "opus 192", Streaming.describe(opus))
            // Still the same song, for the player, downloads and the song cache.
            assertEquals(server.name, server.songId, streamSongId(opus))
            assertEquals(server.name, "song:${server.songId}:opus:192", songCacheKey(opus.toString()))
            assertEquals(server.name, "opus 192", StreamStats.described(opus.toString()))
            assertTrue(server.name, TvSeek.isConversion(opus.toString()))
            assertFalse(server.name, TvSeek.isConversion(plain.toString()))
            // A conversion streams straight from the server, never through the song cache's store.
            assertFalse(server.name, goesThroughCache(Streaming.formatOf(opus), storeInUse = true))
            assertTrue(server.name, goesThroughCache(Streaming.formatOf(plain), storeInUse = true))
            // 0 is the original.
            assertEquals(server.name, plain, Streaming.rewrite(plain, 0))
        }
    }

    @Test fun embyAndJellyfinAskTheirOwnWay() {
        EveryServer.all.filter { it.kind != SourceKind.OPENSUBSONIC }.forEach { server ->
            val opus = Streaming.rewrite(original(server), 256)
            assertEquals(server.name, "false", opus.getQueryParameter("Static"))
            assertEquals("${server.name}: one of each", 1, opus.getQueryParameters("Static").size)
            assertEquals(server.name, "ogg", opus.getQueryParameter("Container"))
            assertEquals(server.name, "opus", opus.getQueryParameter("AudioCodec"))
            assertEquals(server.name, "256000", opus.getQueryParameter("AudioBitRate"))
            // Their login stays in the address.
            assertTrue(server.name, opus.queryParameterNames.any { it == "api_key" || it == "ApiKey" })
            // Each start has its own play session: without one, both servers hand out their first conversion.
            val later = Streaming.rewrite(Streaming.startingAt(original(server), 90_500), 256)
            assertNotEquals(server.name, opus.getQueryParameter("PlaySessionId"), later.getQueryParameter("PlaySessionId"))
            assertEquals("${server.name}: the same stream is the same session", later.getQueryParameter("PlaySessionId"),
                Streaming.rewrite(Streaming.startingAt(original(server, login = 2), 90_500), 256).getQueryParameter("PlaySessionId"))
            // The start can also be set after converting (a seek on a TV): the session follows it.
            assertEquals(server.name, later.getQueryParameter("PlaySessionId"), Streaming.startingAt(opus, 90_500).getQueryParameter("PlaySessionId"))
            assertEquals(server.name, opus.getQueryParameter("PlaySessionId"), Streaming.startingAt(later, 0).getQueryParameter("PlaySessionId"))
            // Converting again (another quality, Opus when the phone can't play the file) leaves one of each.
            val again = Streaming.rewrite(opus, 128)
            listOf("Static", "Container", "AudioCodec", "AudioBitRate", "PlaySessionId").forEach {
                assertEquals("${server.name} $it", 1, again.getQueryParameters(it).size)
            }
            assertEquals(server.name, 128, Streaming.kbpsOf(again))
        }
    }

    @Test fun aSeekStartsTheConversionPartWayInOnEveryServer() {
        EveryServer.all.forEach { server ->
            val plain = original(server)
            assertTrue(server.name, Streaming.canStartAt(plain))
            val at = Streaming.startingAt(plain, 90_500)
            assertEquals("${server.name}: from the whole second", 90_000L, Streaming.offsetMs(at))
            if (server.kind == SourceKind.OPENSUBSONIC) assertEquals("90", at.getQueryParameter("timeOffset"))
            else assertEquals(server.name, "900000000", at.getQueryParameter("StartTimeTicks"))
            // Converted, it still starts there; back at 0 it starts at the beginning.
            assertEquals(server.name, 90_000L, Streaming.offsetMs(Streaming.rewrite(at, 192)))
            assertEquals(server.name, 0L, Streaming.offsetMs(Streaming.startingAt(at, 0)))
            assertEquals(server.name, 1, Streaming.startingAt(at, 120_000).let { it.getQueryParameters("timeOffset").size + it.getQueryParameters("StartTimeTicks").size })
        }
    }

    /** A TV or speaker gets a real MP3, Opus or FLAC from every server, also from part-way in (a seek on the TV). */
    @Test fun everyServerConvertsForATvOrSpeaker() {
        EveryServer.all.forEach { server ->
            val plain = original(server)
            val mp3 = Streaming.forCast(plain, "flac")
            assertEquals(server.name, "mp3 320", Streaming.describe(mp3))
            val opus = Streaming.forCastOpus(plain, "flac")
            assertEquals(server.name, "opus 320", Streaming.describe(opus))
            assertTrue(server.name, TvSeek.isConversion(mp3.toString()) && TvSeek.isConversion(opus.toString()))
            val flac = app.siansa.playback.CastOriginals.flacUrl(plain.toString())!!
            assertEquals(server.name, "flac", Streaming.formatOf(Uri.parse(flac)))
            assertTrue(server.name, TvSeek.isConversion(flac))
            // From 90 seconds in, each still starts there.
            listOf(mp3, opus, Uri.parse(flac)).forEach { assertEquals(server.name, 90_000L, Streaming.offsetMs(Streaming.startingAt(it, 90_000))) }
            assertEquals(server.name, server.songId, streamSongId(mp3))
        }
    }

    /**
     * A file in the format already goes to the TV as it is, and the TV seeks inside it: asked to convert one, Navidrome
     * sends it from its beginning wherever it's asked to start, and Emby answers HTTP 500.
     */
    @Test fun aFileInTheFormatAlreadyGoesAsItIs() {
        EveryServer.all.forEach { server ->
            val plain = original(server)
            assertEquals("${server.name}: an MP3 as MP3", plain, Streaming.forCast(plain, "mp3"))
            assertFalse(server.name, TvSeek.isConversion(Streaming.forCast(plain, "mp3").toString()))
            assertEquals("${server.name}: an Opus file isn't converted to Opus", plain, Streaming.forCastOpus(plain, "opus"))
            // Emby and Jellyfin call an Opus file "ogg"; on Navidrome that's Ogg Vorbis, which it converts.
            assertEquals(server.name, server.kind != SourceKind.OPENSUBSONIC, Streaming.forCastOpus(plain, "ogg") == plain)
            assertEquals("${server.name}: but it converts to MP3", "mp3", Streaming.formatOf(Streaming.forCast(plain, "opus")))
            if (server.kind != SourceKind.OPENSUBSONIC) assertNull("${server.name}: FLAC has no bitrate", Uri.parse(app.siansa.playback.CastOriginals.flacUrl(plain.toString())).getQueryParameter("AudioBitRate"))
        }
    }

    /**
     * A file that is Opus already plays as it is: asked for Opus from one, Navidrome sends it from its beginning
     * wherever it was asked to start, and Emby 4.8 answers "Error starting ffmpeg".
     */
    @Test fun anOpusFilePlaysAsItIsOnEveryServer() {
        EveryServer.all.forEach { server ->
            assertTrue(server.name, Streaming.keepsOriginal(server.kind, "opus"))
            assertTrue(server.name, Streaming.keepsOriginal(server.kind, ".OPUS"))
            assertFalse(server.name, Streaming.keepsOriginal(server.kind, "flac"))
            assertFalse(server.name, Streaming.keepsOriginal(server.kind, "mp3"))
            assertFalse(server.name, Streaming.keepsOriginal(server.kind, null))
            // Emby and Jellyfin call an Opus file "ogg"; on Navidrome that's Ogg Vorbis, which it converts.
            assertEquals(server.name, server.kind != SourceKind.OPENSUBSONIC, Streaming.keepsOriginal(server.kind, "ogg"))
        }
    }
}
