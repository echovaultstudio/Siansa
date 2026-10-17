package app.musix.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musix.data.Album
import app.musix.data.AlbumCategory
import app.musix.data.category
import app.musix.log.AppLog

/**
 * The release types an artist has (Albums, EPs, Singles, DJ-mixes…), in display order, for the chips on
 * their page. Empty when there's only one type, since a filter would change nothing.
 */
fun releaseTypeChoices(albums: List<Album>): List<AlbumCategory> {
    val types = albums.map { it.category }.distinct().sortedBy { it.ordinal }
    return if (types.size < 2) emptyList() else types
}

/** The chosen type, saved by name; nothing when it's unknown or the artist no longer has that type. */
fun chosenReleaseType(saved: String?, choices: List<AlbumCategory>): AlbumCategory? =
    choices.firstOrNull { it.name == saved }

/** The discography groups to show: all of them, or only the chosen type's. */
fun <T> onlyReleaseType(groups: List<Pair<AlbumCategory, T>>, type: AlbumCategory?): List<Pair<AlbumCategory, T>> =
    if (type == null) groups else groups.filter { it.first == type }

/** The space above and below the release-type chips, so they sit evenly between Popular and the discography. */
val CHIPS_GAP = 20.dp

/** The chosen release type's name, kept while the page is open (also across rotation), never saved for good. */
@Composable
fun rememberReleaseTypeFilter(): MutableState<String?> = rememberSaveable { mutableStateOf<String?>(null) }

/**
 * Chips like the ones in Your Library: every type the artist has, or ✕ + the chosen type. Tapping the
 * chosen type or ✕ shows everything again.
 */
@Composable
fun ReleaseTypeRow(
    choices: List<AlbumCategory>,
    selected: AlbumCategory?,
    modifier: Modifier = Modifier,
    onSelect: (AlbumCategory?) -> Unit,
) {
    val pick: (AlbumCategory?) -> Unit = { type ->
        AppLog.d("Artist", "Release type filter: ${type?.name ?: "all"}")
        onSelect(type)
    }
    Row(
        // The same space above the chips (after Popular) as below them (before the first heading). The
        // song row above already has 6 dp of its own below its cover, so less is added here.
        modifier.fillMaxWidth().padding(top = CHIPS_GAP - 6.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected == null) {
            choices.forEach { type -> TypePill(type.plural, active = false) { pick(type) } }
        } else {
            Surface(
                onClick = { pick(null) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(36.dp),
            ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "Clear filter", Modifier.size(20.dp)) } }
            TypePill(selected.plural, active = true) { pick(null) }
        }
    }
}

@Composable
private fun TypePill(text: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) Accent else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}
