package app.siansa.ui

import app.siansa.net.Episode
import org.junit.Assert.assertEquals
import org.junit.Test

/** A podcast episode's line: season, number and date, as much as it has (issue #521). */
class EpisodeLineTest {
    private fun ep(season: String?, number: String?, at: Long) = Episode("e", "p", "T", null, at, 1800, season, number)

    @Test fun seasonNumberAndDate() {
        val line = episodeLine(ep("2", "14", 1760227200000L)) // 12 Oct 2025 UTC
        assert(line.startsWith("S2 E14 · ")) { line }
        assert(line.endsWith("2025")) { line }
    }

    @Test fun onlyWhatItHas() {
        assertEquals("E7", episodeLine(ep(null, "7", 0L)))
        assertEquals("", episodeLine(ep(null, null, 0L)))
    }
}
