package app.siansa.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.siansa.data.HomeLook
import app.siansa.data.Song

/**
 * One thing on a row of Home (an album, a mix, a radio, a book…), in a form every look can draw (issue #655):
 * its title and a line under it, a cover or else a colour gradient, and what a tap opens.
 */
data class HomeItem(
    val key: String,
    val title: String,
    val subtitle: String = "",
    val coverArt: String? = null,
    val colors: Pair<Color, Color> = tileColors(key),
    /** A round cover (an artist). */
    val round: Boolean = false,
    val open: () -> Unit,
    /** Plays this item (an album, a mix): Cover rows' cover does it. Without one, the cover opens the item. */
    val play: (() -> Unit)? = null,
    /** A third line for Cover rows ("11 songs · 2024"); the subtitle is the small line above the title. */
    val meta: String = "",
    /** How far through, 0..1, for a book or an episode: a bar along the cover in Cover rows. */
    val progress: Float? = null,
    /** A mix's covers: in a cover grid it shows four of them, two by two, as its own tiles do (#1263). */
    val mosaic: List<String?> = emptyList(),
    /** Its songs, when known: a hero's Play and Shuffle play them (the owner, 20 Nov). */
    val songs: List<Song> = emptyList(),
    /** Its songs' ids, for its colours by mood (the owner, 20 Nov); its [songs]' when not given. */
    val songIdList: List<String> = emptyList(),
) {
    val songIds: List<String> get() = songIdList.ifEmpty { songs.map { it.id } }
    val artists: String get() = artistLine(songs)
}

/**
 * A row of Home in one of the custom cards' looks (issue #655), for rows that aren't [HomeLook.AS_IS]:
 * - Tiles: a row of covers to swipe through, [openAll] behind the title;
 * - Banner: one wide card with the first covers fanned at the right, and Play (or Open);
 * - List: the first [LIST] listed, [openAll] behind the title;
 * - Play button: a slim card that opens the row; its ▶ plays it ([play]). Without [play] it's an arrow (issue #730).
 * [play] plays the row's music; [songs] (when known) lets the button show ⏸ while they play. Without [openAll]
 * the first item stands in for the row.
 */
@Composable
fun HomeLookRow(
    look: HomeLook,
    id: String,
    title: String,
    items: List<HomeItem>,
    openAll: (() -> Unit)? = null,
    play: (() -> Unit)? = null,
    songs: List<Song> = emptyList(),
    // With [HomeLook.GRID]: how many tiles (2 to 8, Arrange Home's choice for the row).
    gridSize: Int = LocalGridSize.current,
) {
    if (items.isEmpty()) return
    // Without their lines when the row hides them (the owner, 22 Nov), whichever look draws them.
    @Suppress("NAME_SHADOWING")
    val items = if (LocalCardLines.current) items else androidx.compose.runtime.remember(items) { items.map { it.copy(subtitle = "", meta = "") } }
    val open = openAll ?: items.first().open
    when (look) {
        HomeLook.AS_IS -> Shelf(title, items, key = { it.key }, onOpenAll = openAll) { item -> HomeItemTile(item) }
        // Tiles take the row's Size (the owner, 21 Nov: every option for every card type).
        HomeLook.TILES -> { val tile = tileWidth(LocalCrateSize.current); Shelf(title, items, key = { it.key }, onOpenAll = openAll) { item -> HomeItemTile(item, tile) } }
        HomeLook.BANNER -> if (LocalLookVariant.current?.hero == true) {
            val graph = LocalGraph.current
            val first = items.first()
            val tint = rememberHeaderTint(first.mosaic.firstOrNull { it != null } ?: first.coverArt, first.title)
            // With cards (the owner, 22 Nov): the row's next three or four at the hero's foot.
            val under = items.drop(1).take(LocalLookVariant.current?.cardsUnder ?: 0)
            HomeHero(title, first, play, songs, tint, under) { list, shuffle -> graph.player.play(list, shuffle = shuffle) }
        }
            else HomeBanner(id, title, items, open, play, songs)
        HomeLook.LIST -> HomeList(title, items, open)
        HomeLook.BUTTON -> HomeButton(id, title, items, open, play, songs)
        HomeLook.COVER_ROWS -> HomeCoverRows(title, items, open)
        HomeLook.GRID -> TileGrid(title, items.map { GridTile(it.key, it.title, it.coverArt, it.colors, onClick = it.open, subtitle = it.subtitle, songIds = it.songIds, covers = it.mosaic) }, gridSize, openAll)
        HomeLook.CAROUSEL -> HomeCarouselLook(title, items)
        HomeLook.COVER_GRID -> if (LocalLookVariant.current == app.siansa.data.LookVariant.FEATURED) HomeFeaturedGrid(title, items, openAll)
            else HomeCoverGrid(title, items, LocalCoverGridSize.current, openAll)
    }
}

