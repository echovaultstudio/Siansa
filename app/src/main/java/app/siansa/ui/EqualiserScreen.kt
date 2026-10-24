package app.siansa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.playback.EqDesign
import app.siansa.playback.EqPreset
import app.siansa.playback.EqSettings
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Settings › Playback › Equaliser: on/off, presets, ten bands and the one pre-amp. Levelling (ReplayGain) is
 * under Crossfade and levelling (issue #214).
 */
@Composable
fun EqualiserScreen(nav: NavController) {
    val graph = LocalGraph.current
    val eq by graph.equaliser.state.collectAsStateWithLifecycle()
    Screen(title = "Equaliser", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            SwitchRow("Equaliser", eq.enabled) { graph.equaliser.set(eq.copy(enabled = it)) }
            Hint("Shapes the sound of everything this phone plays, for example more bass or clearer vocals. Not used while casting to a TV or speaker.")
            EqCurve(eq)
            // While off, the controls are faded but still work: using one switches the equaliser on.
            val controls = Modifier.alpha(if (eq.enabled) 1f else 0.5f)
            Heading("Preset")
            Column(controls) { EqPresetRow(eq.preset) { graph.equaliser.set(eq.copy(preset = it, enabled = true)) } }
            HorizontalDivider()
            Heading("Bands")
            Column(controls, verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
                EqSettings.BANDS.forEachIndexed { band, hz ->
                    DbSliderRow(
                        label = EqSettings.bandLabel(hz),
                        value = eq.gains[band],
                        range = EqSettings.MAX_DB,
                        onChange = { graph.equaliser.set(graph.equaliser.state.value.withBand(band, it).copy(enabled = true), save = false) },
                        onDone = { graph.equaliser.set(graph.equaliser.state.value) },
                    )
                }
            }
            Hint("Moving a band saves it as your Custom preset.")
            HorizontalDivider()
            Heading("Pre-amp")
            DbSliderRow(
                modifier = controls,
                label = "Level",
                value = eq.preampDb,
                range = EqSettings.MAX_PREAMP_DB,
                onChange = { graph.equaliser.set(graph.equaliser.state.value.copy(preampDb = it, enabled = true), save = false) },
                onDone = { graph.equaliser.set(graph.equaliser.state.value) },
            )
            Hint("Songs stay as loud as with the equaliser off; the pre-amp makes everything louder or quieter on top. Peaks that would distort are smoothed.")
        }
    }
}

/** Custom comes right after Flat, so it's in view once a band has been moved. */
private val presetOrder = listOf(EqPreset.FLAT, EqPreset.CUSTOM) + EqPreset.entries.filter { it != EqPreset.FLAT && it != EqPreset.CUSTOM }

/** The preset chips, scrolling sideways. */
@Composable
internal fun EqPresetRow(selected: EqPreset, onPick: (EqPreset) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        presetOrder.forEach { preset ->
            FilterChip(selected = selected == preset, onClick = { onPick(preset) }, label = { Text(preset.label) })
        }
    }
}

@Composable
private fun DbSliderRow(
    label: String,
    value: Float,
    range: Float,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(56.dp))
        Slider(
            value = value,
            onValueChange = { onChange(it.roundToInt().toFloat()) },
            onValueChangeFinished = onDone,
            valueRange = -range..range,
            steps = (range * 2).toInt() - 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            "%+.0f dB".format(value),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp),
        )
    }
}

/** How the bands shape the sound from 20 Hz to 20 kHz, with the flat line in the middle. */
@Composable
private fun EqCurve(eq: EqSettings) {
    val line = if (eq.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val grid = MaterialTheme.colorScheme.outlineVariant
    val design = remember(eq.gains) { EqDesign.of(eq.copy(preampDb = 0f), 48_000) }
    val shift = remember(design) { -20 * kotlin.math.log10(design.inputGain) }
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val maxDb = 15.0
        fun y(db: Double) = (size.height / 2 - (db / maxDb) * size.height / 2).toFloat()
        drawLine(grid, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 1.dp.toPx())
        val path = Path()
        val steps = 120
        for (i in 0..steps) {
            val hz = 20.0 * 1_000.0.pow(i / steps.toDouble())
            val x = (ln(hz / 20.0) / ln(1_000.0) * size.width).toFloat()
            // The shape of the bands only; the automatic headroom is shown by the pre-amp hint.
            val yy = y((design.responseDb(hz) + shift).coerceIn(-maxDb, maxDb))
            if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
        }
        drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
    }
}
