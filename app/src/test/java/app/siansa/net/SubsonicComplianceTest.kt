package app.siansa.net

import app.siansa.data.Album
import app.siansa.data.AppSettings
import app.siansa.data.Song
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Navidrome and OpenSubsonic compliance (issue #206), against a pretend server. */
class SubsonicComplianceTest {
    /** One request as the server saw it: endpoint, method and all parameters (query or form). */
    data class Seen(val endpoint: String, val method: String, val params: List<Pair<String, String>>) {
        fun all(name: String) = params.filter { it.first == name }.map { it.second }
        fun one(name: String) = all(name).firstOrNull()
    }

    private class FakeServer(val answer: (Seen) -> String) {
        val seen = mutableListOf<Seen>()
        val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain -> respond(chain.request()) }).build()

        private fun respond(request: Request): Response {
            val endpoint = request.url.pathSegments.last()
            val raw = if (request.method == "POST") Buffer().also { request.body!!.writeTo(it) }.readUtf8() else request.url.encodedQuery.orEmpty()
            val params = raw.split('&').filter { it.contains('=') }.map {
                val (k, v) = it.split('=', limit = 2)
                java.net.URLDecoder.decode(k, "UTF-8") to java.net.URLDecoder.decode(v, "UTF-8")
            }
            val one = Seen(endpoint, request.method, params)
            seen += one
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(("""{"subsonic-response":""" + answer(one) + "}").toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private val settings = AppSettings(navidromeUrl = "http://nd.local:4533", navidromeUser = "max", navidromePassword = "sesame")

    private fun ok(body: String = "") = """{"status":"ok","version":"1.16.1","openSubsonic":true${if (body.isEmpty()) "" else ",$body"}}"""
    private fun failed(code: Int, message: String) = """{"status":"failed","version":"1.16.1","error":{"code":$code,"message":"$message"}}"""
    private fun extensions(vararg e: Pair<String, String>) =
        ok(""""openSubsonicExtensions":[${e.joinToString(",") { """{"name":"${it.first}","versions":[${it.second}]}""" }}]""")

    private fun api(server: FakeServer) = SubsonicApi(server.client) { settings }

    @Test fun extensionsAreAskedOnceAndRead() = runBlocking {
        val server = FakeServer { if (it.endpoint == "getOpenSubsonicExtensions") extensions("formPost" to "1", "songLyrics" to "1,2") else ok() }
        val api = api(server)
        assertEquals(mapOf("formPost" to listOf(1), "songLyrics" to listOf(1, 2)), api.extensions())
        assertTrue(api.supports("songLyrics", 2))
        assertFalse(api.supports("apiKeyAuthentication"))
        assertEquals(1, server.seen.count { it.endpoint == "getOpenSubsonicExtensions" })
    }

    @Test fun lyricsOnlyUseWhatTheServerHas() = runBlocking {
        val song = Song(id = "s1", title = "T", artist = "A")
        for ((listed, expected) in listOf("" to null, "1" to "plain", "1,2" to "enhanced")) {
            val server = FakeServer {
                when (it.endpoint) {
                    "getOpenSubsonicExtensions" -> if (listed.isEmpty()) extensions() else extensions("songLyrics" to listed)
                    else -> ok()
                }
            }
            api(server).lyrics(song)
            val structured = server.seen.filter { it.endpoint == "getLyricsBySongId" }
            when (expected) {
                null -> assertTrue("no structured lyrics without songLyrics", structured.isEmpty())
                "plain" -> assertEquals(listOf<String?>(null), structured.map { it.one("enhanced") })
                else -> assertEquals(listOf<String?>("true"), structured.map { it.one("enhanced") })
            }
            assertTrue(server.seen.any { it.endpoint == "getLyrics" })
        }
    }

    @Test fun aNewSaltForEveryCall() = runBlocking {
        val server = FakeServer { if (it.endpoint == "getOpenSubsonicExtensions") extensions() else ok() }
        val api = api(server)
        api.ping()
        api.ping()
        val salts = server.seen.filter { it.endpoint == "ping" }.map { it.one("s")!! }
        assertNotEquals(salts[0], salts[1])
        assertTrue(salts.all { it.length >= 6 })
        // Each token is md5(password + its own salt), lowercase hex.
        server.seen.filter { it.endpoint == "ping" }.forEach { seen ->
            val expected = java.security.MessageDigest.getInstance("MD5").digest(("sesame" + seen.one("s")).toByteArray())
                .joinToString("") { "%02x".format(it) }
            assertEquals(expected, seen.one("t"))
        }
    }

    @Test fun songAndCoverAddressesStayTheSame() {
        val api = api(FakeServer { ok() })
        assertEquals(api.coverUrl("al-1", 300), api.coverUrl("al-1", 300))
        assertEquals(api.streamUrl("s1"), api.streamUrl("s1"))
    }

    @Test fun tokenRefusedThenThePasswordIsSent() = runBlocking {
        val server = FakeServer { seen ->
            when {
                seen.endpoint == "getOpenSubsonicExtensions" -> extensions()
                seen.one("t") != null -> failed(41, "Token authentication not supported for LDAP users")
                else -> ok()
            }
        }
        val api = api(server)
        api.ping()
        val pings = server.seen.filter { it.endpoint == "ping" }
        assertEquals(2, pings.size)
        assertEquals("enc:" + "sesame".toByteArray().joinToString("") { "%02x".format(it) }, pings[1].one("p"))
        assertEquals(null, pings[1].one("t"))
        // From then on, straight away, also in song addresses.
        api.ping()
        assertEquals(3, server.seen.count { it.endpoint == "ping" })
        assertTrue(api.streamUrl("s1").contains("p=enc%3A") || api.streamUrl("s1").contains("p=enc:"))
    }

    @Test fun longPlaylistsGoAsAFormPost() = runBlocking {
        val ids = (1..30).map { "song$it" }
        for (formPost in listOf(true, false)) {
            val server = FakeServer {
                when (it.endpoint) {
                    "getOpenSubsonicExtensions" -> if (formPost) extensions("formPost" to "1") else extensions()
                    else -> ok()
                }
            }
            api(server).createPlaylist("Long", ids)
            val create = server.seen.single { it.endpoint == "createPlaylist" }
            assertEquals(if (formPost) "POST" else "GET", create.method)
            assertEquals(ids, create.all("songId"))
            assertEquals("max", create.one("u"))
        }
        // A short one stays a plain GET.
        val server = FakeServer { if (it.endpoint == "getOpenSubsonicExtensions") extensions("formPost" to "1") else ok() }
        api(server).createPlaylist("Short", ids.take(3))
        assertEquals("GET", server.seen.single { it.endpoint == "createPlaylist" }.method)
    }

    @Test fun errorCodesBecomePlainMessages() = runBlocking {
        val server = FakeServer { if (it.endpoint == "getOpenSubsonicExtensions") extensions() else failed(70, "Song not found") }
        try {
            api(server).song("gone")
            fail("expected an error")
        } catch (e: ApiException) {
            assertEquals(70, e.code)
            assertEquals("Not found on Navidrome. It may have been removed.", e.message)
        }
        assertEquals("Wrong username or password for Navidrome.", SubsonicErrors.message(40, "Wrong username or password"))
        assertEquals("Something odd", SubsonicErrors.message(0, "Something odd"))
        assertEquals("Navidrome refused the request", SubsonicErrors.message(null, null))
    }

    @Test fun listsKeepToTheChosenLibraries() = runBlocking {
        val server = FakeServer { seen ->
            when (seen.endpoint) {
                "getOpenSubsonicExtensions" -> extensions()
                "getStarred2" -> ok(""""starred2":{"song":[{"id":"x${seen.one("musicFolderId")}","title":"t"},{"id":"both","title":"t"}]}""")
                else -> ok()
            }
        }
        val api = api(server).apply { chosenFolders = { setOf("1", "2") } }
        val songs = api.starredSongs()
        assertEquals(listOf("1", "2"), server.seen.filter { it.endpoint == "getStarred2" }.map { it.one("musicFolderId") })
        assertEquals(listOf("x1", "both", "x2"), songs.map { it.id })

        // None chosen: all libraries, no musicFolderId.
        val all = FakeServer { if (it.endpoint == "getOpenSubsonicExtensions") extensions() else ok() }
        api(all).starredSongs()
        assertEquals(listOf<String?>(null), all.seen.filter { it.endpoint == "getStarred2" }.map { it.one("musicFolderId") })
    }

    /** Favourite artists (issue #397): starred by artistId, and read from getStarred2 in each chosen library. */
    @Test fun favouriteArtistsUseTheArtistStar() = runBlocking {
        val server = FakeServer { seen ->
            when (seen.endpoint) {
                "getOpenSubsonicExtensions" -> extensions()
                "getStarred2" -> ok(""""starred2":{"artist":[{"id":"ar${seen.one("musicFolderId")}","name":"N","albumCount":3},{"id":"both","name":"B"}]}""")
                else -> ok()
            }
        }
        val api = api(server).apply { chosenFolders = { setOf("1", "2") } }
        api.starArtist("ar9")
        api.unstarArtist("ar9")
        assertEquals("ar9", server.seen.single { it.endpoint == "star" }.one("artistId"))
        assertEquals(null, server.seen.single { it.endpoint == "star" }.one("id"))
        assertEquals("ar9", server.seen.single { it.endpoint == "unstar" }.one("artistId"))
        val artists = api.starredArtists()
        assertEquals(listOf("ar1", "both", "ar2"), artists.map { it.id })
        assertEquals(3, artists.first().albumCount)
    }

    @Test fun albumsFromSeveralLibrariesInOneOrder() {
        val a = Album(id = "a", name = "B side", created = "2026-01-01")
        val b = Album(id = "b", name = "a side", created = "2026-03-01")
        assertEquals(listOf("b", "a"), SubsonicApi.mergeAlbumLists("newest", listOf(a, b)).map { it.id })
        assertEquals(listOf("b", "a"), SubsonicApi.mergeAlbumLists("alphabeticalByName", listOf(a, b)).map { it.id })
    }

    /** Navidrome's sonic similarity (OpenSubsonic sonicSimilarity), issue #208. */
    @Test fun sonicMatchesFromNavidrome() = runBlocking {
        val server = FakeServer { seen ->
            when (seen.endpoint) {
                "getSonicSimilarTracks" -> ok(""""sonicMatch":[{"entry":{"id":"s2","title":"Two","artist":"A"},"similarity":0.95},{"entry":{"id":"s3","title":"Three"},"similarity":0.5}]""")
                "findSonicPath" -> ok(""""sonicMatch":[{"entry":{"id":"a","title":"A"},"similarity":1.0},{"entry":{"id":"m","title":"M"},"similarity":0.4},{"entry":{"id":"b","title":"B"},"similarity":0.0}]""")
                else -> extensions("sonicSimilarity" to "1")
            }
        }
        val api = api(server)
        val similar = api.sonicSimilar("s1", 2)
        assertEquals(listOf("s2" to 0.95, "s3" to 0.5), similar.map { it.first.id to it.second })
        assertEquals("2", server.seen.single { it.endpoint == "getSonicSimilarTracks" }.one("count"))
        assertEquals(listOf("a", "m", "b"), api.sonicPath("a", "b", 3).map { it.id })
        val path = server.seen.single { it.endpoint == "findSonicPath" }
        assertEquals(listOf("a", "b", "3"), listOf(path.one("startSongId"), path.one("endSongId"), path.one("count")))
    }
}
