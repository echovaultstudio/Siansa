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
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Switch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
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
    // Drawn over its neighbours while it's grown, so it never looks merged into the button beside it.
    return this
        .zIndex(if (focused) 1f else 0f)
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
    /** A radio or genre has no cover: its two colours instead, with its name on them. */
    colors: Pair<Color, Color>? = null,
) {
    val shape: Shape = if (round) CircleShape else RoundedCornerShape(TILE_CORNER)
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
        val art = Modifier.fillMaxWidth().aspectRatio(1f).tvFocus(shape, onClick)
        if (colors != null) {
            Box(art.background(Brush.linearGradient(listOf(colors.first, colors.second)), shape).padding(Space.Medium), contentAlignment = Alignment.BottomStart) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Cover(coverId, SMALL_COVER, art, shape)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
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
    val fore = if (strong) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
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

/** A row of a list of settings: what it is, a line on what it does or is set to, and what OK does. */
@Composable
fun TvSettingRow(title: String, detail: String?, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, trailing: (@Composable () -> Unit)? = null) {
    val shape = RoundedCornerShape(Space.Medium)
    Row(
        modifier.fillMaxWidth().tvFocus(shape, onClick, grow = ROW_GROW).background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(horizontal = Space.Section, vertical = Space.Page),
        horizontalArrangement = Arrangement.spacedBy(Space.Page),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

/** A setting that's on or off: OK switches it. */
@Composable
fun TvSwitchRow(title: String, detail: String?, on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    TvSettingRow(title, detail, onClick = { onChange(!on) }, modifier = modifier, trailing = {
        Switch(checked = on, onCheckedChange = null)
    })
}

/**
 * A short list of choices over the page (a song's actions, the sources to show): OK picks one, Back closes it. The first
 * choice has the focus when it opens.
 */
@Composable
fun TvChoices(title: String, choices: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = CHOICES_WIDTH).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Space.Page)).padding(Space.Section),
            verticalArrangement = Arrangement.spacedBy(Space.Small),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = Space.Small))
            choices.forEachIndexed { i, (label, action) ->
                TvSettingRow(label, null, onClick = { onDismiss(); action() }, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/**
 * Where the remote starts on a page: the main button (Play on an album, the first row of a list). Without it nothing has
 * the focus when a page opens, and the first press only finds a place to start.
 */
@Composable
fun Modifier.initialFocus(): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    return focusRequester(requester)
}

/** A small value that opens something: an artist's name, a genre. */
@Composable
fun TvChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, strong: Boolean = false) {
    val shape = RoundedCornerShape(percent = 50)
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.tvFocus(shape, onClick, grow = BUTTON_GROW).background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(horizontal = Space.Page, vertical = Space.Tight),
    )
}

/** The room at a TV page's left and right edges: TVs may cut off the edge of the picture (overscan). */
val TV_EDGE = Space.Large

private const val FOCUS_GROW = 1.06f
private const val ROW_GROW = 1.02f
private val CHOICES_WIDTH = 520.dp
/** Buttons sit close together: they grow less than covers, so a grown one stays clear of the next. */
private const val BUTTON_GROW = 1.04f
private val FOCUS_RING = 3.dp
/** Six covers across a TV (960 dp wide less its edges), in Home's rows and the Library. */
val TILE_WIDTH = 136.dp
/** The Library's covers across. */
const val GRID_COLUMNS = 6
private val TILE_CORNER = 10.dp
private val CONTROL = 56.dp
private val BIG_CONTROL = 76.dp
private val CONTROL_ICON = 28.dp
private val BIG_CONTROL_ICON = 40.dp
