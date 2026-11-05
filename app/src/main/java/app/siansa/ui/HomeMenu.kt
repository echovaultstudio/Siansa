package app.siansa.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.siansa.data.HomeMenuItem
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.heightIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.playback.Cast
import app.siansa.playback.dlna.Dlna
import kotlin.math.roundToInt

/*
 * Home's menu (the round button at the end of the notices, at the top of Home): Roll the dice, Play on…, Sources and
 * Arrange Home ([HomeMenuItem], in the order set in Settings › Style › Home Menu), as a column of bubbles under the
 * button. A tap opens it; pressing the button and sliding down picks a row as the finger lets go (a glide), with a
 * light tick as the finger passes from row to row.
 */

/** A row's place on the screen, in the root's pixels. */
data class MenuBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

/** The row under the finger at ([x], [y]) during a glide, or null between rows and outside the menu. */
internal fun <K> menuRowAt(x: Float, y: Float, rows: Map<K, MenuBounds>): K? =
    rows.entries.firstOrNull { it.value.contains(x, y) }?.key

/** Whether a press has moved far enough ([slop]) from where it went down to count as a glide, not a tap. */
internal fun glideMoved(dx: Float, dy: Float, slop: Float): Boolean = dx * dx + dy * dy > slop * slop

/** What letting go of the menu button does. */
internal enum class GlideEnd { STAY_OPEN, PICK, CLOSE }

/**
 * Letting go: a plain tap ([moved] false) leaves the menu open for its rows to be tapped; after a glide, the row under
 * the finger ([row]) opens, and anywhere else closes the menu.
 */
internal fun glideEnd(moved: Boolean, row: Any?): GlideEnd = when {
    !moved -> GlideEnd.STAY_OPEN
    row != null -> GlideEnd.PICK
    else -> GlideEnd.CLOSE
}

/** Play on…'s row: what it says, whether it's lit (something cast or connecting) and whether it offers Stop. */
data class PlayOnLine(val title: String, val subtitle: String, val active: Boolean)

/** Play on…'s row for the TV or speaker in use (Chromecast or DLNA), if any. */
internal fun playOnLine(castDevice: String?, castConnecting: Boolean, dlnaName: String?, dlnaConnecting: Boolean): PlayOnLine = when {
    dlnaName != null -> PlayOnLine("Playing on $dlnaName", if (dlnaConnecting) "Connecting…" else "DLNA", true)
    dlnaConnecting -> PlayOnLine("Connecting…", "DLNA", true)
    castDevice != null -> PlayOnLine("Playing on $castDevice", if (castConnecting) "Connecting…" else "Chromecast", true)
    castConnecting -> PlayOnLine("Connecting…", "Chromecast", true)
    else -> PlayOnLine("Play on…", "TV or speaker", false)
}

/**
 * Sources' line in the menu: what the source filter shows ("All libraries", "Downloaded only", "2 of 3 libraries",
 * "2 users"). [shownUsers] counts the user in use too; [chosenLibraries] empty means every library.
 */
internal fun sourcesSummary(downloadedOnly: Boolean, shownUsers: Int, chosenLibraries: Set<String>, allLibraries: Set<String>): String {
    val chosen = chosenLibraries.intersect(allLibraries)
    val parts = listOfNotNull(
        "Downloaded only".takeIf { downloadedOnly },
        "$shownUsers users".takeIf { shownUsers > 1 },
        "${chosen.size} of ${allLibraries.size} libraries".takeIf { allLibraries.size > 1 && chosen.isNotEmpty() && chosen.size < allLibraries.size },
    )
    return parts.joinToString(" · ").ifEmpty { "All libraries" }
}

/** Where Home's menu is: open or not, gliding, the row lit under the finger, and where the button and rows are. */
@Stable
class HomeMenuState {
    var open by mutableStateOf(false)
    /** A finger is on the button and sliding: rows light up under it, and letting go picks one. */
    var gliding by mutableStateOf(false)
    var highlighted by mutableStateOf<HomeMenuItem?>(null)
    /** The button's place in the root, which the menu hangs under. */
    var anchor by mutableStateOf<Rect?>(null)
    internal val rows = mutableStateMapOf<HomeMenuItem, MenuBounds>()

    fun show(glide: Boolean) {
        rows.clear()
        highlighted = null
        gliding = glide
        open = true
    }

    fun close() {
        open = false
        gliding = false
        highlighted = null
    }
}

@Composable
fun rememberHomeMenuState() = remember { HomeMenuState() }

/** What Home's menu shows: Play on…'s line, Sources' line and whether the source filter hides something. */
data class HomeMenuInfo(val playOn: PlayOnLine, val sources: String, val filterActive: Boolean)

