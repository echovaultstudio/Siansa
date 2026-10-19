package app.musix.offline

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.musix.MusixApp
import app.musix.data.Album
import app.musix.data.Artist
import app.musix.data.JsonFile
import app.musix.data.Playlist
import app.musix.data.cover
import app.musix.data.LibraryRepository
import app.musix.data.LocalPlaylist
import app.musix.data.LocalPlaylists
import app.musix.data.toPlaylist
import app.musix.data.Song
import app.musix.data.SettingsRepository
import app.musix.data.Users
import app.musix.log.AppLog
import app.musix.net.SubsonicApi
import app.musix.playback.Streaming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** A song saved on the phone: its details (so it can be listed and played offline) and its size. */
@Serializable
data class DownloadedSong(val song: Song, val bytes: Long = 0, val at: Long = 0, val kbps: Int = 0)

/** What can be picked for downloading in the Downloads menu. */
@Serializable
enum class OfflineKind { ALBUM, PLAYLIST, ARTIST, LIKED, SONG }

/**
 * Something you chose to keep on the phone: an album, playlist, artist, your liked songs or one song.
 * [songIds] are its songs as they were last checked; playlists, artists and liked songs are checked again
 * so songs added later are downloaded too.
 */
@Serializable
data class OfflineItem(
    val kind: OfflineKind,
    val id: String,
    val name: String,
    val subtitle: String = "",
    val cover: String? = null,
    val songIds: List<String> = emptyList(),
    val addedAt: Long = 0,
) {
    val key: String get() = key(kind, id)

    companion object {
        fun key(kind: OfflineKind, id: String) = "$kind:$id"
        const val LIKED_ID = "liked"
    }
}

fun Album.offlineItem() = OfflineItem(OfflineKind.ALBUM, id, name, artist, coverArt)
fun Playlist.offlineItem() = OfflineItem(OfflineKind.PLAYLIST, id, name, owner.orEmpty(), coverArt)
fun Artist.offlineItem() = OfflineItem(OfflineKind.ARTIST, id, name, "", coverArt)
fun Song.offlineItem() = OfflineItem(OfflineKind.SONG, id, title, artist, cover)
fun LocalPlaylist.offlineItem() = toPlaylist().offlineItem().copy(subtitle = "On this phone")
fun likedOfflineItem() = OfflineItem(OfflineKind.LIKED, OfflineItem.LIKED_ID, "Liked songs")

/** What you chose, what's downloaded, and what's still waiting to be (oldest first). */
@Serializable
data class DownloadList(
    val items: List<OfflineItem> = emptyList(),
    val done: List<DownloadedSong> = emptyList(),
    val waiting: List<Song> = emptyList(),
    /**
     * Songs you removed one by one from Downloaded songs: a playlist, artist or Liked songs checked
     * again doesn't download them back. Choosing the item (or the song) again brings them back.
     */
    val skipped: Set<String> = emptySet(),
)

/** How much of an album or playlist is on the phone, for its download button. */
enum class OfflineState { NONE, WAITING, PARTLY, ALL }

/**
 * What's on the phone, for "Only downloaded music" (issues #56 and #141): the albums and artists that
 * have at least one downloaded song, the playlists chosen for downloading that have some of their songs
 * here, and every downloaded song.
 */
data class OfflineShelf(
    val songs: Set<String> = emptySet(),
    val albums: Set<String> = emptySet(),
    val artists: Set<String> = emptySet(),
    val playlists: Set<String> = emptySet(),
    val liked: Boolean = false,
) {
    val isEmpty: Boolean get() = songs.isEmpty()

    /** True when every one of [songIds] is on the phone (a mix plays offline only then). */
    fun hasAll(songIds: List<String>): Boolean = songIds.isNotEmpty() && songIds.all { it in songs }
}

