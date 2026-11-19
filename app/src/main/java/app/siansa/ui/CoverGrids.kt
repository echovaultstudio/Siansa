package app.siansa.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.siansa.AppGraph
import app.siansa.data.Album
import app.siansa.data.Artist
import app.siansa.data.CoverGrid
import app.siansa.data.Song
import app.siansa.data.cover
import app.siansa.data.normalizeKey
import app.siansa.log.AppLog

/** The cover grid of the Home row being drawn (#1263): Arrange Home's choice for it, null for each card's own picture. */
val LocalCoverGrid = compositionLocalOf<CoverGrid?> { null }

/**
 * What a card on Home is, for the covers its grid shows (#1263, the owner, 19 Nov): an album its own cover first, then
 * its artist's other albums; an artist's radio the albums it plays from (the artist's, then those of artists like
 * them); a mix or a list its songs' covers; a playlist its songs'; a genre its albums'; a shortcut what it opens.
 */
sealed interface GridSubject {
    data class OfAlbum(val album: Album) : GridSubject
    data class OfArtist(val artist: Artist) : GridSubject
    data class OfSongs(val songs: List<Song>) : GridSubject
    data class OfCovers(val covers: List<String?>) : GridSubject
    data class OfPlaylist(val id: String) : GridSubject
    data class OfGenre(val name: String) : GridSubject
    /** A page of the app ("album/…", "playlist/…", "mix/…", "artist/…"), as a shortcut holds it. */
    data class OfRoute(val route: String) : GridSubject
}

/** How wide a card [base] wide is with the row's cover grid: twice as wide for 2×4 and 3×6. */
@Composable
fun gridWidth(base: Dp): Dp = base * (LocalCoverGrid.current?.aspect ?: 1f)

/**
 * A card's picture with the row's cover grid when it has one and [subject] gives enough covers, else [single] (its own
 * picture). [modifier] sets the width; the height follows the grid's shape.
 */
@Composable
fun GridOrPicture(subject: GridSubject?, modifier: Modifier, shape: Shape = RoundedCornerShape(8.dp), single: @Composable (Modifier) -> Unit) {
    val grid = LocalCoverGrid.current
    if (grid == null || subject == null) {
        single(modifier.aspectRatio(1f))
        return
    }
    val covers = rememberGridCovers(subject, grid.count)
    val cells = CoverGrid.cells(covers, grid.count)
    if (cells.isEmpty()) single(modifier.aspectRatio(grid.aspect))
    else CoverGridArt(cells, grid, modifier.aspectRatio(grid.aspect), shape)
}

/** [cells] (rows × cols of them) in [grid]'s shape, each cover square, the outer corners in [shape]. */
@Composable
fun CoverGridArt(cells: List<String>, grid: CoverGrid, modifier: Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    Column(modifier.clip(shape).testTag("coverGridArt-${grid.id}")) {
        for (row in 0 until grid.rows) {
            Row(Modifier.weight(1f)) {
                for (col in 0 until grid.cols) {
                    // The small size: a cell is a third of a card or less.
                    Cover(cells.getOrNull(row * grid.cols + col), 150, Modifier.weight(1f).fillMaxSize(), androidx.compose.ui.graphics.RectangleShape)
                }
            }
        }
    }
}

/** The covers for [subject]'s grid, at least [count] when there are, looked up once per subject. */
@Composable
fun rememberGridCovers(subject: GridSubject, count: Int): List<String?> {
    val graph = LocalGraph.current
    val covers by produceState(initialCovers(subject), subject, count) {
        value = runCatching { gridCovers(graph, subject, count) }
            .onFailure { AppLog.d("Home", "Cover grid for $subject: ${it.message}") }
            .getOrDefault(value)
    }
    return covers
}

/** What a grid can show before anything is looked up. */
private fun initialCovers(subject: GridSubject): List<String?> = when (subject) {
    is GridSubject.OfSongs -> subject.songs.map { it.cover }
    is GridSubject.OfCovers -> subject.covers
    is GridSubject.OfAlbum -> listOf(subject.album.coverArt)
    else -> emptyList()
}

private suspend fun gridCovers(graph: AppGraph, subject: GridSubject, count: Int): List<String?> = when (subject) {
    is GridSubject.OfSongs -> subject.songs.map { it.cover }
    is GridSubject.OfCovers -> subject.covers
    is GridSubject.OfAlbum -> albumGridCovers(subject.album, graph.library.artistByName(subject.album.artist)?.let { graph.library.albumsOf(it) }.orEmpty())
    is GridSubject.OfArtist -> artistGridCovers(graph, subject.artist, count)
    is GridSubject.OfPlaylist -> graph.playlistCache.get(subject.id).songs.map { it.cover }
    is GridSubject.OfGenre -> graph.library.albumsWithGenre(subject.name).map { it.coverArt }
    is GridSubject.OfRoute -> routeGridCovers(graph, subject.route, count)
}

/** An album's grid: its own cover first, then its artist's other albums, each once (#1263). */
internal fun albumGridCovers(album: Album, artistAlbums: List<Album>): List<String?> =
    (listOf(album.coverArt) + artistAlbums.filter { it.id != album.id }.map { it.coverArt }).filterNotNull().distinct()

/** An artist radio's grid: the artist's albums, then the albums of artists like them in the library (#1263). */
private suspend fun artistGridCovers(graph: AppGraph, artist: Artist, count: Int): List<String?> {
    val own = graph.library.albumsOf(artist).map { it.coverArt }
    if (own.filterNotNull().distinct().size >= count) return own
    val like = runCatching { graph.similar.artists(artist.name) }.getOrDefault(emptyList())
    val theirs = like.asSequence()
        .mapNotNull { s -> s.id?.let { graph.library.artist(it) } ?: graph.library.artistByName(s.name) }
        .filter { normalizeKey(it.name) != normalizeKey(artist.name) }
        .take(SIMILAR_ARTISTS)
        .toList()
    return own + theirs.flatMap { graph.library.albumsOf(it).take(ALBUMS_EACH).map { a -> a.coverArt } }
}

private suspend fun routeGridCovers(graph: AppGraph, route: String, count: Int): List<String?> {
    val id = route.substringAfter('/')
    return when (route.substringBefore('/')) {
        "album" -> graph.library.album(id)?.let { gridCovers(graph, GridSubject.OfAlbum(it), count) }.orEmpty()
        "artist" -> graph.library.artist(id)?.let { artistGridCovers(graph, it, count) }.orEmpty()
        "playlist" -> graph.playlistCache.get(id).songs.map { it.cover }
        "mix" -> graph.mixes.get(id)?.songs?.map { it.cover }.orEmpty()
        else -> emptyList()
    }
}

/** How many artists like an artist, and how many albums of each, an artist radio's grid looks at. */
private const val SIMILAR_ARTISTS = 6
private const val ALBUMS_EACH = 3
