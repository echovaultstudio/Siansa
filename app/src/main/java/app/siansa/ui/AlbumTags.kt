package app.siansa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import app.siansa.data.Album
import app.siansa.log.AppLog

/** What an album page's tag stands for (#558): a genre or a record label. */
enum class AlbumTag(val route: String, val heading: String) { GENRE("genre", "Genre"), LABEL("label", "Record label") }

/** The page listing every album with [value] as its [tag] (#558). */
fun albumTagRoute(tag: AlbumTag, value: String): String =
    "albums/${tag.route}?value=" + java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

/**
 * The foot of an album's page (#558): how many songs and how long (with its version, "Super Deluxe"), then its
 * genres and record labels as tags. A tag opens every album in the library with it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlbumFootSection(foot: AlbumFoot, nav: NavController) {
    Column(
        Modifier.fillMaxWidth().padding(start = Space.Page, end = Space.Page, top = Space.Section).testTag("albumFoot"),
        verticalArrangement = Arrangement.spacedBy(Space.Medium),
    ) {
        Text(
            listOfNotNull(foot.version, foot.length).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("albumLength"),
        )
        TagGroup(if (foot.genres.size == 1) "Genre" else "Genres", foot.genres, "albumGenres") { genre ->
            AppLog.i("Album", "Open genre \"$genre\"")
            nav.navigate(albumTagRoute(AlbumTag.GENRE, genre))
        }
        TagGroup(if (foot.labels.size == 1) "Record label" else "Record labels", foot.labels, "albumLabels") { label ->
            AppLog.i("Album", "Open label \"$label\"")
            nav.navigate(albumTagRoute(AlbumTag.LABEL, label))
        }
    }
}

/** A heading with its tags under it, in rows that wrap; nothing when there are no tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroup(heading: String, tags: List<String>, testTag: String, onOpen: (String) -> Unit) {
    if (tags.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Space.Small), modifier = Modifier.testTag(testTag)) {
        Text(heading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
            tags.forEach { tag -> LibraryPill(tag, active = false, outlined = true) { onOpen(tag) } }
        }
    }
}

/**
 * Every album in the library with a genre or record label (#558), newest first, in the same grid (or list) as every
 * page of albums. Labels come from the library sync; until it has read them, the page says so.
 */
@Composable
fun AlbumsWithTagScreen(nav: NavController, tagRoute: String?, value: String) {
    val graph = LocalGraph.current
    val tag = AlbumTag.entries.firstOrNull { it.route == tagRoute } ?: AlbumTag.GENRE
    val albums by loadState("albums-${tag.route}-$value") {
        if (!graph.library.isSynced) emptyList<Album>()
        else when (tag) {
            AlbumTag.GENRE -> graph.library.albumsWithGenre(value)
            AlbumTag.LABEL -> graph.library.albumsWithLabel(value)
        }.also { AppLog.i("Album", "${tag.heading} \"$value\": ${it.size} albums") }
    }
    Screen(title = value, nav = nav) { padding ->
        LoadBox(albums, Modifier.padding(top = padding.calculateTopPadding())) { list ->
            if (list.isEmpty()) {
                Text(
                    albumTagEmptyText(tag, graph.library.isSynced),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Space.Page).testTag("albumTagEmpty"),
                )
            } else {
                Box(Modifier.fillMaxSize()) {
                    AlbumsGridOrList(
                        list,
                        page = "albums-${tag.route}",
                        onOpen = { nav.navigate("album/${it.id}") },
                        modifier = Modifier.fillMaxSize(),
                        title = "${tag.heading} · ${list.size} ${if (list.size == 1) "album" else "albums"}",
                    )
                }
            }
        }
    }
}

/** What the page says when it finds no albums. */
internal fun albumTagEmptyText(tag: AlbumTag, synced: Boolean): String = when {
    !synced -> "Sync your library to see every album with this ${tag.heading.lowercase()}."
    tag == AlbumTag.LABEL -> "No albums with this label yet. Labels are read on the next library sync."
    else -> "No albums with this genre."
}
