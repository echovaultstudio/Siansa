package app.siansa.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** TEMPORARY (siansa-dev #63): what a Breakdown or Drop tap sends to Siansa Beats. */
class SectionButtonsTest {
    @Test fun aTapMarksAMomentBeforeItAndNeverBeforeTheStart() {
        assertEquals(60.75, sectionMarkSeconds(61_000), 0.0)
        assertEquals(0.0, sectionMarkSeconds(100), 0.0)
    }

    @Test fun theSongItsKindAndWhere() {
        val sent = Json.parseToJsonElement(sectionMarkBody("tr-1", 60.75, "drop")).jsonObject
        assertEquals("tr-1", sent["track_id"]!!.jsonPrimitive.content)
        assertEquals("drop", sent["kind"]!!.jsonPrimitive.content)
        assertEquals(60.75, sent["seconds"]!!.jsonPrimitive.content.toDouble(), 0.0)
    }
}
