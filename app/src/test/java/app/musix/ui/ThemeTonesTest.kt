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

/** "Your colour" (issue #148, step 2): any picked colour gives a readable theme. */
class YourColourTest {
    private fun readable(what: String, fg: Int, bg: Int, min: Double) {
        val ratio = ThemeTones.contrast(fg, bg)
        assertTrue("$what: contrast %.2f is below %.1f".format(ratio, min), ratio >= min)
    }

    /** Every hue around the slider, the quick picks, and awkward picks: pure yellow, near black, near white, grey. */
    private val seeds = (0 until 360 step 15).map { ThemeTones.fromHue(it.toDouble()) } + QUICK_COLOURS +
        listOf(0xFFFFFF00, 0xFF050505, 0xFFFAFAFA, 0xFF808080, 0xFF00FF00, 0xFF0000FF).map { it.toInt() }

    @Test fun anyPickedColourGivesReadableText() {
        for (seed in seeds) for ((dark, black) in listOf(false to false, true to false, true to true)) {
            val r = ThemeTones.scheme(seed, dark, mono = ThemeTones.isGrey(seed), black = black)
            val name = "${ThemeTones.toHex(seed)} ${if (black) "pure black" else if (dark) "dark" else "light"}"
            readable("$name text", r.onSurface, r.surface, 7.0)
            readable("$name text on cards", r.onSurface, r.containerHighest, 4.5)
            readable("$name second text", r.onSurfaceVariant, r.surface, 4.5)
            readable("$name buttons", r.onPrimary, r.primary, 4.5)
            readable("$name chips", r.onSecondaryContainer, r.secondaryContainer, 4.5)
            readable("$name accent", r.primary, r.surface, 3.0)
        }
    }

    @Test fun hexCodesReadAndWrite() {
        assertEquals(0xFF0E9AA7.toInt(), ThemeTones.parseHex("#0E9AA7"))
        assertEquals(0xFF0E9AA7.toInt(), ThemeTones.parseHex(" 0e9aa7 "))
        assertEquals(0xFF0099AA.toInt(), ThemeTones.parseHex("#09A"))
        assertEquals(null, ThemeTones.parseHex("#0E9AA"))
        assertEquals(null, ThemeTones.parseHex("teal"))
        assertEquals(null, ThemeTones.parseHex(""))
        assertEquals("#0E9AA7", ThemeTones.toHex(0xFF0E9AA7.toInt()))
        for (seed in seeds) assertEquals(seed, ThemeTones.parseHex(ThemeTones.toHex(seed)))
    }

    @Test fun hueSliderGoesRoundTheColourWheel() {
        for (h in (0 until 360 step 30)) {
            val back = ThemeTones.hueOf(ThemeTones.fromHue(h.toDouble()))
            val diff = minOf(abs(back - h), 360 - abs(back - h))
            assertTrue("hue $h came back as $back", diff < 6)
        }
    }

    @Test fun greyPicksGiveAGreyTheme() {
        assertTrue(ThemeTones.isGrey(0xFF808080.toInt()))
        assertTrue(!ThemeTones.isGrey(0xFF0E9AA7.toInt()))
        val r = ThemeTones.scheme(0xFF808080.toInt(), dark = false, mono = true)
        assertTrue(abs((r.primary shr 16 and 0xFF) - (r.primary and 0xFF)) <= 1)
    }

    @Test fun yourColourIsOfferedAfterThePresets() {
        assertEquals(ThemeColours.YOURS, ThemeColours.entries.last())
        assertEquals(null, ThemeColours.YOURS.seed)
    }

    // ---- Opacity (issue #181) ----

    @Test fun opacityIsKeptInTheAlpha() {
        val pink = 0xFFD81B60.toInt()
        assertEquals(1.0, ThemeTones.opacityOf(pink), 0.001)
        val half = ThemeTones.withOpacity(pink, 0.5)
        assertEquals(0.5, ThemeTones.opacityOf(half), 0.01)
        assertEquals("#D81B60", ThemeTones.toHex(half))
        assertEquals("50 %", ThemeTones.opacityLabel(half))
    }

    @Test fun opacityStaysInRange() {
        val pink = 0xFFD81B60.toInt()
        assertEquals(ThemeTones.MIN_OPACITY, ThemeTones.opacityOf(ThemeTones.withOpacity(pink, 0.0)), 0.01)
        assertEquals(1.0, ThemeTones.opacityOf(ThemeTones.withOpacity(pink, 3.0)), 0.001)
        // No alpha at all (an old saved colour) counts as fully opaque.
        assertEquals(1.0, ThemeTones.opacityOf(0x00D81B60), 0.001)
    }

    @Test fun lowerOpacityMeansLessColourSameTone() {
        val pink = 0xFFD81B60.toInt()
        for (dark in listOf(false, true)) {
            val full = ThemeTones.scheme(pink, dark)
            val soft = ThemeTones.scheme(ThemeTones.withOpacity(pink, 0.3), dark)
            for ((a, b) in listOf(full.primary to soft.primary, full.surface to soft.surface, full.secondaryContainer to soft.secondaryContainer)) {
                val (la, ca, _) = ThemeTones.lch(a)
                val (lb, cb, _) = ThemeTones.lch(b)
                assertEquals("same lightness", la, lb, 1.5)
                assertTrue("less colour: $ca → $cb", cb < ca)
            }
        }
    }

    @Test fun everyOpacityHasReadableText() {
        for (seed in listOf(0xFFD81B60, 0xFF0E9AA7, 0xFFF6BF26, 0xFF3F51B5).map { it.toInt() })
            for (opacity in listOf(0.2, 0.5, 0.8)) for (dark in listOf(false, true)) {
                val r = ThemeTones.scheme(ThemeTones.withOpacity(seed, opacity), dark)
                val name = "${ThemeTones.toHex(seed)} at $opacity ${if (dark) "dark" else "light"}"
                readable("$name text", r.onSurface, r.surface, 7.0)
                readable("$name buttons", r.onPrimary, r.primary, 4.5)
                readable("$name chips", r.onSecondaryContainer, r.secondaryContainer, 4.5)
                readable("$name accent", r.primary, r.surface, 3.0)
            }
    }
}
