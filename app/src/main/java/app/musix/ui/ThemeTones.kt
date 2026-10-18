package app.musix.ui

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A full set of theme colours made from one seed colour, the way Android's Material You themes are made
 * (issue #148): every colour is a tone (lightness, 0 = black … 100 = white) of the seed's hue. Text and the
 * colour under it are always far enough apart in tone to stay readable, in light and in dark. Colours are
 * ARGB ints, so this runs (and is tested) without Android.
 */
object ThemeTones {
    /** The colour roles a theme needs, as ARGB. */
    data class Roles(
        val primary: Int, val onPrimary: Int, val primaryContainer: Int, val onPrimaryContainer: Int,
        val secondary: Int, val onSecondary: Int, val secondaryContainer: Int, val onSecondaryContainer: Int,
        val tertiary: Int, val onTertiary: Int, val tertiaryContainer: Int, val onTertiaryContainer: Int,
        val surface: Int, val onSurface: Int, val surfaceVariant: Int, val onSurfaceVariant: Int,
        val containerLowest: Int, val containerLow: Int, val container: Int, val containerHigh: Int, val containerHighest: Int,
        val outline: Int, val outlineVariant: Int, val inverseSurface: Int, val inverseOnSurface: Int, val inversePrimary: Int,
    )

    /** Light or dark colours from [seed]; [mono] makes a grey theme; [black] gives dark a pure black background. */
    fun scheme(seed: Int, dark: Boolean, mono: Boolean = false, black: Boolean = false): Roles {
        val (_, c, h) = lch(seed)
        // The seed's opacity (issue #181) sets how strongly its colour shows: every role keeps its tone (so
        // text stays as readable) and loses colour towards grey, like the colour laid thinly over grey.
        val k = opacityOf(seed)
        val pc = if (mono) 0.0 else maxOf(c, 48.0) * k
        val sc = if (mono) 0.0 else 16.0 * k
        val tc = if (mono) 0.0 else 28.0 * k
        val nc = if (mono) 0.0 else 5.0 * k
        val nvc = if (mono) 0.0 else 9.0 * k
        val th = (h + 60) % 360
        fun p(t: Int) = tone(h, pc, t)
        fun s(t: Int) = tone(h, sc, t)
        fun te(t: Int) = tone(th, tc, t)
        fun n(t: Int) = tone(h, nc, t)
        fun nv(t: Int) = tone(h, nvc, t)
        return if (!dark) Roles(
            primary = p(40), onPrimary = p(100), primaryContainer = p(90), onPrimaryContainer = p(10),
            secondary = s(40), onSecondary = s(100), secondaryContainer = s(90), onSecondaryContainer = s(10),
            tertiary = te(40), onTertiary = te(100), tertiaryContainer = te(90), onTertiaryContainer = te(10),
            surface = n(98), onSurface = n(10), surfaceVariant = nv(90), onSurfaceVariant = nv(30),
            containerLowest = n(100), containerLow = n(96), container = n(94), containerHigh = n(92), containerHighest = n(90),
            outline = nv(50), outlineVariant = nv(80), inverseSurface = n(20), inverseOnSurface = n(95), inversePrimary = p(80),
        ) else Roles(
            primary = p(80), onPrimary = p(20), primaryContainer = p(30), onPrimaryContainer = p(90),
            secondary = s(80), onSecondary = s(20), secondaryContainer = s(30), onSecondaryContainer = s(90),
            tertiary = te(80), onTertiary = te(20), tertiaryContainer = te(30), onTertiaryContainer = te(90),
            surface = if (black) BLACK else n(6), onSurface = n(90), surfaceVariant = nv(30), onSurfaceVariant = nv(80),
            containerLowest = if (black) BLACK else n(4),
            containerLow = if (black) n(4) else n(10),
            container = if (black) n(8) else n(12),
            containerHigh = if (black) n(12) else n(17),
            containerHighest = if (black) n(17) else n(22),
            outline = nv(60), outlineVariant = nv(30), inverseSurface = n(90), inverseOnSurface = n(20), inversePrimary = p(40),
        )
    }

    const val BLACK = 0xFF000000.toInt()

    /** The lowest opacity "Your colour" offers: below it a theme is all but grey (use the Grey theme then). */
    const val MIN_OPACITY = 0.2

    /**
     * How strongly [argb]'s colour shows in its theme, 0.2–1, from its alpha (issue #181). Colours without
     * an alpha (0) count as fully opaque, so presets and colours saved before opacity existed are unchanged.
     */
    fun opacityOf(argb: Int): Double {
        val a = argb ushr 24
        return if (a == 0) 1.0 else (a / 255.0).coerceIn(MIN_OPACITY, 1.0)
    }

    /** [argb] with its opacity set to [opacity] (0.2–1), kept in the alpha. */
    fun withOpacity(argb: Int, opacity: Double): Int {
        val a = (opacity.coerceIn(MIN_OPACITY, 1.0) * 255).roundToInt()
        return (a shl 24) or (argb and 0xFFFFFF)
    }

    /** "80 %" for [argb]'s opacity, in steps of 5. */
    fun opacityLabel(argb: Int): String = "${(opacityOf(argb) * 20).roundToInt() * 5} %"

    // ---- "Your colour" (issue #148, step 2) ----

    /** True when [argb] has almost no colour (a grey), so its theme should be grey too rather than tinted. */
    fun isGrey(argb: Int): Boolean = lch(argb).second < 8.0

    /** A vivid, mid-light colour of hue [h] (0–360), for the hue slider. */
    fun fromHue(h: Double): Int = tone(((h % 360) + 360) % 360, 70.0, 55)

    /** The hue (0–360) of [argb], to place it on the hue slider. */
    fun hueOf(argb: Int): Double = lch(argb).third

    /** "#0E9AA7" for [argb]. */
    fun toHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** The colour written as "#0E9AA7", "0e9aa7" or "#09A" (short form), or null if it isn't one. */
    fun parseHex(text: String): Int? {
        var t = text.trim().removePrefix("#")
        if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
        if (t.length != 6 || !t.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
        return (0xFF shl 24) or t.toInt(16)
    }

    /** How readable [fg] is on [bg] (WCAG contrast ratio, 1 to 21; 4.5 is enough for text). */
    fun contrast(fg: Int, bg: Int): Double {
        fun lum(c: Int): Double {
            fun ch(v: Int) = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
            return 0.2126 * ch(c shr 16 and 0xFF) + 0.7152 * ch(c shr 8 and 0xFF) + 0.0722 * ch(c and 0xFF)
        }
        val a = lum(fg) + 0.05
        val b = lum(bg) + 0.05
        return maxOf(a, b) / minOf(a, b)
    }

    // ---- Colour maths: sRGB ↔ CIE LCh (D65). The tone is CIELAB L*, as in Material's HCT. ----

    private const val XN = 0.95047
    private const val YN = 1.0
    private const val ZN = 1.08883

    private fun toLinear(v: Int): Double = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    private fun fromLinear(v: Double): Double = (if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055) * 255
    private fun f(t: Double) = if (t > 216.0 / 24389) Math.cbrt(t) else (24389.0 / 27 * t + 16) / 116
    private fun fInv(t: Double) = if (t * t * t > 216.0 / 24389) t * t * t else (116 * t - 16) / (24389.0 / 27)

    /** Lightness (0–100), chroma and hue (degrees) of an ARGB colour. */
    fun lch(argb: Int): Triple<Double, Double, Double> {
        val r = toLinear(argb shr 16 and 0xFF)
        val g = toLinear(argb shr 8 and 0xFF)
        val b = toLinear(argb and 0xFF)
        val x = 0.4124 * r + 0.3576 * g + 0.1805 * b
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = 0.0193 * r + 0.1192 * g + 0.9505 * b
        val l = 116 * f(y / YN) - 16
        val a = 500 * (f(x / XN) - f(y / YN))
        val bb = 200 * (f(y / YN) - f(z / ZN))
        return Triple(l, hypot(a, bb), (Math.toDegrees(atan2(bb, a)) + 360) % 360)
    }

    private fun rgb(l: Double, c: Double, h: Double): DoubleArray {
        val a = c * cos(Math.toRadians(h))
        val b = c * sin(Math.toRadians(h))
        val fy = (l + 16) / 116
        val x = XN * fInv(fy + a / 500)
        val y = YN * fInv(fy)
        val z = ZN * fInv(fy - b / 200)
        val r = 3.2406 * x - 1.5372 * y - 0.4986 * z
        val g = -0.9689 * x + 1.8758 * y + 0.0415 * z
        val bl = 0.0557 * x - 0.2040 * y + 1.0570 * z
        return doubleArrayOf(r, g, bl).map { if (it > 0) fromLinear(it) else -1.0 }.toDoubleArray()
    }

    /** Hue [h] at [tone], with as much of chroma [c] as fits on screen. */
    fun tone(h: Double, c: Double, tone: Int): Int {
        var lo = 0.0
        var hi = c
        var best = rgb(tone.toDouble(), 0.0, h)
        repeat(24) {
            val mid = (lo + hi) / 2
            val v = rgb(tone.toDouble(), mid, h)
            if (v.all { it in -0.5..255.5 }) {
                lo = mid
                best = v
            } else {
                hi = mid
            }
        }
        val (r, g, b) = best.map { it.roundToInt().coerceIn(0, 255) }
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
