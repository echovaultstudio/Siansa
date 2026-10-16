package app.musix.ui

import android.os.Build
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
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
 * A page whose picture fills the top of the screen, up behind the status bar. As the page scrolls,
 * the content card slides up over the picture while the picture drifts, fades and blurs behind it,
 * and a title bar fades in once the picture is mostly covered.
 *
 * [scrolledPx] is how far the first item has scrolled (from the list or grid state);
 * [content] gets a modifier that draws the card behind the scrolling content and must start with a
 * [heroHeight] - [overlap] spacer item.
 */
@Composable
fun HeroPage(
    title: String,
    heroHeight: Dp,
    scrolledPx: Float,
    onBack: () -> Unit,
    image: @Composable BoxScope.(Modifier) -> Unit,
    overlap: Dp = 28.dp,
    content: @Composable (cardModifier: Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val heroPx = with(density) { heroHeight.toPx() }
    val cardTopPx = with(density) { (heroHeight - overlap).toPx() }
    val radiusPx = with(density) { overlap.toPx() }
    val progress = (scrolledPx / cardTopPx).coerceIn(0f, 1f)
    val surface = MaterialTheme.colorScheme.surface

    Box(Modifier.fillMaxSize().background(surface)) {
        // The picture: drifts up at half speed, fades and blurs as the card covers it.
        Box(
            Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .graphicsLayer {
                    translationY = -min(scrolledPx, heroPx) * 0.5f
                    alpha = 1f - progress * 0.7f
                    val scale = 1f + progress * 0.08f
                    scaleX = scale
                    scaleY = scale
                }
                .then(if (Build.VERSION.SDK_INT >= 31 && progress > 0.02f) Modifier.blur((progress * 24).dp) else Modifier),
        ) {
            image(Modifier.fillMaxSize())
            // Darkens the bottom edge so the card's rounded top reads clearly.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to surface.copy(alpha = 0.85f))
                )
            )
        }

        // The card behind the scrolling content, drawn once so items sit on one continuous surface.
        val cardModifier = Modifier.drawBehind {
            val top = (cardTopPx - scrolledPx).coerceAtLeast(0f)
            val r = if (top > 0f) radiusPx else 0f
            drawRoundRect(surface, topLeft = Offset(0f, top), size = Size(size.width, size.height - top + radiusPx), cornerRadius = CornerRadius(r, r))
        }
        content(cardModifier)

        // Title bar: transparent over the picture, solid once the picture is covered.
        val barAlpha = ((scrolledPx - cardTopPx * 0.6f) / (cardTopPx * 0.4f)).coerceIn(0f, 1f)
        Row(
            Modifier
                .fillMaxWidth()
                .background(surface.copy(alpha = barAlpha))
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.35f * (1f - barAlpha))),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    "Back",
                    tint = if (barAlpha > 0.5f) MaterialTheme.colorScheme.onSurface else Color.White,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { alpha = barAlpha },
            )
        }
    }
}

/** The spacer every hero page's list or grid starts with. */
@Composable
fun HeroSpacer(heroHeight: Dp, overlap: Dp = 28.dp) {
    Spacer(Modifier.fillMaxWidth().height(heroHeight - overlap))
}
