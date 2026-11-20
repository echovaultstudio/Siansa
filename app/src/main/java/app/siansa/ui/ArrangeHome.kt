package app.siansa.ui

import app.siansa.data.carouselSize
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
 * Home's settings panel with the saved settings, from Home's button or Settings › Home page (issue #780): Sources
 * ([startOnSources]) or Arrange. Your cards' cog closes it and opens Settings › Home page › Cards.
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
    // The notices are the top of Home (issue #872), not a section to move: shown or not in Settings › Home page › Top of Home.
    val beatsRows = beatsRows(beatsMakesMixes && s.beatsFor(app.siansa.data.BeatsUse.MIXES))
    // More of you is Siansa Beats' alone: listed only while its mixes are on.
    val offered = if (HomeSection.MORE_OF_YOU in beatsRows) emptySet() else setOf(HomeSection.NOTICES, HomeSection.MORE_OF_YOU)
    ArrangeHomeSheet(s.homeOrder - offered - HomeSection.NOTICES, s.homeHidden, holds = beatsRows,
        sections = HomeSection.default - HomeSection.NOTICES - offered, gap = s.homeGap, onGapChange = {
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
        carouselOnCover = s.homeCarouselOnCover,
        carouselSizes = HomeSection.entries.associateWith { s.carouselSize(it) },
        onCarouselSizeChange = { section, size ->
            AppLog.i("Home", "${section.id} carousel: ${size.label}")
            graph.settings.setHomeCarouselSize(section, size)
        },
        onCarouselOnCoverChange = { section, onCover ->
            AppLog.i("Home", "${section.id} carousel: names ${if (onCover) "on the cover" else "under the row"}")
            graph.settings.setHomeCarouselOnCover(section, onCover)
        },
        onGridSizeChange = { section, size ->
            AppLog.i("Home", "${section.label}: $size tiles in its grid")
            graph.settings.setHomeGridSize(section, size)
        },
        coverGrids = s.homeCoverGrids,
        pictures = s.homePictures,
        onPictureChange = { section, style ->
            AppLog.i("Home", "${section.label}: pictures as ${style.label}")
            graph.settings.setHomePicture(section, style)
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
    // Drawn as a page of its own (Settings › Home page › Arrange Home) rather than a sheet.
    asPage: Boolean = false,
    // What another service puts in a row instead of its own (Siansa Beats' mixes, the owner, 17 Nov), said under it.
    holds: Map<HomeSection, String> = emptyMap(),
    // The gap between Home's sections, a step of the spacing scale (#554).
    gap: Int = AppSettings.HOME_GAP_DEFAULT,
    onGapChange: (Int) -> Unit = {},
    // Each row's look (issue #655), changed with its cog; Your cards' cog opens Settings › Home page › Cards, and
    // Notices' cog Settings › Home page › Top of Home (issue #784).
    looks: Map<HomeSection, HomeLook> = emptyMap(),
    // Each row's title shown or hidden (issue #961), in the same dialog as its look.
    untitled: Set<HomeSection> = emptySet(),
    onTitleShownChange: (HomeSection, Boolean) -> Unit = { _, _ -> },
    onLookChange: (HomeSection, HomeLook) -> Unit = { _, _ -> },
    onResetLooks: () -> Unit = {},
    // Tiles of a row shown as the shortcut grid (the owner, 10 Nov), picked in its look's dialog.
    gridSizes: Map<HomeSection, Int> = emptyMap(),
    carouselOnCover: Set<HomeSection> = emptySet(),
    onCarouselOnCoverChange: (HomeSection, Boolean) -> Unit = { _, _ -> },
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
    onPictureChange: (HomeSection, app.siansa.data.PictureStyle) -> Unit = { _, _ -> },
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
    picking?.let { section ->
        HomeLookDialog(
            section, looks[section] ?: HomeLook.AS_IS,
            titleShown = section !in untitled,
            onTitleShown = { onTitleShownChange(section, it) },
            // The grid stays open, so how many tiles it has can be picked there.
            onPick = { onLookChange(section, it); if (it != HomeLook.GRID && it != HomeLook.CAROUSEL && it != HomeLook.COVER_GRID && it != HomeLook.BANNER && !(section == HomeSection.QUICK && it == HomeLook.AS_IS)) picking = null },
            onDismiss = { picking = null },
            // Your cards each have their own look, chosen in Settings › Home page › Cards: a way there.
            onOpenSettings = if (section.lookable) null else { { picking = null; onOpenSettings(section) } },
            gridSize = gridSizes[section] ?: HomeLook.GRID_MOST,
            onGridSize = { onGridSizeChange(section, it) },
            coverGrid = coverGrids[section] ?: app.siansa.data.CoverGridSize.DEFAULT,
            onCoverGrid = { onCoverGridChange(section, it) },
            variant = variants[section],
            onVariant = { onVariantChange(section, it) },
            picture = pictures[section] ?: app.siansa.data.PictureStyle.AUTO,
            onPicture = { onPictureChange(section, it) },
            namesOnCover = section in carouselOnCover,
            onNamesOnCover = { onCarouselOnCoverChange(section, it) },
            crateSize = carouselSizes[section] ?: app.siansa.data.CrateSize.BIG,
            onCrateSize = { onCarouselSizeChange(section, it) },
            cardLook = cardLooks[section] ?: CardLook.AS_HOME,
            homeCardLook = cardLook,
            onCardLook = { onSectionCardLookChange(section, it) },
        )
    }
    var items by remember { mutableStateOf(order) }
    var hiddenItems by remember { mutableStateOf(hidden) }
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
        offset += dy
        val visible = list.layoutInfo.visibleItemsInfo
        val current = visible.firstOrNull { it.key == section.id } ?: return
        val middle = current.offset + offset + current.size / 2f
        val target = visible.firstOrNull { it.key != section.id && middle >= it.offset && middle <= it.offset + it.size } ?: return
        // Moving to or from the top row: stay at the same position, not with the row that was first, or the moved
        // row ends up above the screen and seems to vanish (#614).
        if (movesFirstVisible(current.index, target.index, list.firstVisibleItemIndex)) {
            list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        }
        items = items.moved(current.index, target.index)
        // The row is laid out at its new place next frame; keep it under the finger meanwhile.
        offset += current.offset - target.offset
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
            // The cards' colour (the owner, 13 Nov): four small cards to pick by eye; never the shortcut grid.
            SheetLabel("Cards")
            CardLookChooser(cardLook, CardLook.forHome, Modifier.padding(horizontal = Space.Page), onPick = onCardLookChange)
            Text(
                "Every card on Home, not the shortcuts. A card can have its own under its cog.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.Page, end = Space.Page, top = Space.Small),
            )
            SheetLabel("Spacing")
            SpacingRow(gap, onGapChange)
            SheetLabel("Sections", "hold to move")
            // One grouped list (the owner, 13 Nov): the rows on a raised surface, a hairline between them.
            LazyColumn(
                state = list, userScrollEnabled = dragging == null,
                modifier = Modifier.padding(horizontal = Space.Page).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).testTag("arrangeSections"),
            ) {
                items(items, key = { it.id }) { section ->
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
                        Row(Modifier.heightIn(min = 52.dp).padding(start = Space.Small).then(if (!lifted && section != items.first()) Modifier.hairlineAbove() else Modifier), verticalAlignment = Alignment.CenterVertically) {
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
                                // Only "Hidden" under a name: what a row holds and how it looks are under its cog (the owner, 20 Nov).
                                if (isHidden) Text("Hidden", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("homeLookLabel"))
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
                                // Every row's cog opens the same choices (#1261), Your cards' too.
                                onClick = { if (section != HomeSection.NOTICES) picking = section else onOpenSettings(section) },
                                modifier = Modifier.testTag("homeLookCog"),
                            ) {
                                Icon(
                                    Icons.Rounded.Settings,
                                    contentDescription = if (section == HomeSection.NOTICES) "Notices in Settings" else "Look of ${section.label}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The looks one of Home's rows can take (issue #655): its own, or one of the custom cards' looks. */
@Composable
private fun HomeLookDialog(
    section: HomeSection,
    current: HomeLook,
    titleShown: Boolean,
    onTitleShown: (Boolean) -> Unit,
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
    // Its cards' colour (the owner, 13 Nov): its own, or [CardLook.AS_HOME], which is [homeCardLook].
    cardLook: CardLook = CardLook.AS_HOME,
    homeCardLook: CardLook = CardLook.DEFAULT,
    onCardLook: (CardLook) -> Unit = {},
    // Your cards: each card's look is its own, chosen in Settings; this opens it (null: the row's looks are listed).
    onOpenSettings: (() -> Unit)? = null,
    // As a carousel, each name and its details on the cover or under the row (the owner, 19 Nov).
    namesOnCover: Boolean = false,
    onNamesOnCover: (Boolean) -> Unit = {},
    // As a carousel, big, compact or small (the owner, 20 Nov).
    crateSize: app.siansa.data.CrateSize = app.siansa.data.CrateSize.BIG,
    onCrateSize: (app.siansa.data.CrateSize) -> Unit = {},
) {
    // The switch shows its change at once: the dialog stays open for the look.
    var shown by remember(section) { mutableStateOf(titleShown) }
    var look by remember(section) { mutableStateOf(current) }
    var tiles by remember(section) { mutableStateOf(gridSize) }
    var coverGridSize by remember(section) { mutableStateOf(coverGrid) }
    var layout by remember(section) { mutableStateOf(variant) }
    var pictureStyle by remember(section) { mutableStateOf(picture) }
    var onCover by remember(section) { mutableStateOf(namesOnCover) }
    var size by remember(section) { mutableStateOf(crateSize) }
    var colour by remember(section) { mutableStateOf(cardLook) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(section.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("homeLookDialog")) {
                // Every row has a colour (#1261), Shortcuts too: its tiles stay plain until it has one of its own.
                val asHome = asHomeCardLook(section, homeCardLook)
                // One card in the chosen colour, then the choice: As Home follows Arrange Home's.
                CardLookSample(if (colour == CardLook.AS_HOME) asHome else colour, Modifier.fillMaxWidth(), big = true)
                Spacer(Modifier.height(Space.Medium))
                Text("Colour", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                    CardLook.entries.forEach { option ->
                        FilterChip(
                            selected = option == colour,
                            onClick = { colour = option; onCardLook(option) },
                            label = { Text(if (option == CardLook.AS_HOME) "As Home (${asHome.label})" else option.label) },
                            modifier = Modifier.testTag("cardLook-${option.id}"),
                        )
                    }
                }
                Text(
                    if (section == HomeSection.QUICK) "Just these tiles. As Home keeps them plain." else "Just this card. As Home follows the choice in Arrange Home.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.padding(vertical = Space.Small))
                // Its title on Home (issue #961): off leaves only the gap above the section.
                SectionTitleSwitch(shown) {
                    shown = it
                    onTitleShown(it)
                }
                HorizontalDivider(Modifier.padding(vertical = Space.Small))
                if (onOpenSettings == null) {
                    PictureChoice(pictureStyle) { pictureStyle = it; onPicture(it) }
                    HorizontalDivider(Modifier.padding(vertical = Space.Small))
                }
                if (onOpenSettings != null) {
                    // Your cards: each is its own row in its own look, set where the card is made.
                    Text("Look", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Each of your cards has its own look, set with the card.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onOpenSettings, modifier = Modifier.testTag("homeLookCardsSettings")) { Text("Your cards in Settings") }
                }
                // Every look for every row (#1261): Siansa Beats' rows and Shortcuts too.
                if (onOpenSettings == null) HomeLook.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { look = option; onPick(option) }.padding(vertical = Space.Small).testTag("homeLook-${option.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == look, onClick = { look = option; onPick(option) })
                        Spacer(Modifier.width(Space.Small))
                        Column(Modifier.weight(1f)) {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            Text(option.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Shortcuts' own look is its shortcut grid: its tiles can be laid out the same ways.
                    val gridLike = option == HomeLook.GRID || (section == HomeSection.QUICK && option == HomeLook.AS_IS)
                    if (gridLike && look == option) {
                        VariantChips("Tiles", HomeLook.GRID, layout, Modifier.padding(start = Space.Section, bottom = Space.Small)) { layout = it; onVariant(it) }
                    }
                    if (option == HomeLook.GRID && look == HomeLook.GRID) {
                        GridSizeChips(tiles, Modifier.padding(start = Space.Section, bottom = Space.Small)) { tiles = it; onGridSize(it) }
                    }
                    if (option == HomeLook.COVER_GRID && look == HomeLook.COVER_GRID) {
                        VariantChips("Layout", HomeLook.COVER_GRID, layout, Modifier.padding(start = Space.Section, bottom = Space.Small)) { layout = it; onVariant(it) }
                        // One featured has its own six; the columns and rows are for an even grid.
                        if (layout != app.siansa.data.LookVariant.FEATURED) {
                            CoverGridSteppers(coverGridSize, Modifier.padding(start = Space.Section, bottom = Space.Small)) { coverGridSize = it; onCoverGrid(it) }
                        }
                    }
                    if (option == HomeLook.BANNER && look == HomeLook.BANNER) {
                        VariantChips("Style", HomeLook.BANNER, layout, Modifier.padding(start = Space.Section, bottom = Space.Small)) { layout = it; onVariant(it) }
                    }
                    if (option == HomeLook.CAROUSEL && look == HomeLook.CAROUSEL) {
                        CrateSizeChoice(size, Modifier.padding(start = Space.Section, bottom = Space.Small)) { size = it; onCrateSize(it) }
                        CrateNamesChoice(onCover, Modifier.padding(start = Space.Section, bottom = Space.Small)) { onCover = it; onNamesOnCover(it) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

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

/** "Show its title" for one of Home's sections (issue #961). */
@Composable
internal fun SectionTitleSwitch(shown: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!shown) }.padding(vertical = Space.Small).testTag("homeTitleShown"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Show its title", style = MaterialTheme.typography.bodyLarge)
            Text("The heading above it on Home", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Space.Medium))
        androidx.compose.material3.Switch(checked = shown, onCheckedChange = onChange, modifier = Modifier.testTag("homeTitleSwitch"))
    }
}

/**
 * The ways [look] can lay its row out ([LookVariant], the owner, 20 Nov), under [label]: the one chosen, else the
 * first (how it looked before there was a choice).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun VariantChips(label: String, look: HomeLook, chosen: app.siansa.data.LookVariant?, modifier: Modifier = Modifier, onPick: (app.siansa.data.LookVariant) -> Unit) {
    val options = app.siansa.data.LookVariant.of(look)
    val current = chosen?.takeIf { it in options } ?: options.first()
    Column(modifier.testTag("lookVariant"), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
            for (option in options) {
                FilterChip(
                    selected = current == option,
                    onClick = { onPick(option) },
                    label = { Text(option.label) },
                    modifier = Modifier.testTag("lookVariant-${option.id}"),
                )
            }
        }
    }
}

/**
 * How a row's card pictures show (the owner, 20 Nov: "some do not look good with the cover mosaic"): each choice, and
 * what the one chosen does.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PictureChoice(style: app.siansa.data.PictureStyle, modifier: Modifier = Modifier, onPick: (app.siansa.data.PictureStyle) -> Unit) {
    Column(modifier.testTag("pictureStyle"), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
        Text("Pictures", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
            for (option in app.siansa.data.PictureStyle.entries) {
                FilterChip(
                    selected = style == option,
                    onClick = { onPick(option) },
                    label = { Text(option.label) },
                    modifier = Modifier.testTag("pictureStyle-${option.id}"),
                )
            }
        }
        Text(style.blurb + ".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How big a carousel's sleeves are (the owner, 20 Nov): big, compact with three in view, or a small strip. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun CrateSizeChoice(size: app.siansa.data.CrateSize, modifier: Modifier = Modifier, onPick: (app.siansa.data.CrateSize) -> Unit) {
    Column(modifier.testTag("crateSize"), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
        Text("Size", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
            for (option in app.siansa.data.CrateSize.entries) {
                FilterChip(
                    selected = size == option,
                    onClick = { onPick(option) },
                    label = { Text(option.label) },
                    modifier = Modifier.testTag("crateSize-" + option.name.lowercase()),
                )
            }
        }
    }
}

/** Where a carousel shows each name and its details (the owner, 19 Nov): under the row, or on the cover. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun CrateNamesChoice(onCover: Boolean, modifier: Modifier = Modifier, onPick: (Boolean) -> Unit) {
    Column(modifier.testTag("crateNames"), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
        Text("Name and details", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
            for ((value, label) in listOf(false to "Under the covers", true to "On the cover")) {
                FilterChip(
                    selected = onCover == value,
                    onClick = { onPick(value) },
                    label = { Text(label) },
                    modifier = Modifier.testTag(if (value) "crateNames-cover" else "crateNames-under"),
                )
            }
        }
    }
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

/** What "As Home" means for [section]'s colour: Home's choice, but Shortcuts' tiles are plain unless given their own. */
internal fun asHomeCardLook(section: HomeSection, homeCardLook: CardLook): CardLook =
    if (section == HomeSection.QUICK) CardLook.PLAIN else homeCardLook

internal fun beatsRows(on: Boolean): Map<HomeSection, String> =
    if (!on) emptyMap() else mapOf(
        HomeSection.CAROUSEL to "Siansa Beats: For now",
        HomeSection.MADE_FOR_YOU to "Siansa Beats: Made for you",
        HomeSection.MORE_OF_YOU to "Siansa Beats: On repeat, Rediscover, New for you",
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
 * Settings › Home page › Arrange Home (the owner, 17 Nov: a page of its own in Home settings, not a link): the same as
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