/** The columns and rows of the row being drawn as a cover grid (#1263): Arrange Home's choice for it. */
val LocalCoverGridSize = androidx.compose.runtime.compositionLocalOf { app.siansa.data.CoverGridSize.DEFAULT }

/**
 * Any row as a cover grid (#1263, the owner, 19 Nov): its first [size] cards in [CoverGridSize.columns] columns and
 * [CoverGridSize.rows] rows across the screen, each a square as wide as a column, with its own picture: an album its
 * cover, a mix four of its covers, an artist its round photo, one without a cover its colours. Names under them up to
 * three columns; more make the squares too small, and the picture says it ([contentDescription]).
 */
@Composable
fun HomeCoverGrid(title: String, items: List<HomeItem>, size: app.siansa.data.CoverGridSize, onOpenAll: (() -> Unit)? = null) {
    val shown = items.take(size.count)
    if (shown.isEmpty()) return
    Column(Modifier.testTag("homeCoverGrid")) {
        if (onOpenAll != null && LocalSectionTitleShown.current) {
            SectionTitle(title, Modifier.clickable(onClickLabel = "Show all", onClick = onOpenAll))
        } else SectionTitle(title)
        Column(Modifier.padding(horizontal = Space.Page), verticalArrangement = Arrangement.spacedBy(if (size.named) Space.Medium else Space.Small)) {
            shown.chunked(size.columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                    row.forEach { item ->
                        // The Names choice (the owner, 21 Nov), else names under up to three columns.
                        val named = when (LocalCardNames.current) { app.siansa.data.CardNames.UNDER -> true; app.siansa.data.CardNames.ON -> false; else -> size.named }
                        CoverGridCell(item, named, Modifier.weight(1f))
                    }
                    repeat(size.columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * A cover grid with one featured (the owner, 20 Nov): the row's first card two by two, its name and line on it over a
 * soft fade, and the next five small around it (two beside it, three under), each named on its cover.
 */
@Composable
fun HomeFeaturedGrid(title: String, items: List<HomeItem>, onOpenAll: (() -> Unit)? = null) {
    if (items.isEmpty()) return
    val first = items.first()
    val rest = items.drop(1).take(FEATURED_AROUND)
    Column(Modifier.testTag("homeFeaturedGrid")) {
        if (onOpenAll != null && LocalSectionTitleShown.current) {
            SectionTitle(title, Modifier.clickable(onClickLabel = "Show all", onClick = onOpenAll))
        } else SectionTitle(title)
        // Every gap the same (the owner, 22 Nov): the squares are sized from the three-column cell, the featured one two
        // cells and a gap, so it's as tall as the two beside it and their gap. Shared by weight (2 to 1) it came out
        // shorter, and the gap under it wider than the others.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Space.Page)) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val gapPx = with(density) { Space.Tight.roundToPx() }
            val (cellPx, bigPx) = featuredSquares(constraints.maxWidth, gapPx)
            val cell = with(density) { cellPx.toDp() }
            val big = with(density) { bigPx.toDp() }
            Column(verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.Tight)) {
                    NamedSquare(first, featured = true, Modifier.size(big))
                    Column(verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                        rest.take(2).forEach { NamedSquare(it, featured = false, Modifier.size(cell)) }
                    }
                }
                if (rest.size > 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Tight)) {
                        rest.drop(2).forEach { NamedSquare(it, featured = false, Modifier.size(cell)) }
                    }
                }
            }
        }
    }
}

