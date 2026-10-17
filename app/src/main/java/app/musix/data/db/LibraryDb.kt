package app.musix.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/** A song as stored on the phone. [searchKey] is the normalised "title artist album" used by search. */
@Entity(
    tableName = "songs",
    indices = [Index("albumId"), Index("artistId"), Index("genre"), Index("starred")],
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val artistId: String?,
    val album: String,
    val albumId: String?,
    val coverArt: String?,
    val durationSec: Int,
    val track: Int?,
    val disc: Int?,
    val year: Int?,
    val genre: String?,
    val trackGain: Float?,
    val albumGain: Float?,
    val trackPeak: Float?,
    val albumPeak: Float?,
    val starred: Boolean,
    val searchKey: String,
)

/**
 * An album as Navidrome lists it. Albums with the same [groupKey] (name + album artist) in different
 * libraries are one album on screen; [isPrimary] marks the copy that represents the group in lists.
 */
@Entity(
    tableName = "albums",
    indices = [Index("groupKey"), Index("artistId"), Index("artistKey"), Index("isPrimary")],
)
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artist: String,
    val artistId: String?,
    val coverArt: String?,
    val songCount: Int,
    val durationSec: Int,
    val year: Int?,
    val genre: String?,
    val created: String?,
    val playCount: Long,
    val releaseTypes: String,
    val isCompilation: Boolean,
    val sortKey: String,
    val artistKey: String,
    val groupKey: String,
    val signature: String,
    val isPrimary: Boolean = true,
)

@Entity(tableName = "artists", indices = [Index("sortKey")])
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val albumCount: Int,
    val coverArt: String?,
    val imageUrl: String?,
    val sortKey: String,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val songCount: Int,
    val durationSec: Int,
    val coverArt: String?,
    val owner: String?,
    val comment: String?,
    val changed: String?,
    val position: Int,
)

@Entity(tableName = "meta")
data class MetaEntity(@PrimaryKey val key: String, val value: String)

data class AlbumSignature(val id: String, val signature: String)
data class AlbumGroupRow(val id: String, val groupKey: String, val songCount: Int)
data class AlbumTypeRow(val releaseTypes: String, val isCompilation: Boolean)
data class GenreRow(val name: String, val songCount: Int)

