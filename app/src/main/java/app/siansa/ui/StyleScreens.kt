package app.siansa.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.data.MiniButton
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Casino
import app.siansa.data.HomeMenuItem
import app.siansa.data.NavTab
import app.siansa.data.moved
import app.siansa.log.AppLog
import kotlin.math.roundToInt
import app.siansa.data.shownInOrder
import app.siansa.data.arrangeOrder
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures

/** The phone's top bar (issue #681): see-through, black, or hidden for full screen. */
@Composable
internal fun TopBarChoice(selected: app.siansa.data.TopBarStyle, edge: androidx.compose.ui.unit.Dp = Space.Page, onPick: (app.siansa.data.TopBarStyle) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = edge).testTag("topBarChoice")) {
        Text("The clock and battery at the top of the screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.Small))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
            app.siansa.data.TopBarStyle.entries.forEach { style ->
                androidx.compose.material3.FilterChip(
                    selected = selected == style,
                    onClick = { if (style != selected) onPick(style) },
                    label = { Text(style.label) },
                    modifier = Modifier.testTag("topBar-${style.name}"),
                )
            }
        }
        Spacer(Modifier.height(Space.Small))
        Text(selected.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Settings › Now playing (the owner, 13 Nov; Now Playing Style, DJ Mix Bar and Mini Player before, issue #346): the
 * buttons under the controls, the play button's slides and grooves, the order of the DJ mix bar's places, and the
 * mini player's buttons.
 */
@Composable
fun NowPlayingStyleScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    Screen(title = "Now playing", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            FoldSection("Buttons", if (settings.nowPlayingLyricsButton) "Lyrics, device and queue" else "Lyrics card peeking in") {
                Row(
                    Modifier.fillMaxWidth().clickable { setLyricsButton(graph, !settings.nowPlayingLyricsButton) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Lyrics button", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (settings.nowPlayingLyricsButton) "Lyrics, where it plays and the queue under the controls"
                            else "Classic: where it plays and the queue, with the lyrics card peeking in below",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(Space.Medium))
                    Switch(
                        checked = settings.nowPlayingLyricsButton,
                        onCheckedChange = { setLyricsButton(graph, it) },
                        modifier = Modifier.testTag("lyricsButtonSwitch"),
                    )
                }
            }
            HorizontalDivider()
            FoldSection("Play button", "Slides ${if (settings.playSlides.on) "on" else "off"} · grooves ${if (settings.playSlides.grooves) "on" else "off"}") {
                PlaySlidesSettings(settings.playSlides, heading = false) { slides ->
                    AppLog.i("Style", "Play button slides: ${slides.format()}")
                    graph.settings.setPlaySlides(slides)
                }
            }
            HorizontalDivider()
            FoldSection("DJ mix bar", "${settings.djBarOrder.size} places") {
                Hint("Slide up from the play button and the seek bar opens into this bar: let go on a place to start a DJ mix, stop the one playing, or give it that shape. Hold a row and drag it: top to bottom is left to right on the bar.")
                ArrangeList(
                    order = settings.djBarOrder,
                    shown = settings.djBarOrder.toSet(),
                    label = { it.arrangeLabel },
                    icon = { it.icon(starting = true) },
                    key = { it.id },
                    canHide = { false },
                    canShowMore = false,
                    onChange = { places, _ ->
                        AppLog.i("Style", "DJ mix bar: ${DjBarSpot.format(places)}")
                        graph.settings.setDjBarOrder(places)
                    },
                    eyes = false,
                    edge = 0.dp,
                )
                if (settings.djBarOrder != DjBarSpot.default) TextButton(onClick = { graph.settings.setDjBarOrder(DjBarSpot.default) }) { Text("Reset the order") }
            }
            HorizontalDivider()
            FoldSection("Mini player", settings.miniButtons.joinToString(" · ") { it.label }.ifEmpty { "No buttons" }) {
                Hint("Hold a row and drag it, left to right on the bar. Tap the eye to show or hide it. Up to ${MiniButton.MAX} buttons.")
                ArrangeList(
                    order = arrangeOrder(settings.miniButtons, MiniButton.entries),
                    shown = settings.miniButtons.toSet(),
                    label = { it.label },
                    icon = { it.icon() },
                    key = { it.id },
                    canHide = { true },
                    canShowMore = settings.miniButtons.size < MiniButton.MAX,
                    onChange = { order, on ->
                        val buttons = shownInOrder(order, on)
                        AppLog.i("Style", "Mini player buttons: ${MiniButton.format(buttons).ifEmpty { "none" }}")
                        graph.settings.setMiniButtons(buttons)
                    },
                    edge = 0.dp,
                )
                if (settings.miniButtons != MiniButton.default) TextButton(onClick = { graph.settings.setMiniButtons(MiniButton.default) }) { Text("Reset") }
            }
        }
    }
}

/**
 * Play button slides (issue #919): whether a slide from now playing's play button does anything, what each way does,
 * and whether the button gets record grooves while a DJ mix plays.
 */
@Composable
internal fun PlaySlidesSettings(slides: PlaySlides, heading: Boolean = true, onChange: (PlaySlides) -> Unit) {
    Column {
        if (heading) Text("Play button slides", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        StyleSwitchRow("Slide from the play button", "Up, down, left or right starts a mix or finds songs", slides.on, "playSlidesSwitch") {
            onChange(slides.copy(on = it))
        }
        if (slides.on) {
            SlideWay.entries.forEach { way ->
                SlideWayRow(way, slides.of(way)) { onChange(slides.with(way, it)) }
            }
            Text(
                "A tap still plays and pauses, and pressing and holding still stops.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Space.Small),
            )
        }
        StyleSwitchRow("Record grooves", "The play button looks like a record while a DJ mix plays", slides.grooves, "playGroovesSwitch") {
            onChange(slides.copy(grooves = it))
        }
    }
}

@Composable
private fun StyleSwitchRow(title: String, hint: String, on: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = Space.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Space.Medium))
        Switch(checked = on, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}

/** One way to slide: its arrow and name, and what it does, picked from a menu. */
@Composable
private fun SlideWayRow(way: SlideWay, slide: PlaySlide, onPick: (PlaySlide) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = Space.Small).testTag("slideWay-${way.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Medium),
    ) {
        Icon(way.arrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Slide ${way.label}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            Row(
                Modifier.clip(RoundedCornerShape(percent = 50)).background(MaterialTheme.colorScheme.primary.copy(alpha = CHIP_TINT))
                    .padding(horizontal = Space.Medium, vertical = Space.Tight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.Small),
            ) {
                slide.icon?.let { Icon(it, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(CHIP_ICON)) }
                Text(slide.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                Icon(Icons.Rounded.ArrowDropDown, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(CHIP_ICON))
            }
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                PlaySlide.entries.forEach { choice ->
                    val icon = choice.icon
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(choice.label) },
                        leadingIcon = icon?.let { i -> @Composable { Icon(i, null) } },
                        onClick = {
                            open = false
                            if (choice != slide) onPick(choice)
                        },
                        modifier = Modifier.testTag("slideOption-${way.name.lowercase()}-${choice.name.lowercase()}"),
                    )
                }
            }
        }
    }
}

