package app.siansa.playback.dlna

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import app.siansa.data.cover
import app.siansa.log.AppLog
import app.siansa.net.MusicSource
import app.siansa.playback.Streaming
import app.siansa.playback.toSong
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Plays the queue on a UPnP/DLNA TV or speaker. The media session drives it like the phone's own player
 * (notification, lock screen, the app), while the device fetches each song from Navidrome itself as
 * MP3 320, which every DLNA device plays, or as the original file (FLAC…) when that's turned on for the device and it
 * lists the format. It's asked every second where it is (four times a second near a
 * song's end).
 *
 * The next song is handed over ahead (SetNextAVTransportURI) so a device that supports it moves on by itself,
 * without a gap (beta testers #23). Devices that refuse it get the next song when the current one stops.
 */
@OptIn(UnstableApi::class)
class DlnaPlayer(private val device: DlnaDevice, private val api: MusicSource) : SimpleBasePlayer(Looper.getMainLooper()) {
    /** The TV or speaker's name, for the log and the screen. */
    val name: String get() = device.name
    val deviceId: String get() = device.id

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    /** Commands go to the device one at a time, in order. */
    private val commands = Mutex()

    private var items: List<MediaItem> = emptyList()
    private var index = 0
    private var playWhenReady = false
    private var state = Player.STATE_IDLE
    private var repeat = Player.REPEAT_MODE_OFF
    private var positionMs = 0L
    private var durationMs = C.TIME_UNSET
    private var playingSince = 0L
    private var volume = 50
    /** The device has reported PLAYING for the current song, so a later STOPPED means it ended. */
    private var sawPlaying = false
    private var loadedIndex = -1
    private var failures = 0
    private var poll: Job? = null
    /** The address of the song the device was given last, to notice when it moves on by itself. */
    private var currentUrl: String? = null
    /** The song handed to the device ahead (queue place, song id, address); null when none. */
    private var sentNext: Triple<Int, String, String>? = null
    /** Whether the device takes the next song ahead: null until it's tried. */
    private var takesNext: Boolean? = null
    private var nextSending = false

    private val available = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP, Player.COMMAND_RELEASE,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_DEFAULT_POSITION, Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_METADATA,
            Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_CHANGE_MEDIA_ITEMS, Player.COMMAND_GET_DEVICE_VOLUME,
        )
        .apply {
            if (device.renderingUrl != null) {
                addAll(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS, Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
            }
        }
        .build()

    private val deviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMinVolume(0).setMaxVolume(100).build()

    init {
        device.renderingUrl?.let { url ->
            scope.launch {
                runCatching { Dlna.soap(url, Upnp.RENDERING_CONTROL, "GetVolume", "InstanceID" to "0", "Channel" to "Master") }
                    .getOrNull()?.let { Upnp.value(it, "CurrentVolume")?.toIntOrNull() }
                    ?.let { volume = it.coerceIn(0, 100); invalidateState() }
            }
        }
    }

    override fun getState(): SimpleBasePlayer.State {
        val playlist = items.mapIndexed { i, item ->
            SimpleBasePlayer.MediaItemData.Builder("$i-${item.mediaId}")
                .setMediaItem(item)
                .setDurationUs(if (i == index && durationMs != C.TIME_UNSET) durationMs * 1000 else songDurationUs(item))
                .setIsSeekable(true)
                .build()
        }
        val shownState = if (playlist.isEmpty() && state != Player.STATE_ENDED) Player.STATE_IDLE else state
        val position = if (state == Player.STATE_READY && playWhenReady && playingSince > 0) {
            SimpleBasePlayer.PositionSupplier.getExtrapolating(currentPositionNow(), 1f)
        } else {
            SimpleBasePlayer.PositionSupplier.getConstant(positionMs)
        }
        return SimpleBasePlayer.State.Builder()
            .setAvailableCommands(available)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(shownState)
            .setRepeatMode(repeat)
            .setPlaylist(playlist)
            .apply { if (playlist.isNotEmpty()) setCurrentMediaItemIndex(index.coerceIn(0, playlist.size - 1)) }
            .setContentPositionMs(position)
            .setDeviceInfo(deviceInfo)
            .setDeviceVolume(volume)
            .build()
    }

    // ---- What the session asks for ----

    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        items = mediaItems.toList()
        index = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        positionMs = if (startPositionMs == C.TIME_UNSET) 0L else startPositionMs
        loadedIndex = -1
        if (state != Player.STATE_IDLE) load(play = playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        if (items.isNotEmpty() && loadedIndex != index) load(play = playWhenReady)
        else if (items.isNotEmpty()) state = Player.STATE_READY
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val was = this.playWhenReady
        this.playWhenReady = playWhenReady
        if (was == playWhenReady || state == Player.STATE_IDLE) return Futures.immediateVoidFuture()
        if (state == Player.STATE_ENDED && playWhenReady) {
            index = 0
            positionMs = 0
            load(play = true)
        } else if (loadedIndex != index) {
            load(play = playWhenReady)
        } else if (playWhenReady) {
            playingSince = System.currentTimeMillis()
            send("Play") { transport("Play", "InstanceID" to "0", "Speed" to "1") }
        } else {
            positionMs = currentPositionNow()
            playingSince = 0
            send("Pause") { transport("Pause", "InstanceID" to "0") }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (items.isEmpty()) return Futures.immediateVoidFuture()
        val target = mediaItemIndex.coerceIn(0, items.size - 1)
        val to = if (positionMs == C.TIME_UNSET) 0L else positionMs.coerceAtLeast(0)
        this.positionMs = to
        if (target != index || loadedIndex != index) {
            index = target
            load(play = playWhenReady)
        } else {
            if (playWhenReady) playingSince = System.currentTimeMillis()
            send("Seek") { transport("Seek", "InstanceID" to "0", "Unit" to "REL_TIME", "Target" to Upnp.time(to)) }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        positionMs = currentPositionNow()
        playWhenReady = false
        playingSince = 0
        state = Player.STATE_IDLE
        loadedIndex = -1
        sentNext = null
        poll?.cancel()
        send("Stop") { transport("Stop", "InstanceID" to "0") }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        poll?.cancel()
        val transportUrl = device.transportUrl
        // Stopping the TV is sent even though this player is going away.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { Dlna.soap(transportUrl, Upnp.AV_TRANSPORT, "Stop", "InstanceID" to "0") }
        }
        scope.cancel()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        repeat = repeatMode
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: MutableList<MediaItem>): ListenableFuture<*> {
        val at = index.coerceIn(0, items.size)
        items = items.toMutableList().apply { addAll(at, mediaItems) }
        if (items.size == mediaItems.size) {
            // Added to an empty queue: starts from the first.
            this.index = 0
            loadedIndex = -1
        } else if (at <= this.index) {
            this.index += mediaItems.size
            if (loadedIndex >= 0) loadedIndex += mediaItems.size
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        val removed = toIndex - fromIndex
        val wasCurrent = index in fromIndex until toIndex
        items = items.toMutableList().apply { subList(fromIndex, toIndex).clear() }
        when {
            items.isEmpty() -> {
                index = 0
                state = Player.STATE_ENDED
                playingSince = 0
                loadedIndex = -1
                send("Stop") { transport("Stop", "InstanceID" to "0") }
            }
            wasCurrent -> {
                index = fromIndex.coerceAtMost(items.size - 1)
                positionMs = 0
                load(play = playWhenReady)
            }
            index >= toIndex -> {
                index -= removed
                loadedIndex = index
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        val current = items.getOrNull(index)
        val list = items.toMutableList()
        val moving = list.subList(fromIndex, toIndex).toList()
        list.subList(fromIndex, toIndex).clear()
        list.addAll(newIndex.coerceIn(0, list.size), moving)
        items = list
        // The same song keeps playing; only its place in the queue changed.
        val at = list.indexOfFirst { it === current }
        if (at >= 0) {
            if (loadedIndex == index) loadedIndex = at
            index = at
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleReplaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>): ListenableFuture<*> {
        val list = items.toMutableList()
        list.subList(fromIndex, toIndex).clear()
        list.addAll(fromIndex, mediaItems)
        items = list
        index = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        return Futures.immediateVoidFuture()
    }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        setVolume(deviceVolume)
        return Futures.immediateVoidFuture()
    }

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        setVolume(volume + VOLUME_STEP)
        return Futures.immediateVoidFuture()
    }

    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        setVolume(volume - VOLUME_STEP)
        return Futures.immediateVoidFuture()
    }

    // ---- Talking to the device ----

    /**
     * Sends the current song to the device and, when [play], starts it at [positionMs]. [stopFirst] is false
     * when the device has just stopped by itself at a song's end, which saves a round trip.
     */
    private fun load(play: Boolean, stopFirst: Boolean = true) {
        val item = items.getOrNull(index) ?: return
        val loadIndex = index
        val startAt = positionMs
        loadedIndex = index
        sawPlaying = false
        durationMs = C.TIME_UNSET
        playingSince = 0
        state = Player.STATE_BUFFERING
        AppLog.i("DLNA", "Song ${item.mediaId} on ${device.name}" + if (startAt > 0) " from ${startAt / 1000}s" else "")
        // A new song replaces what the device had queued after the old one.
        sentNext = null
        send("Load") {
            val (url, metadata) = streamFor(item)
            currentUrl = url
            // Some devices refuse a new song while one plays.
            if (stopFirst) runCatching { transport("Stop", "InstanceID" to "0") }
            transport("SetAVTransportURI", "InstanceID" to "0", "CurrentURI" to url, "CurrentURIMetaData" to metadata)
            if (startAt > 1000) {
                runCatching { transport("Seek", "InstanceID" to "0", "Unit" to "REL_TIME", "Target" to Upnp.time(startAt)) }
            }
            if (play) transport("Play", "InstanceID" to "0", "Speed" to "1")
            if (loadIndex == index) {
                state = Player.STATE_READY
                if (play) playingSince = System.currentTimeMillis()
                Dlna.connected()
            }
        }
        startPolling()
    }

    /**
     * The address the device gets for [item] and its DIDL description: the original file when that's turned on
     * for this device (the cog in the device list) and it lists the file's format, else MP3 320 (beta testers #23).
     */
    private suspend fun streamFor(item: MediaItem): Pair<String, String> {
        val song = item.toSong()
        val raw = api.streamUrl(item.mediaId)
        var url = Streaming.forCast(Uri.parse(raw)).toString()
        var mime = Streaming.CAST_MIME
        if (device.id in Dlna.original.value) {
            val type = Dlna.originalType(raw)
            if (type != null && DlnaFormats.accepts(device.formats, type)) {
                url = raw
                mime = type
                if (loggedOriginal.add(type)) AppLog.i("DLNA", "${device.name} gets original files ($type)")
            } else if (loggedOriginal.add("no:$type")) {
                AppLog.i("DLNA", "${device.name} doesn't list ${type ?: "the file's format"}: MP3 320 instead")
            }
        }
        val cover = song.cover?.let { api.coverUrl(it, COVER_PX) }
        return url to Upnp.didl(item.mediaId, song.title, song.artist, song.album, url, mime, song.durationSec, cover)
    }

    /** Formats already logged, so each shows once. */
    private val loggedOriginal = HashSet<String>()

    private fun setVolume(to: Int) {
        val url = device.renderingUrl ?: return
        volume = to.coerceIn(0, 100)
        val v = volume
        send("Volume") { Dlna.soap(url, Upnp.RENDERING_CONTROL, "SetVolume", "InstanceID" to "0", "Channel" to "Master", "DesiredVolume" to "$v") }
    }

    private suspend fun transport(action: String, vararg args: Pair<String, String>): String =
        Dlna.soap(device.transportUrl, Upnp.AV_TRANSPORT, action, *args)

    /** Runs a command on the device in order with the others, then refreshes what the session shows. */
    private fun send(what: String, block: suspend () -> Unit) {
        invalidateState()
        scope.launch {
            try {
                commands.withLock { block() }
                failures = 0
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLog.w("DLNA", "$what on ${device.name} failed: ${e.message}")
                failed(e.message ?: e.javaClass.simpleName)
            }
            invalidateState()
        }
    }

    /** Asks the device every second what it's doing; moves to the next song when one ends. */
    private fun startPolling() {
        if (poll?.isActive == true) return
        poll = scope.launch {
            while (isActive) {
                delay(DlnaQueue.pollDelayMs(state == Player.STATE_READY && playWhenReady && playingSince > 0, currentPositionNow(), durationMs))
                if (state == Player.STATE_IDLE) continue
                val result = runCatching {
                    commands.withLock {
                        val info = transport("GetTransportInfo", "InstanceID" to "0")
                        val position = transport("GetPositionInfo", "InstanceID" to "0")
                        Upnp.value(info, "CurrentTransportState") to position
                    }
                }
                val answer = result.getOrNull()
                if (answer == null) {
                    val e = result.exceptionOrNull()
                    failed(e?.message ?: e?.javaClass?.simpleName ?: "no answer")
                } else {
                    failures = 0
                    val (transportState, position) = answer
                    onStatus(
                        transportState,
                        Upnp.parseTime(Upnp.value(position, "RelTime")),
                        Upnp.parseTime(Upnp.value(position, "TrackDuration")),
                        Upnp.value(position, "TrackURI"),
                    )
                }
            }
        }
    }

    private fun onStatus(transportState: String?, relMs: Long?, trackMs: Long?, trackUri: String?) {
        if (loadedIndex != index || state == Player.STATE_BUFFERING) return
        if (movedOnByItself(trackUri, relMs, trackMs)) return
        trackMs?.takeIf { it > 0 }?.let { durationMs = it }
        when (transportState) {
            "PLAYING" -> {
                sawPlaying = true
                relMs?.let { positionMs = it }
                playingSince = System.currentTimeMillis()
                state = Player.STATE_READY
                queueNext()
            }
            "PAUSED_PLAYBACK" -> {
                relMs?.let { positionMs = it }
                playingSince = 0
            }
            "STOPPED", "NO_MEDIA_PRESENT" -> if (sawPlaying && playWhenReady) {
                // The song ended on the device and it didn't move on by itself: on to the next one.
                sawPlaying = false
                if (sentNext != null) AppLog.i("DLNA", "${device.name} stopped instead of playing the next song it was given")
                next()
                return
            }
        }
        invalidateState()
    }

    private fun next() {
        val nextIndex = DlnaQueue.nextIndex(index, items.size, repeat)
        if (nextIndex < 0) {
            state = Player.STATE_ENDED
            playingSince = 0
            positionMs = 0
            invalidateState()
            return
        }
        index = nextIndex
        positionMs = 0
        load(play = true, stopFirst = false)
        invalidateState()
    }

    /**
     * The device started the song it was given ahead: the app follows without sending anything. Returns
     * true when that happened.
     */
    private fun movedOnByItself(trackUri: String?, relMs: Long?, trackMs: Long?): Boolean {
        val (nextIndex, nextId, nextUrl) = sentNext ?: return false
        if (!DlnaQueue.sameStream(trackUri, nextUrl) || DlnaQueue.sameStream(trackUri, currentUrl)) return false
        // The queue may have changed since: follow the song, wherever it is now.
        val at = if (items.getOrNull(nextIndex)?.mediaId == nextId) nextIndex else items.indexOfFirst { it.mediaId == nextId }
        sentNext = null
        currentUrl = nextUrl
        if (at < 0) {
            // Removed from the queue meanwhile: play what's now next instead.
            AppLog.i("DLNA", "${device.name} moved on to a song no longer in the queue")
            next()
            return true
        }
        AppLog.i("DLNA", "Song $nextId on ${device.name}, without a gap")
        index = at
        loadedIndex = at
        positionMs = relMs ?: 0L
        durationMs = trackMs?.takeIf { it > 0 } ?: C.TIME_UNSET
        sawPlaying = true
        playingSince = System.currentTimeMillis()
        state = Player.STATE_READY
        invalidateState()
        return true
    }

    /**
     * Hands the device the song after this one, so it can start it without a gap; again when the queue or
     * repeat changed which song that is, and an empty one when the queue ends here.
     */
    private fun queueNext() {
        if (takesNext == false || nextSending || !playWhenReady) return
        // Repeating one song: the same address twice can't be told apart, so it's sent again at the end instead.
        val nextIndex = if (repeat == Player.REPEAT_MODE_ONE) -1 else DlnaQueue.nextIndex(index, items.size, repeat)
        val item = items.getOrNull(nextIndex)
        val sent = sentNext
        if (item == null) {
            if (sent == null) return
            sentNext = null
            nextSending = true
            scope.launch {
                runCatching { commands.withLock { transport("SetNextAVTransportURI", "InstanceID" to "0", "NextURI" to "", "NextURIMetaData" to "") } }
                nextSending = false
            }
            return
        }
        if (sent != null && sent.first == nextIndex && sent.second == item.mediaId) return
        val forIndex = index
        nextSending = true
        scope.launch {
            var url = ""
            val result = runCatching {
                val (address, metadata) = streamFor(item)
                url = address
                // Repeat all on a one-song queue: the same address again, which the device can't be told apart by.
                if (!DlnaQueue.sameStream(address, currentUrl)) {
                    commands.withLock { transport("SetNextAVTransportURI", "InstanceID" to "0", "NextURI" to address, "NextURIMetaData" to metadata) }
                }
            }
            nextSending = false
            if (result.isSuccess && DlnaQueue.sameStream(url, currentUrl)) return@launch
            val error = result.exceptionOrNull()
            when {
                error is kotlinx.coroutines.CancellationException -> throw error
                error == null -> {
                    if (takesNext != true) AppLog.i("DLNA", "${device.name} takes the next song ahead: songs follow without a gap")
                    takesNext = true
                    // Only kept when the device is still on the same song.
                    if (forIndex == index) sentNext = Triple(nextIndex, item.mediaId, url)
                    AppLog.d("DLNA", "Next song ${item.mediaId} handed to ${device.name}")
                }
                DlnaQueue.nextRefused(error?.message) -> {
                    takesNext = false
                    AppLog.i("DLNA", "${device.name} can't take the next song ahead (${error?.message}): songs change with a short gap")
                }
                else -> AppLog.w("DLNA", "Next song to ${device.name} failed: ${error?.message}")
            }
        }
    }

    private fun failed(why: String) {
        failures++
        // A few missed answers happen on busy Wi-Fi; many in a row means the device is gone.
        if (failures >= MAX_FAILURES) {
            poll?.cancel()
            main.post { Dlna.lost(device, why) }
        }
    }

    private fun currentPositionNow(): Long =
        if (playingSince > 0) positionMs + (System.currentTimeMillis() - playingSince) else positionMs

    private fun songDurationUs(item: MediaItem): Long {
        val seconds = item.toSong().durationSec
        return if (seconds > 0) seconds * 1_000_000L else C.TIME_UNSET
    }

    private companion object {
        const val COVER_PX = 800
        const val VOLUME_STEP = 5
        const val MAX_FAILURES = 8
    }
}
