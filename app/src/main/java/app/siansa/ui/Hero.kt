package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * A page whose picture fills the top of the screen, up behind the status bar (an artist's page). Scrolling
 * slides the page up over the picture, which stays almost still, zooms gently and fades out into [color];
 * the page comes up with a soft edge, so the picture fades into it rather than being cut off (issue #1029).
 * [title] sits large at the foot of the picture and rides up with the page; near the top it glides into the
 * title bar, shrinking as it goes, and becomes the bar's title (the owner, 10 Nov: [heroNameFrame]). The bar
 * fills with [color] as it arrives, and [pinned] (the release-type tabs) sits under it once the page has
 * scrolled past them.
 *
 * [scrolledPx] is how far the first item has scrolled; the list or grid must start with [HeroSpacer]
 * and use the given modifier, which paints the page behind its items.
 */
@Composable
fun HeroPage(
    title: String,
    heroHeight: Dp,
    scrolledPx: Float,
    onBack: () -> Unit,
    color: Color,
    image: @Composable BoxScope.(Modifier) -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
    // Buttons at the right of the title bar (the artist page's heart, issue #397).
    actions: @Composable RowScope.() -> Unit = {},
    // Kept under the title bar while it's given (the artist's release-type tabs once scrolled to, issue #1029).
    pinned: (@Composable () -> Unit)? = null,
    content: @Composable (pageModifier: Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val heroPx = with(density) { heroHeight.toPx() }
    val softPx = with(density) { SOFT_EDGE.toPx() }
    val progress = (scrolledPx / heroPx).coerceIn(0f, 1f)
    val surface = MaterialTheme.colorScheme.surface

    Box(Modifier.fillMaxSize().background(surface)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .background(color)
                .graphicsLayer {
                    // Drifts up slowly while the page slides over it, as if it stayed put.
                    translationY = -min(scrolledPx, heroPx) * HERO_DRIFT
                    // Eased fade: holds while you start scrolling, then melts away.
                    alpha = 1f - progress * progress * (3f - 2f * progress)
                    val scale = 1f + progress * 0.06f
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            image(Modifier.fillMaxSize())
            // A long, soft fade from the picture into the page colour.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f),
                        0.35f to Color.Transparent,
                        0.65f to color.copy(alpha = 0.35f),
                        1f to surface,
                    )
                )
            )
            Box(Modifier.align(Alignment.BottomStart)) { overlay() }
        }

        // Paints the page behind the scrolling content, starting where the picture ends, with a soft edge above it
        // (issue #1029): the picture fades into the page as it slides up, instead of the page's hard top edge cutting
        // across it. At rest the picture's own fade is there, and the name on it stays clear, so the edge grows in as
        // the page starts to move.
        val pageModifier = Modifier.drawBehind {
            val top = (heroPx - scrolledPx).coerceAtLeast(0f)
            val soft = (scrolledPx / softPx).coerceIn(0f, 1f)
            if (soft > 0f && top > 0f) {
                drawRect(
                    Brush.verticalGradient(
                        0f to surface.copy(alpha = 0f),
                        0.6f to surface.copy(alpha = 0.55f * soft),
                        1f to surface.copy(alpha = soft),
                        startY = top - softPx,
                        endY = top,
                    ),
                    topLeft = Offset(0f, top - softPx),
                    size = Size(size.width, softPx),
                )
            }
            drawRect(surface, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
        }
        content(pageModifier)

        // The name's way from the picture into the title bar, all of it worked out while drawing, so it follows the
        // finger frame by frame.
        val statusPx = WindowInsets.statusBars.getTop(density).toFloat()
        val barPx = with(density) { HERO_TITLE_BAR.toPx() }
        val bigStyle = MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.Black,
            // Reads on any photo, and on the page as the photo fades.
            shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 14f),
        )
        val smallStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
        val dockScale = smallStyle.fontSize.value / bigStyle.fontSize.value
        val lineHeightPx = with(density) { bigStyle.lineHeight.toPx() }
        val restLeftPx = with(density) { Space.Page.toPx() }
        // Where the bar's title starts: the bar's padding, the back arrow's button and the gap after it.
        val dockLeftPx = with(density) { (Space.Tight + BACK_BUTTON + Space.Small).toPx() }
        val restBottomPx = with(density) { Space.Medium.toPx() }
        val startGapPx = with(density) { DOCK_START_GAP.toPx() }
        var nameHeight by remember { mutableIntStateOf(0) }
        val restTop = heroPx - nameHeight - restBottomPx
        val dockTop = statusPx + barPx / 2f - lineHeightPx * dockScale / 2f
        val frame = heroNameFrame(scrolledPx, restTop, startTop = statusPx + barPx + startGapPx, dockTop = dockTop)
        val dockT = frame.t
        val barAlpha = maxOf(((scrolledPx - heroPx * 0.65f) / (heroPx * 0.25f)).coerceIn(0f, 1f), smooth(dockT))
        // The last of the way: the shrunk name hands over to the bar's own title (one line, cut off with "…"
        // before the buttons), in the same place and size, so a long name ends tidy.
        val handOver = ((dockT - HAND_OVER_FROM) / (1f - HAND_OVER_FROM)).coerceIn(0f, 1f)
        Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = barAlpha))
                .statusBarsPadding()
                .padding(horizontal = Space.Tight, vertical = Space.Tight)
                .testTag("heroTitleBar"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // No shading behind the arrow (issue #228).
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
            }
            Spacer(Modifier.width(Space.Small))
            Text(
                title,
                style = smallStyle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = handOver }.testTag("heroBarTitle"),
            )
            actions()
        }
        if (pinned != null) {
            // In the title bar's colour, as one with it, its words white as the title's.
            Box(Modifier.fillMaxWidth().background(color.copy(alpha = 1f)).testTag("heroPinned")) { pinned() }
        }
        }

        // The name, over everything: it rides up with the page, then glides into the bar (above it, so the bar
        // filling in never covers it on the way).
        Text(
            title,
            style = bigStyle,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = Space.Page, end = Space.Page)
                .onSizeChanged { nameHeight = it.height }
                .graphicsLayer {
                    // Laid out at the page's left edge; moved and shrunk from its top left corner.
                    transformOrigin = TransformOrigin(0f, 0f)
                    val e = smooth((dockT * SIDEWAYS_LEAD).coerceAtMost(1f))
                    translationX = (dockLeftPx - restLeftPx) * e
                    translationY = frame.top
                    val sc = 1f + (dockScale - 1f) * smooth(dockT)
                    scaleX = sc
                    scaleY = sc
                    alpha = if (nameHeight == 0) 0f else 1f - handOver
                }
                .testTag("heroName"),
        )
    }
}

