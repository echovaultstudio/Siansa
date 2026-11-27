package app.siansa.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.siansa.data.picture
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

    /**
     * What [list]'s card pictures: its artists' pictures when [artists] (the owner, 27 Nov: "allow choosing via toggle
     * to show artist or album images") and it has artists in the library, else its album covers.
     */
    fun pictures(list: TimeCardList, artists: Boolean): Pictures {
        val faces = if (artists) list.artists.filter { it.id != null || it.coverArt != null }
            .map { app.siansa.data.Artist(it.id ?: it.name, it.name, coverArt = it.coverArt, imageUrl = it.imageUrl).picture } else emptyList()
        return if (faces.isNotEmpty()) Pictures(faces, round = true) else Pictures(list.covers, round = false)
    }

    /** A card's pictures, and whether they're artists (round) or albums. */
    data class Pictures(val ids: List<String>, val round: Boolean)

    /** The print after [at] of [of]: after the last, the first again. */
    fun nextPrint(at: Int, of: Int): Int = if (of <= 0) 0 else (at + 1) % of

    /** Beside the pile of prints: "3 MEMORIES", "1 MEMORY". */
    fun howMany(n: Int): String = if (n == 1) "1 MEMORY" else "$n MEMORIES"

    /** Which come next, after the print on top: "Swipe it away for the next: November, last year, then Your 2025." */
    fun comesNext(lists: List<TimeCardList>, at: Int, locale: Locale = Locale.getDefault()): String {
        val rest = (1 until lists.size).map { words(lists[(at + it) % lists.size], locale).title }
        return "Swipe it away for the next: " + when (rest.size) {
            0 -> ""
            1 -> rest[0]
            else -> rest.dropLast(1).joinToString(", ") + ", then " + rest.last()
        } + "."
    }

    /** A print's line: the day's date with how many songs, or how many songs. */
    fun printLine(list: TimeCardList, locale: Locale = Locale.getDefault()): String {
        val line = words(list, locale).line
        return if (list.kind == TimeListKind.DAY.id) "$line · ${songCount(list.songIds.size)}" else line
    }

    /** The day's card: new each morning, so it has a ring. */
    fun isNew(list: TimeCardList): Boolean = list.kind == TimeListKind.DAY.id
}

/** The row: its title, then the cards side by side in its [style]. Nothing without a list. */
@Composable
fun OnThisDayRow(
    lists: List<TimeCardList>,
    onPlay: (TimeCardList) -> Unit,
    onOpen: (TimeCardList) -> Unit,
    style: MemoryStyle = MemoryStyle.STORIES,
    artists: Boolean = false,
    picture: MemoryPicture = MemoryPicture.COLLAGE,
) {
    if (lists.isEmpty()) return
    androidx.compose.runtime.CompositionLocalProvider(LocalMemoryArtists provides artists, LocalMemoryPicture provides picture) {
    Column(Modifier.fillMaxWidth().testTag("onThisDay")) {
        SectionTitle("On this day")
        if (style == MemoryStyle.PRINTS) PrintStack(lists, onPlay, onOpen) else LazyRow(
            contentPadding = PaddingValues(horizontal = Space.Page),
            horizontalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            items(lists, key = { it.kind }) { list ->
                if (style == MemoryStyle.WIDE) WideCard(list, { onPlay(list) }, { onOpen(list) })
                else MemoryCard(list, { onPlay(list) }, { onOpen(list) })
            }
        }
    }
    }
}

/** Whether On this day pictures its artists rather than its albums ([OnThisDay.pictures]). */
private val LocalMemoryArtists = androidx.compose.runtime.staticCompositionLocalOf { false }

/** How On this day lays out its pictures ([MemoryPicture]). */
private val LocalMemoryPicture = androidx.compose.runtime.staticCompositionLocalOf { MemoryPicture.COLLAGE }

/** A picture's shape: an artist's is round, an album's a rounded square. */
private fun pictureShape(round: Boolean): androidx.compose.ui.graphics.Shape =
    if (round) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(MEMORY_COVER_CORNER)

/**
 * On this day's styles (the owner, 27 Nov: "the 3 types from the mockup"), picked under its cog: Stories, tall cards
 * with a collage; Prints, prints on a table, the date typed under; Wide, one wide card at a time, its covers fanned.
 */
