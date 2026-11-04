package app.siansa.tvapp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.siansa.ui.Cover
import app.siansa.ui.SMALL_COVER
import app.siansa.ui.Space

/**
 * Focus on a TV (issue #860): what the remote is on grows a little and gets the accent's ring, so it can be seen from
 * the sofa. Everything the remote can reach is one of these, and OK (the centre button) presses it.
 */
@Composable
fun Modifier.tvFocus(shape: Shape, onClick: () -> Unit, grow: Float = FOCUS_GROW): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) grow else 1f, label = "tvFocus")
    val ring = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .onFocusChanged { focused = it.isFocused }
        .border(FOCUS_RING, ring, shape)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

/** A cover with a title and a line under it, for Home's rows and the Library's grids. */
@Composable
fun TvTile(
    title: String,
    subtitle: String?,
    coverId: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = TILE_WIDTH,
    round: Boolean = false,
) {
    val shape: Shape = if (round) CircleShape else RoundedCornerShape(TILE_CORNER)
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
        Cover(coverId, SMALL_COVER, Modifier.fillMaxWidth().aspectRatio(1f).tvFocus(shape, onClick), shape)
        Column(verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A pill-shaped button: an icon and a word. */
@Composable
fun TvButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, strong: Boolean = false) {
    val shape = RoundedCornerShape(percent = 50)
    val back = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fore = if (strong) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.tvFocus(shape, onClick, grow = BUTTON_GROW).background(back, shape).padding(horizontal = Space.Section, vertical = Space.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        if (icon != null) Icon(icon, null, tint = fore)
        Text(text, color = fore, style = MaterialTheme.typography.titleMedium)
    }
}

/** A round button with only an icon, for now playing's controls; [on] marks a switch that's on (shuffle, repeat). */
@Composable
fun TvIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, big: Boolean = false, on: Boolean = false) {
    val back = if (big) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fore = when {
        big -> MaterialTheme.colorScheme.onPrimary
        on -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier.size(if (big) BIG_CONTROL else CONTROL).tvFocus(CircleShape, onClick, grow = BUTTON_GROW).background(back, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = fore, modifier = Modifier.size(if (big) BIG_CONTROL_ICON else CONTROL_ICON))
    }
}

/** The room at a TV page's left and right edges: TVs may cut off the edge of the picture (overscan). */
val TV_EDGE = Space.Large

private const val FOCUS_GROW = 1.08f
private const val BUTTON_GROW = 1.1f
private val FOCUS_RING = 3.dp
val TILE_WIDTH = 168.dp
private val TILE_CORNER = 10.dp
private val CONTROL = 56.dp
private val BIG_CONTROL = 76.dp
private val CONTROL_ICON = 28.dp
private val BIG_CONTROL_ICON = 40.dp
