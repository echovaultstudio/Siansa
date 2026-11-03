package app.siansa.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import app.siansa.data.LibraryRepository
import app.siansa.data.CacheJson
import app.siansa.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.File

/** What's kept about music on the phone (issue #352): the folders, every file as last read, and the switches. */
@Serializable
data class LocalData(
    val folders: List<LocalFolder> = emptyList(),
    val tracks: List<LocalTrack> = emptyList(),
    /** Hidden with the eye in Home's Sources: kept, but not in the library. */
    val hidden: Boolean = false,
    /** Look up MusicBrainz IDs for files whose tags have none. */
    val musicBrainz: Boolean = true,
)

/** Where a scan or the MusicBrainz lookup is, for Settings and the Sources panel. */
data class LocalStatus(
    val scanning: Boolean = false,
    val read: Int = 0,
    val total: Int = 0,
    val error: String? = null,
    val lookingUp: Boolean = false,
    val looked: Int = 0,
    val toLook: Int = 0,
    val lookupError: String? = null,
    /** Local songs that are also on the server (by MusicBrainz ID, or title, artist and length). */
    val matched: Int = 0,
)

/**
 * Music files on the phone (issue #352): folders picked in Settings › Servers and services › Files on this phone,
 * scanned into the library next to the server's music, played from the files themselves.
 *
 * The folders and what each file's tags say are kept in one file (`local_music.json`), the same for every user; a
 * scan reads only new and changed files. Their songs go into the library of the user in use as the phone's own
 * ([LocalIds.SOURCE]), so the server's sync never removes them, and the source filter can hide them.
 */
