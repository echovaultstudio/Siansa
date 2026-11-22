package app.siansa.net

import app.siansa.data.AppJson
import app.siansa.net.BeatsMixSettings.Kind
import app.siansa.net.BeatsMixSettings.Value
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What goes into Siansa Beats' mixes (0.56; the owner, 17 Nov): read, grouped and changed as Siansa Beats says. */
class BeatsMixSettingsTest {
    private val answer = """{"schema_version":1,"user":"max","keep_days":7,
        "settings":{"now":true,"now_count":4,"daily":false,"daily_count":5,"size":40,"leave_genres":["Metal"],"ai_names":true,"later":true},
        "described":[
          {"name":"now","kind":"on","default":true,"low":null,"high":null,"label":"For now: mixes for this part of the day"},
          {"name":"now_count","kind":"number","default":4,"low":1,"high":6,"label":"How many For now mixes"},
          {"name":"daily","kind":"on","default":true,"low":null,"high":null,"label":"Made for you"},
          {"name":"daily_count","kind":"number","default":5,"low":1,"high":8,"label":"How many Made for you mixes"},
          {"name":"size","kind":"number","default":40,"low":10,"high":100,"label":"Songs in a mix"},
          {"name":"leave_genres","kind":"words","default":[],"low":null,"high":null,"label":"Genres to leave out"},
          {"name":"ai_names","kind":"on","default":true,"low":null,"high":null,"label":"Names and lines from the AI service"},
          {"name":"later","kind":"on","default":false,"low":null,"high":null,"label":"A setting this app doesn't know yet"},
          {"name":"odd","kind":"colour","default":1,"label":"A kind it can't draw"}]}"""

    private val sent = mutableListOf<Pair<String, String>>()
    private val client = BeatsMixSettings(
        { path -> sent += path to ""; AppJson.parseToJsonElement(answer) },
        { path, body -> sent += path to body; AppJson.parseToJsonElement(answer) },
    )

    @Test fun readAndGroupedAsSiansaBeatsPageHasThem() {
        val got = BeatsMixSettings.parse(AppJson.parseToJsonElement(answer).jsonObject)
        assertEquals("max", got.user)
        assertEquals(7, got.keepDays)
        assertEquals(listOf("Which mixes", "What's in each", "Names", "More"), got.grouped().map { it.first })
        assertEquals(listOf("later"), got.grouped().last().second.map { it.name })  // a new one shows by itself
        assertFalse("a kind it can't draw is left out", got.settings.any { it.name == "odd" })
        assertEquals(Value(words = listOf("Metal")), got.value("leave_genres"))
        assertEquals(1 to 6, got.settings.first { it.name == "now_count" }.let { it.low to it.high })
        // How many Made for you mixes shows only while they're made.
        assertTrue(app.siansa.ui.shown("now_count", got))
        assertFalse(app.siansa.ui.shown("daily_count", got))
        assertTrue(app.siansa.ui.shown("size", got))
    }

    @Test fun onlyWhatChangedIsSentEachAsItsKind() = runBlocking {
        client.load("Max B")
        assertEquals("/api/v1/mixes/settings?user=Max+B", sent.single().first)
        val kinds = mapOf("daily" to Kind.ON, "size" to Kind.NUMBER, "leave_genres" to Kind.WORDS)
        client.save("max", mapOf("daily" to Value(on = true), "size" to Value(number = 25), "leave_genres" to Value(words = listOf("Metal", "Jazz"))), kinds, keepDays = 30)
        val (path, body) = sent.last()
        assertEquals("/api/v1/mixes/settings", path)
        assertEquals("""{"user":"max","settings":{"daily":true,"size":25,"leave_genres":["Metal","Jazz"],"keep_days":30}}""", body)
    }
}
