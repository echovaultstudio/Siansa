package app.siansa.ui

import kotlinx.coroutines.delay
import app.siansa.playback.SongRegistry
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.siansa.data.AppSettings
import app.siansa.data.ReplayGainMode
import app.siansa.playback.CrossfadeMode
import app.siansa.playback.Levelling
import app.siansa.playback.LoudnessSource
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The gap between the pieces of this screen. */
internal val CROSSFADE_GAP = Space.Medium

/**
 * Settings › Playback › Crossfade and levelling (issues #60, #214): where song loudness and fade shapes come
 * from, levelling (Off, Track, Album: ReplayGain tags or the Siansa plugin's measurement, never both) with what
 * levels the song playing now, and crossfades.
 */
@Composable
fun CrossfadeScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var test by remember { mutableStateOf<String?>(null) }
    Screen(title = "Crossfade and levelling", nav = nav) { padding ->
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
            if (settings.loudnessSource == LoudnessSource.PLUGIN) {
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
                                modifier = Modifier.weight(1f).testTag("plugin-test"),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (it.startsWith("✗")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Hint("Songs the plugin hasn't measured yet use their tags.")
                }
            }
            LevellingNow(settings)
            HorizontalDivider()
            Heading("Levelling")
            ReplayGainRow(settings.replayGain) { graph.settings.save(graph.settings.current.copy(replayGain = it)) }
            Hint("Evens out loudness. Track: every song at the same level. Album: an album's loud and quiet songs keep their difference. Off: songs play as they are.")
            if (settings.loudnessSource == LoudnessSource.PLUGIN) {
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
    s.replayGain == ReplayGainMode.OFF -> "Levelling is off: songs play at their own loudness."
    s.loudnessSource == LoudnessSource.PLUGIN && s.replayGain == ReplayGainMode.ALBUM ->
        "Each song is levelled one way only: songs with album tags keep them, the rest by the plugin's measurement."
    s.loudnessSource == LoudnessSource.PLUGIN ->
        "Each song is levelled one way only: by the plugin's measurement, or by its ReplayGain tags until the plugin has measured it."
    else -> "Uses the songs' ReplayGain tags (${s.replayGain.label.lowercase()})."
}

private fun crossfadeHint(s: AppSettings): String = when (s.crossfade) {
    CrossfadeMode.OFF -> "Songs play back to back, with no gap."
    CrossfadeMode.FIXED -> "Each song fades into the next over the length below."
    CrossfadeMode.SMART -> "Songs on one album that run straight on stay gapless. " + if (s.loudnessSource == LoudnessSource.PLUGIN) {
        "Other fades start where a song goes quiet and last as long as the two songs need, up to the length below."
    } else {
        "Other fades use the length below; with the Siansa plugin they follow how each song ends and starts."
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

/** Off, Track or Album: whether and how songs are levelled (moved here from the equaliser, issue #214). */
@Composable
internal fun ReplayGainRow(selected: ReplayGainMode, onPick: (ReplayGainMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        listOf(ReplayGainMode.OFF, ReplayGainMode.TRACK, ReplayGainMode.ALBUM).forEach { mode ->
            FilterChip(selected = selected == mode, onClick = { onPick(mode) }, label = { Text(mode.label) })
        }
    }
}

/**
 * What levels the song playing now (issue #214): the Siansa plugin's measurement, its ReplayGain tags, or
 * nothing, and by how much. Looks again every few seconds, since the plugin's measurement can arrive after
 * the song starts.
 */
@Composable
internal fun LevellingNow(settings: AppSettings) {
    val graph = LocalGraph.current
    val player by graph.player.state.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(LEVELLING_REFRESH_MS)
            tick++
        }
    }
    val song = player.current?.let { SongRegistry.get(it.id) ?: it }
    val decision = remember(song, settings, tick) { Levelling.decide(song, settings, song?.let { graph.profiles.cached(it.id) }) }
    val (icon, text) = levellingNowText(song?.title, decision)
    Row(
        Modifier.fillMaxWidth().testTag("levelling-now"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        Icon(
            icon,
            null,
            tint = if (decision.source == Levelling.Source.PLUGIN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The line [LevellingNow] shows, and its icon: a tick when the Siansa plugin levels the song. */
internal fun levellingNowText(title: String?, d: Levelling.Decision): Pair<androidx.compose.ui.graphics.vector.ImageVector, String> {
    if (title == null) return Icons.Rounded.MusicOff to "Nothing playing right now."
    val db = "%+.1f dB".format(d.gainDb)
    return when (d.source) {
        Levelling.Source.PLUGIN -> Icons.Rounded.CheckCircle to "Now playing \"$title\": levelled by the Siansa plugin, $db."
        Levelling.Source.REPLAYGAIN -> Icons.Rounded.Info to "Now playing \"$title\": levelled by its ReplayGain tags, $db, not by the Siansa plugin (${d.why})."
        Levelling.Source.NONE -> Icons.Rounded.Info to "Now playing \"$title\": not levelled (${d.why})."
    }
}

private const val LEVELLING_REFRESH_MS = 3_000L
