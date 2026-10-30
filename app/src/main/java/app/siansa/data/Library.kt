package app.siansa.data

import android.content.Context
import androidx.room.withTransaction
import app.siansa.data.db.AlbumEntity
import app.siansa.data.db.LABEL_MARK
import app.siansa.data.db.SongTitleRow
import app.siansa.data.db.ArtistEntity
import app.siansa.data.db.LibraryDatabase
import app.siansa.data.db.MetaEntity
import app.siansa.data.db.PlaylistEntity
import app.siansa.data.db.SongEntity
import app.siansa.data.db.SourceEntity
import app.siansa.offline.OfflineShelf
import app.siansa.offline.OnlyDownloaded
import app.siansa.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import java.util.Locale

private val MARKS = Regex("\\p{M}+")
private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

/**
 * Whether every one of [terms] (from [normalizeKey]) is in [key], as it is or with its spaces left out: a name's
 * punctuation turns into spaces ("I'm" into "i m"), so "im" or "dont" typed without the apostrophe still match
 * (issue #661).
 */
fun searchMatches(key: String, terms: List<String>): Boolean {
    val compact by lazy(LazyThreadSafetyMode.NONE) { key.replace(" ", "") }
    return terms.all { key.contains(it) || compact.contains(it) }
}

fun normalizeKey(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(NON_WORD, " ")
        .trim()

data class Genre(val name: String, val songCount: Int)

/** Copies of an album are merged in lists when they share this: name, album artist and library (issue #376). */
fun albumGroupKey(name: String, artist: String, folderId: String?): String =
    normalizeKey(name) + "|" + normalizeKey(artist) + (folderId?.let { "|$it" } ?: "")

/**
 * Labels for albums that are in more than one library (issue #376): each copy gets its library's name, so a FLAC
 * and a Dolby Atmos copy can be told apart on the artist page. Albums in one library get none.
 */
fun versionLabels(albums: List<Album>, folderNames: Map<String, String>): Map<String, String> =
    albums.groupBy { normalizeKey(it.name) + "|" + normalizeKey(it.artist) }.values
        .filter { copies -> copies.mapNotNull { it.folderId }.distinct().size > 1 }
        .flatten()
        .mapNotNull { album -> album.folderId?.let { folderNames[it] }?.let { album.id to it } }
        .toMap()

/** What the phone holds after a sync; null in [LibraryRepository.status] means never synced. */
data class LibraryStatus(val syncedAt: Long, val artists: Int, val albums: Int, val songs: Int)

private const val TYPE_SEPARATOR = "\u001F"
private const val CHUNK = 900

/**
 * The synced library in an on-phone SQLite database. Screens ask for exactly what they show, so
 * nothing has to be loaded at start-up. [version] changes after every sync, for screens to reload.
 */
class LibraryRepository(context: Context) {
    private val db = LibraryDatabase.open(context)
    private val dao = db.dao()

    private val _ownHidden = MutableStateFlow(false)
    /**
     * Whether the user in use's own music is hidden, so only other shown users' music is listed (issue #427).
     * Their settings, history and playlists stay in use; nothing restarts.
     */
    val ownHidden: StateFlow<Boolean> = _ownHidden.asStateFlow()

    private val _status = MutableStateFlow<LibraryStatus?>(null)
    val status: StateFlow<LibraryStatus?> = _status.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    init {
        // The JSON library file of earlier versions is replaced by the database.
        File(context.filesDir, "library.json").delete()
    }

    val isSynced: Boolean get() = _status.value != null

    /**
     * What's on the phone while "Only downloaded music" is on (issue #141), else null. Every list this
     * gives out is then narrowed to it; looking up one song, album or artist by id still works.
     */
    @Volatile var downloadedOnly: () -> OfflineShelf? = { null }
    @Volatile private var onlyCache: OnlyDownloaded? = null

    /** The filter for "Only downloaded music", or null when it's off. */
    suspend fun onlyDownloaded(): OnlyDownloaded? {
        val shelf = downloadedOnly() ?: return null
        onlyCache?.let { if (it.shelf === shelf) return it }
        val albums = shelf.albums.toList().chunked(CHUNK).flatMap { dao.albumGroupIds(it) }.toSet() + shelf.albums
        return OnlyDownloaded(shelf, albums).also { onlyCache = it }
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        _ownHidden.value = meta(META_OWN_HIDDEN) == "1"
        refreshStatus()
        _loaded.value = true
        bump()
    }

    suspend fun refreshStatus() {
        val syncedAt = dao.meta(META_SYNCED_AT)?.toLongOrNull()
        _status.value = syncedAt?.let { LibraryStatus(it, dao.artistCount(), dao.albumCount(), dao.songCount()) }
    }

    fun bump() {
        _version.value++
    }

    // ---- Reading ----

    suspend fun song(id: String): Song? = dao.song(id)?.toSong()

    suspend fun albumCoverOfSongCover(songCover: String): String? = dao.albumCoverOfSongCover(songCover)

    /** The cover ids songs show, exactly as Navidrome sends them (its cover settings decide which picture). */
    suspend fun songCoverIds(): List<String> = withContext(Dispatchers.IO) { dao.songCoverIds() }

    /** Songs by id, in the order asked for; ids the library doesn't have are left out. */
    /** Every song's id, title and artist, for matching many songs from other services at once. */
    suspend fun songTitles(): List<SongTitleRow> = withContext(Dispatchers.IO) { dao.songTitles() }

    /** Every song in the libraries you sync, only downloaded ones while that's on (Your Library's Songs, issue #497). */
    suspend fun allSongs(): List<Song> {
        val all = withContext(Dispatchers.IO) { dao.allSongs().map { it.toSong() } }
        return onlyDownloaded()?.songs(all) ?: all
    }

    suspend fun songs(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val found = ids.distinct().chunked(CHUNK).flatMap { dao.songs(it) }.associateBy { it.id }
        return ids.mapNotNull { found[it]?.toSong() }
    }

    /**
     * True when some Navidrome libraries are unchecked in Settings → Library sync. Then songs and albums
     * Navidrome sends that the phone doesn't have are from a left-out library and must not show or play.
     */
    suspend fun leavesOutLibraries(): Boolean = !app.siansa.mix.mayFetchUnsynced(meta(META_FOLDERS))

    /**
     * [songs] from Navidrome (popular songs, radios, liked songs…) without those from unchecked libraries,
     * using the phone's copy of each. Unchanged when every library is synced.
     */
    suspend fun keepSyncedSongs(songs: List<Song>, why: String): List<Song> {
        onlyDownloaded()?.let { return it.songs(songs) }
        if (songs.isEmpty() || !hidesSome()) return songs
        val kept = keepKnown(songs, songs(songs.map { it.id }).associateBy { it.id })
        if (kept.size < songs.size) AppLog.i("Library", "$why: left out ${songs.size - kept.size} of ${songs.size} songs from unchecked libraries")
        return kept
    }

    /** [albums] from Navidrome without those from unchecked libraries. Unchanged when every library is synced. */
    suspend fun keepSyncedAlbums(albums: List<Album>, why: String): List<Album> {
        onlyDownloaded()?.let { return it.albums(albums) }
        if (albums.isEmpty() || !leavesOutLibraries()) return albums
        val known = albums.map { it.id }.distinct().chunked(CHUNK).flatMap { dao.knownAlbumIds(it) }.toSet()
        val kept = albums.filter { it.id in known }
        if (kept.size < albums.size) AppLog.i("Library", "$why: left out ${albums.size - kept.size} of ${albums.size} albums from unchecked libraries")
        return kept
    }

    /** Which of [ids] are songs in the library (any user's, any library). */
    suspend fun knownSongIds(ids: List<String>): Set<String> = ids.distinct().chunked(500).flatMap { dao.knownSongIds(it) }.toSet()

    /** Which of [ids] are albums shown now (not in a hidden library, not a hidden user's); null before the first sync. */
    suspend fun shownAlbums(ids: Collection<String>): Set<String>? {
        if (!isSynced) return null
        return ids.distinct().chunked(500).flatMap { dao.shownAlbumIds(it) }.toSet()
    }

    /** The album for any of its ids, with every id of the same album in other libraries. */
    suspend fun album(id: String): Album? {
        val entity = dao.album(id) ?: return null
        val group = dao.albumGroup(entity.groupKey)
        val primary = group.firstOrNull { it.isPrimary } ?: entity
        return primary.toAlbum().copy(ids = group.map { it.id })
    }

    /** Songs of an album and its copies in other libraries, without duplicates, in disc and track order. */
    suspend fun songsOf(album: Album): List<Song> {
        val ids = album.allIds
        val all = ids.chunked(CHUNK).flatMap { dao.songsOfAlbums(it) }
        val order = ids.withIndex().associate { it.value to it.index }
        return all
            .sortedBy { order[it.albumId] ?: Int.MAX_VALUE }
            .distinctBy { listOf(it.disc ?: 1, it.track?.toString() ?: "d${it.durationSec / 3}", normalizeKey(it.title)) }
            .sortedWith(compareBy({ it.disc ?: 1 }, { it.track ?: Int.MAX_VALUE }, { it.title }))
            .map { it.toSong() }
            .let { songs -> onlyDownloaded()?.songs(songs) ?: songs }
    }

    suspend fun albums(sort: String): List<Album> = when (sort) {
        "newest" -> dao.albumsByNewest()
        "frequent" -> dao.albumsByPlays()
        "year" -> dao.albumsByYear()
        "random" -> dao.albumsRandom(100_000)
        else -> dao.albumsByName()
    }.map { it.toAlbum() }.onlyDownloadedAlbums()

    /** Every album tagged with [genre] (#558), newest first; what's shown only, as the other album lists. */
    suspend fun albumsWithGenre(genre: String): List<Album> = dao.albumsWithGenre(genre.trim()).map { it.toAlbum() }.onlyDownloadedAlbums()

    /** Every album on the record label [label] (#558), newest first. Empty until a sync has read the labels. */
    suspend fun albumsWithLabel(label: String): List<Album> =
        dao.albumsWithLabel(likeLiteral(LABEL_MARK + label.trim() + LABEL_MARK)).map { it.toAlbum() }.onlyDownloadedAlbums()

    suspend fun randomAlbums(count: Int): List<Album> {
        val only = onlyDownloaded() ?: return dao.albumsRandom(count).map { it.toAlbum() }
        return only.albums(dao.albumsRandom(100_000).map { it.toAlbum() }).take(count)
    }

    private suspend fun List<Album>.onlyDownloadedAlbums(): List<Album> = onlyDownloaded()?.albums(this) ?: this

    /** Release categories present, in display order, with album counts. */
    suspend fun categories(): List<Pair<AlbumCategory, Int>> = (onlyDownloaded()?.let { only -> only.albums(dao.primaryAlbums().map { it.toAlbum() }) }
        ?: dao.albumTypes().map { Album(id = "", name = "", releaseTypes = splitTypes(it.releaseTypes), isCompilation = it.isCompilation) })
        .map { it.category }
        .groupingBy { it }.eachCount()
        .toList()
        .sortedBy { it.first.ordinal }

    /** Every artist, without names that are several library artists in one (issue #359). */
    suspend fun artists(): List<Artist> {
        val all = dao.artists().map { it.toArtist() }
        val list = CombinedArtists.withoutCombined(all, combinedArtists(all))
        return onlyDownloaded()?.artists(list) ?: list
    }
    suspend fun artist(id: String): Artist? = dao.artist(id)?.toArtist()
    suspend fun artistByName(name: String): Artist? = dao.artistByKey(normalizeKey(name))?.toArtist()

    @Volatile private var combined: Map<String, CombinedArtist>? = null

    /**
     * Artists Navidrome made from several artists tagged in one name ("Rudimental, Orginal Koffee & Winny"),
     * by id, with the library artists they name (issue #359). Worked out once per sync.
     */
    suspend fun combinedArtists(all: List<Artist>? = null): Map<String, CombinedArtist> {
        combined?.let { return it }
        val found = CombinedArtists.find(all ?: dao.artists().map { it.toArtist() })
        if (found.isNotEmpty()) {
            AppLog.i(
                "Library",
                "${found.size} artists are several artists in one name; shown as their own artists: " +
                    found.values.take(5).joinToString("; ") { c -> "${c.combined.name} → ${(c.parts.map { it.name } + c.unknown).joinToString(" | ")}" },
            )
        }
        combined = found
        return found
    }

    /** The artist's albums, with those of names it's tagged in together with others (issue #359). */
    suspend fun albumsOf(artist: Artist): List<Album> {
        val also = CombinedArtists.containing(artist, combinedArtists())
        return (listOf(artist) + also)
            .flatMap { a -> dao.albumsOfArtist(a.id, normalizeKey(a.name)) }
            .distinctBy { it.id }
            .map { it.toAlbum() }
            .onlyDownloadedAlbums()
    }

    /** The artist's songs, with those of names it's tagged in together with others (issue #359). */
    suspend fun songsOf(artist: Artist): List<Song> {
        val also = CombinedArtists.containing(artist, combinedArtists())
        return (listOf(artist) + also)
            .flatMap { a -> dao.songsOfArtist(a.id, a.name) }
            .map { it.toSong() }
            .distinctBy { songKey(it) }
            .onlyDownloadedSongs()
    }

    private suspend fun List<Song>.onlyDownloadedSongs(): List<Song> = onlyDownloaded()?.songs(this) ?: this

    /**
     * Albums the artist appears on without being the album artist (a feature, a compilation, a split),
     * newest first. Only from the synced library; empty before a sync.
     */
    suspend fun appearsOn(artist: Artist): List<Album> {
        val ownKey = normalizeKey(artist.name)
        val own = albumsOf(artist).map { it.id }.toSet()
        val songs = dao.songsCrediting(artist.id, "%" + likeEscape(artist.name) + "%").map { it.toSong() }
        val ids = appearsOnAlbumIds(songs, artist.id, artist.name) { it in own }
        return ids.mapNotNull { album(it) }
            .distinctBy { it.id }
            .filterNot { it.id in own || it.artistId == artist.id || normalizeKey(it.artist) == ownKey }
            .sortedWith(compareByDescending<Album> { it.year ?: 0 }.thenBy { normalizeKey(it.name) })
            .onlyDownloadedAlbums()
            .also { AppLog.d("Library", "Appears on: ${it.size} albums from ${songs.size} songs checked") }
    }

    suspend fun genres(limit: Int = 60): List<Genre> {
        val only = onlyDownloaded() ?: return dao.genres(limit).map { Genre(it.name, it.songCount) }
        val songs = only.shelf.songs.toList().chunked(CHUNK).flatMap { dao.songsWithGenre(it) }.map { it.toSong() }
        return OnlyDownloaded.genres(songs, limit)
    }

    /** Random songs of what's shown: chosen libraries, shown users, and only downloads when that's on. */
    suspend fun randomSongs(limit: Int): List<Song> {
        val only = onlyDownloaded()
        return dao.randomSongs(if (only != null) 100_000 else limit).map { it.toSong() }
            .let { list -> only?.songs(list)?.take(limit) ?: list }
    }

    /**
     * Whether some of the music on the phone is hidden (beta report 37b60d15): libraries not chosen, or the user in
     * use's own music. Then lists straight from the server must be checked against what's shown.
     */
    suspend fun hidesSome(): Boolean = _ownHidden.value || leavesOutLibraries()

    suspend fun songsOfGenre(genre: String, limit: Int): List<Song> {
        val only = onlyDownloaded()
        return dao.songsOfGenre(genre, if (only != null) 100_000 else limit).map { it.toSong() }.distinctBy { songKey(it) }
            .let { list -> only?.songs(list)?.take(limit) ?: list }
    }

    suspend fun starredSongs(): List<Song> = dao.starredSongs().map { it.toSong() }.distinctBy { songKey(it) }.onlyDownloadedSongs()

    suspend fun setStarred(id: String, starred: Boolean) = dao.setStarred(id, starred)

    suspend fun playlists(): List<Playlist> = dao.playlists().map {
        Playlist(it.id, it.name, it.songCount, it.durationSec, it.coverArt, it.owner, it.comment, it.changed)
    }.let { list ->
        // The other users' ids are only needed (and read) while the user in use's own music is hidden.
        val hidden = _ownHidden.value
        shownPlaylists(list, hidden, if (hidden) dao.sources().mapTo(HashSet()) { it.id } else emptySet())
    }
        .let { list -> onlyDownloaded()?.playlists(list) ?: list }

    /**
     * Playlists fresh from Navidrome ([fetch], the user in use's) stored and returned together with other
     * shown users' playlists. When Navidrome can't be reached, the stored ones; before a first sync, the error.
     */
    suspend fun refreshPlaylists(fetch: suspend () -> List<Playlist>): List<Playlist> {
        runCatching { fetch() }
            .onSuccess { updatePlaylists(it) }
            .onFailure { if (!isSynced) throw it }
        return playlists()
    }

    suspend fun updatePlaylists(playlists: List<Playlist>) {
        db.withTransaction {
            dao.deleteAllPlaylists()
            playlists.chunked(CHUNK).forEach { chunk -> dao.forgetSources(chunk.map { it.id }) }
            dao.putPlaylists(playlists.mapIndexed { i, p ->
                PlaylistEntity(p.id, p.name, p.songCount, p.durationSec, p.coverArt, p.owner, p.comment, p.changed, i)
            })
        }
    }

    /**
     * Every word must appear in the name (accents and case ignored), with or without the spaces punctuation leaves:
     * "Im looking" finds "I'm Looking Through You" (issue #661), see [searchMatches].
     */
    suspend fun search(query: String): SearchResults = withContext(Dispatchers.IO) {
        val terms = normalizeKey(query).split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return@withContext SearchResults(emptyList(), emptyList(), emptyList())
        val first = terms.maxBy { it.length }
        fun all(key: String) = searchMatches(key, terms)
        val only = onlyDownloaded()
        val albumArtists = dao.searchArtists(first, 200).filter { all(it.sortKey) }.map { it.toArtist() }
            .let { CombinedArtists.withoutCombined(it, combinedArtists()) }
        // Artists only credited on songs (featured, or on a compilation) aren't album artists (beta testers #34).
        val found = dao.searchSongs(first, 3000).filter { all(it.searchKey) }.map { it.toSong() }
        val credited = Credits.matching(
            found,
            terms,
            albumArtists.map { it.id }.toSet(),
        ).map { (ref, _) -> Artist(ref.id, ref.name) }
        val artists = rankByName(query, albumArtists + credited) { it.name }
        val albums = dao.searchAlbums(first, 400).filter { all(it.sortKey + " " + it.artistKey) }.map { it.toAlbum() }
        val songs = found.distinctBy { songKey(it) }
        SearchResults(
            artists = (only?.artists(artists) ?: artists).take(30),
            albums = (only?.albums(albums) ?: albums).take(40),
            songs = (only?.songs(songs) ?: songs).take(100),
        )
    }

    // ---- Writing (used by sync) ----

    /** Album id → its cover id, for [app.siansa.covers.CoverVersions] (issue #639). */
    suspend fun albumCovers(): Map<String, String?> = withContext(Dispatchers.IO) { dao.albumCovers().associate { it.id to it.coverArt } }

    suspend fun albumSignatures(): Map<String, String> = dao.albumSignatures().associate { it.id to it.signature }
    suspend fun meta(key: String): String? = dao.meta(key)
    suspend fun putMeta(key: String, value: String) = dao.putMeta(MetaEntity(key, value))

    /** Keeps [id]'s new picture id, so lists ask for the photo the server has now (beta report 95a30366). */
    suspend fun setArtistCover(id: String, coverArt: String) = dao.setArtistCover(id, coverArt)

    suspend fun replaceArtists(artists: List<Artist>) = db.withTransaction {
        combined = null
        dao.deleteAllArtists()
        artists.distinctBy { it.id }.chunked(CHUNK).forEach { chunk ->
            dao.forgetSources(chunk.map { it.id })
            dao.putArtists(chunk.map { it.toEntity() })
        }
    }

    /** Stores albums and their songs; [replaceSongsOf] are albums whose songs are swapped for [songs]. */
    suspend fun putAlbumsAndSongs(albums: List<Album>, songs: List<Song>, replaceSongsOf: Collection<String>, removedAlbums: Collection<String>) =
        db.withTransaction {
            replaceSongsOf.toList().chunked(CHUNK).forEach { dao.deleteSongsOfAlbums(it) }
            removedAlbums.toList().chunked(CHUNK).forEach {
                dao.deleteSongsOfAlbums(it)
                dao.deleteAlbums(it)
            }
            albums.chunked(500).forEach { chunk ->
                dao.forgetSources(chunk.map { it.id })
                dao.putAlbums(chunk.map { it.toEntity() })
            }
            songs.chunked(500).forEach { chunk ->
                dao.forgetSources(chunk.map { it.id })
                dao.putSongs(chunk.map { it.toEntity() })
            }
        }

    suspend fun replaceAllSongs(songs: List<Song>) = db.withTransaction {
        dao.deleteAllSongs()
        songs.chunked(500).forEach { chunk ->
            dao.forgetSources(chunk.map { it.id })
            dao.putSongs(chunk.map { it.toEntity() })
        }
    }

    // ---- Other users' music (issue #141) ----

    /** Which items come from which other user, for sending their requests with that user's login. */
    suspend fun sources(): Map<String, Int> = dao.sources().associate { it.id to it.source }

    /** Which user's library a song or album is from: another user's when it came with their music, else yours. */
    suspend fun userOf(id: String): Int = dao.sourceOf(id) ?: Users.active

    /** Users whose music is in the library besides the user in use's. */
    suspend fun sourceUsers(): List<Int> = dao.sourceUsers()

    /** Removes everything that came from [user]. */
    suspend fun removeSource(user: Int) {
        combined = null
        db.withTransaction {
            dao.deleteSongsOfSource(user)
            dao.deleteAlbumsOfSource(user)
            dao.deleteArtistsOfSource(user)
            dao.deletePlaylistsOfSource(user)
            dao.deleteSource(user)
        }
        recomputePrimaries()
    }

    /**
     * Replaces [user]'s music with [music]. Items the library already has from someone else (the same
     * server, so the same song) are left as they are; the rest are stored and listed as [user]'s.
     * Returns how many songs were added.
     */
    suspend fun putSource(user: Int, music: GuestMusic): Int {
        combined = null
        val added = db.withTransaction {
            dao.deleteSongsOfSource(user)
            dao.deleteAlbumsOfSource(user)
            dao.deleteArtistsOfSource(user)
            dao.deletePlaylistsOfSource(user)
            dao.deleteSource(user)
            suspend fun known(ids: List<String>, query: suspend (List<String>) -> List<String>) =
                ids.distinct().chunked(CHUNK).flatMap { query(it) }.toSet()
            val plan = GuestMusic.plan(
                music,
                knownSongs = known(music.songs.map { it.id }) { dao.knownSongIds(it) },
                // With the songs' own artists and albums (GuestMusic.Plan.links), which may be the user in use's.
                knownAlbums = known(music.albums.map { it.id } + music.songs.mapNotNull { it.albumId }) { dao.knownAlbumIds(it) },
                knownArtists = known(music.artists.map { it.id } + music.songs.flatMap { GuestMusic.songLinks(it) }) { dao.knownArtistIds(it) },
                knownPlaylists = known(music.playlists.map { it.id }) { dao.knownPlaylistIds(it) },
            )
            plan.artists.chunked(CHUNK).forEach { chunk -> dao.putArtists(chunk.map { it.toEntity() }) }
            plan.albums.chunked(500).forEach { chunk -> dao.putAlbums(chunk.map { it.toEntity() }) }
            plan.songs.chunked(500).forEach { chunk -> dao.putSongs(chunk.map { it.toEntity() }) }
            // After the user in use's own playlists.
            dao.putPlaylists(plan.playlists.mapIndexed { i, p ->
                PlaylistEntity(p.id, p.name, p.songCount, p.durationSec, p.coverArt, p.owner, p.comment, p.changed, GUEST_POSITION + user * 10_000 + i)
            })
            plan.ids.chunked(500).forEach { chunk -> dao.putSources(chunk.map { SourceEntity(it, user) }) }
            plan.songs.size
        }
        recomputePrimaries()
        return added
    }

    /**
     * Picks one album per name + album artist + library (the copy with most songs) to stand for the group in
     * lists, among the albums whose library is shown (issue #376). Copies in different libraries stay apart.
     */
    suspend fun recomputePrimaries(): Int = db.withTransaction {
        val rows = dao.albumGroups().filter { it.visible }
        val primaries = rows.groupBy { it.groupKey }.values.map { group -> group.maxBy { it.songCount }.id }
        dao.clearPrimary()
        primaries.chunked(CHUNK).forEach { dao.markPrimary(it) }
        rows.size - primaries.size
    }

    /**
     * Shows the albums, songs and artists of the libraries in [chosen] (all when empty or every one) and hides the
     * rest, at once: every library is on the phone (issue #376). Returns how many copies were merged.
     */
    suspend fun showFolders(chosen: Set<String>): Int {
        val all = folderNames().keys
        val every = chosen.isEmpty() || all.isEmpty() || all.all { it in chosen }
        db.withTransaction {
            if (every) dao.showAllFolders() else dao.showFolders(chosen.toList())
            if (_ownHidden.value) dao.hideOwnAlbums()
        }
        val merged = recomputePrimaries()
        putMeta(META_FOLDERS, if (every) "*" else chosen.sorted().joinToString(","))
        combined = null
        refreshStatus()
        bump()
        AppLog.i("Library", if (every) "Showing every library" else "Showing ${chosen.size} of ${all.size} libraries")
        return merged
    }

    /**
     * Hides or shows the user in use's own music at once (issue #427), keeping the libraries chosen in [chosen].
     * Kept across starts; a library sync keeps it hidden.
     */
    suspend fun setOwnHidden(hidden: Boolean, chosen: Set<String>) {
        if (_ownHidden.value == hidden) return
        _ownHidden.value = hidden
        putMeta(META_OWN_HIDDEN, if (hidden) "1" else "0")
        AppLog.i("Library", if (hidden) "Hiding the music of the user in use" else "Showing the music of the user in use again")
        showFolders(chosen)
    }

    /** Whether every Navidrome library is on the phone, so switching one on needs no sync (issue #376). */
    suspend fun keepsEveryLibrary(): Boolean = meta(META_SCOPE) != null

    /** The server's libraries by id, as of the last sync (issue #376). */
    suspend fun folderNames(): Map<String, String> = meta(META_FOLDER_NAMES).orEmpty().lineSequence()
        .mapNotNull { line -> line.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
        .toMap()

    suspend fun putFolderNames(folders: List<MusicFolder>) =
        putMeta(META_FOLDER_NAMES, folders.joinToString("\n") { "${it.id}\t${it.name.replace('\n', ' ').replace('\t', ' ')}" })

    suspend fun setStarredIds(ids: Collection<String>) = db.withTransaction {
        dao.clearStarred()
        ids.toList().chunked(CHUNK).forEach { dao.markStarred(it) }
    }

    // ---- Mapping ----

    private fun songKey(s: Song) = normalizeKey(s.title) + "|" + normalizeKey(s.artist) + "|" + normalizeKey(s.album)

    private fun splitTypes(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(TYPE_SEPARATOR)

    private fun SongEntity.toSong() = Song(
        id, title, artist, artistId, album, albumId, coverArt, durationSec, track, disc, year, genre,
        trackGain, albumGain, trackPeak, albumPeak, starred, artists = Credits.decode(credits),
    )

    private fun Song.toEntity() = SongEntity(
        id, title, artist, artistId, album, albumId, coverArt, durationSec, track, disc, year, genre,
        trackGain, albumGain, trackPeak, albumPeak, starred,
        searchKey = normalizeKey("$title $artist $album ${artists.joinToString(" ") { it.name }}"),
        credits = Credits.encode(this),
    )

    private fun AlbumEntity.toAlbum() = Album(
        id = id, name = name, artist = artist, artistId = artistId, coverArt = coverArt, songCount = songCount,
        durationSec = durationSec, year = year, genre = genre, created = created, playCount = playCount,
        releaseTypes = splitTypes(releaseTypes), isCompilation = isCompilation, folderId = folderId,
        labels = unmarkLabels(labels),
    )

    private fun Album.toEntity() = AlbumEntity(
        id = id, name = name, artist = artist, artistId = artistId, coverArt = coverArt, songCount = songCount,
        durationSec = durationSec, year = year, genre = genre, created = created, playCount = playCount,
        releaseTypes = releaseTypes.joinToString(TYPE_SEPARATOR), isCompilation = isCompilation,
        sortKey = normalizeKey(name), artistKey = normalizeKey(artist),
        // The same album in two libraries (a FLAC and a Dolby Atmos copy) stays two albums (issue #376).
        groupKey = albumGroupKey(name, artist, folderId),
        signature = signature(),
        folderId = folderId,
        labels = markLabels(labels),
    )

    private fun ArtistEntity.toArtist() = Artist(id, name, albumCount, coverArt, imageUrl, mbid)
    private fun Artist.toEntity() = ArtistEntity(id, name, albumCount, coverArt, imageUrl, normalizeKey(name), mbid)

    companion object {
        const val META_SYNCED_AT = "syncedAt"
        const val META_LAST_SCAN = "lastScan"
        const val META_FOLDERS = "folders"
        const val META_OWN_HIDDEN = "own_hidden"
        /** Which libraries the phone keeps: every one, as "all:<ids>" (issue #376). */
        const val META_SCOPE = "scope"
        const val META_FOLDER_NAMES = "folderNames"
        private const val GUEST_POSITION = 1_000_000

        /** What decides whether an album's songs need fetching again. Play counts are left out on purpose. */
        fun Album.signature(): String =
            listOf(name, artist, songCount, durationSec, year, created, coverArt, releaseTypes.joinToString(","), isCompilation)
                .joinToString("|")
    }
}

/** Labels as the library keeps them: each between two [LABEL_MARK]s, so a LIKE matches a whole label (#558). */
internal fun markLabels(labels: List<String>): String =
    labels.map { it.trim() }.filter { it.isNotEmpty() && LABEL_MARK !in it }.distinct().joinToString("") { LABEL_MARK + it + LABEL_MARK }

internal fun unmarkLabels(stored: String): List<String> = stored.split(LABEL_MARK).filter { it.isNotBlank() }

/** [text] for a LIKE with ESCAPE '\\': its own % and _ match only themselves ("100% Records"). */
internal fun likeLiteral(text: String): String = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/** Songs from Navidrome that the phone has (by id), in their order, as the phone's copy; the rest left out. */
internal fun keepKnown(songs: List<Song>, local: Map<String, Song>): List<Song> = songs.mapNotNull { local[it.id] }

/**
 * Only shown users' playlists: with the user in use's own music hidden, their own playlists go too, and only
 * other shown users' ([othersIds], from the sources table) are left. Playlists on this phone are listed apart.
 */
internal fun shownPlaylists(playlists: List<Playlist>, ownHidden: Boolean, othersIds: Set<String>): List<Playlist> =
    if (ownHidden) playlists.filter { it.id in othersIds } else playlists