enum class MemoryStyle(val id: String, val label: String, val blurb: String) {
    STORIES("stories", "Stories", "Tall cards, a collage of covers on each"),
    PRINTS("prints", "Prints", "A stack of prints: swipe the top one away for the next"),
    WIDE("wide", "Wide", "One wide card at a time, its covers fanned out");

    companion object {
        fun of(id: String?): MemoryStyle = entries.firstOrNull { it.id == id } ?: STORIES
    }
}

/**
 * How a card's pictures are laid out (the owner, 27 Nov: "not just the mosaic pictures but some options"), for
 * covers and artists alike: [COLLAGE] scattered at small angles (a year's covers two by two), [GRID] four two by
 * two, [ONE] the most played alone and big, [FAN] three fanned out side by side.
 */
enum class MemoryPicture(val id: String, val label: String) {
    COLLAGE("collage", "Collage"),
    GRID("grid", "Grid"),
    ONE("one", "Top one"),
    FAN("fan", "Fan");

    companion object {
        fun of(id: String?): MemoryPicture = entries.firstOrNull { it.id == id } ?: COLLAGE
    }
}

/**
 * Prints (the owner, 27 Nov: "P3 looks awesome"): one pile of prints, the top one in front and the next ones' paper
 * peeking out under it. Swipe the top print away, either way, for the next; after the last comes the first again.
 * Beside the pile: how many memories, which comes next, and a dot for each.
 */
@Composable
private fun PrintStack(lists: List<TimeCardList>, onPlay: (TimeCardList) -> Unit, onOpen: (TimeCardList) -> Unit) {
    var top by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val at = top % lists.size
    val list = lists[at]
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val shift = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val away = with(density) { PRINT_SWIPE.toPx() }
    val next: () -> Unit = {
        top = OnThisDay.nextPrint(at, lists.size)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.Page).testTag("onThisDayStack"),
        horizontalArrangement = Arrangement.spacedBy(Space.Page),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The pile: room round the print for the ones under it to peek out at their angles.
        Box(Modifier.padding(vertical = Space.Medium)) {
            // The paper of the next ones under it, at angles, as tall as the print on top.
            for (n in (lists.size - 1).coerceAtMost(2) downTo 1) {
                Box(Modifier.matchParentSize().padding(horizontal = Space.Medium).rotate(PRINT_UNDER[n - 1])
                    .shadow(MEMORY_SHADOW, RoundedCornerShape(PRINT_CORNER)).background(PRINT_UNDER_PAPER[n - 1]))
            }
            Box(
                Modifier.padding(horizontal = Space.Medium)
                    .graphicsLayer { translationX = shift.value; rotationZ = shift.value / away * PRINT_SWIPE_LEAN }
                    .pointerInput(at) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (kotlin.math.abs(shift.value) > away / 3) {
                                        shift.animateTo(kotlin.math.sign(shift.value) * away * 2)
                                        next()
                                    }
                                    shift.snapTo(0f)
                                }
                            },
                            onDragCancel = { scope.launch { shift.animateTo(0f) } },
                        ) { change, dx -> change.consume(); scope.launch { shift.snapTo(shift.value + dx) } }
                    },
            ) { PrintCard(list, lists.size, { onPlay(list) }, { onOpen(list) }, next) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
            // The theme's amber: Siansa's bright amber doesn't read on a light page.
            Text(OnThisDay.howMany(lists.size), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary)
            if (lists.size > 1) {
                Text(OnThisDay.comesNext(lists, at), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("onThisDayNext"))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.Tight), verticalAlignment = Alignment.CenterVertically) {
                    lists.indices.forEach { n ->
                        Box(Modifier.size(if (n == at) PRINT_DOT_ON else PRINT_DOT, PRINT_DOT_HEIGHT).clip(RoundedCornerShape(PRINT_DOT_HEIGHT))
                            .background(if (n == at) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)))
                    }
                }
            }
        }
    }
}

