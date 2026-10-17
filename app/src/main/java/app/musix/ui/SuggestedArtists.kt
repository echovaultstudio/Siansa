package app.musix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.musix.log.AppLog
import app.musix.mix.SuggestedArtist
import coil3.compose.AsyncImage
import java.util.Locale

/** One artist in "Artists you might like": round photo, name, and who it's like. */
@Composable
fun SuggestedArtistTile(artist: SuggestedArtist, onClick: () -> Unit) {
    Column(
        Modifier.width(130.dp).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtistPhoto(artist, 120)
        Spacer(Modifier.height(6.dp))
        Text(artist.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        artist.because.firstOrNull()?.let {
            Text(
                "Like $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** What we know about a suggested artist, with a link to listen on Deezer. */
@Composable
fun SuggestedArtistDialog(artist: SuggestedArtist, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(artist.name, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ArtistPhoto(artist, 160)
                artistFacts(artist)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
                similarToText(artist.because)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Text("Not in your library", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            artist.link?.let { link ->
                TextButton(onClick = {
                    runCatching { uri.openUri(link) }.onFailure { AppLog.w("Suggest", "Couldn't open Deezer: ${it.message}") }
                    onDismiss()
                }) { Text("Open in Deezer") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ArtistPhoto(artist: SuggestedArtist, sizeDp: Int) {
    AsyncImage(
        model = artist.photo,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(sizeDp.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** "12 albums · 1.2M fans on Deezer", or null when Deezer gave neither. */
fun artistFacts(artist: SuggestedArtist): String? {
    val parts = buildList {
        if (artist.albums > 0) add(if (artist.albums == 1) "1 album" else "${artist.albums} albums")
        if (artist.fans > 0) add("${shortCount(artist.fans)} ${if (artist.fans == 1) "fan" else "fans"}")
    }
    return if (parts.isEmpty()) null else parts.joinToString(" · ") + " on Deezer"
}

/** "Similar to A", "Similar to A and B", "Similar to A, B and C" (at most three names). */
fun similarToText(because: List<String>): String? {
    val names = because.take(3)
    return when (names.size) {
        0 -> null
        1 -> "Similar to ${names[0]}"
        else -> "Similar to ${names.dropLast(1).joinToString(", ")} and ${names.last()}"
    }
}

/** 950, 1.2K, 35K, 1.2M. */
fun shortCount(n: Int): String = when {
    n >= 1_000_000 -> trimmed(n / 1_000_000.0) + "M"
    n >= 1_000 -> trimmed(n / 1_000.0) + "K"
    else -> n.toString()
}

private fun trimmed(value: Double): String =
    if (value >= 10) value.toInt().toString() else String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")
