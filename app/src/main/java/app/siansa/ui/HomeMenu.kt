package app.siansa.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.rounded.Close
import app.siansa.data.HomeMenuItem
import androidx.compose.material.icons.rounded.Casino
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.playback.Cast
import app.siansa.playback.dlna.Dlna

/*
 * Home's menu (the round button at the end of the notices, at the top of Home): Roll the dice, Play on…, Sources and
 * Arrange Home ([HomeMenuItem], in the order set in Settings › Home page › Home menu). It opens in the top row itself (the
 * owner, 12 Nov): the notice chips fade and blur away as one pill of glass takes their place, the chips' exact height,
 * with the four as circles along it, as the DJ mix bar's places; nothing on the page is covered. A tap opens it;
 * pressing the button and sliding along the pill picks a place as the finger lets go (a glide), with a light tick as
 * the finger passes from one to the next.
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

/** Where Home's menu is: open or not, gliding, the place lit under the finger, and where the button and places are. */
@Stable
class HomeMenuState {
    var open by mutableStateOf(false)
    /** A finger is on the button and sliding: places light up under it, and letting go picks one. */
    var gliding by mutableStateOf(false)
    var highlighted by mutableStateOf<HomeMenuItem?>(null)
    /** The button's place in the root. */
    var anchor by mutableStateOf<Rect?>(null)
    internal val rows = mutableStateMapOf<HomeMenuItem, MenuBounds>()

    fun show(glide: Boolean) {
        if (!open) rows.clear()  // open already: the places are where they are
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
                onClick { if (state.open) state.close() else state.show(glide = false); true }
            }
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    // Open already (the cross): a tap closes it; a glide from the cross still picks a place.
                    val wasOpen = state.open
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
                        GlideEnd.STAY_OPEN -> if (wasOpen && !moved) state.close()
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
        // Open, the lines become a cross: the same button closes the menu.
        else Icon(if (state.open) Icons.Rounded.Close else Icons.Rounded.FilterList, null, Modifier.size(MENU_ICON), tint = Accent)
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
 * How far open the menu is, 0 to 1 (a touch past 1 on the way, and back): one soft spring that the top row's every
 * part rides (the chips fading and blurring, the pill's tint and edge, each place growing in), so it's one motion.
 */
@Composable
fun rememberHomeMenuOpen(state: HomeMenuState): Float {
    val open by animateFloatAsState(if (state.open) 1f else 0f, spring(dampingRatio = MENU_SPRING_DAMPING, stiffness = MENU_SPRING_STIFFNESS), label = "homeMenuOpen")
    return open
}

/**
 * Over Home while the menu is open: a clear layer that closes it on a tap (nothing dims or blurs the page), and Back
 * closes it too. The layer starts under the top row, where the menu itself is ([HomeMenuBar]), so the row's places
 * and button stay tappable.
 */
@Composable
fun HomeMenuOverlay(state: HomeMenuState) {
    androidx.activity.compose.BackHandler(enabled = state.open) { state.close() }
    if (state.open) {
        var top by remember { mutableStateOf(0f) }
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { top = it.positionInRoot().y }
                .offset { IntOffset(0, ((state.anchor?.bottom ?: 0f) - top).roundToInt().coerceAtLeast(0)) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close the menu") { state.close() }
                .testTag("homeMenuClose"),
        )
    }
}

/** The button's size when nothing measured it: the notice chips' height at the normal text size. */
internal val MENU_CIRCLE = 36.dp

/** The icon in the button. */
private val MENU_ICON = 20.dp

/** The pill's own colour, before its tint. */
private val MENU_PILL_COLOUR = Color(0xFF2C2C2C)

/**
 * The menu in the top row: one pill of glass the notice chips' height and width (the row gives both), the places in
 * [order] along it as circles, as the DJ mix bar's. [open] (0 to 1, [rememberHomeMenuOpen]) brings it in: the tint
 * and hairline come up with it and the places grow in one after another. A place lit under a gliding finger grows and
 * fills with the accent; Play on…'s has a ring in the accent while something is cast, and Sources' while the source
 * filter hides something. Icons only, so the row never grows taller than the chips; the names are read out.
 */
@Composable
fun HomeMenuBar(
    state: HomeMenuState,
    info: HomeMenuInfo,
    order: List<HomeMenuItem>,
    open: Float,
    onPick: (HomeMenuItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    val tint = MENU_PILL_COLOUR.copy(alpha = pillTint(open))
    val edge = Color.White.copy(alpha = MENU_PILL_EDGE * open.coerceIn(0f, 1f))
    Row(
        modifier
            .fillMaxSize()
            .clip(shape)
            .background(tint, shape)
            .border(1.dp, edge, shape)
            // Taps between the places stay here: the chips underneath aren't pressed through the pill.
            .then(if (state.open) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {} else Modifier)
            .testTag("homeMenuPanel"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        order.forEachIndexed { index, item ->
            val (icon, spoken, lit) = when (item) {
                HomeMenuItem.DICE -> Triple(Icons.Rounded.Casino, "Roll the dice: play a song you've never played", false)
                HomeMenuItem.PLAY_ON -> Triple(
                    if (info.playOn.active) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                    "Play on… " + (if (info.playOn.active) info.playOn.title else "TV or speaker"), info.playOn.active,
                )
                HomeMenuItem.SOURCES -> Triple(Icons.Rounded.LibraryMusic, "Sources: ${info.sources}", info.filterActive)
                HomeMenuItem.ARRANGE -> Triple(Icons.Rounded.Reorder, "Arrange Home", false)
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onGloballyPositioned { c -> c.boundsInRoot().let { state.rows[item] = MenuBounds(it.left, it.top, it.right, it.bottom) } }
                    .graphicsLayer {
                        alpha = placeIn(open, index)
                        val scale = placeScale(open, index)
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                // The tag goes on the place itself (the node a tap and TalkBack see); its circle is "homeMenuCircle",
                // "homeMenuChosenCircle" lit.
                GlidePlace(
                    icon, spoken, chosen = state.highlighted == item, now = lit, tag = "homeMenu",
                    onTap = { state.close(); onPick(item) }, modifier = Modifier.fillMaxSize().testTag("homeMenu-${item.id}"),
                )
            }
        }
    }
}
