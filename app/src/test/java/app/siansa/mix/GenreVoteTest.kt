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
    private var savedText: String? = null
    private val vote = GenreVote({ path -> asked += path; reply(path) }, { ready }, { time }, { savedText }, { savedText = it })

    @Test fun asksThePluginForTheGenreAsShown() = runBlocking {
        assertEquals(listOf("a", "b"), vote.songIds("Electronic / Trance", 300))
        assertEquals("/plugins/siansa_beats/api/genre?name=Electronic%20%2F%20Trance&limit=300", asked.single())
    }

    @Test fun withoutAudioMuseItIsntAsked() = runBlocking {
        ready = false
        assertNull(vote.songIds("Trance", 300))
        assertTrue(asked.isEmpty())
    }

    @Test fun aMissingServiceIsLeftAloneForAWhileAfterSeveralMisses() = runBlocking {
        reply = { throw IllegalStateException("AudioMuse answered HTTP 502") }
        // One slow or failed answer doesn't turn the vote off: only GenreVote.MISSES in a row do.
        repeat(GenreVote.MISSES) { assertNull(vote.songIds("Trance", 300)) }
        assertEquals(GenreVote.MISSES, asked.size)
        assertNull(vote.songIds("Trance", 300))
        assertEquals(GenreVote.MISSES, asked.size)
        time += GenreVote.BACK_OFF_MS + 1
        vote.songIds("Trance", 300)
        assertEquals(GenreVote.MISSES + 1, asked.size)
    }

    @Test fun theTagsPlayWhenTheVoteHasTooFewSongs() = runBlocking {
        val tagged = listOf(Song(id = "t", title = "Tagged", artist = "x"))
        val many = (1..GenreVote.MIN_VOTED).map { Song(id = "v$it", title = "v$it", artist = "x") }
        assertEquals(many, GenreVote.pick("Trance", many) { tagged })
        assertEquals(tagged, GenreVote.pick("Trance", many.take(5)) { tagged })
        assertEquals(tagged, GenreVote.pick("Trance", null) { tagged })  // no Siansa Beats: as always
    }

    private fun manyGenres(extra: List<JsonObject> = emptyList()) = JsonObject(mapOf("genres" to JsonArray(
        (1..GenreVote.MIN_GENRES).map { JsonObject(mapOf("name" to JsonPrimitive("Style $it"), "songs" to JsonPrimitive(100 - it))) } + extra,
    )))

    @Test fun everyVotedGenreComesWithItsRadioNameAndIsSaved() = runBlocking {
        reply = { manyGenres(listOf(JsonObject(mapOf("name" to JsonPrimitive("Hip Hop / Electro"), "songs" to JsonPrimitive(3))))) }
        val genres = vote.refresh()!!
        assertEquals("Style 1", genres.first().name)
        assertEquals(99, genres.first().songCount)
        assertEquals("Hip Hop / Electro", genres.last().name)
        assertEquals("/plugins/siansa_beats/api/genres?limit=${GenreVote.MOST_GENRES}", asked.last())
        // Saved: a new start shows it at once, without asking.
        val again = GenreVote({ throw IllegalStateException("not asked") }, { true }, { time }, { savedText }, {})
        assertEquals(genres, again.saved())
        assertTrue(!again.stale())
        time += GenreVote.REFRESH_MS + 1
        assertTrue(again.stale())
    }

    @Test fun theSavedListStaysWhenSiansaBeatsDoesntAnswer() = runBlocking {
        reply = { manyGenres() }
        val first = vote.refresh()!!
        reply = { throw IllegalStateException("AudioMuse answered HTTP 502") }
        assertNull(vote.refresh())
        assertEquals(first, vote.saved())
    }

    @Test fun withoutAudioMuseTheTagsAreShownEvenWithASavedList() = runBlocking {
        reply = { manyGenres() }
        vote.refresh()
        ready = false
        assertNull(vote.saved())  // Siansa Beats isn't needed: the tags' genres show
        assertNull(vote.refresh())
    }

    @Test fun tooFewVotedGenresShowTheTags() = runBlocking {
        reply = { JsonObject(mapOf("genres" to JsonArray(listOf(JsonObject(mapOf("name" to JsonPrimitive("Trance"), "songs" to JsonPrimitive(3))))))) }
        assertNull(vote.refresh())
        assertNull(vote.saved())
    }

    @Test fun aSlowAnswerKeepsTheSavedList() = runBlocking {
        reply = { manyGenres() }
        vote.refresh()
        val slow = GenreVote({ kotlinx.coroutines.delay(GenreVote.WAIT_MS + 1_000); manyGenres() }, { true }, { time }, { savedText }, {})
        assertNull(slow.refresh())
        assertEquals(GenreVote.MIN_GENRES, slow.saved()!!.size)
    }

    @Test fun aGenresDescriptionComesFromSiansaBeatsAndIsKept() = runBlocking {
        reply = { path ->
            if (path.startsWith(GenreVote.ABOUT_PATH)) JsonObject(mapOf("about" to JsonObject(mapOf(
                "summary" to JsonPrimitive("Dubstep is a genre of electronic dance music."),
                "url" to JsonPrimitive("https://en.wikipedia.org/wiki/Dubstep"),
                "source" to JsonPrimitive("Wikipedia"),
            )))) else JsonObject(emptyMap())
        }
        val about = vote.about("Dubstep")!!
        assertEquals("Wikipedia", about.source)
        assertEquals("/plugins/siansa_beats/api/genre/about?name=Dubstep", asked.last())
        vote.about("Dubstep")
        assertEquals(1, asked.size)  // kept
    }

    @Test fun noDescriptionWithoutSiansaBeats() = runBlocking {
        reply = { JsonObject(mapOf("about" to kotlinx.serialization.json.JsonNull)) }
        assertNull(vote.about("Mercury"))
        ready = false
        assertNull(vote.about("Dubstep"))
    }
}
