package app.siansa.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.data.Album
import app.siansa.data.Song
import app.siansa.log.AppLog
import app.siansa.offline.offlineItem
import kotlinx.coroutines.launch

/**
 * Where an album's download is, for its line in [AlbumActions] and under the album page's title (issue #1331, the
 * owner, 25 Nov: Download moved into the album's ⊕): not chosen, on its way ([done] of [total]), or all on the phone.
 */
internal data class AlbumDownload(val chosen: Boolean, val done: Int, val total: Int) {
    val all: Boolean get() = chosen && total > 0 && done >= total

    companion object {
        fun of(chosen: Boolean, songIds: List<String>, done: Set<String>): AlbumDownload =
            AlbumDownload(chosen, songIds.count { it in done }, songIds.size)
    }
}

/** What the album page says of its download after its details, or null when it isn't chosen. */
internal fun downloadNote(d: AlbumDownload): String? = when {
    !d.chosen -> null
    d.all -> "Downloaded"
    else -> "Downloading ${d.done} of ${d.total}"
}

/**
 * Everything you do with an album, from the ⊕ on its page (issue #1331, with #1332 and #1334): your rating, Play next,
 * Add to queue, Add to playlist, its download, and Clear all images. [stars] is null where ratings aren't shown (off, or
 * a server that keeps none: Emby and Jellyfin).
 */
@Composable
internal fun AlbumActions(
    album: Album,
    songCount: Int,
    stars: Int?,
    download: AlbumDownload,
    onRate: (Int) -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onClearImages: (() -> Unit)?,
) {
    Column(Modifier.navigationBarsPadding().padding(bottom = Space.Small).testTag("albumActions")) {
        Row(Modifier.padding(horizontal = Space.Section), verticalAlignment = Alignment.CenterVertically) {
            LocalCoverDraw.current(album.coverArt, 120, Modifier.size(48.dp), RoundedCornerShape(6.dp), null)
            Spacer(Modifier.width(Space.Medium))
            Column(Modifier.weight(1f)) {
                Text(album.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(album.artist.ifBlank { null }, album.year?.toString(), songsLabel(songCount)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(Space.Medium))
        if (stars != null) {
            HorizontalDivider()
            // Your rating right in the sheet (the owner's list, 25 Nov): a tap gives that many stars, a tap on the rating
            // it has takes it off, as for songs.
            Row(
                Modifier.fillMaxWidth().padding(start = Space.Section, end = Space.Page, top = Space.Small, bottom = Space.Small).testTag("albumRating"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Your rating", style = MaterialTheme.typography.bodyLarge)
                    Text("Tap a star; tap it again to clear", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StarRow(stars, big = true, tag = "albumStars", dim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), onRate = onRate)
            }
        }
        ActionLine(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next", tag = "albumPlayNext", onClick = onPlayNext)
        ActionLine(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue", tag = "albumQueue", onClick = onQueue)
        ActionLine(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist", tag = "albumPlaylist", more = true, onClick = onPlaylist)
        when {
            !download.chosen -> ActionLine(Icons.Outlined.ArrowCircleDown, "Download", "For listening without a connection", tag = "albumDownload", onClick = onDownload)
            download.all -> ActionLine(Icons.Rounded.DownloadForOffline, "Downloaded", "${songsLabel(download.total)} on this phone. Tap to remove.", tag = "albumDownload", accent = true, onClick = onDownload)
            else -> ActionLine(null, "Downloading", "${download.done} of ${download.total}. Tap to remove.", tag = "albumDownload", progress = download.done.toFloat() / download.total.coerceAtLeast(1), onClick = onDownload)
        }
        if (onClearImages != null) {
            // Housekeeping, set apart at the foot.
            HorizontalDivider(Modifier.padding(horizontal = Space.Section, vertical = Space.Tight))
            ActionLine(Icons.Rounded.Refresh, "Clear all images", "Gets this album's pictures again from the server", tag = "clearAlbumImages", onClick = onClearImages)
        }
    }
}

private fun songsLabel(n: Int) = if (n == 1) "1 song" else "$n songs"

/** One line of the sheet: an icon (or a download's progress), what it does, and what it means. */
@Composable
private fun ActionLine(
    icon: ImageVector?,
    text: String,
    note: String? = null,
    tag: String,
    more: Boolean = false,
    accent: Boolean = false,
    progress: Float? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = text, onClick = onClick)
            .padding(horizontal = Space.Section, vertical = Space.Medium).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (progress != null) {
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(22.dp), color = Accent, strokeWidth = 2.5.dp)
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (accent) Accent else MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.width(Space.Page))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (more) Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * [AlbumActions] as a sheet over the page, doing what each line says. [songs] are the album's songs; [onClearImages]
 * is the album page's own (null elsewhere, where a card's picture isn't the page's).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumActionsSheet(album: Album, songs: List<Song>, onClearImages: (() -> Unit)?, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val stars by graph.ratings.stars.collectAsStateWithLifecycle()
    val ids by graph.downloads.ids.collectAsStateWithLifecycle()
    val item = remember(album) { album.offlineItem() }
    val songIds = remember(songs) { songs.map { it.id } }
    val download = AlbumDownload.of(item.key in ids.items, songIds, ids.done)
    var picking by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    val say: (String) -> Unit = { message -> scope.launch { snack.showSnackbar(message) } }
    if (picking) {
        AddToPlaylistDialog(songs) { message ->
            picking = false
            onDismiss()
            if (message != null) say(message)
        }
        return
    }
    if (removing) {
        RemoveDialog(album.name, onDismiss = { removing = false }) {
            graph.downloads.drop(item.key)
            removing = false
            onDismiss()
        }
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        AlbumActions(
            album, songs.size,
            stars = if (ratingShown(settings.stars.on, graph.ratings.kept, album.id)) stars[album.id] ?: 0 else null,
            download = download,
            onRate = { n -> graph.ratings.rate(album.id, n) { say(it) } },
            onPlayNext = {
                val n = graph.player.playNext(songs)
                AppLog.i("Album", "${album.id}: $n songs to play next")
                onDismiss()
                say(if (n == 0) "Nothing to add: none of its songs are downloaded" else "${songsLabel(n)} play next")
            },
            onQueue = {
                val n = graph.player.addToQueue(songs)
                AppLog.i("Album", "${album.id}: $n songs added to the queue")
                onDismiss()
                say(if (n == 0) "Nothing to add: none of its songs are downloaded" else "${songsLabel(n)} added to the queue")
            },
            onPlaylist = { picking = true },
            onDownload = {
                if (download.chosen) removing = true else {
                    AppLog.i("Album", "${album.id}: download chosen from its ⊕")
                    graph.downloads.choose(item, songs)
                    say("Downloading ${album.name}")
                }
            },
            onClearImages = onClearImages?.let { clear -> { clear(); onDismiss() } },
        )
    }
}

/** The download as the album page shows it under its title (issue #1331): watched, so it moves on as songs arrive. */
@Composable
internal fun rememberAlbumDownload(album: Album?, songs: List<Song>?): AlbumDownload? {
    val graph = LocalGraph.current
    val ids by graph.downloads.ids.collectAsStateWithLifecycle()
    if (album == null || songs == null) return null
    val songIds = remember(songs) { songs.map { it.id } }
    return AlbumDownload.of(album.offlineItem().key in ids.items, songIds, ids.done)
}
