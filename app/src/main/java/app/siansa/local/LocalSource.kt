package app.siansa.local

import android.net.Uri
import app.siansa.data.Album
import app.siansa.data.AlbumDetail
import app.siansa.data.Artist
import app.siansa.data.ArtistDetail
import app.siansa.data.ArtistInfo
import app.siansa.data.LibraryRepository
import app.siansa.data.Lyrics
import app.siansa.data.MusicFolder
import app.siansa.data.Playlist
import app.siansa.data.PlaylistDetail
import app.siansa.data.SearchResults
import app.siansa.data.Song
import app.siansa.net.ApiException
import app.siansa.net.MusicSource
import app.siansa.net.SongDetails
import app.siansa.net.SongTags
import app.siansa.net.SourceKind
import app.siansa.net.codecName

/**
 * Music files on the phone as a [MusicSource] (issue #352): every request about a local song, album, artist or cover
 * ([LocalIds]) comes here. Songs play from their file; lists come from the library. What only a server can do (similar
 * songs, lyrics, play counts, hearts) goes to the server copy of the song when it has one ([LocalMusic.serverIdOf]),
 * and is empty for a song only on the phone.
 */
class LocalSource(
    private val local: LocalMusic,
    private val library: LibraryRepository,
    private val server: () -> MusicSource,
) : MusicSource {
    override val kind: SourceKind get() = SourceKind.LOCAL

    override fun streamUrl(id: String): String = local.uriOf(id) ?: ""

    override fun coverUrl(id: String, size: Int?): String =
        local.coverFile(id)?.let { Uri.fromFile(it).toString() } ?: ""

    private fun serverId(id: String): String? = local.serverIdOf(id)

    override suspend fun ping(): String = "Files on this phone"
    override suspend fun musicFolders(): List<MusicFolder> = emptyList()
    override suspend fun hasSonicMatch(): Boolean = server().hasSonicMatch()
    override suspend fun lastScan(): String? = null
    override suspend fun artists(folderId: String?): List<Artist> = emptyList()
    override suspend fun songsPage(offset: Int, size: Int, folderId: String?): List<Song> = emptyList()
    override suspend fun albums(type: String, size: Int, offset: Int, folderId: String?): List<Album> = emptyList()
    override suspend fun albums(type: String, size: Int, offset: Int): List<Album> = emptyList()

    override suspend fun album(id: String): AlbumDetail {
        val album = library.album(id) ?: throw ApiException("This album isn't on the phone any more")
        return AlbumDetail(album, library.songsOf(album))
    }

    override suspend fun artist(id: String): ArtistDetail {
        val artist = library.artist(id) ?: throw ApiException("This artist isn't on the phone any more")
        return ArtistDetail(artist, library.albumsOf(artist))
    }

    override suspend fun artistInfo(id: String, includeNotPresent: Boolean): ArtistInfo = ArtistInfo(null, null, null, emptyList())
    override suspend fun topSongs(artistName: String, count: Int): List<Song> = emptyList()

    override suspend fun song(id: String): Song = library.song(id) ?: throw ApiException("This song isn't on the phone any more")

    override suspend fun songDetails(id: String): SongDetails {
        val t = local.track(id) ?: throw ApiException("This song isn't on the phone any more")
        val folder = local.data.value.folders.firstOrNull { it.uri == t.folder }?.name
        val serverDetails = serverId(id)?.let { runCatching { server().songDetails(it) }.getOrNull() }
        return SongDetails(
            codec = codecName(t.suffix, t.mime),
            sizeBytes = t.size.takeIf { it > 0 },
            path = listOfNotNull(folder, t.path).joinToString("/"),
            playCount = serverDetails?.playCount,
            tags = SongTags(
                artists = t.artists,
                albumArtists = listOfNotNull(t.albumArtist.takeIf { it.isNotBlank() }),
                genres = listOfNotNull(t.genre),
                year = t.year,
                musicBrainzId = t.recording,
            ),
        )
    }

    override suspend fun randomSongs(size: Int, genre: String?, fromYear: Int?, toYear: Int?): List<Song> = library.randomSongs(size)
    override suspend fun randomSongPlays(size: Int): List<Pair<Song, Long>> = emptyList()
    override suspend fun search(query: String): SearchResults = library.search(query)

    override suspend fun lyrics(song: Song): Lyrics? = serverId(song.id)?.let { server().lyrics(song.copy(id = it)) }

    override suspend fun similarSongs(songId: String, count: Int): List<Song> = serverId(songId)?.let { server().similarSongs(it, count) } ?: emptyList()
    override suspend fun artistRadio(artistId: String, count: Int): List<Song> = emptyList()
    override suspend fun sonicSimilar(songId: String, count: Int): List<Pair<Song, Double>> =
        serverId(songId)?.let { server().sonicSimilar(it, count) } ?: throw ApiException(ONLY_ON_PHONE)
    override suspend fun sonicPath(startId: String, endId: String, count: Int): List<Song> {
        val start = serverId(startId) ?: throw ApiException(ONLY_ON_PHONE)
        val end = serverId(endId) ?: throw ApiException(ONLY_ON_PHONE)
        return server().sonicPath(start, end, count)
    }

    override suspend fun playlists(): List<Playlist> = emptyList()
    override suspend fun playlist(id: String): PlaylistDetail = throw ApiException("Playlists aren't kept with files on this phone")
    override suspend fun createPlaylist(name: String, songIds: List<String>): Playlist? = null
    override suspend fun renamePlaylist(playlistId: String, name: String) = Unit
    override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>) = Unit
    override suspend fun addToPlaylist(playlistId: String, songIds: List<String>) = Unit

    // Hearts go to the server copy; a song only on the phone is liked on the phone only.
    override suspend fun star(id: String) { serverId(id)?.let { server().star(it) } }
    override suspend fun unstar(id: String) { serverId(id)?.let { server().unstar(it) } }
    override suspend fun starAlbum(albumId: String) = Unit
    override suspend fun unstarAlbum(albumId: String) = Unit
    override suspend fun starredAlbums(): List<Album> = emptyList()
    override suspend fun starredSongs(): List<Song> = emptyList()
    override suspend fun starArtist(artistId: String) = Unit
    override suspend fun unstarArtist(artistId: String) = Unit
    override suspend fun starredArtists(): List<Artist> = emptyList()

    // Plays of a song that's on the server too count there, so its play count and Navidrome's history stay right.
    override suspend fun scrobble(id: String, submission: Boolean, timeMs: Long?) {
        serverId(id)?.let { server().scrobble(it, submission, timeMs) }
    }
    override suspend fun playbackProgress(id: String, positionMs: Long, paused: Boolean) {
        serverId(id)?.let { server().playbackProgress(it, positionMs, paused) }
    }
    override suspend fun playbackStopped(id: String, positionMs: Long) {
        serverId(id)?.let { server().playbackStopped(it, positionMs) }
    }

    companion object {
        /** Why AudioMuse and the server can't make a mix or radio from a song only on the phone. */
        const val ONLY_ON_PHONE = "This song is only on your phone, so AudioMuse hasn't analysed it. Mixes and radios " +
            "use songs that are on your server too."
    }
}
