package app.siansa.net

import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import app.siansa.playback.LoudnessSource
import app.siansa.playback.PluginProfiles
import app.siansa.playback.ProfileStore
import app.siansa.playback.measurer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Siansa Beats asked directly (Siansa Dev, the owner, 10 Nov): its v1 API, the Test and the DJ check. */
class BeatsApiTest {
    private fun caps(api: Int = 1, grids: Int = 1200, loudness: Int = 900, server: String = "navidrome") = BeatsApi.Capabilities.parse(
        AppJson.parseToJsonElement(
            """{"service":"siansa-beats","version":"0.20.0","api":$api,"music_server":{"kind":"$server","set_up":true},
               "features":{"beat_grids":{"available":true,"songs":$grids},"loudness":{"available":true,"songs":$loudness},
               "genres":{"available":true,"styles":80},"similar":{"available":false},"something_new":{"available":true}}}""",
        ) as JsonObject,
    )

    @Test fun thePluginsPathsAreSiansaBeatsOwn() {
        assertEquals("/api/v1/profiles?ids=a,b", BeatsApi.fromPlugin(PluginProfiles.PATH + "?ids=a,b"))
        assertEquals("/api/v1/genre/about?name=Trance", BeatsApi.fromPlugin("/plugins/siansa_beats/api/genre/about?name=Trance"))
        assertEquals("/api/v1/genres?limit=400", BeatsApi.fromPlugin("/plugins/siansa_beats/api/genres?limit=400"))
        assertEquals("/api/v1/bad-blends", BeatsApi.fromPlugin(app.siansa.playback.LastBlend.PATH))
        assertEquals("/api/v1/similar/albums?track_id=t1", BeatsApi.fromPlugin(app.siansa.mix.SimilarPlugin.albumsQuery("t1")))
        assertEquals("/api/v1/similar/artists?artist=Daft+Punk", BeatsApi.fromPlugin(app.siansa.mix.SimilarPlugin.artistsQuery("Daft Punk")))
    }

    @Test fun capabilitiesAreRead() {
        val c = caps()
        assertEquals("0.20.0", c.version)
        assertEquals(1, c.api)
        assertEquals(1200, c.beatGrids)
        assertEquals(900, c.loudness)
        assertTrue(c.genres)
        assertEquals("navidrome", c.musicServer)
        // An empty answer reads as nothing there, not a crash.
        assertEquals(0, BeatsApi.Capabilities.parse(JsonObject(emptyMap())).beatGrids)
    }

    @Test fun theTestSaysWhatsMissing() {
        val ok = ProfileStore.beatsResult(caps(), SourceKind.OPENSUBSONIC)
        assertTrue(ok.message, ok.ok)
        assertTrue(ok.message, "1,200 songs with beat grids" in ok.message)
        assertFalse(ProfileStore.beatsResult(caps(grids = 0), SourceKind.OPENSUBSONIC).ok)
        assertTrue("too old" in ProfileStore.beatsResult(caps(api = 0), SourceKind.OPENSUBSONIC).message)
        val emby = ProfileStore.beatsResult(caps(), SourceKind.EMBY)
        assertFalse(emby.ok)
        assertTrue(emby.message, "doesn't read this Emby server yet" in emby.message)
        // Siansa Beats 0.23 reads Emby and Jellyfin too: working once its numbers are that server's.
        assertTrue(ProfileStore.beatsResult(caps(server = "emby"), SourceKind.EMBY).ok)
        assertTrue(ProfileStore.beatsResult(caps(server = "jellyfin"), SourceKind.JELLYFIN).ok)
    }

    @Test fun profilesComeFromSiansaBeatsWithoutAudioMuse() = runBlocking {
        val settings = AppSettings(beatsUrl = "http://beats:8090", loudnessSource = LoudnessSource.PLUGIN)
        val store = ProfileStore({ error("not asked") }, { settings }, beats = { caps() })
        assertTrue(store.active)
        assertTrue(store.check().ok)
        assertEquals("Siansa Beats", settings.measurer)
        assertEquals("the Siansa plugin", AppSettings(audioMuseUrl = "http://am:8000").measurer)
        assertFalse(ProfileStore({ error("not asked") }, { AppSettings(loudnessSource = LoudnessSource.PLUGIN) }).active)
    }

    @Test fun requestsCarryTheKeyAndErrorsSayWhy() {
        val seen = mutableListOf<okhttp3.Request>()
        val answers = ArrayDeque(listOf(
            200 to """{"service":"siansa-beats","version":"0.20.0","api":1,"features":{"beat_grids":{"available":true,"songs":3}}}""",
            401 to """{"error":"password or API key needed"}""",
            404 to """{"error":"not found"}""",
            200 to "{}",
        ))
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            seen += chain.request()
            val (code, body) = answers.removeFirst()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()
        var settings = AppSettings(beatsUrl = "http://beats:8090", beatsKey = "k3y")
        val api = BeatsApi(http) { settings }
        assertEquals(3, runBlocking { api.capabilities() }.beatGrids)  // an older Siansa Beats: no servers to name
        assertEquals("http://beats:8090/api/v1/capabilities", seen[0].url.toString())
        assertEquals("Bearer k3y", seen[0].header("Authorization"))
        val denied = runCatching { runBlocking { api.get("/api/v1/profiles?ids=a") } }.exceptionOrNull()
        assertTrue(denied?.message, denied?.message.orEmpty().contains("API key or password"))
        val old = runCatching { runBlocking { api.get("/api/v1/profiles?ids=a") } }.exceptionOrNull()
        assertTrue(old?.message, old?.message.orEmpty().contains("too old"))
        settings = settings.copy(beatsKey = "")
        runBlocking { api.get("/api/v1/genres") }
        assertEquals(null, seen[3].header("Authorization"))
        settings = AppSettings()
        assertTrue(runCatching { runBlocking { api.get("/api/v1/genres") } }.exceptionOrNull()?.message.orEmpty().contains("isn't set up"))
    }