@Dao
interface LibraryDao {
    // Songs
    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun song(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun songs(ids: List<String>): List<SongEntity>

    /** The album cover id for a song's cover id (a song without its own picture shows its album's). */
    @Query("SELECT a.coverArt FROM songs s JOIN albums a ON a.id = s.albumId WHERE s.coverArt = :songCover LIMIT 1")
    suspend fun albumCoverOfSongCover(songCover: String): String?

    @Query("SELECT * FROM songs WHERE albumId IN (:albumIds)")
    suspend fun songsOfAlbums(albumIds: List<String>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE artistId = :artistId OR (artistId IS NULL AND artist = :name COLLATE NOCASE)")
    suspend fun songsOfArtist(artistId: String, name: String): List<SongEntity>

    /** Songs whose artist is [artistId] or whose artist text contains [namePattern] (a LIKE pattern, escaped with '\\'). */
    @Query("SELECT * FROM songs WHERE artistId = :artistId OR artist LIKE :namePattern ESCAPE '\\'")
    suspend fun songsCrediting(artistId: String, namePattern: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE genre = :genre COLLATE NOCASE ORDER BY RANDOM() LIMIT :limit")
    suspend fun songsOfGenre(genre: String, limit: Int): List<SongEntity>

    @Query("SELECT * FROM songs WHERE searchKey LIKE '%' || :term || '%' LIMIT :limit")
    suspend fun searchSongs(term: String, limit: Int): List<SongEntity>

    @Query("SELECT * FROM songs WHERE starred = 1")
    suspend fun starredSongs(): List<SongEntity>

    @Query("UPDATE songs SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean)

    @Query("UPDATE songs SET starred = 0 WHERE starred = 1")
    suspend fun clearStarred()

    @Query("UPDATE songs SET starred = 1 WHERE id IN (:ids)")
    suspend fun markStarred(ids: List<String>)

    @Query(
        "SELECT genre AS name, COUNT(*) AS songCount FROM songs WHERE genre IS NOT NULL AND genre != '' " +
            "GROUP BY genre COLLATE NOCASE ORDER BY songCount DESC LIMIT :limit"
    )
    suspend fun genres(limit: Int): List<GenreRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSongs(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE albumId IN (:albumIds)")
    suspend fun deleteSongsOfAlbums(albumIds: List<String>)

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun songCount(): Int

    // Albums
    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun album(id: String): AlbumEntity?

    @Query("SELECT id FROM albums WHERE id IN (:ids)")
    suspend fun knownAlbumIds(ids: List<String>): List<String>

    @Query("SELECT * FROM albums WHERE groupKey = :groupKey ORDER BY isPrimary DESC")
    suspend fun albumGroup(groupKey: String): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE isPrimary = 1 ORDER BY sortKey")
    suspend fun albumsByName(): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE isPrimary = 1 ORDER BY created DESC")
    suspend fun albumsByNewest(): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE isPrimary = 1 ORDER BY year DESC, sortKey")
    suspend fun albumsByYear(): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE isPrimary = 1 ORDER BY playCount DESC")
    suspend fun albumsByPlays(): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE isPrimary = 1 ORDER BY RANDOM() LIMIT :limit")
    suspend fun albumsRandom(limit: Int): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE isPrimary = 1 AND (artistId = :artistId OR artistKey = :artistKey) ORDER BY year DESC")
    suspend fun albumsOfArtist(artistId: String, artistKey: String): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE isPrimary = 1 AND (sortKey LIKE '%' || :term || '%' OR artistKey LIKE '%' || :term || '%') ORDER BY sortKey LIMIT :limit")
    suspend fun searchAlbums(term: String, limit: Int): List<AlbumEntity>

    @Query("SELECT releaseTypes, isCompilation FROM albums WHERE isPrimary = 1")
    suspend fun albumTypes(): List<AlbumTypeRow>

    @Query("SELECT id, signature FROM albums")
    suspend fun albumSignatures(): List<AlbumSignature>

    @Query("SELECT id, groupKey, songCount FROM albums")
    suspend fun albumGroups(): List<AlbumGroupRow>

    @Query("UPDATE albums SET isPrimary = 0")
    suspend fun clearPrimary()

    @Query("UPDATE albums SET isPrimary = 1 WHERE id IN (:ids)")
    suspend fun markPrimary(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAlbums(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums WHERE id IN (:ids)")
    suspend fun deleteAlbums(ids: List<String>)

    @Query("SELECT COUNT(*) FROM albums WHERE isPrimary = 1")
    suspend fun albumCount(): Int

    // Artists
    @Query("SELECT * FROM artists ORDER BY sortKey")
    suspend fun artists(): List<ArtistEntity>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun artist(id: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE sortKey = :key LIMIT 1")
    suspend fun artistByKey(key: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE sortKey LIKE '%' || :term || '%' ORDER BY sortKey LIMIT :limit")
    suspend fun searchArtists(term: String, limit: Int): List<ArtistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putArtists(artists: List<ArtistEntity>)

    @Query("DELETE FROM artists")
    suspend fun deleteAllArtists()

    @Query("SELECT COUNT(*) FROM artists")
    suspend fun artistCount(): Int

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY position")
    suspend fun playlists(): List<PlaylistEntity>

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPlaylists(playlists: List<PlaylistEntity>)

    // Meta
    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun meta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(meta: MetaEntity)
}

@Database(
    entities = [SongEntity::class, AlbumEntity::class, ArtistEntity::class, PlaylistEntity::class, MetaEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        fun open(context: Context): LibraryDatabase =
            Room.databaseBuilder(context, LibraryDatabase::class.java, app.musix.data.Users.scoped(app.musix.data.UserFiles.DATABASE))
                // The library can always be synced again, so a schema change simply starts fresh.
                .fallbackToDestructiveMigration()
                .build()
    }
}
