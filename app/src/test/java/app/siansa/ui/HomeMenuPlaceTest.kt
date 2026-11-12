package app.siansa.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where Home's menu card goes (the owner, 12 Nov): on the shortcut tile under the button, else hanging under it. */
class HomeMenuPlaceTest {
    private val button = Rect(900f, 100f, 1000f, 200f)

    @Test fun liesOnTheTileWhenItsUnderTheButton() {
        val tile = Rect(560f, 240f, 1040f, 400f)
        val at = menuCardPlace(button, tile, pageLeft = 0f, pageRight = 1080f, edge = 48f, gap = 36f, between = 24f, height = 700f)
        assertEquals(Rect(560f, 240f, 1040f, 940f), at)
    }

    @Test fun hangsUnderTheButtonWhenTheTileIsGoneOrAboveIt() {
        val gone = menuCardPlace(button, null, pageLeft = 0f, pageRight = 1080f, edge = 48f, gap = 36f, between = 24f, height = 700f)
        // The right column of a two-column grid: the page less its edges and the gap between the columns, halved.
        assertEquals(Rect(1032f - 480f, 236f, 1032f, 936f), gone)
        val scrolledPast = Rect(560f, 40f, 1040f, 200f)
        assertEquals(gone, menuCardPlace(button, scrolledPast, pageLeft = 0f, pageRight = 1080f, edge = 48f, gap = 36f, between = 24f, height = 700f))
    }

    @Test fun theGlassIsHalfDarkOverABlurAndNearlySolidWithout() {
        assertTrue(glassAlpha(blurs = true) in 0.45f..0.65f)
        assertTrue(glassAlpha(blurs = false) >= 0.9f)
        assertTrue(GLASS_SATURATION > 1f)
    }
}
