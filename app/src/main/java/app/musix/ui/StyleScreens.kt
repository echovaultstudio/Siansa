package app.musix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.musix.data.MiniButton
import app.musix.data.NavTab
import app.musix.data.moved
import app.musix.log.AppLog
import kotlin.math.roundToInt

/**
 * Settings › Style (issue #346): how the app looks, each part on its own page. Theme is the Themes page as
 * before; Now Playing Style, Mini Player and Nav Bar are new.
 */
@Composable
fun StyleScreen(nav: NavController) {
    Screen(title = "Style", nav = nav) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            HubRow(Icons.Rounded.Palette, "Theme") { nav.navigate("settings/themes") }
            HubRow(Icons.Rounded.PhoneAndroid, "Now Playing Style") { nav.navigate("settings/style/now-playing") }
            HubRow(Icons.Rounded.PlayCircle, "Mini Player") { nav.navigate("settings/style/mini-player") }
            HubRow(Icons.Rounded.Dashboard, "Nav Bar") { nav.navigate("settings/style/nav-bar") }
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
 * Mini Player (issue #346): a picture of the mini player where the buttons can be dragged into place, and a
 * list to show, hide or add them (Lyrics, Add to playlist, Like). Each button keeps its own slot, so the
 * spacing stays even however they're arranged.
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
            StyleHint("Drag a button in the picture to move it.")
            Box(Modifier.padding(horizontal = Space.Small)) {
                MiniPlayerPicture(shown, onReorder = ::save)
            }
            Spacer(Modifier.height(Space.Section))
            SectionTitle("Buttons", top = Space.Tight)
            MiniButton.entries.forEach { button ->
                val on = button in shown
                ToggleRow(
                    icon = button.icon(),
                    title = button.label,
                    subtitle = if (!on && shown.size >= MiniButton.MAX) "Up to ${MiniButton.MAX} buttons" else null,
                    checked = on,
                    enabled = on || shown.size < MiniButton.MAX,
                ) { save(MiniButton.toggle(shown, button)) }
            }
        }
    }
}

/**
 * Nav Bar (issue #346): a picture of the bottom bar where the tabs can be dragged into a new order, and a
 * list to show or hide them, up to [NavTab.MAX]. Settings always stays, so these choices can be found again.
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
            StyleHint("Drag a tab in the picture to move it.")
            NavBarPicture(shown, onReorder = ::save)
            Spacer(Modifier.height(Space.Section))
            SectionTitle("Tabs", top = Space.Tight)
            NavTab.entries.forEach { tab ->
                val on = tab in shown
                val required = tab in NavTab.required
                ToggleRow(
                    icon = tab.icon(),
                    title = tab.label,
                    subtitle = if (required) "Always shown" else null,
                    checked = on,
                    enabled = !required && (on || shown.size < NavTab.MAX),
                ) { save(NavTab.toggle(shown, tab)) }
            }
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

/** A row with a checkbox, to show or hide one button or tab. */
@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = Space.Page, vertical = Space.Small)
            .testTag("styleToggle"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = Space.Page)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}

/** The mini player's look, with its buttons in the order set: drag one to move it. */
@Composable
private fun MiniPlayerPicture(buttons: List<MiniButton>, onReorder: (List<MiniButton>) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2A2A30))
            .padding(Space.Small)
            .testTag("miniPicture"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.7f)) }
        Spacer(Modifier.width(Space.Medium))
        Column(Modifier.weight(1f)) {
            Text("Song title", style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Artist", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
        }
        DragOrderRow(buttons, slot = MINI_SLOT, onReorder = onReorder) { button, lifted ->
            Box(
                Modifier
                    .size(MINI_SLOT)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (lifted) Color.White.copy(alpha = 0.2f) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) { Icon(button.icon(), button.label, tint = Color.White) }
        }
    }
}

/** The bottom bar's look, with its tabs in the order set: drag one to move it. */
@Composable
private fun NavBarPicture(tabs: List<NavTab>, onReorder: (List<NavTab>) -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = Space.Small)
            .testTag("navPicture"),
    ) {
        val slot = maxWidth / tabs.size.coerceAtLeast(1)
        DragOrderRow(tabs, slot = slot, onReorder = onReorder) { tab, lifted ->
            Column(
                Modifier
                    .width(slot)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (lifted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .padding(vertical = Space.Tight),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon(), null)
                Text(tab.label, maxLines = 1, softWrap = false, fontSize = 11.sp, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A row of equal slots whose items can be dragged sideways into a new order (issue #346). The dragged item
 * follows the finger; as it passes half a slot it takes the neighbouring place, with a tick, so items always
 * sit on their slots and the spacing stays even. The new order is passed on when it's let go.
 */
@Composable
internal fun <T> DragOrderRow(
    items: List<T>,
    slot: Dp,
    onReorder: (List<T>) -> Unit,
    content: @Composable (item: T, lifted: Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // The drag handlers live as long as their item, so they read these through state that follows each change.
    val slotPx by rememberUpdatedState(with(LocalDensity.current) { slot.toPx() })
    val saved by rememberUpdatedState(items)
    val reorder by rememberUpdatedState(onReorder)
    var order by remember(items) { mutableStateOf(items) }
    var dragging by remember { mutableStateOf<T?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    Row(horizontalArrangement = Arrangement.Start) {
        order.forEach { item ->
            val lifted = item == dragging
            Box(
                Modifier
                    .width(slot)
                    .zIndex(if (lifted) 1f else 0f)
                    .graphicsLayer { translationX = if (lifted) offset else 0f }
                    .pointerInput(item) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragging = item
                                offset = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                dragging = null
                                offset = 0f
                                if (order != saved) reorder(order)
                            },
                            onDragCancel = {
                                dragging = null
                                offset = 0f
                                order = saved
                            },
                        ) { change, dx ->
                            change.consume()
                            offset += dx
                            val from = order.indexOf(item)
                            val to = dragTarget(from, offset, slotPx, order.size)
                            if (to != from) {
                                order = order.moved(from, to)
                                offset -= (to - from) * slotPx
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                    .testTag("dragSlot"),
                contentAlignment = Alignment.Center,
            ) { content(item, lifted) }
        }
    }
}

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
