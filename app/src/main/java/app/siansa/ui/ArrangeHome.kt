package app.siansa.ui

import app.siansa.data.carouselSize
import app.siansa.data.cardNames
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Arrangement
import app.siansa.data.CardLook
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import kotlin.math.roundToInt
import androidx.compose.ui.platform.testTag
import app.siansa.data.AppSettings
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.zIndex
import app.siansa.data.HomeLook
import app.siansa.data.HomeSection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.log.AppLog
import app.siansa.data.moved
import app.siansa.data.movesFirstVisible

/**
 * Home's settings panel with the saved settings, from Home's button or Settings › Home (issue #780): Sources
 * ([startOnSources]) or Arrange. Your cards' cog closes it and opens Settings › Home › Cards.
 */
@Composable
fun HomeSettingsSheet(nav: androidx.navigation.NavController, startOnSources: Boolean, asPage: Boolean = false, onDismiss: () -> Unit) {
    // Sources is a sheet of its own (the owner, 12 Nov): Arrange isn't part of it.
    if (startOnSources) {
        SourcesSheet(onDismiss)
        return
    }
    val graph = LocalGraph.current
    val s by graph.settings.state.collectAsStateWithLifecycle()
    val beatsMakesMixes by app.siansa.net.BeatsSound.mixes.collectAsStateWithLifecycle()
    // The notices are the top of Home (issue #872), not a section to move: shown or not in Settings › Home › Top of Home.
    val beatsRows = beatsRows(beatsMakesMixes && s.beatsFor(app.siansa.data.BeatsUse.MIXES))
    // More of you is listed whoever makes Home's mixes (issue #1353): Siansa Beats', or this phone's On repeat and
    // Discover for you.
    val offered = setOf(HomeSection.NOTICES)
    ArrangeHomeSheet(s.homeOrder - offered - HomeSection.NOTICES, s.homeHidden, holds = beatsRows,
        sections = HomeSection.default - HomeSection.NOTICES - offered,
        listenColour = app.siansa.data.ListenColour.of(s.listenColour), onListenColour = { graph.settings.setListenColour(it.id) },
        memoryStyle = MemoryStyle.of(s.onThisDayStyle),
        onMemoryStyle = {
            AppLog.i("Home", "On this day's style: ${it.label}")
            graph.settings.save(graph.settings.current.copy(onThisDayStyle = it.id))
        },
        memoryArtists = s.onThisDayArtists,
        onMemoryArtists = {
            AppLog.i("Home", "On this day shows ${if (it) "artists" else "albums"}")
            graph.settings.save(graph.settings.current.copy(onThisDayArtists = it))
        },
        memoryPicture = MemoryPicture.of(s.onThisDayPicture),
        onMemoryPicture = {
            AppLog.i("Home", "On this day's pictures: ${it.label}")
            graph.settings.save(graph.settings.current.copy(onThisDayPicture = it.id))
        },
        gap = s.homeGap, onGapChange = {
        AppLog.i("Home", "Spacing: ${homeGapLabel(it)}")
        graph.settings.setHomeGap(it)
    }, onChange = {
        val order = keepUnlisted(listOf(HomeSection.NOTICES) + (it - HomeSection.NOTICES), s.homeOrder)
        AppLog.i("Home", "Arranged: ${HomeSection.format(order)}")
        graph.settings.setHomeOrder(order)
    }, onHiddenChange = {
        AppLog.i("Home", "Hidden: ${HomeSection.formatHidden(it).ifEmpty { "none" }}")
        graph.settings.setHomeHidden(it)
    }, onDismiss = onDismiss, asPage = asPage,
        looks = s.homeLooks,
        onResetRow = { section ->
            AppLog.i("Home", "${section.label}: reset to how it came")
            graph.settings.resetHomeRow(section)
        },
        // Artist radios' artists: yours, Siansa Beats', or both, while Siansa Beats makes Home's mixes (the owner, 26 Nov).
        artistsFrom = app.siansa.mix.ArtistsFrom.of(s.artistRadiosFrom).takeIf { beatsMakesMixes && s.beatsFor(app.siansa.data.BeatsUse.MIXES) },
        onArtistsFrom = {
            AppLog.i("Home", "Artist radios from: ${it.label}")
            graph.settings.save(graph.settings.current.copy(artistRadiosFrom = it.id))
        },
        shortcutRows = shortcutRows(s.homeShortcuts),
        onShortcutRows = { rows ->
            AppLog.i("Home", "Shortcuts: $rows ${if (rows == 1) "row" else "rows"}")
            graph.settings.setHomeShortcuts(rows * 2)
        },
        noLines = s.homeNoLines,
        onLinesShownChange = { section, shown ->
            AppLog.i("Home", "${section.label}: its cards' lines ${if (shown) "shown" else "hidden"}")
            graph.settings.setHomeLinesShown(section, shown)
        },
        untitled = s.homeUntitled,
        onTitleShownChange = { section, shown ->
            AppLog.i("Home", "${section.label}: title ${if (shown) "shown" else "hidden"}")
            graph.settings.setHomeTitleShown(section, shown)
        },
        onLookChange = { section, look ->
            AppLog.i("Home", "${section.label} looks: ${look.label}")
            graph.settings.setHomeLook(section, look)
        },
        gridSizes = s.homeGridSizes,
        cardNames = HomeSection.entries.associateWith { s.cardNames(it) },
        carouselSizes = HomeSection.entries.associateWith { s.carouselSize(it) },
        onCarouselSizeChange = { section, size ->
            AppLog.i("Home", "${section.id} carousel: ${size.label}")
            graph.settings.setHomeCarouselSize(section, size)
        },
        onCardNamesChange = { section, names ->
            AppLog.i("Home", "${section.label}: names ${names.label}")
            graph.settings.setHomeNames(section, names)
        },
        onGridSizeChange = { section, size ->
            AppLog.i("Home", "${section.label}: $size tiles in its grid")
            graph.settings.setHomeGridSize(section, size)
        },
        coverGrids = s.homeCoverGrids,
        pictures = s.homePictures,
        pictureColours = s.homePictureColours,
        onPictureColoursChange = { section, colours ->
            AppLog.i("Home", "${section.label}: picture colours ${colours.label}")
            graph.settings.setHomePictureColours(section, colours)
        },
        onPictureChange = { section, style ->
            AppLog.i("Home", "${section.label}: pictures as ${style.label}")
            graph.settings.setHomePicture(section, style)
        },
        heroCardPictures = s.homeHeroCardPictures,
        onHeroCardPictureChange = { section, style ->
            AppLog.i("Home", "${section.label}: the pictures of the cards under its hero as ${style.label}")
            graph.settings.setHomeHeroCardPicture(section, style)
        },
        variants = s.homeVariants,
        onVariantChange = { section, variant ->
            AppLog.i("Home", "${section.label}: ${variant.look.label} as ${variant.label}")
            graph.settings.setHomeVariant(section, variant)
        },
        onCoverGridChange = { section, size ->
            AppLog.i("Home", "${section.label}: a cover grid of ${size.columns} columns and ${size.rows} rows")
            graph.settings.setHomeCoverGrid(section, size)
        },
        onResetLooks = {
            AppLog.i("Home", "Every row back in its own look, every card in Home's colour")
            graph.settings.resetHomeLooks()
        },
        cardLook = s.homeCardLook,
        cardLooks = s.homeCardLooks,
        onCardLookChange = { look ->
            AppLog.i("Home", "Cards: ${look.label}")
            graph.settings.setHomeCardLook(look)
        },
        onSectionCardLookChange = { section, look ->
            AppLog.i("Home", "${section.label}'s cards: ${look.label}")
            graph.settings.setHomeCardLook(section, look)
        },
        onOpenSettings = { section ->
            onDismiss()
            nav.navigate(if (section == HomeSection.NOTICES) "settings/home" else "settings/cards")
        },
    )
}

