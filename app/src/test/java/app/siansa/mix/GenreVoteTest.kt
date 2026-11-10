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

    @Test fun theSearchPageGetsTheVotedGenresWithTheirRadioNames() = runBlocking {
        val many = (1..GenreVote.MIN_GENRES).map { JsonObject(mapOf("name" to JsonPrimitive("Style $it"), "songs" to JsonPrimitive(100 - it))) }
        reply = { JsonObject(mapOf("genres" to JsonArray(many + JsonObject(mapOf("name" to JsonPrimitive("Hip Hop / Electro"), "songs" to JsonPrimitive(3)))))) }
        val genres = vote.genres(30)!!
        assertEquals("Style 1", genres.first().name)
        assertEquals(99, genres.first().songCount)
        assertEquals("Hip Hop / Electro", genres.last().name)
        assertEquals("/plugins/siansa_beats/api/genres?limit=30", asked.last())
    }

    @Test fun tooFewVotedGenresShowTheTags() = runBlocking {
        reply = { JsonObject(mapOf("genres" to JsonArray(listOf(JsonObject(mapOf("name" to JsonPrimitive("Trance"), "songs" to JsonPrimitive(3))))))) }
        assertNull(vote.genres(30))
        ready = false
        assertNull(vote.genres(30))
    }
}
