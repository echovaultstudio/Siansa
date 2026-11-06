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
import androidx.compose.ui.unit.dp

/** What a slide from an album's Explore button shows (issue #965): right onto Play for a DJ mix. */
data class ExploreSlide(val way: SlideWay, val armed: Boolean)

/** What a slide from Explore can start (issue #965): only the DJ mix; similar albums is a tap. */
enum class ExploreChoice(val title: String) {
    DJ_MIX("DJ mix this album");

    companion object {
        /**
         * The choice for a slide that way: right onto Play; null any other way. Similar albums is a tap only (the owner:
         * no slide up for it).
         */
        fun of(way: SlideWay?): ExploreChoice? = when (way) {
            SlideWay.RIGHT -> DJ_MIX
            else -> null
        }
    }
}

/**
 * An album's Explore button (issue #965), where "Find similar albums" was, as an icon: a tap opens similar albums,
 * and sliding right onto the Play button starts a DJ mix of the album's own songs in the best order. The choice shows in the middle of the cover ([ExploreCoverLabel], from [onSliding]) and is armed once the
 * finger is far enough, with a click; letting go there starts it, with a stronger one. [rightArmPx] is how far right
 * the Play button is (its near edge), measured by the row; until then a fixed distance.
 */
@Composable
internal fun ExploreButton(
    onSimilar: () -> Unit,
    onDjMix: () -> Unit,
    onSliding: (ExploreSlide?) -> Unit,
    modifier: Modifier = Modifier,
    rightArmPx: Float? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val deadZone = with(density) { EXPLORE_DEAD_ZONE.toPx() }
    val rightArm = rightArmPx ?: with(density) { EXPLORE_RIGHT_ARM.toPx() }
    val similar by rememberUpdatedState(onSimilar)
    val djMix by rememberUpdatedState(onDjMix)
    val sliding by rememberUpdatedState(onSliding)
    val armRight by rememberUpdatedState(rightArm)
    var shown by remember { mutableStateOf<ExploreSlide?>(null) }
    fun show(next: ExploreSlide?) {
        if (next == shown) return
        val before = shown
        shown = next
        sliding(next)
        if (next != null && (next.way != before?.way || (next.armed && before?.armed != true))) {
            QueueHaptics.vibrate(context, QueueHaptics.Kind.START)
        }
    }
    val active = shown != null
    // Just its icon (the owner, issue #965): a record with AudioMuse's sparkle, the size of Shuffle beside it. It fills
    // with the accent while it slides.
    Box(
        modifier
            .size(EXPLORE_SIZE)
            .clip(CircleShape)
            .background(if (active) Accent else Color.Transparent)
            .pointerInput(Unit) { detectTapGestures(onTap = { similar() }) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        val last = shown
                        if (last?.armed == true) {
                            QueueHaptics.vibrate(context, QueueHaptics.Kind.ADDED)
                            when (ExploreChoice.of(last.way)) {
                                ExploreChoice.DJ_MIX -> djMix()
                                null -> Unit
                            }
                        }
                        show(null)
                    },
                    onDragCancel = { show(null) },
                    onDrag = { change, _ ->
                        change.consume()
                        val d = change.position - Offset(size.width / 2f, size.height / 2f)
                        val way = slideWay(d.x, d.y, deadZone)
                        show(
                            if (ExploreChoice.of(way) == null) null
                            else ExploreSlide(way!!, exploreArmed(way, d.x, armRight)),
                        )
                    },
                )
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Explore: similar albums, or a DJ mix of this album"
                onClick(label = "Find similar albums") { similar(); true }
                customActions = listOf(
                    CustomAccessibilityAction(ExploreChoice.DJ_MIX.title) { djMix(); true },
                )
            }
            .testTag("exploreButton"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(SiansaIcons.RecordSparkle, null, tint = if (active) OnAccent else Accent, modifier = Modifier.size(EXPLORE_ICON))
    }
}

/** Whether a slide is far enough to start its choice: right past [rightArm] (onto Play), in px. */
internal fun exploreArmed(way: SlideWay, dx: Float, rightArm: Float): Boolean = way == SlideWay.RIGHT && dx >= rightArm


private val EXPLORE_SIZE = 48.dp
private val EXPLORE_ICON = 28.dp
private val EXPLORE_DEAD_ZONE = 16.dp
private val EXPLORE_RIGHT_ARM = 96.dp
