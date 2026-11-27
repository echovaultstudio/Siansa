package app.siansa.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.siansa.mix.TimeCard
import app.siansa.mix.TimeCardList
import app.siansa.mix.TimeListKind
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Home's On this day (the owner, 27 Nov: "similar to how photo apps show photos from the year before"): a row of tall
 * memory cards, each a collage of its covers, from the look back lists Your listening's card already keeps. A tap
 * plays one, held it opens its list. The day's card is new each morning: it has a ring.
 */
object OnThisDay {
    /** The kinds it shows, in its order: the day first, as it's the one that's new every morning. */
    val KINDS = listOf(TimeListKind.DAY, TimeListKind.YEAR_AGO, TimeListKind.LAST_YEAR)

    /** [card]'s lists On this day shows, in [KINDS]' order; none without a card. */
    fun lists(card: TimeCard?): List<TimeCardList> {
        val lists = card?.lists.orEmpty()
        return KINDS.mapNotNull { kind -> lists.firstOrNull { it.kind == kind.id } }
    }

    /** What a card says: its big [title] and the [line] under it. */
    data class Words(val title: String, val line: String)

    fun words(list: TimeCardList, locale: Locale = Locale.getDefault()): Words = when (list.kind) {
        // "27 Nov 2025": the month short, so the date fits the card whole.
        TimeListKind.DAY.id -> Words("A year ago today", list.line.split(' ').let { p -> if (p.size == 3) "${p[0]} ${p[1].take(3)} ${p[2]}" else list.line })
        TimeListKind.YEAR_AGO.id -> Words(
            list.month?.let { "${Month.of(it).getDisplayName(TextStyle.FULL, locale)}, last year" } ?: list.title,
            songCount(list.songIds.size),
        )
        else -> Words(list.title, songCount(list.songIds.size))
    }

    /** The day's card: new each morning, so it has a ring. */
    fun isNew(list: TimeCardList): Boolean = list.kind == TimeListKind.DAY.id
}

