package app.siansa.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pages and cards in grey instead of the theme's purple tint, or a tint of choice (issue #761). */
class SurfaceColourTest {
    // Siansa's old plum surfaces, to be replaced.
    private val plumDark = darkColorScheme(background = Color(0xFF110D1C), surface = Color(0xFF110D1C), surfaceContainerHigh = Color(0xFF272040), primary = Color(0xFFFFB547))
    private val plumLight = lightColorScheme(background = Color(0xFFFBF8FF), surfaceContainerHigh = Color(0xFFECE6F5))

    private fun spread(c: Color) = maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue)

    @Test fun greyIsTheDefaultAndReallyGrey() {
        assertEquals(SurfaceColour.GREY, SurfaceColour.parse(null))
        assertEquals(SurfaceColour.GREY, ThemeChoice().surfaces)
        for (dark in listOf(true, false)) {
            val s = (if (dark) plumDark else plumLight).withSurfaces(SurfaceColour.GREY, dark)
            for (c in listOf(s.background, s.surface, s.surfaceContainerLow, s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest, s.surfaceVariant, s.onSurfaceVariant)) {
                assertTrue("not grey: $c", spread(c) < 0.01f)
            }
        }
    }

    @Test fun cardsStepAwayFromThePage() {
        val d = plumDark.withSurfaces(SurfaceColour.GREY, dark = true)
        val dark = listOf(d.surfaceContainerLowest, d.background, d.surfaceContainerLow, d.surfaceContainer, d.surfaceContainerHigh, d.surfaceContainerHighest).map { luminanceOf(it) }
        assertEquals("darker to lighter in dark mode", dark.sorted(), dark)
        val l = plumLight.withSurfaces(SurfaceColour.GREY, dark = false)
        val light = listOf(l.surfaceContainerLowest, l.background, l.surfaceContainerLow, l.surfaceContainer, l.surfaceContainerHigh, l.surfaceContainerHighest).map { luminanceOf(it) }
        assertEquals("lighter to darker in light mode", light.sortedDescending(), light)
    }

    @Test fun textStaysReadable() {
        for (dark in listOf(true, false)) for (option in SurfaceColour.entries.filter { it != SurfaceColour.THEME }) {
            val s = (if (dark) plumDark else plumLight).withSurfaces(option, dark)
            assertTrue("$option dark=$dark text", contrastOf(s.onSurface, s.surfaceContainerHighest) >= 7f)
            assertTrue("$option dark=$dark quiet text", contrastOf(s.onSurfaceVariant, s.surfaceContainerHigh) >= 4.5f)
        }
    }

    @Test fun tintsLeanTheirWay() {
        val warm = plumDark.withSurfaces(SurfaceColour.WARM, dark = true).surfaceContainerHigh
        assertTrue("warm: $warm", warm.red > warm.blue)
        val cool = plumDark.withSurfaces(SurfaceColour.COOL, dark = true).surfaceContainerHigh
        assertTrue("cool: $cool", cool.blue > cool.red)
    }

    @Test fun matchThemeKeepsTheTheme() {
        assertSame(plumDark, plumDark.withSurfaces(SurfaceColour.THEME, dark = true))
    }

    @Test fun theAccentIsUntouched() {
        assertEquals(plumDark.primary, plumDark.withSurfaces(SurfaceColour.GREY, dark = true).primary)
    }
}
