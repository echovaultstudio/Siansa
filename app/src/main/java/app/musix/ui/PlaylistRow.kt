package app.musix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.musix.data.Playlist

/**
 * The line under a playlist in a list, as Spotify writes it: "Playlist · owner · 12 songs".
 * [fallbackOwner] is used when the server doesn't say who made it; a blank owner is left out.
 */
fun playlistSubtitle(playlist: Playlist, fallbackOwner: String? = null): String {
    val owner = playlist.owner?.takeIf { it.isNotBlank() } ?: fallbackOwner?.takeIf { it.isNotBlank() }
    val songs = playlist.songCount.coerceAtLeast(0)
    return listOfNotNull("Playlist", owner, "$songs song${if (songs == 1) "" else "s"}").joinToString(" · ")
}

/** A playlist as a list row: small cover on the left, name and [playlistSubtitle] beside it. */
@Composable
fun PlaylistRow(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.Page, vertical = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(playlist.coverArt, 150, Modifier.size(56.dp), RoundedCornerShape(4.dp))
        Spacer(Modifier.width(Space.Medium))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Space.Tight))
            Text(
                playlistSubtitle(playlist),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
