package app.siansa.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.RectangleShape
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
 *
 * [compact] (the owner, 20 Nov, beside the big one, chosen under the row's cog): the same carousel with smaller
 * sleeves, three whole in view and the next ones peeking, so a row of mixes takes about half the height. All else is
 * the same: the curve, the spring, the dimming and blur (a little lighter, as the neighbours are whole), and the name
 * and details under the row or on each cover, a size smaller to fit.
 */
@Composable
fun RecordCrate(
    records: List<CrateRecord>,
    modifier: Modifier = Modifier,
    // Where it opens: the second, so there's one on each side (the owner, 17 Nov), unless there are only two.
    startAt: Int = if (records.size >= 3) 1 else 0,
    namesOnCover: Boolean = LocalCrateNamesOnCover.current,
    size: app.siansa.data.CrateSize = LocalCrateSize.current,
) {
    if (records.isEmpty()) return
    if (size == app.siansa.data.CrateSize.SMALL) {
        CrateStrip(records, namesOnCover, modifier)
        return
    }
    val compact = size == app.siansa.data.CrateSize.COMPACT
    val look = if (compact) CrateLook.COMPACT else CrateLook.BIG
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = startAt.coerceIn(0, records.lastIndex)) { records.size }
    Column(modifier.fillMaxWidth().testTag("recordCrate"), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // A sleeve a little over half the width, so the neighbours show on both sides; never huge on a tablet.
            val sleeve = min(maxWidth.value * look.sleeve, look.most).dp
            val side = ((maxWidth - sleeve) / 2).coerceAtLeast(0.dp)
            HorizontalPager(
                state = pager,
                pageSize = PageSize.Fixed(sleeve),
                pageSpacing = if (compact) Space.Small else Space.Medium,
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
                    compact,
                    Modifier
                        .size(sleeve)
                        .graphicsLayer {
                            // How far this sleeve is from the front, eased: 0 in front, 1 beside it and beyond.
                            val off = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceAtMost(1f)
                            val away = FastOutSlowInEasing.transform(off)
                            val shrink = 1f - look.shrink * away
                            scaleX = shrink
                            scaleY = shrink
                            alpha = 1f - look.dim * away
                            // Blurred beside the front (Android 12 and later; before that they're only dimmed).
                            val blur = look.blur.toPx() * away
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
                WholeWordsText(record.name, (if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge).copy(fontWeight = FontWeight.ExtraBold),
                    Modifier.testTag("crateName"), maxLines = 2)
                if (record.caption.isNotBlank()) {
                    Text(record.caption, style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("crateCaption"))
                }
            }
        }
    }
}

/** The size of the carousel of the row being drawn (Arrange Home, the owner 20 Nov). */
val LocalCrateSize = androidx.compose.runtime.compositionLocalOf { app.siansa.data.CrateSize.BIG }

/**
 * The small carousel (the owner, 20 Nov): a strip of squares flicked like any row of covers, settling on a square's
 * edge, every one in full and each with its own name and line, under it or on it ([namesOnCover]). No front one,
 * so nothing is dimmed; the same sleeves, fade and words as the big carousel, a size smaller.
 */
@Composable
private fun CrateStrip(records: List<CrateRecord>, namesOnCover: Boolean, modifier: Modifier) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.foundation.lazy.LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth().testTag("recordCrate"),
        contentPadding = PaddingValues(horizontal = Space.Page),
        horizontalArrangement = Arrangement.spacedBy(Space.Medium),
        flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(state),
    ) {
        items(records.size, key = { records[it].key }) { i ->
            val record = records[i]
            Column(
                Modifier
                    .width(CRATE_STRIP)
                    .clickable(onClickLabel = "Open ${record.name}", onClick = record.onOpen)
                    .semantics { contentDescription = record.name },
                verticalArrangement = Arrangement.spacedBy(Space.Small),
            ) {
                Sleeve(record, namesOnCover, true, Modifier.size(CRATE_STRIP))
                if (!namesOnCover) {
                    // Three lines on a narrow square, so a long name keeps its size before a word is cut.
                    WholeWordsText(record.name, MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 3)
                    if (record.caption.isNotBlank()) {
                        Text(record.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** A square of the small carousel: three and a bit in view on a phone. */
private val CRATE_STRIP: Dp = 112.dp

/** The two carousels' sizes: the front sleeve's part of the width and most, and how its neighbours step back. */
private enum class CrateLook(val sleeve: Float, val most: Float, val shrink: Float, val dim: Float, val blur: Dp) {
    BIG(sleeve = 0.62f, most = 280f, shrink = 0.12f, dim = 0.3f, blur = 3.dp),
    // Three whole in view: the neighbours stay readable, so they step back a little less.
    COMPACT(sleeve = 0.34f, most = 170f, shrink = 0.14f, dim = 0.2f, blur = 1.5.dp),
}

/** Whether the carousel of the row being drawn puts each name on its cover (Arrange Home, the owner 19 Nov). */
val LocalCrateNamesOnCover = androidx.compose.runtime.compositionLocalOf { false }

/**
 * A sleeve: four covers in a square (one when there's one), the mix's colours behind. With [nameOnIt], its name and
 * line over a fade at its foot: clear for most of the cover, deepening smoothly to near black under the words, so
 * they read on any cover and the fade never shows a hard edge.
 */
@Composable
private fun Sleeve(record: CrateRecord, nameOnIt: Boolean, compact: Boolean, modifier: Modifier) {
    val colors = remember(record.key, record.colors) { record.colors ?: tileColors(record.key) }
    val shape = RoundedCornerShape(CRATE_CORNER)
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.first, colors.second)))
            .testTag("crateSleeve"),
    ) {
        val ids = remember(record.songs) { record.songs.map { it.id } }
        CardPicture(record.covers, null, colors, record.name, Modifier.fillMaxSize(), RectangleShape, 600, nameOver = nameOnIt, songIds = ids) { paint ->
            if (!nameOnIt) return@CardPicture
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    // Its own deep shade, easing in (the owner, 20 Nov: not a grey-black fade with a line).
                    .background(nameFade(paint.deep))
                    .padding(
                        start = if (compact) Space.Small else Space.Medium, end = if (compact) Space.Small else Space.Medium,
                        top = if (compact) Space.Section else Space.Large, bottom = if (compact) Space.Small else Space.Medium,
                    )
                    .testTag("crateOnCover"),
                verticalArrangement = Arrangement.spacedBy(Space.Tight),
            ) {
                WholeWordsText(record.name, (if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium).copy(fontWeight = FontWeight.ExtraBold, shadow = CRATE_SHADOW),
                    color = Color.White, maxLines = if (compact) 3 else 2)
                if (record.caption.isNotBlank()) {
                    Text(record.caption, style = (if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall).copy(shadow = CRATE_SHADOW), color = Color.White.copy(alpha = 0.82f),
                        // One line on a small sleeve, so the name keeps its size above it.
                        maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// A soft shadow under the words on a cover, for the odd light cover the fade doesn't darken enough.
private val CRATE_SHADOW = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 6f)

private val CRATE_CORNER: Dp = 6.dp