/** The print on top: a cream print, its picture square, its name written under it and the date or how many songs. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PrintCard(list: TimeCardList, of: Int, onPlay: () -> Unit, onOpen: () -> Unit, onNext: () -> Unit) {
    val words = OnThisDay.words(list)
    val (light, dark) = timeListColours(list.kind)
    val shape = RoundedCornerShape(PRINT_CORNER)
    val new = OnThisDay.isNew(list)
    Box {
        Column(
            // As tall as its words need, at a larger text size too.
            Modifier.width(PRINT_WIDTH).heightIn(min = PRINT_HEIGHT)
                .shadow(MEMORY_SHADOW, shape)
                .clip(shape)
                .then(if (new) Modifier.border(MEMORY_RING, MEMORY_ACCENT, shape) else Modifier)
                .memoryTap(words.title, onPlay, onOpen)
                // TalkBack can't swipe the pile: the next print is an action of its own.
                .then(if (of > 1) Modifier.semantics { customActions = listOf(CustomAccessibilityAction("Next memory") { onNext(); true }) } else Modifier)
                .graphicsLayer {}
                .background(PRINT_PAPER)
                .padding(start = Space.Small, end = Space.Small, top = Space.Small, bottom = Space.Small)
                .testTag("onThisDayCard"),
            verticalArrangement = Arrangement.spacedBy(Space.Small),
        ) {
            Box(Modifier.size(PRINT_PHOTO).clip(RoundedCornerShape(PRINT_CORNER)).background(Brush.linearGradient(listOf(light, dark)))) {
                Collage(list, Modifier.align(Alignment.Center).size(PRINT_COLLAGE), PRINT_COLLAGE)
            }
            Column(Modifier.padding(horizontal = Space.Tight)) {
                Text(words.title, style = MaterialTheme.typography.titleMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Cursive,
                    fontWeight = FontWeight.Bold, color = PRINT_INK, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("onThisDayTitle"))
                Text(OnThisDay.printLine(list), style = MaterialTheme.typography.labelSmall, color = PRINT_INK_SOFT, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (new) NewTag(Modifier.align(Alignment.TopEnd).offset(Space.Tight, -Space.Small))
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
            .memoryTap(words.title, onPlay, onOpen)
            .graphicsLayer {}
            .background(Brush.radialGradient(listOf(light, dark, MEMORY_FOOT), center = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f), radius = 900f))
            .testTag("onThisDayCard"),
    ) {
        // The covers fanned at the right, the last on top.
        val pictures = OnThisDay.pictures(list, LocalMemoryArtists.current)
        val covers = pictures.ids.take(3)
        val shape = pictureShape(pictures.round)
        val draw = LocalCoverDraw.current
        // A grid or the top one sits square at the right; a collage or a fan is its own fan.
        val layout = LocalMemoryPicture.current
        if (layout == MemoryPicture.GRID || layout == MemoryPicture.ONE) {
            Collage(list, Modifier.align(Alignment.TopEnd).padding(top = Space.Medium, end = Space.Page).size(WIDE_PICTURE), WIDE_PICTURE)
        } else covers.forEachIndexed { n, cover ->
            val fromRight = (covers.size - 1 - n)
            draw(cover, COVER_PX / 2,
                Modifier.align(Alignment.TopEnd).padding(top = Space.Page, end = Space.Page).offset(x = -WIDE_FAN_STEP * fromRight)
                    .size(WIDE_COVER).rotate(WIDE_LEANS[(n + 3 - covers.size) % 3]).shadow(MEMORY_SHADOW, shape),
                shape, null)
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
            .memoryTap(words.title, onPlay, onOpen)
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
 * A memory's tap: it opens its list and plays when held, or the other way round with Play on tap on (the owner, 27 Nov).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.memoryTap(title: String, onPlay: () -> Unit, onOpen: () -> Unit): Modifier =
    if (LocalTapPlays.current) combinedClickable(onClickLabel = "Play $title", onClick = onPlay, onLongClickLabel = "Open $title", onLongClick = onOpen)
    else combinedClickable(onClickLabel = "Open $title", onClick = onOpen, onLongClickLabel = "Play $title", onLongClick = onPlay)

/**
 * The card's covers: a year's top four two by two, a day's or a month's scattered at small angles; one cover alone
 * sits big. Drawn through [LocalCoverDraw], small ones the phone already has.
 */
