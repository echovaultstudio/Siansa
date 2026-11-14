package app.siansa.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import app.siansa.log.AppLog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Now playing's colours from the cover (issue #970): [controls] for the seek bar and the Play button, [text] for the
 * device name under them (text needs more contrast), and [onControls] for the play symbol on the button.
 */
data class DynamicColours(val controls: Color, val text: Color, val onControls: Color)

/** One of a cover's colours and how much of the cover it covers (Android's palette swatch), for [DynamicAccent.seedOf]. */
data class CoverSwatch(val color: Color, val population: Int)

/**
 * The cover's colour made to stand out on now playing (issue #970). The page behind the controls is the cover itself,
 * blurred and darkened ([backdropOf]), so the colour is the cover's own ([seedOf]), lightened until it stands out from
 * that by [CONTROLS_CONTRAST] (the seek bar, Play) and [TEXT_CONTRAST] (the device name), as accessibility asks of
 * controls and text.
 *
 * The colour is the one that fills most of the cover, as much as its liveliest (issue #994): a sepia cover with a red
 * light in it is tan, not red, and a muted colour stays muted. It's calmed rather than livened (the owner, 10 Nov):
 * never more than [MOST_SATURATION], and a little less than the cover's own ([CALM]), so a bright blue cover gives a
 * steel blue that sits with it rather than a loud one. A cover in black, white and grey gets silver, from its
 * own greys: the theme's amber came from nowhere on it. Only a cover that couldn't be read keeps the theme's colour.
 */
object DynamicAccent {
    const val CONTROLS_CONTRAST = 3.0
    const val TEXT_CONTRAST = 4.5

    /** Below this chroma (the most and least of red, green and blue apart) a colour is grey: its hue means nothing. */
    const val GREY = 0.04f

    /** A cover's colour is calmed a little, and never past [MOST_SATURATION]: a soft tone of the cover, not a loud one. */
    private const val CALM = 0.8f
    const val MOST_SATURATION = 0.45f

    /**
     * A colour is never more saturated than this many times its chroma (the owner, 14 Nov: a grey photo with a blue tint
     * gave a steel blue Play). The saturation of a near grey, dark or light, is high on paper though it hardly has a
     * colour; its chroma says how much colour it really has.
     */
    private const val SATURATION_PER_CHROMA = 1.5f
    private const val LIGHTNESS_FROM = 0.52f

    /** A muted colour (below [MUTED] saturation) starts lighter, like the cover's own highlights, so it isn't muddy. */
    private const val MUTED = 0.35f
    private const val MUTED_LIGHTNESS_FROM = 0.62f

    /** Silver for a cover in black, white and grey: a light grey, lightened from here as needed. */
    private const val SILVER_FROM = 0.78f
    // Up to white: on the lightest covers, text may need it.
    private const val LIGHTNESS_TOP = 1.0f
    private const val STEP = 0.01f

    /**
     * A page lighter than this (its luminance) gets darker colours rather than lighter ones: here black and white stand
     * out from it equally, so the way chosen can always reach its contrast. Now playing stays below it.
     */
    private const val LIGHT_PAGE = 0.18f

    /** On a light page, a colour starts no lighter than this, and a black and white cover's from graphite. */
    private const val DARK_LIGHTNESS_FROM = 0.45f
    private const val GRAPHITE_FROM = 0.32f

    /** The grey [seedOf] gives a cover without a colour of its own: [of] makes it silver. */
    private val SILVER_SEED = Color(0.5f, 0.5f, 0.5f)

    /** A colour this lively counts as a colour of the cover's own, not a tint of its greys. */
    private const val VIVID = 0.15f

    /** Below this share of the cover, its lively colours are spots in a black and white cover. */
    private const val FEW_COLOURS = 0.15f

    /** Hues this far apart (a sixth of the round, 60°) are different colours: red and blue, not red and orange. */
    private const val OTHER_HUE = 1f / 6f

    /** A second colour with this much of the first's weight is as strong as it. */
    private const val RIVAL = 0.6

    /** A colour on less than this share of the cover is a spot, a title or a logo: [vividOf] finds its true colour. */
    private const val ACCENT_SHARE = 0.10f

    /** Pixels within this of a colour's hue (a 30th of the round, 12°) are the same colour. */
    private const val SAME_HUE = 1f / 30f

    /** Fewer pixels than this of a colour can't be told from noise. */
    private const val FEWEST_PIXELS = 12

    /** The play symbol on the button: dark or white, whichever reads better. */
    private val ON_DARK = Color(0xFF120D00)

