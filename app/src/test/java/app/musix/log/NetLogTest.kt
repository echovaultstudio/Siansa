package app.musix.log

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #180: every request logged in full, without its secrets. */
class NetLogTest {
    private val request = Request.Builder()
        .url("http://192.168.1.106:4533/rest/stream?id=59oa&u=max&t=abc123token&s=salt99&v=1.16.1&c=Musix&f=json&maxBitRate=0")
        .header("Authorization", "Basic bWF4OnNlY3JldA==")
        .header("Range", "bytes=0-")
        .header("Cookie", "audiomuse_jwt=eyJhbGciOi")
        .header("X-Musix-Animation", "1")
        .build()

    private fun response(code: Int = 206, length: String? = "68500000") = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("Partial Content")
        .header("Content-Type", "audio/flac")
        .header("Content-Range", "bytes 0-68499999/68500000")
        .apply { length?.let { header("Content-Length", it) } }
        .body("".toResponseBody("audio/flac".toMediaType()))
        .build()

    @Test fun showsWhereItWentAndWhatCameBack() {
        val text = NetLog.describe(request, response(), "192.168.1.106:4533", "http/1.1", 326)
        assertTrue(text, text.startsWith("→ GET http://192.168.1.106:4533/rest/stream?id=59oa"))
        assertTrue(text, text.contains("(to 192.168.1.106:4533 · http/1.1)"))
        assertTrue(text, text.contains("v=1.16.1") && text.contains("maxBitRate=0"))
        assertTrue(text, text.contains("Range: bytes=0-"))
        assertTrue(text, text.contains("← 206 Partial Content · audio/flac · 65.3 MB · range bytes 0-68499999/68500000 · in 326ms"))
    }

    @Test fun leavesOutSecrets() {
        val text = NetLog.describe(request, response(), "192.168.1.106:4533", null, 1)
        for (secret in listOf("abc123token", "salt99", "bWF4OnNlY3JldA", "eyJhbGciOi", "u=max")) {
            assertFalse("$secret in: $text", text.contains(secret))
        }
        assertTrue(text, text.contains("Authorization: •••") && text.contains("Cookie: •••"))
        assertFalse("Musix's own markers aren't sent anywhere: $text", text.contains("X-Musix"))
    }

    @Test fun sizeNotSentIsSaid() {
        assertTrue(NetLog.describe(request, response(200, null), null, null, 5).contains("size not sent"))
    }

    @Test fun requestBodySizeIsLogged() {
        val post = Request.Builder().url("https://scrobble.example/apis/listenbrainz/1/submit-listens?token=xyz")
            .post("{\"listen_type\":\"single\"}".toRequestBody("application/json".toMediaType())).build()
        val text = NetLog.sent(post, "10.0.0.5:443", "h2")
        assertTrue(text, text.contains("body 24 B"))
        assertFalse(text, text.contains("xyz"))
    }

    @Test fun sizes() {
        assertEquals("512 B", NetLog.size(512))
        assertEquals("1.5 KB", NetLog.size(1536))
        assertEquals("2.0 MB", NetLog.size(2 * 1_048_576))
    }

    @Test fun moreSecretParametersAreRedacted() {
        val text = AppLog.redact("https://ws.audioscrobbler.com/2.0/?method=track.scrobble&sk=SESSION&api_sig=SIG&api_key=KEY")
        assertFalse(text, text.contains("SESSION") || text.contains("SIG") || text.contains("KEY"))
        assertTrue(text, text.contains("method=track.scrobble"))
    }
}
