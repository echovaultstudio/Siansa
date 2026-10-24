package app.siansa.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** Issue #414: the problem report sent from the app. */
class ReportTest {
    @Test fun cleanTrimsSqueezesAndCaps() {
        assertEquals("a\n\nb", Report.clean("  a  \n\n\n\n b".replace(" b", "b")))
        assertEquals(Report.MAX_TEXT, Report.clean("x".repeat(5000)).length)
    }

    @Test fun problemAsksForBothAnswers() {
        assertNotNull(Report.problem("short", "it plays"))
        assertNotNull(Report.problem("The song stops after a minute", " "))
        assertNull(Report.problem("The song stops after a minute", "It keeps playing"))
    }

    @Test fun headerCarriesWordsAndDetails() {
        val h = Report.header(
            " It stops ", "It plays",
            mapOf("app_version" to "0.28.1", "android" to "15", "device" to "Google Pixel 8", "session_id" to "s1"), "EMBY",
        )
        assertTrue(!h.contains("=") && !h.contains("+") && !h.contains("/"))
        val json = String(Base64.getUrlDecoder().decode(h))
        assertTrue(json.contains("\"happened\":\"It stops\""))
        assertTrue(json.contains("\"device\":\"Google Pixel 8\""))
        assertTrue(json.contains("\"source\":\"EMBY\""))
    }

    @Test fun parseReadsIdAndIssue() {
        val r = Report.parse("""{"id":"ab12","issue":7,"url":"https://github.com/echovaultstudio/siansa-beta-testers/issues/7"}""")!!
        assertEquals("ab12", r.id)
        assertEquals(7, r.issue)
        assertNull(Report.parse("""{"id":"ab12","url":"https://evil.example/x"}""")!!.url)
        assertNull(Report.parse("nope"))
        assertNull(Report.parse("""{"issue":3}"""))
    }

    @Test fun failureExplainsInPlainWords() {
        assertTrue(Report.failure(413).contains("too big"))
        assertTrue(Report.failure(429).contains("Too many"))
        assertTrue(Report.failure(404).contains("404"))
    }
}
