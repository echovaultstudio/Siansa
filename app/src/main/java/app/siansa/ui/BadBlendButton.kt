package app.siansa.ui

import android.os.SystemClock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.playback.LastBlend
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The "Bad blend" button (the owner, 9 Nov): over the cover, during a DJ blend and for a minute after it. One tap
 * writes the blend to the log and sends it to Siansa Beats' list of bad blends. Shown or hidden in Settings › Help
 * (on by default in Siansa Dev only).
 */
@Composable
internal fun BadBlendButton(modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val shown by graph.settings.state.collectAsStateWithLifecycle()
    if (!shown.badBlendButton) return
    val report by LastBlend.report.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var said by remember(report) { mutableStateOf<String?>(null) }
    LaunchedEffect(report) {
        while (report != null) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val blend = report
    if (blend == null || !LastBlend.showing(blend, now)) return
    AssistChip(
        onClick = {
            if (said == null) {
                said = "Sending…"
                scope.launch {
                    val problem = LastBlend.mark(blend) { path, body -> graph.siansaBeatsPost(path, body) }
                    said = if (problem == null) "Marked as a bad blend" else "In the log; not sent: $problem"
                }
            }
        },
        label = { Text(said ?: "Bad blend") },
        colors = AssistChipDefaults.assistChipColors(containerColor = Color.Black.copy(alpha = 0.6f), labelColor = Color.White),
        modifier = modifier.testTag("badBlend"),
    )
}