/** Where the hero's name is, [top] in px from the top of the screen, and how far into the title bar it is ([t], 0–1). */
data class HeroNameFrame(val top: Float, val t: Float)

/**
 * The hero's name as the page scrolls [scrolled] px: at rest its top is at [restTop], and it moves up with the page
 * until it reaches [startTop] (just under the title bar). From there it glides into the bar's title place, [dockTop],
 * over [DOCK_SPAN] times that last distance of scrolling: its speed at the start is the page's, so there's no jolt,
 * and it slows to a stop as it settles (a Hermite curve, which never overshoots at this span). Scrolling back runs it
 * the other way, frame for frame.
 */
fun heroNameFrame(scrolled: Float, restTop: Float, startTop: Float, dockTop: Float): HeroNameFrame {
    // A tall font on a short screen can start the name above the start line: then it starts gliding at once.
    val start = minOf(startTop, restTop).coerceAtLeast(dockTop)
    val free = restTop - scrolled
    if (free >= start) return HeroNameFrame(free, 0f)
    val distance = start - dockTop
    if (distance <= 0f) return HeroNameFrame(dockTop, 1f)
    val span = DOCK_SPAN * distance
    val t = ((scrolled - (restTop - start)) / span).coerceIn(0f, 1f)
    // Hermite from (start, moving up at the page's speed) to (dockTop, at rest).
    val t2 = t * t
    val t3 = t2 * t
    val top = (2 * t3 - 3 * t2 + 1) * start + (t3 - 2 * t2 + t) * (-span) + (-2 * t3 + 3 * t2) * dockTop
    return HeroNameFrame(top, t)
}

private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

/** How many times the last distance into the bar the name takes to glide there (at most 3, or it would overshoot). */
const val DOCK_SPAN = 2.5f

/** How far under the title bar the name starts gliding into it. */
private val DOCK_START_GAP = Space.Section

/** The back arrow's button, before the bar's title. */
private val BACK_BUTTON = 48.dp

/** The name is sideways in its place a little before it's up: it clears the back arrow on the way. */
private const val SIDEWAYS_LEAD = 1.3f

/** From here on the way into the bar, the name hands over to the bar's own title. */
private const val HAND_OVER_FROM = 0.8f

/** How fast the picture drifts up as the page slides over it: slowly, so it seems to stay put. */
private const val HERO_DRIFT = 0.3f

/** How tall the soft edge at the top of the page is, where the picture fades into it. */
private val SOFT_EDGE = 140.dp

/** The title bar's height under the status bar: an icon button and the bar's padding. */
val HERO_TITLE_BAR = 56.dp

/** The spacer every hero page's list or grid starts with. */
@Composable
fun HeroSpacer(heroHeight: Dp) {
    Spacer(Modifier.fillMaxWidth().height(heroHeight))
}
