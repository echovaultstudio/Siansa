package app.siansa.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tabs stand out on the bottom bar in light and dark (issue #699). */
class BottomBarContrastTest {
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance() + 0.05, b.luminance() + 0.05).sortedDescending()
        return hi / lo
    }

    /** What's behind the labels: the bar's last stop over the page. */
    private fun behindLabels(surface: Color, page: Color) = bottomBarStops(surface).last().second.compositeOver(page)

    @Test fun lightModeTabsAreReadable() {
        val surface = Color(0xFFFBF8FF)
        // Material's light onSurfaceVariant, the unselected tabs' colour.
        val label = Color(0xFF49454F)
        assertTrue(contrast(label, behindLabels(surface, page = Color.White)) >= 4.5)
        // Even over a dark cover scrolling behind the bar.
        assertTrue(contrast(label, behindLabels(surface, page = Color(0xFF101010))) >= 3.0)
    }

    @Test fun darkModeTabsAreReadable() {
        val surface = Color(0xFF110D1C)
        val label = Color(0xFFB9B0CC)
        assertTrue(contrast(label, behindLabels(surface, page = Color.White)) >= 3.0)
        assertTrue(contrast(label, behindLabels(surface, page = surface)) >= 4.5)
    }

    @Test fun theBarIsClearAtTheTop() {
        assertTrue(bottomBarStops(Color.White).first().second.alpha == 0f)
    }
}
