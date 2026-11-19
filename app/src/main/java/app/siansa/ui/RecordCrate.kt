package app.siansa.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    /** The sleeve's colours where there's no cover; the key's own when not given. */
    val colors: Pair<Color, Color>? = null,
    /** What it is, for the row's cover grid (#1263); without one, its [covers] fill the grid. */
    val grid: GridSubject? = null,
)

/**
 * Mixes as sleeves to flick through (the owner, 17 Nov: "a beautiful carousel card for them on Home that resembles
 * flicking through records"; then no record peeking out and smooth swiping; then, from a video of it: the ones not in
 * front more blurred, no dots, and no sleeves sliding over each other as they pass). A flat row with a gap between
 * the sleeves, so none ever overlaps another: the one in front is sharp and full size; the ones beside it are a little
 * smaller, blurred and dimmed, all three following the finger on one eased curve. A flick passes a few sleeves at most
 * and settles on a soft spring without bouncing. Tap the front one to open it.
 *
 * Its name and a short line on what's in it show in one place, never twice (the owner, 19 Nov, a choice under the
 * row's cog): under the row, fading out as a sleeve leaves the front and in as the next arrives, or [namesOnCover]
 * on each sleeve, over a soft fade at its foot, so the neighbours carry theirs too. Long names keep whole words, a
 * size smaller before a word would be cut; a line that still doesn't fit ends with "…".
 */
@Composable
fun RecordCrate(
    records: List<CrateRecord>,
    modifier: Modifier = Modifier,
    // Where it opens: the second, so there's one on each side (the owner, 17 Nov), unless there are only two.
    startAt: Int = if (records.size >= 3) 1 else 0,
    namesOnCover: Boolean = LocalCrateNamesOnCover.current,
) {
    if (records.isEmpty()) return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = startAt.coerceIn(0, records.lastIndex)) { records.size }
    Column(modifier.fillMaxWidth().testTag("recordCrate"), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // A sleeve a little over half the width, so the neighbours show on both sides; never huge on a tablet.
            val sleeve = min(maxWidth.value * CRATE_SLEEVE, CRATE_SLEEVE_MOST).dp
            val side = ((maxWidth - sleeve) / 2).coerceAtLeast(0.dp)
            HorizontalPager(
                state = pager,
                pageSize = PageSize.Fixed(sleeve),
                pageSpacing = Space.Medium,
                contentPadding = PaddingValues(horizontal = side), // spacing: exact (the front sleeve in the middle)
                beyondViewportPageCount = 2,
                // A flick passes a few sleeves at most and settles softly, never with a jolt or a bounce.
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pager,
                    pagerSnapDistance = PagerSnapDistance.atMost(3),
                    snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                ),
                modifier = Modifier.fillMaxWidth().height(sleeve),
            ) { page ->
                val record = records[page]
                Sleeve(
                    record,
                    namesOnCover,
                    Modifier
                        .size(sleeve)
                        .graphicsLayer {
                            // How far this sleeve is from the front, eased: 0 in front, 1 beside it and beyond.
                            val off = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceAtMost(1f)
                            val away = FastOutSlowInEasing.transform(off)
                            val shrink = 1f - CRATE_SHRINK * away
                            scaleX = shrink
                            scaleY = shrink
                            alpha = 1f - CRATE_DIM * away
                            // Blurred beside the front (Android 12 and later; before that they're only dimmed).
                            val blur = CRATE_BLUR.toPx() * away
                            renderEffect = if (blur >= 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
                        }
                        .clickable(onClickLabel = "Open ${record.name}") { if (page == pager.currentPage) record.onOpen() else scope.launch { pager.animateScrollToPage(page) } }
                        .semantics { contentDescription = record.name },
                )
            }
        }
        // The front sleeve's name and line: gone halfway between two sleeves, back as the next settles in front.
        if (!namesOnCover) {
            val record = records[pager.currentPage.coerceIn(0, records.lastIndex)]
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = (1f - 2f * abs(pager.currentPageOffsetFraction)).coerceIn(0f, 1f) }
                    .clickable(onClick = record.onOpen)
                    .padding(horizontal = Space.Page),
                verticalArrangement = Arrangement.spacedBy(Space.Tight),
            ) {
                WholeWordsText(record.name, MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    Modifier.testTag("crateName"), maxLines = 2)
                if (record.caption.isNotBlank()) {
                    Text(record.caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("crateCaption"))
                }
            }
        }
    }
}

/** Whether the carousel of the row being drawn puts each name on its cover (Arrange Home, the owner 19 Nov). */
val LocalCrateNamesOnCover = androidx.compose.runtime.compositionLocalOf { false }

/**
 * A sleeve: four covers in a square (one when there's one), the mix's colours behind. With [nameOnIt], its name and
 * line over a fade at its foot: clear for most of the cover, deepening smoothly to near black under the words, so
 * they read on any cover and the fade never shows a hard edge.
 */
@Composable
private fun Sleeve(record: CrateRecord, nameOnIt: Boolean, modifier: Modifier) {
    val colors = remember(record.key, record.colors) { record.colors ?: tileColors(record.key) }
    val shape = RoundedCornerShape(CRATE_CORNER)
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.first, colors.second)))
            .testTag("crateSleeve"),
    ) {
        val covers = record.covers.filterNotNull().distinct()
        // The row's cover grid (#1263): across the sleeve, 2×4 and 3×6 a band across its middle with the colours above
        // and below, so every cover stays square.
        val coverGrid = LocalCoverGrid.current
        val cells = if (coverGrid != null) app.siansa.data.CoverGrid.cells(if (record.grid != null) rememberGridCovers(record.grid, coverGrid.count) else record.covers, coverGrid.count) else emptyList()
        when {
            coverGrid != null && cells.isNotEmpty() ->
                CoverGridArt(cells, coverGrid, Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(coverGrid.aspect), androidx.compose.ui.graphics.RectangleShape)
            covers.size >= 4 -> Column(Modifier.fillMaxSize()) {
                for (row in 0..1) Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0..1) Cover(covers[row * 2 + col], 300, Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(0.dp))
                }
            }
            covers.isNotEmpty() -> Cover(covers.first(), 600, Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
        }
        if (nameOnIt) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Black.copy(alpha = 0.45f), 1f to Color.Black.copy(alpha = 0.85f)))
                    .padding(start = Space.Medium, end = Space.Medium, top = Space.Large, bottom = Space.Medium)
                    .testTag("crateOnCover"),
                verticalArrangement = Arrangement.spacedBy(Space.Tight),
            ) {
                WholeWordsText(record.name, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, shadow = CRATE_SHADOW),
                    color = Color.White, maxLines = 2)
                if (record.caption.isNotBlank()) {
                    Text(record.caption, style = MaterialTheme.typography.bodySmall.copy(shadow = CRATE_SHADOW), color = Color.White.copy(alpha = 0.82f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// A soft shadow under the words on a cover, for the odd light cover the fade doesn't darken enough.
private val CRATE_SHADOW = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 6f)

private const val CRATE_SLEEVE = 0.62f // of the width
private const val CRATE_SLEEVE_MOST = 280f // dp
private const val CRATE_SHRINK = 0.12f // how much smaller a sleeve beside the front is
private const val CRATE_DIM = 0.3f // how much a sleeve beside the front fades
private val CRATE_BLUR: Dp = 3.dp // how blurred a sleeve beside the front: gently (the owner, 17 Nov)
private val CRATE_CORNER: Dp = 6.dp
