package app.siansa.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.playback.Cast
import app.siansa.playback.dlna.Dlna
import kotlin.math.roundToInt

/*
 * Home's menu (the button at the top right of Home): Play on…, Sources and Arrange Home, in a drop-down under the
 * button. A tap opens it; pressing the button and sliding down picks a row as the finger lets go (a glide), with a
 * light tick as the finger passes from row to row.
 */

/** The rows of Home's menu, top to bottom. */
enum class HomeMenuItem { PLAY_ON, SOURCES, ARRANGE }

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

/** Ends casting from Home's menu (its Stop), back to this phone, as "This phone" in Play on does. */
internal fun stopCasting() {
    val status = Cast.status.value
    if (status.device != null || status.connecting) Cast.disconnect()
    Dlna.select(null)
}

/**
 * Home's menu button: the three lines, lit while the source filter hides something, with a small dot while
 * something is cast. A tap opens the menu; a press slid down glides through it ([HomeMenuState]). Once a press
 * starts here, the slide belongs to the menu, so Home doesn't scroll.
 */
@Composable
fun HomeMenuButton(state: HomeMenuState, casting: Boolean, filterActive: Boolean, onPick: (HomeMenuItem) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val pick by rememberUpdatedState(onPick)
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val description = "Home menu: play on a TV or speaker, sources, arrange Home" + if (casting) ". Something is playing on a TV or speaker" else ""
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (state.open) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent)
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
        Icon(Icons.Rounded.FilterList, null, tint = if (filterActive) Accent else MaterialTheme.colorScheme.onSurface)
        if (casting) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-11).dp, y = 11.dp) // spacing: exact (the dot on the icon's corner)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Accent)
                    .testTag("homeMenuCastDot"),
            )
        }
    }
}

/**
 * The open menu over Home: a dim layer (a tap on it closes the menu) and the drop-down under the button. Back
 * closes it too.
 */
@Composable
fun HomeMenuOverlay(state: HomeMenuState, info: HomeMenuInfo, onPick: (HomeMenuItem) -> Unit, onStop: () -> Unit) {
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
        val width = with(density) { MENU_WIDTH.toPx() }
        val edge = with(density) { Space.Page.toPx() }
        val gap = with(density) { Space.Tight.toPx() }
        AnimatedVisibility(
            state.open && anchor != null,
            // Only a fade: a growing menu would report its rows smaller than they are to the glide.
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(100)),
            modifier = Modifier.offset {
                val a = anchor ?: return@offset IntOffset.Zero
                IntOffset((a.right - width - origin.x).coerceAtLeast(edge).roundToInt(), (a.bottom + gap - origin.y).roundToInt())
            },
        ) {
            Column {
                HomeMenuPanel(
                    info,
                    highlighted = state.highlighted,
                    onPick = { item -> state.close(); onPick(item) },
                    onStop = onStop,
                    onRowPlaced = { item, b -> state.rows[item] = b },
                )
                if (state.gliding && state.highlighted != null) {
                    Text(
                        "Let go to open",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Space.Medium)
                            .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = Space.Medium, vertical = Space.Tight),
                    )
                }
            }
        }
    }
}

/** The drop-down's width, as in the design. */
private val MENU_WIDTH = 272.dp

/**
 * The drop-down itself: Play on… (a little taller, the main action), a line, then Sources and Arrange Home. While
 * something is cast, Play on… says where in the accent colour, with Stop at its end.
 */
@Composable
internal fun HomeMenuPanel(
    info: HomeMenuInfo,
    highlighted: HomeMenuItem?,
    onPick: (HomeMenuItem) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    onRowPlaced: (HomeMenuItem, MenuBounds) -> Unit = { _, _ -> },
) {
    Surface(
        modifier.width(MENU_WIDTH).testTag("homeMenuPanel"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(Space.Small)) {
            val playOn = info.playOn
            HomeMenuRow(
                HomeMenuItem.PLAY_ON,
                if (playOn.active) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                playOn.title, playOn.subtitle,
                highlighted = highlighted == HomeMenuItem.PLAY_ON,
                lit = playOn.active,
                tall = true,
                onPick = onPick, onRowPlaced = onRowPlaced,
                onStop = onStop.takeIf { playOn.active },
            )
            HorizontalDivider(Modifier.padding(horizontal = Space.Medium, vertical = Space.Tight), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            HomeMenuRow(HomeMenuItem.SOURCES, Icons.Rounded.LibraryMusic, "Sources", info.sources, highlighted == HomeMenuItem.SOURCES, onPick = onPick, onRowPlaced = onRowPlaced)
            HomeMenuRow(HomeMenuItem.ARRANGE, Icons.Rounded.Reorder, "Arrange Home", "Order and looks", highlighted == HomeMenuItem.ARRANGE, onPick = onPick, onRowPlaced = onRowPlaced)
        }
    }
}

@Composable
private fun HomeMenuRow(
    item: HomeMenuItem,
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    lit: Boolean = false,
    tall: Boolean = false,
    onPick: (HomeMenuItem) -> Unit,
    onRowPlaced: (HomeMenuItem, MenuBounds) -> Unit,
    /** Stop at the row's end instead of the arrow (Play on… while casting). */
    onStop: (() -> Unit)? = null,
) {
    val accent = highlighted || lit
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlighted) Accent.copy(alpha = 0.16f) else Color.Transparent)
            .onGloballyPositioned { c -> c.boundsInRoot().let { onRowPlaced(item, MenuBounds(it.left, it.top, it.right, it.bottom)) } }
            .clickable(role = Role.Button) { onPick(item) }
            .padding(start = Space.Medium, end = if (onStop != null) Space.Tight else Space.Medium, top = if (tall) Space.Page else Space.Medium, bottom = if (tall) Space.Page else Space.Medium)
            .testTag("homeMenu-${item.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (accent) Accent else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(Space.Page))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (accent) Accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onStop != null) {
            TextButton(
                onClick = onStop,
                contentPadding = PaddingValues(horizontal = Space.Medium),
                modifier = Modifier.semantics { contentDescription = "Stop playing on the TV or speaker" }.testTag("homeMenuStop"),
            ) { Text("Stop", color = Accent, fontWeight = FontWeight.SemiBold) }
        } else {
            Spacer(Modifier.width(Space.Small))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
