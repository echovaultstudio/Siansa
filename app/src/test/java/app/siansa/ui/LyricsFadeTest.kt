package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The lyrics fade out at the top and bottom instead of sitting in a box. */
class LyricsFadeTest {
    @Test fun clearAtEdgesAndFullInBetween() {
        val stops = lyricsFadeStops(heightPx = 1000f, fadePx = 100f)
        val expected = listOf(0f to 0f, 0.1f to 1f, 0.9f to 1f, 1f to 0f)
        assertEquals(expected.size, stops.size)
        expected.zip(stops).forEach { (want, got) ->
            assertEquals(want.first, got.first, 0.0001f)
            assertEquals(want.second, got.second, 0.0001f)
        }
    }

    @Test fun fadeNeverTakesMoreThanAQuarterAtEachEdge() {
        val stops = lyricsFadeStops(heightPx = 200f, fadePx = 150f)
        assertEquals(0.25f, stops[1].first, 0.0001f)
        assertEquals(0.75f, stops[2].first, 0.0001f)
    }

    @Test fun noFadeBeforeTheAreaHasASize() {
        assertEquals(listOf(0f to 1f, 1f to 1f), lyricsFadeStops(heightPx = 0f, fadePx = 100f))
    }
}
