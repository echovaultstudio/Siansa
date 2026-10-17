package app.musix

import app.musix.data.Song
import app.musix.data.keepKnown
import app.musix.log.logEntries
import app.musix.log.problemsForClipboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Copy problems" on the Logs page: every error and warning with its stack trace, nothing else. */
class CopyErrorsTest {
    private val log = listOf(
        "2026-10-17 09:00:00.000 I/Player: Playing",
        "2026-10-17 09:00:01.000 E/Stream: Failed",
        "java.io.IOException: reset",
        "\tat okhttp3.Call.execute(Call.kt:1)",
        "2026-10-17 09:00:02.000 W/Cache: Slow",
        "2026-10-17 09:00:03.000 E/Sync: Timed out",
        "2026-10-17 09:00:04.000 D/Audio: Ready",
    )

    @Test fun errorsAndWarningsWithTheirStackTraces() {
        val text = problemsForClipboard(logEntries(log), "Musix 0.2.0beta")!!
        assertEquals(
            "Musix 0.2.0beta · 2 errors, 1 warning\n" +
                "2026-10-17 09:00:01.000 E/Stream: Failed\n" +
                "java.io.IOException: reset\n" +
                "\tat okhttp3.Call.execute(Call.kt:1)\n" +
                "2026-10-17 09:00:02.000 W/Cache: Slow\n" +
                "2026-10-17 09:00:03.000 E/Sync: Timed out\n",
            text,
        )
    }

    @Test fun onlyWarningsAreCopiedToo() {
        val text = problemsForClipboard(logEntries(listOf("2026-10-17 09:00:02.000 W/Phone: Low memory")), "Musix")!!
        assertEquals("Musix · 1 warning\n2026-10-17 09:00:02.000 W/Phone: Low memory\n", text)
    }

    @Test fun nothingWhenThereAreNoProblems() {
        assertNull(problemsForClipboard(logEntries(listOf(log[0], log[6])), "Musix"))
    }

    @Test fun oldestLeftOutWhenTooLong() {
        val many = (1..100).map { "2026-10-17 09:00:%02d.000 E/Net: error number $it".format(it % 60) }
        val text = problemsForClipboard(logEntries(many), "Musix", maxChars = 500)!!
        assertTrue(text.length <= 500 + 60)
        assertTrue(text.contains("error number 100"))
        assertFalse(text.contains("error number 1\n"))
        assertTrue(text.lines().first().contains("100 errors (oldest"))
    }
}

/** Songs Navidrome sends from libraries left out in Settings → Library sync never show or play. */
class SyncedLibrariesTest {
    private fun song(id: String, title: String = id) = Song(id = id, title = title)

    @Test fun songsTheLibraryDoesntHaveAreLeftOut() {
        val remote = listOf(song("jungle"), song("rumble-atmos"), song("lights"))
        val local = mapOf("jungle" to song("jungle", "Jungle (local)"), "lights" to song("lights"))
        assertEquals(listOf("jungle", "lights"), keepKnown(remote, local).map { it.id })
    }

    @Test fun thePhonesCopyIsUsed() {
        val local = mapOf("jungle" to song("jungle", "Jungle (local)"))
        assertEquals("Jungle (local)", keepKnown(listOf(song("jungle")), local).single().title)
    }
}