/** The arrow for a way to slide. */
private val SlideWay.arrow: ImageVector
    get() = when (this) {
        SlideWay.UP -> Icons.Rounded.ArrowUpward
        SlideWay.DOWN -> Icons.Rounded.ArrowDownward
        SlideWay.LEFT -> Icons.AutoMirrored.Rounded.ArrowBack
        SlideWay.RIGHT -> Icons.AutoMirrored.Rounded.ArrowForward
    }

private const val CHIP_TINT = 0.14f
private val CHIP_ICON = 18.dp

private fun setLyricsButton(graph: app.siansa.AppGraph, on: Boolean) {
    AppLog.i("Style", "Now playing: ${if (on) "lyrics button" else "classic"}")
    graph.settings.setNowPlayingLyricsButton(on)
}

/**
 * Nav Bar (issue #346, #372): the tabs as a list, in their order on the bar. Hold a row or grab its 6-dot handle to
 * move it; the eye shows or hides it, up to [NavTab.MAX]. Settings always stays, so these choices can be found.
 */
@Composable
fun NavBarStyleScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val shown = settings.navTabs
    fun save(tabs: List<NavTab>) {
        AppLog.i("Style", "Nav bar: ${NavTab.format(tabs)}")
        graph.settings.setNavTabs(tabs)
    }
    Screen(
        title = "Bottom bar",
        nav = nav,
        actions = { TextButton(onClick = { save(NavTab.default) }, enabled = shown != NavTab.default) { Text("Reset") } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            StyleHint("Hold a row and drag it, left to right on the bar. Tap the eye to show or hide it. Settings always stays.")
            ArrangeList(
                order = arrangeOrder(shown, NavTab.entries),
                shown = shown.toSet(),
                label = { it.label },
                icon = { it.icon() },
                key = { it.route },
                canHide = { it !in NavTab.required },
                canShowMore = shown.size < NavTab.MAX,
                onChange = { order, on -> save(shownInOrder(order, on)) },
            )
        }
    }
}

