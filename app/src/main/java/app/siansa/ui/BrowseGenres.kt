package app.siansa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.AppGraph
import app.siansa.data.Genre
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The genres to browse (Search, Home's "Your genres" and its page of all): Siansa Beats' genres by vote as last saved
 * on the phone, or the songs' tags when there are none (no AudioMuse, no Siansa Beats, or it hasn't voted enough yet):
 * Siansa works fully without Siansa Beats. Genres only in hidden libraries are left out. At most [limit].
 */
suspend fun AppGraph.browseGenres(limit: Int): List<Genre> {
    if (!library.isSynced) return emptyList()
    val voted = withContext(Dispatchers.IO) { genreVote.saved(shownLibraries()) }
    return voted?.take(limit) ?: library.genres(limit)
}

/**
 * [AppGraph.browseGenres] for a page, at once (the saved list, never waiting on Siansa Beats), then Siansa Beats'
 * newest list when the saved one is old and it answers within a few seconds. null while the first is read.
 */
@Composable
fun rememberBrowseGenres(limit: Int): State<List<Genre>?> {
    val graph = LocalGraph.current
    val version by graph.library.version.collectAsStateWithLifecycle()
    return produceState<List<Genre>?>(initialValue = null, version, limit) {
        value = graph.browseGenres(limit)
        if (graph.library.isSynced && graph.genreVote.stale()) graph.genreVote.refresh(graph.shownLibraries())?.let { value = it.take(limit) }
    }
}

/**
 * The libraries whose songs are on the phone: those chosen to sync in Home's filter (empty: every library), so a genre
 * only in hidden libraries isn't offered ([app.siansa.mix.GenreVote.inLibraries]).
 */
fun AppGraph.shownLibraries(): Set<String> = settings.current.syncFolders
