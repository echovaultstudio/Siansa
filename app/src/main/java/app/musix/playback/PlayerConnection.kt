package app.musix.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import app.musix.data.Song
import app.musix.log.AppLog
import app.musix.net.SubsonicApi
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
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
)

/** The app's side of playback: a MediaController talking to [PlaybackService]. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerConnection(
    private val context: Context,
    private val api: SubsonicApi,
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

    /** Called when a new list starts playing (anything other than a radio topping itself up). */
    var onNewQueue: (() -> Unit)? = null

    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        onNewQueue?.invoke()
        SongRegistry.register(songs)
        val items = songs.map { it.toMediaItem(api) }
        val start = if (shuffle) Random.nextInt(songs.size) else startIndex.coerceIn(0, songs.size - 1)
        withController { c ->
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(items, start, 0L)
            c.prepare()
            c.play()
        }
    }

    fun playNext(song: Song) {
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

    fun addToQueue(song: Song) {
        SongRegistry.register(listOf(song))
        withController { c ->
            c.addMediaItem(song.toMediaItem(api))
            if (c.mediaItemCount == 1) {
                c.prepare()
                c.play()
            }
        }
    }

    /** Keeps the current song playing and swaps everything after it for [songs]. */
    fun replaceUpcoming(songs: List<Song>) {
        SongRegistry.register(songs)
        withController { c ->
            val keep = c.currentMediaItemIndex + 1
            if (c.mediaItemCount > keep) c.removeMediaItems(keep, c.mediaItemCount)
            c.shuffleModeEnabled = false
            c.addMediaItems(songs.map { it.toMediaItem(api) })
        }
    }

    fun addAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        SongRegistry.register(songs)
        withController { c -> c.addMediaItems(songs.map { it.toMediaItem(api) }) }
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

    /** Stops playback and empties the queue (long-press on play). */
    fun stop() {
        onNewQueue?.invoke()
        withController { c ->
            c.stop()
            c.clearMediaItems()
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
    }

    /** Plays the current song (and the next ones) at [kbps] Opus, or 0 for the original file. */
    fun setQuality(kbps: Int) = withController { c ->
        AppLog.i("Player", "Quality picked in now playing: ${StreamQuality.choiceLabel(kbps)}")
        val args = Bundle().apply { putInt(StreamQuality.EXTRA_KBPS, kbps) }
        c.sendCustomCommand(SessionCommand(StreamQuality.ACTION_SET_QUALITY, Bundle.EMPTY), args)
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L

    /** The current song, where it is and how long it is, read together so they always belong to the same song. */
    data class Progress(val mediaId: String?, val positionMs: Long, val durationMs: Long, val playing: Boolean)

    fun progress(): Progress {
        val c = controller ?: return Progress(null, 0, 0, false)
        val item = c.currentMediaItem
        val duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            ?: ((item?.toSong()?.durationSec ?: 0) * 1000L)
        return Progress(item?.mediaId, c.currentPosition, duration, c.isPlaying)
    }

    private fun refresh() {
        val c = controller ?: return
        val queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toSong() }
        val index = c.currentMediaItemIndex
        val current = queue.getOrNull(index)
        val duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            ?: ((current?.durationSec ?: 0) * 1000L)
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
        )
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
