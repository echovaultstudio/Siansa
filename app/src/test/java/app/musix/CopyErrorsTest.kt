package app.musix

import app.musix.log.errorsForClipboard
import app.musix.log.logEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Copy errors" on the Logs page: every error with its stack trace, nothing else. */
class CopyErrorsTest {
    private val log = listOf(
        "2026-10-17 09:00:00.000 I/Player: Playing",
        "2026-10-17 09:00:01.000 E/Stream: Failed",
        "java.io.IOException: reset",
        "\tat okhttp3.Call.execute(Call.kt:1)",
        "2026-10-17 09:00:02.000 W/Cache: Slow",
        "2026-10-17 09:00:03.000 E/Sync: Timed out",
    )

    @Test fun onlyErrorsWithTheirStackTraces() {
        val text = errorsForClipboard(logEntries(log), "Musix 0.1.0beta")!!
        assertEquals(
            "Musix 0.1.0beta · 2 errors\n" +
                "2026-10-17 09:00:01.000 E/Stream: Failed\n" +
                "java.io.IOException: reset\n" +
                "\tat okhttp3.Call.execute(Call.kt:1)\n" +
                "2026-10-17 09:00:03.000 E/Sync: Timed out\n",
            text,
        )
    }

    @Test fun nothingWhenThereAreNoErrors() {
        assertNull(errorsForClipboard(logEntries(log.filter { " E/" !in it && !it.startsWith("java") && !it.startsWith("\t") }), "Musix"))
    }

    @Test fun oldestErrorsLeftOutWhenTooLong() {
        val many = (1..100).map { "2026-10-17 09:00:%02d.000 E/Net: error number $it".format(it % 60) }
        val text = errorsForClipboard(logEntries(many), "Musix", maxChars = 500)!!
        assertTrue(text.length <= 500 + 60)
        assertTrue(text.contains("error number 100"))
        assertFalse(text.contains("error number 1\n"))
        assertTrue(text.lines().first().contains("100 errors (oldest"))
    }

    @Test fun oneError() {
        val text = errorsForClipboard(logEntries(listOf("2026-10-17 09:00:01.000 E/X: y")), "Musix")!!
        assertTrue(text.startsWith("Musix · 1 error\n"))
    }
}
