package app.siansa.data.db

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
/** Songs whose library is shown (issue #376): no album, or an album in a chosen library. */
const val VISIBLE_SONG = " AND (albumId IS NULL OR albumId IN (SELECT id FROM albums WHERE visible = 1))"

/** Artists with something in a shown library, or another user's (issue #376). */
const val VISIBLE_ARTIST = "(id IN (SELECT artistId FROM albums WHERE visible = 1 AND artistId IS NOT NULL) OR id IN (SELECT artistId FROM songs WHERE artistId IS NOT NULL AND (albumId IS NULL OR albumId IN (SELECT id FROM albums WHERE visible = 1))) OR id IN (SELECT id FROM sources))"

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
    /**
     * Every artist credited on the song (Navidrome's OpenSubsonic "artists", Emby's ArtistItems) as JSON, so a
     * featured or compilation artist is found and their page knows the song (beta testers #34).
     */
    @androidx.room.ColumnInfo(defaultValue = "") val credits: String = "",
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
    /** Which Navidrome library it's in, when the server has several (issue #376). */
    val folderId: String? = null,
    /** Whether its library is chosen to show; every library is kept, so switching one is instant (issue #376). */
    @androidx.room.ColumnInfo(defaultValue = "1") val visible: Boolean = true,
    /** Its record labels, each between two [LABEL_MARK]s ("‹A›‹B›"), so one can be matched whole (#558). */
    @androidx.room.ColumnInfo(defaultValue = "''") val labels: String = "",
)

/** What brackets each label in [AlbumEntity.labels]; a character no label has. */
const val LABEL_MARK = "\u001F"

@Entity(tableName = "artists", indices = [Index("sortKey")])
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val albumCount: Int,
    val coverArt: String?,
    val imageUrl: String?,
    val sortKey: String,
    /** MusicBrainz ID, when Navidrome has one (issue #359). */
    val mbid: String? = null,
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

/**
 * Songs, albums, artists, playlists and covers that come from another user shown alongside the one in use
 * (issue #141), with that user's id. Everything not listed here is the user in use's own.
 */
@Entity(tableName = "sources", indices = [Index("source")])
data class SourceEntity(@PrimaryKey val id: String, val source: Int)

@Entity(tableName = "meta")
data class MetaEntity(@PrimaryKey val key: String, val value: String)

data class AlbumSignature(val id: String, val signature: String)
data class AlbumCoverRow(val id: String, val coverArt: String?)
data class AlbumGroupRow(val id: String, val groupKey: String, val songCount: Int, val visible: Boolean = true)
data class AlbumTypeRow(val releaseTypes: String, val isCompilation: Boolean)
data class GenreRow(val name: String, val songCount: Int)
/** Just what matching songs by title and artist needs (issue #186's time machine). */
data class SongTitleRow(val id: String, val title: String, val artist: String)

@Dao
interface LibraryDao {
    // Songs
    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun song(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:ids)" + VISIBLE_SONG)
    suspend fun songs(ids: List<String>): List<SongEntity>

    @Query("SELECT id, title, artist FROM songs WHERE 1 = 1" + VISIBLE_SONG)
    suspend fun songTitles(): List<SongTitleRow>

    /** Every song, for Your Library's Songs (issue #497). */
    @Query("SELECT * FROM songs WHERE 1 = 1" + VISIBLE_SONG)
    suspend fun allSongs(): List<SongEntity>

    /** The album cover id for a song's cover id (a song without its own picture shows its album's). */
    @Query("SELECT a.coverArt FROM songs s JOIN albums a ON a.id = s.albumId WHERE s.coverArt = :songCover LIMIT 1")
    suspend fun albumCoverOfSongCover(songCover: String): String?

    /** Every cover id Navidrome gives songs (`dc-…` for a disc, `mf-…` for a song with its own picture). */
    @Query("SELECT DISTINCT coverArt FROM songs WHERE coverArt IS NOT NULL")
    suspend fun songCoverIds(): List<String>

