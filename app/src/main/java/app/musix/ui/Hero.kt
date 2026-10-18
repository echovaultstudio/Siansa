package app.musix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * A page whose picture fills the top of the screen, up behind the status bar, as on a Spotify
 * artist page. Scrolling slides the page up while the picture drifts at half speed, zooms gently
 * and fades out smoothly into [color]; the title bar fills with [color] once the picture is gone.
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
    content: @Composable (pageModifier: Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val heroPx = with(density) { heroHeight.toPx() }
    val progress = (scrolledPx / heroPx).coerceIn(0f, 1f)
    val surface = MaterialTheme.colorScheme.surface

    Box(Modifier.fillMaxSize().background(surface)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .background(color)
                .graphicsLayer {
                    translationY = -min(scrolledPx, heroPx) * 0.5f
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

        // Paints the page behind the scrolling content, starting where the picture ends.
        val pageModifier = Modifier.drawBehind {
            val top = (heroPx - scrolledPx).coerceAtLeast(0f)
            drawRect(surface, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
        }
        content(pageModifier)

        val barAlpha = ((scrolledPx - heroPx * 0.65f) / (heroPx * 0.25f)).coerceIn(0f, 1f)
        Row(
            Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = barAlpha))
                .statusBarsPadding()
                .padding(horizontal = Space.Tight, vertical = Space.Tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.3f * (1f - barAlpha))),
            ) {
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
                modifier = Modifier.graphicsLayer { alpha = barAlpha },
            )
        }
    }
}

/** The spacer every hero page's list or grid starts with. */
@Composable
fun HeroSpacer(heroHeight: Dp) {
    Spacer(Modifier.fillMaxWidth().height(heroHeight))
}