@Composable
private fun Collage(list: TimeCardList, modifier: Modifier, size: androidx.compose.ui.unit.Dp) {
    val pictures = OnThisDay.pictures(list, LocalMemoryArtists.current)
    val layout = LocalMemoryPicture.current
    val covers = pictures.ids.take(if (layout == MemoryPicture.FAN) 3 else 4)
    val shape = pictureShape(pictures.round)
    val draw = LocalCoverDraw.current
    Box(modifier) {
        when {
            covers.isEmpty() -> Unit
            covers.size == 1 || layout == MemoryPicture.ONE ->
                draw(covers[0], COVER_PX, Modifier.align(Alignment.Center).size(size * 0.86f).shadow(MEMORY_SHADOW, shape), shape, null)
            layout == MemoryPicture.FAN -> covers.forEachIndexed { n, cover ->
                // Side by side across the middle, leaning out, the first on top.
                val at = n - (covers.size - 1) / 2f
                draw(cover, COVER_PX / 2,
                    Modifier.align(Alignment.Center).offset(x = size * 0.22f * at).size(size * 0.5f).rotate(at * 9f).zIndex(-n.toFloat())
                        .shadow(MEMORY_SHADOW, shape), shape, null)
            }
            // Four two by two: in a grid, round ones each in its corner.
            layout == MemoryPicture.GRID && covers.size == 4 && pictures.round -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                for (row in 0..1) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Space.Tight)) {
                    for (col in 0..1) draw(covers[row * 2 + col], COVER_PX / 2, Modifier.weight(1f).fillMaxSize().shadow(MEMORY_SHADOW, shape), shape, null)
                }
            }
            // Artists are round: scattered in a collage, never squared up.
            !pictures.round && covers.size == 4 && (layout == MemoryPicture.GRID || list.kind == TimeListKind.LAST_YEAR.id) -> Column(Modifier.fillMaxSize().shadow(MEMORY_SHADOW, RoundedCornerShape(MEMORY_COVER_CORNER)).clip(RoundedCornerShape(MEMORY_COVER_CORNER))) {
                for (row in 0..1) Row(Modifier.weight(1f)) {
                    for (col in 0..1) draw(covers[row * 2 + col], COVER_PX / 2, Modifier.weight(1f).fillMaxSize(), RectangleShape, null)
                }
            }
            else -> covers.forEachIndexed { i, cover -> Scattered(cover, i, covers.size, size, shape) }
        }
    }
}

/** One cover of a scattered collage: where it sits and how it leans, the same every time. */
@Composable
private fun BoxScope.Scattered(cover: String, i: Int, of: Int, collage: androidx.compose.ui.unit.Dp, shape: androidx.compose.ui.graphics.Shape) {
    val spot = SCATTER[of.coerceIn(2, 4) - 2][i]
    val size = collage * spot.size
    LocalCoverDraw.current(
        cover, COVER_PX / 2,
        Modifier.align(Alignment.TopStart).offset(collage * spot.x, collage * spot.y).size(size).rotate(spot.lean)
            .shadow(MEMORY_SHADOW, shape),
        shape, null,
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
private val PRINT_WIDTH = 150.dp
private val PRINT_HEIGHT = 190.dp
private val PRINT_PHOTO = 134.dp
private val PRINT_COLLAGE = 108.dp
/** How the prints under the top one lean, and their paper, a shade darker the further down. */
private val PRINT_UNDER = listOf(-5f, 7f)
private val PRINT_UNDER_PAPER = listOf(Color(0xFFEFEBE3), Color(0xFFE9E4DA))
/** How far a swiped print goes, and how much it leans on the way. */
private val PRINT_SWIPE = 220.dp
private const val PRINT_SWIPE_LEAN = 12f
private val PRINT_DOT = 8.dp
private val PRINT_DOT_ON = 18.dp
private val PRINT_DOT_HEIGHT = 4.dp
private val PRINT_CORNER = 3.dp
private val PRINT_PAPER = Color(0xFFF2EEE6)
private val PRINT_INK = Color(0xFF1A1712)
private val PRINT_INK_SOFT = Color(0xFF5E564A)
private val WIDE_WIDTH = 300.dp
private val WIDE_HEIGHT = 180.dp
private val WIDE_CORNER = 16.dp
private val WIDE_COVER = 84.dp
private val WIDE_PICTURE = 88.dp
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
