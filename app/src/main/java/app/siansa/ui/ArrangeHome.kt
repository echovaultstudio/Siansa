package app.siansa.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Arrangement
import app.siansa.data.CardLook
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
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
 * Home's settings panel with the saved settings, from Home's button or Settings › Home (issue #780): Sources
 * ([startOnSources]) or Arrange. Your cards' cog closes it and opens Settings › Home page › Cards.
 */
@Composable
fun HomeSettingsSheet(nav: androidx.navigation.NavController, startOnSources: Boolean, onDismiss: () -> Unit) {
    // Sources is a sheet of its own (the owner, 12 Nov): Arrange isn't part of it.
    if (startOnSources) {
        SourcesSheet(onDismiss)
        return
    }
    val graph = LocalGraph.current
    val s by graph.settings.state.collectAsStateWithLifecycle()
    // The notices are the top of Home (issue #872), not a section to move: shown or not in Settings › Home page › Top of Home.
    ArrangeHomeSheet(s.homeOrder - HomeSection.NOTICES, s.homeHidden, gap = s.homeGap, onGapChange = {
        AppLog.i("Home", "Spacing: ${homeGapLabel(it)}")
        graph.settings.setHomeGap(it)
    }, onChange = {
        val order = listOf(HomeSection.NOTICES) + (it - HomeSection.NOTICES)
        AppLog.i("Home", "Arranged: ${HomeSection.format(order)}")
        graph.settings.setHomeOrder(order)
    }, onHiddenChange = {
        AppLog.i("Home", "Hidden: ${HomeSection.formatHidden(it).ifEmpty { "none" }}")
        graph.settings.setHomeHidden(it)
    }, onDismiss = onDismiss,
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
        onGridSizeChange = { section, size ->
            AppLog.i("Home", "${section.label}: $size tiles in its grid")
            graph.settings.setHomeGridSize(section, size)
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
    onGridSizeChange: (HomeSection, Int) -> Unit = { _, _ -> },
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
            onPick = { onLookChange(section, it); if (it != HomeLook.GRID) picking = null },
            onDismiss = { picking = null },
            gridSize = gridSizes[section] ?: HomeLook.GRID_MOST,
            onGridSize = { onGridSizeChange(section, it) },
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Space.Small)) {
            Row(Modifier.fillMaxWidth().padding(start = Space.Page, end = Space.Small), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Arrange Home", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                        Row(Modifier.height(52.dp).padding(start = Space.Small).then(if (!lifted && section != items.first()) Modifier.hairlineAbove() else Modifier), verticalAlignment = Alignment.CenterVertically) {
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
                            Column(Modifier.weight(1f).padding(start = Space.Tight).alpha(if (isHidden) 0.6f else 1f)) {
                                Text(
                                    section.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                                // A row in another look says which (issue #655), and its cards' own colour; a hidden one says so.
                                val line = if (isHidden) "Hidden" else listOfNotNull(looks[section]?.label, cardLooks[section]?.label).joinToString(" · ").ifEmpty { null }
                                if (line != null) Text(line, style = MaterialTheme.typography.bodySmall, color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else Accent, modifier = Modifier.testTag("homeLookLabel"))
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
                                onClick = { if (section.lookable) picking = section else onOpenSettings(section) },
                                modifier = Modifier.testTag("homeLookCog"),
                            ) {
                                Icon(
                                    Icons.Rounded.Settings,
                                    contentDescription = when {
                                        section.lookable -> "Look of ${section.label}"
                                        section == HomeSection.NOTICES -> "Notices in Settings"
                                        else -> "Your cards in Settings"
                                    },
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
    // Its cards' colour (the owner, 13 Nov): its own, or [CardLook.AS_HOME], which is [homeCardLook].
    cardLook: CardLook = CardLook.AS_HOME,
    homeCardLook: CardLook = CardLook.DEFAULT,
    onCardLook: (CardLook) -> Unit = {},
) {
    // The switch shows its change at once: the dialog stays open for the look.
    var shown by remember(section) { mutableStateOf(titleShown) }
    var look by remember(section) { mutableStateOf(current) }
    var tiles by remember(section) { mutableStateOf(gridSize) }
    var colour by remember(section) { mutableStateOf(cardLook) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(section.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("homeLookDialog")) {
                if (section != HomeSection.QUICK) {
                    // One card in the chosen colour, then the choice: As Home follows Arrange Home's.
                    CardLookSample(if (colour == CardLook.AS_HOME) homeCardLook else colour, Modifier.fillMaxWidth(), big = true)
                    Spacer(Modifier.height(Space.Medium))
                    Text("Colour", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                        CardLook.entries.forEach { option ->
                            FilterChip(
                                selected = option == colour,
                                onClick = { colour = option; onCardLook(option) },
                                label = { Text(if (option == CardLook.AS_HOME) "As Home (${homeCardLook.label})" else option.label) },
                                modifier = Modifier.testTag("cardLook-${option.id}"),
                            )
                        }
                    }
                    Text("Just this card. As Home follows the choice in Arrange Home.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(vertical = Space.Small))
                }
                // Its title on Home (issue #961): off leaves only the gap above the section.
                SectionTitleSwitch(shown) {
                    shown = it
                    onTitleShown(it)
                }
                HorizontalDivider(Modifier.padding(vertical = Space.Small))
                // Shortcuts is the grid already: its size is set in Settings › Home page.
                HomeLook.entries.filter { it != HomeLook.GRID || section != HomeSection.QUICK }.forEach { option ->
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
                    if (option == HomeLook.GRID && look == HomeLook.GRID) {
                        GridSizeChips(tiles, Modifier.padding(start = Space.Section, bottom = Space.Small)) { tiles = it; onGridSize(it) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
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
