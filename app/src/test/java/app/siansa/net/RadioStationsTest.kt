package app.siansa.net

import app.siansa.data.AppJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The music server's internet radio stations (OpenSubsonic getInternetRadioStations, issue #353). */
class RadioStationsTest {
    private fun root(json: String) = AppJson.parseToJsonElement(json).jsonObject

    @Test fun stationsAreReadWithTheirStreamAndHomePage() {
        val stations = SubsonicApi.parseRadioStations(root("""
            {"status": "ok", "internetRadioStations": {"internetRadioStation": [
              {"id": "1", "name": "Radio Paradise", "streamUrl": "https://stream.radioparadise.com/aac-320", "homePageUrl": "https://radioparadise.com"},
              {"id": "2", "name": "SomaFM Groove Salad", "streamUrl": "https://somafm.com/groovesalad.pls"}
            ]}}
        """))
        assertEquals(listOf("Radio Paradise", "SomaFM Groove Salad"), stations.map { it.name })
        val first = stations.first()
        assertEquals("server-1", first.id)
        assertEquals("https://stream.radioparadise.com/aac-320", first.streamUrl)
        assertEquals("https://radioparadise.com", first.homePage)
        assertTrue(first.fromServer)
        assertNull(stations[1].homePage)
    }

    @Test fun stationsWithoutANameOrAWebStreamAreLeftOut() {
        val stations = SubsonicApi.parseRadioStations(root("""
            {"internetRadioStations": {"internetRadioStation": [
              {"id": "1", "name": "", "streamUrl": "https://a.example.com/s"},
              {"id": "2", "name": "Local file", "streamUrl": "file:///music/radio.mp3"},
              {"id": "3", "name": "No stream"},
              {"id": "4", "name": "Good", "streamUrl": "http://b.example.com:8000/live", "homePageUrl": ""}
            ]}}
        """))
        assertEquals(listOf("Good"), stations.map { it.name })
        assertNull(stations.single().homePage)
    }

    @Test fun aServerWithNoStationsGivesAnEmptyList() {
        assertEquals(emptyList<Any>(), SubsonicApi.parseRadioStations(root("""{"status": "ok", "internetRadioStations": {}}""")))
        assertEquals(emptyList<Any>(), SubsonicApi.parseRadioStations(root("""{"status": "ok"}""")))
    }
}