/**
 * Arrange Home, from Home's menu (Sources has a sheet of its own, [SourcesSheet]).
 *
 * Arrange is a short list of Home's sections to drag into a new order: hold a row, or grab its handle, and move it.
 * Only the row being dragged follows the finger; the others slide out of the way. The order is saved
 * when a row is let go. The eye on each row hides the section from Home, or shows it again.
 */
@Composable
fun ArrangeHomeSheet(
    order: List<HomeSection>,
    hidden: Set<HomeSection>,
    onChange: (List<HomeSection>) -> Unit,
    onHiddenChange: (Set<HomeSection>) -> Unit,
    onDismiss: () -> Unit,
    // Drawn as a page of its own (Settings › Home › Arrange Home) rather than a sheet.
    asPage: Boolean = false,
    // What another service puts in a row instead of its own (Siansa Beats' mixes, the owner, 17 Nov), said under it.
    holds: Map<HomeSection, String> = emptyMap(),
    // The gap between Home's sections, a step of the spacing scale (#554).
    gap: Int = AppSettings.HOME_GAP_DEFAULT,
    onGapChange: (Int) -> Unit = {},
    // Each row's look (issue #655), changed with its cog; Your cards' cog opens Settings › Home › Cards, and
    // Notices' cog Settings › Home › Top of Home (issue #784).
    looks: Map<HomeSection, HomeLook> = emptyMap(),
    // Each row's title shown or hidden (issue #961), in the same dialog as its look.
    onResetRow: (HomeSection) -> Unit = {},
    // How many rows of tiles Shortcuts shows (the owner, 23 Nov), set in its cog.
    shortcutRows: Int = SHORTCUT_ROWS.last,
    onShortcutRows: (Int) -> Unit = {},
    artistsFrom: app.siansa.mix.ArtistsFrom? = null,
    onArtistsFrom: (app.siansa.mix.ArtistsFrom) -> Unit = {},
    // Your listening's colour (issue #1347), set in its cog.
    listenColour: app.siansa.data.ListenColour = app.siansa.data.ListenColour.TEAL,
    onListenColour: (app.siansa.data.ListenColour) -> Unit = {},
    memoryStyle: MemoryStyle = MemoryStyle.STORIES,
    onMemoryStyle: (MemoryStyle) -> Unit = {},
    memoryArtists: Boolean = false,
    onMemoryArtists: (Boolean) -> Unit = {},
    memoryPicture: MemoryPicture = MemoryPicture.COLLAGE,
    onMemoryPicture: (MemoryPicture) -> Unit = {},
    noLines: Set<HomeSection> = emptySet(),
    onLinesShownChange: (HomeSection, Boolean) -> Unit = { _, _ -> },
    untitled: Set<HomeSection> = emptySet(),
    onTitleShownChange: (HomeSection, Boolean) -> Unit = { _, _ -> },
    onLookChange: (HomeSection, HomeLook) -> Unit = { _, _ -> },
    onResetLooks: () -> Unit = {},
    // Tiles of a row shown as the shortcut grid (the owner, 10 Nov), picked in its look's dialog.
    gridSizes: Map<HomeSection, Int> = emptyMap(),
    cardNames: Map<HomeSection, app.siansa.data.CardNames> = emptyMap(),
    onCardNamesChange: (HomeSection, app.siansa.data.CardNames) -> Unit = { _, _ -> },
    carouselSizes: Map<HomeSection, app.siansa.data.CrateSize> = emptyMap(),
    onCarouselSizeChange: (HomeSection, app.siansa.data.CrateSize) -> Unit = { _, _ -> },
    onGridSizeChange: (HomeSection, Int) -> Unit = { _, _ -> },
    // Each cover grid's columns and rows (#1263).
    coverGrids: Map<HomeSection, app.siansa.data.CoverGridSize> = emptyMap(),
    onCoverGridChange: (HomeSection, app.siansa.data.CoverGridSize) -> Unit = { _, _ -> },
    variants: Map<HomeSection, app.siansa.data.LookVariant> = emptyMap(),
    onVariantChange: (HomeSection, app.siansa.data.LookVariant) -> Unit = { _, _ -> },
    // How each row's card pictures show (the owner, 20 Nov).
    pictures: Map<HomeSection, app.siansa.data.PictureStyle> = emptyMap(),
    // How the cards at a hero's foot show theirs (the owner, 22 Nov).
    heroCardPictures: Map<HomeSection, app.siansa.data.PictureStyle> = emptyMap(),
    onHeroCardPictureChange: (HomeSection, app.siansa.data.PictureStyle) -> Unit = { _, _ -> },
    onPictureChange: (HomeSection, app.siansa.data.PictureStyle) -> Unit = { _, _ -> },
    pictureColours: Map<HomeSection, app.siansa.data.PictureColours> = emptyMap(),
    onPictureColoursChange: (HomeSection, app.siansa.data.PictureColours) -> Unit = { _, _ -> },
    onOpenSettings: (HomeSection) -> Unit = {},
    // The sections listed here, in Reset's order: all but the notices, which are the top of Home (issue #872).
    sections: List<HomeSection> = HomeSection.default - HomeSection.NOTICES,
    // How Home's coloured cards are painted (the owner, 13 Nov), and the sections with a look of their own.
    cardLook: CardLook = CardLook.DEFAULT,
    cardLooks: Map<HomeSection, CardLook> = emptyMap(),
    onCardLookChange: (CardLook) -> Unit = {},
    onSectionCardLookChange: (HomeSection, CardLook) -> Unit = { _, _ -> },
) {
    var picking by remember { mutableStateOf<HomeSection?>(null) }
    // Your listening's colour, under its cog (issue #1347).
    var colouring by remember { mutableStateOf(false) }
    var colour by remember(listenColour) { mutableStateOf(listenColour) }
    if (colouring) ListenColourDialog(colour, onPick = { colour = it; onListenColour(it) }) { colouring = false }
    var styling by remember { mutableStateOf(false) }
    var memory by remember(memoryStyle) { mutableStateOf(memoryStyle) }
    var faces by remember(memoryArtists) { mutableStateOf(memoryArtists) }
    var layout by remember(memoryPicture) { mutableStateOf(memoryPicture) }
    if (styling) MemoryStyleDialog(memory, onPick = { memory = it; onMemoryStyle(it) }, artists = faces, onArtists = { faces = it; onMemoryArtists(it) },
        picture = layout, onPicture = { layout = it; onMemoryPicture(it) }) { styling = false }
    var hiddenItems by remember { mutableStateOf(hidden) }
    picking?.let { section ->
        HomeLookDialog(
            section, looks[section] ?: HomeLook.AS_IS,
            linesShown = section !in noLines,
            onLinesShown = { onLinesShownChange(section, it) },
            titleShown = section !in untitled,
            onTitleShown = { onTitleShownChange(section, it) },
            // The sheet stays open: the look's own choices are under it.
            onPick = { onLookChange(section, it) },
            onHome = section !in hiddenItems,
            onOnHome = { hiddenItems = if (it) hiddenItems - section else hiddenItems + section; onHiddenChange(hiddenItems) },
            onReset = { onResetRow(section) },
            shortcutRows = shortcutRows.takeIf { section == HomeSection.QUICK },
            onShortcutRows = onShortcutRows,
            artistsFrom = artistsFrom.takeIf { section == HomeSection.ARTIST_RADIOS },
            onArtistsFrom = onArtistsFrom,
            onDismiss = { picking = null },
            // Your cards each have their own look, chosen in Settings › Home › Cards: a way there.
            onOpenSettings = if (section.lookable) null else { { picking = null; onOpenSettings(section) } },
            gridSize = gridSizes[section] ?: HomeLook.GRID_MOST,
            onGridSize = { onGridSizeChange(section, it) },
            coverGrid = coverGrids[section] ?: app.siansa.data.CoverGridSize.DEFAULT,
            onCoverGrid = { onCoverGridChange(section, it) },
            variant = variants[section],
            onVariant = { onVariantChange(section, it) },
            picture = pictures[section] ?: app.siansa.data.PictureStyle.AUTO,
            onPicture = { onPictureChange(section, it) },
            heroCardPicture = heroCardPictures[section] ?: app.siansa.data.PictureStyle.AUTO,
            onHeroCardPicture = { onHeroCardPictureChange(section, it) },
            pictureColours = pictureColours[section] ?: app.siansa.data.PictureColours.COVERS,
            onPictureColours = { onPictureColoursChange(section, it) },
            names = cardNames[section] ?: app.siansa.data.CardNames.AUTO,
            onNames = { onCardNamesChange(section, it) },
            crateSize = carouselSizes[section] ?: app.siansa.data.CrateSize.BIG,
            onCrateSize = { onCarouselSizeChange(section, it) },
            cardLook = cardLooks[section] ?: CardLook.AS_HOME,
            homeCardLook = cardLook,
            onCardLook = { onSectionCardLookChange(section, it) },
        )
    }
    var items by remember { mutableStateOf(order) }
    var dragging by remember { mutableStateOf<HomeSection?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val list = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    fun start(section: HomeSection) {
        dragging = section
        offset = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun dragBy(dy: Float) {
        val section = dragging ?: return
        val info = list.layoutInfo
        val visible = info.visibleItemsInfo
        val current = visible.firstOrNull { it.key == section.id } ?: return
        // Kept inside the list's box (the owner, 27 Nov: dragged too far up it vanished, cut off by the box's edge);
        // at an edge the list scrolls under it instead.
        offset = clampDrag(offset + dy, current.offset, current.size, info.viewportStartOffset, info.viewportEndOffset)
        // Only past the next row's middle, that row being the one beside it in the way it's going: rows of different
        // heights (a Siansa Beats tag makes one taller) no longer swap back and forth on one spot (the owner, 27 Nov).
        val down = offset > 0f
        val target = visible.firstOrNull { it.index == current.index + if (down) 1 else -1 } ?: return
        if (!passesMiddle(current.offset + offset, current.size, target.offset, target.size, down)) return
        // Within its own group: the rows on Home, or the hidden ones under them (the owner, 26 Nov); not onto the heading.
        val over = items.firstOrNull { it.id == target.key } ?: return
        if ((over in hiddenItems) != (section in hiddenItems)) return
        // Moving to or from the top row: stay at the same position, not with the row that was first, or the moved
        // row ends up above the screen and seems to vanish (#614).
        if (movesFirstVisible(current.index, target.index, list.firstVisibleItemIndex)) {
            list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        }
        items = items.moved(items.indexOf(section), items.indexOf(over))
        // The row is laid out at its new place next frame; keep it under the finger meanwhile. Its new place is where
        // the other row began going up, and under it going down: the two needn't be one height.
        offset += current.offset - offsetAfterSwap(current.size, target.offset, target.size, down)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun end() {
        if (dragging == null) return
        dragging = null
        offset = 0f
        onChange(items)
    }

    // A held row near the list's top or bottom edge scrolls it (issue #701), so a row can go anywhere, not only
    // where the list was when it was picked up. The row stays under the finger as the list moves.
    val edgePx = with(androidx.compose.ui.platform.LocalDensity.current) { AUTO_SCROLL_EDGE.toPx() }
    LaunchedEffect(dragging) {
        val section = dragging ?: return@LaunchedEffect
        while (dragging == section) {
            androidx.compose.runtime.withFrameNanos { }
            val info = list.layoutInfo
            val current = info.visibleItemsInfo.firstOrNull { it.key == section.id } ?: continue
            val top = current.offset + offset
            val speed = autoScrollSpeed(top, top + current.size, info.viewportStartOffset.toFloat(), info.viewportEndOffset.toFloat(), edgePx)
            if (speed == 0f) continue
            val moved = list.scrollBy(speed)
            if (moved != 0f) {
                offset += moved
                dragBy(0f)
            }
        }
    }

    SheetOrPage(asPage, onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Space.Small)) {
            Row(Modifier.fillMaxWidth().padding(start = Space.Page, end = Space.Small), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // As a page, its title is the page's (the owner, 17 Nov: Arrange Home has its own page in Home page).
                    if (!asPage) Text("Arrange Home", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "What's on Home, in what order, and how its cards look.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        if (items != sections) {
                            list.requestScrollToItem(0)  // the reset order shows from its top (#614)
                            items = sections
                            onChange(items)
                        }
                        // Only what's listed here shows again: notices hidden in Settings stay hidden.
                        if (hiddenItems.any { it in sections }) {
                            hiddenItems = hiddenItems - sections.toSet()
                            onHiddenChange(hiddenItems)
                        }
                        if (gap != AppSettings.HOME_GAP_DEFAULT) onGapChange(AppSettings.HOME_GAP_DEFAULT)
                        if (looks.isNotEmpty() || cardLook != CardLook.DEFAULT || cardLooks.isNotEmpty()) onResetLooks()
                    },
                    enabled = items != sections || hiddenItems.any { it in sections } || gap != AppSettings.HOME_GAP_DEFAULT || looks.isNotEmpty() ||
                        cardLook != CardLook.DEFAULT || cardLooks.isNotEmpty(),
                ) { Text("Reset") }
            }
            // The cards' colour (the owner, 13 Nov; 25 Nov: Your listening's swatches); never the shortcut grid.
            SheetLabel("Cards")
            CardLookChooser(cardLook, CardLook.forHome, Modifier.padding(horizontal = Space.Page), onPick = onCardLookChange)
            Text(
                "Every card on Home, not the shortcuts. A card can have its own under its cog.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.Page, end = Space.Page, top = Space.Small),
            )
            SheetLabel("Spacing")
            SpacingRow(gap, onGapChange)
            @Composable
            fun androidx.compose.foundation.lazy.LazyItemScope.SectionLine(section: HomeSection, first: Boolean) {
                    val lifted = section == dragging
                    val isHidden = section in hiddenItems
                    // Plain rows (the owner, 12 Nov): only the one being held is a lifted card.
                    Surface(
                        color = if (lifted) MaterialTheme.colorScheme.surfaceContainerHighest else androidx.compose.ui.graphics.Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .then(
                                if (lifted) Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
                                else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                            )
                            .zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer { translationY = if (lifted) offset else 0f }
                            .then(if (lifted) Modifier.shadow(6.dp, RoundedCornerShape(12.dp)) else Modifier)
                            .fillMaxWidth()
                            .pointerInput(section) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { start(section) },
                                    onDrag = { change, amount -> change.consume(); dragBy(amount.y) },
                                    onDragEnd = { end() },
                                    onDragCancel = { end() },
                                )
                            },
                    ) {
                        // At least a row's height, taller when the line under the name wraps (the owner, 19 Nov: it was cut off).
                        Row(Modifier.heightIn(min = 52.dp).padding(start = Space.Small).then(if (!lifted && !first) Modifier.hairlineAbove() else Modifier), verticalAlignment = Alignment.CenterVertically) {
                            // The handle moves straight away, without the hold.
                            Icon(
                                Icons.Rounded.DragIndicator,
                                contentDescription = "Move ${section.label}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .pointerInput(section) {
                                        detectDragGestures(
                                            onDragStart = { start(section) },
                                            onDrag = { change, amount -> change.consume(); dragBy(amount.y) },
                                            onDragEnd = { end() },
                                            onDragCancel = { end() },
                                        )
                                    }
                                    .padding(horizontal = Space.Small, vertical = Space.Page)
                                    .size(24.dp),
                            )
                            // Hidden rows stay in the list, dimmed, so they keep their place and can come back.
                            Column(Modifier.weight(1f).padding(start = Space.Tight, top = Space.Small, bottom = Space.Small).alpha(if (isHidden) 0.6f else 1f)) {
                                Text(
                                    section.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                                // Rows Siansa Beats fills carry a small tag (the owner, 26 Nov), and Today's mix, empty then, says so.
                                arrangeTag(section, holds)?.let { RowTag(it) }
                            }
                            IconButton(onClick = {
                                hiddenItems = if (isHidden) hiddenItems - section else hiddenItems + section
                                onHiddenChange(hiddenItems)
                            }) {
                                Icon(
                                    if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (isHidden) "Show ${section.label}" else "Hide ${section.label}",
                                    tint = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(
                                // Every row's cog opens the same choices (#1261), Your cards' too. Your listening's
                                // look is fixed: its cog has its colour alone (issue #1347); On this day's, its styles.
                                onClick = {
                                    when {
                                        section == HomeSection.ON_THIS_DAY -> styling = true
                                        section.fixed -> colouring = true
                                        section != HomeSection.NOTICES -> picking = section
                                        else -> onOpenSettings(section)
                                    }
                                },
                                modifier = Modifier.testTag("homeLookCog"),
                            ) {
                                Icon(
                                    Icons.Rounded.Settings,
                                    contentDescription = if (section == HomeSection.NOTICES) "Notices in Settings" else if (section.fixed) "Colour of ${section.label}" else "Look of ${section.label}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
            }
            SheetLabel("Sections", "hold to move")
            // One grouped list (the owner, 13 Nov): the rows on a raised surface, a hairline between them.
            LazyColumn(
                state = list, userScrollEnabled = dragging == null,
                modifier = Modifier.padding(horizontal = Space.Page).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).testTag("arrangeSections"),
            ) {
                // The rows on Home in their order, then the hidden ones under their own heading (the owner, 26 Nov: "the
                // visible ones reflect actually whats seen on home screen"). Each keeps its place in the order, so one
                // shown again goes back where it was.
                val onHome = items.filter { it !in hiddenItems }
                val off = items.filter { it in hiddenItems }
                items(onHome, key = { it.id }) { section -> SectionLine(section, first = section == onHome.firstOrNull()) }
                if (off.isNotEmpty()) {
                    item(key = "hiddenHeading") {
                        Text(
                            "Hidden".uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = androidx.compose.ui.unit.TextUnit(0.08f, androidx.compose.ui.unit.TextUnitType.Em),
                            modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null).fillMaxWidth().hairlineAbove()
                                .padding(start = Space.Page, end = Space.Page, top = Space.Page, bottom = Space.Small).testTag("arrangeHiddenHeading"),
                        )
                    }
                    items(off, key = { it.id }) { section -> SectionLine(section, first = section == off.first()) }
                }
            }
        }
    }
}

/** The looks one of Home's rows can take (issue #655): its own, or one of the custom cards' looks. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun HomeLookDialog(
    section: HomeSection,
    current: HomeLook,
    titleShown: Boolean,
    onTitleShown: (Boolean) -> Unit,
    // Whether its cards show their lines (the owner, 22 Nov).
    linesShown: Boolean = true,
    onLinesShown: (Boolean) -> Unit = {},
    onPick: (HomeLook) -> Unit,
    onDismiss: () -> Unit,
    gridSize: Int = HomeLook.GRID_MOST,
    onGridSize: (Int) -> Unit = {},
    coverGrid: app.siansa.data.CoverGridSize = app.siansa.data.CoverGridSize.DEFAULT,
    onCoverGrid: (app.siansa.data.CoverGridSize) -> Unit = {},
    // Its look's own layout (the owner, 20 Nov): shortcut tiles, a featured cover grid, a hero banner.
    variant: app.siansa.data.LookVariant? = null,
    onVariant: (app.siansa.data.LookVariant) -> Unit = {},
    // How its cards' pictures show (the owner, 20 Nov).
    picture: app.siansa.data.PictureStyle = app.siansa.data.PictureStyle.AUTO,
    onPicture: (app.siansa.data.PictureStyle) -> Unit = {},
    // And the cards at its hero's foot theirs, chosen apart (the owner, 22 Nov).
    heroCardPicture: app.siansa.data.PictureStyle = app.siansa.data.PictureStyle.AUTO,
    onHeroCardPicture: (app.siansa.data.PictureStyle) -> Unit = {},
    // Where its pictures' colours come from (the owner, 20 Nov).
    pictureColours: app.siansa.data.PictureColours = app.siansa.data.PictureColours.COVERS,
    onPictureColours: (app.siansa.data.PictureColours) -> Unit = {},
    // Shortcuts alone: how many rows of tiles it shows (the owner, 23 Nov); null for every other row.
    shortcutRows: Int? = null,
    onShortcutRows: (Int) -> Unit = {},
    // Artist radios: whose artists (null: not offered).
    artistsFrom: app.siansa.mix.ArtistsFrom? = null,
    onArtistsFrom: (app.siansa.mix.ArtistsFrom) -> Unit = {},
    // Its cards' colour (the owner, 13 Nov): its own, or [CardLook.AS_HOME], which is [homeCardLook].
    cardLook: CardLook = CardLook.AS_HOME,
    homeCardLook: CardLook = CardLook.DEFAULT,
    onCardLook: (CardLook) -> Unit = {},
    // Your cards: each card's look is its own, chosen in Settings; this opens it (null: the row's looks are listed).
    onOpenSettings: (() -> Unit)? = null,
    // As a carousel, each name and its details on the cover or under the row (the owner, 19 Nov).
    // Where its cards show their names (the owner, 21 Nov): the same choice for every look with pictures.
    names: app.siansa.data.CardNames = app.siansa.data.CardNames.AUTO,
    onNames: (app.siansa.data.CardNames) -> Unit = {},
    // As a carousel, big, compact or small (the owner, 20 Nov).
    crateSize: app.siansa.data.CrateSize = app.siansa.data.CrateSize.BIG,
    onCrateSize: (app.siansa.data.CrateSize) -> Unit = {},
    // Whether the row is on Home at all, and back to how it came (the owner, 22 Nov).
    onHome: Boolean = true,
    onOnHome: (Boolean) -> Unit = {},
    onReset: () -> Unit = {},
    // As a sheet over the page; false draws its contents alone, with [opened] of its lines open, for the screenshot test.
    sheet: Boolean = true,
    opened: Set<String> = emptySet(),
) {
    // The switch shows its change at once: the dialog stays open for the look.
    var home by remember(section) { mutableStateOf(onHome) }
    var shown by remember(section) { mutableStateOf(titleShown) }
    var lines by remember(section) { mutableStateOf(linesShown) }
    var rows by remember(section) { mutableStateOf(shortcutRows ?: SHORTCUT_ROWS.last) }
    var look by remember(section) { mutableStateOf(current) }
    var tiles by remember(section) { mutableStateOf(gridSize) }
    var coverGridSize by remember(section) { mutableStateOf(coverGrid) }
    var layout by remember(section) { mutableStateOf(variant) }
    var pictureStyle by remember(section) { mutableStateOf(picture) }
    var heroCardStyle by remember(section) { mutableStateOf(heroCardPicture) }
    var pictureColour by remember(section) { mutableStateOf(pictureColours) }
    var cardNames by remember(section) { mutableStateOf(names) }
    var size by remember(section) { mutableStateOf(crateSize) }
    var colour by remember(section) { mutableStateOf(cardLook) }
    var from by remember(section) { mutableStateOf(artistsFrom ?: app.siansa.mix.ArtistsFrom.BOTH) }
    // One sheet in three parts (the owner, 22 Nov, from the mockup): switches for what shows, then the look, then the
    // cards. Each choice is one line with what's chosen on it; a tap opens its options under it. Only what the chosen
    // look has is listed.
    var open by remember(section) { mutableStateOf(opened) }
    fun toggle(part: String) { open = if (part in open) open - part else open + part }
    val asHome = asHomeCardLook(section, homeCardLook)
    val variants = if (look == HomeLook.AS_IS && section == HomeSection.QUICK) app.siansa.data.LookVariant.of(HomeLook.GRID) else app.siansa.data.LookVariant.of(look)
    val style = layout?.takeIf { it in variants } ?: variants.firstOrNull()
    val heroCards = look == HomeLook.BANNER && (style?.cardsUnder ?: 0) > 0
    val contents: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = Space.Section).verticalScroll(rememberScrollState()).testTag("homeLookDialog")) {
            // What it is: a card in its colour, its name, and its look and pictures in a few words.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Medium), modifier = Modifier.padding(bottom = Space.Small)) {
                CardLookSample(if (colour == CardLook.AS_HOME) asHome else colour, Modifier.size(SHEET_SAMPLE))
                Column(Modifier.weight(1f)) {
                    Text(section.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (onOpenSettings == null) {
                        Text(rowSummary(look, style, pictureStyle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("rowSummary"))
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("rowSettingsDone")) { Text("Done", fontWeight = FontWeight.Bold) }
            }

            SheetPart("Show")
            SwitchLine("On Home", "Show this row", home, "rowOnHome") { home = it; onOnHome(it) }
            SheetRule()
            // Its title on Home (issue #961): off leaves only the gap above the section.
            SwitchLine("Title", "The heading above it", shown, "homeTitleShown") { shown = it; onTitleShown(it) }
            if (onOpenSettings == null) {
                SheetRule()
                // Its cards' lines (the owner, 22 Nov): what's in a mix, an album's artist, on the card or under it.
                SwitchLine("Descriptions", "The small line on or under each card", lines, "homeCardLines") { lines = it; onLinesShown(it) }
            }
            if (artistsFrom != null) {
                SheetRule()
                ValueLine("Artists from", from.label, "from" in open, "rowArtistsFrom") { toggle("from") }
                if ("from" in open) {
                    SegmentBar(app.siansa.mix.ArtistsFrom.entries, from, { it.label }, { "artistsFrom-${it.id}" }, Modifier.padding(bottom = Space.Medium)) { from = it; onArtistsFrom(it) }
                }
            }

            SheetPart("Look")
            if (onOpenSettings != null) {
                // Your cards: each is its own row in its own look, set where the card is made.
                Text("Each of your cards has its own look, set with the card.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onOpenSettings, modifier = Modifier.testTag("homeLookCardsSettings")) { Text("Your cards in Settings") }
            } else {
                // Every look for every row (#1261): Siansa Beats' rows and Shortcuts too.
                ValueLine("Look", look.label, "look" in open, "rowLook") { toggle("look") }
                if ("look" in open) LookCards(look, Modifier.padding(bottom = Space.Medium)) { look = it; onPick(it) }
                if (variants.size > 1 && style != null) {
                    SheetRule()
                    // Shortcuts' four shapes on a slider (the owner, 23 Nov): a bar of four broke their names. Open, the
                    // chosen one's name stands under its stop only, so the line above doesn't say it twice.
                    val onSlider = look == HomeLook.AS_IS && section == HomeSection.QUICK
                    ValueLine(variantTitle(look), if (onSlider && "style" in open) "" else style.label, "style" in open, "rowStyle") { toggle("style") }
                    if ("style" in open) {
                        if (onSlider) {
                            StopSlider(variants, style, { it.label }, { "lookVariant-${it.id}" }, Modifier.padding(bottom = Space.Medium), sliderTag = "rowStyleSlider", onlyChosen = true) { layout = it; onVariant(it) }
                        } else {
                            SegmentBar(variants, style, { shortLabel(it) }, { "lookVariant-${it.id}" }, Modifier.padding(bottom = Space.Medium)) { layout = it; onVariant(it) }
                        }
                    }
                }
                // How many rows of shortcuts (the owner, 23 Nov: "no way to set number of shortcuts vertically… minimum
                // 1 maximum 4"), for Shortcuts as it is; as a Shortcut grid it's Tiles, below.
                if (shortcutRows != null && look == HomeLook.AS_IS) {
                    SheetRule()
                    ValueLine("Rows", "$rows", "rows" in open, "rowRows") { toggle("rows") }
                    if ("rows" in open) {
                        StopSlider(SHORTCUT_ROWS.toList(), rows, { "$it" }, { "shortcutRows-$it" }, Modifier.padding(bottom = Space.Medium), sliderTag = "rowRowsSlider") { rows = it; onShortcutRows(it) }
                    }
                }
                if (look == HomeLook.GRID) {
                    SheetRule()
                    ValueLine("Tiles", "$tiles", "tiles" in open, "rowTiles") { toggle("tiles") }
                    if ("tiles" in open) GridSizeChips(tiles, Modifier.padding(bottom = Space.Medium)) { tiles = it; onGridSize(it) }
                }
                // One featured has its own six; the columns and rows are for an even grid.
                if (look == HomeLook.COVER_GRID && style != app.siansa.data.LookVariant.FEATURED) {
                    SheetRule()
                    ValueLine("Columns and rows", coverGridSize.label, "grid" in open, "rowCoverGrid") { toggle("grid") }
                    if ("grid" in open) CoverGridSteppers(coverGridSize, Modifier.padding(bottom = Space.Medium)) { coverGridSize = it; onCoverGrid(it) }
                }
                if (look in HomeLook.SIZED) {
                    SheetRule()
                    ValueLine("Size", size.label, "size" in open, "rowSize") { toggle("size") }
                    if ("size" in open) {
                        SegmentBar(app.siansa.data.CrateSize.entries, size, { it.label }, { "crateSize-" + it.name.lowercase() }, Modifier.padding(bottom = Space.Medium)) { size = it; onCrateSize(it) }
                    }
                }
            }

            SheetPart("Cards")
            if (onOpenSettings == null) {
                // A hero with cards under it: the hero's picture, and the cards' own (the owner, 22 Nov).
                ValueLine(if (heroCards) "Picture of the big card" else "Pictures", pictureStyle.label, "picture" in open, "rowPicture") { toggle("picture") }
                if ("picture" in open) PictureChips(pictureStyle, "pictureStyle") { pictureStyle = it; onPicture(it) }
                if (heroCards) {
                    SheetRule()
                    ValueLine("Pictures of the cards under it", heroCardStyle.label, "cards" in open, "rowHeroCardPicture") { toggle("cards") }
                    if ("cards" in open) PictureChips(heroCardStyle, "heroCardPictureStyle") { heroCardStyle = it; onHeroCardPicture(it) }
                }
                if (look in HomeLook.NAMED) {
                    SheetRule()
                    ValueLine("Names", cardNames.label, "names" in open, "rowNames") { toggle("names") }
                    if ("names" in open) {
                        SegmentBar(app.siansa.data.CardNames.entries, cardNames, { it.label }, { "cardNames-${it.id}" }, Modifier.padding(bottom = Space.Medium)) { cardNames = it; onNames(it) }
                    }
                }
                SheetRule()
            }
            // One Colour for the row (the owner, 25 Nov: "x2 colour selectors and its impossible to know what does
            // what"): the card's colour and its pictures' in one choice, Your listening's swatches, As Home first and
            // the sets of colours last. Every row has one (#1261); Shortcuts' tiles stay plain until it has its own.
            val chosenColour = RowColour.of(colour, pictureColour)
            val colourOptions = if (onOpenSettings == null) RowColour.all else RowColour.all.filter { it.set == app.siansa.data.PictureColours.COVERS }
            ValueLine("Colour", chosenColour.label(asHome), "colour" in open, "rowColour", swatch = { RowColourDot(chosenColour, asHome) }) { toggle("colour") }
            if ("colour" in open) {
                ColourSwatches(
                    colourOptions, chosenColour, { it.fade(asHome) }, { it.label(asHome) },
                    { "rowColour-${it.id}" }, Modifier.padding(bottom = Space.Medium).testTag("rowColours"),
                ) {
                    colour = it.look; pictureColour = it.set
                    onCardLook(it.look); onPictureColours(it.set)
                }
            }

            if (onOpenSettings == null) {
                TextButton(
                    onClick = {
                        // As it came: the choices here first, so the sheet shows it at once.
                        look = HomeLook.AS_IS; layout = null; tiles = HomeLook.GRID_MOST; coverGridSize = app.siansa.data.CoverGridSize.DEFAULT
                        pictureStyle = app.siansa.data.PictureStyle.AUTO; heroCardStyle = app.siansa.data.PictureStyle.AUTO
                        pictureColour = app.siansa.data.PictureColours.COVERS; cardNames = app.siansa.data.CardNames.AUTO
                        size = app.siansa.data.CrateSize.BIG; colour = CardLook.AS_HOME; shown = true; lines = true; open = emptySet()
                        onReset()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = Space.Page).testTag("rowReset"),
                ) { Text("Reset this row", color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(Space.Section))
        }
    }
    if (sheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) { contents() }
    } else contents()
}

/** A row's look and pictures in a few words, under its name: "Hero and 3 cards · Stickers". */
internal fun rowSummary(look: HomeLook, style: app.siansa.data.LookVariant?, picture: app.siansa.data.PictureStyle): String {
    val shape = if (look == HomeLook.BANNER && style != null) style.label else look.label
    return if (picture == app.siansa.data.PictureStyle.AUTO) shape else "$shape · ${picture.label}"
}

/** What a look calls its own layouts. */
private fun variantTitle(look: HomeLook): String = when (look) {
    HomeLook.COVER_GRID -> "Layout"
    HomeLook.BANNER -> "Style"
    else -> "Tiles' shape"
}

/** A layout's name short enough for a bar of four: "Hero + 3". */
internal fun shortLabel(variant: app.siansa.data.LookVariant): String = when (variant) {
    app.siansa.data.LookVariant.HERO_THREE -> "Hero + 3"
    app.siansa.data.LookVariant.HERO_FOUR -> "Hero + 4"
    else -> variant.label
}

/** Every picture style as a chip, and what the chosen one does. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PictureChips(style: app.siansa.data.PictureStyle, tag: String, onPick: (app.siansa.data.PictureStyle) -> Unit) {
    Column(Modifier.padding(bottom = Space.Medium).testTag(tag), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
            for (option in app.siansa.data.PictureStyle.entries) {
                FilterChip(selected = style == option, onClick = { onPick(option) }, label = { Text(option.label) }, modifier = Modifier.testTag("$tag-${option.id}"))
            }
        }
        Text(style.blurb + ".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val SHEET_SAMPLE = 44.dp

/**
 * A cover grid's columns and rows (#1263): each with − and +, columns 2 to 6, rows 1 to 6. Names show under the
 * pictures up to three columns.
 */
@Composable
internal fun CoverGridSteppers(size: app.siansa.data.CoverGridSize, modifier: Modifier = Modifier, onChange: (app.siansa.data.CoverGridSize) -> Unit) {
    Column(modifier.testTag("coverGridSize")) {
        GridStepper("Columns", size.columns, app.siansa.data.CoverGridSize.COLUMNS, "coverGridColumns") { onChange(size.copy(columns = it)) }
        GridStepper("Rows", size.rows, app.siansa.data.CoverGridSize.ROWS, "coverGridRows") { onChange(size.copy(rows = it)) }
        Text(
            if (size.named) "${size.count} covers, with their names." else "${size.count} covers; names show up to ${app.siansa.data.CoverGridSize.NAMED_COLUMNS} columns.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GridStepper(label: String, value: Int, range: IntRange, tag: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first, modifier = Modifier.testTag("$tag-less")) {
            Icon(androidx.compose.material.icons.Icons.Rounded.Remove, contentDescription = "Fewer ${label.lowercase()}")
        }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(tag))
        IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last, modifier = Modifier.testTag("$tag-more")) {
            Icon(androidx.compose.material.icons.Icons.Rounded.Add, contentDescription = "More ${label.lowercase()}")
        }
    }
}