/** [HomeMenuInfo] from the cast and source filter as they are now. */
@Composable
fun rememberHomeMenuInfo(): HomeMenuInfo {
    val graph = LocalGraph.current
    val context = LocalContext.current
    LaunchedEffect(Unit) { Cast.whenReady(context) { } }
    val status by Cast.status.collectAsStateWithLifecycle()
    val dlna by Dlna.selected.collectAsStateWithLifecycle()
    val dlnaConnecting by Dlna.connecting.collectAsStateWithLifecycle()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val shown by graph.guests.shown.collectAsStateWithLifecycle()
    val version by graph.library.version.collectAsStateWithLifecycle()
    val libraries by produceState(emptySet<String>(), version) { value = graph.library.folderNames().keys }
    return HomeMenuInfo(
        playOn = playOnLine(status.device, status.connecting, dlna?.name, dlnaConnecting),
        sources = sourcesSummary(settings.downloadedOnly, shown.size, settings.syncFolders, libraries),
        filterActive = sourceFilterActive(settings.downloadedOnly, shown.size),
    )
}

/**
 * Home's menu button: a circle the notice chips' height ([size]), so it sits in line with them, its three lines always
 * in the accent colour. It has a thin ring in the accent colour while the source filter hides something, a small dot while
 * something is cast, and shows the dice tumbling while a roll finds a song. A tap opens the menu; a press slid down
 * glides through it ([HomeMenuState]). Once a press starts here, the slide belongs to the menu, so Home doesn't scroll.
 */
@Composable
fun HomeMenuButton(
    state: HomeMenuState,
    casting: Boolean,
    filterActive: Boolean,
    onPick: (HomeMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = MENU_CIRCLE,
    rolling: Boolean = false,
    rolls: Int = 0,
) {
    val haptics = LocalHapticFeedback.current
    val pick by rememberUpdatedState(onPick)
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val description = when {
        rolling -> "Rolling the dice…"
        else -> "Home menu: roll the dice, play on a TV or speaker, sources, arrange Home" +
            (if (casting) ". Something is playing on a TV or speaker" else "") +
            (if (filterActive) ". The source filter hides some music" else "")
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (state.open) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh)
            // A ring, not a fill: the lines stay the accent colour and must show on it.
            .then(if (filterActive) Modifier.border(1.5.dp, Accent, CircleShape) else Modifier)
            .onGloballyPositioned { coords = it; state.anchor = it.boundsInRoot() }
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick { state.show(glide = false); true }
            }
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    state.show(glide = true)
                    val slop = viewConfiguration.touchSlop
                    var moved = false
                    var row: HomeMenuItem? = null
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            val d = change.position - down.position
                            if (!moved && glideMoved(d.x, d.y, slop)) moved = true
                            if (!moved) continue
                            val at = coords?.takeIf { it.isAttached }?.localToRoot(change.position) ?: continue
                            val now = menuRowAt(at.x, at.y, state.rows)
                            if (now != row) {
                                row = now
                                state.highlighted = now
                                if (now != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    } finally {
                        state.gliding = false
                        state.highlighted = null
                    }
                    when (glideEnd(moved, row)) {
                        GlideEnd.STAY_OPEN -> Unit
                        GlideEnd.CLOSE -> state.close()
                        GlideEnd.PICK -> {
                            state.close()
                            row?.let(pick)
                        }
                    }
                }
            }
            .testTag("homeMenu"),
        contentAlignment = Alignment.Center,
    ) {
        if (rolling) RollingDie(rolls, true, Accent, Modifier.size(MENU_ICON))
        else Icon(Icons.Rounded.FilterList, null, Modifier.size(MENU_ICON), tint = Accent)
        if (casting) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp) // spacing: exact (the dot on the circle's corner)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Accent)
                    .testTag("homeMenuCastDot"),
            )
        }
    }
}

/**
 * The open menu over Home: a dim layer (a tap on it closes the menu) and the column of bubbles under the button, the
 * button's size, each bubble's centre on the button's centre line. Back closes it too.
 */
@Composable
fun HomeMenuOverlay(state: HomeMenuState, info: HomeMenuInfo, order: List<HomeMenuItem>, onPick: (HomeMenuItem) -> Unit) {
    androidx.activity.compose.BackHandler(enabled = state.open) { state.close() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        AnimatedVisibility(state.open, enter = fadeIn(tween(120)), exit = fadeOut(tween(120))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close the menu") { state.close() },
            )
        }
        val anchor = state.anchor
        val gap = with(density) { Space.Small.toPx() }
        val edge = with(density) { Space.Page.toPx() }
        // The bubbles are the button's size, so the menu's right edge on the button's lines their centres up.
        val bubble = anchor?.let { with(density) { it.height.toDp() } } ?: MENU_CIRCLE
        // The room between rows counts half for the row on each side, so a glide never falls between two.
        val reach = with(density) { Space.Tight.toPx() } / 2
        AnimatedVisibility(
            state.open && anchor != null,
            // Only a fade: a growing menu would report its rows smaller than they are to the glide.
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(100)),
            modifier = Modifier.layout { measurable, constraints ->
                // No wider than the room left of the button's right edge, keeping the page margin on the left.
                val room = anchor?.let { (it.right - origin.x - edge).roundToInt() }?.coerceIn(0, constraints.maxWidth) ?: constraints.maxWidth
                val menu = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = room))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val a = anchor ?: return@layout
                    menu.place((a.right - origin.x).roundToInt() - menu.width, (a.bottom + gap - origin.y).roundToInt())
                }
            },
        ) {
            Column(horizontalAlignment = Alignment.End) {
                HomeMenuPanel(
                    info,
                    highlighted = state.highlighted,
                    onPick = { item -> state.close(); onPick(item) },
                    order = order,
                    bubble = bubble,
                    onRowPlaced = { item, b -> state.rows[item] = MenuBounds(b.left, b.top - reach, b.right, b.bottom + reach) },
                )
                if (state.gliding && state.highlighted != null) {
                    Text(
                        "Let go to open",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(top = Space.Medium)
                            .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = Space.Medium, vertical = Space.Tight),
                    )
                }
            }
        }
    }
}

