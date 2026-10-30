package app.siansa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import kotlin.math.roundToInt
import androidx.compose.ui.platform.testTag
import app.siansa.data.AppSettings
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.zIndex
import app.siansa.data.HomeLook
import app.siansa.data.HomeSection
import app.siansa.data.moved
import app.siansa.data.movesFirstVisible

/**
 * Home settings, behind Home's top-right buttons: Sources (whose music shows, see [SourcesPanel]) and Arrange.
 *
 * Arrange is a short list of Home's sections to drag into a new order: hold a row, or grab its handle, and move it.
 * Only the row being dragged follows the finger; the others slide out of the way. The order is saved
 * when a row is let go. The eye on each row hides the section from Home, or shows it again.
 */
@Composable
fun ArrangeHomeSheet(
    order: List<HomeSection>,
    hidden: Set<HomeSection>,
    onChange: (List<HomeSection>) -> Unit,
    onHiddenChange: (Set<HomeSection>) -> Unit,
    onDismiss: () -> Unit,
    startOnSources: Boolean = false,
    // The gap between Home's sections, a step of the spacing scale (#554).
    gap: Int = AppSettings.HOME_GAP_DEFAULT,
    onGapChange: (Int) -> Unit = {},
    // Each row's look (issue #655), changed with its cog; Your cards' cog opens Settings › Cards.
    looks: Map<HomeSection, HomeLook> = emptyMap(),
    onLookChange: (HomeSection, HomeLook) -> Unit = { _, _ -> },
    onResetLooks: () -> Unit = {},
    onOpenCards: () -> Unit = {},
) {
    var picking by remember { mutableStateOf<HomeSection?>(null) }
    picking?.let { section ->
        HomeLookDialog(section, looks[section] ?: HomeLook.AS_IS, onPick = { onLookChange(section, it); picking = null }, onDismiss = { picking = null })
    }
    var sources by remember { mutableStateOf(startOnSources) }
    var items by remember { mutableStateOf(order) }
    var hiddenItems by remember { mutableStateOf(hidden) }
    var dragging by remember { mutableStateOf<HomeSection?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val list = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    fun start(section: HomeSection) {
        dragging = section
        offset = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun dragBy(dy: Float) {
        val section = dragging ?: return
        offset += dy
        val visible = list.layoutInfo.visibleItemsInfo
        val current = visible.firstOrNull { it.key == section.id } ?: return
        val middle = current.offset + offset + current.size / 2f
        val target = visible.firstOrNull { it.key != section.id && middle >= it.offset && middle <= it.offset + it.size } ?: return
        // Moving to or from the top row: stay at the same position, not with the row that was first, or the moved
        // row ends up above the screen and seems to vanish (#614).
        if (movesFirstVisible(current.index, target.index, list.firstVisibleItemIndex)) {
            list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        }
        items = items.moved(current.index, target.index)
        // The row is laid out at its new place next frame; keep it under the finger meanwhile.
        offset += current.offset - target.offset
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun end() {
        if (dragging == null) return
        dragging = null
        offset = 0f
        onChange(items)
    }

    // A held row near the list's top or bottom edge scrolls it (issue #701), so a row can go anywhere, not only
    // where the list was when it was picked up. The row stays under the finger as the list moves.
    val edgePx = with(androidx.compose.ui.platform.LocalDensity.current) { AUTO_SCROLL_EDGE.toPx() }
    LaunchedEffect(dragging) {
        val section = dragging ?: return@LaunchedEffect
        while (dragging == section) {
            androidx.compose.runtime.withFrameNanos { }
            val info = list.layoutInfo
            val current = info.visibleItemsInfo.firstOrNull { it.key == section.id } ?: continue
            val top = current.offset + offset
            val speed = autoScrollSpeed(top, top + current.size, info.viewportStartOffset.toFloat(), info.viewportEndOffset.toFloat(), edgePx)
            if (speed == 0f) continue
            val moved = list.scrollBy(speed)
            if (moved != 0f) {
                offset += moved
                dragBy(0f)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Space.Small)) {
            Row(Modifier.fillMaxWidth().padding(start = Space.Page, end = Space.Small), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (sources) "Source filter" else "Arrange Home", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (sources) "Whose music shows, everywhere in the app" else "Hold a row and drag it. Tap the eye to hide it, the cog to change its look.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!sources) TextButton(
                    onClick = {
                        if (items != HomeSection.default) {
                            list.requestScrollToItem(0)  // the reset order shows from its top (#614)
                            items = HomeSection.default
                            onChange(items)
                        }
                        if (hiddenItems.isNotEmpty()) {
                            hiddenItems = emptySet()
                            onHiddenChange(hiddenItems)
                        }
                        if (gap != AppSettings.HOME_GAP_DEFAULT) onGapChange(AppSettings.HOME_GAP_DEFAULT)
                        if (looks.isNotEmpty()) onResetLooks()
                    },
                    enabled = items != HomeSection.default || hiddenItems.isNotEmpty() || gap != AppSettings.HOME_GAP_DEFAULT || looks.isNotEmpty(),
                ) { Text("Reset") }
            }
            SheetTabs(sources) { sources = it }
            Spacer(Modifier.height(Space.Medium))
            if (sources) {
                SourcesPanel()
                return@Column
            }
            SpacingRow(gap, onGapChange)
            LazyColumn(state = list, userScrollEnabled = dragging == null) {
                items(items, key = { it.id }) { section ->
                    val lifted = section == dragging
                    val isHidden = section in hiddenItems
                    Surface(
                        color = if (lifted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .then(
                                if (lifted) Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
                                else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                            )
                            .zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer { translationY = if (lifted) offset else 0f }
                            .padding(horizontal = Space.Page, vertical = Space.Tight)
                            .then(if (lifted) Modifier.shadow(6.dp, RoundedCornerShape(12.dp)) else Modifier)
                            .fillMaxWidth()
                            .pointerInput(section) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { start(section) },
                                    onDrag = { change, amount -> change.consume(); dragBy(amount.y) },
                                    onDragEnd = { end() },
                                    onDragCancel = { end() },
                                )
                            },
                    ) {
                        Row(Modifier.height(52.dp).padding(start = Space.Page), verticalAlignment = Alignment.CenterVertically) {
                            // Hidden rows stay in the list, dimmed, so they keep their place and can come back.
                            Column(Modifier.weight(1f).alpha(if (isHidden) 0.6f else 1f)) {
                                Text(
                                    section.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                                // A row in another look says which (issue #655).
                                looks[section]?.let { look ->
                                    Text(look.label, style = MaterialTheme.typography.bodySmall, color = Accent, modifier = Modifier.testTag("homeLookLabel"))
                                }
                            }
                            IconButton(
                                onClick = { if (section.lookable) picking = section else onOpenCards() },
                                modifier = Modifier.testTag("homeLookCog"),
                            ) {
                                Icon(
                                    Icons.Rounded.Settings,
                                    contentDescription = if (section.lookable) "Look of ${section.label}" else "Your cards in Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                hiddenItems = if (isHidden) hiddenItems - section else hiddenItems + section
                                onHiddenChange(hiddenItems)
                            }) {
                                Icon(
                                    if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (isHidden) "Show ${section.label}" else "Hide ${section.label}",
                                    tint = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // The handle moves straight away, without the hold.
                            Icon(
                                Icons.Rounded.DragIndicator,
                                contentDescription = "Move ${section.label}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .pointerInput(section) {
                                        detectDragGestures(
                                            onDragStart = { start(section) },
                                            onDrag = { change, amount -> change.consume(); dragBy(amount.y) },
                                            onDragEnd = { end() },
                                            onDragCancel = { end() },
                                        )
                                    }
                                    .padding(horizontal = Space.Page, vertical = Space.Page)
                                    .size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The looks one of Home's rows can take (issue #655): its own, or one of the custom cards' looks. */
@Composable
private fun HomeLookDialog(section: HomeSection, current: HomeLook, onPick: (HomeLook) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(section.label) },
        text = {
            Column(Modifier.testTag("homeLookDialog")) {
                HomeLook.entries.forEach { look ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(look) }.padding(vertical = Space.Small).testTag("homeLook-${look.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = look == current, onClick = { onPick(look) })
                        Spacer(Modifier.width(Space.Small))
                        Column(Modifier.weight(1f)) {
                            Text(look.label, style = MaterialTheme.typography.bodyLarge)
                            Text(look.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * One slider for the gaps between Home's sections (#554): five steps of the spacing scale, from tight to airy, so
 * every gap on Home grows or shrinks together.
 */
@Composable
private fun SpacingRow(gap: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.Page).testTag("homeSpacing"), verticalAlignment = Alignment.CenterVertically) {
        Text("Spacing", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(Space.Page))
        Slider(
            value = gap.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..AppSettings.HOME_GAP_MAX.toFloat(),
            steps = AppSettings.HOME_GAP_MAX - 1,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
            modifier = Modifier.weight(1f).testTag("homeSpacingSlider"),
        )
        Spacer(Modifier.width(Space.Page))
        Text(homeGapLabel(gap), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
    }
}

/** Sources | Arrange, as two halves of one rounded bar (from Ratty's #124). */
@Composable
private fun SheetTabs(sources: Boolean, onSources: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(start = Space.Page, end = Space.Page, top = Space.Medium),
    ) {
        Row(Modifier.padding(3.dp)) {  // spacing: exact (the segmented control's inset)
            listOf(true to "Sources", false to "Arrange").forEach { (isSources, label) ->
                val on = sources == isSources
                Surface(
                    onClick = { onSources(isSources) },
                    shape = RoundedCornerShape(50),
                    color = if (on) Accent else androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = Space.Small),
                    )
                }
            }
        }
    }
}

/** How close to the list's edge a held row starts scrolling it, and how fast it scrolls at most (px a frame). */
private val AUTO_SCROLL_EDGE = 56.dp
internal const val AUTO_SCROLL_MAX = 18f

/**
 * How far to scroll the list this frame while a row is held between [top] and [bottom] (issue #701): nothing in the
 * middle; within [edge] of the viewport's end or start, faster the deeper the row is into that edge, up to [max].
 */
internal fun autoScrollSpeed(top: Float, bottom: Float, viewportStart: Float, viewportEnd: Float, edge: Float, max: Float = AUTO_SCROLL_MAX): Float {
    if (edge <= 0f) return 0f
    val intoEnd = bottom - (viewportEnd - edge)
    val intoStart = (viewportStart + edge) - top
    return when {
        intoEnd > 0f -> max * (intoEnd / edge).coerceIn(0f, 1f)
        intoStart > 0f -> -max * (intoStart / edge).coerceIn(0f, 1f)
        else -> 0f
    }
}