/**
 * The sides of a featured cover grid's squares, in px, for a row [width] wide with [gap] between squares: a cell of the
 * three columns, and the featured one, two cells and the gap between them.
 */
internal fun featuredSquares(width: Int, gap: Int): Pair<Int, Int> {
    val cell = ((width - 2 * gap) / 3).coerceAtLeast(0)
    return cell to 2 * cell + gap
}

/** How many small cards are round the featured one: two beside it, three under. */
private const val FEATURED_AROUND = 5

/** A square of a featured cover grid: its picture, its name (and on the featured one its line) over a soft fade. */
@Composable
private fun NamedSquare(item: HomeItem, featured: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .clickable(onClickLabel = "Open ${item.title}", onClick = item.open)
            .semantics(mergeDescendants = true) {}
            .testTag(if (featured) "homeFeatured" else "homeCoverGridCell"),
    ) {
        CardPicture(item.mosaic, item.coverArt, item.colors, item.title, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(0.dp), if (featured) 600 else 300,
            nameOver = true, songIds = item.songIds, artists = item.artists, round = item.round) { paint ->
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    // Its own deep shade, easing in (the owner, 20 Nov).
                    .background(nameFade(paint.deep))
                    .padding(start = Space.Small, end = Space.Small, top = if (featured) Space.Large else Space.Section, bottom = Space.Small),
                verticalArrangement = Arrangement.spacedBy(Space.Tight),
            ) {
                WholeWordsText(item.title, (if (featured) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge).copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White, maxLines = if (featured) 2 else 3)
                val line = item.subtitle.ifBlank { item.meta }
                if (featured && line.isNotBlank()) {
                    Text(line, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.82f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * A banner as a hero (the owner, 20 Nov): the row's first card big at the top of the row, in the colours of its
 * picture, a card with the page's margins beside it like every other (the owner, 22 Nov: "the same padding instead of
 * extending right to the phone edges"): [title] in capitals over its big cover, its name and line, and Play
 * and Shuffle. Play plays the card ([HomeItem.play]), else its songs, else the row ([play]); Shuffle its songs, or
 * the row's, shuffled.
 */
@Composable
fun HomeHero(
    title: String,
    item: HomeItem,
    play: (() -> Unit)?,
    rowSongs: List<Song>,
    // The colour of its picture ([rememberHeaderTint]); without one, its own colours.
    tint: Color? = null,
    // The row's next cards, at its foot on the same gradient (the owner, 22 Nov): three or four, each as wide as its
    // share of the row, its name on its picture and its line under it.
    under: List<HomeItem> = emptyList(),
    playSongs: (List<Song>, Boolean) -> Unit,
) {
    val songs = item.songs.ifEmpty { rowSongs }
    // Its colours follow its row, as every other card's do (the owner, 23 Nov: "changing hero cards colours has no
    // effect"): the row's Colours when they're a set or the songs' moods, else its picture's; filled as the row's
    // Card colour says ([heroColours]).
    val chosen = shortcutColours(item.title, item.songIds)
    // A row with no colour of its own and its Colours chosen takes them ([shortcutLookOf] gives As Home then).
    val look = if (chosen != null && LocalShortcutLook.current == app.siansa.data.CardLook.AS_HOME) app.siansa.data.CardLook.AS_HOME else LocalCardLook.current
    val fill = heroColours(look, chosen?.first ?: tint ?: item.colors.first, chosen ?: item.colors, fromPicture = chosen == null)
    val onPlay: (() -> Unit)? = item.play ?: songs.takeIf { it.isNotEmpty() }?.let { { playSongs(it, false) } } ?: play
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.Page, end = Space.Page, top = LocalSectionGap.current)
            .clip(RoundedCornerShape(HERO_CORNER))
            // Deep enough under the white words in light mode too, deeper towards its foot.
            .background(Brush.verticalGradient(0f to fill.first, 1f to fill.second))
            .clickable(onClick = item.open)
            .testTag("homeHero"),
    ) {
        Column(Modifier.padding(start = Space.Page, end = Space.Page, top = Space.Section, bottom = Space.Section), verticalArrangement = Arrangement.spacedBy(Space.Page)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp)),
                fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.Page), verticalAlignment = Alignment.Bottom) {
                // Its name stands beside it, so it's never on the picture as well (issue #1353: a mix not made yet, or
                // an Aura, said "Wake up" twice).
                CardPicture(item.mosaic, item.coverArt, item.colors, item.title, Modifier.size(HERO_COVER), RoundedCornerShape(8.dp), 600, names = false, songIds = item.songIds, artists = item.artists, round = item.round)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                    WholeWordsText(item.title, MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = Color.White, maxLines = 3)
                    val line = listOf(item.subtitle, item.meta).filter { it.isNotBlank() }.joinToString(" · ")
                    if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.82f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.Medium), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Button(onClick = { (onPlay ?: item.open)() }, modifier = Modifier.height(HERO_BUTTON).testTag("homeHeroPlay")) {
                    Icon(if (onPlay == null) Icons.Rounded.ChevronRight else Icons.Rounded.PlayArrow, null)
                    Text(if (onPlay == null) "Open" else "Play", Modifier.padding(start = Space.Tight), fontWeight = FontWeight.Bold)
                }
                if (songs.isNotEmpty()) {
                    androidx.compose.material3.OutlinedIconButton(
                        onClick = { playSongs(songs, true) },
                        modifier = Modifier.size(HERO_BUTTON).testTag("homeHeroShuffle"),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    ) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = Color.White) }
                }
            }
            if (under.isNotEmpty()) HeroCards(under)
        }
    }
}