/** The download list's decisions, kept apart from files and the network so they can be tested. */
object OfflinePlan {
    /**
     * [list] with [item] chosen (or its songs updated), and its songs that aren't on the phone yet waiting.
     * [explicit] is true when you chose it (it brings back songs you removed by hand); false when a
     * playlist, artist or Liked songs is only checked again, which leaves those songs out.
     */
    fun choose(list: DownloadList, item: OfflineItem, songs: List<Song>, explicit: Boolean = true): DownloadList {
        val skipped = if (explicit) list.skipped - songs.mapTo(HashSet()) { it.id } else list.skipped
        val kept = songs.filter { it.id !in skipped }
        val updated = item.copy(songIds = kept.map { it.id }.distinct())
        val items = if (list.items.any { it.key == item.key }) list.items.map { if (it.key == item.key) updated.copy(addedAt = it.addedAt) else it }
        else list.items + updated
        // Songs that dropped out of a playlist (or were unliked) go, unless something else still wants them.
        return prune(enqueue(list.copy(items = items, skipped = skipped), kept))
    }

    /**
     * [list] without the songs [ids], removed by hand from Downloaded songs: they're taken out of every
     * album, playlist and artist that had them (one left with no songs goes too) and aren't downloaded again
     * when a playlist is checked for new songs.
     */
    /**
     * "Stop all downloads" (issue #235): every song still waiting is dropped and won't come back by itself,
     * as if removed by hand; songs already downloaded stay. Albums and playlists left with no songs go.
     */
    fun stopAll(list: DownloadList): DownloadList = removeSongs(list, list.waiting.mapTo(HashSet()) { it.id })

    fun removeSongs(list: DownloadList, ids: Set<String>): DownloadList {
        if (ids.isEmpty()) return list
        val items = list.items
            .map { it.copy(songIds = it.songIds.filterNot { id -> id in ids }) }
            .filter { it.songIds.isNotEmpty() }
        return remove(list.copy(items = items, skipped = list.skipped + ids), ids)
    }

    /**
     * [list] with the phone-only playlists ([phone], each with its songs) you chose to download kept up to
     * date (issue #147): songs added to one download, songs taken out go unless something else wants them,
     * and a playlist deleted (or sent to Navidrome) goes with its songs. A phone playlist you haven't chosen
     * to download isn't downloaded by itself (issue #235): making one just makes the playlist. Songs removed
     * by hand from Downloaded songs stay out, unless they're added to the playlist again: [previous] is each
     * playlist's songs at the last sync (null when the app has just started, so nothing counts as added).
     */
    fun syncPhonePlaylists(
        list: DownloadList,
        phone: List<Pair<OfflineItem, List<Song>>>,
        previous: Map<String, Set<String>>?,
    ): DownloadList {
        val keys = phone.mapTo(HashSet()) { it.first.key }
        var result = list.items
            .filter { it.kind == OfflineKind.PLAYLIST && LocalPlaylists.isLocal(it.id) && it.key !in keys }
            .fold(list) { l, gone -> drop(l, gone.key) }
        for ((item, songs) in phone) {
            // Only playlists already chosen for download (issue #235).
            val old = result.items.firstOrNull { it.key == item.key } ?: continue
            if (songs.isEmpty()) {
                result = drop(result, item.key)
                continue
            }
            val ids = songs.mapTo(HashSet()) { it.id }
            val added = if (previous == null) emptySet() else ids - previous[item.key].orEmpty()
            val skipped = result.skipped - added
            val wanted = songs.filter { it.id !in skipped }.map { it.id }.distinct()
            if (old.songIds == wanted && old.name == item.name && old.cover == item.cover) continue
            result = choose(result.copy(skipped = skipped), item.copy(addedAt = old.addedAt), songs, explicit = false)
        }
        return result
    }

    /** [list] without the item [key], and without songs no other item still wants. */
    fun drop(list: DownloadList, key: String): DownloadList = prune(list.copy(items = list.items.filterNot { it.key == key }))

