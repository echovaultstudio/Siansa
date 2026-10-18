package app.musix.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.musix.data.AppSettings
import app.musix.data.ReplayGainMode
import app.musix.playback.CrossfadeMode
import app.musix.playback.Levelling
import app.musix.playback.LoudnessSource
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The gap between the pieces of this screen. */
internal val CROSSFADE_GAP = Space.Medium

/**
 * Settings › Playback › Crossfade and loudness (issue #60): where song loudness and fade shapes come from,
 * the loudness songs are levelled to, and crossfades.
 */
@Composable
fun CrossfadeScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var test by remember { mutableStateOf<String?>(null) }
    Screen(title = "Crossfade and loudness", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(CROSSFADE_GAP),
        ) {
            Heading("Where loudness and fades come from")
            LoudnessSourceRow(settings.loudnessSource) {
                test = null
                graph.settings.setLoudness(source = it)
            }
            Hint(settings.loudnessSource.detail + ".")
            if (settings.loudnessSource == LoudnessSource.LUMAE) {
                if (!settings.audioMuseReady) {
                    Hint("Set up AudioMuse in Settings › Connections first. Until then, song tags are used.")
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
                        OutlinedButton(onClick = {
                            test = "Testing…"
                            scope.launch {
                                val result = graph.profiles.check()
                                test = (if (result.ok) "✓ " else "✗ ") + result.message
                            }
                        }) { Text("Test") }
                        test?.let {
                            Text(
                                it,
                                modifier = Modifier.weight(1f).testTag("lumae-test"),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (it.startsWith("✗")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Hint("Songs the plugin hasn't measured yet use their tags.")
                }
            }
            HorizontalDivider()
            Heading("Levelling")
            if (settings.loudnessSource == LoudnessSource.LUMAE) {
                LoudnessTargetRow(settings.loudnessTarget) { graph.settings.setLoudness(target = it) }
                Hint("How loud songs are brought to. −18 matches ReplayGain and leaves room for loud songs. −14 is louder, like Spotify, but quiet songs can't be raised to it.")
            }
            Hint(levellingHint(settings))
            HorizontalDivider()
            Heading("Crossfade")
            CrossfadeModeRow(settings.crossfade) { graph.settings.setCrossfade(mode = it) }
            Hint(crossfadeHint(settings))
            if (settings.crossfade != CrossfadeMode.OFF) {
                CrossfadeLengthRow(settings.crossfadeSeconds, smart = settings.crossfade == CrossfadeMode.SMART) {
                    graph.settings.setCrossfade(seconds = it)
                }
                Hint("Only on this phone: on a TV or speaker, songs play back to back. Not used with Repeat one, or while the sleep timer lets a song finish.")
            }
        }
    }
}

private fun levellingHint(s: AppSettings): String = when {
    s.replayGain == ReplayGainMode.OFF -> "Off: ReplayGain is off in Equaliser and ReplayGain."
    s.loudnessSource == LoudnessSource.LUMAE && s.replayGain == ReplayGainMode.ALBUM ->
        "ReplayGain is on Album: songs with album tags keep them, the rest are levelled from the plugin's measurement."
    s.loudnessSource == LoudnessSource.LUMAE -> "On, from the plugin's measurement of each song. Turn it off with ReplayGain in Equaliser and ReplayGain."
    else -> "Uses the ReplayGain tags (${s.replayGain.label.lowercase()}), set in Equaliser and ReplayGain."
}

private fun crossfadeHint(s: AppSettings): String = when (s.crossfade) {
    CrossfadeMode.OFF -> "Songs play back to back, with no gap."
    CrossfadeMode.FIXED -> "Each song fades into the next over the length below."
    CrossfadeMode.SMART -> "Songs on one album that run straight on stay gapless. " + if (s.loudnessSource == LoudnessSource.LUMAE) {
        "Other fades start where a song goes quiet and last as long as the two songs need, up to the length below."
    } else {
        "Other fades use the length below; with the Lumae plugin they follow how each song ends and starts."
    }
}

/** The choice of where loudness and fades come from. */
@Composable
internal fun LoudnessSourceRow(selected: LoudnessSource, onPick: (LoudnessSource) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        LoudnessSource.entries.forEach { source ->
            FilterChip(selected = selected == source, onClick = { onPick(source) }, label = { Text(source.label) })
        }
    }
}

@Composable
private fun LoudnessTargetRow(selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        Levelling.TARGETS.sorted().forEach { target ->
            FilterChip(selected = selected == target, onClick = { onPick(target) }, label = { Text("−${-target} LUFS") })
        }
    }
}

/** Off, Fixed or Smart. */
@Composable
internal fun CrossfadeModeRow(selected: CrossfadeMode, onPick: (CrossfadeMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        CrossfadeMode.entries.forEach { mode ->
            FilterChip(selected = selected == mode, onClick = { onPick(mode) }, label = { Text(mode.label) })
        }
    }
}

@Composable
private fun CrossfadeLengthRow(seconds: Int, smart: Boolean, onDone: (Int) -> Unit) {
    var value by remember(seconds) { mutableFloatStateOf(seconds.toFloat()) }
    val range = AppSettings.CROSSFADE_SECONDS
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (smart) "Up to" else "Length", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(56.dp))
        Slider(
            value = value,
            onValueChange = { value = it.roundToInt().toFloat() },
            onValueChangeFinished = { onDone(value.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            modifier = Modifier.weight(1f).testTag("crossfade-length"),
        )
        Text("${value.roundToInt()} s", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
    }
}
