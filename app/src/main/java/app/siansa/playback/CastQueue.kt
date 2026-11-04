package app.siansa.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import app.siansa.log.AppLog
import androidx.media3.common.C
import com.google.common.util.concurrent.Futures
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
 * The Cast player as the rest of the app sees it (issue #114): the same, with
 * - the songs Cast leaves blank named from [CastQueueCopy], so the queue sheet, the notification, scrobbling and
 *   bringing the music back to the phone all get real songs;
 * - a long queue sent as a window ([CastWindow]): the TV holds the current song, a few before it and the next ones,
 *   topped up as it plays, while the phone keeps (and shows) the whole queue. Cast's messages are limited to 64 KB:
 *   a 497-song queue sent whole never reached the TV (0.52.0-dev).
 * Main thread, like the player it wraps.
 */
@OptIn(UnstableApi::class)
class CastQueuePlayer(private val tv: Player, private val prep: Prep? = null) : ForwardingSimpleBasePlayer(tv) {
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

    /** A song of the phone's queue, with an id of its own for the queue the phone shows. */
    private class Entry(val item: MediaItem) {
        val uid = Any()
    }

    private val copy = CastQueueCopy<MediaItem> { it.mediaId }
    private var lastFill: CastQueueFill? = null
    private val window = CastWindow<Entry>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastOutOfStep: String? = null

    /** The TV's queue as the phone last saw it with songs in it, for bringing the music back (issue #114). */
    private var known: CastSpot<MediaItem>? = null
    private var knownPlaying = false
    private var knownAt = 0L

    init {
        // As the TV moves on (by itself, or by its remote), its window follows.
        tv.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                main.post { topUp() }
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                main.post { topUp() }
            }
        })
    }

    /**
     * Where the music was on the TV when the phone last saw its queue, moved on by the time since while it played:
     * when casting ends and the TV forgot its queue first (Back on the TV's remote), the music comes back from here.
     */
    fun lastKnown(nowMs: Long = android.os.SystemClock.elapsedRealtime()): CastSpot<MediaItem>? =
        known?.let { CastHandBack.movedOn(it, knownPlaying, nowMs - knownAt) }

    /** How many songs Media3 lists for the TV (its window), to compare with what the TV itself lists. */
    val tvSideCount: Int get() = tv.mediaItemCount

    override fun getState(): SimpleBasePlayer.State {
        val state = super.getState()
        val timeline = state.timeline
        if (timeline.isEmpty) return state
        val tw = Timeline.Window()
        val uids = ArrayList<Any>(timeline.windowCount)
        val items = ArrayList<MediaItem>(timeline.windowCount)
        for (i in 0 until timeline.windowCount) {
            timeline.getWindow(i, tw)
            uids += tw.uid
            items += tw.mediaItem
        }
        val filled = copy.fill(uids, items)
        val local = state.currentMediaItemIndex.coerceIn(0, filled.size - 1)
        val fill = CastQueueFill.of(items, filled) { it.mediaId }
        if (fill != lastFill && (fill.fromPhone > 0 || fill.unknown > 0)) {
            AppLog.d("Cast", "The TV's queue: ${fill.songs} songs, ${fill.fromTv} named by the TV, ${fill.fromPhone} from the phone's copy, ${fill.unknown} unknown")
        }
        lastFill = fill
        val position = runCatching { state.contentPositionMsSupplier.get() }.getOrDefault(0L).coerceAtLeast(0L)
        knownPlaying = state.playWhenReady && state.playbackState == Player.STATE_READY
        knownAt = android.os.SystemClock.elapsedRealtime()
        // The whole queue, where the TV's current song is found in it.
        val current = CastWindowView.fullIndex(window.full.map { it.item.mediaId }, window.start, filled.map { it.mediaId }, local)
        if (current == null) {
            val why = "${filled.size} songs on the TV, window ${window.start + 1}–${window.end} of ${window.full.size}"
            if (window.full.isNotEmpty() && why != lastOutOfStep) AppLog.d("Cast", "The TV's queue isn't the phone's window ($why): showing the TV's")
            lastOutOfStep = why
            known = CastSpot(filled, local, position)
            if (fill.fromPhone == 0) return state
            // The Cast player's song details are its current song's, so they come from the named song now (null).
            return state.buildUpon().setPlaylist(NamedTimeline(timeline, filled), state.currentTracks, null).build()
        }
        lastOutOfStep = null
        known = CastSpot(window.full.map { it.item }, current, position)
        if (window.full.size == filled.size && window.start == 0) {
            // The TV holds the whole queue: as it says, with the songs it left blank named.
            if (fill.fromPhone == 0) return state
            return state.buildUpon().setPlaylist(NamedTimeline(timeline, filled), state.currentTracks, null).build()
        }
        val currentDurationUs = timeline.getWindow(local, tw).durationUs
        val playlist = window.full.mapIndexed { i, e ->
            val song = e.item.toSong()
            SimpleBasePlayer.MediaItemData.Builder(e.uid)
                .setMediaItem(e.item)
                .setIsSeekable(true)
                .setIsPlaceholder(false)
                .setDurationUs(if (i == current && currentDurationUs != C.TIME_UNSET) currentDurationUs else if (song.durationSec > 0) song.durationSec * 1_000_000L else C.TIME_UNSET)
                .apply { if (i == current) setTracks(state.currentTracks) }
                .build()
        }
        return state.buildUpon().setPlaylist(playlist).setCurrentMediaItemIndex(current).build()
    }

    override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        val start = if (startIndex == C.INDEX_UNSET) 0 else startIndex
        window.set(mediaItems.map(::Entry), start)
        return sendWindow(start, startPositionMs, "a new queue")
    }

    /** Sends the TV the window, playing the full queue's [index] from [positionMs]. */
    private fun sendWindow(index: Int, positionMs: Long, why: String): ListenableFuture<*> {
        val items = window.items().map { it.item }
        copy.set(items)
        val local = (index - window.start).coerceIn(0, (items.size - 1).coerceAtLeast(0))
        if (window.full.size > items.size) {
            AppLog.i("Cast", "To the TV ($why): songs ${window.start + 1}–${window.end} of ${window.full.size}, the rest follow as it plays")
        }
        return afterPrep(items) { setNow(items, local, positionMs) }
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        val wasEmpty = window.size == 0
        val local = window.insert(index, mediaItems.map(::Entry))
        if (wasEmpty && window.size > 0) return sendWindow(0, 0L, "songs added to an empty queue")
        if (local == null) return Futures.immediateVoidFuture<Any>()
        copy.add(local, mediaItems)
        return afterPrep(mediaItems) { addNow(local, mediaItems) }
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        val currentFull = currentFullIndex()
        val local = window.remove(fromIndex, toIndex) ?: return Futures.immediateVoidFuture<Any>()
        if (window.size == 0 && window.full.isNotEmpty()) {
            // Everything the TV held is gone: it gets the songs after them.
            window.reset(fromIndex)
            return sendWindow(fromIndex.coerceAtMost(window.full.size - 1), 0L, "its songs were removed")
        }
        copy.remove(local.first, local.last + 1)
        if (currentFull in fromIndex until toIndex) AppLog.d("Cast", "The song playing on the TV was removed from the queue")
        return removeNow(local.first, local.last + 1)
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        val currentFull = currentFullIndex()
        val count = toIndex - fromIndex
        val inside = window.toLocal(fromIndex) != null && toIndex <= window.end &&
            newIndex >= window.start && newIndex + count <= window.end
        if (inside) {
            val (from, to) = window.move(fromIndex, toIndex, newIndex)!!
            copy.move(from, from + count, to)
            return super.handleMoveMediaItems(from, from + count, to)
        }
        if (currentFull in fromIndex until toIndex) {
            // The song playing moves across the window's edge: the TV gets the window around it again.
            val playing = window.full[currentFull]
            window.move(fromIndex, toIndex, newIndex)
            val at = window.full.indexOf(playing)
            window.reset(at)
            return sendWindow(at, tv.currentPosition, "the playing song moved")
        }
        // Out of one place and into another: as a removal and an insertion, each done on the TV where it holds them.
        val moved = window.full.subList(fromIndex, toIndex).map { it.item }
        val removed = handleRemoveMediaItems(fromIndex, toIndex)
        handleAddMediaItems(newIndex, moved)
        return removed
    }

    override fun handleReplaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        if (mediaItems.size != toIndex - fromIndex) {
            handleRemoveMediaItems(fromIndex, toIndex)
            return handleAddMediaItems(fromIndex, mediaItems)
        }
        val local = window.replace(fromIndex, toIndex, mediaItems.map(::Entry)) ?: return Futures.immediateVoidFuture<Any>()
        val part = mediaItems.subList(local.first + window.start - fromIndex, local.last + window.start - fromIndex + 1)
        copy.replace(local.first, local.last + 1, part)
        return super.handleReplaceMediaItems(local.first, local.last + 1, part)
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (mediaItemIndex == C.INDEX_UNSET || window.full.isEmpty() || window.full.size == window.size) {
            return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
        val local = window.toLocal(mediaItemIndex)
        if (local != null) {
            // The TV holds it: a jump to a song is by the TV's index; next, previous and the rest as they are.
            return if (seekCommand == Player.COMMAND_SEEK_TO_MEDIA_ITEM) super.handleSeek(local, positionMs, seekCommand)
            else super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
        // A song the TV doesn't hold (a jump far along the queue, or next at the window's edge): the window around it.
        window.reset(mediaItemIndex)
        val at = if (positionMs == C.TIME_UNSET) 0L else positionMs.coerceAtLeast(0)
        return sendWindow(mediaItemIndex, at, "a jump to song ${mediaItemIndex + 1}")
    }

    /**
     * Keeps the TV's window ahead of what it plays: more songs at its end as it nears them, and the oldest dropped.
     * Only while the TV's queue is the window sent (not while a change is on its way).
     */
    private fun topUp() {
        if (window.full.size <= window.size) return
        val count = tv.mediaItemCount
        if (count != window.size) return
        val local = tv.currentMediaItemIndex.takeIf { it in 0 until count } ?: return
        val step = window.topUp(local)
        if (step.nothing) return
        if (!step.add.isEmpty()) {
            val items = window.full.subList(step.add.first, step.add.last + 1).map { it.item }
            val at = window.size
            window.grown(CastWindow.TopUp(step.add, 0))
            copy.add(at, items)
            AppLog.d("Cast", "The TV gets songs ${step.add.first + 1}–${step.add.last + 1} of ${window.full.size} next")
            afterPrep(items) { addNow(at, items) }
        }
        if (step.drop > 0) {
            window.grown(CastWindow.TopUp(IntRange.EMPTY, step.drop))
            copy.remove(0, step.drop)
            removeNow(0, step.drop)
        }
    }

    private fun currentFullIndex(): Int = window.toFull(tv.currentMediaItemIndex.coerceAtLeast(0))

    private fun setNow(items: List<MediaItem>, start: Int, positionMs: Long) = super.handleSetMediaItems(items, start, positionMs)

    private fun addNow(index: Int, items: List<MediaItem>) = super.handleAddMediaItems(index, items)

    private fun removeNow(from: Int, to: Int) = super.handleRemoveMediaItems(from, to)

    /** [send] the songs to the TV once they're ready ([prep]); straight away when there's nothing to get ready. */
    private fun afterPrep(items: List<MediaItem>, send: () -> ListenableFuture<*>): ListenableFuture<*> {
        val p = prep
        if (p == null || !p.needs(items)) return send()
        val result = SettableFuture.create<Any?>()
        p.run(items) { result.setFuture(send()) }
        return result
    }
}

/** Where the TV's current song is in the phone's whole queue (issue #114). */
object CastWindowView {
    /**
     * The full queue's index of the TV's song [local] (of [tvIds]), given the window sent from [start] of [fullIds]:
     * at the same place when the songs agree there (blank ones from the TV count as agreeing), else the same song
     * nearest the window, else null (the TV's queue isn't the phone's: someone else changed it).
     */
    fun fullIndex(fullIds: List<String>, start: Int, tvIds: List<String>, local: Int): Int? {
        if (fullIds.isEmpty() || local !in tvIds.indices) return null
        val id = tvIds[local]
        val atPlace = start + local
        if (atPlace in fullIds.indices && (id.isBlank() || fullIds[atPlace] == id)) return atPlace
        if (id.isBlank()) return null
        return fullIds.indices.filter { fullIds[it] == id }.minByOrNull { kotlin.math.abs(it - atPlace) }
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
