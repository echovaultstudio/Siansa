package app.siansa

import app.siansa.log.logEntries
import app.siansa.log.logForClipboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Copy whole log: everything when it fits the clipboard, else the newest whole entries. */
class CopyWholeLogTest {
    private val lines = listOf(
        "2026-10-18 20:57:55.730 I/Log: Logging on",
        "2026-10-18 20:57:58.573 D/Net: → GET https://koito/apis/web/v1/top/tracks?from=1&to=2",
        "  Accept-Encoding: gzip · Authorization: •••",
        "  ← 200 · application/json · in 81ms",
        "2026-10-18 20:57:59.923 I/TimeMachine: April 2015: 11 songs",
        "2026-10-18 20:58:00.074 W/Http: GET 192.168.1.106/share/img failed: IOException: Canceled",
    )

    @Test fun everythingWhenItFits() {
        val text = logForClipboard(logEntries(lines), "Siansa 0.10.1beta")!!
        assertTrue(text, text.startsWith("Siansa 0.10.1beta · 4 entries\n"))
        lines.forEach { assertTrue(it, text.contains(it)) }
        // Oldest first, as in the file.
        assertTrue(text.indexOf("Logging on") < text.indexOf("Canceled"))
    }

    @Test fun theNewestWholeEntriesWhenTooBig() {
        val text = logForClipboard(logEntries(lines), "Siansa", maxChars = 200)!!
        assertTrue(text, text.lineSequence().first().startsWith("Siansa · newest "))
        assertTrue(text, text.contains("of 4 entries (Save logs has them all)"))
        assertTrue("the newest is kept", text.contains("Canceled"))
        assertFalse("the oldest is left out", text.contains("Logging on"))
        // A request and its detail lines stay together or are left out together.
        assertEquals(text.contains("→ GET"), text.contains("← 200"))
    }

    @Test fun theNewestEntryEvenWhenItAloneIsTooBig() {
        val text = logForClipboard(logEntries(lines), "Siansa", maxChars = 10)!!
        assertTrue(text.contains("Canceled"))
    }

    @Test fun emptyLog() = assertNull(logForClipboard(emptyList(), "Siansa"))
}
