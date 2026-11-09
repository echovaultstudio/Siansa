package app.siansa.mix

import app.siansa.data.Song
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Genre radios by Siansa Beats' vote, and by the songs' tags whenever the vote can't be had. */
class GenreVoteTest {
    private var time = 1_000_000L
    private var ready = true
    private val asked = mutableListOf<String>()
    private var reply: (String) -> JsonElement = {
        JsonObject(mapOf("ids" to JsonArray(listOf("a", "b").map { JsonPrimitive(it) }),
            "styles" to JsonArray(listOf(JsonPrimitive("Electronic---Trance"))), "genres" to JsonArray(emptyList())))
    }
    private val vote = GenreVote({ path -> asked += path; reply(path) }, { ready }, { time })

    @Test fun asksThePluginForTheGenreAsShown() = runBlocking {
        assertEquals(listOf("a", "b"), vote.songIds("Electronic / Trance", 300))
        assertEquals("/plugins/siansa_beats/api/genre?name=Electronic%20%2F%20Trance&limit=300", asked.single())
    }

    @Test fun withoutAudioMuseItIsntAsked() = runBlocking {
        ready = false
        assertNull(vote.songIds("Trance", 300))
        assertTrue(asked.isEmpty())
    }

    @Test fun aMissingServiceIsLeftAloneForAWhile() = runBlocking {
        reply = { throw IllegalStateException("AudioMuse answered HTTP 502") }
        assertNull(vote.songIds("Trance", 300))
        assertNull(vote.songIds("Trance", 300))
        assertEquals(1, asked.size)
        time += GenreVote.BACK_OFF_MS + 1
        vote.songIds("Trance", 300)
        assertEquals(2, asked.size)
    }

    @Test fun theTagsPlayWhenTheVoteHasTooFewSongs() = runBlocking {
        val tagged = listOf(Song(id = "t", title = "Tagged", artist = "x"))
        val many = (1..GenreVote.MIN_VOTED).map { Song(id = "v$it", title = "v$it", artist = "x") }
        assertEquals(many, GenreVote.pick("Trance", many) { tagged })
        assertEquals(tagged, GenreVote.pick("Trance", many.take(5)) { tagged })
        assertEquals(tagged, GenreVote.pick("Trance", null) { tagged })  // no Siansa Beats: as always
    }
}
