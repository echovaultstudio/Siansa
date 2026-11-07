package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.ui.unit.dp

/** A slide from an album's shuffle button towards Play (issue #965); [armed] once the finger is on Play. */
data class DjSlide(val armed: Boolean) {
    /** Always to the right, onto Play. */
    val way: SlideWay get() = SlideWay.RIGHT
}

/**
 * An album's shuffle button (issue #965, the owner's choice 2026-11-07): a tap shuffles the album as always, and
 * sliding right onto the Play button beside it starts a DJ mix of the album's own songs. While it slides, the cover
 * says DJ mix and the rest of the bar dims ([onSliding]); once the finger is on Play it's armed, with a click, and Play
 * becomes a record; letting go there starts the mix, with a stronger click. [playLeftPx] is how far right of the
 * button's middle Play's near edge is, measured by the bar; until then a fixed distance.
 */
@Composable
internal fun ShuffleDjButton(
    enabled: Boolean,
    onShuffle: () -> Unit,
    onDjMix: () -> Unit,
    onSliding: (DjSlide?) -> Unit,
    modifier: Modifier = Modifier,
    playLeftPx: Float? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val deadZone = with(density) { DJ_DEAD_ZONE.toPx() }
    val rightArm = playLeftPx ?: with(density) { DJ_RIGHT_ARM.toPx() }
    val shuffle by rememberUpdatedState(onShuffle)
    val djMix by rememberUpdatedState(onDjMix)
    val sliding by rememberUpdatedState(onSliding)
    val armRight by rememberUpdatedState(rightArm)
    var shown by remember { mutableStateOf<DjSlide?>(null) }
    fun show(next: DjSlide?) {
        if (next == shown) return
        val before = shown
        shown = next
        sliding(next)
        if (next != null && (before == null || (next.armed && !before.armed))) QueueHaptics.vibrate(context, QueueHaptics.Kind.START)
    }
    val active = shown != null
    Box(
        modifier
            .size(SHUFFLE_SIZE)
            .clip(CircleShape)
            .background(if (active) Accent else Color.Transparent)
            .then(
                if (!enabled) Modifier
                else Modifier
                    .pointerInput(Unit) { detectTapGestures(onTap = { shuffle() }) }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                if (shown?.armed == true) {
                                    QueueHaptics.vibrate(context, QueueHaptics.Kind.ADDED)
                                    djMix()
                                }
                                show(null)
                            },
                            onDragCancel = { show(null) },
                            onDrag = { change, _ ->
                                change.consume()
                                val d = change.position - Offset(size.width / 2f, size.height / 2f)
                                val way = slideWay(d.x, d.y, deadZone)
                                show(if (way == SlideWay.RIGHT) DjSlide(djArmed(d.x, armRight)) else null)
                            },
                        )
                    },
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Shuffle"
                if (enabled) {
                    onClick(label = "Shuffle") { shuffle(); true }
                    customActions = listOf(CustomAccessibilityAction(DJ_MIX_ACTION) { djMix(); true })
                }
            }
            .testTag("shuffleAll"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Shuffle, null, tint = if (active) OnAccent else Accent, modifier = Modifier.size(SHUFFLE_ICON))
    }
}

/** Whether a slide right by [dx] px reaches Play, [rightArm] px from the button's middle. */
internal fun djArmed(dx: Float, rightArm: Float): Boolean = dx >= rightArm

/** The accessibility action on an album's shuffle button for its DJ mix. */
internal const val DJ_MIX_ACTION = "DJ mix this album"

private val SHUFFLE_SIZE = 48.dp
private val SHUFFLE_ICON = 30.dp
private val DJ_DEAD_ZONE = 16.dp
private val DJ_RIGHT_ARM = 72.dp
