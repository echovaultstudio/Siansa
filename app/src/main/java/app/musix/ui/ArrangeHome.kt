package app.musix.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.musix.data.HomeSection
import app.musix.data.moved

/**
 * A short list of Home's sections to drag into a new order: hold a row, or grab its handle, and move it.
 * Only the row being dragged follows the finger; the others slide out of the way. The order is saved
 * when a row is let go.
 */
@Composable
fun ArrangeHomeSheet(order: List<HomeSection>, onChange: (List<HomeSection>) -> Unit, onDismiss: () -> Unit) {
    var items by remember { mutableStateOf(order) }
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Arrange Home", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Hold a row and drag it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        items = HomeSection.default
                        onChange(items)
                    },
                    enabled = items != HomeSection.default,
                ) { Text("Reset") }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(state = list, userScrollEnabled = dragging == null) {
                items(items, key = { it.id }) { section ->
                    val lifted = section == dragging
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
                            .padding(horizontal = 16.dp, vertical = 3.dp)
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
                        Row(Modifier.height(52.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(section.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
