package app.siansa.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Radio links as people paste them (issue #353): streams, .m3u and .pls playlists, HLS and feeds. */
class StreamLinksTest {
    @Test fun anM3uListsItsStreamsInOrder() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Radio Paradise - Main Mix
            https://stream.radioparadise.com/aac-320

            #EXTINF:-1,Backup
            http://backup.example.com:8000/live.mp3
        """.trimIndent()
        assertEquals(listOf("https://stream.radioparadise.com/aac-320", "http://backup.example.com:8000/live.mp3"), StreamLinks.parseM3u(m3u))
        assertEquals("Radio Paradise - Main Mix", StreamLinks.m3uTitle(m3u))
    }

    @Test fun anM3uWithoutHeaderAndRelativeLinesIsReadAgainstItsAddress() {
        val m3u = "﻿live/stream.mp3\r\nhttps://other.example.org/a.aac\r\n"
        assertEquals(
            listOf("https://radio.example.com/lists/live/stream.mp3", "https://other.example.org/a.aac"),
            StreamLinks.parseM3u(m3u, "https://radio.example.com/lists/station.m3u"),
        )
        assertNull(StreamLinks.m3uTitle(m3u))
    }

    @Test fun onlyWebAddressesAreTaken() {
        val m3u = "#EXTM3U\nrtsp://example.com/x\nfile:///sdcard/a.mp3\nhttps://ok.example.com/s\n<html>\n"
        assertEquals(listOf("https://ok.example.com/s"), StreamLinks.parseM3u(m3u, "https://example.com/a.m3u"))
    }

    @Test fun aPlsListsItsFilesByNumber() {
        val pls = """
            [playlist]
            NumberOfEntries=2
            File2=http://two.example.com/stream
            Title2=Second
            File1=http://one.example.com:8000/
            Title1=SomaFM: Groove Salad
            Length1=-1
            Version=2
        """.trimIndent()
        assertEquals(listOf("http://one.example.com:8000/", "http://two.example.com/stream"), StreamLinks.parsePls(pls))
        assertEquals("SomaFM: Groove Salad", StreamLinks.plsTitle(pls))
    }

    @Test fun anHlsPlaylistIsAStreamOfItsOwn() {
        val hls = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-STREAM-INF:BANDWIDTH=128000\nchunklist.m3u8\n"
        assertTrue(StreamLinks.isHls(hls))
        assertEquals(StreamLinks.Type.HLS, StreamLinks.typeOf("https://a.example.com/live.m3u8", "application/vnd.apple.mpegurl", hls))
        assertFalse(StreamLinks.isHls("#EXTM3U\nhttps://a.example.com/s\n"))
    }

    @Test fun whatALinkIsComesFromItsBodyThenItsTypeThenItsAddress() {
        assertEquals(StreamLinks.Type.STREAM, StreamLinks.typeOf("https://ice.example.com/radio", "audio/mpeg", ""))
        assertEquals(StreamLinks.Type.STREAM, StreamLinks.typeOf("https://ice.example.com/radio", "audio/aacp", ""))
        assertEquals(StreamLinks.Type.STREAM, StreamLinks.typeOf("https://ice.example.com/radio", "application/ogg", ""))
        assertEquals(StreamLinks.Type.PLS, StreamLinks.typeOf("https://x.example.com/listen", "text/plain", "[playlist]\nFile1=http://a.example.com/\n"))
        assertEquals(StreamLinks.Type.M3U, StreamLinks.typeOf("https://x.example.com/listen", "text/plain", "#EXTM3U\nhttp://a.example.com/\n"))
        assertEquals(StreamLinks.Type.M3U, StreamLinks.typeOf("https://x.example.com/listen.m3u", null, ""))
        assertEquals(StreamLinks.Type.PLS, StreamLinks.typeOf("https://x.example.com/listen.pls?x=1", "application/octet-stream", ""))
        assertEquals(StreamLinks.Type.FEED, StreamLinks.typeOf("https://feeds.example.com/show", "text/xml", "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel>"))
        assertEquals(StreamLinks.Type.UNKNOWN, StreamLinks.typeOf("https://example.com/", "text/html", "<!doctype html><html><body>Hello</body></html>"))
        // No type, and a body that isn't text: audio from a server that doesn't say so.
        assertEquals(StreamLinks.Type.STREAM, StreamLinks.typeOf("https://ice.example.com/radio", null, "ID3\u0004\u0000\u0000\u0000"))
    }

    @Test fun onlyPlaylistsAndFeedsAreRead() {
        assertFalse(StreamLinks.worthReading("https://ice.example.com/radio", "audio/mpeg"))
        assertTrue(StreamLinks.worthReading("https://ice.example.com/radio.pls", "audio/mpeg"))
        assertTrue(StreamLinks.worthReading("https://feeds.example.com/show", "application/rss+xml; charset=utf-8"))
        assertTrue(StreamLinks.worthReading("https://x.example.com/a", "audio/x-mpegurl"))
    }

    @Test fun linksAreCleaned() {
        assertEquals("https://stream.example.com/live", StreamLinks.clean("  https://stream.example.com/live "))
        assertEquals("https://stream.example.com/live", StreamLinks.clean("stream.example.com/live"))
        assertEquals("http://192.168.1.5:8000/radio", StreamLinks.clean("http://192.168.1.5:8000/radio"))
        assertNull(StreamLinks.clean(""))
        assertNull(StreamLinks.clean("not a link"))
        assertNull(StreamLinks.clean("hello"))
    }
}
