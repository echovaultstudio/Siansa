package app.siansa.net

import app.siansa.data.AppJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sound searches are logged with what was asked and what AudioMuse answered (issue #229). */
class SoundSearchLogTest {
    private val hits = listOf(
        ClapHit("1", "Streets", "Doja Cat", "Hot Pink", 0.31234),
        ClapHit("2", "Space Enough to Grow", "Of Mice & Men", "Restoring Force", 0.29),
    )

    @Test fun wordsConceptsAndBestResults() {
        val line = AudioMuseApi.searchSummary("sad acoustic", listOf(Steer("sad", true), Steer("electronic", false)), hits, emptyList())
        assertEquals(
            "Search \"sad acoustic\" · concepts +sad, −electronic · 2 songs · best: Streets – Doja Cat (0.312); " +
                "Space Enough to Grow – Of Mice & Men (0.290)",
            line,
        )
    }

    @Test fun nothingFoundAndAudioMuseNotes() {
        val root = AppJson.parseToJsonElement("""{"results":[],"steering_warnings":["unknown concept 'acustic' ignored"]}""").jsonObject
        val line = AudioMuseApi.searchSummary("sad", emptyList(), emptyList(), AudioMuseApi.warningsOf(root))
        assertEquals("Search \"sad\" · concepts none · 0 songs · AudioMuse: unknown concept 'acustic' ignored", line)
        assertTrue(AudioMuseApi.warningsOf(AppJson.parseToJsonElement("""{"results":[]}""").jsonObject).isEmpty())
    }
}
