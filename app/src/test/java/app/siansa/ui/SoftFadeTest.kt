package app.siansa.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A page's colour fades into the page on one smooth curve (the owner, 14 Nov: stripes in the album page's fade). */
class SoftFadeTest {
    @Test fun allOfTheColourAtTheTopAThirdThreeQuartersDownNoneAtTheBottom() {
        assertEquals(1f, SoftFade.share(0f), 0f)
        assertEquals(0.35f, SoftFade.share(0.75f), 0.01f)
        assertEquals(0f, SoftFade.share(1f), 0f)
        assertEquals(Color.Red, SoftFade.at(Color.Red, Color.Black, 0f))
        assertEquals(Color.Black, SoftFade.at(Color.Red, Color.Black, 1f))
    }

    @Test fun theFadeHasNoBend() {
        // Each step down takes away a little more than the one before, never suddenly less (the old fade's bend).
        val drops = (0 until SoftFade.STEPS).map { SoftFade.share(it / SoftFade.STEPS.toFloat()) - SoftFade.share((it + 1) / SoftFade.STEPS.toFloat()) }
        assertTrue(drops.all { it > 0f })
        assertTrue(drops.zipWithNext().all { (a, b) -> b >= a - 1e-4f })
    }
}
