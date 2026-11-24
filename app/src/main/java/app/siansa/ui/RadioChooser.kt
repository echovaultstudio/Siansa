package app.siansa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.data.Song
import app.siansa.log.AppLog
import app.siansa.mix.ContinuousMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * "Start song radio", from anywhere (issue #337): first a choice of Steady or DJ, then the radio starts. One
 * chooser for the whole app, shown by [RadioChooserHost] in the root, so a menu can close as soon as it's
 * tapped.
 */
object RadioChooser {
    val song = MutableStateFlow<Song?>(null)

    /** Asks how the radio from [song] should play, then starts it. */
    fun ask(song: Song) {
        this.song.value = song
    }

    /**
     * Starts a mix from [song] in [mode] without asking (a slide from the play button, issue #919, or a choice here),
     * saying so with [say], and why not if it can't.
     */
    fun startNow(graph: app.siansa.AppGraph, song: Song, mode: ContinuousMode, shape: app.siansa.mix.MixShape? = null, say: (String) -> Unit) {
        // A DJ mix only when its needs are met: otherwise it says why and nothing starts (Steady radio still can).
        if (mode == ContinuousMode.DJ) graph.djProblem()?.let { problem ->
            AppLog.i("SongRadio", "No DJ mix from \"${song.title}\": $problem")
            say(app.siansa.mix.DjReady.explain(problem))
            return
        }
        // A DJ mix blends its songs, which Untouched playback can't: asked first, then started (the owner, 23 Nov).
        val start = {
            AppLog.i("SongRadio", "Chose ${mode.label} for a radio from \"${song.title}\"")
            say("Starting ${mixName(mode)} from ${song.title}… more songs are added as it plays")
            graph.songRadio.start(song, mode, shape) { message -> say(message) }
        }
        if (mode == ContinuousMode.DJ) DjNeedsBlends.then(graph, start) else start()
    }
}

/**
 * A DJ mix asked for while Untouched playback is on (the owner, 23 Nov: "still allows starting DJ mix from now playing
 * screen. But in settings it says untouched playback disables DJ and fades"): the mix can't blend on the phone's own
 * output, so the start waits here on a yes, shown by [DjNeedsBlendsHost] in the root. Nothing is switched unasked.
 */
object DjNeedsBlends {
    /** The start that waits on the answer, or null when nothing is asked. */
    val waiting = MutableStateFlow<(() -> Unit)?>(null)

    /** Runs [start] now, or asks first while Untouched playback is on. */
    fun then(graph: app.siansa.AppGraph, start: () -> Unit) = then(graph.settings.current.untouchedNow, start)

    internal fun then(untouched: Boolean, start: () -> Unit) {
        if (!untouched) return start()
        AppLog.i("Player", "A DJ mix was asked for with Untouched playback on: asking whether to pause it for the mix")
        waiting.value = start
    }

    const val TITLE = "DJ mix pauses Untouched playback"
    const val WHY = "Untouched playback sends each song straight to your phone's sound output, so songs can't be blended. " +
        "It's paused for this mix and comes back on when you play something else. The song stops for a moment."
    const val YES = "Pause it and start"

    /** Under DJ in a chooser, while Untouched playback is on. */
    const val NOTE = "Pauses Untouched playback for the mix"

    /** Under the switch in Settings, while it's set aside for a DJ mix. */
    const val PAUSED = "Paused for the DJ mix that's playing. It comes back on when you play something else."
}

/** Asks whether to turn Untouched playback off for the DJ mix that waits in [DjNeedsBlends]; placed once, in the app's root. */
@Composable
fun DjNeedsBlendsHost() {
    val graph = LocalGraph.current
    val start by DjNeedsBlends.waiting.collectAsStateWithLifecycle()
    val go = start ?: return
    DjNeedsBlendsDialog(
        onYes = {
            DjNeedsBlends.waiting.value = null
            // For this mix only (issue #1345): the switch stays on, and it's taken up again when the mix is over.
            graph.settings.pauseUntouched(true)
            go()
        },
        onNo = { DjNeedsBlends.waiting.value = null },
    )
}

@Composable
internal fun DjNeedsBlendsDialog(onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text(DjNeedsBlends.TITLE) },
        text = { Text(DjNeedsBlends.WHY) },
        confirmButton = { TextButton(onClick = onYes, modifier = Modifier.testTag("djNeedsBlendsYes")) { Text(DjNeedsBlends.YES) } },
        dismissButton = { TextButton(onClick = onNo) { Text("Not now") } },
    )
}

/** A mix's name as the app says it: Steady radio or DJ mix (issue #919). */
internal fun mixName(mode: ContinuousMode): String = if (mode == ContinuousMode.DJ) "DJ mix" else "Steady radio"

/** Each choice's icon in the chooser (issue #916): a wave for Steady, a turntable for DJ. */
internal fun radioModeIcon(mode: ContinuousMode): ImageVector = when (mode) {
    ContinuousMode.DJ -> SiansaIcons.Turntable
    else -> Icons.Rounded.Waves
}

/**
 * Steady and DJ, each its icon and name in a row, nothing more (issue #916: the descriptions crowded it; they're in the
 * docs, Discovery › Song radio).
 */
@Composable
internal fun RadioModeChoices(djProblem: String? = null, djNote: String? = null, onPick: (ContinuousMode) -> Unit) {
    Column {
        listOf(ContinuousMode.STEADY, ContinuousMode.DJ).forEach { mode ->
            // A DJ mix only when its needs are met: shown greyed with why (Needs Siansa Beats…), not to be picked.
            val why = djProblem.takeIf { mode == ContinuousMode.DJ }
            Row(
                Modifier
                    .fillMaxWidth()
                    .alpha(if (why != null) 0.45f else 1f)
                    .clickable(enabled = why == null) { onPick(mode) }
                    .padding(vertical = Space.Medium)
                    .testTag("radioMode-${mode.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.Page),
            ) {
                Icon(radioModeIcon(mode), null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(mode.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (why != null) Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("radioModeWhy"))
                    // It can be picked, and says what picking it does (Untouched playback is on).
                    else if (mode == ContinuousMode.DJ && djNote != null) Text(djNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("radioModeNote"))
                }
            }
        }
    }
}

/** The Steady or DJ chooser for [RadioChooser]; placed once, in the app's root. */
@Composable
fun RadioChooserHost() {
    val graph = LocalGraph.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val song by RadioChooser.song.collectAsStateWithLifecycle()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val connection by graph.connection.state.collectAsStateWithLifecycle()
    val djBeats by graph.djReady.beats.collectAsStateWithLifecycle()
    val seed = song ?: return

    fun start(mode: ContinuousMode) {
        RadioChooser.song.value = null
        RadioChooser.startNow(graph, seed, mode) { message -> scope.launch { snack.showSnackbar(message) } }
    }

    AlertDialog(
        onDismissRequest = { RadioChooser.song.value = null },
        title = { Text("Start a mix") },
        text = { RadioModeChoices(app.siansa.mix.DjReady.problem(settings, connection, djBeats), DjNeedsBlends.NOTE.takeIf { settings.untouchedNow }, ::start) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { RadioChooser.song.value = null }) { Text("Cancel") } },
    )
}
