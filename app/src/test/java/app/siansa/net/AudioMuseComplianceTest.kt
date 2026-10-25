package app.siansa.net

import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import app.siansa.mix.SonicRoute
import app.siansa.mix.evenly
import app.siansa.mix.parseSteering
import app.siansa.mix.pathEnd
import app.siansa.mix.sonicRoute
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
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
import org.junit.Assert.fail
import org.junit.Test

/** AudioMuse-AI compatibility (issue #208), against a pretend AudioMuse. */
class AudioMuseComplianceTest {
    private class FakeAudioMuse(val status: Int = 200, val answer: (Request) -> String) {
        val seen = mutableListOf<Pair<Request, String>>()
        val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val request = chain.request()
            val body = request.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
            seen += request to body
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("x")
                .body(answer(request).toResponseBody("application/json".toMediaType())).build()
        }).build()
    }

    private val settings = AppSettings(audioMuseUrl = "http://am.local:8000", audioMuseToken = "secret-token")
    private fun api(fake: FakeAudioMuse) = AudioMuseApi(fake.client) { settings }
    private val row = """{"item_id":"s2","title":"T","author":"A","album":"B","distance":0.25}"""

    @Test fun similarTracksLeavesTheServersSettingsAlone() = runBlocking {
        val fake = FakeAudioMuse { "[$row]" }
        val hits = api(fake).similarTracks("s1", 30)
        val url = fake.seen.single().first.url
        assertEquals("s1", url.queryParameter("item_id"))
        assertEquals("30", url.queryParameter("n"))
        assertNull(url.queryParameter("eliminate_duplicates"))
        assertNull(url.queryParameter("mood_similarity"))
        assertEquals("Bearer secret-token", fake.seen.single().first.header("Authorization"))
        assertEquals(0.75, hits.single().similarity, 1e-9)
        // Only when the listener chose it.
        api(fake).similarTracks("s1", 30, sameMood = false)
        assertEquals("false", fake.seen.last().first.url.queryParameter("mood_similarity"))
    }

    @Test fun pathsAreTheLengthPicked() = runBlocking {
        val fake = FakeAudioMuse { """{"path":[$row],"total_distance":1.0}""" }
        api(fake).findPath("a", "b", 20)
        val url = fake.seen.single().first.url
        assertEquals("20", url.queryParameter("max_steps"))
        assertEquals("true", url.queryParameter("path_fix_size"))
        assertNull(url.queryParameter("path_space"))
        api(fake).findPath("a", null, 10, endMood = PathMood.RELAXED, lyrics = true)
        val mood = fake.seen.last().first.url
        assertEquals("relaxed", mood.queryParameter("end_mood"))
        assertNull(mood.queryParameter("end_song_id"))
        assertEquals("lyrics", mood.queryParameter("path_space"))
    }

    @Test fun steeringGoesWithTheSearch() = runBlocking {
        val fake = FakeAudioMuse { """{"query":"q","count":1,"results":[{"item_id":"s2","title":"T","author":"A","similarity":0.9}]}""" }
        api(fake).textSearch("calm", 10, listOf(Steer("piano"), Steer("vocals", more = false)))
        val body = AppJson.parseToJsonElement(fake.seen.single().second).jsonObject
        assertEquals(
            """[{"term":"piano","direction":"more"},{"term":"vocals","direction":"less"}]""",
            body["steering"].toString(),
        )
        api(fake).textSearch("calm", 10)
        assertFalse(fake.seen.last().second.contains("steering"))
    }

    @Test fun alchemyBlendsSongs() = runBlocking {
        val fake = FakeAudioMuse { """{"results":[$row]}""" }
        val hits = api(fake).alchemy(listOf("s1", "s3"), count = 40)
        val body = fake.seen.single().second
        assertTrue(body.contains(""""id":"s1","op":"ADD","type":"song""""))
        assertTrue(body.contains(""""n":40"""))
        assertEquals("/api/alchemy", fake.seen.single().first.url.encodedPath)
        assertEquals(listOf("s2"), hits.map { it.id })
    }

    @Test fun moodCentres() = runBlocking {
        val fake = FakeAudioMuse { """{"relaxed":[{"cluster_id":0,"index":0,"top_tags":["ambient","piano","chill"]}],"happy":[{"index":1,"top_tags":[]}]}""" }
        val centres = api(fake).moodCentres()
        assertEquals(listOf("relaxed-0", "happy-1"), centres.map { AudioMuseApi.centreKey(it) })
        assertEquals("Relaxed: ambient, piano", AudioMuseApi.centreTitle(centres[0]))
        assertEquals("Happy", AudioMuseApi.centreTitle(centres[1]))
        api(fake).moodRadio(centres[0], 50)
        val url = fake.seen.last().first.url
        assertEquals("relaxed", url.queryParameter("mood"))
        assertEquals("0", url.queryParameter("centroid_index"))
    }

    @Test fun errorCodesAreRead() = runBlocking {
        val fake = FakeAudioMuse(status = 503) {
            """{"error_code":3002,"error_class":"Index Error","error_message":"Index not loaded","error":"Index not loaded"}"""
        }
        try {
            api(fake).similarTracks("s1", 10)
            fail("expected an error")
        } catch (e: ApiException) {
            assertEquals(3002, e.code)
            assertEquals("AudioMuse's index isn't ready yet. Run an analysis in AudioMuse first.", e.message)
        }
        // Older servers: only the HTTP status and "error".
        assertEquals("AudioMuse answered HTTP 500: boom", AudioMuseErrors.message(null, 500, "boom"))
        assertEquals("Odd", AudioMuseErrors.message(9999, 500, "Odd"))
        // Beta report 810dc85c: a song AudioMuse doesn't know points to the server name setting too.
        assertTrue(AudioMuseErrors.message(1004, 404, "x").contains("Server name in AudioMuse"))
        assertEquals(AudioMuseErrors.NOT_KNOWN, AudioMuseErrors.message(null, 404, "x"))
    }

    @Test fun whereSimilarSongsComeFrom() {
        assertEquals(SonicRoute.NAVIDROME_SONIC, sonicRoute(navidromeSonic = true, audioMuseReady = true))
        assertEquals(SonicRoute.AUDIOMUSE, sonicRoute(navidromeSonic = false, audioMuseReady = true))
        assertEquals(SonicRoute.NAVIDROME_SIMILAR, sonicRoute(navidromeSonic = false, audioMuseReady = false))
    }

    @Test fun pathEnds() {
        assertEquals("song1" to null, pathEnd("song1"))
        assertEquals(null to PathMood.SAD, pathEnd("mood:sad"))
        assertEquals(null to null, pathEnd("mood:unknown"))
    }

    @Test fun aiSteeringOnlyUsesListedConcepts() {
        val json = AppJson.parseToJsonElement(
            """[{"term":"Piano","direction":"more"},{"term":"made-up","direction":"less"},{"term":"vocals","direction":"less"},{"term":"piano"},{"term":"drums"},{"term":"strings"}]""",
        )
        val steer = parseSteering(json, listOf("piano", "vocals", "drums", "strings"))
        assertEquals(listOf(Steer("piano", true), Steer("vocals", false), Steer("drums", true)), steer)
        assertEquals(emptyList<Steer>(), parseSteering(null, listOf("piano")))
    }

    @Test fun blendSeedsAreSpreadEvenly() {
        assertEquals(listOf(1, 2, 3), evenly(listOf(1, 2, 3), 20))
        assertEquals(listOf(0, 25, 50, 75), evenly((0 until 100).toList(), 4))
    }

    @Test fun everySongRequestNamesTheSongsServer() = runBlocking {
        // Before: similar songs, mood radios, paths and song details built their own address and lost the name.
        val fake = FakeAudioMuse { "[$row]" }
        val api = AudioMuseApi(fake.client, serverFor = { ids -> if ("e1" in ids) "Emby" else "Navidrome" }) { settings }
        api.similarTracks("e1", 10)
        assertEquals("Emby", fake.seen.last().first.url.queryParameter("server"))
        api.similarTracks("n1", 10)
        assertEquals("Navidrome", fake.seen.last().first.url.queryParameter("server"))
        api.findPath("e1", "e2", 5)
        assertEquals("Emby", fake.seen.last().first.url.queryParameter("server"))
        api.plugin("/plugins/x/api/profiles?ids=e1,e2")
        assertEquals("Emby", fake.seen.last().first.url.queryParameter("server"))
    }

    @Test fun pluginCallsNameTheServerOfTheirSong() = runBlocking {
        // Before: similar albums sent track_id=… without a server.
        assertEquals(listOf("885028"), songIdsIn("/plugins/siansa_analysis/api/similar/albums?track_id=885028"))
        assertEquals(listOf("a", "b"), songIdsIn("/plugins/x/api/profiles?ids=a%2Cb"))
        assertTrue(songIdsIn("/plugins/x/api/similar/artists?artist=Overmono").isEmpty())
        val fake = FakeAudioMuse { "[]" }
        AudioMuseApi(fake.client, serverFor = { ids -> if ("885028" in ids) "Emby" else "" }) { settings }
            .plugin("/plugins/x/api/similar/albums?track_id=885028")
        assertEquals("Emby", fake.seen.last().first.url.queryParameter("server"))
    }

    @Test fun withoutServerForTheSettingsNameIsUsed() = runBlocking {
        val fake = FakeAudioMuse { "[$row]" }
        AudioMuseApi(fake.client) { settings.copy(audioMuseServer = "Emby") }.similarTracks("s1", 10)
        assertEquals("Emby", fake.seen.single().first.url.queryParameter("server"))
    }
}