    private fun server(id: String, kind: String, url: String, user: String, default: Boolean = false) =
        BeatsApi.MusicServer(id, kind, id, url, user, default)

    @Test fun theServerThisAppPlaysFromIsFoundAmongSiansaBeats() {
        val servers = listOf(
            server("main", "navidrome", "http://192.168.1.106:30043", "max", default = true),
            server("navidrome", "navidrome", "http://192.168.1.106:30043", "anna"),
            server("emby", "emby", "http://192.168.1.106:8096", "max"),
        )
        fun pick(kind: SourceKind, url: String, user: String) = BeatsApi.serverFor(servers, kind, url, user)
        assertEquals("emby", pick(SourceKind.EMBY, "https://emby.example.org", "max"))
        assertEquals(null, pick(SourceKind.JELLYFIN, "http://192.168.1.106:8097", "max"))
        // Two users of one Navidrome: by the user, whatever address the phone knows it by.
        assertEquals("navidrome", pick(SourceKind.OPENSUBSONIC, "https://music.example.org", "Anna"))
        assertEquals("main", pick(SourceKind.OPENSUBSONIC, "http://192.168.1.106:30043", "max"))
        // A user Siansa Beats doesn't have: the default one of that server.
        assertEquals("main", pick(SourceKind.OPENSUBSONIC, "http://192.168.1.106:30043", "guest"))
        assertEquals(null, BeatsApi.serverFor(emptyList(), SourceKind.OPENSUBSONIC, "http://x", "max"))
        assertEquals("/api/v1/genre?name=Trance&server=living+room", BeatsApi.withServer("/api/v1/genre?name=Trance", "living room"))
        assertEquals("/api/v1/genres?server=emby", BeatsApi.withServer("/api/v1/genres", "emby"))
    }

    @Test fun everyRequestNamesTheServerOnceItsKnown() {
        val seen = mutableListOf<String>()
        val caps = """{"service":"siansa-beats","version":"0.23.0","api":1,"music_server":{"kind":"KIND","set_up":true,"id":"ID"},
            "servers":[{"id":"main","kind":"navidrome","name":"Navidrome","url":"http://nas:4533","user":"max","default":true,"songs":9},
                       {"id":"emby","kind":"emby","name":"Emby","url":"http://nas:8096","user":"max","default":false,"songs":9}],
            "features":{"beat_grids":{"available":true,"songs":GRIDS}}}"""
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val url = chain.request().url
            seen += url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
            val emby = url.queryParameter("server") == "emby"
            val body = if (url.encodedPath.endsWith("/capabilities")) caps.replace("KIND", if (emby) "emby" else "navidrome")
                .replace("ID", if (emby) "emby" else "main").replace("GRIDS", if (emby) "7" else "9") else "{}"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()
        var settings = AppSettings(beatsUrl = "http://beats:8090", sourceKind = SourceKind.EMBY, navidromeUrl = "http://nas:8096", navidromeUser = "max")
        val api = BeatsApi(http) { settings }
        runBlocking { api.get("/api/v1/profiles?ids=a") }
        runBlocking { api.get("/api/v1/genres") }
        runBlocking { api.post("/api/v1/bad-blends", "{}") }
        // Similar music's server is AudioMuse's own name for one: left as it is.
        runBlocking { api.get("/api/v1/similar/artists?artist=X&server=Home") }
        assertEquals(listOf("/api/v1/capabilities", "/api/v1/profiles?ids=a&server=emby", "/api/v1/genres?server=emby",
            "/api/v1/bad-blends?server=emby", "/api/v1/similar/artists?artist=X&server=Home"), seen)
        // The Test and the DJ check read this server's numbers, not the default's.
        val mine = runBlocking { api.capabilities() }
        assertEquals("emby", mine.musicServer)
        assertEquals(7, mine.beatGrids)
        assertTrue(ProfileStore.beatsResult(mine, SourceKind.EMBY).ok)
        // Another server in the app: looked up again; one Siansa Beats doesn't read is asked without a name.
        seen.clear()
        settings = settings.copy(sourceKind = SourceKind.JELLYFIN)
        runBlocking { api.get("/api/v1/genres") }
        assertEquals(listOf("/api/v1/capabilities", "/api/v1/genres"), seen)
        assertFalse(ProfileStore.beatsResult(runBlocking { api.capabilities() }, SourceKind.JELLYFIN).ok)
        seen.clear()
        settings = settings.copy(sourceKind = SourceKind.OPENSUBSONIC, navidromeUrl = "http://nas:4533")
        runBlocking { api.get("/api/v1/genres") }
        assertEquals(listOf("/api/v1/capabilities", "/api/v1/genres?server=main"), seen)
    }
}
