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
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
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
    /** The card of Home the menu lies on ([homeMenuCard]): the first tile under the top row, when it's on screen. */
    var card by mutableStateOf<Rect?>(null)
    var cardCorner by mutableStateOf(QUICK_TILE_CORNER)
    /** Home's page as last drawn, for the glass to blur what's under the menu; null until Home draws it. */
    internal var backdrop: GraphicsLayer? = null
    /** Where the card is in the overlay, so the glass reads the right part of the page. */
    internal var cardDrawOrigin = Offset.Zero
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
 * Home's page, kept as a picture while the menu is open, so the menu's glass can show what's under it blurred
 * ([HomeMenuOverlay]). Costs nothing while the menu is closed: the page draws as usual.
 */
@Composable
fun Modifier.homeMenuBackdrop(state: HomeMenuState): Modifier {
    val layer = rememberGraphicsLayer()
    state.backdrop = layer
    return drawWithContent {
        if (state.open) {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        } else drawContent()
    }
}

/**
 * The card of Home the menu lies on when it opens (the owner, 12 Nov): the second shortcut tile, so the menu has the
 * same left and right edges, top and corners as the tiles under it, each row on a tile. Only the first section under
 * the top row says so; when it's scrolled away the menu hangs under the button at the same width instead.
 */
@Composable
fun Modifier.homeMenuCard(state: HomeMenuState, corner: Dp): Modifier {
    DisposableEffect(state) { onDispose { state.card = null } }
    return onGloballyPositioned { state.card = it.boundsInRoot(); state.cardCorner = corner }
}

/**
 * Where the menu goes, in the root's pixels (the owner, 12 Nov): on [card] when there is one under the [button] (the
 * shortcut tile at the right), else hanging under the button at the right [edge] of the page ([pageLeft] to
 * [pageRight]), [gap] below it, as wide as the right column of a two-column grid (the page less the edges and
 * [between] the columns, halved). [height] is the rows'.
 */
internal fun menuCardPlace(button: Rect, card: Rect?, pageLeft: Float, pageRight: Float, edge: Float, gap: Float, between: Float, height: Float): Rect {
    if (card != null && card.top >= button.bottom && card.width > 0f) return Rect(card.left, card.top, card.right, card.top + height)
    val width = (pageRight - pageLeft - 2 * edge - between) / 2
    val right = pageRight - edge
    return Rect(right - width, button.bottom + gap, right, button.bottom + gap + height)
}

/** How far the glass blurs what's under it, and how much more colour it gives it: frosted, not smeared. */
internal val GLASS_BLUR = 28.dp
internal const val GLASS_SATURATION = 1.6f

/** The glass's own darkness over the blur (Android 12 and later), and without a blur (older phones), where it must carry the words alone. */
internal fun glassAlpha(blurs: Boolean): Float = if (blurs) 0.55f else 0.92f

/**
 * The open menu over Home: a clear layer that closes it on a tap (nothing dims or blurs the page itself), and the menu
 * as one card of frosted glass on Home's first tiles ([menuCardPlace]): what's under it shows through, blurred and a
 * little richer in colour, under a half-dark tint with a light hairline. Back closes it too.
 */
