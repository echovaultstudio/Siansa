package app.siansa.playback

import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import app.siansa.net.ApiException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Siansa Beats' beat grids laid over the plugin's profiles (Siansa Dev, an experiment). */
class BeatGridsTest {
    /** As Siansa Beats 0.8.0 answers GET /api/grids for its test song at 140 BPM. */
    private fun grid(id: String, bpm: Double = 140.0) = """{"track_id":"$id","beats_until_ms":99000,""" +
        """"intro_bpm":$bpm,"intro_beat_ms":0,"intro_bar_ms":0,"intro_phrase_ms":6857,"intro_phrase_bars":8,"intro_rhythm":0.972,"intro_steady":1,""" +
        """"outro_bpm":$bpm,"outro_beat_ms":99000,"outro_bar_ms":97714,"outro_phrase_ms":89143,"outro_phrase_bars":8,"outro_rhythm":0.985,"outro_steady":1}"""

    private fun grids(vararg ids: String, missing: List<String> = emptyList()) = AppJson.parseToJsonElement(
        """{"version":"0.8.0","grids":[${ids.joinToString(",") { grid(it) }}],"missing":[${missing.joinToString(",") { "\"$it\"" }}]}""",
    ) as JsonObject

    /** The plugin's profile of a song with its own (wrong) reading, as Control's 187.1. */
    private fun plugin(id: String) = TrackProfile(
        id, -9.0, emptyList(), emptyList(), 210_000,
        intro = BeatEnd(187.1, 120.0, 120.0, 0.9f, 0.8f), outro = BeatEnd(140.1, 200_000.0, 199_000.0, 0.9f, 0.8f), beatsUntilMs = 150_000,
    )

    @Test fun readsSiansaBeatsGridsAsThePluginsFields() {
        val parsed = BeatsGrids.parse(grids("a", "b"))
        assertEquals(setOf("a", "b"), parsed.keys)
        val a = parsed.getValue("a")
        assertEquals(140.0, a.intro!!.bpm, 0.001)
        assertEquals(6857.0, a.intro!!.phraseMs!!, 0.1)
        assertEquals(8, a.intro!!.phraseBars)
        assertEquals(89143.0, a.outro!!.phraseMs!!, 0.1)
        assertEquals(97714.0, a.outro!!.barMs!!, 0.1)
        assertTrue(a.outro!!.matchable)
        assertEquals(99_000L, a.beatsUntilMs)
        assertTrue(BeatsGrids.parse(AppJson.parseToJsonElement("""{"grids":"no"}""") as JsonObject).isEmpty())
    }

    @Test fun thePluginKeepsItsGridsUnlessSiansaBeatsIsChosen() {
        val beats = BeatsGrids.parse(grids("a")).getValue("a")
        assertEquals(187.1, BeatsGrids.merge("a", plugin("a"), beats, BeatGridSource.PLUGIN)!!.intro!!.bpm, 0.001)
    }

    @Test fun siansaBeatsFirstTakesItsGridAndKeepsThePluginsLoudness() {
        val beats = BeatsGrids.parse(grids("a")).getValue("a")
        val merged = BeatsGrids.merge("a", plugin("a"), beats, BeatGridSource.BEATS)!!
        assertEquals(140.0, merged.intro!!.bpm, 0.001)
        assertEquals(99_000L, merged.beatsUntilMs)
        assertEquals(-9.0, merged.refLufs!!, 0.0)
        assertEquals(210_000L, merged.durationMs)
        // A song Siansa Beats hasn't done keeps the plugin's grid.
        assertEquals(187.1, BeatsGrids.merge("b", plugin("b"), null, BeatGridSource.BEATS)!!.intro!!.bpm, 0.001)
        // A song only Siansa Beats has: its grid, no loudness.
        val only = BeatsGrids.merge("c", null, beats, BeatGridSource.BEATS)!!
        assertNull(only.refLufs)
        assertEquals(140.0, only.outro!!.bpm, 0.001)
    }

    @Test fun siansaBeatsOnlyIgnoresThePluginsBeatReadings() {
        val none = BeatsGrids.merge("b", plugin("b"), null, BeatGridSource.BEATS_ONLY)!!
        assertNull(none.intro)
        assertNull(none.outro)
        assertNull(none.beatsUntilMs)
        assertEquals(-9.0, none.refLufs!!, 0.0)  // loudness and fades still from the plugin
        assertNull(BeatsGrids.merge("x", null, null, BeatGridSource.BEATS_ONLY))
    }

    // ---- the profile store, with the plugin and Siansa Beats answering ----

    private var time = 1_000_000L
    private var settings = AppSettings(
        audioMuseUrl = "http://am:8000", loudnessSource = LoudnessSource.PLUGIN,
        beatGridSource = BeatGridSource.BEATS, beatsUrl = "http://beats:8090",
    )
    private val pluginAsked = mutableListOf<String>()
    private val beatsAsked = mutableListOf<String>()
    private var beatsDown = false