/** The row: its title, then the cards side by side. Nothing without a list. */
@Composable
fun OnThisDayRow(lists: List<TimeCardList>, onPlay: (TimeCardList) -> Unit, onOpen: (TimeCardList) -> Unit) {
    if (lists.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag("onThisDay")) {
        SectionTitle("On this day")
        LazyRow(
            contentPadding = PaddingValues(horizontal = Space.Page),
            horizontalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            items(lists, key = { it.kind }) { list -> MemoryCard(list, { onPlay(list) }, { onOpen(list) }) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryCard(list: TimeCardList, onPlay: () -> Unit, onOpen: () -> Unit) {
    val words = OnThisDay.words(list)
    val (light, dark) = timeListColours(list.kind)
    val shape = RoundedCornerShape(MEMORY_CORNER)
    val new = OnThisDay.isNew(list)
    Box(
        Modifier.size(MEMORY_WIDTH, MEMORY_HEIGHT)
            .clip(shape)
            .then(if (new) Modifier.border(MEMORY_RING, MEMORY_ACCENT, shape) else Modifier)
            .combinedClickable(onClickLabel = "Play ${words.title}", onClick = onPlay, onLongClickLabel = "Open ${words.title}", onLongClick = onOpen)
            // Its own layer: the collage is drawn once and kept while the row scrolls.
            .graphicsLayer {}
            .background(Brush.verticalGradient(listOf(light, dark, MEMORY_FOOT)))
            .testTag("onThisDayCard"),
    ) {
        Collage(list, Modifier.align(Alignment.TopCenter).padding(top = Space.Large).size(MEMORY_COLLAGE))
        if (new) {
            Text(
                "NEW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MEMORY_ON_ACCENT, letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(Space.Medium).clip(RoundedCornerShape(MEMORY_CORNER)).background(MEMORY_ACCENT)
                    .padding(horizontal = Space.Small, vertical = Space.Tight).testTag("onThisDayNew"),
            )
        }
        // The words on a dark fade at the foot, so they read on any covers.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().size(MEMORY_WIDTH, MEMORY_FADE)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(Space.Medium), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
            Text(words.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2,
                overflow = TextOverflow.Ellipsis, lineHeight = 20.sp, modifier = Modifier.testTag("onThisDayTitle"))
            Text(words.line, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The card's covers: a year's top four two by two, a day's or a month's scattered at small angles; one cover alone
 * sits big. Drawn through [LocalCoverDraw], small ones the phone already has.
 */
@Composable
private fun Collage(list: TimeCardList, modifier: Modifier) {
    val covers = list.covers.take(4)
    val draw = LocalCoverDraw.current
    Box(modifier) {
        when {
            covers.isEmpty() -> Unit
            covers.size == 1 -> draw(covers[0], COVER_PX, Modifier.align(Alignment.Center).size(MEMORY_COLLAGE * 0.8f).shadow(MEMORY_SHADOW, RoundedCornerShape(MEMORY_COVER_CORNER)), RoundedCornerShape(MEMORY_COVER_CORNER), null)
            list.kind == TimeListKind.LAST_YEAR.id && covers.size == 4 -> Column(Modifier.fillMaxSize().shadow(MEMORY_SHADOW, RoundedCornerShape(MEMORY_COVER_CORNER)).clip(RoundedCornerShape(MEMORY_COVER_CORNER))) {
                for (row in 0..1) Row(Modifier.weight(1f)) {
                    for (col in 0..1) draw(covers[row * 2 + col], COVER_PX / 2, Modifier.weight(1f).fillMaxSize(), RectangleShape, null)
                }
            }
            else -> covers.forEachIndexed { i, cover -> Scattered(cover, i, covers.size) }
        }
    }
}

/** One cover of a scattered collage: where it sits and how it leans, the same every time. */
@Composable
private fun BoxScope.Scattered(cover: String, i: Int, of: Int) {
    val spot = SCATTER[of.coerceIn(2, 4) - 2][i]
    val size = MEMORY_COLLAGE * spot.size
    LocalCoverDraw.current(
        cover, COVER_PX / 2,
        Modifier.align(Alignment.TopStart).offset(MEMORY_COLLAGE * spot.x, MEMORY_COLLAGE * spot.y).size(size).rotate(spot.lean)
            .shadow(MEMORY_SHADOW, RoundedCornerShape(MEMORY_COVER_CORNER)),
        RoundedCornerShape(MEMORY_COVER_CORNER), null,
    )
}

/** Where a scattered cover sits, as shares of the collage, its size as a share, and its lean in degrees. */
private data class Spot(val x: Float, val y: Float, val size: Float, val lean: Float)

/** For two, three and four covers. */
private val SCATTER = listOf(
    listOf(Spot(0.02f, 0.08f, 0.6f, -8f), Spot(0.38f, 0.3f, 0.6f, 6f)),
    listOf(Spot(0.0f, 0.04f, 0.55f, -8f), Spot(0.45f, 0.0f, 0.53f, 7f), Spot(0.2f, 0.45f, 0.54f, 3f)),
    listOf(Spot(0.0f, 0.04f, 0.52f, -8f), Spot(0.46f, 0.0f, 0.5f, 7f), Spot(0.04f, 0.5f, 0.48f, 5f), Spot(0.48f, 0.47f, 0.47f, -4f)),
)

private val MEMORY_WIDTH = 132.dp
private val MEMORY_HEIGHT = 236.dp
private val MEMORY_COLLAGE = 112.dp
private val MEMORY_FADE = 110.dp
private val MEMORY_CORNER = 18.dp
private val MEMORY_COVER_CORNER = 6.dp
private val MEMORY_RING = 2.dp
private val MEMORY_SHADOW = 8.dp
private const val COVER_PX = 300
/** Under the card's colours: near black, as Your listening's card is dark in both themes. */
private val MEMORY_FOOT = Color(0xFF141012)
/** The accent on it: Siansa's amber, in both themes, as the card is dark in both. */
private val MEMORY_ACCENT = Color(0xFFFFB547)
private val MEMORY_ON_ACCENT = Color(0xFF1A1206)