    /** Ids of songs downloaded or waiting that no chosen item wants any more. */
    fun unwanted(list: DownloadList): Set<String> {
        val wanted = list.items.flatMapTo(HashSet()) { it.songIds }
        return (list.done.map { it.song.id } + list.waiting.map { it.id }).filterTo(HashSet()) { it !in wanted }
    }

    private fun prune(list: DownloadList): DownloadList {
        val gone = unwanted(list)
        return if (gone.isEmpty()) list else remove(list, gone)
    }

    /** [list] with [songs] added to the waiting list, skipping ones already downloaded or waiting. */
    fun enqueue(list: DownloadList, songs: List<Song>): DownloadList {
        val known = list.done.mapTo(HashSet()) { it.song.id } + list.waiting.map { it.id }
        val added = songs.filter { it.id !in known }.distinctBy { it.id }
        return if (added.isEmpty()) list else list.copy(waiting = list.waiting + added)
    }

    /** [list] without the songs [ids] (an item that fails for good keeps its other songs). */
    fun remove(list: DownloadList, ids: Set<String>): DownloadList =
        list.copy(done = list.done.filterNot { it.song.id in ids }, waiting = list.waiting.filterNot { it.id in ids })

    /** [song] moved from waiting to done. */
    fun finished(list: DownloadList, song: Song, bytes: Long, at: Long, kbps: Int = 0): DownloadList = list.copy(
        done = list.done.filterNot { it.song.id == song.id } + DownloadedSong(song, bytes, at, kbps),
        waiting = list.waiting.filterNot { it.id == song.id },
    )

    fun state(songIds: List<String>, done: Set<String>, waiting: Set<String>): OfflineState {
        if (songIds.isEmpty()) return OfflineState.NONE
        val have = songIds.count { it in done }
        return when {
            have == songIds.size -> OfflineState.ALL
            songIds.any { it in waiting } -> OfflineState.WAITING
            have > 0 -> OfflineState.PARTLY
            else -> OfflineState.NONE
        }
    }

    /** What [list] has on the phone, by album, artist and playlist. */
    fun shelf(list: DownloadList): OfflineShelf {
        val songs = list.done.mapTo(HashSet()) { it.song.id }
        val withSongs = list.items.filter { item -> item.songIds.any { it in songs } }
        return OfflineShelf(
            songs = songs,
            albums = list.done.mapNotNullTo(HashSet()) { it.song.albumId },
            artists = list.done.flatMapTo(HashSet()) { d -> listOfNotNull(d.song.artistId) + d.song.artists.map { it.id } },
            playlists = withSongs.filter { it.kind == OfflineKind.PLAYLIST }.mapTo(HashSet()) { it.id },
            liked = withSongs.any { it.kind == OfflineKind.LIKED },
        )
    }

    /**
     * Whether a finished download got all of the file. Opus conversions only have an estimated length,
     * so they can end a little short or long of it.
     */
    fun complete(written: Long, total: Long, converted: Boolean): Boolean = converted || total <= 0 || written == total

    /** True when downloads have used up the limit ([limitMb] 0 = no limit). */
    fun full(usedBytes: Long, limitMb: Int): Boolean = limitMb > 0 && usedBytes >= limitMb * 1024L * 1024L

