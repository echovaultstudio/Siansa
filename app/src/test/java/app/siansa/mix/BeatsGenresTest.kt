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

/** The genres Siansa Beats hears and their songs, and the songs' tags whenever it can't be asked. */
class BeatsGenresTest {
    private var time = 1_000_000L
    private var ready = true
    private val asked = mutableListOf<String>()
    private var reply: (String) -> JsonElement = {
        JsonObject(mapOf("ids" to JsonArray(listOf("a", "b").map { JsonPrimitive(it) }),
            "styles" to JsonArray(listOf(JsonPrimitive("Electronic---Trance"))), "genres" to JsonArray(emptyList())))
    }
    private var savedText: String? = null
    private val beats = BeatsGenres({ path -> asked += path; reply(path) }, { ready }, { time }, { savedText }, { savedText = it })

    @Test fun asksThePluginForTheGenreAsShown() = runBlocking {
        assertEquals(listOf("a", "b"), beats.songIds("Electronic / Trance", 300))
        assertEquals("/plugins/siansa_beats/api/genre?name=Electronic%20%2F%20Trance&limit=300", asked.single())
    }

    @Test fun withoutAudioMuseItIsntAsked() = runBlocking {
        ready = false
        assertNull(beats.songIds("Trance", 300))
        assertTrue(asked.isEmpty())
    }

    @Test fun aMissingServiceIsLeftAloneForAWhileAfterSeveralMisses() = runBlocking {
        reply = { throw IllegalStateException("AudioMuse answered HTTP 502") }
        // One slow or failed answer doesn't turn Siansa Beats' genres off: only BeatsGenres.MISSES in a row do.
        repeat(BeatsGenres.MISSES) { assertNull(beats.songIds("Trance", 300)) }
        assertEquals(BeatsGenres.MISSES, asked.size)
        assertNull(beats.songIds("Trance", 300))
        assertEquals(BeatsGenres.MISSES, asked.size)
        time += BeatsGenres.BACK_OFF_MS + 1
        beats.songIds("Trance", 300)
        assertEquals(BeatsGenres.MISSES + 1, asked.size)
    }

    @Test fun aGenresSongsAreSiansaBeatsOwnHoweverFew() = runBlocking {
        val tagged = (1..40).map { Song(id = "t$it", title = "t$it", artist = "x") }
        val heard = (1..12).map { Song(id = "v$it", title = "v$it", artist = "x") }
        // Straight from Siansa Beats (the owner, 11 Nov): never swapped for the tags because the tags have more.
        assertEquals(heard, BeatsGenres.pick("Trance", heard) { tagged })
        assertEquals(heard.take(1), BeatsGenres.pick("Surf", heard.take(1)) { tagged })
        assertEquals(heard, BeatsGenres.pick("Yé-Yé", heard) { emptyList() })
        // A genre that's only a tag (opened from an album's page) is none of Siansa Beats' styles: its tagged songs.
        assertEquals(tagged, BeatsGenres.pick("Soundtrack", emptyList()) { tagged })
        assertEquals(tagged, BeatsGenres.pick("Trance", null) { tagged })  // no Siansa Beats: as always
    }

    private fun manyGenres(extra: List<JsonObject> = emptyList()) = JsonObject(mapOf("genres" to JsonArray(
        (1..BeatsGenres.MIN_GENRES).map { JsonObject(mapOf("name" to JsonPrimitive("Style $it"), "songs" to JsonPrimitive(100 - it))) } + extra,
    )))

    @Test fun everyGenreComesWithItsRadioNameAndIsSaved() = runBlocking {
        reply = { manyGenres(listOf(JsonObject(mapOf("name" to JsonPrimitive("Hip Hop / Electro"), "songs" to JsonPrimitive(3))))) }
        val genres = beats.refresh()!!
        assertEquals("Style 1", genres.first().name)
        assertEquals(99, genres.first().songCount)
        assertEquals("Hip Hop / Electro", genres.last().name)
        assertEquals("/plugins/siansa_beats/api/genres?limit=${BeatsGenres.MOST_GENRES}", asked.last())
        // Saved: a new start shows it at once, without asking.
        val again = BeatsGenres({ throw IllegalStateException("not asked") }, { true }, { time }, { savedText }, {})
        assertEquals(genres, again.saved())
        assertTrue(!again.stale())
        time += BeatsGenres.REFRESH_MS + 1
        assertTrue(again.stale())
    }

    @Test fun genresOnlyInHiddenLibrariesAreLeftOut() = runBlocking {
        // The owner, 10 Nov: Kayōkyoku showed, with 0 songs, as all of it is in a library hidden on the phone.
        fun g(name: String, songs: Int, libraries: Map<String, Int>?) = JsonObject(buildMap {
            put("name", JsonPrimitive(name)); put("songs", JsonPrimitive(songs))
            libraries?.let { by -> put("libraries", JsonObject(by.mapValues { JsonPrimitive(it.value) })) }
        })
        reply = { manyGenres(listOf(
            g("Kayōkyoku", 40, mapOf("3" to 40)),
            g("Indie Pop", 200, mapOf("1" to 20, "3" to 180)),
            g("Older Beats", 5, null),
        )) }
        val shown = beats.refresh(setOf("1"))!!.associate { it.name to it.songCount }
        assertTrue("Kayōkyoku" !in shown)
        assertEquals(20, shown["Indie Pop"])  // its songs in the shown library
        assertEquals(5, shown["Older Beats"])  // an older Siansa Beats said no libraries: kept as it was
        // Every library (none chosen): all of them, as counted.
        assertEquals(200, beats.saved()!!.first { it.name == "Indie Pop" }.songCount)
        // Saved with the libraries: a new start leaves it out at once too.
        val again = BeatsGenres({ throw IllegalStateException("not asked") }, { true }, { time }, { savedText }, {})
        assertTrue(again.saved(setOf("1"))!!.none { it.name == "Kayōkyoku" })
        assertTrue(again.saved(setOf("3"))!!.any { it.name == "Kayōkyoku" })
    }

