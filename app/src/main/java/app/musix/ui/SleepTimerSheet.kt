package app.musix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.musix.playback.SleepTimerState
import app.musix.playback.leftMs
import app.musix.playback.sleepTimeLabel
import app.musix.playback.sleepTimeShort
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val SheetText = Color(0xFFEDE7F6)
private val SheetSubtle = Color(0xFFB9B0CC)

/** The current time on the sleep timer's clock, updated every second while [running]. */
@Composable
internal fun rememberTimerNow(running: Boolean): Long {
    val graph = LocalGraph.current
    var now by remember { mutableLongStateOf(graph.sleepTimer.now()) }
    LaunchedEffect(running) {
        while (running) {
            now = graph.sleepTimer.now()
            delay(1_000)
        }
    }
    return now
}

/** What the timer is doing, in words. */
private fun timerStatus(timer: SleepTimerState, now: Long): String {
    val left = timer.leftMs(now) ?: return "Music stops after this song"
    return "Music stops in ${sleepTimeLabel(left)}" + if (timer.windDown) ", winding down" else ""
}

/** The moon in the player's top corner: opens the sleep timer and shows the time left while it runs. */
@Composable
fun SleepTimerButton(onClick: () -> Unit) {
    val graph = LocalGraph.current
    val timer by graph.sleepTimer.state.collectAsStateWithLifecycle()
    val now = rememberTimerNow(timer != null)
    val active = timer
    val left = active?.leftMs(now)
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (active == null) "Sleep timer" else "Sleep timer: ${timerStatus(active, now)}" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.Bedtime,
                null,
                tint = if (active != null) Accent else Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(if (left != null) 18.dp else 22.dp),
            )
            if (left != null) {
                Text(sleepTimeShort(left), style = MaterialTheme.typography.labelSmall, color = Accent, maxLines = 1)
            }
        }
    }
}

/** Choose how long music plays before it fades out and pauses. */
@Composable
fun SleepTimerSheet(hasSong: Boolean, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val timer by graph.sleepTimer.state.collectAsStateWithLifecycle()
    val now = rememberTimerNow(timer != null)
    val audioMuse = graph.settings.current.audioMuseReady
    var windDown by remember { mutableStateOf(graph.sleepTimer.windDownChosen && audioMuse) }
    var custom by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableFloatStateOf(90f) }

    fun start(minutes: Int) {
        graph.sleepTimer.start(minutes, windDown)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Brand.PlumRaised,
        contentColor = SheetText,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sleep timer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        timer?.let { timerStatus(it, now) } ?: "The song playing then finishes and fades out",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (timer != null) Accent else SheetSubtle,
                    )
                }
                if (timer != null) {
                    TextButton(onClick = { graph.sleepTimer.cancel(); onDismiss() }) { Text("Turn off", color = Accent) }
                }
            }
            Spacer(Modifier.height(8.dp))
            listOf(15, 30, 45, 60).forEach { minutes ->
                SheetRow(if (minutes == 60) "1 hour" else "$minutes minutes") { start(minutes) }
            }
            SheetRow("End of this song", enabled = hasSong) {
                graph.sleepTimer.startEndOfSong()
                onDismiss()
            }
            SheetRow("Custom time") { custom = !custom }
            if (custom) {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Slider(
                        value = customMinutes,
                        onValueChange = { customMinutes = it },
                        valueRange = 5f..180f,
                        steps = 34,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sleepTimeLabel(customMinutes.roundToInt() * 60_000L), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Button(
                            onClick = { start(customMinutes.roundToInt()) },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF2A1700)),
                        ) { Text("Start") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = audioMuse) { windDown = !windDown }
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Wind down", style = MaterialTheme.typography.bodyLarge, color = if (audioMuse) SheetText else SheetSubtle)
                    Text(
                        if (audioMuse) "Calmer, slower songs near the end" else "Needs AudioMuse, set it up in Settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = SheetSubtle,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = windDown,
                    onCheckedChange = { windDown = it },
                    enabled = audioMuse,
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color(0xFF2A1700)),
                )
            }
        }
    }
}

@Composable
private fun SheetRow(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (enabled) SheetText else SheetSubtle.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}