    /** The file name for a song id (Navidrome ids are letters and digits, but nothing else gets through). */
    fun fileName(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
}

/**
 * Songs saved on the phone to play without a connection (issue #55). Unlike the song cache, nothing
 * here is removed until you remove it. Each song is the original file from Navidrome, kept in the app's
 * own storage (one folder per user); the player plays the file instead of streaming whenever it's there.
 *
 * Downloads run one at a time in the app's scope. When they have to wait (for Wi-Fi, or a connection
 * that dropped), WorkManager starts them again once the network is back.
 */
class Downloads(
    private val context: Context,
    private val subsonic: SubsonicApi,
    private val library: LibraryRepository,
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    /** Song ids downloaded and waiting, and the keys of the chosen items, for the screens. */
    data class Ids(val done: Set<String> = emptySet(), val waiting: Set<String> = emptySet(), val items: Set<String> = emptySet())

    data class Progress(
        val running: Boolean = false,
        val current: Song? = null,
        val bytes: Long = 0,
        val total: Long = 0,
        val note: String? = null,
    )

    private val dir = File(context.filesDir, Users.scoped("offline")).apply { mkdirs() }
    private val json = JsonFile(File(context.filesDir, Users.scoped("downloads.json")), DownloadList.serializer()) { DownloadList() }

    private val _list = MutableStateFlow(DownloadList())
    val list: StateFlow<DownloadList> = _list.asStateFlow()
    private val _ids = MutableStateFlow(Ids())
    val ids: StateFlow<Ids> = _ids.asStateFlow()
    private val _shelf = MutableStateFlow(OfflineShelf())
    /** What's on the phone by album, artist and playlist, for "Only downloaded music". */
    val shelf: StateFlow<OfflineShelf> = _shelf.asStateFlow()
    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    @Volatile private var job: Job? = null

    // Each phone-only playlist's songs at the last sync, to tell songs added to it.
    private var lastPhone: Map<String, Set<String>>? = null

    init {
        scope.launch {
            publish(json.read())
            // Songs whose file is gone (a restored backup, or storage cleared) are downloaded again.
            val missing = _list.value.done.filter { fileFor(it.song.id) == null }
            if (missing.isNotEmpty()) {
                AppLog.i("Downloads", "${missing.size} downloaded songs aren't on the phone any more; downloading them again")
                val ids = missing.mapTo(HashSet()) { it.song.id }
                publish(json.update { l -> l.copy(done = l.done.filterNot { it.song.id in ids }, waiting = l.waiting + missing.map { it.song }) })
            }
            if (_list.value.waiting.isNotEmpty()) start("left from last time")
            if (settings.current.navidromeReady && _list.value.items.isNotEmpty()) refresh()
        }
    }

    /** The song's file when it's downloaded. Called by the player for every song it opens, so it only looks at the disk. */
    fun fileFor(id: String): File? = File(dir, OfflinePlan.fileName(id)).takeIf { it.isFile }

    /** The downloaded songs of the item [key], in its order, for showing it without a connection. */
    fun songsOf(key: String): List<Song>? {
        val list = _list.value
        val item = list.items.firstOrNull { it.key == key } ?: return null
        val done = list.done.associateBy { it.song.id }
        return item.songIds.mapNotNull { done[it]?.song }
    }

    fun isDownloaded(id: String): Boolean = id in _ids.value.done

    fun usedBytes(): Long = _list.value.done.sumOf { it.bytes }

    /** Keeps [item] on the phone, with [songs] as its songs; already-downloaded songs aren't fetched again. */
    fun choose(item: OfflineItem, songs: List<Song>) {
        if (songs.isEmpty()) return
        scope.launch {
            change { OfflinePlan.choose(it, item.copy(addedAt = System.currentTimeMillis()), songs) }
            AppLog.i("Downloads", "Chose ${item.kind.name.lowercase()} ${item.id} (${songs.size} songs), ${_list.value.waiting.size} waiting")
            start("chosen")
        }
    }

    /** Looks up the songs of [item] and keeps it on the phone. */
    fun choose(item: OfflineItem) {
        scope.launch { chooseNow(item) }
    }

    private suspend fun chooseNow(item: OfflineItem) {
        val songs = runCatching { songsOf(item) }
            .onFailure { AppLog.w("Downloads", "Couldn't get the songs of ${item.key}: ${it.message}") }
            .getOrNull()
        if (songs.isNullOrEmpty()) {
            _progress.value = Progress(note = "Couldn't get the songs of ${item.name}")
            return
        }
        change { OfflinePlan.choose(it, item.copy(addedAt = System.currentTimeMillis()), songs) }
        AppLog.i("Downloads", "Chose ${item.kind.name.lowercase()} ${item.id} (${songs.size} songs), ${_list.value.waiting.size} waiting")
        start("chosen")
    }

    /** Stops keeping the item [key]; its songs are deleted unless another item still wants them. */
    fun drop(key: String) {
        scope.launch {
            change { OfflinePlan.drop(it, key) }
            AppLog.i("Downloads", "Removed $key from downloads")
        }
    }

    /** Removes the songs [ids] (chosen in Downloaded songs) from the phone, wherever they came from. */
    fun removeSongs(ids: Set<String>) {
        if (ids.isEmpty()) return
        scope.launch {
            change { OfflinePlan.removeSongs(it, ids) }
            AppLog.i("Downloads", "Removed ${ids.size} downloaded songs by hand")
        }
    }

    /** Keeps the phone-only playlists you chose to download up to date; called whenever they change. */
    suspend fun syncPhonePlaylists(playlists: List<LocalPlaylist>) {
        val phone = playlists.map { it.offlineItem() to it.songs }
        val previous = lastPhone
        lastPhone = phone.associate { (item, songs) -> item.key to songs.mapTo(HashSet()) { it.id } }
        // Nothing to do (the usual case): leave the file alone.
        if (OfflinePlan.syncPhonePlaylists(_list.value, phone, previous) == _list.value) return
        val before = _list.value.waiting.size
        change { OfflinePlan.syncPhonePlaylists(it, phone, previous) }
        val waiting = _list.value.waiting.size
        if (waiting != before) AppLog.i("Downloads", "Phone playlists synced (${playlists.size}), $waiting waiting")
        if (waiting > 0) start("phone playlists")
    }

    /**
     * Keeps the downloads of the item [fromKey] under [to] instead, so a phone playlist sent to Navidrome
     * stays on the phone without downloading its songs again.
     */
    suspend fun moveItem(fromKey: String, to: OfflineItem) {
        change { list -> list.copy(items = list.items.map { if (it.key == fromKey) to.copy(songIds = it.songIds, addedAt = it.addedAt) else it }) }
        AppLog.i("Downloads", "Kept the downloads of $fromKey as ${to.key}")
    }

    /** Downloads [items] (e.g. every playlist), one after the other. */
    fun chooseAll(items: List<OfflineItem>) {
        AppLog.i("Downloads", "Chose ${items.size} items at once")
        scope.launch { items.forEach { chooseNow(it) } }
    }

    /** Stops keeping [keys] (e.g. every playlist) at once. */
    fun dropAll(keys: List<String>) {
        val set = keys.toSet()
        scope.launch {
            change { list -> set.fold(list) { l, key -> OfflinePlan.drop(l, key) } }
            AppLog.i("Downloads", "Removed ${set.size} items from downloads")
        }
    }

    fun removeAll() {
        scope.launch {
            job?.cancel()
            change { DownloadList() }
            dir.listFiles()?.forEach { it.delete() }
            _progress.value = Progress()
            AppLog.i("Downloads", "Removed all downloads")
        }
    }

    /**
     * Checks playlists, artists and liked songs for songs added or removed since they were chosen, and
     * downloads the new ones. Albums don't change, so they're left as they are.
     */
    fun refresh() {
        scope.launch {
            // Phone-only playlists aren't on Navidrome; syncPhonePlaylists keeps them up to date.
            val checked = _list.value.items.filter { it.kind == OfflineKind.PLAYLIST || it.kind == OfflineKind.ARTIST || it.kind == OfflineKind.LIKED }
            for (item in checked.filterNot { it.kind == OfflineKind.PLAYLIST && LocalPlaylists.isLocal(it.id) }) {
                val songs = runCatching { songsOf(item) }.getOrNull() ?: continue
                // Songs removed by hand stay out; they don't count as a change either.
                val skipped = _list.value.skipped
                if (songs.filter { it.id !in skipped }.map { it.id }.toSet() == item.songIds.toSet()) continue
                AppLog.i("Downloads", "${item.key} changed: ${item.songIds.size} → ${songs.size} songs")
                change { OfflinePlan.choose(it, item, songs, explicit = false) }
            }
            if (_list.value.waiting.isNotEmpty()) start("refreshed")
        }
    }

    private suspend fun songsOf(item: OfflineItem): List<Song> = when (item.kind) {
        OfflineKind.ALBUM -> library.album(item.id)?.let { library.songsOf(it) }.orEmpty().ifEmpty { subsonic.album(item.id).songs }
        OfflineKind.PLAYLIST -> library.keepSyncedSongs(subsonic.playlist(item.id).songs, "downloads")
        OfflineKind.ARTIST -> library.artist(item.id)?.let { library.songsOf(it) }.orEmpty()
        OfflineKind.LIKED -> runCatching { subsonic.starredSongs() }
            .map { list ->
                val local = library.songs(list.map { it.id }).associateBy { it.id }
                library.keepSyncedSongs(list.map { local[it.id] ?: it }, "Downloads")
            }
            .getOrElse { library.starredSongs() }
        OfflineKind.SONG -> listOfNotNull(library.song(item.id))
    }

    /** Applies [edit] to the list, and deletes the files of songs it no longer has. */
    private suspend fun change(edit: (DownloadList) -> DownloadList) {
        val before = _ids.value
        publish(json.update(edit))
        val after = _ids.value
        val gone = (before.done + before.waiting) - (after.done + after.waiting)
        if (gone.isEmpty()) return
        val stopCurrent = _progress.value.current?.id in gone
        if (stopCurrent) job?.cancelAndJoin()
        gone.forEach { id -> File(dir, OfflinePlan.fileName(id)).delete() }
        AppLog.i("Downloads", "Deleted ${gone.size} songs no longer chosen")
        if (stopCurrent && after.waiting.isNotEmpty()) start("carrying on")
    }

    /** Stops every download still waiting (the ✕ beside the progress, issue #235); what's downloaded stays. */
    fun stopAll() {
        scope.launch {
            val waiting = _list.value.waiting.size
            job?.cancelAndJoin()
            _progress.value.current?.let { File(dir, OfflinePlan.fileName(it.id) + PART).delete() }
            change { OfflinePlan.stopAll(it) }
            _progress.value = Progress(note = "Stopped")
            AppLog.i("Downloads", "Stopped all downloads: $waiting songs won't download")
        }
    }

    /** Pauses the downloads (the Pause button); they carry on with [retry] or when more is chosen. */
    fun pause() {
        job?.cancel()
        _progress.value = Progress(note = "Paused")
        AppLog.i("Downloads", "Downloads paused, ${_list.value.waiting.size} waiting")
    }

    /** Starts the downloads if they aren't running, and waits for them. */
    suspend fun runShared(why: String) {
        val current = synchronized(this) {
            job?.takeIf { it.isActive } ?: scope.launch {
                AppLog.i("Downloads", "Downloads starting ($why), ${_list.value.waiting.size} waiting")
                try {
                    run()
                } finally {
                    _progress.update { it.copy(running = false, current = null) }
                }
            }.also { job = it }
        }
        current.join()
    }

    /** Lets the waiting songs download again after a setting changed (Wi-Fi only, the limit). */
    fun retry() = start("settings changed")

    private fun start(why: String) {
        scope.launch { runShared(why) }
    }

    private suspend fun run() {
        while (true) {
            coroutineContext.ensureActive()
            val song = _list.value.waiting.firstOrNull() ?: break
            val s = settings.current
            if (!s.navidromeReady) return stopped("Navidrome isn't set up")
            if (s.downloadWifiOnly && !Streaming.isUnmetered(context)) {
                later(NetworkType.UNMETERED)
                return stopped("Waiting for Wi-Fi")
            }
            if (OfflinePlan.full(usedBytes(), s.downloadLimitMb)) {
                AppLog.i("Downloads", "Storage limit of ${s.downloadLimitMb} MB reached, ${_list.value.waiting.size} still waiting")
                return stopped("Storage limit reached")
            }
            _progress.value = Progress(running = true, current = song)
            val kbps = s.downloadKbps
            val bytes = try {
                download(song, kbps)
            } catch (e: IOException) {
                AppLog.w("Downloads", "Download of ${song.id} stopped: ${e.javaClass.simpleName}: ${e.message}")
                later(if (s.downloadWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                return stopped("Couldn't reach Navidrome, will try again")
            }
            if (bytes == null) {
                // Navidrome doesn't have it (any more): no point trying again.
                _progress.update { it.copy(current = null) }
                change { OfflinePlan.remove(it, setOf(song.id)) }
                continue
            }
            publish(json.update { OfflinePlan.finished(it, song, bytes, System.currentTimeMillis(), kbps) })
        }
        val done = _list.value.done
        AppLog.i("Downloads", "Downloads done: ${done.size} songs, ${done.sumOf { it.bytes } / 1_048_576} MB")
        _progress.value = Progress(note = null)
    }

    /** Saves one song: the original file, or Opus when kbps > 0. Returns its size, or null when Navidrome refused it. */
    private suspend fun download(song: Song, kbps: Int): Long? {
        val target = File(dir, OfflinePlan.fileName(song.id))
        val part = File(dir, OfflinePlan.fileName(song.id) + PART)
        // The original file, or an Opus conversion by Navidrome when that's the chosen download quality.
        val url = if (kbps > 0) Streaming.rewrite(android.net.Uri.parse(subsonic.streamUrl(song.id)), kbps).toString() else subsonic.streamUrl(song.id)
        val call = http.newCall(Request.Builder().url(url).build())
        val cancel = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    AppLog.w("Downloads", "Song ${song.id}: HTTP ${response.code}")
                    if (response.code >= 500) throw IOException("HTTP ${response.code}")
                    return null
                }
                val body = response.body ?: throw IOException("No data")
                val type = body.contentType()?.toString().orEmpty()
                // Navidrome answers some errors with a JSON or XML message instead of the file.
                if (type.startsWith("application/json") || type.startsWith("text/xml") || type.startsWith("application/xml")) {
                    AppLog.w("Downloads", "Song ${song.id}: Navidrome sent $type instead of audio")
                    return null
                }
                val total = body.contentLength()
                _progress.update { it.copy(total = total.coerceAtLeast(0)) }
                var written = 0L
                var lastShown = 0L
                part.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            written += n
                            if (written - lastShown > 256 * 1024) {
                                lastShown = written
                                _progress.update { it.copy(bytes = written) }
                            }
                        }
                    }
                }
                if (!OfflinePlan.complete(written, total, converted = kbps > 0)) throw IOException("Got $written of $total bytes")
                coroutineContext.ensureActive()
                if (!part.renameTo(target)) throw IOException("Couldn't save the file")
                AppLog.d("Downloads", "Song ${song.id}: ${written / 1024} KB" + if (kbps > 0) " (Opus $kbps)" else "")
                return written
            }
        } finally {
            cancel?.dispose()
            part.delete()
        }
    }

    private fun stopped(note: String) {
        _progress.value = Progress(note = note)
    }

    /** Starts the downloads again once the phone has the right network. */
    private fun later(network: NetworkType) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun publish(list: DownloadList) {
        _list.value = list
        _ids.value = Ids(list.done.mapTo(HashSet()) { it.song.id }, list.waiting.mapTo(HashSet()) { it.id }, list.items.mapTo(HashSet()) { it.key })
        _shelf.value = OfflinePlan.shelf(list)
    }

    private companion object {
        const val PART = ".part"
        const val WORK = "song-downloads"
    }
}

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as MusixApp).graph.downloads.runShared("network is back")
        return Result.success()
    }
}
