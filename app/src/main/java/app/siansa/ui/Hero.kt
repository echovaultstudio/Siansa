package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
 * The title bar fills with [color] once the picture is gone, and [pinned] (the release-type tabs) sits
 * under it once the page has scrolled past them.
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

        val barAlpha = ((scrolledPx - heroPx * 0.65f) / (heroPx * 0.25f)).coerceIn(0f, 1f)
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = barAlpha },
            )
            actions()
        }
        if (pinned != null) {
            // In the title bar's colour, as one with it, its words white as the title's.
            Box(Modifier.fillMaxWidth().background(color.copy(alpha = 1f)).testTag("heroPinned")) { pinned() }
        }
        }
    }
}

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
