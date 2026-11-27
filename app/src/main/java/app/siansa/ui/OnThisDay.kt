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
import androidx.compose.foundation.lazy.itemsIndexed
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
 * opens its list, as Your listening's Look back does (the owner, 27 Nov); held, it plays. The day's card is new each
 * morning: it has a ring.
 */
object OnThisDay {
    /** The kinds it shows, in its order: the day first, as it's the one that's new every morning. */
    val KINDS = listOf(TimeListKind.DAY, TimeListKind.YEAR_AGO, TimeListKind.LAST_YEAR)

    /** [card]'s lists On this day shows, in [KINDS]' order; none without a card. */
    fun lists(card: TimeCard?): List<TimeCardList> {
        val lists = card?.lists.orEmpty()
        return KINDS.mapNotNull { kind -> lists.firstOrNull { it.kind == kind.id } }
    }

    /** What a card says: its big [title], the [line] under it, and the small [kicker] a Wide card has over them. */
    data class Words(val title: String, val line: String, val kicker: String = "")

    fun words(list: TimeCardList, locale: Locale = Locale.getDefault()): Words = when (list.kind) {
        // "27 Nov 2025": the month short, so the date fits the card whole.
        TimeListKind.DAY.id -> Words("A year ago today", list.line.split(' ').let { p -> if (p.size == 3) "${p[0]} ${p[1].take(3)} ${p[2]}" else list.line }, "1 YEAR AGO TODAY")
        TimeListKind.YEAR_AGO.id -> Words(
            list.month?.let { "${Month.of(it).getDisplayName(TextStyle.FULL, locale)}, last year" } ?: list.title,
            songCount(list.songIds.size), "1 YEAR AGO",
        )
        else -> Words(list.title, songCount(list.songIds.size), "LAST YEAR")
    }

    /** The day's card: new each morning, so it has a ring. */
    fun isNew(list: TimeCardList): Boolean = list.kind == TimeListKind.DAY.id
}

/** The row: its title, then the cards side by side in its [style]. Nothing without a list. */
@Composable
fun OnThisDayRow(lists: List<TimeCardList>, onPlay: (TimeCardList) -> Unit, onOpen: (TimeCardList) -> Unit, style: MemoryStyle = MemoryStyle.STORIES) {
    if (lists.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag("onThisDay")) {
        SectionTitle("On this day")
        LazyRow(
            // Prints lean: room above and below inside the row, so their corners and shadows aren't cut off.
            contentPadding = if (style == MemoryStyle.PRINTS) PaddingValues(horizontal = Space.Page, vertical = Space.Small) else PaddingValues(horizontal = Space.Page),
            horizontalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            itemsIndexed(lists, key = { _, it -> it.kind }) { i, list ->
                when (style) {
                    MemoryStyle.STORIES -> MemoryCard(list, { onPlay(list) }, { onOpen(list) })
                    MemoryStyle.PRINTS -> PrintCard(list, i, { onPlay(list) }, { onOpen(list) })
                    MemoryStyle.WIDE -> WideCard(list, { onPlay(list) }, { onOpen(list) })
                }
            }
        }
    }
}

/**
 * On this day's styles (the owner, 27 Nov: "the 3 types from the mockup"), picked under its cog: Stories, tall cards
 * with a collage; Prints, prints on a table, the date typed under; Wide, one wide card at a time, its covers fanned.
 */
enum class MemoryStyle(val id: String, val label: String, val blurb: String) {
    STORIES("stories", "Stories", "Tall cards, a collage of covers on each"),
    PRINTS("prints", "Prints", "Like prints on a table, the date under each"),
    WIDE("wide", "Wide", "One wide card at a time, its covers fanned out");

    companion object {
        fun of(id: String?): MemoryStyle = entries.firstOrNull { it.id == id } ?: STORIES
    }
}

/** How a print leans, the same each time: one way, the other, a little the first again. */
internal fun printLean(i: Int): Float = listOf(-2f, 1.5f, -1f)[i % 3]

