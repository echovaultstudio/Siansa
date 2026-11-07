package app.siansa.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Now playing's colours from the cover stand out on the cover behind them, dark or light (issue #970). */
class DynamicAccentTest {
    // The covers of the approved mockup: their liveliest colour and their average.
    private val covers = mapOf(
        "Clubfood (lime, light)" to (Color(0xFFB2CE05) to Color(0xFFB8C64F)),
        "HOSPITAL30 (blue on black)" to (Color(0xFF09A2E5) to Color(0xFF203440)),
        "Night mix (rust)" to (Color(0xFFAD6B41) to Color(0xFF6A4E31)),
        "NYE 2026 (mustard)" to (Color(0xFF8D7B11) to Color(0xFFA6996E)),
        "The Fork and Knife (white, a little brown)" to (Color(0xFF6A4744) to Color(0xFFCDC9C7)),
        "Very dark navy" to (Color(0xFF0F2347) to Color(0xFF0A1226)),
        "Almost white" to (Color(0xFFE9E1CF) to Color(0xFFF4F1EA)),
    )

    @Test
    fun alwaysCleanlyVisible() {
        covers.forEach { (name, tones) ->
            val (seed, average) = tones
            val backdrop = DynamicAccent.backdropOf(average)
            val c = DynamicAccent.of(seed, backdrop)
            assertNotNull(name, c)
            c!!
            assertTrue("$name: seek bar and Play ${DynamicAccent.contrast(c.controls, backdrop)}", DynamicAccent.contrast(c.controls, backdrop) >= DynamicAccent.CONTROLS_CONTRAST)
            assertTrue("$name: device name ${DynamicAccent.contrast(c.text, backdrop)}", DynamicAccent.contrast(c.text, backdrop) >= DynamicAccent.TEXT_CONTRAST)
            assertTrue("$name: play symbol ${DynamicAccent.contrast(c.onControls, c.controls)}", DynamicAccent.contrast(c.onControls, c.controls) >= DynamicAccent.TEXT_CONTRAST)
        }
    }

    @Test
    fun keepsTheCoversOwnHue() {
        // Lime stays green, blue stays blue: lighter if needed, never another colour.
        val lime = DynamicAccent.of(Color(0xFFB2CE05), DynamicAccent.backdropOf(Color(0xFFB8C64F)))!!.controls
        assertTrue("lime is green: $lime", lime.green > lime.red && lime.green > lime.blue)
        val blue = DynamicAccent.of(Color(0xFF09A2E5), DynamicAccent.backdropOf(Color(0xFF203440)))!!.controls
        assertTrue("blue is blue: $blue", blue.blue > blue.red && blue.blue > blue.green)
    }

    @Test
    fun aGreyCoverKeepsTheThemesColour() {
        assertNull(DynamicAccent.of(Color(0xFF808080), DynamicAccent.backdropOf(Color(0xFF808080))))
        assertNull(DynamicAccent.of(Color(0xFF111111), DynamicAccent.backdropOf(Color(0xFF202020))))
    }
}
