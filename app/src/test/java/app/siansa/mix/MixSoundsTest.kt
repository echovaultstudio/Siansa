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

/** A vibe's sound dials come from what its own songs have to offer (the owner, 11 Nov), and the sound pad. */
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

    @Test fun theDialsAreTheAisAndTheMixsOwnNeverAFixedList() {
        val steered = listOf(Steer("ambient music", true), Steer("dance", false))
        val offered = listOf(MixSound("Drone", 5), MixSound("Piano", 4))
        // A vibe made with the old padding: the AI's two, and "acoustic" from the fixed list, never moved.
        val old = VibeTuning(sounds = listOf(SoundDial("ambient music", 3), SoundDial("dance", -3), SoundDial("acoustic", 0)))
        assertEquals(
            listOf(SoundDial("ambient music", 3), SoundDial("dance", -3), SoundDial("Drone", 0, genre = true)),
            vibeDials(old, steered, offered).sounds,
        )
        // Without Siansa Beats: the AI's alone, also one of them set back to the middle.
        assertEquals(
            listOf(SoundDial("ambient music", 0), SoundDial("dance", -3)),
            vibeDials(old.copy(sounds = listOf(SoundDial("ambient music", 0), SoundDial("dance", -3), SoundDial("acoustic", 0))), steered, emptyList()).sounds,
        )
        // No AI: the mix's own three. Three from the AI: still one of the mix's.
        assertEquals(listOf("Drone", "Piano"), vibeDials(VibeTuning(), emptyList(), offered).sounds.map { it.term })
        val three = steered + Steer("piano", true)
        assertEquals(listOf("ambient music", "dance", "piano", "Drone"), vibeDials(VibeTuning(), three, offered).sounds.map { it.term })
        // A sound that's already a dial isn't offered twice ("piano" and "Piano").
        assertEquals(listOf(MixSound("Drone", 5)), soundsToAdd(vibeDials(VibeTuning(), listOf(Steer("piano", true)), emptyList()), offered))
    }

    @Test fun aSoundIsAddedWhileThereIsRoomThenTakesAnUnusedDialsPlace() {
        var t = VibeTuning(sounds = listOf(SoundDial("a", 1), SoundDial("b", 0)))
        t = addSound(t, "Piano")
        assertEquals(listOf("a", "b", "Piano"), t.sounds.map { it.term })
        assertTrue(t.sounds.last().genre)
        assertEquals(t, addSound(t, "piano"))  // already there
        t = addSound(addSound(t, "Drone"), "Strings")
        assertEquals(VibeTuning.MOST_DIALS, t.sounds.size)
        // Full: the last dial that isn't set makes way; the ones set stay.
        assertEquals(listOf("a", "b", "Piano", "Drone", "Jazz"), addSound(t, "Jazz").sounds.map { it.term })
        val allSet = t.copy(sounds = t.sounds.map { it.copy(value = 2) })
        assertEquals("Jazz", addSound(allSet, "Jazz").sounds.last().term)
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

    @Test fun thePadSaysWhereItsDotIs() {
        assertEquals("Energy and speed as planned. Drag the dot to change them.", padCaption(VibeTuning()))
        assertEquals("A bit calmer and much faster.", padCaption(VibeTuning(energy = -1, tempo = 2)))
        assertEquals("Much more energy.", padCaption(VibeTuning(energy = 2)))
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
