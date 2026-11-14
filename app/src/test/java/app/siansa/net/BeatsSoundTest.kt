package app.siansa.net

import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * What songs sound like is asked of Siansa Beats first once it has it (Siansa Beats 0.25, the owner, 11 Nov), at
 * AudioMuse-AI's own paths, and of AudioMuse-AI otherwise: so AudioMuse-AI can be switched off in the app, and stays
 * for everyone without Siansa Beats.
 */
class BeatsSoundTest {
    private val song = """[{"item_id":"s2","title":"Deep","author":"House Test","album":"Rooms","distance":0.1}]"""

    /** AudioMuse-AI's stand-in: answers every request with [song], and remembers what was asked. */
    private val askedOfAudioMuse = mutableListOf<String>()
    private val audioMuseHttp = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val request = chain.request()
        askedOfAudioMuse += request.method + " " + request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
        val body = if (request.url.encodedPath.endsWith("/api/alchemy")) """{"results":$song}"""
        else if (request.url.encodedPath.endsWith("/api/find_path")) """{"path":$song}""" else song
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }).build()

    private val askedOfBeats = mutableListOf<Pair<String, String?>>()
    private var beatsFails = false

    private fun api(settings: AppSettings) = AudioMuseApi(
        audioMuseHttp,
        beats = { path, body ->
            askedOfBeats += path to body
            if (beatsFails) throw ApiException("Siansa Beats doesn't know this song", 404)
            val answer: JsonElement = AppJson.parseToJsonElement(
                when {
                    path.contains("/api/clap/search") -> """{"query":"q","count":1,"results":[{"item_id":"s2","title":"Deep","author":"House Test","album":"Rooms","similarity":0.42}],"steering_warnings":["\"bagpipes\" is not a validated concept for this library"]}"""
                    path.contains("/api/clap/concepts") -> """{"available":true,"max_terms":10,"categories":[{"category":"instrument","label":"Instrument","terms":[{"term":"piano","grounding":0.5},{"term":"choir","grounding":0.9}]}]}"""
                    path.contains("/api/alchemy") -> """{"results":$song}"""
                    path.contains("/api/find_path") -> """{"path":$song}"""
                    else -> song
                },
            )
            answer
        },
    ) { settings }

    private val both = AppSettings(audioMuseUrl = "http://am.local:8000", audioMuseToken = "t", beatsUrl = "http://beats.local:8090")
    private val beatsOnly = AppSettings(beatsUrl = "http://beats.local:8090")
    private val audioMuseOnly = AppSettings(audioMuseUrl = "http://am.local:8000", audioMuseToken = "t")
    private val everything = setOf("/api/similar_tracks", "/api/mood_centroids", "/api/find_path", "/api/alchemy", "/api/sonic_fingerprint/generate", "/api/sync")

    @After fun forget() = BeatsSound.heard(emptySet())

    @Test fun siansaBeatsIsAskedFirstAtAudioMusesOwnPaths() = runBlocking {
        BeatsSound.heard(everything)
        val hits = api(both).similarTracks("s1", 5, sameMood = true)
        assertEquals(listOf("s2"), hits.map { it.id })
        assertEquals(0.9, hits[0].similarity, 1e-9)
        assertEquals(listOf("/api/v1/audiomuse/api/similar_tracks?item_id=s1&n=5&mood_similarity=true" to null), askedOfBeats.toList())
        assertEquals(emptyList<String>(), askedOfAudioMuse)
    }

    @Test fun audioMuseNeedntBeSetUpInTheApp() = runBlocking {
        BeatsSound.heard(everything)
        val api = api(beatsOnly)
        assertEquals(listOf("s2"), api.similarTracks("s1", 5).map { it.id })
        assertEquals(listOf("s2"), api.alchemy(listOf("s1"), avoid = listOf("s9"), count = 4).map { it.id })
        assertEquals(listOf("s2"), api.findPath("s1", "s3", 5).map { it.id })
        assertEquals(listOf("s2"), api.sonicFingerprint(10, "max", "sesame").map { it.id })
        assertEquals(emptyList<String>(), askedOfAudioMuse)
        val (path, body) = askedOfBeats[1]
        assertEquals("/api/v1/audiomuse/api/alchemy", path)
        val items = (AppJson.parseToJsonElement(body!!) as JsonObject)["items"].toString()
        assertTrue(items, items.contains("\"ADD\"") && items.contains("\"SUBTRACT\"") && items.contains("s9"))
        assertEquals("/api/v1/audiomuse/api/find_path?start_song_id=s1&end_song_id=s3&max_steps=5&path_fix_size=true", askedOfBeats[2].first)
        // The music server's login is never sent to Siansa Beats: it reads the most played songs with its own.
        assertEquals("/api/v1/audiomuse/api/sonic_fingerprint/generate" to """{"n":10}""", askedOfBeats[3])
        assertTrue(beatsOnly.soundReady)
    }

    @Test fun audioMuseIsAskedWhenSiansaBeatsCant() = runBlocking {
        BeatsSound.heard(everything)
        beatsFails = true
        assertEquals(listOf("s2"), api(both).similarTracks("s1", 5).map { it.id })
        assertEquals(1, askedOfBeats.size)
        assertEquals(listOf("GET /api/similar_tracks?item_id=s1&n=5"), askedOfAudioMuse)
        // "Your sound" too, with the login AudioMuse-AI needs for it.
        assertEquals(listOf("s2"), api(both).sonicFingerprint(10, "max", "sesame").map { it.id })
        assertEquals("POST /api/sonic_fingerprint/generate", askedOfAudioMuse.last())
    }

    @Test fun withoutAudioMuseSiansaBeatsReasonIsSaid() = runBlocking {
        BeatsSound.heard(everything)
        beatsFails = true
        try {
            api(beatsOnly).similarTracks("s1", 5)
            fail("no error")
        } catch (e: ApiException) {
            assertEquals("Siansa Beats doesn't know this song", e.message)
        }
        assertEquals(emptyList<String>(), askedOfAudioMuse)
    }

    @Test fun whatSiansaBeatsDoesntAnswerGoesToAudioMuse() = runBlocking {
        // A Siansa Beats before 0.25, or one that hasn't taken the songs' sound over yet.
        assertEquals(listOf("s2"), api(both).similarTracks("s1", 5).map { it.id })
        // One that answers some: the rest are still AudioMuse-AI's.
        BeatsSound.heard(setOf("/api/similar_tracks"))
        assertEquals(listOf("s2"), api(both).findPath("s1", "s3", 5).map { it.id })
        assertEquals(emptyList<Pair<String, String?>>(), askedOfBeats.toList())
        assertEquals(2, askedOfAudioMuse.size)
    }

    @Test fun withoutSiansaBeatsNothingChanges() = runBlocking {
        BeatsSound.heard(everything) // kept from another setup: means nothing without Siansa Beats set up here
        assertEquals(listOf("s2"), api(audioMuseOnly).similarTracks("s1", 5).map { it.id })
        assertEquals(emptyList<Pair<String, String?>>(), askedOfBeats.toList())
        assertTrue(audioMuseOnly.soundReady)
    }

    @Test fun theSoundFeaturesAreOfferedWithEitherOne() {
        assertFalse(AppSettings().soundReady)
        assertFalse("Siansa Beats without the songs' sound", beatsOnly.soundReady)
        assertTrue(audioMuseOnly.soundReady)
        BeatsSound.heard(everything)
        assertTrue(beatsOnly.soundReady)
        assertFalse("the sound kept from before, Siansa Beats no longer set up", AppSettings().soundReady)
    }

    @Test fun theCapabilitiesSayWhichPathsAreAnswered() {
        val caps = BeatsApi.Capabilities.parse(AppJson.parseToJsonElement(
            """{"version":"0.25.0","api":1,"features":{"sound":{"available":true,"songs":46000,"prefix":"/api/v1/audiomuse",
               "answers":["/api/similar_tracks","/api/find_path"]}}}""",
        ) as JsonObject)
        assertEquals(setOf("/api/similar_tracks", "/api/find_path"), caps.sound)
        val older = BeatsApi.Capabilities.parse(AppJson.parseToJsonElement("""{"version":"0.24.2","api":1,"features":{}}""") as JsonObject)
        assertEquals(emptySet<String>(), older.sound)
        BeatsSound.heard(caps.sound)
        assertTrue(BeatsSound.answers("/api/similar_tracks?item_id=1"))
        assertFalse(BeatsSound.answers("/api/clap/search"))
        assertEquals("/api/v1/audiomuse/api/sync?ids=a", BeatsSound.path("/api/sync?ids=a"))
    }

    /**
     * Siansa Beats 0.44 (the owner, 13 Nov) has paths of its own for what songs sound like and says them in its
     * capabilities: the app asks there. An older one says none, and is asked where it always was.
     */
    @Test fun siansaBeatsIsAskedAtItsOwnPathsWhenItSaysThem() {
        val caps = BeatsApi.Capabilities.parse(AppJson.parseToJsonElement(
            """{"version":"0.44.0","api":1,"features":{"sound":{"available":true,"songs":46000,"prefix":"/api/v1/audiomuse",
               "answers":["/api/similar_tracks","/api/sync","/api/clap/search"],
               "paths":{"/api/similar_tracks":"/api/v1/sound/similar","/api/sync":"/api/v1/sound/songs",
                        "/api/clap/search":"/api/v1/sound/search","/api/not_answered":"/api/v1/sound/else","/api/find_path":"http://elsewhere/x"}}}}""",
        ) as JsonObject)
        assertEquals("/api/v1/sound/similar", caps.soundPaths["/api/similar_tracks"])
        var kept: Map<String, String>? = null
        BeatsSound.onChanged = { _, where -> kept = where }
        try {
            BeatsSound.heard(caps.sound, caps.soundPaths)
            assertEquals("/api/v1/sound/similar?item_id=s1&n=5", BeatsSound.path("/api/similar_tracks?item_id=s1&n=5"))
            assertEquals("/api/v1/sound/songs?ids=a", BeatsSound.path("/api/sync?ids=a"))
            assertEquals("/api/v1/sound/search", BeatsSound.path("/api/clap/search"))
            // Only for what it answers, and only a path of its own: anything else is asked where it always was.
            assertEquals(setOf("/api/similar_tracks", "/api/sync", "/api/clap/search"), kept!!.keys)
            assertEquals("/api/v1/audiomuse/api/find_path?start_song_id=a", BeatsSound.path("/api/find_path?start_song_id=a"))
            // Kept for the next start, and an older Siansa Beats (no paths) is asked under the old prefix again.
            BeatsSound.restore(caps.sound, kept!!)
            assertEquals("/api/v1/sound/songs?ids=a", BeatsSound.path("/api/sync?ids=a"))
            BeatsSound.heard(caps.sound)
            assertEquals("/api/v1/audiomuse/api/sync?ids=a", BeatsSound.path("/api/sync?ids=a"))
            assertEquals(emptyMap<String, String>(), kept)
        } finally {
            BeatsSound.onChanged = null
        }
    }

    /** From the owner's log, 11 Nov: similar songs still came from Navidrome's AudioMuse-AI plugin. */
    @Test fun similarSongsAreAskedOfSiansaBeatsBeforeTheMusicServersPlugin() {
        assertEquals(app.siansa.mix.SonicRoute.BEATS, app.siansa.mix.sonicRoute(navidromeSonic = true, audioMuseReady = true, beatsSound = true))
        assertEquals(app.siansa.mix.SonicRoute.BEATS, app.siansa.mix.sonicRoute(navidromeSonic = false, audioMuseReady = false, beatsSound = true))
        assertEquals(app.siansa.mix.SonicRoute.NAVIDROME_SONIC, app.siansa.mix.sonicRoute(navidromeSonic = true, audioMuseReady = true))
    }

    /** From the same log: with AudioMuse-AI's address cleared, Song path and the mix slides said "not available". */
    @Test fun theSoundFeaturesArentHeldBackByAudioMuseBeingOff() {
        val connection = ConnectionState(network = true, server = true, audioMuse = null)
        assertEquals("AudioMuse isn't set up. Add it in Settings › Connections.", connection.soundProblem(beatsOnly))
        BeatsSound.heard(everything)
        assertEquals(null, connection.soundProblem(beatsOnly))
        assertEquals(null, ConnectionState(audioMuse = false).soundProblem(both))  // AudioMuse-AI down: Siansa Beats answers
        assertEquals("You're offline.", ConnectionState(network = false).soundProblem(beatsOnly))
        assertEquals("Only downloaded music is on.", connection.soundProblem(beatsOnly.copy(downloadedOnly = true)))
        // The search by words is still AudioMuse-AI's, and says so plainly.
        assertEquals("Describing music in words needs Siansa Beats 0.26 or newer, or AudioMuse (Settings › Connections).", connection.aiProblem(beatsOnly))
        assertEquals(null, connection.aiProblem(both))
        // Without Siansa Beats, as before.
        assertEquals("AudioMuse isn't answering.", ConnectionState(audioMuse = false).soundProblem(audioMuseOnly))
    }

    /** The owner, 11 Nov: "only and always, don't use AudioMuse tempos": a DJ mix's song analysis is Siansa Beats' or none. */
    @Test fun aDjMixNeverReadsAudioMusesTempos() = runBlocking {
        // Siansa Beats without the songs' sound, AudioMuse-AI set up: not asked.
        try {
            api(both).features(listOf("s1"), beatsOnly = true)
            fail("no error")
        } catch (e: ApiException) {
            assertTrue(e.message, e.message!!.contains("Siansa Beats doesn't have the songs' sound yet"))
        }
        // Siansa Beats failing: its reason, not AudioMuse-AI's answer.
        BeatsSound.heard(everything)
        beatsFails = true
        try {
            api(both).features(listOf("s1"), beatsOnly = true)
            fail("no error")
        } catch (e: ApiException) {
            assertEquals("Siansa Beats doesn't know this song", e.message)
        }
        assertEquals(emptyList<String>(), askedOfAudioMuse)
    }

    /** Siansa Beats 0.26 searches by words itself, with AudioMuse-AI's models: mood radios, vibes, sound cards. */
    @Test fun describingMusicInWordsIsAskedOfSiansaBeats() = runBlocking {
        val withWords = everything + setOf("/api/clap/search", "/api/clap/concepts")
        assertFalse(beatsOnly.wordsReady)
        BeatsSound.heard(everything)
        assertFalse("Siansa Beats 0.25: the sound, not the words", beatsOnly.wordsReady)
        assertTrue(audioMuseOnly.wordsReady)
        BeatsSound.heard(withWords)
        assertTrue(beatsOnly.wordsReady)
        val connection = ConnectionState(network = true, server = true, audioMuse = null)
        assertEquals(null, connection.aiProblem(beatsOnly))
        assertEquals(null, ConnectionState(audioMuse = false).aiProblem(both))  // AudioMuse-AI down: Siansa Beats answers

        val api = api(beatsOnly)
        val hits = api.textSearch("chill mellow grooves", 30, listOf(Steer("piano"), Steer("vocals", more = false, weight = 5)))
        assertEquals(listOf("s2"), hits.map { it.id })
        assertEquals(0.42, hits[0].similarity, 1e-9)
        val (path, body) = askedOfBeats.single()
        assertEquals("/api/v1/audiomuse/api/clap/search", path)
        val sent = AppJson.parseToJsonElement(body!!) as JsonObject
        assertEquals("\"chill mellow grooves\"", sent["query"].toString())
        assertEquals("30", sent["limit"].toString())
        assertTrue(sent["steering"].toString(), sent["steering"].toString().contains("\"direction\":\"less\"") && sent["steering"].toString().contains("\"weight\":5"))
        assertFalse("AudioMuse-AI's own name for a server isn't sent to Siansa Beats", sent.containsKey("server"))
        // The concepts the sliders and the AI's wording use, best understood first.
        assertEquals(listOf("choir", "piano"), api.conceptCatalogue().categories.single().terms)
        assertEquals(listOf("piano", "choir"), api.concepts())
        assertEquals(emptyList<String>(), askedOfAudioMuse)
    }
}
