package app.siansa.playback

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import app.siansa.log.AppLog
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaQueueData
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import org.json.JSONObject

/**
 * Music on a Chromecast or Google TV, the queue kept on the phone (issue #114), as [app.siansa.playback.dlna.DlnaPlayer]
 * does for DLNA: the phone's queue is the only one, the TV holds a few songs of it ([TvWindow]: the one playing, one
 * before for its remote's previous, and the next few, loaded ahead for no gap), and what the TV says is read as "the
 * TV plays entry X of the phone's queue", found by a key of the phone's own on each song it sends.
 *
 * Instead of Media3's RemoteCastPlayer, which builds the queue the app sees from the TV's list of item ids: that list
 * arrives late and in parts, so the phone showed "Now playing """, songs of the TV's old queue, a seek applied to that
 * old queue, and no queue at all for seconds (no mini player) while the TV played (0.52.0-dev, the owner's log 21:28);
 * it also drops the TV's answer to a load, so a load the TV refused went unnoticed.
 *
 * Main thread.
 */
@OptIn(UnstableApi::class)
class TvPlayer(
    private val converter: CastItems,
    /** The Cast session now, if any. */
    private val castSession: () -> CastSession?,
    private val prep: Prep? = null,
) : SimpleBasePlayer(Looper.getMainLooper()) {
    /** Gets songs ready before they go to the TV (their file types, for lossless); [done] on the main thread. */
    interface Prep {
        fun needs(items: List<MediaItem>): Boolean
        fun run(items: List<MediaItem>, done: () -> Unit)
    }

    private val main = Handler(Looper.getMainLooper())

    private var items: List<MediaItem> = emptyList()
    /** A key per queue entry, sent with the song so the TV's word maps back to it (the same song can be in twice). */
    private var keys: List<Long> = emptyList()
    private var nextKey = 1L
    private var index = 0
    private var playWhenReady = false
    private var state = Player.STATE_IDLE
    private var repeat = Player.REPEAT_MODE_OFF
    private var positionMs = 0L
    private var positionAt = 0L
    private var playing = false
    private var durationMs = C.TIME_UNSET
    private var prepared = false
    private var volume = 1.0
    /** The load the TV was last asked for, so the TV's word about an older one is ignored. */
    private var loadGeneration = 0
    private var loading = false
    /** The queue entry playing as a conversion started part-way in after a seek, and where it starts (issue #901). */
    private var startedPartWay: Pair<Long, Long>? = null
    /** The queue entry the phone last told the TV to jump to, and when: see [TvJump]. */
    private var jumpingTo: Long? = null
    private var jumpedAt = 0L
    private var syncing = false
    private var syncAgain = false
    private var client: RemoteMediaClient? = null

    private val available = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP, Player.COMMAND_RELEASE,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_DEFAULT_POSITION, Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_METADATA,
            Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_CHANGE_MEDIA_ITEMS, Player.COMMAND_GET_DEVICE_VOLUME,
            Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS, Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
        )
        .build()

    private val deviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMinVolume(0).setMaxVolume(VOLUME_STEPS).build()

    private val callback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = onStatus()
        override fun onQueueStatusUpdated() = onStatus()
    }

    /** Listens to the TV of [session] (a cast started or came back). */
    fun attach(session: CastSession) {
        val c = session.remoteMediaClient ?: return
        if (c === client) return
        client?.unregisterCallback(callback)
        client = c
        c.registerCallback(callback)
        volume = runCatching { session.volume }.getOrDefault(volume)
        invalidateState()
    }

    /** The cast ended: nothing more to hear. */
    fun detach() {
        client?.unregisterCallback(callback)
        client = null
        playing = false
    }

    /** Whether a Cast session is connected now. */
    val connected: Boolean get() = castSession()?.isConnected == true

    override fun getState(): SimpleBasePlayer.State {
        val playlist = items.mapIndexed { i, item ->
            SimpleBasePlayer.MediaItemData.Builder(keys[i])
                .setMediaItem(item)
                .setDurationUs(if (i == index && durationMs != C.TIME_UNSET) durationMs * 1000 else songDurationUs(item))
                .setIsSeekable(true)
                .build()
        }
        val shown = if (playlist.isEmpty() && state != Player.STATE_ENDED) Player.STATE_IDLE else state
        val position = if (playing) SimpleBasePlayer.PositionSupplier.getExtrapolating(positionNow(), 1f)
        else SimpleBasePlayer.PositionSupplier.getConstant(positionMs)
        return SimpleBasePlayer.State.Builder()
            .setAvailableCommands(available)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(shown)
            .setRepeatMode(repeat)
            .setPlaylist(playlist)
            .apply { if (playlist.isNotEmpty()) setCurrentMediaItemIndex(index.coerceIn(0, playlist.size - 1)) }
            .setContentPositionMs(position)
            .setDeviceInfo(deviceInfo)
            .setDeviceVolume((volume * VOLUME_STEPS).toInt())
            .build()
    }

    // ---- What the session asks for ----

    override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        items = mediaItems.toList()
        keys = items.map { nextKey++ }
        index = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        positionMs = if (startPositionMs == C.TIME_UNSET) 0L else startPositionMs.coerceAtLeast(0)
        durationMs = C.TIME_UNSET
        if (items.isEmpty()) {
            state = Player.STATE_IDLE
            return Futures.immediateVoidFuture()
        }
        return if (prepared) load("a new queue of ${items.size} songs") else Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        prepared = true
        if (items.isEmpty()) return Futures.immediateVoidFuture()
        state = Player.STATE_BUFFERING
        return load("prepare")
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        val c = client ?: return Futures.immediateVoidFuture()
        if (state == Player.STATE_ENDED && playWhenReady && items.isNotEmpty()) {
            index = 0
            positionMs = 0
            return load("play again from the start")
        }
        if (playWhenReady) c.play() else {
            positionMs = positionNow()
            playing = false
            c.pause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (items.isEmpty()) return Futures.immediateVoidFuture()
        val target = if (mediaItemIndex == C.INDEX_UNSET) index else mediaItemIndex.coerceIn(0, items.size - 1)
        val at = if (positionMs == C.TIME_UNSET) 0L else positionMs.coerceAtLeast(0)
        val c = client
        if (target == index && c != null && state != Player.STATE_IDLE && state != Player.STATE_ENDED) {
            this.positionMs = at
            positionAt = System.currentTimeMillis()
            // A conversion the server makes as it plays can't be sought in: the TV went back to the start (issue #901).
            // It's loaded again from that point instead.
            if (TvSeek.isConversion(c.mediaStatus?.mediaInfo?.contentUrl) && !loading) {
                startedPartWay = keys[index] to at
                AppLog.i("Cast", "Seek to ${at / 1000} s in a song the server converts: the TV loads it again from there")
                return load("a seek in a converted song")
            }
            val partWay = startedPartWay?.takeIf { it.first == keys[index] }?.second ?: 0L
            c.seek(com.google.android.gms.cast.MediaSeekOptions.Builder().setPosition((at - partWay).coerceAtLeast(0)).build())
            return Futures.immediateVoidFuture()
        }
        startedPartWay = null
        index = target
        this.positionMs = at
        durationMs = C.TIME_UNSET
        // A song the TV holds is jumped to there; anything else is a new load around it.
        val itemId = tvItemId(keys[target])
        if (c != null && itemId != null && !loading) {
            jumpingTo = keys[target]
            jumpedAt = System.currentTimeMillis()
            c.queueJumpToItem(itemId, at, null)
            sync()
            return Futures.immediateVoidFuture()
        }
        return if (prepared) load("a jump to song ${target + 1} of ${items.size}") else Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        positionMs = positionNow()
        playing = false
        state = Player.STATE_IDLE
        prepared = false
        runCatching { client?.stop() }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        detach()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        repeat = repeatMode
        // Repeat one is the TV's own; repeat all is the phone's, which wraps the songs it hands ahead.
        runCatching { client?.queueSetRepeatMode(if (repeatMode == Player.REPEAT_MODE_ONE) MediaStatus.REPEAT_MODE_REPEAT_SINGLE else MediaStatus.REPEAT_MODE_REPEAT_OFF, null) }
        sync()
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        val at = index.coerceIn(0, items.size)
        items = items.toMutableList().apply { addAll(at, mediaItems) }
        keys = keys.toMutableList().apply { addAll(at, mediaItems.map { nextKey++ }) }
        if (items.size == mediaItems.size) {
            this.index = 0
            return if (prepared) load("songs added to an empty queue") else Futures.immediateVoidFuture()
        }
        if (at <= this.index) this.index += mediaItems.size
        sync()
        return Futures.immediateVoidFuture()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        val removingCurrent = index in fromIndex until toIndex
        items = items.toMutableList().apply { subList(fromIndex, toIndex).clear() }
        keys = keys.toMutableList().apply { subList(fromIndex, toIndex).clear() }
        when {
            items.isEmpty() -> {
                index = 0
                state = Player.STATE_ENDED
                playing = false
                runCatching { client?.stop() }
                return Futures.immediateVoidFuture()
            }
            index >= toIndex -> index -= toIndex - fromIndex
            removingCurrent -> {
                index = fromIndex.coerceAtMost(items.size - 1)
                positionMs = 0
                return if (prepared) load("the song playing was removed") else Futures.immediateVoidFuture()
            }
        }
        sync()
        return Futures.immediateVoidFuture()
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        val currentKey = keys.getOrNull(index)
        val movedItems = items.subList(fromIndex, toIndex).toList()
        val movedKeys = keys.subList(fromIndex, toIndex).toList()
        items = items.toMutableList().apply { subList(fromIndex, toIndex).clear(); addAll(minOf(newIndex, size), movedItems) }
        keys = keys.toMutableList().apply { subList(fromIndex, toIndex).clear(); addAll(minOf(newIndex, size), movedKeys) }
        index = keys.indexOf(currentKey).coerceAtLeast(0)
        sync()
        return Futures.immediateVoidFuture()
    }

    override fun handleReplaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        val replacingCurrent = index in fromIndex until toIndex
        items = items.toMutableList().apply { subList(fromIndex, toIndex).clear(); addAll(fromIndex, mediaItems) }
        keys = keys.toMutableList().apply { subList(fromIndex, toIndex).clear(); addAll(fromIndex, mediaItems.map { nextKey++ }) }
        if (replacingCurrent) {
            index = fromIndex.coerceAtMost(items.size - 1)
            return if (prepared) load("the song playing was replaced") else Futures.immediateVoidFuture()
        }
        if (index >= toIndex) index += mediaItems.size - (toIndex - fromIndex)
        sync()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        setVolume(deviceVolume.toDouble() / VOLUME_STEPS)
        return Futures.immediateVoidFuture()
    }

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        setVolume(volume + 1.0 / VOLUME_STEPS)
        return Futures.immediateVoidFuture()
    }

    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        setVolume(volume - 1.0 / VOLUME_STEPS)
        return Futures.immediateVoidFuture()
    }

    private fun setVolume(to: Double) {
        volume = to.coerceIn(0.0, 1.0)
        runCatching { castSession()?.volume = volume }
    }

    // ---- The TV ----

    /**
     * Loads the window around the current song on the TV, in one load, replacing whatever the TV had. The songs' file
     * types are looked up first ([prep]); the future ends once the load is sent.
     */
    private fun load(why: String): ListenableFuture<*> {
        val generation = ++loadGeneration
        loading = true
        jumpingTo = null
        state = Player.STATE_BUFFERING
        val window = TvWindow.around(items.size, index, repeat == Player.REPEAT_MODE_ALL)
        val sending = window.map { items[it] }
        val result = SettableFuture.create<Any?>()
        val send = {
            if (generation == loadGeneration) sendLoad(window, why, generation)
            result.set(null)
        }
        val p = prep
        if (p != null && p.needs(sending)) p.run(sending) { send() } else send()
        return result
    }

    private fun sendLoad(window: List<Int>, why: String, generation: Int) {
        val c = client
        if (c == null) {
            loading = false
            AppLog.w("Cast", "No TV to load the queue on ($why)")
            return
        }
        val start = window.indexOf(index).coerceAtLeast(0)
        val queueItems = window.map { toQueueItem(it) }
        val bytes = queueItems.sumOf { runCatching { it.toJson().toString().length }.getOrDefault(0) }
        AppLog.i("Cast", "To the TV ($why): song ${index + 1} of ${items.size} with ${window.size - 1} around it, ${bytes / 1024} KB")
        val request = MediaLoadRequestData.Builder()
            .setQueueData(
                MediaQueueData.Builder()
                    .setItems(queueItems)
                    .setStartIndex(start)
                    .setRepeatMode(if (repeat == Player.REPEAT_MODE_ONE) MediaStatus.REPEAT_MODE_REPEAT_SINGLE else MediaStatus.REPEAT_MODE_REPEAT_OFF)
                    .build(),
            )
            .setAutoplay(playWhenReady)
            // A song started part-way in by the server starts at its own beginning.
            .setCurrentTime(if (startedPartWay?.first == keys.getOrNull(index)) 0L else positionMs)
            .build()
        val sentAt = System.currentTimeMillis()
        c.load(request).setResultCallback { r ->
            if (generation != loadGeneration) return@setResultCallback
            loading = false
            val status = r.status
            if (status.isSuccess) {
                AppLog.d("Cast", "The TV took the queue in ${System.currentTimeMillis() - sentAt} ms")
            } else {
                // Said, not just lost (Media3 dropped this answer): the code tells too big, refused, or the TV gone.
                AppLog.w("Cast", "The TV didn't take the queue ($why): ${status.statusCode} ${com.google.android.gms.cast.CastStatusCodes.getStatusCodeString(status.statusCode)} ${status.statusMessage.orEmpty()}")
            }
            onStatus()
        }
    }

    /** A queue song for the TV, with this phone's key for its queue entry. */
    private fun toQueueItem(i: Int): MediaQueueItem {
        val partWay = startedPartWay?.takeIf { it.first == keys[i] }?.second ?: 0L
        val base = converter.toMediaQueueItem(items[i], keys[i], partWay)
        return MediaQueueItem.Builder(base)
            .setCustomData(JSONObject().put(ENTRY_KEY, keys[i]))
            .build()
    }

    /** The TV's item id for the queue entry [key], if the TV holds it. */
    private fun tvItemId(key: Long): Int? {
        val s = client?.mediaStatus ?: return null
        return s.queueItems.firstOrNull { entryOf(it) == key }?.itemId
    }

    private fun entryOf(item: MediaQueueItem?): Long? =
        (item?.customData?.optLong(ENTRY_KEY, -1L)?.takeIf { it > 0 }) ?: item?.media?.let { entryOf(it) }

    private fun entryOf(info: com.google.android.gms.cast.MediaInfo): Long? = info.customData?.optLong(ENTRY_KEY, -1L)?.takeIf { it > 0 }

    /** What the TV says: which of the phone's songs it plays, where, and whether. */
    private fun onStatus() {
        val c = client ?: return
        val s = c.mediaStatus ?: return
        val current = s.getQueueItemById(s.currentItemId)
        // The song playing, from its queue entry, or its media when the TV's status doesn't list the queue.
        val key = entryOf(current) ?: s.mediaInfo?.let { entryOf(it) }
        val at = key?.let { keys.indexOf(it) } ?: -1
        // The TV's word about a queue the phone has replaced (or is replacing): nothing to take from it.
        if (at < 0 || loading) {
            if (s.playerState == MediaStatus.PLAYER_STATE_IDLE && s.idleReason == MediaStatus.IDLE_REASON_ERROR && !loading) {
                AppLog.w("Cast", "The TV stopped with an error")
            }
            return
        }
        // Right after a jump the TV still reports the song it's leaving: the phone stays on the one it asked for
        // (issue #901: each skip went back and on again, and told ListenBrainz both songs).
        when (TvJump.follow(jumpingTo, jumpedAt, key, System.currentTimeMillis())) {
            TvJump.Follow.IGNORE -> return
            TvJump.Follow.ARRIVED -> jumpingTo = null
            TvJump.Follow.FOLLOW -> if (jumpingTo != null) {
                AppLog.w("Cast", "The TV didn't reach song ${keys.indexOf(jumpingTo!!) + 1} within ${TvJump.SETTLE_MS} ms; following the TV")
                jumpingTo = null
            }
        }
        val before = index
        index = at
        if (before != at) {
            AppLog.i("Cast", "The TV moved on to song ${at + 1} of ${items.size}")
            durationMs = C.TIME_UNSET
        }
        // A song the server started part-way in: the TV counts from there, the phone from the song's start.
        if (startedPartWay != null && startedPartWay?.first != key) startedPartWay = null
        val partWay = startedPartWay?.second ?: 0L
        s.mediaInfo?.streamDuration?.takeIf { it > 0 }?.let { durationMs = it + partWay }
        positionMs = c.approximateStreamPosition.coerceAtLeast(0) + partWay
        positionAt = System.currentTimeMillis()
        when (s.playerState) {
            MediaStatus.PLAYER_STATE_PLAYING -> { state = Player.STATE_READY; playing = true; playWhenReady = true }
            MediaStatus.PLAYER_STATE_PAUSED -> { state = Player.STATE_READY; playing = false; playWhenReady = false }
            MediaStatus.PLAYER_STATE_BUFFERING, MediaStatus.PLAYER_STATE_LOADING -> { state = Player.STATE_BUFFERING; playing = false }
            MediaStatus.PLAYER_STATE_IDLE -> {
                playing = false
                when (s.idleReason) {
                    MediaStatus.IDLE_REASON_FINISHED -> {
                        // The TV played out the songs it held: on with the phone's queue, or the end.
                        val next = TvWindow.next(items.size, index, repeat == Player.REPEAT_MODE_ALL)
                        if (next == null) {
                            state = Player.STATE_ENDED
                        } else {
                            index = next
                            positionMs = 0
                            load("the TV played out what it held")
                        }
                    }
                    MediaStatus.IDLE_REASON_ERROR -> {
                        AppLog.w("Cast", "The TV couldn't play song ${index + 1}; going on with the next")
                        TvWindow.next(items.size, index, repeat == Player.REPEAT_MODE_ALL)?.let { index = it; positionMs = 0; load("after an error") }
                            ?: run { state = Player.STATE_ENDED }
                    }
                    else -> {}
                }
            }
        }
        invalidateState()
        if (before != at) sync()
    }

    /**
     * Keeps the songs the TV holds the window around the current one: the ones after it (and the one before) replaced
     * where they differ, never the one playing. One change at a time.
     */
    private fun sync() {
        val c = client ?: return
        if (loading) return
        if (syncing) { syncAgain = true; return }
        val s = c.mediaStatus ?: return
        val currentId = s.currentItemId
        val tvKeys = s.queueItems.map { entryOf(it) }
        val tvIds = s.queueItems.map { it.itemId }
        val currentAt = tvIds.indexOf(currentId)
        if (currentAt < 0 || tvKeys[currentAt] != keys.getOrNull(index)) return
        val want = TvWindow.around(items.size, index, repeat == Player.REPEAT_MODE_ALL).map { keys[it] }
        val wantAt = want.indexOf(keys[index])
        val plan = TvWindow.plan(tvKeys, currentAt, want, wantAt)
        if (plan.nothing) return
        syncing = true
        val remove = plan.remove.map { tvIds[it] }.toIntArray()
        val beforeId = tvIds.getOrElse(plan.insertBeforeAt) { currentId }
        fun insertAll() {
            val afterItems = plan.insertAfter.map { k -> toQueueItem(keys.indexOf(k)) }.toTypedArray()
            val beforeItems = plan.insertBefore.map { k -> toQueueItem(keys.indexOf(k)) }.toTypedArray()
            fun done() { syncing = false; if (syncAgain) { syncAgain = false; sync() } }
            when {
                afterItems.isNotEmpty() -> c.queueInsertItems(afterItems, MediaQueueItem.INVALID_ITEM_ID, null).setResultCallback {
                    if (beforeItems.isNotEmpty()) c.queueInsertItems(beforeItems, beforeId, null).setResultCallback { done() } else done()
                }
                beforeItems.isNotEmpty() -> c.queueInsertItems(beforeItems, beforeId, null).setResultCallback { done() }
                else -> done()
            }
        }
        AppLog.d("Cast", "The TV's songs follow the queue: ${remove.size} out, ${plan.insertAfter.size + plan.insertBefore.size} in")
        if (remove.isNotEmpty()) c.queueRemoveItems(remove, null).setResultCallback { insertAll() } else insertAll()
    }

    private fun positionNow(): Long = if (playing) positionMs + (System.currentTimeMillis() - positionAt) else positionMs

    private fun songDurationUs(item: MediaItem): Long = item.toSong().durationSec.takeIf { it > 0 }?.let { it * 1_000_000L } ?: C.TIME_UNSET

    companion object {
        /** This phone's key for a queue entry, in each TV song's own custom data. */
        const val ENTRY_KEY = "siansaEntry"
        const val VOLUME_STEPS = 20
    }
}

