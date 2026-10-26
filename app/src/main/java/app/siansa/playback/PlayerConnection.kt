package app.siansa.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.siansa.data.Song
import app.siansa.log.AppLog
import app.siansa.playback.dlna.Dlna
import app.siansa.net.MusicSource
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import app.siansa.net.offlinePick
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

data class PlayerState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val hasMedia: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val index: Int = 0,
    /** Waiting for data before it can play on. */
    val buffering: Boolean = false,
    /** The format of what's playing ("FLAC", "MP3", "OPUS"...), or null until the player knows. */
    val codec: String? = null,
    /** Opus kbit/s while Navidrome converts the current song, or 0 when the original file plays. */
    val transcodeKbps: Int = 0,
    /** Songs still to come after this one, in the order they'll play (shuffle included), for keep playing (issue #337). */
    val left: Int = 0,
    /** Positions in [queue] of songs keep playing or a song radio added by itself (issue #337). */
    val auto: Set<Int> = emptySet(),
)

/** The app's side of playback: a MediaController talking to [PlaybackService]. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerConnection(
    private val context: Context,
    private val api: MusicSource,
) {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                // The service says here whether the current song is converted to Opus.
                override fun onExtrasChanged(controller: MediaController, extras: Bundle) = refresh()
            })
            .buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull()
            if (c == null) {
                future = null
                return@addListener
            }
            controller = c
            c.addListener(listener)
            refresh()
            val queued = pending.toList()
            pending.clear()
            queued.forEach { it(c) }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null && c.isConnected) {
            block(c)
            return
        }
        if (c != null) {
            // The service went away (e.g. after the app was swiped off); build a fresh connection.
            c.removeListener(listener)
            controller = null
            future = null
        }
        pending += block
        connect()
    }

    /**
     * Goes up each time a new list starts playing or playback is stopped (beta report 95a30366), so songs that
     * arrive later for an older list (an artist radio's second batch) can tell they're too late and are dropped.
     */
    @Volatile var queueGeneration: Long = 0L
        private set

    /** Called when a new list starts playing (anything other than a radio topping itself up). */
    var onNewQueue: (() -> Unit)? = null

    private val _newQueues = MutableSharedFlow<Song>(extraBufferCapacity = 4)
    /** The first song of each new list that starts playing, so the sleep timer's wind down can follow it (issue #330). */
    val newQueues: SharedFlow<Song> = _newQueues.asSharedFlow()

    /** True while music can only come from the phone (no network, or the server doesn't answer). */
    var isOffline: () -> Boolean = { false }
    /** True while "Only downloaded music" is on (issue #141): then too only downloaded songs play. */
    var onlyDownloads: () -> Boolean = { false }
    var isDownloaded: (String) -> Boolean = { false }

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Short messages for the screen, e.g. why a song didn't play while offline. */
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    /** Why only downloaded songs can play right now, or null when anything can. */
    private fun limited(): String? = playLimit(isOffline(), onlyDownloads())

    fun notice(message: String) {
        AppLog.i("Player", message)
        _notices.tryEmit(message)
    }

    /** Pauses the music; a book starting does this, so the two never play at once (issue #521). */
    fun pause() = withController { c -> if (c.isPlaying) c.pause() }

    /** Plays [songs] from [startIndex]; [startMs] starts that song part-way (a mix carried on, issue #269). */
    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false, startMs: Long = 0L) {
        if (songs.isEmpty()) return
        val why = limited()
        if (why != null) {
            // Offline: only downloaded songs can play. Say so instead of a play button that does nothing.
            val pick = offlinePick(songs, startIndex.coerceIn(0, songs.size - 1), isDownloaded)
            if (pick.songs.isEmpty()) {
                notice(if (songs.size == 1) "$why and this song isn't downloaded" else "$why and none of these songs are downloaded")
                return
            }
            if (pick.skipped && !shuffle) notice("$why: playing only the downloaded songs")
            if (pick.songs.size < songs.size) {
                return playNow(pick.songs, pick.start, shuffle)
            }
        }
        playNow(songs, startIndex, shuffle, startMs)
    }

    private fun playNow(songs: List<Song>, startIndex: Int, shuffle: Boolean, startMs: Long = 0L) {
        queueGeneration++
        onNewQueue?.invoke()
        SongRegistry.register(songs)
        val items = songs.map { it.toMediaItem(api) }
        val start = if (shuffle) Random.nextInt(songs.size) else startIndex.coerceIn(0, songs.size - 1)
        withController { c ->
            c.shuffleModeEnabled = shuffle
            // Repeat belongs to the list it was set on: a new list starts with it off, like shuffle. The player
            // service outlives the app, so a repeat set hours ago otherwise showed as on by default (beta #42).
            if (QueueRules.repeatForNewQueue(c.repeatMode) != c.repeatMode) {
                AppLog.i("Player", "Repeat: off for the new list")
                c.repeatMode = QueueRules.repeatForNewQueue(c.repeatMode)
            }
            c.setMediaItems(items, start, if (shuffle) 0L else startMs.coerceAtLeast(0L))
            c.prepare()
            c.play()
        }
        _newQueues.tryEmit(songs[start])
    }

    fun playNext(song: Song) {
        limited()?.let { why -> if (!isDownloaded(song.id)) return notice("$why and this song isn't downloaded") }
        SongRegistry.register(listOf(song))
        withController { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItem(song.toMediaItem(api))
                c.prepare()
                c.play()
            } else {
                c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem(api))
            }
        }
    }

    /**
     * Spotify's "Add to queue": the song plays after the current one and after songs queued before it,
     * ahead of the rest of the album or playlist. With nothing playing, it starts playing.
     */
    fun addToQueue(song: Song) {
        limited()?.let { why -> if (!isDownloaded(song.id)) return notice("$why and this song isn't downloaded") }
        SongRegistry.register(listOf(song))
        withController { c ->
            val item = song.toMediaItem(api, queued = true)
            if (c.mediaItemCount == 0) {
                c.setMediaItem(item)
                c.prepare()
                c.play()
                return@withController
            }
            val at = queueInsertIndex(c.currentMediaItemIndex, c.mediaItemCount) { c.getMediaItemAt(it).isQueued() }
            c.addMediaItem(at, item)
            AppLog.d("Player", "Added ${song.id} to the queue at ${at + 1} of ${c.mediaItemCount}")
        }
    }

    /** Keeps the current song playing and swaps everything after it for [songs]. */
    /** [auto]: the songs were picked by a song radio, not chosen, and the queue marks them (issue #337). */
    fun replaceUpcoming(songs: List<Song>, auto: Boolean = false) {
        SongRegistry.register(songs)
        withController { c ->
            val keep = c.currentMediaItemIndex + 1
            if (c.mediaItemCount > keep) c.removeMediaItems(keep, c.mediaItemCount)
            c.shuffleModeEnabled = false
            c.addMediaItems(songs.map { it.toMediaItem(api, auto = auto) })
        }
    }

    /** [auto]: the songs were picked by a song radio, not chosen, and the queue marks them (issue #337). */
    fun addAll(songs: List<Song>, auto: Boolean = false) {
        if (songs.isEmpty()) return
        SongRegistry.register(songs)
        withController { c -> c.addMediaItems(songs.map { it.toMediaItem(api, auto = auto) }) }
    }

    /**
     * Adds [songs] after the queue for keep playing (issue #337). If the queue already ran out (the last song
     * ended before they were found), the first of them starts playing, unless playback was paused (the sleep timer).
     */
    fun continueWith(songs: List<Song>) {
        if (songs.isEmpty()) return
        SongRegistry.register(songs)
        withController { c ->
            val ended = c.playbackState == Player.STATE_ENDED && c.playWhenReady
            val first = c.mediaItemCount
            // Keep playing picked these itself: the queue marks them (issue #337).
            c.addMediaItems(songs.map { it.toMediaItem(api, auto = true) })
            if (ended) {
                c.seekTo(first, 0L)
                c.prepare()
                c.play()
            }
        }
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0L)
            c.play()
        }
    }

    /**
     * Stops playback and empties the queue (long-press on play). While casting, casting ends too, so the next song
     * picked plays on the phone (issue #491).
     */
    fun stop() {
        queueGeneration++
        onNewQueue?.invoke()
        withController { c ->
            c.stop()
            c.clearMediaItems()
        }
        val cast = Cast.status.value
        val onTv = cast.device != null || cast.connecting
        val onDlna = Dlna.selected.value != null
        if (onTv || onDlna) {
            AppLog.i("Player", "Hold to stop: casting ends too")
            StopCasting.requested = true
            if (onDlna) Dlna.select(null)
            if (onTv) Cast.disconnect()
        }
    }

    /** The song before this one, even when this one has been playing for a while (cover swipe). */
    fun previousTrack() = withController { c ->
        if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem() else c.seekTo(0L)
    }

    fun hasNext(): Boolean = controller?.hasNextMediaItem() == true

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    fun skipTo(index: Int) = withController { c ->
        c.seekTo(index, 0L)
        c.play()
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        // Shown at once rather than when the service reports back, so the button answers the tap (issue #370).
        refresh()
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L

    /** The phone player's volume (levelling × crossfade × sleep timer), for the emulator tests. */
    internal fun volumeForTest(): Float = controller?.volume ?: 1f

    /** The current song, where it is and how long it is, read together so they always belong to the same song. */
    data class Progress(val mediaId: String?, val positionMs: Long, val durationMs: Long, val playing: Boolean)

    fun progress(): Progress {
        val c = controller ?: return Progress(null, 0, 0, false)
        val item = c.currentMediaItem
        val duration = songDurationMs(c.duration, item?.toSong()?.durationSec ?: 0)
        return Progress(item?.mediaId, c.currentPosition, duration, c.isPlaying)
    }

    private fun refresh() {
        val c = controller ?: return
        val queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toSong() }
        val index = c.currentMediaItemIndex
        val current = queue.getOrNull(index)
        val duration = songDurationMs(c.duration, current?.durationSec ?: 0)
        _state.value = PlayerState(
            current = current,
            isPlaying = c.isPlaying,
            hasMedia = c.mediaItemCount > 0,
            durationMs = duration,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = queue,
            index = index,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            codec = audioCodec(c.currentTracks),
            transcodeKbps = c.sessionExtras
                .takeIf { it.getString(StreamQuality.EXTRA_SONG_ID) == current?.id }
                ?.getInt(StreamQuality.EXTRA_KBPS, 0) ?: 0,
            left = leftInPlayOrder(c),
            auto = (0 until c.mediaItemCount).filter { c.getMediaItemAt(it).isAuto() }.toSet(),
        )
    }

    /** How many songs come after the current one in play order, without repeat; shuffle follows its own order. */
    private fun leftInPlayOrder(c: MediaController): Int {
        val timeline = c.currentTimeline
        if (timeline.isEmpty || c.currentMediaItemIndex == C.INDEX_UNSET) return 0
        var index = c.currentMediaItemIndex
        var count = 0
        while (count < timeline.windowCount) {
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            if (index == C.INDEX_UNSET) break
            count++
        }
        return count
    }

    private fun audioCodec(tracks: Tracks): String? {
        val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            ?: tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
            ?: return null
        val index = (0 until group.length).firstOrNull { group.isTrackSelected(it) } ?: 0
        val format = group.getTrackFormat(index)
        return StreamQuality.codecName(format.sampleMimeType, format.containerMimeType)
    }
}

/** Why only downloaded songs can play: offline, or "Only downloaded music" is on. Null when anything can. */
internal fun playLimit(offline: Boolean, onlyDownloads: Boolean): String? = when {
    offline -> "You're offline"
    onlyDownloads -> "Only downloaded music is on"
    else -> null
}

/**
 * How long the playing song is, in ms: what the player read from the file, unless the server's length differs from it
 * by more than half. Some .m4a files with E-AC3 (Dolby) sound carry a wrong length in their header, e.g. 34 minutes for
 * a 4-minute song (beta report 7552a06d), which made the seek bar useless; the server's length is right for those.
 */
fun songDurationMs(playerMs: Long, serverSec: Int): Long {
    val server = serverSec.coerceAtLeast(0) * 1000L
    val player = if (playerMs == C.TIME_UNSET || playerMs <= 0) 0L else playerMs
    return when {
        player == 0L -> server
        server == 0L -> player
        player > server * 3 / 2 || player < server / 2 -> server
        else -> player
    }
}