/** Prints: a cream print with a collage, slightly tilted, its name and line typed under it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PrintCard(list: TimeCardList, i: Int, onPlay: () -> Unit, onOpen: () -> Unit) {
    val words = OnThisDay.words(list)
    val (light, dark) = timeListColours(list.kind)
    val shape = RoundedCornerShape(PRINT_CORNER)
    Box(Modifier.rotate(printLean(i))) {
        Column(
            Modifier.size(PRINT_WIDTH, PRINT_HEIGHT)
                .shadow(MEMORY_SHADOW, shape)
                .clip(shape)
                .combinedClickable(onClickLabel = "Open ${words.title}", onClick = onOpen, onLongClickLabel = "Play ${words.title}", onLongClick = onPlay)
                .graphicsLayer {}
                .background(PRINT_PAPER)
                .padding(start = Space.Small, end = Space.Small, top = Space.Small, bottom = Space.Medium)
                .testTag("onThisDayCard"),
            verticalArrangement = Arrangement.spacedBy(Space.Small),
        ) {
            Box(Modifier.fillMaxWidth().size(PRINT_WIDTH, PRINT_PHOTO).clip(RoundedCornerShape(PRINT_CORNER)).background(Brush.linearGradient(listOf(light, dark)))) {
                Collage(list, Modifier.align(Alignment.Center).size(PRINT_COLLAGE), PRINT_COLLAGE)
            }
            Column(Modifier.padding(horizontal = Space.Tight)) {
                Text(words.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = PRINT_INK, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("onThisDayTitle"))
                Text(words.line, style = MaterialTheme.typography.labelSmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = PRINT_INK_SOFT, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (OnThisDay.isNew(list)) NewTag(Modifier.align(Alignment.TopEnd).offset(Space.Tight, -Space.Small))
    }
}

/** Wide: a wide card, its covers fanned at the right, the date at the top left, words and Play at its foot. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WideCard(list: TimeCardList, onPlay: () -> Unit, onOpen: () -> Unit) {
    val words = OnThisDay.words(list)
    val (light, dark) = timeListColours(list.kind)
    val shape = RoundedCornerShape(WIDE_CORNER)
    val new = OnThisDay.isNew(list)
    Box(
        Modifier.size(WIDE_WIDTH, WIDE_HEIGHT)
            .clip(shape)
            .then(if (new) Modifier.border(MEMORY_RING, MEMORY_ACCENT, shape) else Modifier)
            .combinedClickable(onClickLabel = "Open ${words.title}", onClick = onOpen, onLongClickLabel = "Play ${words.title}", onLongClick = onPlay)
            .graphicsLayer {}
            .background(Brush.radialGradient(listOf(light, dark, MEMORY_FOOT), center = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f), radius = 900f))
            .testTag("onThisDayCard"),
    ) {
        // The covers fanned at the right, the last on top.
        val covers = list.covers.take(3)
        val draw = LocalCoverDraw.current
        covers.forEachIndexed { n, cover ->
            val fromRight = (covers.size - 1 - n)
            draw(cover, COVER_PX / 2,
                Modifier.align(Alignment.TopEnd).padding(top = Space.Page, end = Space.Page).offset(x = -WIDE_FAN_STEP * fromRight)
                    .size(WIDE_COVER).rotate(WIDE_LEANS[(n + 3 - covers.size) % 3]).shadow(MEMORY_SHADOW, RoundedCornerShape(MEMORY_COVER_CORNER)),
                RoundedCornerShape(MEMORY_COVER_CORNER), null)
        }
        // The date on a small chip: "27 NOV", "NOV 2025", "2025".
        Text(
            list.tag.replace(' ', '\n'), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = Color.White,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 14.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(Space.Page).clip(RoundedCornerShape(MEMORY_COVER_CORNER))
                .background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = Space.Small, vertical = Space.Tight),
        )
        Box(Modifier.align(Alignment.BottomCenter).size(WIDE_WIDTH, WIDE_FADE)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(Space.Page), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
            Column(Modifier.weight(1f)) {
                Text(words.kicker, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                Text(words.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("onThisDayTitle"))
                Text(words.line, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RoundPlayButton(false, "Play ${words.title}", WIDE_PLAY, WIDE_PLAY_ICON, Modifier.testTag("onThisDayPlay"), onClick = onPlay)
        }
    }
}

/** The amber NEW on the day's card. */
@Composable
private fun NewTag(modifier: Modifier = Modifier) {
    Text(
        "NEW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MEMORY_ON_ACCENT, letterSpacing = 1.sp,
        modifier = modifier.clip(RoundedCornerShape(MEMORY_CORNER)).background(MEMORY_ACCENT)
            .padding(horizontal = Space.Small, vertical = Space.Tight).testTag("onThisDayNew"),
    )
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
            .combinedClickable(onClickLabel = "Open ${words.title}", onClick = onOpen, onLongClickLabel = "Play ${words.title}", onLongClick = onPlay)
            // Its own layer: the collage is drawn once and kept while the row scrolls.
            .graphicsLayer {}
            .background(Brush.verticalGradient(listOf(light, dark, MEMORY_FOOT)))
            .testTag("onThisDayCard"),
    ) {
        Collage(list, Modifier.align(Alignment.TopCenter).padding(top = Space.Large).size(MEMORY_COLLAGE), MEMORY_COLLAGE)
        if (new) NewTag(Modifier.align(Alignment.TopStart).padding(Space.Medium))
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
private fun Collage(list: TimeCardList, modifier: Modifier, size: androidx.compose.ui.unit.Dp) {
    val covers = list.covers.take(4)
    val draw = LocalCoverDraw.current
    Box(modifier) {
        when {
            covers.isEmpty() -> Unit
            covers.size == 1 -> draw(covers[0], COVER_PX, Modifier.align(Alignment.Center).size(size * 0.8f).shadow(MEMORY_SHADOW, RoundedCornerShape(MEMORY_COVER_CORNER)), RoundedCornerShape(MEMORY_COVER_CORNER), null)
            list.kind == TimeListKind.LAST_YEAR.id && covers.size == 4 -> Column(Modifier.fillMaxSize().shadow(MEMORY_SHADOW, RoundedCornerShape(MEMORY_COVER_CORNER)).clip(RoundedCornerShape(MEMORY_COVER_CORNER))) {
                for (row in 0..1) Row(Modifier.weight(1f)) {
                    for (col in 0..1) draw(covers[row * 2 + col], COVER_PX / 2, Modifier.weight(1f).fillMaxSize(), RectangleShape, null)
                }
            }
            else -> covers.forEachIndexed { i, cover -> Scattered(cover, i, covers.size, size) }
        }
    }
}

/** One cover of a scattered collage: where it sits and how it leans, the same every time. */
@Composable
private fun BoxScope.Scattered(cover: String, i: Int, of: Int, collage: androidx.compose.ui.unit.Dp) {
    val spot = SCATTER[of.coerceIn(2, 4) - 2][i]
    val size = collage * spot.size
    LocalCoverDraw.current(
        cover, COVER_PX / 2,
        Modifier.align(Alignment.TopStart).offset(collage * spot.x, collage * spot.y).size(size).rotate(spot.lean)
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
private val PRINT_WIDTH = 148.dp
private val PRINT_HEIGHT = 214.dp
private val PRINT_PHOTO = 132.dp
private val PRINT_COLLAGE = 104.dp
private val PRINT_CORNER = 3.dp
private val PRINT_PAPER = Color(0xFFF2EEE6)
private val PRINT_INK = Color(0xFF1A1712)
private val PRINT_INK_SOFT = Color(0xFF5E564A)
private val WIDE_WIDTH = 300.dp
private val WIDE_HEIGHT = 180.dp
private val WIDE_CORNER = 16.dp
private val WIDE_COVER = 84.dp
private val WIDE_FAN_STEP = 28.dp
private val WIDE_LEANS = listOf(-12f, -2f, 9f)
private val WIDE_FADE = 96.dp
private val WIDE_PLAY = 44.dp
private val WIDE_PLAY_ICON = 22.dp
/** Under the card's colours: near black, as Your listening's card is dark in both themes. */
private val MEMORY_FOOT = Color(0xFF141012)
/** The accent on it: Siansa's amber, in both themes, as the card is dark in both. */
private val MEMORY_ACCENT = Color(0xFFFFB547)
private val MEMORY_ON_ACCENT = Color(0xFF1A1206)
