package app.siansa.net

import app.siansa.mix.SongPlays
import app.siansa.data.Album
import app.siansa.data.AlbumDetail
import app.siansa.data.Artist
import app.siansa.data.ArtistDetail
import app.siansa.data.ArtistInfo
import app.siansa.data.Lyrics
import app.siansa.data.MusicFolder
import app.siansa.data.Playlist
import app.siansa.data.PlaylistDetail
import app.siansa.data.SearchResults
import app.siansa.data.Song

/**
 * The source in use (issue #113): the server the settings name, asked again on every call, so choosing Emby or
 * Navidrome in setup takes effect at once without restarting the app.
 *
 * A call about one song, album, artist, playlist or cover goes to [route]'s source when it gives one: another
 * user's music shown next to the user in use's goes to their server, whatever kind it is (issue #427).
 */
class ActiveSource(
    private val route: (String?) -> MusicSource? = { null },
    private val pick: () -> MusicSource,
) : MusicSource {
    private fun of(id: String?): MusicSource = route(id) ?: pick()

    override val kind: SourceKind get() = pick().kind
    override suspend fun playbackProgress(id: String, positionMs: Long, paused: Boolean) = of(id).playbackProgress(id, positionMs, paused)
    override suspend fun playbackStopped(id: String, positionMs: Long) = of(id).playbackStopped(id, positionMs)

    override fun streamUrl(id: String): String = of(id).streamUrl(id)
    override fun coverUrl(id: String, size: Int?): String = of(id).coverUrl(id, size)
    override suspend fun ping(): String = pick().ping()
    override suspend fun musicFolders(): List<MusicFolder> = pick().musicFolders()
    override suspend fun hasSonicMatch(): Boolean = pick().hasSonicMatch()
    override suspend fun lastScan(): String? = pick().lastScan()
    override suspend fun artists(folderId: String?): List<Artist> = pick().artists(folderId)
    override suspend fun songsPage(offset: Int, size: Int, folderId: String?): List<Song> = pick().songsPage(offset, size, folderId)
    override suspend fun albums(type: String, size: Int, offset: Int, folderId: String?): List<Album> = pick().albums(type, size, offset, folderId)
    override suspend fun albums(type: String, size: Int, offset: Int): List<Album> = pick().albums(type, size, offset)
    override suspend fun album(id: String): AlbumDetail = of(id).album(id)
    override suspend fun artist(id: String): ArtistDetail = of(id).artist(id)
    override suspend fun artistInfo(id: String, includeNotPresent: Boolean): ArtistInfo = of(id).artistInfo(id, includeNotPresent)
    override suspend fun topSongs(artistName: String, count: Int): List<Song> = pick().topSongs(artistName, count)
    override suspend fun song(id: String): Song = of(id).song(id)
    override suspend fun songDetails(id: String): SongDetails = of(id).songDetails(id)
    override suspend fun randomSongs(size: Int, genre: String?, fromYear: Int?, toYear: Int?): List<Song> = pick().randomSongs(size, genre, fromYear, toYear)
    override suspend fun randomSongPlays(size: Int): List<Pair<Song, Long>> = pick().randomSongPlays(size)
    override suspend fun recentSongPlays(albumCount: Int): List<SongPlays> = pick().recentSongPlays(albumCount)
    override suspend fun search(query: String): SearchResults = pick().search(query)
    override suspend fun lyrics(song: Song): Lyrics? = of(song.id).lyrics(song)
    override suspend fun similarSongs(songId: String, count: Int): List<Song> = of(songId).similarSongs(songId, count)
    override suspend fun artistRadio(artistId: String, count: Int): List<Song> = of(artistId).artistRadio(artistId, count)
    override suspend fun sonicSimilar(songId: String, count: Int): List<Pair<Song, Double>> = of(songId).sonicSimilar(songId, count)
    override suspend fun sonicPath(startId: String, endId: String, count: Int): List<Song> = of(startId).sonicPath(startId, endId, count)
    override suspend fun playlists(): List<Playlist> = pick().playlists()
    override suspend fun playlist(id: String): PlaylistDetail = of(id).playlist(id)
    override suspend fun createPlaylist(name: String, songIds: List<String>): Playlist? = pick().createPlaylist(name, songIds)
    override suspend fun renamePlaylist(playlistId: String, name: String) = of(playlistId).renamePlaylist(playlistId, name)
    override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>) = of(playlistId).removeFromPlaylist(playlistId, indexes)
    override suspend fun addToPlaylist(playlistId: String, songIds: List<String>) = of(playlistId).addToPlaylist(playlistId, songIds)
    override suspend fun star(id: String) = of(id).star(id)
    override suspend fun unstar(id: String) = of(id).unstar(id)
    override suspend fun starAlbum(albumId: String) = of(albumId).starAlbum(albumId)
    override suspend fun unstarAlbum(albumId: String) = of(albumId).unstarAlbum(albumId)
    override suspend fun starredAlbums(): List<Album> = pick().starredAlbums()
    override suspend fun starredSongs(): List<Song> = pick().starredSongs()
    override suspend fun starArtist(artistId: String) = of(artistId).starArtist(artistId)
    override suspend fun unstarArtist(artistId: String) = of(artistId).unstarArtist(artistId)
    override suspend fun starredArtists(): List<Artist> = pick().starredArtists()
    override suspend fun scrobble(id: String, submission: Boolean, timeMs: Long?) = of(id).scrobble(id, submission, timeMs)
}