/** The button's size when nothing measured it: the notice chips' height at the normal text size. */
internal val MENU_CIRCLE = 36.dp

/** A row of the menu: never shorter than this, so each is easy to hit even with a small bubble. */
internal val MENU_ROW = 48.dp

/** The icon in the button and in each bubble. */
private val MENU_ICON = 20.dp

/** The longest a bubble's label gets before it ends in "…". */
private val MENU_LABEL_MAX = 224.dp

/**
 * The menu itself: a column of round bubbles in [order], each with its name in a label to its left. Every bubble is
 * [bubble] wide (the button's size) and right-aligned, so their centres are on one line; every row is the same
 * height, so they're evenly spaced; a lit bubble only changes colour. Each opens its usual menu (the TVs and
 * speakers, the source filter, Arrange) or rolls the dice. Play on… is in the accent colour while something is cast,
 * and Sources while the source filter hides something.
 */
@Composable
internal fun HomeMenuPanel(
    info: HomeMenuInfo,
    highlighted: HomeMenuItem?,
    onPick: (HomeMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    order: List<HomeMenuItem> = HomeMenuItem.default,
    bubble: Dp = MENU_CIRCLE,
    onRowPlaced: (HomeMenuItem, MenuBounds) -> Unit = { _, _ -> },
) {
    Column(
        modifier.testTag("homeMenuPanel"),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Space.Tight),
    ) {
        val playOn = info.playOn
        for (item in order) {
            when (item) {
                HomeMenuItem.DICE -> HomeMenuBubble(
                    item, Icons.Rounded.Casino, "Roll the dice: play a song you've never played",
                    highlighted = highlighted == item, bubble = bubble, onPick = onPick, onRowPlaced = onRowPlaced,
                )
                HomeMenuItem.PLAY_ON -> HomeMenuBubble(
                    item, if (playOn.active) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                    "Play on… ${playOn.title.takeIf { playOn.active } ?: ""}".trim(),
                    highlighted = highlighted == item, lit = playOn.active, bubble = bubble, onPick = onPick, onRowPlaced = onRowPlaced,
                )
                HomeMenuItem.SOURCES -> HomeMenuBubble(
                    item, Icons.Rounded.LibraryMusic, "Sources: ${info.sources}",
                    highlighted = highlighted == item, lit = info.filterActive, bubble = bubble, onPick = onPick, onRowPlaced = onRowPlaced,
                )
                HomeMenuItem.ARRANGE -> HomeMenuBubble(
                    item, Icons.Rounded.Reorder, "Arrange Home",
                    highlighted = highlighted == item, bubble = bubble, onPick = onPick, onRowPlaced = onRowPlaced,
                )
            }
        }
    }
}

@Composable
private fun HomeMenuBubble(
    item: HomeMenuItem,
    icon: ImageVector,
    /** What TalkBack reads: the name, and what's in use (where it's cast, what the source filter shows). */
    spoken: String,
    highlighted: Boolean,
    bubble: Dp,
    lit: Boolean = false,
    onPick: (HomeMenuItem) -> Unit,
    onRowPlaced: (HomeMenuItem, MenuBounds) -> Unit,
) {
    val tag = item.id
    Row(
        Modifier
            .onGloballyPositioned { c -> c.boundsInRoot().let { onRowPlaced(item, MenuBounds(it.left, it.top, it.right, it.bottom)) } }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onPick(item) }
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            // Every row the same height, so the bubbles stay evenly spaced at any font size.
            .height(maxOf(MENU_ROW, bubble))
            .testTag("homeMenu-$tag"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp,
            // Weighted, so the Row measures the bubble first: a long name ends in "…", the bubble never shrinks or
            // leaves the line. The label is the bubble's height, like the chips.
            modifier = Modifier.weight(1f, fill = false).widthIn(max = MENU_LABEL_MAX).heightIn(min = bubble).testTag("homeMenuLabel-$tag"),
        ) {
            Box(Modifier.padding(horizontal = Space.Medium), contentAlignment = Alignment.Center) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = PILL_FONT,
                    fontWeight = FontWeight.SemiBold,
                    color = if (highlighted || lit) Accent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = if (highlighted) Accent else MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 6.dp,
            modifier = Modifier.size(bubble).testTag("homeMenuBubble-$tag"),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(MENU_ICON), tint = when {
                    highlighted -> OnAccent
                    lit -> Accent
                    else -> MaterialTheme.colorScheme.onSurface
                })
            }
        }
    }
}
