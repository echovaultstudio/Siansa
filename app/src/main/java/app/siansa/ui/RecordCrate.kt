package app.siansa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Mixes as records in a crate (the owner, 17 Nov: "a beautiful carousel card for them on Home that resembles flicking
 * through records"): the one in front stands up straight with its record slid half out of the sleeve; the ones behind
 * lean back and stack, as sleeves do when you flick through a crate. Swipe to flick; tap the front one to open it.
 * Under the crate: its name, what it is, and Play.
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
                pageSize = PageSize.Fixed(slot),
                contentPadding = PaddingValues(horizontal = side), // spacing: exact (the front sleeve in the middle)
                beyondViewportPageCount = 3,
                modifier = Modifier.fillMaxWidth().height(sleeve + CRATE_ROOM),
            ) { page ->
                val record = records[page]
                // How far this sleeve is from the front: 0 in front, 1 one along, negative to the left.
                val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
                val far = abs(off).coerceAtMost(2f)
                val front = (1f - far).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .zIndex(-far)
                        .requiredSize(sleeve, sleeve + CRATE_ROOM)
                        .graphicsLayer {
                            // Leaning back into the crate, away from the one in front.
                            rotationY = (off * CRATE_TILT).coerceIn(-CRATE_TILT * 1.6f, CRATE_TILT * 1.6f)
                            cameraDistance = 14f * density
                            val shrink = 1f - CRATE_SHRINK * far
                            scaleX = shrink
                            scaleY = shrink
                            translationY = CRATE_SINK.toPx() * far
                            alpha = 1f - 0.25f * (far - 1f).coerceAtLeast(0f)
                        },
                    contentAlignment = Alignment.TopStart,
                ) {
                    // The record, slid out to the right of the one in front.
                    Vinyl(
                        record.key,
                        Modifier
                            .size(sleeve * 0.94f)
                            .offset(x = sleeve * 0.06f + sleeve * CRATE_SLIDE * front, y = sleeve * 0.03f)
                            .graphicsLayer { rotationZ = 160f * front },
                    )
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
            Column(Modifier.weight(1f).clickable(onClick = record.onOpen), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                Text(record.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("crateName"))
                Text(record.caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
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

/** A record: black, with grooves, and a label in the mix's colours. */
@Composable
private fun Vinyl(key: String, modifier: Modifier) {
    val colors = remember(key) { tileColors(key) }
    Canvas(modifier.aspectRatio(1f).testTag("crateVinyl")) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Color(0xFF0E0E10), r, c)
        for (i in 1..9) drawCircle(Color.White.copy(alpha = 0.05f), r * (0.42f + i * 0.06f), c, style = Stroke(width = 1.dp.toPx()))
        // A sheen across the grooves.
        drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.White.copy(alpha = 0.06f)),
            start = Offset(0f, 0f), end = Offset(size.width, size.height)), r, c)
        drawCircle(Brush.linearGradient(listOf(colors.first, colors.second), start = c - Offset(r, r) * 0.3f, end = c + Offset(r, r) * 0.3f), r * 0.34f, c)
        drawCircle(Color(0xFF0E0E10), r * 0.04f, c)
    }
}

private const val CRATE_SLEEVE = 0.62f // of the width
private const val CRATE_SLEEVE_MOST = 280f // dp
private const val CRATE_OVERLAP = 0.42f // of a sleeve: how far the ones behind tuck under
private const val CRATE_TILT = 38f // degrees a sleeve one along leans back
private const val CRATE_SHRINK = 0.12f
private const val CRATE_SLIDE = 0.30f // of a sleeve: how far the front record slides out
private val CRATE_ROOM: Dp = 20.dp // under the sleeves, for the ones behind to sink into
private val CRATE_SINK: Dp = 8.dp
private val CRATE_CORNER: Dp = 6.dp
private val CRATE_SHADOW: Dp = 12.dp
internal val CRATE_PLAY: Dp = 52.dp
internal val CRATE_PLAY_ICON: Dp = 30.dp