/** A small capital label over a part of the sheet ("CARDS"), with a quiet note after it when given. */
@Composable
private fun SheetLabel(text: String, note: String? = null) {
    Row(Modifier.padding(start = Space.Page, end = Space.Page, top = Space.Page, bottom = Space.Small), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = androidx.compose.ui.unit.TextUnit(0.08f, androidx.compose.ui.unit.TextUnitType.Em))
        if (note != null) Text(" · $note", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
    }
}

/** A hairline along the top of a row in a grouped list. */
@Composable
private fun Modifier.hairlineAbove(): Modifier {
    val colour = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    return drawBehind { drawLine(colour, Offset.Zero, Offset(size.width, 0f), 1f) }  // spacing: exact (a hairline)
}

/**
 * One slider for the gaps between Home's sections (#554): five steps of the spacing scale, from tight to airy, so
 * every gap on Home grows or shrinks together.
 */
@Composable
internal fun SpacingRow(gap: Int, onChange: (Int) -> Unit) {
    // As wide as the longest step's name at the phone's font size, on one line (issue #694), so the slider keeps
    // its length as the name changes.
    val style = MaterialTheme.typography.bodyMedium
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val labelWidth = androidx.compose.runtime.remember(style, density) {
        with(density) { HOME_GAP_LABELS.maxOf { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }.toDp() }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.Page).testTag("homeSpacing"), verticalAlignment = Alignment.CenterVertically) {
        Text("Spacing", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(Space.Page))
        Slider(
            value = gap.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..AppSettings.HOME_GAP_MAX.toFloat(),
            steps = AppSettings.HOME_GAP_MAX - 1,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
            modifier = Modifier.weight(1f).testTag("homeSpacingSlider"),
        )
        Spacer(Modifier.width(Space.Page))
        Text(
            homeGapLabel(gap),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(labelWidth).testTag("homeSpacingLabel"),
        )
    }
}


