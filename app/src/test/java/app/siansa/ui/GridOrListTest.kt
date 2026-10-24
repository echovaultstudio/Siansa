package app.siansa.ui

import androidx.compose.foundation.lazy.grid.GridCells
import org.junit.Assert.assertEquals
import org.junit.Test

/** Grids of albums and the like are three across; the list is one per row (issue #289). */
class GridOrListTest {
    @Test fun gridIsThreeAcrossAndListIsOne() {
        assertEquals(3, GRID_COLUMNS)
        assertEquals(GridCells.Fixed(3), gridCells(grid = true))
        assertEquals(GridCells.Fixed(1), gridCells(grid = false))
    }
}
