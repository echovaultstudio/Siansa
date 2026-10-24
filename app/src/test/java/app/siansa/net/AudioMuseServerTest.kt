package app.siansa.net

import app.siansa.data.AppJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** AudioMuse 3 with several media servers (issue #113): each request names this one. */
class AudioMuseServerTest {
    @Test fun theServerGoesInTheAddress() {
        assertEquals("http://am.local:8000/api/similar_tracks?item_id=s1&server=Home%20Emby",
            serverInUrl("http://am.local:8000/api/similar_tracks?item_id=s1", "Home Emby"))
        assertEquals("http://am.local:8000/api/clap/concepts?server=Emby", serverInUrl("http://am.local:8000/api/clap/concepts", "Emby"))
    }

    @Test fun andInJsonBodies() {
        val body = AppJson.parseToJsonElement(serverInBody("""{"query":"calm piano","limit":3}""", "Home Emby")) as JsonObject
        assertEquals("Home Emby", body["server"]!!.jsonPrimitive.content)
        assertEquals("calm piano", body["query"]!!.jsonPrimitive.content)
    }

    @Test fun nothingForTheDefaultServer() {
        assertEquals("http://am.local:8000/api/x?a=1", serverInUrl("http://am.local:8000/api/x?a=1", " "))
        assertEquals("""{"n":5}""", serverInBody("""{"n":5}""", ""))
    }
}