    /**
     * The cover's colour from its [swatches] (issue #994): of the ones with a colour of their own, the one with the
     * most of the cover and of colour together (its share × √chroma, so a big muted area beats a small bright spot, and a
     * bright logo on a black cover still counts). With none (black, white and grey), a grey: [of] makes it silver. Null
     * without swatches.
     *
     * A cover in black and white with a few small spots of different colours (red, green and blue stripes) has no one
     * colour of its own: picking one of them would be a guess, so it's silver too.
     */
    fun seedOf(swatches: List<CoverSwatch>): Color? {
        if (swatches.isEmpty()) return null
        val coloured = swatches.filter { chroma(it.color) >= GREY }
        if (coloured.isEmpty()) return SILVER_SEED
        if (spotsOfManyColours(swatches)) return SILVER_SEED
        return coloured.maxBy { score(it) }.color
    }

    /**
     * The colour for the cover: [seedOf] its [swatches], made as lively as the cover's own pixels of it when it's a small
     * spot such as a title or a logo ([vividOf]). [pixels] are the cover's, as ARGB.
     */
    fun pick(swatches: List<CoverSwatch>, pixels: IntArray): Color? {
        val seed = seedOf(swatches) ?: return null
        if (chroma(seed) < GREY) return seed
        val total = swatches.sumOf { it.population }.coerceAtLeast(1)
        val share = swatches.filter { it.color == seed }.sumOf { it.population }.toFloat() / total
        return if (share < ACCENT_SHARE) vividOf(seed, pixels) else seed
    }

    /**
     * The cover's own, liveliest pixels of [seed]'s colour: the palette mixes a small yellow title with the grey around
     * its letters into khaki, while the title itself is bright yellow. Of the pixels of the same hue (within [SAME_HUE])
     * and at least as lively as [seed], the liveliest quarter's average; [seed] when there are too few of them.
     */
    fun vividOf(seed: Color, pixels: IntArray): Color {
        val hue = hsl(seed).first
        val least = chroma(seed)
        val same = ArrayList<Color>()
        for (argb in pixels) {
            if ((argb ushr 24) < 0x80) continue
            val c = Color(argb)
            val ch = chroma(c)
            if (ch < least || ch < GREY) continue
            if (hueDistance(hsl(c).first, hue) > SAME_HUE) continue
            same += c
        }
        if (same.size < FEWEST_PIXELS) return seed
        val liveliest = same.sortedByDescending { chroma(it) }.take((same.size / 4).coerceAtLeast(FEWEST_PIXELS))
        val n = liveliest.size.toFloat()
        return Color(liveliest.sumOf { it.red.toDouble() }.toFloat() / n, liveliest.sumOf { it.green.toDouble() }.toFloat() / n, liveliest.sumOf { it.blue.toDouble() }.toFloat() / n)
    }

    /**
     * A cover that is mostly black, white and grey ([VIVID] colours fill less than [FEW_COLOURS] of it) whose two
     * liveliest colours are far apart ([OTHER_HUE]) and as strong as each other: no single colour stands for it.
     */
    private fun spotsOfManyColours(swatches: List<CoverSwatch>): Boolean {
        val total = swatches.sumOf { it.population }.coerceAtLeast(1)
        val vivid = swatches.filter { chroma(it.color) >= VIVID }.sortedByDescending { score(it) }
        if (vivid.size < 2) return false
        if (vivid.sumOf { it.population }.toFloat() / total >= FEW_COLOURS) return false
        val (first, second) = vivid
        return hueDistance(hsl(first.color).first, hsl(second.color).first) >= OTHER_HUE && score(second) >= RIVAL * score(first)
    }

    private fun score(s: CoverSwatch): Double = s.population * kotlin.math.sqrt(chroma(s.color).toDouble())