/** How close to the list's edge a held row starts scrolling it, and how fast it scrolls at most (px a frame). */
private val AUTO_SCROLL_EDGE = 56.dp
internal const val AUTO_SCROLL_MAX = 18f

/**
 * How far to scroll the list this frame while a row is held between [top] and [bottom] (issue #701): nothing in the
 * middle; within [edge] of the viewport's end or start, faster the deeper the row is into that edge, up to [max].
 */
/**
 * Where a held row's drag [offset] stays so the row ([itemOffset], [itemSize]) is all inside the list's box
 * ([viewStart] to [viewEnd]): never dragged out of sight.
 */
internal fun clampDrag(offset: Float, itemOffset: Int, itemSize: Int, viewStart: Int, viewEnd: Int): Float {
    val least = (viewStart - itemOffset).toFloat()
    val most = (viewEnd - itemSize - itemOffset).toFloat()
    return if (most < least) offset else offset.coerceIn(least, most)
}

/**
 * Whether a held row at [top] ([size] tall) has gone past the middle of the row beside it ([targetOffset],
 * [targetSize]) going [down] (its foot past it) or up (its top past it): when the two swap.
 */
internal fun passesMiddle(top: Float, size: Int, targetOffset: Int, targetSize: Int, down: Boolean): Boolean {
    val middle = targetOffset + targetSize / 2f
    return if (down) top + size > middle else top < middle
}

