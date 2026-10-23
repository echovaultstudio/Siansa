package app.musix.ui

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
import app.musix.data.MiniButton
import app.musix.data.NavTab
import app.musix.data.moved
import app.musix.log.AppLog
import kotlin.math.roundToInt
import app.musix.data.shownInOrder
import app.musix.data.arrangeOrder
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures

/**
 * Settings › Style (issue #346): how the app looks, each part on its own page. Theme is the Themes page as
 * before; Now Playing Style, Mini Player and Nav Bar are new.
 */
@Composable
fun StyleScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    Screen(title = "Style", nav = nav) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            HubRow(Icons.Rounded.Palette, "Theme", themeSummary(settings)) { nav.navigate("settings/themes") }
            HubRow(
                Icons.Rounded.PhoneAndroid,
                "Now Playing Style",
                if (settings.nowPlayingLyricsButton) "With the lyrics button" else "Classic, lyrics card below",
            ) { nav.navigate("settings/style/now-playing") }
            HubRow(
                Icons.Rounded.PlayCircle,
                "Mini Player",
                settings.miniButtons.joinToString(", ") { it.label }.ifEmpty { "No buttons" },
            ) { nav.navigate("settings/style/mini-player") }
            HubRow(
                Icons.Rounded.Dashboard,
                "Nav Bar",
                settings.navTabs.joinToString(", ") { it.label },
            ) { nav.navigate("settings/style/nav-bar") }
        }
    }
}

/** Now Playing Style (issue #346): today's look with the lyrics button, or the one before it. */
@Composable
fun NowPlayingStyleScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    Screen(title = "Now Playing Style", nav = nav) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.Page)) {
            Row(
                Modifier.fillMaxWidth().clickable { setLyricsButton(graph, !settings.nowPlayingLyricsButton) }.padding(vertical = Space.Medium),
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
            Spacer(Modifier.height(Space.Section))
            Text(
                "More now playing looks will come here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun setLyricsButton(graph: app.musix.AppGraph, on: Boolean) {
    AppLog.i("Style", "Now playing: ${if (on) "lyrics button" else "classic"}")
    graph.settings.setNowPlayingLyricsButton(on)
}

/**
 * Mini Player (issue #346, #372): its buttons as a list, in the order they sit on the bar. Hold a row or grab its
 * 6-dot handle to move it; the eye shows or hides it. Lyrics, Add to playlist and Like can be added, up to
 * [MiniButton.MAX] at once.
 */
@Composable
fun MiniPlayerStyleScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val shown = settings.miniButtons
    fun save(buttons: List<MiniButton>) {
        AppLog.i("Style", "Mini player buttons: ${MiniButton.format(buttons).ifEmpty { "none" }}")
        graph.settings.setMiniButtons(buttons)
    }
    Screen(
        title = "Mini Player",
        nav = nav,
        actions = { TextButton(onClick = { save(MiniButton.default) }, enabled = shown != MiniButton.default) { Text("Reset") } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            StyleHint("Hold a row and drag it, left to right on the bar. Tap the eye to show or hide it. Up to ${MiniButton.MAX} buttons.")
            ArrangeList(
                order = arrangeOrder(shown, MiniButton.entries),
                shown = shown.toSet(),
                label = { it.label },
                icon = { it.icon() },
                key = { it.id },
                canHide = { true },
                canShowMore = shown.size < MiniButton.MAX,
                onChange = { order, on -> save(shownInOrder(order, on)) },
            )
        }
    }
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
        title = "Nav Bar",
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
) {
    val haptics = LocalHapticFeedback.current
    val pitchPx = with(LocalDensity.current) { ARRANGE_PITCH.toPx() }
    val saved by rememberUpdatedState(order)
    val on by rememberUpdatedState(shown)
    val change by rememberUpdatedState(onChange)
    var items by remember(order) { mutableStateOf(order) }
    var dragging by remember { mutableStateOf<T?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }

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
            val lifted = item == dragging
            val isShown = item in shown
            val toggleable = if (isShown) canHide(item) else canShowMore
            Surface(
                color = if (lifted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .zIndex(if (lifted) 1f else 0f)
                    .graphicsLayer { translationY = if (lifted) offset else 0f }
                    .padding(horizontal = Space.Page, vertical = Space.Tight)
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
                    IconButton(
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
