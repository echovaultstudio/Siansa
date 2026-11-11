package app.siansa.scrobble

import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import app.siansa.data.Song
import app.siansa.net.BeatsApi
import app.siansa.net.BeatsListens
import app.siansa.net.BeatsSound
import app.siansa.net.beatsScrobbleReady
import app.siansa.net.beatsTakesListens
import app.siansa.net.soundReady
import app.siansa.servers.EveryServer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Scrobble to Siansa Beats" (the owner, 11 Nov): offered when Siansa Beats takes listens, sent from every server. */
class ScrobbleToBeatsTest {
    @After fun forget() = BeatsSound.heard(emptySet())

    private fun caps(json: String) = BeatsApi.Capabilities.parse(AppJson.parseToJsonElement(json) as JsonObject)
    private val takes = """{"version":"0.29.0","api":1,"features":{"scrobbling":{"available":true,"listens":12}}}"""
    private val older = """{"version":"0.28.0","api":1,"features":{"sound":{"available":true,"answers":["/api/similar_tracks"]}}}"""

    @Test fun theSwitchIsOfferedOnceSiansaBeatsTakesListens() {
        val set = AppSettings(beatsUrl = "http://beats:8090")
        assertFalse(set.beatsTakesListens)  // not asked yet
        BeatsSound.heard(caps(older).sound)
        assertFalse(set.beatsTakesListens)  // 0.28: no listening history
        BeatsSound.heard(caps(takes).sound)
        assertTrue(set.beatsTakesListens)
        assertFalse(AppSettings().beatsTakesListens)  // no Siansa Beats set up
        // Taking listens doesn't mean it has the songs' sound: the sound features aren't offered for it.
        assertFalse(BeatsSound.available)
        assertFalse(set.soundReady)
    }

    @Test fun listensGoThereOnlyWhenSwitchedOn() {
        BeatsSound.heard(caps(takes).sound)
        val off = AppSettings(beatsUrl = "http://beats:8090", scrobbleNavidrome = true)
        assertFalse(off.beatsScrobbleReady)
        assertEquals(setOf(Scrobbler.NAVIDROME), scrobbleTargets(off))
        val on = off.copy(scrobbleBeats = true)
        assertEquals(setOf(Scrobbler.NAVIDROME, Scrobbler.BEATS), scrobbleTargets(on))
        // Switched on, then Siansa Beats taken away or downgraded: nothing is sent to it.
        assertEquals(setOf(Scrobbler.NAVIDROME), scrobbleTargets(on.copy(beatsUrl = "")))
        BeatsSound.heard(caps(older).sound)
        assertEquals(setOf(Scrobbler.NAVIDROME), scrobbleTargets(on))
        assertEquals(emptySet<String>(), scrobbleTargets(AppSettings(scrobbleNavidrome = false)))
    }

    @Test fun aListenCarriesTheServersOwnSongIdOnEveryServer() {
        for (server in EveryServer.all) {
            val song = Song(id = server.songId, title = "Kerala", artist = "Bonobo", album = "Migration", durationSec = 238)
            val user = BeatsListens.listener(server.settings)
            assertEquals(server.name, "max", user)
            val body = AppJson.parseToJsonElement(BeatsListens.listen(user, song, 1_762_862_400)) as JsonObject
            assertEquals(server.name, JsonPrimitive("max"), body["user"])
            val listen = (body["listens"] as JsonArray).single() as JsonObject
            assertEquals(server.name, mapOf(
                "id" to JsonPrimitive(server.songId), "at" to JsonPrimitive(1_762_862_400L), "artist" to JsonPrimitive("Bonobo"),
                "title" to JsonPrimitive("Kerala"), "album" to JsonPrimitive("Migration"), "duration" to JsonPrimitive(238),
            ), listen.toMap())
            val now = AppJson.parseToJsonElement(BeatsListens.now(user, song)) as JsonObject
            assertEquals(server.name, JsonPrimitive(server.songId), now["id"])
            assertEquals(server.name, JsonPrimitive("Kerala"), now["title"])
        }
    }

    @Test fun aHeartCarriesTheSongOnEveryServer() {
        for (server in EveryServer.all) {
            val song = Song(id = server.songId, title = "Kerala", artist = "Bonobo")
            val body = AppJson.parseToJsonElement(BeatsListens.love("max", song, loved = true)) as JsonObject
            assertEquals(server.name, mapOf("user" to JsonPrimitive("max"), "id" to JsonPrimitive(server.songId),
                "artist" to JsonPrimitive("Bonobo"), "title" to JsonPrimitive("Kerala"), "loved" to JsonPrimitive(true)), body.toMap())
        }
        val off = AppJson.parseToJsonElement(BeatsListens.love("max", Song(id = "x", title = "T", artist = "A"), loved = false)) as JsonObject
        assertEquals(JsonPrimitive(false), off["loved"])
    }

    @Test fun whatASongDoesntSayIsLeftOut() {
        val bare = Song(id = "x", title = "Untitled", artist = "Someone")
        val listen = ((AppJson.parseToJsonElement(BeatsListens.listen("", bare, 5)) as JsonObject)["listens"] as JsonArray).single() as JsonObject
        assertNull(listen["album"])
        assertNull(listen["duration"])
    }

    /** The owner's log, 11 Nov: "The sound of none of those songs is known yet" was shown as "too old". */
    @Test fun siansaBeatsOwnReasonIsSaidAndOnlyAMissingPathIsTooOld() {
        assertEquals("Siansa Beats: The sound of none of those songs is known yet",
            BeatsApi.message(404, "The sound of none of those songs is known yet"))
        assertTrue("too old" in BeatsApi.message(404, "not found"))
        assertTrue("too old" in BeatsApi.message(404, "no details"))
        assertTrue("too old" in BeatsApi.message(404, "<html><body>404</body></html>"))
    }
}
