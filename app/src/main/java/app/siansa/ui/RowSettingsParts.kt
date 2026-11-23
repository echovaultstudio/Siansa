package app.siansa.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.siansa.data.HomeLook

/*
 * The parts of a row's settings sheet (the owner, 22 Nov: "make the arrange home cog settings in all cards more clear,
 * tidier, and easy to navigate… simple toggles", then of the mockup: "build it exactly like you showed me"): a few
 * named parts, a switch for what's on or off, and one line for each choice with what's chosen on it, which opens its
 * options under it.
 */

/** A part's heading: SHOW, LOOK, CARDS. */
@Composable
internal fun SheetPart(title: String) {
    Text(
        title.uppercase(),
        Modifier.padding(top = Space.Page, bottom = Space.Tight),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp)),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A switch on its line: what it is, a few words on it, and the switch. */
@Composable
internal fun SwitchLine(title: String, note: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = SHEET_LINE).clickable(role = Role.Switch) { onChange(!checked) }.testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Space.Medium))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag("${tag}Switch"))
    }
}

/** A choice on its line: what it is and what's chosen; a tap opens its options under it ([open]), another closes them. */
@Composable
internal fun ValueLine(title: String, value: String, open: Boolean, tag: String, swatch: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = SHEET_LINE)
            .clickable(onClickLabel = if (open) "Close $title" else "Choose $title", onClick = onClick).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(Space.Medium))
        if (swatch != null) { swatch(); Spacer(Modifier.width(Space.Small)) }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (open) FontWeight.Bold else FontWeight.Normal,
            color = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(Space.Tight))
        Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The hairline between two lines of a part. */
@Composable
internal fun SheetRule() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

/** A few options in one bar, the chosen one filled: Card, Hero, Hero + 3, Hero + 4. */
@Composable
internal fun <T> SegmentBar(options: List<T>, chosen: T, label: (T) -> String, tag: (T) -> String, modifier: Modifier = Modifier, onPick: (T) -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(Space.Tight),
        horizontalArrangement = Arrangement.spacedBy(Space.Tight),
    ) {
        for (option in options) {
            val on = option == chosen
            Box(
                Modifier.weight(1f).heightIn(min = SEGMENT).clip(RoundedCornerShape(8.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onPick(option) }
                    .semantics { selected = on }.testTag(tag(option)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option), Modifier.padding(horizontal = Space.Tight),
                    style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Every look as a small card with a picture of it, three across; the chosen one ringed. */
@Composable
internal fun LookCards(chosen: HomeLook, modifier: Modifier = Modifier, onPick: (HomeLook) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.Small)) {
        for (row in HomeLook.entries.chunked(LOOKS_ACROSS)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                for (look in row) {
                    val on = look == chosen
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .border(BorderStroke(2.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent), RoundedCornerShape(10.dp))
                            .clickable(role = Role.RadioButton, onClickLabel = look.hint) { onPick(look) }
                            .semantics { selected = on }.padding(Space.Small).testTag("homeLook-${look.id}"),
                        verticalArrangement = Arrangement.spacedBy(Space.Tight),
                    ) {
                        LookPicture(look, Modifier.fillMaxWidth().height(LOOK_PICTURE))
                        Text(look.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
                repeat(LOOKS_ACROSS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A look in a few blocks, so its card says what it is before its name does. */
@Composable
private fun LookPicture(look: HomeLook, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val soft = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val block = RoundedCornerShape(3.dp)
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(soft, Color.Transparent))).padding(Space.Tight), contentAlignment = Alignment.Center) {
        when (look) {
            HomeLook.TILES, HomeLook.AS_IS -> Row(horizontalArrangement = Arrangement.spacedBy(Space.Tight)) {
                repeat(3) { Box(Modifier.weight(1f).height(LOOK_BLOCK).clip(block).background(ink)) }
            }
            HomeLook.CAROUSEL -> Row(horizontalArrangement = Arrangement.spacedBy(Space.Tight), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(LOOK_BLOCK_SMALL).clip(block).background(soft))
                Box(Modifier.weight(1.6f).height(LOOK_BLOCK_BIG).clip(block).background(ink))
                Box(Modifier.weight(1f).height(LOOK_BLOCK_SMALL).clip(block).background(soft))
            }
            HomeLook.GRID, HomeLook.COVER_GRID -> Column(verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Tight)) {
                        repeat(if (look == HomeLook.GRID) 2 else 3) { Box(Modifier.weight(1f).height(LOOK_BLOCK_SMALL).clip(block).background(ink)) }
                    }
                }
            }
            HomeLook.BANNER -> Box(Modifier.fillMaxWidth().height(LOOK_BLOCK_BIG).clip(block).background(ink))
            HomeLook.LIST, HomeLook.COVER_ROWS -> Column(verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
                repeat(3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Tight), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(LOOK_ROW).height(LOOK_ROW).clip(block).background(ink))
                        Box(Modifier.weight(1f).height(LOOK_LINE).clip(block).background(soft))
                    }
                }
            }
            HomeLook.BUTTON -> Box(Modifier.fillMaxWidth().height(LOOK_BLOCK_SMALL).clip(RoundedCornerShape(50)).background(ink))
        }
    }
}