    /** How far apart two hues (0 to 1, round) are, 0 to ½. */
    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b)
        return min(d, 1f - d)
    }

    /** Hues further apart than this (0 to ½, a sixth of the circle: 60°) are of different families. */
    private const val OTHER_FAMILY = 1f / 6f

    /**
     * The seed for a page whose header is painted in [tint] (the owner, 13 Nov: an artist page in khaki with a steel
     * blue Play, the picture's sky): [seed] when it's of the header's family or either is grey, else the header's own
     * colour, so Play never clashes with the header above it.
     */
    fun inFamily(seed: Color, tint: Color?): Color {
        if (tint == null || chroma(seed) < GREY || chroma(tint) < GREY) return seed
        return if (hueDistance(hsl(seed).first, hsl(tint).first) > OTHER_FAMILY) tint else seed
    }

    /**
     * The colours for a cover whose own colour is [seed] ([seedOf]), on now playing's [backdrop] (see [backdropOf]). A
     * grey [seed] gives silver. On a page whose header is painted in [tint], a seed of another family gives way to the
     * header's ([inFamily]). Null only for a colour that can't be read.
     */
    fun of(seed: Color, backdrop: Color, tint: Color? = null): DynamicColours? {
        if (seed.alpha <= 0f) return null
        val (h, s0, l0) = hsl(inFamily(seed, tint))
        val grey = chroma(seed) < GREY
        val s = if (grey) 0f else min(MOST_SATURATION, min(s0 * CALM, chroma(seed) * SATURATION_PER_CHROMA))
        // A light page (an album in the light theme, issue #1028): the colour is darkened until it stands out, from
        // graphite for a black and white cover. Now playing is always dark behind its controls.
        val lightPage = backdrop.luminance() > LIGHT_PAGE
        val from = when {
            lightPage && grey -> GRAPHITE_FROM
            lightPage -> min(l0, DARK_LIGHTNESS_FROM)
            grey -> SILVER_FROM
            s < MUTED -> max(l0, MUTED_LIGHTNESS_FROM)
            else -> max(l0, LIGHTNESS_FROM)
        }
        // Play's colour must also carry its symbol, dark or white: on a light page a middling colour stands out from the
        // page yet leaves neither easy to read, so it goes on darkening until white is.
        val controls = shadeUntil(h, s, from, lighter = !lightPage) {
            contrast(it, backdrop) >= CONTROLS_CONTRAST && max(contrast(it, ON_DARK), contrast(it, Color.White)) >= TEXT_CONTRAST
        }
        val text = shadeUntil(h, s, from, lighter = !lightPage) { contrast(it, backdrop) >= TEXT_CONTRAST }
        val on = if (contrast(controls, ON_DARK) >= contrast(controls, Color.White)) ON_DARK else Color.White
        return DynamicColours(controls, text, on)
    }

    /**
     * What's behind the controls for a cover of [average] colour: now playing draws the cover blurred at 85% over
     * black, under black at about 45% where the controls are.
     */
    fun backdropOf(average: Color): Color = lerp(Color.Black, average, 0.85f * 0.55f)

    /** The colour of hue [h] and saturation [s], made [lighter] (or darker) from [from] until it's [good] (stands out). */
    private fun shadeUntil(h: Float, s: Float, from: Float, lighter: Boolean, good: (Color) -> Boolean): Color {
        var l = from
        var colour = fromHsl(h, s, l)
        while ((if (lighter) l < LIGHTNESS_TOP else l > 0f) && !good(colour)) {
            l = if (lighter) l + STEP else (l - STEP).coerceAtLeast(0f)
            colour = fromHsl(h, s, l)
        }
        return colour
    }

    /** WCAG's contrast between two colours, 1 to 21. */
    fun contrast(a: Color, b: Color): Double {
        val x = a.luminance().toDouble()
        val y = b.luminance().toDouble()
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    /** How much colour there is: the most and least of red, green and blue apart, 0 for grey to 1. */
    fun chroma(c: Color): Float = max(c.red, max(c.green, c.blue)) - min(c.red, min(c.green, c.blue))

    private fun hsl(c: Color): Triple<Float, Float, Float> {
        val r = c.red
        val g = c.green
        val b = c.blue
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val l = (hi + lo) / 2f
        if (hi == lo) return Triple(0f, 0f, l)
        val d = hi - lo
        val s = if (l > 0.5f) d / (2f - hi - lo) else d / (hi + lo)
        val h = when (hi) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f))
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } / 6f
        return Triple(h, s, l)
    }

    private fun fromHsl(h: Float, s: Float, l: Float): Color {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = (h * 6f) % 6f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r, g, b) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f))
    }
}

/**
 * Now playing's colours from [coverId]'s cover (issue #970) while [on]; null when off, before the cover has been
 * looked at, or for a cover without a colour of its own. They fade from song to song.
 *
 * On a page ([backdrop] given, issue #1028), they stand out from that page's colour instead of now playing's blurred
 * cover; [where] names the page in the log. The cover's colours come from [rememberCoverTones]: kept on the phone, so a
 * page or song seen before has them in its first frame.
 */