/** How many shortcuts Home shows, 2 to 8, two to a row (issue #934). */
@Composable
internal fun ShortcutCountRow(count: Int, edge: androidx.compose.ui.unit.Dp = Space.Page, onDone: (Int) -> Unit) {
    var value by remember(count) { androidx.compose.runtime.mutableFloatStateOf(count.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = edge)) {
        Text("Shortcuts", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Slider(
                value = value,
                onValueChange = { value = kotlin.math.round(it) },
                onValueChangeFinished = { onDone(value.toInt()) },
                valueRange = QUICK_MIN.toFloat()..QUICK_COUNT.toFloat(),
                steps = QUICK_COUNT - QUICK_MIN - 1,
                modifier = Modifier.weight(1f).testTag("homeShortcuts"),
            )
            Spacer(Modifier.width(Space.Medium))
            Text("${value.toInt()}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("homeShortcutsCount"))
        }
    }
}

internal fun HomeMenuItem.icon(): ImageVector = when (this) {
    HomeMenuItem.DICE -> Icons.Rounded.Casino
    HomeMenuItem.PLAY_ON -> Icons.Rounded.Cast
    HomeMenuItem.SOURCES -> Icons.Rounded.LibraryMusic
    HomeMenuItem.ARRANGE -> Icons.Rounded.Reorder
}

@Composable
private fun StyleHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.Page, end = Space.Page, bottom = Space.Medium),
    )
}

/**
 * A short list to put in order and show or hide, as Home's Arrange does (issue #372): hold a row, or grab its 6-dot
 * handle, and move it; the others make room. Hidden rows stay, dimmed, below the shown ones. [onChange] gets the
 * whole order and the shown set when a row is let go or its eye is tapped.
 */
