package app.siansa.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * One record in a [RecordCrate]: its sleeve shows up to four of its songs' covers, and its [name] and [caption] show
 * under the crate while it's the one in front.
 */
@Immutable
data class CrateRecord(
    val key: String,
    val name: String,
    val caption: String,
    val covers: List<String?>,
    val songs: List<app.siansa.data.Song>,
    val onOpen: () -> Unit,
    val onPlay: () -> Unit,
)

/**
 * Mixes as sleeves in a crate (the owner, 17 Nov: "a beautiful carousel card for them on Home that resembles flicking
 * through records"; later the same day: no record peeking out, and "make the swiping animation really smooth"): the
 * one in front stands up straight; the ones behind lean back gently and tuck under it. Every change follows the finger
 * continuously (no step or clamp), and a flick settles on a soft spring without bouncing. Tap the front one to open it.
 * Under the crate: its name, a short line on what's in it, and Play, fading across as the front one changes.
 */
@Composable
fun RecordCrate(
    records: List<CrateRecord>,
    modifier: Modifier = Modifier,
    startAt: Int = 0,
    // Play, for the record in front: it shows Pause while its songs play (drawn plain by the screenshot tests).
    play: @Composable (CrateRecord) -> Unit = { CratePlay(it) },
) {
    if (records.isEmpty()) return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = startAt.coerceIn(0, records.lastIndex)) { records.size }
    Column(modifier.fillMaxWidth().testTag("recordCrate"), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // A sleeve a little over half the width, so the neighbours show on both sides; never huge on a tablet.
            val sleeve = min(maxWidth.value * CRATE_SLEEVE, CRATE_SLEEVE_MOST).dp
            // Each page is narrower than its sleeve, so the sleeves overlap and stack as in a crate.
            val slot = sleeve * (1f - CRATE_OVERLAP)
            val side = ((maxWidth - slot) / 2).coerceAtLeast(0.dp)
            HorizontalPager(
                state = pager,
                // A flick passes a few sleeves at most and settles softly, never with a jolt or a bounce.
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pager,
                    pagerSnapDistance = PagerSnapDistance.atMost(3),
                    snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                ),
                pageSize = PageSize.Fixed(slot),
                contentPadding = PaddingValues(horizontal = side), // spacing: exact (the front sleeve in the middle)
                beyondViewportPageCount = 3,
                modifier = Modifier.fillMaxWidth().height(sleeve + CRATE_ROOM),
            ) { page ->
                val record = records[page]
                // How far this sleeve is from the front: 0 in front, 1 one along, negative to the left.
                val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
                val far = abs(off).coerceAtMost(CRATE_FAR)
                // Eased, so a sleeve leaves the front slowly and settles slowly: no kink as it passes a neighbour.
                val eased = FastOutSlowInEasing.transform(far / CRATE_FAR) * CRATE_FAR
                Box(
                    Modifier
                        .zIndex(-far)
                        .requiredSize(sleeve, sleeve + CRATE_ROOM)
                        .graphicsLayer {
                            // Leaning back into the crate, away from the one in front.
                            rotationY = kotlin.math.sign(off) * eased / CRATE_FAR * CRATE_TILT
                            cameraDistance = 16f * density
                            val shrink = 1f - CRATE_SHRINK * eased
                            scaleX = shrink
                            scaleY = shrink
                            translationY = CRATE_SINK.toPx() * eased
                            alpha = 1f - 0.35f * (eased / CRATE_FAR)
                        },
                    contentAlignment = Alignment.TopStart,
                ) {
                    Sleeve(
                        record,
                        Modifier
                            .size(sleeve)
                            .clickable(onClickLabel = "Open ${record.name}") { if (page == pager.currentPage) record.onOpen() else scope.launch { pager.animateScrollToPage(page) } }
                            .semantics { contentDescription = record.name },
                    )
                }
            }
        }
        val record = records[pager.currentPage.coerceIn(0, records.lastIndex)]
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.Page),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            AnimatedContent(
                targetState = record,
                transitionSpec = { fadeIn(tween(CRATE_FADE_MS, delayMillis = CRATE_FADE_MS / 2)) togetherWith fadeOut(tween(CRATE_FADE_MS / 2)) },
                contentKey = { it.key },
                label = "crateCaption",
                modifier = Modifier.weight(1f),
            ) { shown ->
                Column(Modifier.fillMaxWidth().clickable(onClick = shown.onOpen), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                    Text(shown.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("crateName"))
                    Text(shown.caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("crateCaption"))
                }
            }
            play(record)
        }
        CarouselDots(count = records.size, current = pager.currentPage)
    }
}

/** Play for the record in front, Pause while its songs are playing. */
@Composable
private fun CratePlay(record: CrateRecord) {
    val graph = LocalGraph.current
    val card = rememberCardPlay(record.songs)
    RoundPlayButton(card.playing, if (card.playing) "Pause ${record.name}" else "Play ${record.name}", CRATE_PLAY, CRATE_PLAY_ICON, Modifier.testTag("cratePlay")) {
        if (card.loaded) graph.player.togglePlay() else record.onPlay()
    }
}

/** A sleeve: four covers in a square (one when there's one), the mix's colours behind, a soft edge of light. */
@Composable
private fun Sleeve(record: CrateRecord, modifier: Modifier) {
    val colors = remember(record.key) { tileColors(record.key) }
    val shape = RoundedCornerShape(CRATE_CORNER)
    Box(
        modifier
            .shadow(CRATE_SHADOW, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.first, colors.second)))
            .testTag("crateSleeve"),
    ) {
        val covers = record.covers.filterNotNull().distinct()
        when {
            covers.size >= 4 -> Column(Modifier.fillMaxSize()) {
                for (row in 0..1) Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0..1) Cover(covers[row * 2 + col], 300, Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(0.dp))
                }
            }
            covers.isNotEmpty() -> Cover(covers.first(), 600, Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
        }
        // The sleeve's name strip, as a record shop prints it: on a dark band so it reads over any cover.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                .padding(start = Space.Medium, end = Space.Medium, top = Space.Large, bottom = Space.Medium),
        ) {
            WholeWordsText(record.name, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = Color.White, maxLines = 2)
        }
    }
}

private const val CRATE_SLEEVE = 0.62f // of the width
private const val CRATE_SLEEVE_MOST = 280f // dp
private const val CRATE_OVERLAP = 0.42f // of a sleeve: how far the ones behind tuck under
private const val CRATE_TILT = 30f // degrees a sleeve two along leans back (about 23 one along, eased)
private const val CRATE_FAR = 2f // sleeves from the front where the lean, shrink and fade stop growing
private const val CRATE_SHRINK = 0.06f // per sleeve along
private const val CRATE_FADE_MS = 220
private val CRATE_ROOM: Dp = 20.dp // under the sleeves, for the ones behind to sink into
private val CRATE_SINK: Dp = 8.dp
private val CRATE_CORNER: Dp = 6.dp
private val CRATE_SHADOW: Dp = 12.dp
internal val CRATE_PLAY: Dp = 52.dp
internal val CRATE_PLAY_ICON: Dp = 30.dp
