package app.siansa.local

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Looking up MusicBrainz IDs for files without them (issue #352). */
class MusicBrainzTest {
    private fun json(text: String) = Json.parseToJsonElement(text) as JsonObject

    private val search = json(
        """
        {"recordings":[
          {"id":"live-0000-0000-0000-000000000000","score":100,"title":"Song","length":300000,
           "artist-credit":[{"name":"Artist","artist":{"id":"a1","name":"Artist"}}]},
          {"id":"studio-000-0000-0000-000000000000","score":100,"title":"Song","length":201000,
           "artist-credit":[{"name":"Artist","joinphrase":" feat. ","artist":{"id":"a1","name":"Artist"}},{"name":"Guest","artist":{"id":"a2","name":"Guest"}}]},
          {"id":"cover-000-0000-0000-000000000000","score":95,"title":"Song","length":200000,
           "artist-credit":[{"name":"Someone Else","artist":{"id":"a9","name":"Someone Else"}}]},
          {"id":"low-00000-0000-0000-000000000000","score":40,"title":"Song","length":200000,
           "artist-credit":[{"name":"Artist","artist":{"id":"a1","name":"Artist"}}]}
        ]}
        """,
    )

    @Test fun theRecordingOfTheSameArtistAndLengthIsPicked() {
        val found = MusicBrainzApi.pickRecording(search, "Artist feat. Guest", "Song", 200)!!
        assertEquals("studio-000-0000-0000-000000000000", found.id)
        assertEquals(listOf("a1", "a2"), found.artistIds)
        assertEquals(201, found.lengthSec)
        // The first artist alone matches too.
        assertEquals("studio-000-0000-0000-000000000000", MusicBrainzApi.pickRecording(search, "Artist", "Song", 199)!!.id)
    }

    @Test fun nothingSureIsNothing() {
        // No version near this length.
        assertNull(MusicBrainzApi.pickRecording(search, "Artist", "Song", 100))
        assertNull(MusicBrainzApi.pickRecording(search, "Artist", "Another song", 200))
        assertNull(MusicBrainzApi.pickRecording(json("""{"recordings":[]}"""), "Artist", "Song", 200))
    }

    @Test fun anArtistIsPickedOnlyWhenOneHasTheName() {
        val one = json("""{"artists":[{"id":"x1","name":"Bonobo","score":100},{"id":"x2","name":"Bonobo Bash","score":70}]}""")
        assertEquals("x1", MusicBrainzApi.pickArtist(one, "bonobo"))
        val two = json("""{"artists":[{"id":"y1","name":"Nirvana","score":100},{"id":"y2","name":"Nirvana","score":100}]}""")
        assertNull(MusicBrainzApi.pickArtist(two, "Nirvana"))
    }

    @Test fun queriesAreEscapedAndTheAppNamesItself() {
        assertEquals("AC\\/DC \\(live\\)", MusicBrainzApi.escape("AC/DC (live)"))
        assertEquals("Siansa/0.52.0 ( https://siansa.app )", MusicBrainzApi.userAgent("0.52.0"))
    }

    @Test fun requestsWaitASecondAndSayWhoAsks() = runBlocking {
        val seen = ArrayList<okhttp3.Request>()
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            seen += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"recordings":[],"artists":[]}""".toResponseBody("application/json".toMediaType())).build()
        }).build()
        val api = MusicBrainzApi(client, MusicBrainzApi.userAgent("1.0"))
        val started = System.currentTimeMillis()
        api.recording("Artist", "Song", 200)
        api.artistId("Artist")
        assertTrue(System.currentTimeMillis() - started >= MusicBrainzApi.MIN_GAP_MS)
        assertEquals("Siansa/1.0 ( https://siansa.app )", seen.first().header("User-Agent"))
        assertEquals("json", seen.first().url.queryParameter("fmt"))
        assertTrue(seen.first().url.queryParameter("query")!!.contains("recording:\"Song\""))
    }

    @Test fun eachSongIsLookedUpOnce() = runBlocking {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            calls++
            val body = if (chain.request().url.encodedPath.endsWith("recording")) {
                """{"recordings":[{"id":"r1","score":100,"title":"Song","length":200000,"artist-credit":[{"name":"Artist","artist":{"id":"a1","name":"Artist"}}]}]}"""
            } else """{"artists":[]}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()
        val lookup = MusicBrainzLookup(MusicBrainzApi(client, "test", now = { 0L }))
        val t = LocalTrack(uri = "u1", folder = "f", path = "p", title = "Song", artist = "Artist", artists = listOf("Artist"), durationSec = 200)
        val done = lookup.lookUp(t)
        assertEquals("r1", done.mbRecording)
        assertEquals(listOf("a1"), done.mbArtists)
        assertTrue(done.mbLooked)
        assertEquals("r1", done.recording)
        // The same song in another folder: no new request.
        lookup.lookUp(t.copy(uri = "u2"))
        assertEquals(1, calls)
    }
}
