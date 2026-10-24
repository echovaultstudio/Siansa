package app.musix.net

import app.musix.data.Album
import app.musix.data.AlbumDetail
import app.musix.data.Artist
import app.musix.data.ArtistDetail
import app.musix.data.ArtistInfo
import app.musix.data.Lyrics
import app.musix.data.MusicFolder
import app.musix.data.Playlist
import app.musix.data.PlaylistDetail
import app.musix.data.SearchResults
import app.musix.data.Song

/**
 * The source in use (issue #113): the server the settings name, asked again on every call, so choosing Emby or
 * Navidrome in setup takes effect at once without restarting the app.
 */
class ActiveSource(private val pick: () -> MusicSource) : MusicSource {
    override val kind: SourceKind get() = pick().kind
    override suspend fun playbackProgress(id: String, positionMs: Long, paused: Boolean) = pick().playbackProgress(id, positionMs, paused)
    override suspend fun playbackStopped(id: String, positionMs: Long) = pick().playbackStopped(id, positionMs)

    override fun streamUrl(id: String): String = pick().streamUrl(id)
    override fun coverUrl(id: String, size: Int?): String = pick().coverUrl(id, size)
    override suspend fun ping(): String = pick().ping()
    override suspend fun musicFolders(): List<MusicFolder> = pick().musicFolders()
    override suspend fun hasSonicMatch(): Boolean = pick().hasSonicMatch()
    override suspend fun lastScan(): String? = pick().lastScan()
    override suspend fun artists(folderId: String?): List<Artist> = pick().artists(folderId)
    override suspend fun songsPage(offset: Int, size: Int, folderId: String?): List<Song> = pick().songsPage(offset, size, folderId)
    override suspend fun albums(type: String, size: Int, offset: Int, folderId: String?): List<Album> = pick().albums(type, size, offset, folderId)
    override suspend fun albums(type: String, size: Int, offset: Int): List<Album> = pick().albums(type, size, offset)
    override suspend fun album(id: String): AlbumDetail = pick().album(id)
    override suspend fun artist(id: String): ArtistDetail = pick().artist(id)
    override suspend fun artistInfo(id: String, includeNotPresent: Boolean): ArtistInfo = pick().artistInfo(id, includeNotPresent)
    override suspend fun topSongs(artistName: String, count: Int): List<Song> = pick().topSongs(artistName, count)
    override suspend fun song(id: String): Song = pick().song(id)
    override suspend fun songDetails(id: String): SongDetails = pick().songDetails(id)
    override suspend fun randomSongs(size: Int, genre: String?, fromYear: Int?, toYear: Int?): List<Song> = pick().randomSongs(size, genre, fromYear, toYear)
    override suspend fun randomSongPlays(size: Int): List<Pair<Song, Long>> = pick().randomSongPlays(size)
    override suspend fun search(query: String): SearchResults = pick().search(query)
    override suspend fun lyrics(song: Song): Lyrics? = pick().lyrics(song)
    override suspend fun similarSongs(songId: String, count: Int): List<Song> = pick().similarSongs(songId, count)
    override suspend fun artistRadio(artistId: String, count: Int): List<Song> = pick().artistRadio(artistId, count)
    override suspend fun sonicSimilar(songId: String, count: Int): List<Pair<Song, Double>> = pick().sonicSimilar(songId, count)
    override suspend fun sonicPath(startId: String, endId: String, count: Int): List<Song> = pick().sonicPath(startId, endId, count)
    override suspend fun playlists(): List<Playlist> = pick().playlists()
    override suspend fun playlist(id: String): PlaylistDetail = pick().playlist(id)
    override suspend fun createPlaylist(name: String, songIds: List<String>): Playlist? = pick().createPlaylist(name, songIds)
    override suspend fun renamePlaylist(playlistId: String, name: String) = pick().renamePlaylist(playlistId, name)
    override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>) = pick().removeFromPlaylist(playlistId, indexes)
    override suspend fun addToPlaylist(playlistId: String, songIds: List<String>) = pick().addToPlaylist(playlistId, songIds)
    override suspend fun star(id: String) = pick().star(id)
    override suspend fun unstar(id: String) = pick().unstar(id)
    override suspend fun starAlbum(albumId: String) = pick().starAlbum(albumId)
    override suspend fun unstarAlbum(albumId: String) = pick().unstarAlbum(albumId)
    override suspend fun starredAlbums(): List<Album> = pick().starredAlbums()
    override suspend fun starredSongs(): List<Song> = pick().starredSongs()
    override suspend fun starArtist(artistId: String) = pick().starArtist(artistId)
    override suspend fun unstarArtist(artistId: String) = pick().unstarArtist(artistId)
    override suspend fun starredArtists(): List<Artist> = pick().starredArtists()
    override suspend fun scrobble(id: String, submission: Boolean, timeMs: Long?) = pick().scrobble(id, submission, timeMs)
}