/** The few songs of the phone's queue the TV holds (issue #114), as index arithmetic. */
/** Whether a TV stream is the server converting the song as it plays (Navidrome's stream with a format other than raw). */
object TvSeek {
    fun isConversion(url: String?): Boolean {
        if (url == null || !url.substringBefore('?').endsWith("/rest/stream")) return false
        val format = url.substringAfter('?', "").split('&').firstOrNull { it.startsWith("format=") }?.substringAfter('=')
        return format != null && format != "raw"
    }
}

/**
 * After the phone tells the TV to jump to a song, the TV's next status updates can still name the song it's leaving
 * (issue #901). Those are ignored until the TV names the song it was sent to, for up to [SETTLE_MS]; after that the
 * TV's word counts again.
 */
object TvJump {
    const val SETTLE_MS = 3_000L

    enum class Follow { FOLLOW, IGNORE, ARRIVED }

    fun follow(jumpingTo: Long?, jumpedAt: Long, reported: Long?, now: Long): Follow = when {
        jumpingTo == null -> Follow.FOLLOW
        reported == jumpingTo -> Follow.ARRIVED
        now - jumpedAt < SETTLE_MS -> Follow.IGNORE
        else -> Follow.FOLLOW
    }
}

object TvWindow {
    /** Songs before the current one (the TV remote's previous). */
    const val BEHIND = 1
    /** Songs after it: the next is loaded ahead for no gap, the rest are a cushion for the TV remote's next. */
    const val AHEAD = 4

