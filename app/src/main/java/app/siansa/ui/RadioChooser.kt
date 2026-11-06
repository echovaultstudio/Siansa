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
}

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
internal fun RadioModeChoices(onPick: (ContinuousMode) -> Unit) {
    Column {
        listOf(ContinuousMode.STEADY, ContinuousMode.DJ).forEach { mode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(mode) }
                    .padding(vertical = Space.Medium)
                    .testTag("radioMode-${mode.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.Page),
            ) {
                Icon(radioModeIcon(mode), null, tint = MaterialTheme.colorScheme.primary)
                Text(mode.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
    val seed = song ?: return

    fun start(mode: ContinuousMode) {
        RadioChooser.song.value = null
        AppLog.i("SongRadio", "Chose ${mode.label} for a radio from \"${seed.title}\"")
        scope.launch { snack.showSnackbar("Starting ${mode.label} radio from ${seed.title}… more songs are added as it plays") }
        graph.songRadio.start(seed, mode) { message -> scope.launch { snack.showSnackbar(message) } }
    }

    AlertDialog(
        onDismissRequest = { RadioChooser.song.value = null },
        title = { Text("Start song radio") },
        text = { RadioModeChoices(::start) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { RadioChooser.song.value = null }) { Text("Cancel") } },
    )
}