class LocalMusic(
    private val context: Context,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
    private val musicBrainz: MusicBrainzApi,
) {
    private val file = File(context.filesDir, "local_music.json")
    val coversDir = File(context.filesDir, "local_covers")
    private val scanner = LocalScanner(context, coversDir)
    private val lookup = MusicBrainzLookup(musicBrainz)
    private val writing = Mutex()

    private val _data = MutableStateFlow(load())
    val data: StateFlow<LocalData> = _data.asStateFlow()
    private val _status = MutableStateFlow(LocalStatus())
    val status: StateFlow<LocalStatus> = _status.asStateFlow()

    @Volatile private var byId: Map<String, LocalTrack> = _data.value.tracks.associateBy { it.id }
    @Volatile private var matches: Map<String, String> = emptyMap()
    private var scanJob: Job? = null
    private var lookupJob: Job? = null

    /** The file's address (content://…) for a local song id, or null. */
    fun uriOf(id: String): String? = byId[id]?.uri

    fun track(id: String): LocalTrack? = byId[id]

    /**
     * The id AudioMuse and the server know a song by: a local song's server copy, or null when it's only on the phone;
     * any other song's own id.
     */
    fun serverIdOf(id: String): String? = if (LocalIds.isLocal(id)) matches[id] else id

    /** The picture file of a local cover id. */
    fun coverFile(id: String): File? = LocalIds.coverKey(id)?.let { File(coversDir, "$it.jpg") }

    /** At app start: the files' songs into the library (when the library doesn't have them), then a quick scan. */
    fun start() {
        if (_data.value.folders.isEmpty()) return
        scope.launch {
            if (!_data.value.hidden) rebuild("start")
            scan(quiet = true)
        }
    }

    /** Keeps [tree] (a folder picked with Android's picker) and scans it. */
    fun addFolder(tree: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { AppLog.w("Local", "Android didn't keep the permission for the folder: ${it.message}") }
        val name = folderName(tree)
        if (_data.value.folders.any { it.uri == tree.toString() }) return
        AppLog.i("Local", "Folder added: $name")
        update { it.copy(folders = it.folders + LocalFolder(tree.toString(), name), hidden = false) }
        scan()
    }

    /** Forgets [folder] and its songs; nothing on the phone is deleted. */
    fun removeFolder(folder: LocalFolder) {
        AppLog.i("Local", "Folder removed: ${folder.name}")
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(folder.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        scanJob?.cancel()
        update { d -> d.copy(folders = d.folders - folder, tracks = d.tracks.filter { it.folder != folder.uri }) }
        scope.launch { rebuild("folder removed") }
    }

    /** Hides or shows the phone's music everywhere (the eye in Home's Sources). */
    fun setHidden(hidden: Boolean) {
        AppLog.i("Local", if (hidden) "Hiding music on this phone" else "Showing music on this phone")
        update { it.copy(hidden = hidden) }
        scope.launch { rebuild("shown or hidden") }
    }

    fun setMusicBrainz(on: Boolean) {
        AppLog.i("Local", "MusicBrainz lookups ${if (on) "on" else "off"}")
        update { it.copy(musicBrainz = on) }
        if (on) lookUpMissing() else lookupJob?.cancel()
    }

    /** Reads every folder again; only new and changed files are opened. [quiet] is the check at app start. */
    fun scan(quiet: Boolean = false) {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            try {
                scanNow(quiet)
            } catch (e: CancellationException) {
                _status.update { it.copy(scanning = false) }
                throw e
            } catch (e: Exception) {
                AppLog.e("Local", "Scan failed", e)
                _status.update { it.copy(scanning = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    private suspend fun scanNow(quiet: Boolean) {
        val folders = _data.value.folders
        if (folders.isEmpty()) return
        _status.update { it.copy(scanning = true, read = 0, total = 0, error = null) }
        val started = System.currentTimeMillis()
        val before = _data.value.tracks.associateBy { it.uri }
        val kept = ArrayList<LocalTrack>()
        var changed = 0
        var unreadable = 0
        for (folder in folders) {
            val tree = Uri.parse(folder.uri)
            val found = try {
                scanner.list(tree) { scanJob?.isCancelled == true }
            } catch (e: SecurityException) {
                AppLog.w("Local", "No permission for ${folder.name} any more; add it again", e)
                _status.update { it.copy(error = "Siansa can't open ${folder.name} any more. Remove it and add it again.") }
                kept += before.values.filter { it.folder == folder.uri }
                continue
            }
            _status.update { it.copy(total = it.total + found.size) }
            for (f in found) {
                val old = before[f.uri]
                val track = if (old != null && old.size == f.size && old.modified == f.modified && old.folder == folder.uri) {
                    old
                } else {
                    changed++
                    runCatching { scanner.read(f, folder.uri) { t -> File(coversDir, LocalLibrary.coverKey(t) + ".jpg") } }
                        .onFailure { unreadable++; AppLog.w("Local", "Couldn't read ${f.path}: ${it.message}") }
                        .getOrNull()
                        // What was read before survives a file that can't be read now.
                        ?.let { t -> if (old != null) t.copy(mbLooked = old.mbLooked && t.recordingMbid == old.recordingMbid, mbRecording = old.mbRecording, mbArtists = old.mbArtists) else t }
                }
                if (track != null) kept += track
                _status.update { it.copy(read = it.read + 1) }
            }
        }
        val removed = before.size - kept.count { it.uri in before }
        update { it.copy(tracks = kept.distinctBy { t -> t.uri }) }
        AppLog.i(
            "Local",
            "Scan done in ${System.currentTimeMillis() - started} ms: ${kept.size} songs in ${folders.size} folder(s), $changed new or changed, $removed gone, $unreadable unreadable",
            event = "local.scanned",
            fields = mapOf("songs" to kept.size, "changed" to changed, "removed" to removed, "quiet" to quiet),
        )
        _status.update { it.copy(scanning = false) }
        if (changed > 0 || removed > 0 || !quiet) rebuild("scan")
        lookUpMissing()
    }

    /**
     * Puts the files' songs into the library again, after a scan, a server sync (its artists and songs to match
     * may have changed), or the eye. Hidden, they're taken out.
     */
    suspend fun rebuild(why: String) = writing.withLock {
        val d = _data.value
        byId = d.tracks.associateBy { it.id }
        if (d.hidden || d.tracks.isEmpty()) {
            library.removeSource(LocalIds.SOURCE)
            matches = emptyMap()
            _status.update { it.copy(matched = 0) }
        } else {
            val music = LocalLibrary.build(d.tracks, library.serverArtists())
            library.putSource(LocalIds.SOURCE, music)
            val found = LocalMatch.match(music.songs, library.serverSongs())
            matches = found
            _status.update { it.copy(matched = found.size) }
            AppLog.i(
                "Local",
                "Library has ${music.songs.size} songs from this phone ($why): ${music.albums.size} albums, ${music.artists.size} artists of their own, " +
                    "${found.size} songs also on the server, ${music.songs.size - found.size} only on the phone",
            )
        }
        library.refreshStatus()
        library.bump()
    }

    /** Looks up MusicBrainz IDs for files without them, one a second, while the switch is on. */
    private fun lookUpMissing() {
        if (!_data.value.musicBrainz || lookupJob?.isActive == true) return
        val wanted = _data.value.tracks.filter { it.wantsLookup }
        if (wanted.isEmpty()) return
        lookupJob = scope.launch {
            AppLog.i("MusicBrainz", "Looking up ${wanted.size} songs without MusicBrainz IDs")
            _status.update { it.copy(lookingUp = true, looked = 0, toLook = wanted.size, lookupError = null) }
            var found = 0
            var failures = 0
            try {
                for ((i, t) in wanted.withIndex()) {
                    if (!_data.value.musicBrainz) break
                    val done = try {
                        lookup.lookUp(t)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failures++
                        AppLog.w("MusicBrainz", "Lookup of ${t.artist} – ${t.title} failed: ${e.message}")
                        if (failures >= MAX_FAILURES) {
                            _status.update { it.copy(lookupError = "MusicBrainz couldn't be reached: ${e.message}") }
                            break
                        }
                        continue
                    }
                    if (done.mbRecording != null) found++
                    update { d -> d.copy(tracks = d.tracks.map { if (it.uri == done.uri) done else it }) }
                    _status.update { it.copy(looked = i + 1) }
                    // The library is rebuilt now and then, so matches and artists fill in as it goes.
                    if ((i + 1) % REBUILD_EVERY == 0) rebuild("MusicBrainz")
                }
            } finally {
                _status.update { it.copy(lookingUp = false) }
                AppLog.i("MusicBrainz", "Lookup done: $found recordings found", event = "local.musicbrainz", fields = mapOf("found" to found, "failures" to failures))
            }
            if (found > 0) rebuild("MusicBrainz")
        }
    }

    private fun folderName(tree: Uri): String = runCatching {
        val id = DocumentsContract.getTreeDocumentId(tree)
        // "primary:Music/Albums" → "Music/Albums"; an SD card's id starts with its own name.
        val path = id.substringAfter(':', id)
        val where = id.substringBefore(':')
        if (where == "primary" || where == id) path.ifBlank { "Phone storage" } else "$path (SD card)".trimStart()
    }.getOrDefault(tree.lastPathSegment ?: "Folder")

    private fun update(change: (LocalData) -> LocalData) {
        val next = synchronized(this) { change(_data.value).also { _data.value = it } }
        byId = next.tracks.associateBy { it.id }
        runCatching {
            val temp = File(file.path + ".tmp")
            temp.writeText(CacheJson.encodeToString(LocalData.serializer(), next))
            temp.renameTo(file)
        }.onFailure { AppLog.w("Local", "Couldn't save the list of files: ${it.message}") }
    }

    private fun load(): LocalData = runCatching {
        if (file.isFile) CacheJson.decodeFromString(LocalData.serializer(), file.readText()) else LocalData()
    }.onFailure { AppLog.w("Local", "Couldn't read the list of files: ${it.message}") }.getOrDefault(LocalData())

    private companion object {
        const val MAX_FAILURES = 5
        const val REBUILD_EVERY = 100
    }
}
