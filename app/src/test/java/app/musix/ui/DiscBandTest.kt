package app.musix.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** The band behind each disc heading keeps its name easy to read on any album colour (issue #66). */
class DiscBandTest {
    private val covers = listOf(
        Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.White, Color.Black, Color.Gray, Color.Cyan,
        Color(0xFF9C27B0), Color(0xFFFFB547), Color(0xFF2E8B9A), Color(0xFFE8572A), Color(0xFFFF69B4), Color(0xFF556B2F),
    )

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    @Test fun textReadsWellOnEveryBand() {
        for (dark in listOf(true, false)) for (cover in covers) {
            val band = discBand(cover, dark)
            val ratio = contrast(band.start, band.text)
            assertTrue("cover $cover dark=$dark: contrast $ratio", ratio >= 4.5f)
        }
    }

    @Test fun bandIsDarkInDarkModeAndLightInLightMode() {
        for (cover in covers) {
            assertTrue("dark band for $cover", discBand(cover, dark = true).start.luminance() < 0.1f)
            assertTrue("light band for $cover", discBand(cover, dark = false).start.luminance() > 0.35f)
            assertTrue(discBand(cover, dark = true).text == Color.White)
        }
    }
}