    /** The phone's indexes the TV holds around [index] of [size], in order; wrapping when [wrap] (repeat all). */
    fun around(size: Int, index: Int, wrap: Boolean): List<Int> {
        if (size <= 0) return emptyList()
        val out = ArrayList<Int>()
        for (d in -BEHIND..AHEAD) {
            val i = index + d
            val j = when {
                i in 0 until size -> i
                wrap && size > BEHIND + AHEAD + 1 -> ((i % size) + size) % size
                else -> continue
            }
            if (j !in out) out += j
        }
        return out
    }

    /** The song after [index], or null at the end (unless [wrap]). */
    fun next(size: Int, index: Int, wrap: Boolean): Int? = when {
        index + 1 < size -> index + 1
        wrap && size > 0 -> 0
        else -> null
    }

    /** What to change on the TV: its items to remove (TV positions), and keys to add after its last and before current. */
    data class Plan(val remove: List<Int>, val insertAfter: List<Long>, val insertBefore: List<Long>, val insertBeforeAt: Int = -1) {
        val nothing: Boolean get() = remove.isEmpty() && insertAfter.isEmpty() && insertBefore.isEmpty()
    }

    /**
     * The TV holds [tv] (keys, null for songs not the phone's) with the one playing at [tvCurrent]; the phone wants
     * [want] with the current at [wantCurrent]. Songs before and after the current one are replaced where they differ;
     * the current one is never touched.
     */
    fun plan(tv: List<Long?>, tvCurrent: Int, want: List<Long>, wantCurrent: Int): Plan {
        val tvBefore = tv.subList(0, tvCurrent)
        val tvAfter = tv.subList(tvCurrent + 1, tv.size)
        val wantBefore = want.subList(0, wantCurrent)
        val wantAfter = want.subList(wantCurrent + 1, want.size)
        val remove = ArrayList<Int>()
        var insertAfter: List<Long> = emptyList()
        var insertBefore: List<Long> = emptyList()
        var insertBeforeAt = tvCurrent
        if (tvAfter != wantAfter) {
            // Keep what already agrees at the start of what follows; replace the rest.
            var same = 0
            while (same < tvAfter.size && same < wantAfter.size && tvAfter[same] == wantAfter[same]) same++
            for (i in same until tvAfter.size) remove += tvCurrent + 1 + i
            insertAfter = wantAfter.drop(same)
        }
        if (tvBefore != wantBefore) {
            // Keep what already agrees just before the current one; replace the rest, before what's kept.
            var same = 0
            while (same < tvBefore.size && same < wantBefore.size && tvBefore[tvBefore.size - 1 - same] == wantBefore[wantBefore.size - 1 - same]) same++
            for (i in 0 until tvBefore.size - same) remove += i
            insertBefore = wantBefore.dropLast(same)
            insertBeforeAt = if (same > 0) tvBefore.size - same else tvCurrent
        }
        return Plan(remove.sorted(), insertAfter, insertBefore, insertBeforeAt)
    }
}