/**
 * The cards at a hero's foot: a section's gap under its buttons ([Space.Page] from the column and [Space.Small] here),
 * the cards a small step apart and as wide as their share of the row, so their outer edges are the hero's own.
 */
@Composable
private fun HeroCards(cards: List<HomeItem>) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(top = Space.Small).testTag("homeHeroCards")) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val gapPx = with(density) { Space.Small.roundToPx() }
        val side = with(density) { heroCardSide(constraints.maxWidth, gapPx, cards.size).toDp() }
        CompositionLocalProvider(LocalPictureStyle provides LocalHeroCardPictures.current) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                for (card in cards) {
                    Column(
                        Modifier.width(side).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Open ${card.title}", onClick = card.open)
                            .semantics(mergeDescendants = true) {}.testTag("homeHeroCard"),
                    ) {
                        CardPicture(card.mosaic, card.coverArt, card.colors, card.title, Modifier.size(side), RoundedCornerShape(8.dp), 300,
                            names = true, line = "", songIds = card.songIds, artists = card.artists, round = card.round)
                        if (card.subtitle.isNotBlank()) {
                            Spacer(Modifier.height(Space.Small))
                            Text(card.subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.82f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A hero's fill, top and foot, in its row's Card colour [look] (issue #1351; the owner, 25 Nov: Your listening's colours):
 * - Teal, Plum, Ink, Ember, Moss: that colour's own fade, as on Your listening's card.
 * - From its cover: [tint] (its picture's colour) darkened, as Your listening's card from its top song.
 * - As Home with the row's Colours chosen (By mood, Siansa, Mono; [fromPicture] false): [tint], the first of them, with
 *   its [own] two mixed in, deeper towards the foot.
 * Its words are white in both themes, so each fill is kept dark enough to read them on ([readableUnderWhite]).
 */
internal fun heroColours(look: app.siansa.data.CardLook, tint: Color, own: Pair<Color, Color>, fromPicture: Boolean = true): Pair<Color, Color> {
    val colour = look.colour
    val (top, foot) = when {
        colour == null && !fromPicture ->
            lerp(lerp(tint, own.first, 0.35f), Color.Black, 0.25f) to lerp(lerp(tint, own.second, 0.5f), Color.Black, 0.5f)
        (colour ?: app.siansa.data.CardLook.DEFAULT.colour) == app.siansa.data.ListenColour.COVER ->
            lerp(tint, Color.Black, COVER_TOP) to lerp(tint, Color.Black, COVER_FOOT)
        else -> (colour ?: app.siansa.data.CardLook.DEFAULT.colour!!).let { Color(it.top) to Color(it.foot) }
    }
    return readableUnderWhite(top) to readableUnderWhite(foot)
}

/** [colour], made darker until white words read on it (a pale tint: Siansa's frost, Mono's light greys, a white cover). */
internal fun readableUnderWhite(colour: Color): Color {
    var c = colour
    repeat(8) { if (c.luminance() <= HERO_LIGHTEST) return c; c = lerp(c, Color.Black, 0.2f) }
    return c
}

private const val HERO_LIGHTEST = 0.2f

/** The side of each of [count] cards, in px, sharing a row [width] wide with [gap] between them. */
internal fun heroCardSide(width: Int, gap: Int, count: Int): Int = ((width - gap * (count - 1).coerceAtLeast(0)) / count.coerceAtLeast(1)).coerceAtLeast(0)

private val HERO_CORNER = 16.dp
private val HERO_COVER = 148.dp
private val HERO_BUTTON = 48.dp

@Composable
private fun CoverGridCell(item: HomeItem, named: Boolean, modifier: Modifier) {
    val shape = if (item.round) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(8.dp)
    Column(
        modifier
            .clickable(onClickLabel = "Open ${item.title}", onClick = item.open)
            .semantics(mergeDescendants = true) { if (!named) contentDescription = item.title }
            .testTag("homeCoverGridCell"),
    ) {
        val square = Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
        when {
            // An artist keeps its round photo whatever the row's pictures.
            item.round && item.coverArt != null -> Cover(item.coverArt, 300, square, shape)
            item.round -> Box(square.background(Brush.linearGradient(listOf(item.colors.first, item.colors.second))))
            else -> CardPicture(item.mosaic, item.coverArt, item.colors, item.title, square, shape, showName = !named, songIds = item.songIds, artists = item.artists,
                names = when (LocalCardNames.current) { app.siansa.data.CardNames.UNDER -> false; app.siansa.data.CardNames.ON -> true; else -> null }, line = item.subtitle)
        }
        if (named) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = if (item.round) androidx.compose.ui.text.style.TextAlign.Center else null,
                modifier = Modifier.fillMaxWidth().padding(top = Space.Tight))
        }
    }
}

/**
 * Any row as a carousel (the owner, 17 Nov): its items as big covers to flick through, the one in the middle in focus,
 * its name and what it is under the row ([RecordCrate], as Siansa Beats' mixes are).
 */
@Composable
private fun HomeCarouselLook(title: String, items: List<HomeItem>) {
    SectionTitle(title)
    RecordCrate(items.map(::crateRecordOf), Modifier.testTag("homeLookCarousel"))
}

/** A Home item as a carousel's sleeve: its cover, else its colours; under it the small line, else the third. */
internal fun crateRecordOf(item: HomeItem): CrateRecord = CrateRecord(
    key = item.key,
    name = item.title,
    caption = item.subtitle.ifBlank { item.meta },
    covers = listOf(item.coverArt),
    songs = emptyList(),
    onOpen = item.open,
    colors = item.colors,
    round = item.round,
)

/**
 * Cover rows for a row of Home (issue #789): the same shelf as a custom card's, three rows to a column. Each item's
 * cover plays it when it has a play, else opens it; the row opens it. The first [CoverRows.ON_HOME] items.
 */
@Composable
private fun HomeCoverRows(title: String, items: List<HomeItem>, open: () -> Unit) {
    val actions = items.take(app.siansa.mix.CoverRows.ON_HOME).map { item -> CoverRowAction(coverRowOf(item), playing = false, open = item.open, play = item.play ?: item.open, menu = emptyList()) }
    CoverRowsShelf(title, actions, open)
}

/** A Home item as a cover row: subtitle above, title, then its third line; colours where there's no cover. */
internal fun coverRowOf(item: HomeItem): app.siansa.mix.CoverRow = app.siansa.mix.CoverRow(
    key = item.key,
    kind = app.siansa.mix.CoverRow.Kind.OTHER,
    cover = item.coverArt,
    small = item.subtitle,
    title = item.title,
    meta = item.meta,
    progress = item.progress,
    colors = if (item.coverArt == null) argb(item.colors.first) to argb(item.colors.second) else null,
    round = item.round,
)

private fun argb(c: Color): Long = c.toArgb().toLong() and 0xFFFFFFFFL

@Composable
private fun HomeItemTile(item: HomeItem, tile: androidx.compose.ui.unit.Dp = TILE) {
    val names = LocalCardNames.current
    if (!item.round && (item.mosaic.isNotEmpty() || names == app.siansa.data.CardNames.ON)) {
        // A mix (or, with names on the picture, any cover): its picture in the row's style, its name and line under it
        // or on it, as the row's Names choice says (the owner, 21 Nov).
        val on = names == app.siansa.data.CardNames.ON
        Column(Modifier.width(tile).clickable(onClickLabel = "Open ${item.title}", onClick = item.open).testTag("homeLookTile")) {
            CardPicture(item.mosaic, item.coverArt, item.colors, item.title, Modifier.fillMaxWidth().aspectRatio(1f), songIds = item.songIds, artists = item.artists,
                names = when (names) { app.siansa.data.CardNames.UNDER -> false; app.siansa.data.CardNames.ON -> true; else -> null }, line = item.subtitle)
            // On Auto, a picture with its name on it has just its line under it.
            val style = LocalPictureStyle.current
            val namesItself = androidx.compose.runtime.remember(style, item.mosaic, item.coverArt) { pictureNamesItself(style, item.mosaic, item.coverArt) }
            val named = names == app.siansa.data.CardNames.AUTO && namesItself
            if (!on) TileNames(item.title, item.subtitle, showTitle = !named)
        }
    } else if (item.coverArt != null) {
        AlbumTile(item.title, item.subtitle, item.coverArt, item.open, Modifier.width(tile).testTag("homeLookTile"), round = item.round)
    } else {
        GradientTile(title = item.title, colors = item.colors, onClick = item.open, size = tile, modifier = Modifier.testTag("homeLookTile"))
    }
}

/**
 * A tile's name and line under its picture: two lines each, so a long one (its genres, the owner, 21 Nov) isn't cut
 * off after a few words.
 */
@Composable
internal fun TileNames(title: String, line: String, showTitle: Boolean = true) {
    @Suppress("NAME_SHADOWING")
    val line = if (LocalCardLines.current) line else ""
    if (!showTitle && line.isBlank()) return
    Spacer(Modifier.height(Space.Small))
    if (showTitle) Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** The covers of the first items, or a colour block for an item without one. */
@Composable
private fun ItemArt(item: HomeItem, modifier: Modifier) {
    val shape = if (item.round) CircleShape else RoundedCornerShape(8.dp)
    if (!item.round) CardPicture(item.mosaic, item.coverArt, item.colors, item.title, modifier, shape, songIds = item.songIds, artists = item.artists)
    else if (item.coverArt != null) Cover(item.coverArt, 300, modifier, shape)
    else Box(modifier.clip(shape).background(Brush.linearGradient(listOf(item.colors.first, item.colors.second))))
}

@Composable
private fun HomeBanner(id: String, title: String, items: List<HomeItem>, open: () -> Unit, play: (() -> Unit)?, songs: List<Song>) {
    val colors = tileColors(id)
    val playback = if (play != null && songs.isNotEmpty()) rememberListPlayback(songs) else ListPlayback.IDLE
    Column(Modifier.padding(start = Space.Page, end = Space.Page, top = LocalSectionGap.current)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(colors.first, lerp(colors.second, Color.Black, 0.2f))))
                .clickable(onClick = open)
                .testTag("homeLookBanner"),
        ) {
            val fan = items.take(3)
            Box(Modifier.align(Alignment.TopEnd).padding(Space.Medium)) {
                fan.forEachIndexed { i, item ->
                    ItemArt(item, Modifier.offset(x = (-(fan.size - 1 - i) * 36).dp, y = (i * 8).dp).size(80.dp))  // spacing: exact (the fan)
                }
            }
            Column(Modifier.padding(Space.Page).fillMaxWidth(0.55f)) {
                WholeWordsText(title, MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = Color.White, maxLines = 2)
                Spacer(Modifier.height(Space.Tight))
                Text(items.take(3).joinToString(" · ") { it.title }, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Space.Page))
                FilledTonalButton(onClick = { if (play != null) playback.press(play) else open() }, modifier = Modifier.testTag("homeLookBannerButton")) {
                    Icon(if (play == null) Icons.Rounded.ChevronRight else if (playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                    Text(if (play == null) "Open" else if (playback.playing) "Pause" else "Play", Modifier.padding(start = Space.Tight))
                }
            }
        }
    }
}

@Composable
private fun HomeList(title: String, items: List<HomeItem>, open: () -> Unit) {
    Column(Modifier.testTag("homeLookList")) {
        if (!LocalSectionTitleShown.current) HiddenTitleGap() else Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Show all", onClick = open)
                .padding(start = Space.Page, end = Space.Page, top = LocalSectionGap.current, bottom = Space.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items.take(LIST).forEach { item ->
            Row(
                Modifier.fillMaxWidth().clickable(onClick = item.open).padding(horizontal = Space.Page, vertical = Space.Small).testTag("homeLookListRow"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ItemArt(item, Modifier.size(48.dp))
                Spacer(Modifier.width(Space.Medium))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.subtitle.isNotBlank()) {
                        Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeButton(id: String, title: String, items: List<HomeItem>, open: () -> Unit, play: (() -> Unit)?, songs: List<Song>) {
    val colors = tileColors(id)
    val playback = if (play != null && songs.isNotEmpty()) rememberListPlayback(songs) else ListPlayback.IDLE
    // The card opens the section; only the play badge plays (or pauses) it, and there's no arrow (issue #730).
    Row(
        Modifier
            .padding(start = Space.Page, end = Space.Page, top = LocalSectionGap.current)
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = "Open $title", onClick = open)
            .testTag("homeLookButton"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayBadge(colors, playing = playback.playing, title = title, onPlay = play?.let { p -> { playback.press(p) } }, tag = "homeLookButtonPlay")
        Column(Modifier.weight(1f).padding(horizontal = Space.Medium)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(homeButtonCaption(play != null, playback.playing, items.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The coloured square at the start of a play button card (issue #730): ▶ (⏸ while its songs play) that plays or pauses
 * them when tapped. Without [onPlay] it's only an arrow, and a tap goes to the card, which opens.
 */
@Composable
internal fun PlayBadge(colors: Pair<Color, Color>, playing: Boolean, title: String, onPlay: (() -> Unit)?, tag: String) {
    Box(
        Modifier
            .size(64.dp)
            .background(Brush.linearGradient(listOf(colors.first, colors.second)))
            .then(if (onPlay != null) Modifier.clickable(onClickLabel = if (playing) "Pause $title" else "Play $title", role = Role.Button, onClick = onPlay).testTag(tag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            when {
                onPlay == null -> Icons.Rounded.ChevronRight
                playing -> Icons.Rounded.Pause
                else -> Icons.Rounded.PlayArrow
            },
            when {
                onPlay == null -> null
                playing -> "Pause $title"
                else -> "Play $title"
            },
            tint = Color.White, modifier = Modifier.size(32.dp),
        )
    }
}

/** What the slim card says under its title: the card opens, the ▶ plays. */
internal fun homeButtonCaption(plays: Boolean, playing: Boolean, count: Int): String = when {
    playing -> "Playing"
    plays -> "Press play, or tap to open"
    else -> "Tap to open · $count"
}

private val TILE = 140.dp
private val TILE_COMPACT = 120.dp
private val TILE_SMALL = 100.dp

/** A tile's width for the row's Size: Big as Home's tiles have always been, Compact and Small narrower. */
internal fun tileWidth(size: app.siansa.data.CrateSize): androidx.compose.ui.unit.Dp = when (size) {
    app.siansa.data.CrateSize.BIG -> TILE
    app.siansa.data.CrateSize.COMPACT -> TILE_COMPACT
    app.siansa.data.CrateSize.SMALL -> TILE_SMALL
}

/** One tile of a [TileGrid]: as a shortcut, its cover (or its colours and an icon) and its name. */
data class GridTile(
    val key: String,
    val title: String,
    val coverArt: String?,
    val colors: Pair<Color, Color>? = null,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val onClick: () -> Unit,
    /** What it is ("Mix", "Album"…), under its name on a big tile. */
    val subtitle: String = "",
    /** A mix's songs and their covers, for its row's Colours (By mood) and Pictures. */
    val songIds: List<String> = emptyList(),
    val covers: List<String?> = emptyList(),
)

/** The layout chosen for the row being drawn ([app.siansa.data.LookVariant], Arrange Home): null as before. */
val LocalLookVariant = androidx.compose.runtime.compositionLocalOf<app.siansa.data.LookVariant?> { null }

/**
 * Shortcut tiles in the row's layout (the owner, 20 Nov): two a row as always, three a row and smaller, big two a row
 * with what each is, or pill chips flowing on to as many lines as they need.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ShortcutTiles(tiles: List<GridTile>, variant: app.siansa.data.LookVariant?, modifier: Modifier = Modifier) {
    if (variant == app.siansa.data.LookVariant.CHIPS) {
        androidx.compose.foundation.layout.FlowRow(
            modifier.testTag("shortcutChips"),
            horizontalArrangement = Arrangement.spacedBy(Space.Small),
            verticalArrangement = Arrangement.spacedBy(Space.Small),
        ) {
            tiles.forEach { t -> ShortcutChip(t.title, t.coverArt, t.colors, t.onClick, Modifier.testTag("tileGridTile"), icon = t.icon, songIds = t.songIds) }
        }
        return
    }
    val perRow = if (variant == app.siansa.data.LookVariant.THREE) 3 else 2
    val tall = when (variant) {
        app.siansa.data.LookVariant.THREE -> QUICK_TILE_SMALL
        app.siansa.data.LookVariant.BIG -> QUICK_TILE_BIG
        else -> QUICK_TILE_HEIGHT
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (perRow == 3) Space.Tight else Space.Small)) {
        tiles.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(if (perRow == 3) Space.Tight else Space.Small)) {
                row.forEach { t ->
                    QuickTile(t.title, t.coverArt, t.colors, t.onClick, Modifier.weight(1f).testTag("tileGridTile"), icon = t.icon,
                        tall = tall, subtitle = t.subtitle.takeIf { variant == app.siansa.data.LookVariant.BIG && LocalCardLines.current },
                        songIds = t.songIds, covers = t.covers)
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The Shortcuts card's look for any card or row of Home (the owner, 10 Nov): its title (tapped, [onOpenAll]), then
 * small tiles two to a row, at most [most] ([GRID_LOOK_MOST], four rows).
 */
@Composable
fun TileGrid(title: String, tiles: List<GridTile>, most: Int, onOpenAll: (() -> Unit)? = null) {
    val shown = tiles.take(most.coerceIn(2, GRID_LOOK_MOST))
    if (shown.isEmpty()) return
    Column(Modifier.testTag("tileGrid")) {
        if (onOpenAll != null && LocalSectionTitleShown.current) {
            SectionTitle(title, Modifier.clickable(onClickLabel = "Show all", onClick = onOpenAll))
        } else SectionTitle(title)
        ShortcutTiles(shown, LocalLookVariant.current, Modifier.padding(horizontal = Space.Page))
    }
}

/** The most tiles a shortcut grid shows: four rows of two, as Shortcuts. */
const val GRID_LOOK_MOST = app.siansa.data.HomeLook.GRID_MOST

/** How many tiles a Home row set to the shortcut grid shows (Arrange Home): [titledItem] sets it per row. */
val LocalGridSize = androidx.compose.runtime.compositionLocalOf { GRID_LOOK_MOST }
