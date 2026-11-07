package app.siansa.tvapp

import org.junit.Assert.assertEquals
import org.junit.Test

/** A TV row fills the screen, however few covers it has (issue #996). */
class RowLookTest {
    @Test
    fun fewAreWideCardsSomeShareTheWidthManyScroll() {
        assertEquals(RowLook.WIDE_CARDS, rowLook(1))
        assertEquals(RowLook.WIDE_CARDS, rowLook(3))
        assertEquals(RowLook.FILLED, rowLook(4))
        assertEquals(RowLook.FILLED, rowLook(5))
        assertEquals(RowLook.SCROLLING, rowLook(GRID_COLUMNS))
        assertEquals(RowLook.SCROLLING, rowLook(40))
    }
}