    @Query("SELECT * FROM songs WHERE albumId IN (:albumIds)")
    suspend fun songsOfAlbums(albumIds: List<String>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE (artistId = :artistId OR (artistId IS NULL AND artist = :name COLLATE NOCASE))" + VISIBLE_SONG)
    suspend fun songsOfArtist(artistId: String, name: String): List<SongEntity>

    /** Songs whose artist is [artistId] or whose artist text contains [namePattern] (a LIKE pattern, escaped with '\\'). */
    @Query(
        "SELECT * FROM songs WHERE (artistId = :artistId OR artist LIKE :namePattern ESCAPE '\\' OR " +
            "credits LIKE '%\"' || :artistId || '\"%')" + VISIBLE_SONG,
    )
    suspend fun songsCrediting(artistId: String, namePattern: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE genre = :genre COLLATE NOCASE" + VISIBLE_SONG + " ORDER BY RANDOM() LIMIT :limit")
    suspend fun songsOfGenre(genre: String, limit: Int): List<SongEntity>

    /** Random songs of the libraries and users shown (beta report 37b60d15). */
    @Query("SELECT * FROM songs WHERE 1 = 1" + VISIBLE_SONG + " ORDER BY RANDOM() LIMIT :limit")
    suspend fun randomSongs(limit: Int): List<SongEntity>

    @Query("SELECT * FROM songs WHERE searchKey LIKE '%' || :term || '%'" + VISIBLE_SONG + " LIMIT :limit")
    suspend fun searchSongs(term: String, limit: Int): List<SongEntity>

    @Query("SELECT * FROM songs WHERE starred = 1" + VISIBLE_SONG)
    suspend fun starredSongs(): List<SongEntity>

    @Query("UPDATE songs SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean)

    @Query("UPDATE songs SET starred = 0 WHERE starred = 1")
    suspend fun clearStarred()

    @Query("UPDATE songs SET starred = 1 WHERE id IN (:ids)")
    suspend fun markStarred(ids: List<String>)

    @Query(
        "SELECT genre AS name, COUNT(*) AS songCount FROM songs WHERE genre IS NOT NULL AND genre != ''" + VISIBLE_SONG + " " +
            "GROUP BY genre COLLATE NOCASE ORDER BY songCount DESC LIMIT :limit"
    )
    suspend fun genres(limit: Int): List<GenreRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSongs(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE albumId IN (:albumIds)")
    suspend fun deleteSongsOfAlbums(albumIds: List<String>)

    /** Every own song (other users' songs stay). */
    @Query("DELETE FROM songs WHERE id NOT IN (SELECT id FROM sources)")
    suspend fun deleteAllSongs()

    @Query("SELECT COUNT(*) FROM songs WHERE 1 = 1" + VISIBLE_SONG)
    suspend fun songCount(): Int

    // Albums
    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun album(id: String): AlbumEntity?

    @Query("SELECT id FROM albums WHERE id IN (:ids)")
    suspend fun knownAlbumIds(ids: List<String>): List<String>

    @Query("SELECT id FROM albums WHERE visible = 1 AND id IN (:ids)")
    suspend fun shownAlbumIds(ids: List<String>): List<String>

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

    /** Albums tagged with [genre] (#558): the album's own genre, or any of its songs'; newest first. */
    @Query(
        "SELECT * FROM albums WHERE isPrimary = 1 AND (genre = :genre COLLATE NOCASE OR id IN " +
            "(SELECT albumId FROM songs WHERE genre = :genre COLLATE NOCASE AND albumId IS NOT NULL)) ORDER BY year DESC, sortKey"
    )
    suspend fun albumsWithGenre(genre: String): List<AlbumEntity>

    /** Albums on the record label [marked] (the label between two [LABEL_MARK]s) (#558); newest first. */
    @Query("SELECT * FROM albums WHERE isPrimary = 1 AND labels LIKE '%' || :marked || '%' ESCAPE '\\' ORDER BY year DESC, sortKey")
    suspend fun albumsWithLabel(marked: String): List<AlbumEntity>

    @Query("SELECT releaseTypes, isCompilation FROM albums WHERE isPrimary = 1")
    suspend fun albumTypes(): List<AlbumTypeRow>

    /** Every album's cover id, for caching song and disc covers per album cover version (issue #639). */
    @Query("SELECT id, coverArt FROM albums")
    suspend fun albumCovers(): List<AlbumCoverRow>

    /** Own albums only: other users' albums are kept up to date on their own. */
    @Query("SELECT id, signature FROM albums WHERE id NOT IN (SELECT id FROM sources)")
    suspend fun albumSignatures(): List<AlbumSignature>

    @Query("SELECT id, groupKey, songCount, visible FROM albums")
    suspend fun albumGroups(): List<AlbumGroupRow>

    /** Every library shows (issue #376). */
    @Query("UPDATE albums SET visible = 1")
    suspend fun showAllFolders()

    /** The user in use's own albums (everything that isn't another user's) are hidden (issue #427). */
    @Query("UPDATE albums SET visible = 0 WHERE id NOT IN (SELECT id FROM sources)")
    suspend fun hideOwnAlbums()

    /** Only albums in [folderIds] show, and albums without a library (another user's music). */
    @Query("UPDATE albums SET visible = CASE WHEN folderId IS NULL OR folderId IN (:folderIds) THEN 1 ELSE 0 END")
    suspend fun showFolders(folderIds: List<String>)

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
    @Query("SELECT * FROM artists WHERE " + VISIBLE_ARTIST + " ORDER BY sortKey")
    suspend fun artists(): List<ArtistEntity>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun artist(id: String): ArtistEntity?

    /** An artist's picture id, as the server gives it now (beta report 95a30366). */
    @Query("UPDATE artists SET coverArt = :coverArt WHERE id = :id")
    suspend fun setArtistCover(id: String, coverArt: String)

    @Query("SELECT * FROM artists WHERE sortKey = :key LIMIT 1")
    suspend fun artistByKey(key: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE sortKey LIKE '%' || :term || '%' AND " + VISIBLE_ARTIST + " ORDER BY sortKey LIMIT :limit")
    suspend fun searchArtists(term: String, limit: Int): List<ArtistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putArtists(artists: List<ArtistEntity>)

    @Query("DELETE FROM artists WHERE id NOT IN (SELECT id FROM sources)")
    suspend fun deleteAllArtists()

    @Query("SELECT COUNT(*) FROM artists WHERE " + VISIBLE_ARTIST)
    suspend fun artistCount(): Int

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY position")
    suspend fun playlists(): List<PlaylistEntity>

    @Query("DELETE FROM playlists WHERE id NOT IN (SELECT id FROM sources)")
    suspend fun deleteAllPlaylists()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPlaylists(playlists: List<PlaylistEntity>)

    // Other users' items
    /** The other user an item came from, or null when it's the user in use's own (issue #356). */
    @Query("SELECT source FROM sources WHERE id = :id LIMIT 1")
    suspend fun sourceOf(id: String): Int?

    @Query("SELECT * FROM sources")
    suspend fun sources(): List<SourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSources(sources: List<SourceEntity>)

    /** Ids that turned out to be the user in use's own (they gained access to them). */
    @Query("DELETE FROM sources WHERE id IN (:ids)")
    suspend fun forgetSources(ids: List<String>)

    @Query("DELETE FROM songs WHERE id IN (SELECT id FROM sources WHERE source = :user)")
    suspend fun deleteSongsOfSource(user: Int)

    @Query("DELETE FROM albums WHERE id IN (SELECT id FROM sources WHERE source = :user)")
    suspend fun deleteAlbumsOfSource(user: Int)

    @Query("DELETE FROM artists WHERE id IN (SELECT id FROM sources WHERE source = :user)")
    suspend fun deleteArtistsOfSource(user: Int)

    @Query("DELETE FROM playlists WHERE id IN (SELECT id FROM sources WHERE source = :user)")
    suspend fun deletePlaylistsOfSource(user: Int)

    @Query("DELETE FROM sources WHERE source = :user")
    suspend fun deleteSource(user: Int)

    @Query("SELECT DISTINCT source FROM sources")
    suspend fun sourceUsers(): List<Int>

    @Query("SELECT id FROM songs WHERE id IN (:ids)")
    suspend fun knownSongIds(ids: List<String>): List<String>

    @Query("SELECT id FROM artists WHERE id IN (:ids)")
    suspend fun knownArtistIds(ids: List<String>): List<String>

    @Query("SELECT id FROM playlists WHERE id IN (:ids)")
    suspend fun knownPlaylistIds(ids: List<String>): List<String>

    /** Every album id sharing a name and album artist with one of [ids] (its copies in other libraries). */
    @Query("SELECT id FROM albums WHERE groupKey IN (SELECT groupKey FROM albums WHERE id IN (:ids))")
    suspend fun albumGroupIds(ids: List<String>): List<String>

    @Query("SELECT * FROM songs WHERE id IN (:ids) AND genre IS NOT NULL AND genre != ''")
    suspend fun songsWithGenre(ids: List<String>): List<SongEntity>

    @Query("SELECT * FROM albums WHERE isPrimary = 1")
    suspend fun primaryAlbums(): List<AlbumEntity>

    // Meta
    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun meta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(meta: MetaEntity)
}

@Database(
    entities = [SongEntity::class, AlbumEntity::class, ArtistEntity::class, PlaylistEntity::class, MetaEntity::class, SourceEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        /** Version 2 adds the list of other users' items, keeping the synced library. */
        val ADD_SOURCES = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `sources` (`id` TEXT NOT NULL, `source` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sources_source` ON `sources` (`source`)")
            }
        }

        /** Version 3 keeps each artist's MusicBrainz ID (issue #359); it fills in on the next sync. */
        val ADD_ARTIST_MBID = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `artists` ADD COLUMN `mbid` TEXT")
            }
        }

        /**
         * Version 4 keeps every Navidrome library and which one each album is in, showing only the chosen ones
         * (issue #376); the next sync fetches the libraries not on the phone yet.
         */
        val ADD_ALBUM_FOLDERS = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `albums` ADD COLUMN `folderId` TEXT")
                db.execSQL("ALTER TABLE `albums` ADD COLUMN `visible` INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * Version 5 keeps every artist credited on a song (beta testers #34). Forgetting the albums' signatures makes
         * the next sync fetch every song again, so the credits fill in without starting over.
         */
        val ADD_SONG_CREDITS = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `songs` ADD COLUMN `credits` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `albums` SET `signature` = ''")
            }
        }

        /**
         * Version 6 keeps each album's record labels (#558). Forgetting the last scan makes the next sync read the album
         * list again (which carries the labels) without fetching any songs.
         */
        val ADD_ALBUM_LABELS = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `albums` ADD COLUMN `labels` TEXT NOT NULL DEFAULT ''")
                db.execSQL("DELETE FROM `meta` WHERE `key` = 'lastScan'")
            }
        }

        fun open(context: Context): LibraryDatabase =
            Room.databaseBuilder(context, LibraryDatabase::class.java, app.siansa.data.Users.scoped(app.siansa.data.UserFiles.DATABASE))
                .addMigrations(ADD_SOURCES, ADD_ARTIST_MBID, ADD_ALBUM_FOLDERS, ADD_SONG_CREDITS, ADD_ALBUM_LABELS)
                // The library can always be synced again, so any other schema change simply starts fresh.
                .fallbackToDestructiveMigration()
                .build()
    }
}