/** Where a held row is laid out once it has swapped with the row at [targetOffset] ([targetSize] tall). */
internal fun offsetAfterSwap(size: Int, targetOffset: Int, targetSize: Int, down: Boolean): Int =
    if (down) targetOffset + targetSize - size else targetOffset

internal fun autoScrollSpeed(top: Float, bottom: Float, viewportStart: Float, viewportEnd: Float, edge: Float, max: Float = AUTO_SCROLL_MAX): Float {
    if (edge <= 0f) return 0f
    val intoEnd = bottom - (viewportEnd - edge)
    val intoStart = (viewportStart + edge) - top
    return when {
        intoEnd > 0f -> max * (intoEnd / edge).coerceIn(0f, 1f)
        intoStart > 0f -> -max * (intoStart / edge).coerceIn(0f, 1f)
        else -> 0f
    }
}

/** A small round swatch of [colours]: a wheel of its colours. */
@Composable
private fun ColoursSwatch(colours: app.siansa.data.PictureColours) {
    val swatch = remember(colours) { swatchOf(colours) }
    Box(
        Modifier.size(SWATCH).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (swatch.size > 2) androidx.compose.ui.graphics.Brush.sweepGradient(swatch + swatch.first()) else androidx.compose.ui.graphics.Brush.linearGradient(swatch)),
    )
}

