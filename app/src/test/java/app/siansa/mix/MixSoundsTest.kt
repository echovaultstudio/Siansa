package app.siansa.mix

import app.siansa.data.AppJson
import app.siansa.net.ClapHit
import app.siansa.net.Steer
import app.siansa.servers.EveryServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a vibe's own songs have to offer for shaping it (the owner, 11 Nov), and the pad. */
class MixSoundsTest {
    private fun g(vararg names: String) = names.map { SongGenre(it, 0.5) }

    /** Ten songs, all ambient; four with piano, five drone, one rock. */
    private val tired: Map<String, List<SongGenre>> = (1..10).associate { i ->
        "s$i" to g(*listOfNotNull("Ambient", "Piano".takeIf { i <= 4 }, "Drone".takeIf { i in 3..7 }, "Rock".takeIf { i == 10 }).toTypedArray())
    }

    @Test fun onlySoundsWhereTheSongsDifferAreOffered() {
        // Every song is ambient and one is rock: neither tells the songs apart. Drone (half of them) splits them best.
        assertEquals(listOf(MixSound("Drone", 5), MixSound("Piano", 4)), mixSounds(tired))
    }

    @Test fun aMixWithTooFewKnownSongsOffersNothing() {
        assertTrue(mixSounds(tired.entries.take(4).associate { it.key to it.value }).isEmpty())
        assertTrue(mixSounds(emptyMap()).isEmpty())
    }

    @Test fun theMixsOwnDialsReRankAndNeverSteerTheSearch() {
        val t = VibeTuning(sounds = listOf(SoundDial("piano", 3), SoundDial("Drone", 5, genre = true)))
        assertEquals(listOf(Steer("piano", true, 3)), t.steering())
        assertEquals("+piano ×3 · +Drone ×5", t.summary())
        val hits = listOf("s1", "s2", "s3", "s4").map { ClapHit(it, "T$it", "A$it", null, 0.5) }
        val genres = mapOf("s4" to listOf(SongGenre("drone", 0.9)), "s2" to listOf(SongGenre("Drone", 0.4)))
        val more = VibeTuning(sounds = listOf(SoundDial("Drone", 5, genre = true)))
        assertEquals(listOf("s4", "s2", "s1", "s3"), rankVibe(hits, emptyMap(), emptyMap(), more, genres).map { it.id })
        val less = VibeTuning(sounds = listOf(SoundDial("Drone", -5, genre = true)))
        assertEquals("s4", rankVibe(hits, emptyMap(), emptyMap(), less, genres).last().id)
        // Genres unknown (Siansa Beats away): the order stays.
        assertEquals(hits, rankVibe(hits, emptyMap(), emptyMap(), more, emptyMap()))
    }

    @Test fun aSongsPlaceOnThePad() {
        assertEquals(PadDot(0.5f, 0.5f), padPlace(120.0, 0.08))
        assertEquals(PadDot(0f, 0f), padPlace(40.0, 0.0))
        assertEquals(PadDot(1f, 1f), padPlace(200.0, 0.4))
        assertNull(padPlace(null, 0.1))
        assertNull(padPlace(120.0, null))
        // With Siansa Beats 0.35 a song's height is its arousal, not its loudness.
        assertEquals(PadDot(0.5f, 0.5f), padPlace(120.0, 0.84, arousal = 5.25))
        assertEquals(PadDot(0.5f, 1f), padPlace(120.0, null, arousal = 8.0))
        assertEquals(PadDot(0.5f, 0f), padPlace(120.0, 0.84, arousal = 2.0))
    }

    private fun answer(id: String) = AppJson.parseToJsonElement(
        """{"schema_version":1,"minimum":0.15,"songs":{"$id":[{"name":"Ambient","genre":"Electronic","style":"Ambient","score":0.61},
           {"name":"Drone","score":0.2},{"score":0.9}],"other":[]}}""",
    ) as JsonObject

    /** Whichever server the songs are from (their ids differ in style), each song's genres come back under its own id. */
    @Test fun eachSongsGenresAreAskedOfSiansaBeatsOnEveryServer() = runBlocking {
        for (server in EveryServer.all) {
            val asked = mutableListOf<String>()
            val beats = BeatsGenres({ path -> asked += path; answer(server.songId) as JsonElement }, { true })
            val genres = beats.ofSongs(listOf(server.songId, server.songId))
            assertEquals("$server", "/plugins/siansa_beats/api/songs/genres?ids=${server.songId}", asked.single())
            assertEquals("$server", mapOf(server.songId to listOf(SongGenre("Ambient", 0.61, "Electronic", "Ambient"), SongGenre("Drone", 0.2))), genres)
        }
    }

    @Test fun anOlderSiansaBeatsIsAskedOnceAndThenLeftAlone() = runBlocking {
        var time = 1_000L
        var asked = 0
        val beats = BeatsGenres({ asked++; throw IllegalStateException("HTTP 404") }, { true }, { time })
        assertNull(beats.ofSongs(listOf("a")))
        assertNull(beats.ofSongs(listOf("a")))
        assertEquals(1, asked)
        time += BeatsGenres.BACK_OFF_MS + 1
        assertNull(beats.ofSongs(listOf("a")))
        assertEquals(2, asked)
        // Not set up at all: never asked.
        assertNull(BeatsGenres({ asked++; JsonObject(emptyMap()) }, { false }).ofSongs(listOf("a")))
        assertEquals(2, asked)
        assertFalse(songGenresOf(null).isNotEmpty())
    }

    @Test fun manySongsAreAskedInBatches() = runBlocking {
        val asked = mutableListOf<String>()
        val beats = BeatsGenres({ path -> asked += path; JsonObject(emptyMap()) }, { true })
        beats.ofSongs((1..(BeatsGenres.SONGS_AT_ONCE + 5)).map { "s$it" })
        assertEquals(2, asked.size)
    }
}