@Composable
fun HomeMenuOverlay(state: HomeMenuState, info: HomeMenuInfo, order: List<HomeMenuItem>, onPick: (HomeMenuItem) -> Unit) {
    androidx.activity.compose.BackHandler(enabled = state.open) { state.close() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val blurs = android.os.Build.VERSION.SDK_INT >= 31
    val glass = rememberGraphicsLayer()
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        if (state.open) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close the menu") { state.close() },
            )
        }
        val anchor = state.anchor
        val rowsHeight = with(density) { (MENU_ROW * order.size + MENU_ROW_GAP * (order.size - 1)).toPx() }
        val corner = if (state.card != null) state.cardCorner else QUICK_TILE_CORNER
        // The room between rows counts half for the row on each side, so a glide never falls between two.
        val reach = with(density) { MENU_ROW_GAP.toPx() } / 2
        val tint = if (blurs) Color(0xFF2C2C2C).copy(alpha = glassAlpha(true)) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = glassAlpha(false))
        AnimatedVisibility(
            state.open && anchor != null,
            // Only a fade: a growing menu would report its rows smaller than they are to the glide.
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(100)),
            modifier = Modifier.layout { measurable, constraints ->
                val a = anchor
                // The page's width is the overlay's: the fallback place is worked out here, where it's known.
                val at = if (a == null) null else menuCardPlace(
                    a, state.card, pageLeft = origin.x, pageRight = origin.x + constraints.maxWidth, edge = Space.Page.toPx(),
                    gap = Space.Medium.toPx(), between = Space.Small.toPx(), height = rowsHeight,
                )
                val width = at?.width?.roundToInt()?.coerceIn(0, constraints.maxWidth) ?: 0
                val menu = measurable.measure(constraints.copy(minWidth = width, maxWidth = width, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    if (at != null) {
                        val x = (at.left - origin.x).roundToInt()
                        val y = (at.top - origin.y).roundToInt()
                        state.cardDrawOrigin = Offset(x.toFloat(), y.toFloat())
                        menu.place(x, y)
                    }
                }
            },
        ) {
            val shape = RoundedCornerShape(corner)
            Column {
                Column(
                    Modifier
                        .clip(shape)
                        .drawWithContent {
                            // The glass: Home as drawn under this card, blurred and a touch more colourful, then the tint.
                            val page = state.backdrop
                            if (blurs && page != null) {
                                val pad = (GLASS_BLUR.toPx() * 2).roundToInt()
                                glass.renderEffect = BlurEffect(GLASS_BLUR.toPx(), GLASS_BLUR.toPx(), TileMode.Clamp)
                                glass.colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(GLASS_SATURATION) })
                                val cardAt = state.cardDrawOrigin
                                glass.record(IntSize(size.width.roundToInt() + 2 * pad, size.height.roundToInt() + 2 * pad)) {
                                    translate(pad - cardAt.x, pad - cardAt.y) { drawLayer(page) }
                                }
                                translate(-pad.toFloat(), -pad.toFloat()) { drawLayer(glass) }
                            }
                            drawRect(tint)
                            drawContent()
                        }
                        .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
                ) {
                    HomeMenuPanel(
                        info,
                        highlighted = state.highlighted,
                        onPick = { item -> state.close(); onPick(item) },
                        order = order,
                        onRowPlaced = { item, b -> state.rows[item] = MenuBounds(b.left, b.top - reach, b.right, b.bottom + reach) },
                    )
                }
                if (state.gliding && state.highlighted != null) {
                    Text(
                        "Let go to open",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.End).padding(top = Space.Medium)
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

/** A row of the menu: a shortcut tile's height, so each row lies on one tile; and the room between rows, the tiles'. */
internal val MENU_ROW = QUICK_TILE_HEIGHT
internal val MENU_ROW_GAP = Space.Small

/** The icon in the button and in each row. */
private val MENU_ICON = 20.dp

/**
 * The menu itself: the rows in [order], each an icon, its name and one line of what's in use under it (where it plays,
 * what the source filter shows), every row a tile's height. A lit row (under a gliding finger) is a shade lighter;
 * Play on…'s line is in the accent colour while something is cast, and Sources' while the source filter hides something.
 */
@Composable
internal fun HomeMenuPanel(
    info: HomeMenuInfo,
    highlighted: HomeMenuItem?,
    onPick: (HomeMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    order: List<HomeMenuItem> = HomeMenuItem.default,
    onRowPlaced: (HomeMenuItem, MenuBounds) -> Unit = { _, _ -> },
) {
    Column(modifier.testTag("homeMenuPanel"), verticalArrangement = Arrangement.spacedBy(MENU_ROW_GAP)) {
        val playOn = info.playOn
        for (item in order) {
            when (item) {
                HomeMenuItem.DICE -> HomeMenuRow(
                    item, Icons.Rounded.Casino, "Never played", "Roll the dice: play a song you've never played",
                    highlighted = highlighted == item, onPick = onPick, onRowPlaced = onRowPlaced,
                )
                HomeMenuItem.PLAY_ON -> HomeMenuRow(
                    item, if (playOn.active) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                    if (playOn.active) playOn.title else "This phone", "Play on… ${playOn.title.takeIf { playOn.active } ?: ""}".trim(),
                    highlighted = highlighted == item, lit = playOn.active, onPick = onPick, onRowPlaced = onRowPlaced,
                )
                HomeMenuItem.SOURCES -> HomeMenuRow(
                    item, Icons.Rounded.LibraryMusic, info.sources, "Sources: ${info.sources}",
                    highlighted = highlighted == item, lit = info.filterActive, onPick = onPick, onRowPlaced = onRowPlaced,
                )
                HomeMenuItem.ARRANGE -> HomeMenuRow(
                    item, Icons.Rounded.Reorder, null, "Arrange Home",
                    highlighted = highlighted == item, onPick = onPick, onRowPlaced = onRowPlaced,
                )
            }
        }
    }
}

@Composable
private fun HomeMenuRow(
    item: HomeMenuItem,
    icon: ImageVector,
    /** The line under the name: what's in use. */
    line: String?,
    /** What TalkBack reads: the name, and what's in use (where it's cast, what the source filter shows). */
    spoken: String,
    highlighted: Boolean,
    lit: Boolean = false,
    onPick: (HomeMenuItem) -> Unit,
    onRowPlaced: (HomeMenuItem, MenuBounds) -> Unit,
) {
    val tag = item.id
    Row(
        Modifier
            .fillMaxWidth()
            .height(MENU_ROW)
            .background(if (highlighted) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .onGloballyPositioned { c -> c.boundsInRoot().let { onRowPlaced(item, MenuBounds(it.left, it.top, it.right, it.bottom)) } }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onPick(item) }
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(horizontal = Space.Medium)
            .testTag("homeMenu-$tag"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        Icon(icon, null, Modifier.size(MENU_ICON), tint = if (lit) Accent else Color.White)
        Column(Modifier.weight(1f)) {
            Text(
                item.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("homeMenuLabel-$tag"),
            )
            if (line != null) Text(
                line,
                style = MaterialTheme.typography.labelSmall,
                color = if (lit) Accent else Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("homeMenuLine-$tag"),
            )
        }
    }
}
