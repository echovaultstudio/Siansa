package app.siansa.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** AudioMuse's failures as plain messages, and a missing plugin told from an unknown song (issue #788). */
class AudioMuseErrorsTest {
    private val page = "<!doctype html>\n<html lang=\"en\"><head><title>AudioMuse-AI</title></head><body>Not found</body></html>"

    @Test fun aWebPageForAPluginPathMeansThePluginIsntThere() {
        // Report 8799f57b: every plugin call answered 404 with AudioMuse's own page, and the app said the song wasn't known.
        assertEquals(AudioMuseErrors.PLUGIN_MISSING, AudioMuseErrors.pluginMessage(null, 404, page.take(120), page))
        assertTrue(AudioMuseErrors.isWebPage(page))
        assertTrue(AudioMuseErrors.isWebPage("  <HTML><body>…"))
        assertFalse(AudioMuseErrors.isWebPage("""{"error": "not found"}"""))
    }

    @Test fun thePluginSayingItDoesntKnowASongIsStillThat() {
        // The plugin answers JSON: a 404 from it is a song it can't find, not a missing plugin.
        val body = """{"error": "track not found"}"""
        assertEquals(AudioMuseErrors.NOT_KNOWN, AudioMuseErrors.pluginMessage(null, 404, "track not found", body))
        assertEquals(AudioMuseErrors.NOT_KNOWN, AudioMuseErrors.pluginMessage(1004, 404, "track not found", body))
        // Other failures keep their own message.
        assertEquals("AudioMuse's index isn't ready yet (index building)", AudioMuseErrors.pluginMessage(null, 503, "index building", "index building"))
    }
}
