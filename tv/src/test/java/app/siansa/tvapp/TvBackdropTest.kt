package app.siansa.tvapp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvBackdropTest {
    @Test
    fun anEvenColourStaysTheSame() {
        val red = 0xFFCC2200.toInt()
        val pixels = IntArray(8 * 8) { red }
        assertArrayEquals(pixels, boxBlur(pixels, 8, 8, radius = 2, passes = 3))
    }

    @Test
    fun aSharpEdgeIsSpreadOut() {
        // Left half black, right half white: after blurring, the middle is grey and both ends keep their colour.
        val width = 16
        val pixels = IntArray(width * 4) { i -> if (i % width < width / 2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val out = boxBlur(pixels, width, 4, radius = 2, passes = 3)
        val row = (0 until width).map { out[it] and 0xFF }
        assertEquals(0, row.first())
        assertEquals(255, row.last())
        assertTrue("middle is between: $row", row[width / 2 - 1] in 60..195 && row[width / 2] in 60..195)
        assertTrue("goes from dark to light: $row", row.zipWithNext().all { (a, b) -> a <= b })
    }

    @Test
    fun theInputIsLeftAsItWas() {
        val pixels = IntArray(4 * 4) { if (it == 5) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        val before = pixels.copyOf()
        boxBlur(pixels, 4, 4, radius = 1, passes = 2)
        assertArrayEquals(before, pixels)
    }
}
