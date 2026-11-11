package app.siansa.car

import app.siansa.net.soundReady
import app.siansa.AppGraph
import app.siansa.data.Album
import app.siansa.data.Artist
import app.siansa.data.Mix
import app.siansa.data.Playlist
import app.siansa.data.PlaylistDetail
import app.siansa.data.RecentPlay
import app.siansa.data.SearchResults
import app.siansa.data.Song
import app.siansa.mix.MoodRadio
import app.siansa.mix.moodRadios

/** The car's browse tree (issue #62) made from the phone's synced library, its mixes and the server's search. */
class GraphCarLibrary(private val graph: AppGraph) : CarLibrary {
    override suspend fun mixes(): List<Mix> = latestMixes(graph.mixes.all())
    override suspend fun mix(id: String): Mix? = graph.mixes.get(id)
    override fun moods(): List<MoodRadio> = if (graph.settings.current.soundReady) moodRadios else emptyList()
    override suspend fun moodSongs(id: String): List<Song> = moodRadios.firstOrNull { it.id == id }?.let { graph.radios.mood(it) }.orEmpty()
    override suspend fun recent(): List<RecentPlay> = graph.recentPlays.all()
    override suspend fun playlists(): List<Playlist> = graph.library.playlists()
    override suspend fun playlist(id: String): PlaylistDetail? = runCatching { graph.playlistCache.get(id) }.getOrNull()
    override suspend fun albums(): List<Album> = graph.library.albums("name")
    override suspend fun album(id: String): Album? = graph.library.album(id)
    override suspend fun albumSongs(id: String): List<Song> =
        graph.library.album(id)?.let { graph.library.songsOf(it) } ?: graph.source.album(id).songs
    override suspend fun artists(): List<Artist> = graph.library.artists()
    override suspend fun artist(id: String): Artist? = graph.library.artist(id)
    override suspend fun artistSongs(id: String): List<Song> = graph.library.artist(id)?.let { graph.library.songsOf(it) }.orEmpty()
    override suspend fun search(query: String): SearchResults = graph.source.search(query)
    override suspend fun anything(): List<Song> = graph.library.randomSongs(100)
}

/** The newest mix of each vibe, newest first: what Home shows as today's mixes. */
fun latestMixes(all: List<Mix>): List<Mix> =
    all.groupBy { it.selectorId }.values.mapNotNull { mixes -> mixes.maxByOrNull { it.createdAt } }
        .filter { it.songs.isNotEmpty() }
        .sortedByDescending { it.createdAt }