    @Test fun theSavedListStaysWhenSiansaBeatsDoesntAnswer() = runBlocking {
        reply = { manyGenres() }
        val first = beats.refresh()!!
        reply = { throw IllegalStateException("AudioMuse answered HTTP 502") }
        assertNull(beats.refresh())
        assertEquals(first, beats.saved())
    }

    @Test fun withoutAudioMuseTheTagsAreShownEvenWithASavedList() = runBlocking {
        reply = { manyGenres() }
        beats.refresh()
        ready = false
        assertNull(beats.saved())  // Siansa Beats isn't needed: the tags' genres show
        assertNull(beats.refresh())
    }

    @Test fun tooFewGenresShowTheTags() = runBlocking {
        reply = { JsonObject(mapOf("genres" to JsonArray(listOf(JsonObject(mapOf("name" to JsonPrimitive("Trance"), "songs" to JsonPrimitive(3))))))) }
        assertNull(beats.refresh())
        assertNull(beats.saved())
    }

    @Test fun aSlowAnswerKeepsTheSavedList() = runBlocking {
        reply = { manyGenres() }
        beats.refresh()
        val slow = BeatsGenres({ kotlinx.coroutines.delay(BeatsGenres.WAIT_MS + 1_000); manyGenres() }, { true }, { time }, { savedText }, {})
        assertNull(slow.refresh())
        assertEquals(BeatsGenres.MIN_GENRES, slow.saved()!!.size)
    }

    @Test fun aGenresDescriptionComesFromSiansaBeatsAndIsKept() = runBlocking {
        reply = { path ->
            if (path.startsWith(BeatsGenres.ABOUT_PATH)) JsonObject(mapOf("about" to JsonObject(mapOf(
                "summary" to JsonPrimitive("Dubstep is a genre of electronic dance music."),
                "url" to JsonPrimitive("https://en.wikipedia.org/wiki/Dubstep"),
                "source" to JsonPrimitive("Wikipedia"),
            )))) else JsonObject(emptyMap())
        }
        val about = beats.about("Dubstep")!!
        assertEquals("Wikipedia", about.source)
        assertEquals("/plugins/siansa_beats/api/genre/about?name=Dubstep", asked.last())
        beats.about("Dubstep")
        assertEquals(1, asked.size)  // kept
    }

    @Test fun aDescriptionStillBeingLookedUpIsAskedForAgain() = runBlocking {
        var asks = 0
        reply = { path ->
            if (!path.startsWith(BeatsGenres.ABOUT_PATH)) JsonObject(emptyMap())
            else if (++asks == 1) JsonObject(mapOf("about" to kotlinx.serialization.json.JsonNull, "pending" to JsonPrimitive(true)))
            else JsonObject(mapOf("about" to JsonObject(mapOf("summary" to JsonPrimitive("Nu metal is a subgenre of alternative metal."),
                "source" to JsonPrimitive("Wikipedia")))))
        }
        assertEquals("Nu metal is a subgenre of alternative metal.", beats.about("Nu Metal")!!.summary)
        assertEquals(2, asks)
    }

    @Test fun descriptionsAreKeptOnThePhone() = runBlocking {
        // The owner, 10 Nov: genre descriptions weren't kept at all; each visit asked again.
        var kept: String? = null
        var asks = 0
        fun fresh() = BeatsGenres({ path ->
            asks++
            when {
                "Polka" in path -> JsonObject(mapOf("about" to kotlinx.serialization.json.JsonNull, "status" to JsonPrimitive("missing")))
                "Surf" in path -> JsonObject(mapOf("about" to kotlinx.serialization.json.JsonNull, "status" to JsonPrimitive("failed")))
                else -> JsonObject(mapOf("about" to JsonObject(mapOf("summary" to JsonPrimitive("Trance is fast."), "source" to JsonPrimitive("Wikipedia"))), "status" to JsonPrimitive("found")))
            }
        }, { true }, { time }, loadAbouts = { kept }, saveAbouts = { kept = it })
        val first = fresh()
        assertEquals("Trance is fast.", first.about("Trance")!!.summary)
        assertNull(first.about("Polka"))
        assertNull(first.about("Surf"))
        assertEquals(3, asks)
        // A new start: the description and the "nothing on it" come from the phone; a failure is asked again.
        val again = fresh()
        assertEquals("Trance is fast.", again.keptAbout("Trance")!!.summary)
        assertEquals("Trance is fast.", again.about("Trance")!!.summary)
        assertNull(again.about("Polka"))
        assertEquals(3, asks)
        again.about("Surf")
        assertEquals(4, asks)
        // A day on, "nothing on it" is asked again; the description stays a month.
        time += KeptAbout.ABOUT_MISSING_MS + 1
        again.about("Polka")
        again.about("Trance")
        assertEquals(5, asks)
    }

    @Test fun noDescriptionWithoutSiansaBeats() = runBlocking {
        reply = { JsonObject(mapOf("about" to kotlinx.serialization.json.JsonNull)) }
        assertNull(beats.about("Mercury"))
        ready = false
        assertNull(beats.about("Dubstep"))
    }
}
