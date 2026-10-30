package app.siansa.net

import app.siansa.data.AppSettings
import app.siansa.data.AppJson
import app.siansa.data.Song
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Jellyfin as a music source (issue #703): the Emby client in its Jellyfin mode, with Jellyfin's own calls. */
class JellyfinApiTest {
    private data class Seen(val method: String, val path: String, val query: Map<String, String>, val auth: String?, val embyHeaders: Boolean, val body: String)

    /** Answers like a Jellyfin server; [answer] gets each request after the login. */
    private class FakeJellyfin(val answer: (Seen) -> Pair<Int, String>) {
        val seen = mutableListOf<Seen>()
        val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(Interceptor { respond(it.request()) }).build()

        private fun respond(request: Request): Response {
            val body = request.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
            val query = (0 until request.url.querySize).associate { request.url.queryParameterName(it) to request.url.queryParameterValue(it).orEmpty() }
            val one = Seen(
                request.method, request.url.encodedPath, query, request.header("Authorization"),
                request.header("X-Emby-Authorization") != null || request.header("X-Emby-Token") != null, body,
            )
            seen += one
            val (code, text) = when (one.path) {
                "/Users/AuthenticateByName" -> 200 to """{"AccessToken":"jt1","User":{"Id":"u1"}}"""
                else -> answer(one)
            }
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(text.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private val settings = AppSettings(navidromeUrl = "http://jelly.local:8096", navidromeUser = "max", navidromePassword = "sesame", sourceKind = SourceKind.JELLYFIN)
    private fun api(server: FakeJellyfin) = EmbyApi(server.client, { settings }, jellyfin = true)

    private val songJson = """{"Id":"s1","Name":"Archangel","Type":"Audio","Album":"Untrue","AlbumId":"a1","Artists":["Burial"],
        "ArtistItems":[{"Id":"ar1","Name":"Burial"}],"RunTimeTicks":2410000000,"IndexNumber":2,"UserData":{"IsFavorite":false}}"""

    @Test fun logsInWithTheStandardHeaderOnly() = runBlocking {
        val server = FakeJellyfin { 200 to """{"Items":[$songJson]}""" }
        val a = api(server)
        assertEquals(SourceKind.JELLYFIN, a.kind)
        a.starredSongs()
        val login = server.seen.first { it.path == "/Users/AuthenticateByName" }
        assertTrue(login.auth!!.startsWith("MediaBrowser Client=\"Siansa\""))
        assertFalse(login.auth!!.contains("Token="))
        val list = server.seen.last()
        assertTrue(list.auth!!.startsWith("MediaBrowser ") && list.auth!!.contains("Token=\"jt1\""))
        // Jellyfin 12.0 dropped Emby's headers.
        assertFalse(server.seen.any { it.embyHeaders })
    }

    @Test fun theUsersItemsAreAskedTheJellyfinWay() = runBlocking {
        val server = FakeJellyfin { seen ->
            when (seen.path) {
                "/UserViews" -> 200 to """{"Items":[{"Id":"v1","Name":"Music","CollectionType":"music"},{"Id":"v2","Name":"Films","CollectionType":"movies"}]}"""
                "/Items/s1" -> 200 to songJson
                else -> 200 to """{"Items":[$songJson]}"""
            }
        }
        val a = api(server)
        a.songsPage(0, 50, "lib1")
        val page = server.seen.last()
        assertEquals("/Items", page.path)
        assertEquals("u1", page.query["userId"])
        assertEquals("lib1", page.query["ParentId"])
        assertEquals("Audio", page.query["IncludeItemTypes"])
        assertEquals(listOf("Music"), a.musicFolders().map { it.name })
        assertEquals("u1", server.seen.last().query["userId"])
        assertEquals("Archangel", a.song("s1").title)
        assertEquals("/Items/s1", server.seen.last().path)
        assertEquals("u1", server.seen.last().query["userId"])
    }

    @Test fun addressesCarryApiKey() = runBlocking {
        val server = FakeJellyfin { 200 to """{"Items":[]}""" }
        val a = api(server)
        a.starredSongs()
        val stream = a.streamUrl("s1")
        assertTrue(stream, stream.startsWith("http://jelly.local:8096/Audio/s1/stream?") && stream.contains("ApiKey=jt1"))
        assertFalse(stream.contains("api_key"))
        assertTrue(a.coverUrl("al-a1", 300).contains("/Items/a1/Images/Primary?") && a.coverUrl("a1", 300).contains("ApiKey=jt1"))
    }

    @Test fun favouritesAndPlaysUseTheUserCalls() = runBlocking {
        val server = FakeJellyfin { 200 to "" }
        val a = api(server)
        a.star("s1")
        assertEquals("POST", server.seen.last().method)
        assertEquals("/UserFavoriteItems/s1", server.seen.last().path)
        assertEquals("u1", server.seen.last().query["userId"])
        a.unstar("s1")
        assertEquals("DELETE", server.seen.last().method)
        a.scrobble("s1", submission = true, timeMs = 1_700_000_000_000L)
        val played = server.seen.last()
        assertEquals("/UserPlayedItems/s1", played.path)
        assertEquals("2023-11-14T22:13:20Z", played.query["datePlayed"])
        // Now playing: the song is its own media source.
        a.scrobble("s1", submission = false, timeMs = null)
        val playing = AppJson.parseToJsonElement(server.seen.last().body) as JsonObject
        assertEquals("/Sessions/Playing", server.seen.last().path)
        assertEquals("s1", playing.str("MediaSourceId"))
    }

    @Test fun lyricsComeFromTheirOwnCall() = runBlocking {
        val server = FakeJellyfin { seen ->
            if (seen.path == "/Audio/s1/Lyrics") 200 to """{"Metadata":{},"Lyrics":[{"Text":"First line","Start":0},{"Text":"Second line","Start":52000000}]}"""
            else 404 to ""
        }
        val a = api(server)
        val lyrics = a.lyrics(Song(id = "s1", title = "Archangel"))!!
        assertTrue(lyrics.synced)
        assertEquals(listOf(0L, 5200L), lyrics.lines.map { it.startMs })
        assertEquals("Second line", lyrics.lines[1].text)
        // A song without lyrics: Jellyfin answers 404.
        assertNull(a.lyrics(Song(id = "s2", title = "Ghost Hardware")))
    }

    @Test fun plainLyricsHaveNoTimes() {
        val o = AppJson.parseToJsonElement("""{"Lyrics":[{"Text":"One"},{"Text":"Two"}]}""") as JsonObject
        val lyrics = EmbyApi.readJellyfinLyrics(o)!!
        assertFalse(lyrics.synced)
        assertEquals(listOf<Long?>(null, null), lyrics.lines.map { it.startMs })
        assertNull(EmbyApi.readJellyfinLyrics(AppJson.parseToJsonElement("""{"Lyrics":[]}""") as JsonObject))
    }

    @Test fun playlistsAreMadeAndRenamedTheJellyfinWay() = runBlocking {
        val server = FakeJellyfin { seen -> if (seen.path == "/Playlists" && seen.method == "POST") 200 to """{"Id":"p9"}""" else 200 to "" }
        val a = api(server)
        assertEquals("p9", a.createPlaylist("Night", listOf("s1", "s2"))?.id)
        val made = AppJson.parseToJsonElement(server.seen.last().body) as JsonObject
        assertEquals("Night", made.str("Name"))
        assertEquals("u1", made.str("UserId"))
        assertEquals(2, (made["Ids"] as kotlinx.serialization.json.JsonArray).size)
        a.renamePlaylist("p9", "Late night")
        assertEquals("/Playlists/p9", server.seen.last().path)
        assertEquals("Late night", (AppJson.parseToJsonElement(server.seen.last().body) as JsonObject).str("Name"))
    }

    @Test fun theConnectionTestNamesJellyfin() = runBlocking {
        val server = FakeJellyfin { seen -> if (seen.path == "/System/Info/Public") 200 to """{"ProductName":"Jellyfin Server","Version":"12.0.1"}""" else 200 to "" }
        assertEquals("Jellyfin Server 12.0.1", api(server).ping())
    }

    @Test fun behindAProxyLoginTheProxyKeepsTheAuthorizationHeader() = runBlocking {
        val server = FakeJellyfin { 200 to """{"Items":[]}""" }
        val proxied = settings.copy(proxyUser = "gate", proxyPassword = "pw")
        EmbyApi(server.client, { proxied }, jellyfin = true).starredSongs()
        val list = server.seen.last()
        // Left free for the proxy's Basic login (added by the app's HTTP client); the token goes as ApiKey instead.
        assertNull(list.auth)
        assertTrue(list.embyHeaders)
        assertEquals("jt1", list.query["ApiKey"])
        assertEquals("u1", list.query["userId"])
    }
}
