package app.musix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.musix.mix.FingerprintProfile
import kotlinx.coroutines.launch

/**
 * Home: your sonic fingerprint (issue #216). What the music you play most sounds like, from AudioMuse: its six
 * moods as bars, the leading genres, the typical tempo and key, and the songs to play. Made once a day.
 */
@Composable
fun FingerprintCard() {
    val graph = LocalGraph.current
    val launch = rememberPlayLauncher()
    val scope = rememberCoroutineScope()
    var round by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val profile by loadState("fingerprint-$round") { graph.fingerprint.today(force = round > 0) }
    val colors = Brand.VioletDeep to Brand.Plum
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Page, vertical = Space.Small)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(colors.first, colors.second)))
            .testTag("fingerprint"),
    ) {
        Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Fingerprint, null, tint = Color.White)
                Spacer(Modifier.width(Space.Small))
                Column(Modifier.weight(1f)) {
                    Text("Your sonic fingerprint", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("What the music you play most sounds like", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }
            }
            when (val p = profile) {
                is Load.Loading -> Box(Modifier.fillMaxWidth().padding(Space.Large), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
                is Load.Err -> Text(p.message, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                is Load.Ok -> FingerprintBody(p.value)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (busy || profile is Load.Loading) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp) // spacing: exact
                } else {
                    IconButton(onClick = { round++ }) { Icon(Icons.Rounded.Refresh, "Make it again", tint = Color.White) }
                }
                Spacer(Modifier.weight(1f))
                val ids = (profile as? Load.Ok)?.value?.songIds.orEmpty()
                FilledIconButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            val songs = graph.generator.resolveSongs(ids)
                            busy = false
                            if (songs.isEmpty()) launch.notify("None of these songs are in your library") else graph.player.play(songs.shuffled())
                        }
                    },
                    enabled = ids.isNotEmpty() && !busy,
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent, contentColor = OnAccent),
                ) { Icon(Icons.Rounded.PlayArrow, "Play your fingerprint", Modifier.size(30.dp)) }
            }
        }
    }
}

@Composable
private fun FingerprintBody(p: FingerprintProfile) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
        if (p.moods.any { it.second > 0 }) {
            p.moods.forEach { (mood, value) -> MoodBar(mood, value) }
        }
        if (p.genres.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                p.genres.forEach { genre ->
                    Text(
                        genre,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f))
                            .padding(horizontal = Space.Medium, vertical = Space.Tight),
                    )
                }
            }
        }
        fingerprintFacts(p)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f)) }
    }
}

/** One mood as a labelled bar, 0..1. */
@Composable
private fun MoodBar(mood: String, value: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(mood.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(88.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.18f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(value.toFloat().coerceIn(0f, 1f)).clip(RoundedCornerShape(4.dp)).background(Accent))
        }
        Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(44.dp).padding(start = Space.Small))
    }
}

/** "Around 122 BPM · mostly minor keys · 50 songs". */
internal fun fingerprintFacts(p: FingerprintProfile): String? = listOfNotNull(
    p.bpm?.let { "Around $it BPM" },
    p.scale?.let { "mostly $it keys" },
    p.songIds.size.takeIf { it > 0 }?.let { "$it songs" },
).joinToString(" · ").ifEmpty { null }
