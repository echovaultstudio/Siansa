package app.siansa.net

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
 * The kinds of place your music can come from (issue #113), in the order the first-run picker lists them.
 * Only [ready] ones can be picked; the rest show as "Coming soon" until their own pull request adds them.
 */
enum class SourceKind(val label: String, val detail: String, val ready: Boolean) {
    OPENSUBSONIC("OpenSubsonic", "Navidrome, Gonic, Airsonic, Ampache and other Subsonic servers", ready = true),
    JELLYFIN("Jellyfin", "Coming soon", ready = false),
    EMBY("Emby", "Emby Server", ready = true),
    LOCAL("Files on this phone", "Coming soon", ready = false),
    ;

    companion object {
        /** The saved kind, or OpenSubsonic for anything unknown (and for everyone set up before #113). */
        fun parse(name: String?): SourceKind = entries.firstOrNull { it.name == name && it.ready } ?: OPENSUBSONIC
    }
}

/**
 * What Siansa needs from a music server (issue #113): its music, its playlists and favourites, and telling it
 * what was played. Screens, mixes and services use this rather than one server's API, so each kind of source
 * ([SourceKind]) only has to provide these. [SubsonicApi] is the OpenSubsonic one, [EmbyApi] the Emby one.
 *
 * Still OpenSubsonic only: other users' logins ([app.siansa.data.Guests]) and the Navidrome-only extras
 * (AudioMuse's plugin, Navidrome's own API for smart playlists).
 */
interface MusicSource {
    val kind: SourceKind

    // ---- Addresses ----

    /** Where the untouched original file of a song plays from. */
    fun streamUrl(id: String): String

    /** A cover picture, at [size] pixels when given. */
    fun coverUrl(id: String, size: Int?): String

    // ---- Server ----

    /** Says hello; the answer names the server and its version, for the connection test. */
    suspend fun ping(): String

    /** The server's libraries (music folders), for choosing which ones Siansa uses. */
    suspend fun musicFolders(): List<MusicFolder>

    /** Whether the server can find songs that sound alike by itself (OpenSubsonic sonicSimilarity). */
    suspend fun hasSonicMatch(): Boolean

    // ---- Library sync ----

    /** When the server last scanned its music, to skip syncs with no changes; null when it can't say. */
    suspend fun lastScan(): String?

    /** Every artist, of one library ([folderId]) or all. */
    suspend fun artists(folderId: String? = null): List<Artist>

    /** One page of every song, of one library ([folderId]) or all. */
    suspend fun songsPage(offset: Int, size: Int, folderId: String? = null): List<Song>

    /** A list of albums of one library ([folderId]), or all for null. */
    suspend fun albums(type: String, size: Int, offset: Int, folderId: String?): List<Album>

    // ---- Music ----

    /** A list of albums: [type] is "newest", "recent", "frequent", "random", "alphabeticalByName"… */
    suspend fun albums(type: String, size: Int, offset: Int): List<Album>
    suspend fun album(id: String): AlbumDetail
    suspend fun artist(id: String): ArtistDetail
    suspend fun artistInfo(id: String, includeNotPresent: Boolean = false): ArtistInfo
    suspend fun topSongs(artistName: String, count: Int = 10): List<Song>
    suspend fun song(id: String): Song
    suspend fun songDetails(id: String): SongDetails
    suspend fun randomSongs(size: Int, genre: String? = null, fromYear: Int? = null, toYear: Int? = null): List<Song>

    /** Random songs with how often each was played, for the dice (issue #347). */
    suspend fun randomSongPlays(size: Int): List<Pair<Song, Long>>
    suspend fun search(query: String): SearchResults
    suspend fun lyrics(song: Song): Lyrics?

    // ---- Similar ----

    suspend fun similarSongs(songId: String, count: Int = 50): List<Song>
    suspend fun artistRadio(artistId: String, count: Int = 50): List<Song>
    suspend fun sonicSimilar(songId: String, count: Int): List<Pair<Song, Double>>
    suspend fun sonicPath(startId: String, endId: String, count: Int): List<Song>

    // ---- Playlists ----

    suspend fun playlists(): List<Playlist>
    suspend fun playlist(id: String): PlaylistDetail
    suspend fun createPlaylist(name: String, songIds: List<String>): Playlist?
    suspend fun renamePlaylist(playlistId: String, name: String)
    suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>)
    suspend fun addToPlaylist(playlistId: String, songIds: List<String>)

    // ---- Favourites ----

    suspend fun star(id: String)
    suspend fun unstar(id: String)
    suspend fun starAlbum(albumId: String)
    suspend fun unstarAlbum(albumId: String)
    suspend fun starredAlbums(): List<Album>
    suspend fun starredSongs(): List<Song>
    suspend fun starArtist(artistId: String)
    suspend fun unstarArtist(artistId: String)
    suspend fun starredArtists(): List<Artist>

    // ---- Listening ----

    /** Tells the server a song is playing ([submission] false) or was played ([submission] true). */
    suspend fun scrobble(id: String, submission: Boolean, timeMs: Long? = null)

    /**
     * Where the playing song is, for servers that follow playback as it happens (Emby's now-playing timeline):
     * sent on play and pause, and every so often while playing. Nothing for servers without one.
     */
    suspend fun playbackProgress(id: String, positionMs: Long, paused: Boolean) {}

    /** The song stopped being played, [positionMs] in. Nothing for servers without a timeline. */
    suspend fun playbackStopped(id: String, positionMs: Long) {}
}