private val SWATCH = 18.dp

/**
 * A row's one Colour (the owner, 25 Nov): a card colour ([look], with pictures from the covers), or a set of colours
 * ([set]: By mood, Siansa, Mono) each card takes its own from, card and picture, with no card colour of its own.
 */
internal data class RowColour(val look: CardLook, val set: app.siansa.data.PictureColours) {
    val id: String get() = if (set != app.siansa.data.PictureColours.COVERS) set.id else look.id

    fun label(asHome: CardLook): String = when {
        set != app.siansa.data.PictureColours.COVERS -> set.label
        look == CardLook.AS_HOME -> "As Home (${asHome.label})"
        else -> look.label
    }

    /** Its swatch: the colour's fade, or a set's first two colours. */
    fun fade(asHome: CardLook): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> =
        if (set == app.siansa.data.PictureColours.COVERS) cardSwatchFade(look, asHome)
        else swatchOf(set).let { it.first() to it.getOrElse(1) { _ -> it.first() } }

    companion object {
        /** As Home, Vivid, Your listening's six, then the sets. */
        val all: List<RowColour> = CardLook.entries.map { RowColour(it, app.siansa.data.PictureColours.COVERS) } +
            app.siansa.data.PictureColours.entries.filter { it != app.siansa.data.PictureColours.COVERS }.map { RowColour(CardLook.AS_HOME, it) }

        /** What a row has: its own card colour first (it wins over a set chosen before the two were one), else its set. */
        fun of(look: CardLook, set: app.siansa.data.PictureColours): RowColour =
            if (look != CardLook.AS_HOME) RowColour(look, app.siansa.data.PictureColours.COVERS) else RowColour(CardLook.AS_HOME, set)
    }
}

