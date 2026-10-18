package app.musix.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Themes made from one colour (issue #148): readable text in every preset, light, dark and pure black. */
class ThemeTonesTest {
    private val presets = ThemeColours.entries.filter { it.seed != null }

    private fun readable(what: String, fg: Int, bg: Int, min: Double) {
        val ratio = ThemeTones.contrast(fg, bg)
        assertTrue("$what: contrast %.2f is below %.1f".format(ratio, min), ratio >= min)
    }

    @Test fun everyPresetHasReadableText() {
        for (preset in presets) for ((dark, black) in listOf(false to false, true to false, true to true)) {
            val r = ThemeTones.scheme(preset.seed!!.toInt(), dark, mono = preset == ThemeColours.MONO, black = black)
            val name = "${preset.label} ${if (black) "pure black" else if (dark) "dark" else "light"}"
            readable("$name text", r.onSurface, r.surface, 7.0)
            readable("$name text on cards", r.onSurface, r.containerHighest, 4.5)
            readable("$name second text", r.onSurfaceVariant, r.surface, 4.5)
            readable("$name buttons", r.onPrimary, r.primary, 4.5)
            readable("$name chips", r.onSecondaryContainer, r.secondaryContainer, 4.5)
            readable("$name tiles", r.onPrimaryContainer, r.primaryContainer, 4.5)
            // The accent stands out from the background (play buttons, icons).
            readable("$name accent", r.primary, r.surface, 3.0)
        }
    }

    @Test fun pureBlackIsBlack() {
        val r = ThemeTones.scheme(ThemeColours.OCEAN.seed!!.toInt(), dark = true, black = true)
        assertEquals(ThemeTones.BLACK, r.surface)
    }

    @Test fun monoIsGrey() {
        val r = ThemeTones.scheme(ThemeColours.MONO.seed!!.toInt(), dark = false, mono = true)
        for (c in listOf(r.primary, r.secondary, r.surface, r.outline)) {
            // Grey: red, green and blue the same (give or take rounding).
            assertTrue(abs((c shr 16 and 0xFF) - (c shr 8 and 0xFF)) <= 1)
            assertTrue(abs((c shr 8 and 0xFF) - (c and 0xFF)) <= 1)
        }
    }

    @Test fun tonesKeepTheSeedsHueAndGoFromDarkToLight() {
        val seed = ThemeColours.OCEAN.seed!!.toInt()
        val hue = ThemeTones.lch(seed).third
        val tones = listOf(10, 30, 50, 70, 90).map { ThemeTones.tone(hue, 48.0, it) }
        val lightness = tones.map { ThemeTones.lch(it).first }
        assertEquals(lightness.sorted(), lightness)
        lightness.zip(listOf(10, 30, 50, 70, 90)).forEach { (l, t) -> assertTrue("tone $t came out as $l", abs(l - t) < 1.5) }
        assertTrue(abs(ThemeTones.lch(tones[2]).third - hue) < 3)
    }

    @Test fun contrastOfBlackOnWhiteIsTheMost() {
        assertEquals(21.0, ThemeTones.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
    }
}