@Composable
internal fun <T> ArrangeList(
    order: List<T>,
    shown: Set<T>,
    label: (T) -> String,
    icon: (T) -> ImageVector,
    key: (T) -> String,
    canHide: (T) -> Boolean,
    canShowMore: Boolean,
    onChange: (order: List<T>, shown: Set<T>) -> Unit,
    /** Whether rows have the eye at all: Home's menu only puts its options in order (issue #872). */
    eyes: Boolean = true,
    /** The rows' distance from the page's edges: the page margin on a page of its own, none inside a padded section. */
    edge: androidx.compose.ui.unit.Dp = Space.Page,
) {
    val haptics = LocalHapticFeedback.current
    val pitchPx = with(LocalDensity.current) { ARRANGE_PITCH.toPx() }
    val saved by rememberUpdatedState(order)
    val on by rememberUpdatedState(shown)
    val change by rememberUpdatedState(onChange)
    // One state for the list's whole life (issue #701). Each row's drag handler is set up once and keeps reading this
    // state; a state made again for each saved order left later drags moving an old copy, so rows stopped making room.
    var items by remember { mutableStateOf(order) }
    var dragging by remember { mutableStateOf<T?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    // The saved order (after a drop, an eye or Reset) shows once no row is held.
    LaunchedEffect(order, dragging) { if (dragging == null && items != order) items = order }

    fun start(item: T) {
        dragging = item
        offset = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun dragBy(dy: Float) {
        val item = dragging ?: return
        offset += dy
        val from = items.indexOf(item)
        val to = dragTarget(from, offset, pitchPx, items.size)
        if (to != from) {
            items = items.moved(from, to)
            offset -= (to - from) * pitchPx
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun end() {
        if (dragging == null) return
        dragging = null
        offset = 0f
        if (items != saved) change(items, on)
    }

    Column(Modifier.fillMaxWidth().testTag("arrangeList")) {
        items.forEach { item ->
          // Each row keeps its identity as it moves, so its drag isn't cancelled when it passes another row and
          // the row underneath takes its place (beta report 9b2142f0: rows got stuck half-way).
          androidx.compose.runtime.key(key(item)) {
            val lifted = item == dragging
            val isShown = item in shown
            val toggleable = if (isShown) canHide(item) else canShowMore
            // A row pushed aside by the one being moved glides to its new place instead of jumping.
            val index = items.indexOf(item)
            var lastIndex by remember { mutableIntStateOf(index) }
            val glide = remember { Animatable(0f) }
            LaunchedEffect(index) {
                val moved = lastIndex - index
                lastIndex = index
                if (moved != 0 && !lifted) {
                    glide.snapTo(moved * pitchPx)
                    glide.animateTo(0f, tween(150))
                }
            }
            Surface(
                color = if (lifted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .zIndex(if (lifted) 1f else 0f)
                    .graphicsLayer { translationY = if (lifted) offset else glide.value }
                    .padding(horizontal = edge, vertical = Space.Tight)
                    .then(if (lifted) Modifier.shadow(6.dp, RoundedCornerShape(12.dp)) else Modifier)
                    .fillMaxWidth()
                    .pointerInput(item) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { start(item) },
                            onDrag = { c, amount -> c.consume(); dragBy(amount.y) },
                            onDragEnd = { end() },
                            onDragCancel = { end() },
                        )
                    }
                    .testTag("arrangeRow"),
            ) {
                Row(Modifier.height(ARRANGE_ROW).padding(start = Space.Page), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon(item), null, tint = if (isShown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Text(
                        label(item),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isShown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(start = Space.Page).alpha(if (isShown) 1f else 0.6f),
                    )
                    if (eyes) IconButton(
                        enabled = toggleable,
                        onClick = { change(items, if (isShown) on - item else on + item) },
                    ) {
                        Icon(
                            if (isShown) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = if (isShown) "Hide ${label(item)}" else "Show ${label(item)}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (toggleable) 1f else 0.3f),
                        )
                    }
                    // The handle moves straight away, without the hold.
                    Icon(
                        Icons.Rounded.DragIndicator,
                        contentDescription = "Move ${label(item)}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .pointerInput(item) {
                                detectDragGestures(
                                    onDragStart = { start(item) },
                                    onDrag = { c, amount -> c.consume(); dragBy(amount.y) },
                                    onDragEnd = { end() },
                                    onDragCancel = { end() },
                                )
                            }
                            .padding(Space.Page)
                            .size(24.dp),
                    )
                }
            }
          }
        }
    }
}

/** An arrange row's height, and the distance from one row to the next (the row plus its gaps above and below). */
private val ARRANGE_ROW = 52.dp
private val ARRANGE_PITCH = ARRANGE_ROW + Space.Tight * 2

/** Where an item dragged [offset] px from slot [from] lands: the slot it's over by more than half, in range. */
internal fun dragTarget(from: Int, offset: Float, slotPx: Float, count: Int): Int {
    if (from < 0 || slotPx <= 0f || count <= 0) return from
    return (from + (offset / slotPx).roundToInt()).coerceIn(0, count - 1)
}

/** Each mini player button's slot, the same as the player's own 48 dp buttons. */
private val MINI_SLOT = 48.dp

internal fun MiniButton.icon(): ImageVector = when (this) {
    MiniButton.PLAY -> Icons.Rounded.PlayArrow
    MiniButton.CAST -> Icons.Rounded.Cast
    MiniButton.NEXT -> Icons.Rounded.SkipNext
    MiniButton.LYRICS -> Icons.Rounded.ChatBubble
    MiniButton.ADD -> Icons.Rounded.AddCircleOutline
    MiniButton.LIKE -> Icons.Rounded.FavoriteBorder
}
