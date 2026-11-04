package app.siansa.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import app.siansa.log.AppLog
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture

/**
 * Siansa's own copy of the queue it sent to the TV (issue #114), to name the songs the TV doesn't. Cast only tells
 * the phone about the few songs around the one playing (Google's TV screen: about six of a 55-song queue), so Media3
 * shows the rest as blank songs with no id; and after the same songs are sent again, Media3 can forget even the one
 * playing (0.52.0-dev: "Now playing" with no song, over and over).
 *
 * A blank song is named by what the TV said about that queue entry before ([uids] are the TV's queue item ids), else
 * by the song at the same place in the queue sent, while that copy still lines up with the TV's: the same length,
 * and the same song wherever the TV names one. Changes made on the phone keep the copy in step; one that can't be
 * followed leaves only what the TV said.
 */
class CastQueueCopy<T>(private val idOf: (T) -> String) {
    private var sent: MutableList<T>? = ArrayList()
    private val named = HashMap<Any, T>()

    /**
     * The queue as sent to the TV, replacing what was there. What the TV named stays until its queue no longer lists
     * those entries: the TV gives a new queue new ids.
     */
    fun set(items: List<T>) {
        sent = items.toMutableList()
    }

    fun add(index: Int, items: List<T>) = edit { list ->
        if (index !in 0..list.size) return@edit false
        list.addAll(index, items)
        true
    }

    fun remove(from: Int, to: Int) = edit { list ->
        if (from < 0 || to > list.size || from > to) return@edit false
        list.subList(from, to).clear()
        true
    }

    /** As [Player.moveMediaItems]: songs [from] to [to] (exclusive) go to [newIndex], counted without them. */
    fun move(from: Int, to: Int, newIndex: Int) = edit { list ->
        if (from < 0 || to > list.size || from > to || newIndex < 0) return@edit false
        val moved = list.subList(from, to).toList()
        list.subList(from, to).clear()
        list.addAll(minOf(newIndex, list.size), moved)
        true
    }

    fun replace(from: Int, to: Int, items: List<T>) = edit { list ->
        if (from < 0 || to > list.size || from > to) return@edit false
        list.subList(from, to).clear()
        list.addAll(from, items)
        true
    }

    /** The copy of the queue as it stands, or null once a change couldn't be followed. */
    fun copy(): List<T>? = sent?.toList()

    private inline fun edit(change: (MutableList<T>) -> Boolean) {
        val list = sent ?: return
        if (!change(list)) sent = null
    }

    /**
     * The TV's queue ([items], with its queue item ids [uids]) with every blank song named where it can be. Songs
     * still blank afterwards are unknown: nobody can say which they are.
     */
    fun fill(uids: List<Any>, items: List<T>): List<T> {
        require(uids.size == items.size)
        if (uids.isNotEmpty()) named.keys.retainAll(uids.toSet())
        items.forEachIndexed { i, item -> if (idOf(item).isNotBlank()) named[uids[i]] = item }
        val copy = sent?.takeIf { linesUp(it, items) }
        return items.mapIndexed { i, item ->
            if (idOf(item).isNotBlank()) item
            else named[uids[i]] ?: copy?.get(i)?.takeIf { idOf(it).isNotBlank() } ?: item
        }
    }

    private fun linesUp(copy: List<T>, items: List<T>): Boolean =
        copy.size == items.size && items.indices.all { i -> idOf(items[i]).isBlank() || idOf(items[i]) == idOf(copy[i]) }
}

/** How a TV's queue came out after [CastQueueCopy.fill], for the log. */
data class CastQueueFill(val songs: Int, val fromTv: Int, val fromPhone: Int, val unknown: Int) {
    companion object {
        fun <T> of(before: List<T>, after: List<T>, idOf: (T) -> String): CastQueueFill {
            val fromTv = before.count { idOf(it).isNotBlank() }
            val unknown = after.count { idOf(it).isBlank() }
            return CastQueueFill(before.size, fromTv, before.size - fromTv - unknown, unknown)
        }
    }
}

/**
 * The Cast player as the rest of the app sees it (issue #114): the same, with the songs Cast leaves blank named from
 * [CastQueueCopy], so the queue sheet, the notification, scrobbling and bringing the music back to the phone all get
 * real songs. Main thread, like the player it wraps.
 */
@OptIn(UnstableApi::class)
class CastQueuePlayer(tv: Player, private val prep: Prep? = null) : ForwardingSimpleBasePlayer(tv) {
    /**
     * Gets songs ready before they go to the TV (issue #114): their file types, asked from the server, so "Lossless"
     * can send each as it is or as FLAC. Until [run] says it's [done] (on the main thread), the phone shows the new
     * queue as it will be.
     */
    interface Prep {
        /** Whether [items] need getting ready; when not, it says in the log how they go. */
        fun needs(items: List<MediaItem>): Boolean
        fun run(items: List<MediaItem>, done: () -> Unit)
    }

    private val copy = CastQueueCopy<MediaItem> { it.mediaId }
    private var lastFill: CastQueueFill? = null

