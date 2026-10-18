package app.musix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** The even gap between the parts of the "Your colour" picker. */
internal val PICKER_GAP = Space.Page

/** Quick picks under the hue slider: red, orange, yellow, green, teal, indigo, purple and pink. */
internal val QUICK_COLOURS = listOf(
    0xFFE53935, 0xFFF4511E, 0xFFF6BF26, 0xFF33B679, 0xFF0E9AA7, 0xFF3F51B5, 0xFF8E24AA, 0xFFD81B60,
).map { it.toInt() }

/**
 * "Your colour" (issue #148, step 2): pick one colour with the hue slider, a quick pick or a hex code, and
 * Musix builds the light and dark theme from it, previewed side by side before it's used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YourColourSheet(start: Int, onDismiss: () -> Unit, onUse: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        YourColourPicker(start, onCancel = onDismiss, onUse = onUse, modifier = Modifier.padding(horizontal = Space.Page).padding(bottom = Space.Section))
    }
}

/** The picker itself (also used on its own by the spacing test). */
@Composable
internal fun YourColourPicker(start: Int, onCancel: () -> Unit, onUse: (Int) -> Unit, modifier: Modifier = Modifier) {
    var seed by remember { mutableIntStateOf(start) }
    var hex by remember { mutableStateOf(ThemeTones.toHex(start)) }
    // A new colour keeps the opacity already chosen (issue #181).
    fun pick(c: Int) {
        seed = ThemeTones.withOpacity(c, ThemeTones.opacityOf(seed))
        hex = ThemeTones.toHex(c)
    }

    Column(modifier.testTag("picker"), verticalArrangement = Arrangement.spacedBy(PICKER_GAP)) {
        Column {
            Text("Your colour", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Pick one colour. Musix makes the rest of the theme from it, in light and dark.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.Tight),
            )
        }

        HueSlider(ThemeTones.hueOf(seed), onHue = { pick(ThemeTones.fromHue(it)) }, Modifier.testTag("hue"))

        Row(Modifier.fillMaxWidth().testTag("quick"), horizontalArrangement = Arrangement.SpaceBetween) {
            QUICK_COLOURS.forEach { c ->
                val selected = (c and 0xFFFFFF) == (seed and 0xFFFFFF)
                Box(
                    Modifier
                        .size(34.dp)
                        .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape).padding(3.dp) else Modifier)  // spacing: exact (the ring around the chosen colour)
                        .clip(CircleShape)
                        .background(Color(c))
                        .selectable(selected, role = Role.RadioButton) { pick(c) }
                        .semantics { contentDescription = ThemeTones.toHex(c) },
                )
            }
        }

        OutlinedTextField(
            value = hex,
            onValueChange = { text ->
                hex = text.take(7)
                ThemeTones.parseHex(text)?.let { seed = ThemeTones.withOpacity(it, ThemeTones.opacityOf(seed)) }
            },
            singleLine = true,
            isError = ThemeTones.parseHex(hex) == null,
            label = { Text("Colour code") },
            leadingIcon = { Box(Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(Color(seed))) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth().testTag("hex"),
        )

        OpacitySlider(seed, onChange = { seed = ThemeTones.withOpacity(seed, it) }, Modifier.testTag("opacity"))

        Row(Modifier.fillMaxWidth().testTag("previews"), horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
            ColourPreview(seed, dark = false, Modifier.weight(1f))
            ColourPreview(seed, dark = true, Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(Space.Small))
            Button(
                onClick = { onUse(seed) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
            ) { Text("Use this colour") }
        }
    }
}

/** A rainbow bar to drag or tap along; the thumb shows the hue of the current colour. */
@Composable
private fun HueSlider(hue: Double, onHue: (Double) -> Unit, modifier: Modifier = Modifier) {
    val rainbow = remember { (0..12).map { Color(ThemeTones.fromHue(it * 30.0)) } }
    BoxWithConstraints(modifier.fillMaxWidth().height(36.dp)) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        fun at(x: Float) = onHue((x / widthPx).coerceIn(0f, 1f) * 360.0)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(widthPx) { detectTapGestures { at(it.x) } }
                .pointerInput(widthPx) { detectDragGestures { change, _ -> at(change.position.x) } }
                .semantics { contentDescription = "Hue" },
        ) {
            drawRoundRect(Brush.horizontalGradient(rainbow), cornerRadius = CornerRadius(size.height / 2))
        }
        val thumb = 30.dp
        Box(
            Modifier
                .offset { IntOffset(((hue / 360.0) * widthPx).roundToInt() - (thumb.toPx() / 2).roundToInt(), (3.dp).roundToPx()) }
                .size(thumb)
                .clip(CircleShape)
                .background(Color.White)
                .padding(3.dp)  // spacing: exact (the hue thumb's white ring)
                .clip(CircleShape)
                .background(Color(ThemeTones.fromHue(hue))),
        )
    }
}

/** A small mini player and two chips in [seed]'s light or dark theme. */
@Composable
private fun ColourPreview(seed: Int, dark: Boolean, modifier: Modifier) {
    val scheme = themeScheme(ThemeColours.YOURS, dark, seed = seed)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = scheme.surface,
        modifier = modifier.border(1.dp, scheme.outlineVariant, RoundedCornerShape(14.dp)),
    ) {
        Column(Modifier.padding(Space.Medium), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
            Text(if (dark) "Dark" else "Light", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(scheme.surfaceContainerHighest).padding(Space.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(5.dp)).background(Brush.linearGradient(listOf(Color(0xFF6B4FA8), Color(0xFF2B6AA8)))))
                Text("Midnight City", style = MaterialTheme.typography.labelMedium, color = scheme.onSurface, maxLines = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = Space.Small))
                Box(Modifier.size(26.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(16.dp), tint = scheme.onPrimary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                listOf("Chill", "Focus").forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSecondaryContainer,
                        modifier = Modifier.clip(CircleShape).background(scheme.secondaryContainer).padding(horizontal = Space.Medium, vertical = Space.Tight),
                    )
                }
            }
        }
    }
}

/**
 * How strongly the colour shows in the theme (issue #181): 100 % is the colour as picked, lower lays it more
 * thinly, towards grey. Text stays as readable at every opacity.
 */
@Composable
private fun OpacitySlider(seed: Int, onChange: (Double) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Opacity", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(ThemeTones.opacityLabel(seed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = ThemeTones.opacityOf(seed).toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = ThemeTones.MIN_OPACITY.toFloat()..1f,
            // Steps of 5 %: 20, 25 … 100.
            steps = ((1.0 - ThemeTones.MIN_OPACITY) * 20).roundToInt() - 1,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Opacity" },
        )
        Text(
            "How strongly your colour shows. Lower is softer, closer to grey.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
