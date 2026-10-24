package app.musix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.musix.log.AppLog

/** The gap between the rows of colour swatches, and around them. */
internal val SWATCH_GAP = Space.Page

/**
 * Settings → Themes (issue #148): light, dark or following the phone; pure black; and where the colours
 * come from (Musix, the wallpaper on Android 12+, or a preset). A preview at the top shows the choice at
 * once. Album, artist and now playing pages keep their colours from the cover.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemesScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val choice = themeChoiceOf(settings)
    val dark = isDark(choice)

    var picking by rememberSaveable { mutableStateOf(false) }

    fun save(mode: ThemeMode = choice.mode, colours: ThemeColours = choice.colours, pureBlack: Boolean = choice.pureBlack, seed: Int = choice.seed) {
        val yours = if (colours == ThemeColours.YOURS) " ${ThemeTones.toHex(seed)}" else ""
        AppLog.i("Themes", "Theme: ${colours.label}$yours, ${mode.label}${if (pureBlack) ", pure black" else ""}")
        graph.settings.setTheme(mode.name, colours.name, pureBlack, seed)
    }

    if (picking) {
        YourColourSheet(
            start = choice.seed,
            onDismiss = { picking = false },
            onUse = { seed ->
                picking = false
                save(colours = ThemeColours.YOURS, seed = seed)
            },
        )
    }

    Screen(title = "Themes", nav = nav) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState())) {
            ThemePreview(Modifier.padding(horizontal = Space.Page, vertical = Space.Small))

            ThemeLabel("Light or dark")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Space.Page)) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = choice.mode == mode,
                        onClick = { save(mode = mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(mode.label) }
                }
            }
            Text(
                "Auto follows your phone, including its dark-mode schedule.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.Page, end = Space.Page, top = Space.Small),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Space.Small)
                    .toggleable(choice.pureBlack, role = Role.Switch) { save(pureBlack = it) }
                    .padding(horizontal = Space.Page, vertical = Space.Medium)
                    .alpha(if (dark) 1f else 0.6f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Pure black", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Black background when dark. Saves battery on OLED screens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Space.Medium))
                Switch(checked = choice.pureBlack, onCheckedChange = null)
            }

            ThemeLabel("Colours")
            SwatchGrid(
                ThemeColours.available,
                selected = choice.colours,
                dark = dark,
                // "Your colour" opens the picker; the colour is used once it's confirmed there.
                onPick = { if (it == ThemeColours.YOURS) picking = true else save(colours = it) },
                modifier = Modifier.padding(horizontal = Space.Page),
            )
            Text(
                "Album, artist and now playing pages keep their colours from the cover.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.Page, end = Space.Page, top = SWATCH_GAP),
            )

            // One accent colour for everything that stands out (issues #313, #404).
            ThemeLabel("Accent colour")
            SelectionSwatches(choice.selection, onPick = { pick ->
                AppLog.i("Themes", "Accent colour: ${pick.label}")
                graph.settings.setSelectionColour(pick.name)
            }, modifier = Modifier.padding(horizontal = Space.Page))
            Text(
                "Buttons, hearts, the playing song, and anything that's on. It gets darker or lighter where it needs to stand out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.Page, end = Space.Page, top = SWATCH_GAP, bottom = Space.Section),
            )
        }
    }
}

@Composable
private fun ThemeLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Space.Page, end = Space.Page, top = Space.Section, bottom = Space.Medium),
    )
}

/** The colour choices, four to a row, each a circle of its main, second and third colours. */
@Composable
internal fun SwatchGrid(
    options: List<ThemeColours>,
    selected: ThemeColours,
    dark: Boolean,
    onPick: (ThemeColours) -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = SWATCH_GAP,
) {
    Column(modifier.testTag("swatches"), verticalArrangement = Arrangement.spacedBy(gap)) {
        options.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    Swatch(option, option == selected, dark, Modifier.weight(1f)) { onPick(option) }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Swatch(option: ThemeColours, selected: Boolean, dark: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = themeScheme(option, dark)
    val main = if (option == ThemeColours.MUSIX) Brand.Amber else scheme.primary
    Column(
        modifier.selectable(selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(58.dp)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(5.dp) else Modifier)  // spacing: exact (the ring around the chosen theme)
                .clip(CircleShape),
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(main))
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Box(Modifier.weight(1f).fillMaxSize().background(scheme.secondary))
                    Box(Modifier.weight(1f).fillMaxSize().background(scheme.tertiary))
                }
            }
            if (selected) Icon(Icons.Rounded.Check, null, Modifier.align(Alignment.Center), tint = Color.White)
            else if (option == ThemeColours.YOURS) Icon(Icons.Rounded.Add, "Pick your colour", Modifier.align(Alignment.Center), tint = Color.White)
        }
        Text(
            option.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.Small),
        )
        if (option == ThemeColours.YOURS && selected) {
            Text("Tap to change", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (option == ThemeColours.WALLPAPER) {
            Text("From your wallpaper", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** A small Home in the current theme: the greeting, a chip, two tiles and the mini player. */
@Composable
private fun ThemePreview(modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
    ) {
        Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Good evening", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.FilterList, null, tint = Accent)
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text("Only downloaded", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = Space.Medium, vertical = Space.Small))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                PreviewTile("Liked songs", Modifier.weight(1f), Brush.linearGradient(BrandGradient), heart = true)
                PreviewTile("Daily mix", Modifier.weight(1f), Brush.linearGradient(listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary)))
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(Space.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(Color(0xFF6B4FA8), Color(0xFF2B6AA8)))))
                Column(Modifier.weight(1f).padding(start = Space.Medium)) {
                    Text("Midnight City", style = MaterialTheme.typography.labelLarge)
                    Text("M83", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.size(38.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Pause, null, tint = OnAccent)
                }
            }
        }
    }
}

@Composable
private fun PreviewTile(name: String, modifier: Modifier, art: Brush, heart: Boolean = false) {
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(art), contentAlignment = Alignment.Center) {
            if (heart) Icon(Icons.Rounded.Favorite, null, Modifier.size(18.dp), tint = Color.White)
        }
        Text(name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = Space.Small))
    }
}

/** The swatch grid for the spacing check: two rows (Musix to Rose, Sunset and Mono). */
@Composable
internal fun SwatchGridForTest() {
    SwatchGrid(
        listOf(ThemeColours.MUSIX, ThemeColours.OCEAN, ThemeColours.FOREST, ThemeColours.ROSE, ThemeColours.SUNSET, ThemeColours.MONO),
        selected = ThemeColours.MUSIX,
        dark = false,
        onPick = {},
    )
}

/** The accent colours, as circles four to a row (issues #313, #404); "Match theme" shows the theme's own. */
@Composable
internal fun SelectionSwatches(selected: SelectionColour, onPick: (SelectionColour) -> Unit, modifier: Modifier = Modifier) {
    val themeColour = ThemeAccent
    Column(modifier.testTag("selectionSwatches"), verticalArrangement = Arrangement.spacedBy(SWATCH_GAP)) {
        SelectionColour.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    val colour = option.argb?.let { Color(it) } ?: themeColour
                    Column(
                        Modifier.weight(1f).selectable(option == selected, role = Role.RadioButton) { onPick(option) }.testTag("selection-${option.name}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .then(if (option == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape).padding(4.dp) else Modifier) // spacing: exact (the ring around the chosen colour)
                                .clip(CircleShape)
                                .background(colour),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (option == selected) Icon(Icons.Rounded.Check, null, tint = onSelectionColourOf(colour))
                        }
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = Space.Small),
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
