package app.siansa.net

import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The listening history read back from Siansa Beats (the owner, 12 Nov): with listens going to Siansa Beats alone,
 * the Time machine, the reports, Rediscover and the taste profile read it there.
 */
class BeatsHistoryTest {
    @After fun forget() = BeatsSound.heard(emptySet())

    private fun json(text: String) = AppJson.parseToJsonElement(text) as JsonObject

    private val songs = json(
        """{"user":"max","kind":"songs","from":0,"to":9,"listens":3,"top":[
           {"artist":"DJ Seinfeld","title":"She Loves Me","album":"Mirrors","listens":12,"id":"abc","song":"s1"},
           {"artist":"Someone","title":"Elsewhere","album":null,"listens":1,"id":null,"song":null},
           {"artist":"","title":"No artist","listens":4}, {"artist":"No title","listens":4}, "not a row"]}""",
    )

    @Test fun theMostListenedSongsArtistsAndAlbums() {
        assertEquals(listOf(Played("DJ Seinfeld", "She Loves Me", 12), Played("Someone", "Elsewhere", 1)), beatsTop(songs, "songs"))
        val artists = json("""{"top":[{"artist":"Moby","listens":40,"song":null},{"artist":"Air","listens":7}]}""")
        assertEquals(listOf(Played("Moby", null, 40), Played("Air", null, 7)), beatsTop(artists, "artists"))
        // An album's name is its title, as Koito's rows have it, so both are matched to the library the same way.
        val albums = json("""{"top":[{"album":"Play","artist":"Moby","listens":30,"song":"s2"},{"artist":"No album","listens":2}]}""")
        assertEquals(listOf(Played("Moby", "Play", 30)), beatsTop(albums, "albums"))
        assertTrue(beatsTop(null, "songs").isEmpty())
        assertTrue(beatsTop(json("""{"error":"no"}"""), "songs").isEmpty())
    }

    @Test fun aStretchInNumbers() {
        val hours = (0 until 24).joinToString(",") { if (it == 22) "5" else "0" }
        val t = beatsTotals(json("""{"user":"max","listens":5,"seconds":1260,"guessed":1,"artists":3,"songs":4,"albums":2,"new_artists":1,"hours":[$hours]}"""))!!
        assertEquals(HistoryApi.Totals(minutes = 21, plays = 5, artists = 3, tracks = 4, albums = 2, newArtists = 1, newTracks = 0), t.totals)
        assertEquals(5, t.hours[22])
        assertEquals(24, t.hours.size)
        // Not an answer (an older Siansa Beats says "not found"): nothing, so the report falls back.
        assertNull(beatsTotals(json("""{"error":"Not found"}""")))
        assertNull(beatsTotals(null))
        // Hours missing or short: none counted, never a crash drawing them.
        assertEquals(List(24) { 0 }, beatsTotals(json("""{"listens":2,"hours":[1,2]}"""))!!.hours)
    }

    @Test fun itAsksForTheListenerSignedInOnTheMusicServer() = runBlocking {
        val asked = mutableListOf<String>()
        val history = BeatsHistory({ path -> asked += path; songs as JsonElement }, { AppSettings(navidromeUser = " max & co ") })
        assertEquals(2, history.top("songs", 100, 200, 150).size)
        assertEquals("/api/v1/listens/top?user=max+%26+co&kind=songs&from=100&to=200&limit=150", asked.single())
        asked.clear()
        runCatching { history.totals(100, 200, "Europe/Helsinki") }
        assertEquals("/api/v1/listens/totals?user=max+%26+co&from=100&to=200&tz=Europe%2FHelsinki", asked.single())
    }

    @Test fun itIsTheHistoryOnlyWhileListensGoThere() {
        val takes = BeatsApi.Capabilities.parse(json("""{"version":"0.33.0","api":1,"features":{"scrobbling":{"available":true,"listens":12}}}"""))
        BeatsSound.heard(takes.sound)
        val set = AppSettings(beatsUrl = "http://beats:8090", scrobbleBeats = true)
        assertTrue(BeatsHistory({ JsonObject(emptyMap()) }, { set }).on)
        assertFalse(BeatsHistory({ JsonObject(emptyMap()) }, { set.copy(scrobbleBeats = false) }).on)
        assertFalse(BeatsHistory({ JsonObject(emptyMap()) }, { AppSettings(scrobbleBeats = true) }).on)  // no Siansa Beats set up
    }
}
