package app.musix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavController
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
 * Your sonic fingerprint (issue #216), a page of its own under Your Library → Your listening (issue #255):
 * what the music you play most sounds like, from AudioMuse. Its six moods as bars, the leading genres, the
 * typical tempo and key, then the songs themselves to play. Made once a day.
 */
@Composable
fun FingerprintScreen(nav: NavController) {
    MusixDarkTheme { FingerprintPage(nav) }
}

@Composable
private fun FingerprintPage(nav: NavController) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    var round by rememberSaveable { mutableIntStateOf(0) }
    val profile by loadStateFresh("fingerprint-$round") {
        if (!graph.settings.current.audioMuseReady) error("Set up AudioMuse in Settings to see your sonic fingerprint.")
        graph.fingerprint.today(force = round > 0)
    }
    val p = profile.valueOrNull()
    val songs by loadState("fingerprint-songs-${p?.date}-${p?.songIds?.hashCode()}") {
        p?.songIds?.let { graph.generator.resolveSongs(it) }.orEmpty()
    }
    val list = songs.valueOrNull().orEmpty()

    Box(Modifier.fillMaxSize().background(Brand.Plum)) {
        LazyColumn(Modifier.fillMaxSize().testTag("fingerprint"), contentPadding = PaddingValues(bottom = bottomBarPadding(Space.Large))) {
            item {
                ListeningHeader(nav, "Your sonic fingerprint", "What the music you play most sounds like. Made once a day.") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.Page).padding(top = Space.Page),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (profile is Load.Loading) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp) // spacing: exact
                        } else {
                            IconButton(onClick = { round++ }) { Icon(Icons.Rounded.Refresh, "Make it again", tint = Color.White) }
                        }
                        Spacer(Modifier.weight(1f))
                        FilledIconButton(
                            onClick = { scope.launch { graph.player.play(list.shuffled()) } },
                            enabled = list.isNotEmpty(),
                            modifier = Modifier.size(52.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent, contentColor = OnAccent),
                        ) { Icon(Icons.Rounded.PlayArrow, "Play your fingerprint", Modifier.size(30.dp)) }
                    }
                }
            }
            item {
                when (val load = profile) {
                    is Load.Loading -> Box(Modifier.fillMaxWidth().padding(Space.Large), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                    is Load.Err -> Text(load.message, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.padding(Space.Page))
                    is Load.Ok -> Column(Modifier.padding(horizontal = Space.Page).padding(top = Space.Section), verticalArrangement = Arrangement.spacedBy(Space.Section)) {
                        FingerprintStats(load.value)
                        FingerprintBody(load.value)
                    }
                }
            }
            if (list.isNotEmpty()) {
                item { SectionTitle("The songs", Modifier.padding(horizontal = Space.Page)) }
                itemsIndexed(list, key = { i, s -> "$i-${s.id}" }) { i, song ->
                    SongRow(song = song, onClick = { graph.player.play(list, i) }, leading = "${i + 1}")
                }
            }
        }
    }
}

/** The fingerprint in numbers: tempo, key, strongest mood and songs, as tiles. */
@Composable
internal fun FingerprintStats(p: FingerprintProfile) {
    val tiles = fingerprintStats(p)
    if (tiles.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        tiles.forEach { (value, label) ->
            Column(
                Modifier.weight(1f).testTag("fingerprint-stat").clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f)).padding(Space.Medium),
            ) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
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

/** The stat tiles: value and what it is ("122" BPM, "Minor" key, "Happy" mood, "50" songs). */
internal fun fingerprintStats(p: FingerprintProfile): List<Pair<String, String>> = listOfNotNull(
    p.bpm?.let { "$it" to "BPM" },
    p.scale?.let { it.replaceFirstChar { c -> c.uppercase() } to "Key" },
    p.moods.maxByOrNull { it.second }?.takeIf { it.second > 0 }?.let { it.first.replaceFirstChar { c -> c.uppercase() } to "Top mood" },
    p.songIds.size.takeIf { it > 0 }?.let { "$it" to "Songs" },
)

/** "Around 122 BPM · mostly minor keys · 50 songs". */
internal fun fingerprintFacts(p: FingerprintProfile): String? = listOfNotNull(
    p.bpm?.let { "Around $it BPM" },
    p.scale?.let { "mostly $it keys" },
    p.songIds.size.takeIf { it > 0 }?.let { "$it songs" },
).joinToString(" · ").ifEmpty { null }
