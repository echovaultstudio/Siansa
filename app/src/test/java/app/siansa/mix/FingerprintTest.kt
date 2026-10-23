package app.siansa.mix

import app.siansa.data.AppJson
import app.siansa.net.AudioMuseApi
import app.siansa.net.TrackFeatures
import app.siansa.ui.fingerprintFacts
import app.siansa.ui.fingerprintStats
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Your sonic fingerprint and the Sound search's concept groups (issue #216). */
class FingerprintTest {
    private fun track(id: String, tempo: Double?, scale: String?, moods: String, genres: String) =
        TrackFeatures(id, "A", tempo, "C", scale, AudioMuseApi.parseScores(moods), AudioMuseApi.parseScores(genres))

    @Test fun packedScoresAreRead() {
        assertEquals(mapOf("rock" to 0.53, "pop" to 0.2), AudioMuseApi.parseScores("rock:0.53, pop:0.2,broken,:1,x:y"))
        assertEquals(emptyMap<String, Double>(), AudioMuseApi.parseScores(null))
    }

    @Test fun theProfileAveragesMoodsAndGenres() {
        val tracks = listOf(
            track("1", 120.0, "minor", "danceable:0.8,sad:0.2,relaxed:0.1", "electronic:0.6,house:0.5"),
            track("2", 124.0, "minor", "danceable:0.6,sad:0.4,relaxed:0.3", "electronic:0.4,ambient:0.3"),
            track("3", 90.0, "major", "danceable:0.4,sad:0.0,relaxed:0.5", "electronic:0.5,house:0.1"),
        )
        val p = FingerprintProfile.of("2026-10-19", tracks, listOf("1", "2", "3"))
        assertEquals(FingerprintProfile.MOODS, p.moods.map { it.first })
        assertEquals(0.6, p.moods.first { it.first == "danceable" }.second, 1e-9)
        assertEquals(0.0, p.moods.first { it.first == "aggressive" }.second, 1e-9)
        assertEquals("electronic", p.genres.first())
        assertEquals(listOf("electronic", "house", "ambient"), p.genres)
        assertEquals(120, p.bpm)
        assertEquals("minor", p.scale)
        assertEquals("Around 120 BPM · mostly minor keys · 3 songs", fingerprintFacts(p))
        // Its own page under Your listening (issue #255) shows these as tiles.
        assertEquals(listOf("120" to "BPM", "Minor" to "Key", "Danceable" to "Top mood", "3" to "Songs"), fingerprintStats(p))
    }

    @Test fun noClearScaleAndNothingAnalysed() {
        val mixed = listOf(track("1", null, "minor", "", ""), track("2", null, "major", "", ""))
        assertNull(FingerprintProfile.of("d", mixed, listOf("1", "2")).scale)
        val empty = FingerprintProfile.of("d", emptyList(), emptyList())
        assertNull(empty.bpm)
        assertEquals(emptyList<String>(), empty.genres)
        assertNull(fingerprintFacts(empty))
        assertEquals(emptyList<Pair<String, String>>(), fingerprintStats(empty))
    }

    @Test fun theProfileIsSavedAndReadBack() {
        val p = FingerprintProfile.of("d", listOf(track("1", 100.0, "major", "happy:0.7", "pop:0.9")), listOf("1"))
        assertEquals(p, AppJson.decodeFromString(FingerprintProfile.serializer(), AppJson.encodeToString(FingerprintProfile.serializer(), p)))
    }

    @Test fun everyConceptGroupIsOffered() {
        val root = AppJson.parseToJsonElement(
            """{"available":true,"max_terms":10,"categories":[
              {"category":"genre","label":"Genre","terms":[{"term":"trance","grounding":0.1},{"term":"house","grounding":0.4}]},
              {"category":"instrument","label":"Instrument","terms":[{"term":"piano","grounding":0.3}]},
              {"category":"vocals","label":"Vocals","terms":[]},
              {"category":"mood","label":"Mood","terms":[{"term":"dark","grounding":0.2}]}]}""",
        ).jsonObject
        val c = AudioMuseApi.parseCatalogue(root)
        assertEquals(listOf("genre", "instrument", "mood"), c.categories.map { it.id })
        // Best-understood first.
        assertEquals(listOf("house", "trance"), c.categories.first().terms)
        assertEquals(10, c.maxTerms)
        assertEquals(emptyList<Any>(), AudioMuseApi.parseCatalogue(AppJson.parseToJsonElement("""{"available":false}""").jsonObject).categories)
    }
}