private val SHEET_LINE = 52.dp
private val SEGMENT = 36.dp
private const val LOOKS_ACROSS = 3
private val LOOK_PICTURE = 44.dp
private val LOOK_BLOCK = 24.dp
private val LOOK_BLOCK_BIG = 30.dp
private val LOOK_BLOCK_SMALL = 14.dp
private val LOOK_ROW = 8.dp
private val LOOK_LINE = 4.dp

/**
 * One of a few choices on a slider (the owner, 23 Nov: "can we also use sliders for tiles shapes with perfect text
 * spacing"; a bar of four broke "Three a row" over two lines): a stop for each of [options], and under the slider each
 * one's [label], centred on its own stop (the first and last kept inside the edges), the chosen one in the accent
 * colour. A tap on a label picks it too. [onPick] is told as the thumb comes to a stop.
 */
@Composable
internal fun <T> StopSlider(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    tag: (T) -> String,
    modifier: Modifier = Modifier,
    sliderTag: String = "stopSlider",
    onPick: (T) -> Unit,
) {
    if (options.size < 2) return
    val at = options.indexOf(selected).coerceAtLeast(0)
    Column(modifier.fillMaxWidth()) {
        androidx.compose.material3.Slider(
            value = at.toFloat(),
            onValueChange = { v -> stopFor(v, options.size).takeIf { it != at }?.let { onPick(options[it]) } },
            valueRange = 0f..(options.size - 1).toFloat(),
            steps = options.size - 2,
            modifier = Modifier.fillMaxWidth().testTag(sliderTag),
        )
        androidx.compose.ui.layout.Layout(
            content = {
                options.forEach { option ->
                    val chosen = option == selected
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                        color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier
                            .clip(RoundedCornerShape(STOP_LABEL_CORNER))
                            .selectable(selected = chosen, role = androidx.compose.ui.semantics.Role.RadioButton) { onPick(option) }
                            .padding(horizontal = Space.Tight, vertical = Space.Tight)
                            .testTag(tag(option)),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("stopLabels"),
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            // Each label may be as wide as its share of the row: a long one breaks on to two lines, centred.
            val each = androidx.compose.ui.unit.Constraints(maxWidth = (width / measurables.size).coerceAtLeast(1))
            val placed = measurables.map { it.measure(each) }
            val height = placed.maxOf { it.height }
            layout(width, height) {
                placed.forEachIndexed { i, p -> p.placeRelative(stopLabelLeft(i, placed.size, width, p.width), 0) }
            }
        }
    }
}

/** The stop nearest [value] on a slider of [count] stops. */
internal fun stopFor(value: Float, count: Int): Int = kotlin.math.round(value).toInt().coerceIn(0, count - 1)

/**
 * Where the label of stop [index] of [count] starts in a row [width] wide, itself [own] wide: centred on its stop
 * (stops are evenly spread from edge to edge), and never past an edge.
 */
internal fun stopLabelLeft(index: Int, count: Int, width: Int, own: Int): Int {
    val centre = if (count <= 1) width / 2f else width * index / (count - 1).toFloat()
    return (centre - own / 2f).toInt().coerceIn(0, (width - own).coerceAtLeast(0))
}

private val STOP_LABEL_CORNER = 8.dp