/** The small dot on a row's Colour line. */
@Composable
private fun RowColourDot(c: RowColour, asHome: CardLook) {
    if (c.set != app.siansa.data.PictureColours.COVERS) return ColoursSwatch(c.set)
    val (top, foot) = c.fade(asHome)
    Box(Modifier.size(SWATCH).clip(CircleShape).background(Brush.verticalGradient(listOf(top, foot))))
}

/** How many tiles a shortcut grid has (the owner, 10 Nov): 2, 4, 6 or 8, two to a row. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun GridSizeChips(size: Int, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(modifier.testTag("gridSize"), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.Small)) {
        HomeLook.GRID_SIZES.forEach { n ->
            androidx.compose.material3.FilterChip(
                selected = size == n,
                onClick = { onPick(n) },
                label = { Text("$n tiles") },
                modifier = Modifier.testTag("gridSize-$n"),
            )
        }
    }
}

/**
 * The rows of Arrange Home that show Siansa Beats' mixes while they're on (the owner, 17 Nov: their look is the row's),
 * and which of them: said under each row, so it's clear which cog changes them.
 */
/**
 * The look a row of Siansa Beats' mixes shows for [saved]: the carousel as its own look, else the one chosen. Every look
 * (#1261, the owner, 19 Nov: every row gets every option; it was the carousel and Tiles only, 17 Nov).
 */
