package app.siansa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.log.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * TEMPORARY (siansa-dev #63): **Breakdown** and **Drop** under the cover, for tuning how Siansa Beats finds them.
 * A tap sends where the song is (less a moment for the tap to come) to Siansa Beats as a mark of that kind; its
 * songs list gathers them under Marked. Shown with the Bad blend button's switch (Settings › Help, on in Siansa Dev).
 * Goes again once the tuning is done, with Siansa Beats' `/api/v1/marks`.
 */
@Composable
internal fun SectionButtons(songId: String?, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val shown by graph.settings.state.collectAsStateWithLifecycle()
    if (!shown.badBlendButton || songId == null) return
    val scope = rememberCoroutineScope()
    var said by remember(songId) { mutableStateOf<String?>(null) }
    LaunchedEffect(said) {
        if (said != null && said != SENDING) {
            delay(2_500)
            said = null
        }
    }
    Row(modifier.testTag("sectionButtons"), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        for ((kind, label) in listOf("breakdown" to "Breakdown", "drop" to "Drop")) {
            AssistChip(
                onClick = {
                    if (said == SENDING) return@AssistChip
                    val seconds = sectionMarkSeconds(graph.player.positionMs())
                    said = SENDING
                    scope.launch {
                        said = try {
                            graph.siansaBeatsPost(PATH, sectionMarkBody(songId, seconds, kind))
                            AppLog.i("Sections", "Marked a $kind at %.1f s of $songId".format(java.util.Locale.ROOT, seconds))
                            "$label marked"
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLog.w("Sections", "Couldn't send the $kind mark to Siansa Beats: ${e.message}")
                            "Not sent"
                        }
                    }
                },
                label = { Text(if (said != null && said != SENDING && said!!.startsWith(label)) said!! else label) },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color.Black.copy(alpha = 0.6f), labelColor = Color.White),
            )
        }
        if (said == "Not sent") Text("Not sent", color = Color.White)
    }
}

private const val SENDING = "…"

/** Siansa Beats' temporary marks, through the plugin's path as the other Siansa Beats calls go. */
private const val PATH = "/plugins/siansa_beats/api/marks"

/** A tap comes about a quarter of a second after the sound it marks. */
internal const val SECTION_TAP_LATE_MS = 250L

/** Where (seconds of the song) a tap at [positionMs] marks. */
internal fun sectionMarkSeconds(positionMs: Long): Double = (positionMs - SECTION_TAP_LATE_MS).coerceAtLeast(0L) / 1000.0

/** What goes to Siansa Beats for a [kind] ("breakdown" or "drop") tapped at [seconds] of song [songId]. */
internal fun sectionMarkBody(songId: String, seconds: Double, kind: String): String =
    buildJsonObject {
        put("track_id", songId)
        put("seconds", seconds)
        put("kind", kind)
    }.toString()
