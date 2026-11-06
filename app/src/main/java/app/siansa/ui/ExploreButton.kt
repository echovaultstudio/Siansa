package app.siansa.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** What a slide from an album's Explore button shows (issue #965): up for similar albums, right onto Play for a DJ mix. */
data class ExploreSlide(val way: SlideWay, val armed: Boolean)

/** The two things a slide from Explore can start (issue #965). */
enum class ExploreChoice(val title: String, val hint: String, val ready: String) {
    SIMILAR("Similar albums", "Albums that sound like this one", "Let go to open"),
    DJ_MIX("DJ mix this album", "Its songs only, in the order that blends best", "Let go to start");

    companion object {
        /** The choice for a slide that way: up onto the cover, right onto Play; null any other way. */
        fun of(way: SlideWay?): ExploreChoice? = when (way) {
            SlideWay.UP -> SIMILAR
            SlideWay.RIGHT -> DJ_MIX
            else -> null
        }
    }
}

/**
 * An album's Explore button (issue #965), where "Find similar albums" was, as an icon: a tap opens similar albums; sliding the finger
 * up (onto the cover) does too, and sliding right onto the Play button starts a DJ mix of the album's own songs in the
 * best order. The choice shows in the middle of the cover ([ExploreCoverLabel], from [onSliding]) and is armed once the
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
    val upArm = with(density) { EXPLORE_UP_ARM.toPx() }
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
                                ExploreChoice.SIMILAR -> similar()
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
                            else ExploreSlide(way!!, exploreArmed(way, d.x, d.y, upArm, armRight)),
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

/** Whether a slide is far enough to start its choice: up past [upArm], right past [rightArm] (onto Play), in px. */
internal fun exploreArmed(way: SlideWay, dx: Float, dy: Float, upArm: Float, rightArm: Float): Boolean = when (way) {
    SlideWay.UP -> -dy >= upArm
    SlideWay.RIGHT -> dx >= rightArm
    else -> false
}

/**
 * The choice of a slide from Explore, in the middle of the album's cover (issue #965), as now playing shows a slide
 * from its play button: the cover darkens behind it (the page blurs the cover itself), faint until it's far enough.
 */
@Composable
internal fun ExploreCoverLabel(slide: ExploreSlide?, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf(slide) }
    if (slide != null) last = slide
    AnimatedVisibility(slide != null, modifier = modifier, enter = fadeIn(tween(EXPLORE_FADE_MS)), exit = fadeOut(tween(EXPLORE_FADE_MS))) {
        val shownSlide = last ?: return@AnimatedVisibility
        val choice = ExploreChoice.of(shownSlide.way) ?: return@AnimatedVisibility
        val ready = shownSlide.armed
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (ready) EXPLORE_SCRIM_READY else EXPLORE_SCRIM)).testTag("exploreLabel"),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = Space.Page).alpha(if (ready) 1f else EXPLORE_FAINT),
            ) {
                val tint = if (ready) Accent else Color.White
                if (choice == ExploreChoice.DJ_MIX) Icon(SiansaIcons.Turntable, null, tint = tint, modifier = Modifier.size(EXPLORE_LABEL_ICON))
                else Icon(rememberSimilarAlbumsIcon(), null, tint = tint, modifier = Modifier.size(EXPLORE_LABEL_ICON))
                Spacer(Modifier.height(Space.Small))
                Text(choice.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("exploreName"))
                Spacer(Modifier.height(Space.Tight))
                Text(choice.hint, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(Space.Tight))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (shownSlide.way == SlideWay.UP) Icons.Rounded.KeyboardArrowUp else Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                        tint = Accent, modifier = Modifier.size(EXPLORE_ARROW))
                    Text(if (ready) choice.ready else "Slide a little further", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }
        }
    }
}

private val EXPLORE_SIZE = 48.dp
private val EXPLORE_ICON = 28.dp
private val EXPLORE_LABEL_ICON = 36.dp
private val EXPLORE_ARROW = 18.dp
private val EXPLORE_DEAD_ZONE = 16.dp
private val EXPLORE_UP_ARM = 56.dp
private val EXPLORE_RIGHT_ARM = 96.dp
private const val EXPLORE_FADE_MS = 140
private const val EXPLORE_FAINT = 0.6f
private const val EXPLORE_SCRIM = 0.3f
private const val EXPLORE_SCRIM_READY = 0.5f