internal fun beatsLook(saved: HomeLook?): HomeLook = if (saved == null || saved == HomeLook.AS_IS) HomeLook.CAROUSEL else saved

/** What "As Home" means for [section]'s colour: Home's choice, but Shortcuts' tiles stay dark (Ink) unless given their own. */
internal fun asHomeCardLook(section: HomeSection, homeCardLook: CardLook): CardLook =
    if (section == HomeSection.QUICK) CardLook.INK else homeCardLook

/**
 * The small tag on a row in Arrange Home's list (the owner, 26 Nov: "the mixes made by siansa could have a small tag"):
 * the rows Siansa Beats fills ([holds]); null for the others, Today's mix too (made on the phone, the owner, 26 Nov).
 */
internal fun arrangeTag(section: HomeSection, holds: Map<HomeSection, String>): String? = when {
    section in holds -> "Siansa Beats"
    // Today's mix is the phone's own, whoever makes the others (the owner, 26 Nov).
    else -> null
}

/** A small tag under a row's name. */
@Composable
private fun RowTag(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Space.Tight).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = Space.Small, vertical = 2.dp).testTag("arrangeRowTag"),  // spacing: exact (a tag's inner)
    )
}

internal fun beatsRows(on: Boolean): Map<HomeSection, String> =
    if (!on) emptyMap() else mapOf(
        // The rows are named as on Siansa Beats' Mixes page (the owner, 20 Nov), so this only says where they come from.
        HomeSection.CAROUSEL to "From Siansa Beats",
        HomeSection.MADE_FOR_YOU to "From Siansa Beats",
        HomeSection.MORE_OF_YOU to "From Siansa Beats: On repeat, Rediscover, New for you",
    )

/**
 * [arranged] with the sections it doesn't list (More of you while Siansa Beats' mixes are off) back where they were in
 * [before], after the same section, so a row hidden from the list keeps its place for when it's back.
 */
internal fun keepUnlisted(arranged: List<HomeSection>, before: List<HomeSection>): List<HomeSection> {
    val out = arranged.toMutableList()
    for (section in before) {
        if (section in out) continue
        val after = before.subList(0, before.indexOf(section)).lastOrNull { it in out }
        out.add(if (after == null) 0 else out.indexOf(after) + 1, section)
    }
    return out
}

/** A sheet over the page, or the same as a page of its own ([asPage]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetOrPage(asPage: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (asPage) content()
    else ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) { content() }
}

/**
 * Settings › Home › Arrange Home (the owner, 17 Nov: a page of its own in Home settings, not a link): the same as
 * the sheet behind Home's button, the order, what shows, each row's look and title, the cards' colours and the spacing.
 */
@Composable
fun ArrangeHomePage(nav: androidx.navigation.NavController) {
    Screen(title = "Arrange Home", nav = nav) { padding ->
        Box(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().testTag("arrangeHomePage")) {
            HomeSettingsSheet(nav, startOnSources = false, onDismiss = { nav.popBackStack() }, asPage = true)
        }
    }
}


/**
 * Your listening's colour (issue #1347): each as a round swatch of the card's own fade, the chosen one ringed and
 * named in bold. A tap picks it at once; Done closes.
 */
@Composable
internal fun ListenColourDialog(chosen: app.siansa.data.ListenColour, onPick: (app.siansa.data.ListenColour) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your listening") },
        text = { ListenColourSwatches(chosen, onPick) },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("listenColourDone")) { Text("Done") } },
    )
}

/** On this day's cog (the owner, 27 Nov): its three styles, each with a line on what it is, artists or albums, and how its pictures sit. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun MemoryStyleDialog(
    chosen: MemoryStyle,
    onPick: (MemoryStyle) -> Unit,
    artists: Boolean = false,
    onArtists: (Boolean) -> Unit = {},
    picture: MemoryPicture = MemoryPicture.COLLAGE,
    onPicture: (MemoryPicture) -> Unit = {},
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("On this day") },
        text = {
            Column(Modifier.selectableGroup().testTag("memoryStyles")) {
                MemoryStyle.entries.forEach { style ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .selectable(style == chosen, role = androidx.compose.ui.semantics.Role.RadioButton) { onPick(style) }
                            .testTag("memoryStyle-${style.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = style == chosen, onClick = null)
                        Column(Modifier.padding(start = Space.Medium)) {
                            Text(style.label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (style == chosen) FontWeight.Bold else FontWeight.Normal)
                            Text(style.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                SwitchRow("Artists' pictures", if (artists) "The artists you played most" else "Album covers", artists, tag = "memoryArtists", onChange = onArtists)
                Text("Pictures", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Space.Small))
                androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = Space.Small).testTag("memoryPictures"), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                    MemoryPicture.entries.forEach { p ->
                        FilterChip(selected = p == picture, onClick = { onPicture(p) }, label = { Text(p.label) }, modifier = Modifier.testTag("memoryPicture-${p.id}"))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("memoryStyleDone")) { Text("Done") } },
    )
}

/** The swatches, three to a row, evenly spread. */
@Composable
internal fun ListenColourSwatches(chosen: app.siansa.data.ListenColour, onPick: (app.siansa.data.ListenColour) -> Unit) {
    ColourSwatches(
        app.siansa.data.ListenColour.entries, chosen, { androidx.compose.ui.graphics.Color(it.top) to androidx.compose.ui.graphics.Color(it.foot) },
        { it.label }, { "listenColour-${it.id}" }, Modifier.testTag("listenColours"), title = "Colour", onPick = onPick,
    )
}

/**
 * Colours to pick by eye (issue #1347; the owner, 25 Nov: every card's colours as Your listening's): each a round swatch
 * of its [fade], top to foot, named under it; [chosen] ringed and named in bold. Three to a row, evenly spread; a row
 * that isn't full keeps the others' widths. [title] above them, when given.
 */
@Composable
internal fun <T> ColourSwatches(
    options: List<T>,
    chosen: T,
    fade: (T) -> Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>,
    label: (T) -> String,
    tag: (T) -> String,
    modifier: Modifier = Modifier,
    title: String? = null,
    onPick: (T) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.Page)) {
        if (title != null) Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        options.chunked(SWATCHES_A_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                row.forEach { c ->
                    val on = c == chosen
                    val (top, foot) = fade(c)
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(LISTEN_SWATCH_CORNER))
                            .selectable(selected = on, role = androidx.compose.ui.semantics.Role.RadioButton) { onPick(c) }
                            .padding(vertical = Space.Small).testTag(tag(c)),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.Small),
                    ) {
                        Box(
                            Modifier.size(LISTEN_SWATCH)
                                .border(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .padding(Space.Tight).clip(CircleShape)
                                .background(Brush.verticalGradient(listOf(top, foot))),
                        )
                        Text(label(c), style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
                    }
                }
                repeat(SWATCHES_A_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val SWATCHES_A_ROW = 3

/** A card colour's fade for its swatch: As Home shows Home's own. */
internal fun cardSwatchFade(look: CardLook, home: CardLook): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val c = (if (look == CardLook.AS_HOME) home else look).resolved.colour ?: return VIVID_SAMPLE
    return androidx.compose.ui.graphics.Color(c.top) to androidx.compose.ui.graphics.Color(c.foot)
}

private val LISTEN_SWATCH = 52.dp
private val LISTEN_SWATCH_CORNER = 12.dp