    private fun pluginAnswer(path: String): JsonElement {
        val ids = path.substringAfter("ids=").split(",")
        val profiles = ids.filter { it != "nowhere" }.joinToString(",") {
            """{"track_id":"$it","duration_ms":210000,"ref_lufs":-9.0,"start_ramp":"","end_ramp":"",""" +
                """"intro_bpm":187.1,"intro_beat_ms":120,"intro_rhythm":0.9,"intro_steady":0.8,"outro_bpm":140.1,"outro_beat_ms":200000,"outro_rhythm":0.9,"outro_steady":0.8}"""
        }
        return AppJson.parseToJsonElement("""{"schema_version":1,"profiles":[$profiles],"pending":[]}""")
    }

    private fun beatsAnswer(path: String): JsonElement {
        if (beatsDown) throw ApiException("Siansa Beats answered HTTP 502", 502)
        // Siansa Beats has only the songs of its playlist: here "a" and "nowhere" (a song the plugin hasn't measured).
        val ids = path.substringAfter("ids=").split(",").filter { it == "a" || it == "nowhere" }
        return grids(*ids.toTypedArray())
    }

    private val store = ProfileStore(
        { path -> pluginAsked += path; pluginAnswer(path) }, { settings }, { time },
        beatsGet = { path -> beatsAsked += path; beatsAnswer(path) },
    )

    @Test fun fetchesBothAndLaysSiansaBeatsGridsOverThePlugins() = runBlocking {
        val found = store.fetch(listOf("a", "b", "nowhere"))
        assertEquals(setOf("a", "b", "nowhere"), found.keys)
        assertEquals(140.0, found.getValue("a").intro!!.bpm, 0.001)  // Siansa Beats'
        assertEquals(187.1, found.getValue("b").intro!!.bpm, 0.001)  // the plugin's: Siansa Beats hasn't done it
        assertNull(found.getValue("nowhere").refLufs)  // only Siansa Beats has it
        assertTrue(beatsAsked.single().startsWith("/api/grids?ids="))
        store.fetch(listOf("a", "b"))
        assertEquals(1, beatsAsked.size)  // kept; "b" asked again only after a while
    }

    @Test fun switchingWhereGridsComeFromTakesAtOnce() = runBlocking {
        store.fetch(listOf("a"))
        assertEquals(140.0, store.cached("a")!!.intro!!.bpm, 0.001)
        settings = settings.copy(beatGridSource = BeatGridSource.PLUGIN)
        assertEquals(187.1, store.cached("a")!!.intro!!.bpm, 0.001)
        settings = settings.copy(beatGridSource = BeatGridSource.BEATS_ONLY)
        store.fetch(listOf("b"))
        assertNull(store.cached("b")!!.intro)
        assertEquals(140.0, store.cached("a")!!.intro!!.bpm, 0.001)
    }

    @Test fun siansaBeatsDownLeavesThePluginsGridsAndIsAskedAgainLater() = runBlocking {
        beatsDown = true
        val found = store.fetch(listOf("a"))
        assertEquals(187.1, found.getValue("a").intro!!.bpm, 0.001)
        assertNotNull(store.beatsProblem)
        assertEquals(1, pluginAsked.size)
        beatsDown = false
        store.fetch(listOf("a"))
        assertEquals(1, beatsAsked.size)  // backed off
        time += ProfileStore.BACK_OFF_MS + 1
        store.fetch(listOf("a"))
        assertEquals(140.0, store.cached("a")!!.intro!!.bpm, 0.001)
        assertNull(store.beatsProblem)
        assertEquals(1, pluginAsked.size)  // the plugin's profile was kept all along
    }

    @Test fun withoutAnAddressSiansaBeatsIsNeverAsked() = runBlocking {
        settings = settings.copy(beatsUrl = "")
        val found = store.fetch(listOf("a"))
        assertEquals(187.1, found.getValue("a").intro!!.bpm, 0.001)
        assertTrue(beatsAsked.isEmpty())
        assertFalse(store.checkBeats().ok)
    }

    @Test fun songsOfEveryServerAreAskedAndMatchedByTheirOwnId() = runBlocking {
        // Siansa Beats reads a Navidrome playlist, but the grids are asked for and laid over by the song's id as the
        // app has it, so Emby's and Jellyfin's ids are asked the same way (and just aren't found there).
        for (server in app.siansa.servers.EveryServer.all) {
            val asked = mutableListOf<String>()
            val local = ProfileStore(
                { path -> pluginAnswer(path) }, { settings }, { time },
                beatsGet = { path -> asked += path; grids(*path.substringAfter("ids=").split(",").toTypedArray()) },
            )
            val found = local.fetch(listOf(server.songId))
            assertEquals(server.name, "/api/grids?ids=" + java.net.URLEncoder.encode(server.songId, "UTF-8"), asked.single())
            assertEquals(server.name, 140.0, found.getValue(server.songId).intro!!.bpm, 0.001)
        }
    }

    @Test fun theTestSaysWhetherSiansaBeatsAnswers() = runBlocking {
        assertTrue(store.checkBeats().ok)
        beatsDown = true
        val result = store.checkBeats()
        assertFalse(result.ok)
        assertTrue(result.message, "502" in result.message)
    }
}