@Composable
fun rememberDynamicColours(
    coverId: String?,
    on: Boolean,
    backdrop: Color? = null,
    where: String = "Now playing",
    tint: Color? = null,
    /** Something the colours depend on isn't known yet (a page's header colour): nothing is worked out until it is. */
    waiting: Boolean = false,
    /** What shows until the colours are known, in place of the theme's. */
    until: DynamicColours? = null,
): DynamicColours? {
    val tones = rememberCoverTones(if (on) coverId else null)
    val known = tones.takeIf { !waiting }
    val colours = remember(known, backdrop, tint) {
        known?.let { t -> t.seed?.let { DynamicAccent.of(it, backdrop ?: DynamicAccent.backdropOf(t.average), tint) } }
    }
    // Once per cover, not on every frame of a page colour fading in.
    LaunchedEffect(colours) {
        if (colours != null) AppLog.d("Colours", "$where in the cover's colours: controls ${hex(colours.controls)}, text ${hex(colours.text)}")
    }
    // While the next cover is read, the last song's colours stay, so the theme's doesn't flash in between; a cover that
    // couldn't be read gives the theme's.
    var last by remember { mutableStateOf<DynamicColours?>(null) }
    androidx.compose.runtime.SideEffect { if (known != null) last = colours }
    if (!on) return null
    // Known in the first frame (kept on the phone), they're drawn at once: the fade only runs from one cover's colours
    // to another's, or from [until] for a cover read while the page is open.
    val target = (if (known != null) colours else last ?: until) ?: return null
    val controls by animateColorAsState(target.controls, tween(FADE_MS), label = "dynamicControls")
    val text by animateColorAsState(target.text, tween(FADE_MS), label = "dynamicText")
    return DynamicColours(controls, text, target.onControls)
}

/**
 * [content] with its Play button, Shuffle and the other buttons in the colour of [coverId]'s cover, with Style's
 * Dynamic colours on (issue #1028), standing out from a page of [backdrop]. With it off, or before the cover has
 * been read, the theme's colours.
 */
@Composable
fun WithPageColours(coverId: String?, backdrop: Color, where: String, content: @Composable () -> Unit) {
    val colours = rememberPageColours(coverId, backdrop, where)
    WithAccent(colours?.controls, colours?.onControls, content)
}

/** The page's colours for its Play button only (Dynamic colours), set by the page around its content. */
val LocalPlayColours = androidx.compose.runtime.staticCompositionLocalOf<DynamicColours?> { null }

/**
 * A page's colours from [coverId]'s cover on a page of [backdrop], with Style's Dynamic colours on; null when off.
 * [tint] is the colour the page's header is painted in ([rememberCoverColor]): Play keeps to its family.
 */
@Composable
fun rememberPageColours(
    coverId: String?,
    backdrop: Color,
    where: String,
    tint: Color? = null,
    /** The page's picture isn't known yet (an artist's photo being looked up): Play waits in silver, never the theme's amber. */
    pending: Boolean = false,
): DynamicColours? {
    val on = LocalGraph.current.settings.state.collectAsStateWithLifecycle().value.dynamicColours
    val surface = MaterialTheme.colorScheme.surface
    val grey = MaterialTheme.colorScheme.surfaceVariant
    val silver = remember(surface, grey) { DynamicAccent.of(coverTone(grey), pageBackdrop(surface, coverTone(grey))) }
    if (on && pending && coverId == null) return silver
    if (tint == null) return rememberDynamicColours(coverId, on && coverId != null, backdrop, where)
    // A page with a painted header: its colours are worked out once, from the header's colour as it will be, not from
    // each shade it fades through (the owner, 14 Nov: Play was blue while the header was still grey, then turned to
    // the header's family). Until the cover has been looked at, Play is the silver a page without a colour has, and
    // fades to its colour once, with the header.
    val main = rememberCoverTarget(coverId)
    val settled = main?.takeIf { it != Color.Unspecified }?.let { coverTone(it) }
    return rememberDynamicColours(
        coverId, on && coverId != null,
        backdrop = pageBackdrop(surface, settled ?: coverTone(grey)),
        where = where,
        tint = settled,
        waiting = main == null,
        until = silver,
    )
}

/**
 * The colour behind a page's buttons: its cover colour ([rememberCoverColor]) runs down into the page, mostly faded
 * by where the buttons are.
 */
fun pageBackdrop(surface: Color, coverTone: Color): Color = lerp(surface, coverTone.copy(alpha = 1f), PAGE_TINT)

/** How much of the cover colour is left behind a page's buttons. */
private const val PAGE_TINT = 0.3f

private fun hex(c: Color) = "#%06X".format(0xFFFFFF and (((c.red * 255).toInt() shl 16) or ((c.green * 255).toInt() shl 8) or (c.blue * 255).toInt()))

private const val FADE_MS = 600
