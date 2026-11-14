package app.siansa.tvapp

import app.siansa.log.logEntries
import org.junit.Assert.assertEquals
import org.junit.Test

/** The log as the TV's own page shows it (the owner, 14 Nov): newest first, in blocks the remote steps through. */
class TvLogBlocksTest {
    private val log = logEntries(
        listOf(
            "2026-11-14 10:00:00.000 I/Nav: tv: home",
            "2026-11-14 10:00:01.000 E/Tv: Mix failed",
            "    at one",
            "    at two",
            "2026-11-14 10:00:02.000 D/Net: Streamed",
            "2026-11-14 10:00:03.000 W/Freeze: Main thread blocked",
        ),
    )

    @Test fun newestFirstWithAnEntrysLinesInOrder() {
        assertEquals(
            listOf(
                listOf("2026-11-14 10:00:03.000 W/Freeze: Main thread blocked", "2026-11-14 10:00:02.000 D/Net: Streamed"),
                listOf("2026-11-14 10:00:01.000 E/Tv: Mix failed", "    at one"),
                listOf("    at two", "2026-11-14 10:00:00.000 I/Nav: tv: home"),
            ),
            logBlocks(log, problemsOnly = false, perBlock = 2),
        )
    }

    @Test fun problemsAloneAreErrorsAndWarnings() {
        assertEquals(
            listOf(listOf("2026-11-14 10:00:03.000 W/Freeze: Main thread blocked", "2026-11-14 10:00:01.000 E/Tv: Mix failed", "    at one", "    at two")),
            logBlocks(log, problemsOnly = true, perBlock = 10),
        )
    }

    @Test fun anEmptyLogHasNoBlocks() {
        assertEquals(emptyList<List<String>>(), logBlocks(emptyList(), problemsOnly = false))
    }
}
