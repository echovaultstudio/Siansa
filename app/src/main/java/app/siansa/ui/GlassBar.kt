package app.siansa.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/**
 * The title bar of every page with a cover or a picture at its top: an album, a playlist, a mix, a radio, liked
 * songs, an artist, a book, a podcast (the owner, 15 Nov: the title sliding into a solid bar looked clunky, and Play
 * and the heart shouldn't be up there). Nothing travels. As the page's own title goes under the bar, three things come
 * in together, on one number ([step]):
 *
 * - a wash of the page's colour behind what scrolls ([GlassWash]): all of the colour at the very top, running out
 *   below the bar on one curve with no edge and no stripes (solid colours, dithered, under [SoftFade]'s grain);
 * - what scrolls dissolves into it ([dissolveUnderBar]): nothing shows behind the bar's title, all of it at the wash's
 *   end, so no row is cut by a line or read through the colour;
 * - the page's title fades out, then the bar's fades in, in the middle of the bar ([GlassTitleBar]): the name is never
 *   there twice.
 */
internal object GlassBar {
    /** The bar's height, under the status bar. */
    val HEIGHT = 56.dp
    /** How far below the bar the colour runs out. */
    val TAIL = 40.dp
    /** The page's title starts the bar this far before it reaches the bar's lower edge. */
    val LEAD = Space.Page
    /** How far the bar's title rises into its place as it fades in. */
    val RISE = Space.Small
    /** On a short page: room enough at its end to bring the page's title under the bar. */
    val TITLE_ROOM = 200.dp
    const val STEPS = 32

    /**
     * How far the bar has come in, 0–1, for a page title whose top is at [titleTop] px (from the top of the screen)
     * and is [titleHeight] px tall, under a bar that ends at [barBottom]: 0 until the title is [lead] px from the
     * bar, 1 once all of it is under the bar, eased at both ends so it neither starts nor stops with a jolt.
     */
    fun step(titleTop: Float, titleHeight: Float, barBottom: Float, lead: Float): Float {
        val span = titleHeight + lead
        if (span <= 0f) return if (titleTop < barBottom) 1f else 0f
        return smooth(((barBottom + lead - titleTop) / span).coerceIn(0f, 1f))
    }

    /** How much of the page's own title shows at [step]: gone by half way. */
    fun pageTitle(step: Float): Float = 1f - smooth((step / 0.5f).coerceIn(0f, 1f))

    /** How much of the bar's title shows at [step]: from just before half way, so the two never show together. */
    fun barTitle(step: Float): Float = smooth(((step - 0.45f) / 0.55f).coerceIn(0f, 1f))

    /**
     * The colour's share [t] of the way down the wash (0 its top, 1 its end): all of it at the top, three quarters
     * behind the bar's title, none at the end, on one curve that is level at both ends (no edge where it runs out).
     */
    fun colour(t: Float): Float = 1f - smooth(t.coerceIn(0f, 1f).pow(1.6f))

    /**
     * How much of what scrolls under the bar shows [t] of the way down the wash: nothing down to [solid] (behind the
     * bar's title, and the tabs pinned under it), all of it at the end, level at both ends.
     */
    fun shows(t: Float, solid: Float): Float {
        if (solid >= 1f) return if (t >= 1f) 1f else 0f
        return smooth(((t - solid) / (1f - solid)).coerceIn(0f, 1f))
    }

    /** What scrolls starts to show again this far above the bar's lower edge (below the bar's title). */
    val SOFT = 12.dp
}

/**
 * The wash of [color] at the top of a page, [height] tall, running out to nothing; [amount] of it (0 none, 1 all).
 * Goes behind what scrolls, which [dissolveUnderBar] lets it show through. See-through where it thins, so it has no
 * edge over a picture either (an artist's photo behind it).
 */
@Composable
internal fun GlassWash(color: Color, height: Dp, amount: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { alpha = amount() }
            .drawWithCache {
                val colours = IntArray(GlassBar.STEPS + 1) { i -> color.copy(alpha = GlassBar.colour(i / GlassBar.STEPS.toFloat())).toArgb() }
                val paint = android.graphics.Paint().apply {
                    isDither = true
                    shader = android.graphics.LinearGradient(0f, 0f, 0f, size.height.coerceAtLeast(1f), colours, null, android.graphics.Shader.TileMode.CLAMP)
                }
                // The grain thins with the colour, so it stops where the colour does.
                val grain = ShaderBrush(ImageShader(SoftFade.grain, TileMode.Repeated, TileMode.Repeated))
                val thinning = Brush.verticalGradient(0f to Color.Black, 0.5f to Color.Black, 1f to Color.Transparent)
                onDrawBehind {
                    drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), androidx.compose.ui.graphics.Paint())
                        drawRect(grain)
                        drawRect(thinning, blendMode = BlendMode.DstIn)
                        canvas.restore()
                    }
                }
            }
            .testTag("glassWash"),
    )
}

/**
 * What scrolls under the bar dissolves over the top [heightPx] of it, none of it showing down to [solidPx], as far as
 * [amount] says (0 not at all): see [GlassBar.shows]. For the list or grid of a page with a [GlassWash] behind it.
 */
internal fun Modifier.dissolveUnderBar(heightPx: () -> Float, solidPx: () -> Float, amount: () -> Float): Modifier = this
    // Its own layer only while something dissolves: the rest of the time the list draws as it always did.
    .graphicsLayer { compositingStrategy = if (amount() > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
    // The fade is made again only when it changes (as the bar comes in), not at every frame of a scroll.
    .drawWithCache {
        val a = amount().coerceIn(0f, 1f)
        val h = heightPx()
        val fade = if (a > 0f && h > 0f) {
            val solid = (solidPx() / h).coerceIn(0f, 1f)
            val stops = Array(GlassBar.STEPS + 1) { i ->
                val t = i / GlassBar.STEPS.toFloat()
                t to Color.Black.copy(alpha = 1f - a * (1f - GlassBar.shows(t, solid)))
            }
            Brush.verticalGradient(*stops, startY = 0f, endY = h)
        } else null
        onDrawWithContent {
            drawContent()
            if (fade != null) drawRect(fade, size = Size(size.width, h), blendMode = BlendMode.DstIn)
        }
    }

/**
 * The bar itself: the back arrow, [title] in the middle (as much of it as [titleAmount] says, rising into its place),
 * and [actions] at the right for the few pages that have some (never Play or a heart). It draws nothing behind
 * itself: the colour is the page's [GlassWash].
 */
@Composable
internal fun GlassTitleBar(
    title: String,
    onBack: () -> Unit,
    titleAmount: () -> Float,
    modifier: Modifier = Modifier,
    tag: String = "glassTitleBar",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    var actionsPx by remember { mutableIntStateOf(0) }
    val risePx = with(density) { GlassBar.RISE.toPx() }
    // In the middle of the bar, whatever is at its sides: as much room is kept free at the left as at the right.
    val side = maxOf(Space.Tight + BACK_BUTTON + Space.Small, Space.Tight + with(density) { actionsPx.toDp() } + Space.Small)
    Box(modifier.fillMaxWidth().statusBarsPadding().height(GlassBar.HEIGHT).testTag(tag)) {
        // No shading behind the arrow (issue #228).
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = Space.Tight)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = side)
                .graphicsLayer {
                    val a = titleAmount().coerceIn(0f, 1f)
                    alpha = a
                    translationY = (1f - a) * risePx
                }
                .testTag("barTitle"),
        )
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = Space.Tight).onSizeChanged { actionsPx = it.width },
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}
