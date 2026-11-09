package app.siansa.ui

import kotlinx.coroutines.delay
import app.siansa.playback.SongRegistry
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import app.siansa.log.AppLog
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
import app.siansa.playback.BeatMatchMode
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
                        OutlinedButton(modifier = Modifier.focusRing(), onClick = {
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
            // Dolby songs go to the receiver unchanged, so levelling can't reach them (issue #930).
            if (app.siansa.BuildConfig.TV_APP && settings.audioPassthrough != app.siansa.playback.PassthroughMode.OFF) {
                val louder = settings.dolbyLouderDb
                FoldSection("Dolby songs on your receiver", if (louder == 0) "As they are" else "+$louder dB", tag = "dolby-louder") {
                    DolbyLouderRow(louder, graph.settings::setDolbyLouder)
                    Hint(
                        "Dolby Atmos and Dolby Digital Plus songs go to your receiver as they are, and most play quieter than other " +
                            "music at the same volume. Raise them until they match: the receiver plays them louder, the sound itself " +
                            "isn't changed. A song can't go past the receiver's full level, so some get less. Not for Dolby TrueHD, " +
                            "DTS or plain Dolby Digital.",
                    )
                }
                HorizontalDivider()
            }
            // The sections below fold (issue #776): their heading and what's set show; tap to open.
            val levellingSummary = if (settings.replayGain == ReplayGainMode.OFF) "Off" else "${settings.replayGain.label} · −${-settings.loudnessTarget} LUFS"
            FoldSection("Levelling", levellingSummary) {
                ReplayGainRow(settings.replayGain) { graph.settings.save(graph.settings.current.copy(replayGain = it)) }
                Hint("Evens out loudness. Track: every song at the same level. Album: an album's loud and quiet songs keep their difference. Off: songs play as they are.")
                // The target applies to ReplayGain tags too (issue #667), so it's there whenever levelling is on.
                if (settings.replayGain != ReplayGainMode.OFF) {
                    LoudnessTargetRow(settings.loudnessTarget) { graph.settings.setLoudness(target = it) }
                    Hint("How loud songs are brought to, by the plugin's measurement or by ReplayGain tags. −18 is ReplayGain's own level and leaves room for loud songs. −14 is louder, like Spotify, but quiet songs can't be raised to it.")
                }
                Hint(levellingHint(settings))
            }
            HorizontalDivider()
            FoldSection("Crossfade", crossfadeSummary(settings)) {
                CrossfadeModeRow(settings.crossfade) { graph.settings.setCrossfade(mode = it) }
                Hint(crossfadeHint(settings))
                // Smart decides every change; only Fixed has a length and bars to set (the owner, 9 Nov).
                if (settings.crossfade == CrossfadeMode.FIXED) {
                    CrossfadeLengthRow(settings.crossfadeSeconds, smart = false) {
                        graph.settings.setCrossfade(seconds = it)
                    }
                    if (settings.loudnessSource == LoudnessSource.PLUGIN) {
                        FixedBlendBars(settings.blendBars, graph.settings::setBlendBars)
                    }
                }
                if (settings.crossfade != CrossfadeMode.OFF) {
                    Hint(
                        // The TV app plays on the TV itself and has no sleep timer (issue #860).
                        if (app.siansa.BuildConfig.TV_APP) "Not used with Repeat one, or for Dolby and DTS songs sent to your receiver."
                        else "Only on this phone: on a TV or speaker, songs play back to back. Not used with Repeat one, or while the sleep timer lets a song finish.",
                    )
                }
            }
            HorizontalDivider()
            MixingDocsLink()
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

/**
 * What the length means with Smart on: a ceiling, not the length of every fade (the owner, 0.52.0-dev: "do we still
 * need the fade length?"). Null when the mode's own hint already says it.
 */
internal fun crossfadeLengthHint(s: AppSettings): String? =
    if (s.crossfade != CrossfadeMode.SMART || s.loudnessSource != LoudnessSource.PLUGIN) null
    else "The longest a fade can be. Most are shorter: the songs decide. The full length is used for songs not measured yet" +
        if (s.beatMatch != BeatMatchMode.OFF) " and for a quiet intro without a beat in DJ blends." else "."

/** The section's summary: Smart has nothing to set; Fixed says its length and bars. */
internal fun crossfadeSummary(s: AppSettings): String = when (s.crossfade) {
    CrossfadeMode.OFF -> "Off"
    CrossfadeMode.SMART -> "Smart"
    CrossfadeMode.FIXED -> "Fixed · ${s.crossfadeSeconds} s" + if (s.loudnessSource == LoudnessSource.PLUGIN) " · ${s.blendBars} bars" else ""
}

internal fun crossfadeHint(s: AppSettings): String = when (s.crossfade) {
    CrossfadeMode.OFF -> "Songs play back to back, with no gap."
    CrossfadeMode.FIXED -> "Each song fades into the next over the length below. An album playing in order stays gapless." +
        if (s.loudnessSource == LoudnessSource.PLUGIN) " In DJ radio and DJ mixes, songs that keep a beat blend beat on beat over the bars below, the bass swapped halfway." else ""
    CrossfadeMode.SMART -> "The phone decides every change. An album playing in order stays gapless. " + if (s.loudnessSource == LoudnessSource.PLUGIN) {
        "Other fades start where a song goes quiet and last as long as the two songs need. In DJ radio and DJ mixes, " +
            "songs that keep a beat blend beat on beat, phrase on phrase, with the next song a touch faster or slower to match."
    } else {
        "With the Siansa plugin, fades follow how each song ends and starts, and DJ radio blends beat on beat."
    }
}

/** Fixed's blend length in DJ playback (the owner, 9 Nov). */
@Composable
internal fun FixedBlendBars(bars: Int, onBars: (Int) -> Unit) {
    Text("Blend length in DJ radio and DJ mixes", style = MaterialTheme.typography.bodyLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        AppSettings.BLEND_BAR_CHOICES.forEach { n ->
            FilterChip(selected = bars == n, onClick = { onBars(n) }, label = { Text("$n bars") })
        }
    }
    Hint("8 bars is about 15 seconds at 128 BPM. A blend never takes more than a third of a song.")
}

/** The beat-matched blends' choice, its hint, the echo at the end (issue #759), the bass swap (issue #807) and keeping the key (issue #816), for the page and its screenshot. */
@Composable
internal fun BeatMatchSection(
    mode: BeatMatchMode,
    bars: Int,
    echo: Boolean,
    bassSwap: Boolean,
    keepKey: Boolean,
    onMode: (BeatMatchMode) -> Unit,
    onBars: (Int) -> Unit,
    onEcho: (Boolean) -> Unit,
    onBassSwap: (Boolean) -> Unit,
    onKeepKey: (Boolean) -> Unit,
) {
    BeatMatchRow(mode, onMode)
    Hint(beatMatchHint(mode))
    if (mode != BeatMatchMode.OFF) {
        Text("Blend length", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
            AppSettings.BLEND_BAR_CHOICES.forEach { n ->
                FilterChip(selected = bars == n, onClick = { onBars(n) }, label = { Text("$n bars") })
            }
        }
        Hint("Apart from the crossfade length: 8 bars is about 15 seconds at 128 BPM. A blend never takes more than a third of a song.")
        SwitchRow("Bass swap", bassSwap, onBassSwap)
        Hint("Over the first half of a blend the old song's bass is taken away as the new song's comes in, as a DJ does with the EQ, so two kick drums never play at once.")
        if (AppSettings.BLEND_EXTRAS) {
            SwitchRow("Echo at the end", echo, onEcho)
            Hint("Over the last bar of a blend the old song's last beats repeat on the beat and die away, as a DJ echoes a track out.")
            SwitchRow("Keep the key", keepKey, onKeepKey)
            Hint("The next song plays a touch faster or slower to match. Off, its pitch moves with it, like a DJ's pitch fader. On, its key is kept by stretching the sound.")
        }
    }
}

private fun beatMatchHint(mode: BeatMatchMode): String = when (mode) {
    BeatMatchMode.OFF -> "Fades don't follow the beat. Two songs that both keep a beat get a short fade, so their beats don't clash for long."
    BeatMatchMode.DJ_RADIO -> "In DJ radio, a song that keeps a beat blends into the next one like a DJ mix: phrase on phrase where the Siansa plugin for AudioMuse found the phrases a song is built from, else bar for bar. The next song comes in on its first phrase, a touch faster or slower to match, then eases back to its own speed."
    BeatMatchMode.EVERYWHERE -> "Every smart crossfade blends phrase on phrase or bar for bar when both songs keep a beat, with the next song a touch faster or slower to match (up to 8 %). Uses the beats and phrases the Siansa plugin for AudioMuse measures."
}

/** Off, DJ radio or Everywhere: where blends are beat-matched (issue #646). */
@Composable
internal fun BeatMatchRow(selected: BeatMatchMode, onPick: (BeatMatchMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        BeatMatchMode.entries.forEach { mode ->
            FilterChip(selected = selected == mode, onClick = { onPick(mode) }, label = { Text(mode.label) })
        }
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

/** How much louder Dolby songs play on the receiver (issue #930). */
@Composable
private fun DolbyLouderRow(selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        app.siansa.playback.DolbyLevel.CHOICES.forEach { db ->
            FilterChip(selected = selected == db, onClick = { onPick(db) }, label = { Text(if (db == 0) "Off" else "+$db dB") })
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

/** How mixing works, in the docs (issue #916): crossfades, smart crossfade and beat-matched blends, opened in the browser. */
@Composable
internal fun MixingDocsLink() {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { runCatching { uri.openUri(AboutLinks.MIXING) }.onFailure { AppLog.w("Settings", "Couldn't open the mixing docs: ${it.message}") } }
            .padding(vertical = Space.Tight)
            .testTag("mixingDocs"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Medium),
    ) {
        Column(Modifier.weight(1f)) {
            Heading("How mixing works")
            Hint("Crossfades, smart crossfade and DJ blends, in the docs")
        }
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Opens in the browser", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