    /** The TV's queue as the phone last saw it with songs in it, for bringing the music back (issue #114). */
    private var known: CastSpot<MediaItem>? = null
    private var knownPlaying = false
    private var knownAt = 0L

    /**
     * Where the music was on the TV when the phone last saw its queue, moved on by the time since while it played:
     * when casting ends and the TV forgot its queue first (Back on the TV's remote), the music comes back from here.
     */
    fun lastKnown(nowMs: Long = android.os.SystemClock.elapsedRealtime()): CastSpot<MediaItem>? =
        known?.let { CastHandBack.movedOn(it, knownPlaying, nowMs - knownAt) }

    override fun getState(): SimpleBasePlayer.State {
        val state = super.getState()
        val timeline = state.timeline
        if (timeline.isEmpty) return state
        val window = Timeline.Window()
        val uids = ArrayList<Any>(timeline.windowCount)
        val items = ArrayList<MediaItem>(timeline.windowCount)
        for (i in 0 until timeline.windowCount) {
            timeline.getWindow(i, window)
            uids += window.uid
            items += window.mediaItem
        }
        val filled = copy.fill(uids, items)
        val index = state.currentMediaItemIndex.coerceIn(0, filled.size - 1)
        known = CastSpot(filled, index, runCatching { state.contentPositionMsSupplier.get() }.getOrDefault(0L).coerceAtLeast(0L))
        knownPlaying = state.playWhenReady && state.playbackState == Player.STATE_READY
        knownAt = android.os.SystemClock.elapsedRealtime()
        val fill = CastQueueFill.of(items, filled) { it.mediaId }
        if (fill != lastFill && (fill.fromPhone > 0 || fill.unknown > 0)) {
            AppLog.d("Cast", "The TV's queue: ${fill.songs} songs, ${fill.fromTv} named by the TV, ${fill.fromPhone} from the phone's copy, ${fill.unknown} unknown")
        }
        lastFill = fill
        if (fill.fromPhone == 0) return state
        // The Cast player's song details are its current song's, so they come from the named song now (null).
        return state.buildUpon()
            .setPlaylist(NamedTimeline(timeline, filled), state.currentTracks, null)
            .build()
    }

    override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        copy.set(mediaItems)
        return afterPrep(mediaItems) { setNow(mediaItems, startIndex, startPositionMs) }
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        copy.add(index, mediaItems)
        return afterPrep(mediaItems) { addNow(index, mediaItems) }
    }

    private fun setNow(items: List<MediaItem>, start: Int, positionMs: Long) = super.handleSetMediaItems(items, start, positionMs)

    private fun addNow(index: Int, items: List<MediaItem>) = super.handleAddMediaItems(index, items)

    /** [send] the songs to the TV once they're ready ([prep]); straight away when there's nothing to get ready. */
    private fun afterPrep(items: List<MediaItem>, send: () -> ListenableFuture<*>): ListenableFuture<*> {
        val p = prep
        if (p == null || !p.needs(items)) return send()
        val result = SettableFuture.create<Any?>()
        p.run(items) { result.setFuture(send()) }
        return result
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        copy.move(fromIndex, toIndex, newIndex)
        return super.handleMoveMediaItems(fromIndex, toIndex, newIndex)
    }

    override fun handleReplaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        copy.replace(fromIndex, toIndex, mediaItems)
        return super.handleReplaceMediaItems(fromIndex, toIndex, mediaItems)
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        copy.remove(fromIndex, toIndex)
        return super.handleRemoveMediaItems(fromIndex, toIndex)
    }
}

/** [inner] with its songs swapped for [items], one per window. */
@OptIn(UnstableApi::class)
private class NamedTimeline(private val inner: Timeline, private val items: List<MediaItem>) : Timeline() {
    override fun getWindowCount(): Int = inner.windowCount

    override fun getWindow(windowIndex: Int, window: Timeline.Window, defaultPositionProjectionUs: Long): Timeline.Window {
        inner.getWindow(windowIndex, window, defaultPositionProjectionUs)
        window.mediaItem = items[windowIndex]
        return window
    }

    override fun getPeriodCount(): Int = inner.periodCount

    override fun getPeriod(periodIndex: Int, period: Timeline.Period, setIds: Boolean): Timeline.Period = inner.getPeriod(periodIndex, period, setIds)

    override fun getIndexOfPeriod(uid: Any): Int = inner.getIndexOfPeriod(uid)

    override fun getUidOfPeriod(periodIndex: Int): Any = inner.getUidOfPeriod(periodIndex)
}

/**
 * Whether the phone's view of the TV's queue is out of date (issue #114): the TV lists songs but the phone sees
 * another number, or the TV plays something while the phone sees no queue at all. Media3 reads the TV's queue only
 * when the TV sends news, and after a new queue the list of songs can arrive just after that news: the phone then
 * showed no queue (and no mini player) while the TV played on (0.52.0-dev). Asking the TV for its news again fixes it.
 */
object CastQueueCheck {
    fun stale(phoneCount: Int, tvCount: Int, tvHasMedia: Boolean): Boolean =
        (tvCount > 0 && phoneCount != tvCount) || (phoneCount == 0 && tvHasMedia)
}
