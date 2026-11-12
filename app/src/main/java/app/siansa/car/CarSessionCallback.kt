package app.siansa.car

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import app.siansa.BuildConfig
import app.siansa.playback.QueueRules
import app.siansa.AppGraph
import app.siansa.log.AppLog
import app.siansa.playback.SongRegistry
import app.siansa.playback.toMediaItem
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * The music session's callback: the app's own queue changes, and the browse tree, search and playback that Android
 * Auto asks for (issue #62). The car only ever sends ids from [CarTree]; the app's songs pass through as before.
 */
@OptIn(UnstableApi::class)
class CarSessionCallback(private val graph: AppGraph, private val tree: CarTree) : MediaLibrarySession.Callback {
    /** The last searches' results, so the car's paging through them doesn't search again. */
    private val searches = ConcurrentHashMap<String, List<MediaItem>>()

    /**
     * Repeat and shuffle are changed in the app only ([QueueRules.takesModeChange]): another app's ask (a car's or a
     * headset's Bluetooth) is turned down and written to the log, so repeat no longer comes on by itself.
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
        val mode = playerCommand == Player.COMMAND_SET_REPEAT_MODE || playerCommand == Player.COMMAND_SET_SHUFFLE_MODE
        if (mode && !QueueRules.takesModeChange(controller.packageName, BuildConfig.APPLICATION_ID)) {
            AppLog.i("Player", "${controller.packageName} asked to change ${if (playerCommand == Player.COMMAND_SET_REPEAT_MODE) "repeat" else "shuffle"}: not taken, that's set in Siansa only")
            return SessionError.ERROR_NOT_SUPPORTED
        }
        return SessionResult.RESULT_SUCCESS
    }

    override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
        // "Recent" roots are Android's own resume suggestions, which aren't offered.
        if (params?.isRecent == true) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
        AppLog.i("Car", "${browser.packageName} opened the music to browse")
        return Futures.immediateFuture(LibraryResult.ofItem(tree.root(), params))
    }

    override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> =
        async("Item $mediaId", onError = { LibraryResult.ofError(SessionError.ERROR_UNKNOWN) }) { tree.item(mediaId)?.let { LibraryResult.ofItem(it, null) } ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE) }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = async("List $parentId", onError = { LibraryResult.ofError(SessionError.ERROR_IO) }) {
        val items = tree.children(parentId) ?: return@async LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        AppLog.d("Car", "List $parentId: ${items.size} items (page $page)")
        LibraryResult.ofItemList(CarTree.page(items, page, pageSize), params)
    }

    override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
        graph.scope.launch {
            val found = runCatching { tree.search(query) }
                .onFailure { if (it is CancellationException) throw it; AppLog.w("Car", "Search \"$query\" failed: ${it.message}") }
                .getOrDefault(emptyList())
            if (searches.size > 8) searches.clear()
            searches[query] = found
            AppLog.i("Car", "Search \"$query\": ${found.size} results")
            withContext(Dispatchers.Main) { session.notifySearchResultChanged(browser, query, found.size, params) }
        }
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = async("Search \"$query\"", onError = { LibraryResult.ofError(SessionError.ERROR_IO) }) {
        val found = searches[query] ?: tree.search(query).also { searches[query] = it }
        LibraryResult.ofItemList(CarTree.page(found, page, pageSize), params)
    }

    /**
     * A song picked in the car plays its whole list from that song; a mood radio, album or playlist plays whole; a
     * spoken "Play … on Siansa" plays what it names. Everything the app itself queues keeps its own list.
     */
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val only = mediaItems.singleOrNull()
        val spoken = only?.requestMetadata?.searchQuery
        val picked = only?.mediaId?.takeIf { CarId.parse(it) != null }
        if (spoken == null && picked == null) return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        graph.scope.launch {
            try {
                val queue = if (spoken != null) tree.voice(spoken) else tree.queue(picked!!)
                if (queue == null || queue.songs.isEmpty()) {
                    AppLog.w("Car", if (spoken != null) "Nothing found to play for \"$spoken\"" else "Nothing to play for $picked")
                    future.setException(IllegalArgumentException("Nothing to play"))
                    return@launch
                }
                AppLog.i("Car", (if (spoken != null) "Playing \"$spoken\"" else "Playing $picked") +
                    ": ${queue.songs.size} songs from song ${queue.start + 1}, asked by ${controller.packageName}")
                SongRegistry.register(queue.songs)
                val items = queue.songs.map { it.toMediaItem(graph.source) }
                if (queue.route != null && queue.title != null) runCatching { graph.recentPlays.note(queue.route, queue.title, queue.cover) }
                future.set(MediaSession.MediaItemsWithStartPosition(items, queue.start, 0L))
            } catch (e: CancellationException) {
                future.cancel(false)
            } catch (e: Exception) {
                AppLog.e("Car", "Couldn't start ${picked ?: "\"$spoken\""} from the car", e)
                future.setException(e)
            }
        }
        return future
    }

    // Items arrive from the app's controller without their URI, so it's restored here. A song added from the car
    // (its id names the list it's in) becomes that song.
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> {
        if (mediaItems.none { CarId.parse(it.mediaId) is CarId.Song }) return Futures.immediateFuture(mediaItems.map(::withUri).toMutableList())
        return async("Add from the car") {
            mediaItems.flatMap { item ->
                if (CarId.parse(item.mediaId) !is CarId.Song) return@flatMap listOf(withUri(item))
                val queue = tree.queue(item.mediaId) ?: return@flatMap emptyList()
                val song = queue.songs[queue.start]
                SongRegistry.register(listOf(song))
                listOf(song.toMediaItem(graph.source))
            }.toMutableList()
        }
    }

    private fun withUri(item: MediaItem): MediaItem {
        val uri = item.requestMetadata.mediaUri ?: Uri.parse(graph.source.streamUrl(item.mediaId))
        return item.buildUpon().setUri(uri).build()
    }

    /**
     * [block] run in the background, its answer handed to Media3. A failure is logged and answered with [onError]'s
     * result (the car then shows it couldn't load), or failed when there's none.
     */
    private fun <T> async(what: String, onError: (() -> T)? = null, block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        graph.scope.launch {
            try {
                future.set(block())
            } catch (e: CancellationException) {
                future.cancel(false)
            } catch (e: Exception) {
                AppLog.w("Car", "$what failed: ${e.message}")
                if (onError != null) future.set(onError()) else future.setException(e)
            }
        }
        return future
    }
}
