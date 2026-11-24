package app.siansa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * What holding an album's cover offers (the owner, 21 Nov: "a card pop with user having to select clear all images";
 * more to come here later). Each choice is a row; nothing happens until one is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumCoverSheet(album: String, onClearImages: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        AlbumCoverChoices(album, onClearImages = { onClearImages(); onDismiss() })
    }
}

/** The sheet's contents, apart for the screenshot test. */
@Composable
internal fun AlbumCoverChoices(album: String, onClearImages: () -> Unit) {
    Column(Modifier.navigationBarsPadding().padding(bottom = Space.Small).testTag("albumCoverSheet")) {
        Column(Modifier.padding(horizontal = Space.Section)) {
            Text(album, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Album cover", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Space.Small))
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Clear all images", onClick = onClearImages)
                .padding(horizontal = Space.Section, vertical = Space.Medium).testTag("clearAlbumImages"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(Space.Page))
            Column {
                Text("Clear all images", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Gets this album's pictures again from the server: here, in Shortcuts, recent plays and the mini player.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
