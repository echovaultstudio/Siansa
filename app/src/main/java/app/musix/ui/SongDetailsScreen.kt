package app.musix.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.musix.data.Song
import app.musix.data.cover
import app.musix.log.AppLog
import app.musix.net.SongDetails
import app.musix.net.channelsLabel
import app.musix.net.formatFileSize
import app.musix.net.formatSampleRate
import app.musix.net.skipCount

fun NavController.openSongDetails(songId: String) = navigate("song/${Uri.encode(songId)}/details")

private data class SongDetailsPage(val song: Song, val details: SongDetails, val skips: Int)

/** "Show details" from a song's menu: format, bit rate, plays, skips and the file's path on the server. */
@Composable
fun SongDetailsScreen(nav: NavController, songId: String) {
    val graph = LocalGraph.current
    val page by loadState(songId) {
        AppLog.i("Details", "Song details for $songId")
        val details = graph.subsonic.songDetails(songId)
        val song = graph.library.songs(listOf(songId)).firstOrNull() ?: graph.subsonic.song(songId)
        SongDetailsPage(song, details, skipCount(graph.history.all(), songId))
    }

    Screen("Song details", nav) { padding ->
        LoadBox(page, Modifier.padding(padding)) { p ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().padding(Space.Page), verticalAlignment = Alignment.CenterVertically) {
                    Cover(p.song.cover, 300, Modifier.size(72.dp), RoundedCornerShape(8.dp), albumId = p.song.albumId)
                    Spacer(Modifier.width(Space.Page))
                    Column(Modifier.weight(1f)) {
                        Text(
                            p.song.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        artistAndAlbum(p.song)?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                HorizontalDivider()
                songDetailRows(p.details, p.skips).forEach { (label, value) -> DetailRow(label, value) }
            }
        }
    }
}

/** The label and value lines of the details page, in order; missing values show as "Unknown". */
fun songDetailRows(d: SongDetails, skips: Int): List<Pair<String, String>> {
    val quality = listOfNotNull(
        d.sampleRateHz?.let(::formatSampleRate),
        d.bitDepth?.let { "$it-bit" },
        d.channels?.let(::channelsLabel),
    ).joinToString(" · ")
    return listOfNotNull(
        "Format" to (d.codec ?: "Unknown"),
        "Bit rate" to (d.bitRateKbps?.let { "$it kbps" } ?: "Unknown"),
        quality.takeIf { it.isNotEmpty() }?.let { "Quality" to it },
        d.sizeBytes?.let { "File size" to formatFileSize(it) },
        "Plays" to (d.playCount?.toString() ?: "0"),
        "Skips on this phone" to skips.toString(),
        "File" to (d.path ?: "Unknown"),
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.Page, vertical = Space.Small)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Accent)
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
