package app.siansa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A Home row as a cover grid (#1263): its columns and rows, how they're saved, and when names show. */
class CoverGridSizeTest {
    @Test fun anyRowCanBeACoverGrid() {
        assertEquals(mapOf(HomeSection.ARTIST_RADIOS to HomeLook.COVER_GRID), HomeLook.parse("artist_radios:cover_grid"))
    }

    @Test fun columnsAndRowsSavedPerRow() {
        val sizes = mapOf(HomeSection.RECENT to CoverGridSize(5, 3), HomeSection.MADE_FOR_YOU to CoverGridSize(2, 2))
        assertEquals(sizes, CoverGridSize.parse(CoverGridSize.format(sizes)))
        // Out of 2..6 columns or 1..6 rows, an unknown row, or broken: dropped.
        assertEquals(mapOf(HomeSection.GENRES to CoverGridSize(6, 1)), CoverGridSize.parse("genres:6x1,recent:7x2,added:3x0,nowhere:3x3,broken,radios:3"))
        assertEquals(emptyMap<HomeSection, CoverGridSize>(), CoverGridSize.parse(null))
    }

    @Test fun howManyItShowsAndWhenNamesFit() {
        // The owner's examples: 2×2, 3×3, 2×4, 3×6; and 5 columns by 3 rows.
        assertEquals(listOf(4, 9, 8, 18, 15), listOf(CoverGridSize(2, 2), CoverGridSize(3, 3), CoverGridSize(2, 4), CoverGridSize(3, 6), CoverGridSize(5, 3)).map { it.count })
        assertTrue(CoverGridSize(3, 6).named)
        assertFalse(CoverGridSize(4, 2).named)
        assertEquals("5×3", CoverGridSize(5, 3).label)
        assertEquals(CoverGridSize(3, 2), CoverGridSize.DEFAULT)
    }
}
