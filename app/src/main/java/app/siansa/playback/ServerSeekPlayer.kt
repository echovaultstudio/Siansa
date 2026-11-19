package app.siansa.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import app.siansa.log.AppLog

/**
 * The phone's player, with seeking in converted (Opus) streams done by the server (issue #162; Emby and Jellyfin, #1204).
 *
 * The server converts a song while it sends it, so the player can't jump inside that stream: it has to read
 * everything up to the new spot first (on a slow connection that's minutes, and the song looks stuck). Instead,
 * a seek in a converted song asks the server for a new conversion starting there (Navidrome's `timeOffset`, Emby's
 * and Jellyfin's `StartTimeTicks`, in whole seconds), and this player adds where that stream starts to every
 * position it reports, so the seek bar, the lock screen and scrobbling see the song's real position. Originals, downloads and cached songs seek as usual.
 *
 * [converted] says whether a song streams converted now (and isn't played from a download).
 */
@OptIn(UnstableApi::class)
class ServerSeekPlayer(
    private val exo: ExoPlayer,
    private val converted: (String) -> Boolean,
    private val durationMsOf: (String) -> Long?,
) : ForwardingPlayer(exo) {

    init {
        // A song left behind with a stream that starts part-way in plays from its start when it comes round again.
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                resetOthers()
                announceSeek()
            }
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) = announceSeek()
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = announceSeek()
        })
    }

    /** Where the current stream starts in its song (0 unless the server was asked to start later). */
    private fun offset(): Long = Streaming.offsetMs(exo.currentMediaItem?.localConfiguration?.uri)

    override fun getCurrentPosition(): Long = exo.currentPosition + offset()
    override fun getContentPosition(): Long = exo.contentPosition + offset()
    override fun getBufferedPosition(): Long = exo.bufferedPosition.let { if (it == C.TIME_UNSET) it else it + offset() }
    override fun getContentBufferedPosition(): Long = exo.contentBufferedPosition.let { if (it == C.TIME_UNSET) it else it + offset() }

    override fun getDuration(): Long {
        val offset = offset()
        val id = exo.currentMediaItem?.mediaId
        // A converted stream has no length the player can see: the song's own length, from the library.
        val converted = id != null && converted(id)
        if (offset == 0L && !converted) return exo.duration
        return id?.let(durationMsOf) ?: exo.duration.let { if (it == C.TIME_UNSET) it else it + offset }
    }

    override fun getContentDuration(): Long = duration

    // The player calls a stream without a length live, and a converted song is one. For a live stream Media3 hands
    // no length and no position to the media session, so a car, the lock screen and the notification showed the
    // song without its progress bar (issue #1039). It's a song with a length ([getDuration]), not a broadcast.
    override fun isCurrentMediaItemLive(): Boolean =
        live(exo.isCurrentMediaItemLive, exo.currentMediaItem?.mediaId?.let(converted) == true)

    // A converted stream has no length, so the player itself says it can't seek in it, and the app's controller
    // would then drop a seek before it got here. Seeking is done by the server, so it's offered.
    private fun withSeek(commands: Player.Commands): Player.Commands {
        val id = exo.currentMediaItem?.mediaId ?: return commands
        if (!converted(id)) return commands
        return commands.buildUpon()
            .addAll(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD, Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
            .build()
    }

    override fun getAvailableCommands(): Player.Commands = withSeek(exo.availableCommands)
    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    // Listeners get every event from the player unchanged. Right after the player announces what can be done
    // (which never includes seeking in a converted stream), they're told once more, with seeking. (Wrapping the
    // listeners instead lost their other events: Kotlin's delegation skips Java's default methods.)
    private val outer = java.util.concurrent.CopyOnWriteArrayList<Player.Listener>()
    private val handler = android.os.Handler(exo.applicationLooper)

    override fun addListener(listener: Player.Listener) {
        outer += listener
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        outer -= listener
        super.removeListener(listener)
    }

    private fun announceSeek() {
        handler.post {
            val commands = availableCommands
            if (commands != exo.availableCommands) outer.forEach { it.onAvailableCommandsChanged(commands) }
        }
    }

    override fun isCurrentMediaItemSeekable(): Boolean =
        exo.currentMediaItem?.mediaId?.let(converted) == true || exo.isCurrentMediaItemSeekable

    // Every swap of one song (a seek in a converted song, another quality, Opus when the phone can't play the
    // original) keeps the player in that song and the shuffled order as it was ([swap], issue #1202).
    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
        if (index !in 0 until exo.mediaItemCount) return exo.replaceMediaItem(index, mediaItem)
        swap(index, mediaItem)
    }

    /**
     * Shuffle turned on shuffles the list (an album, a playlist) after the song playing, with the songs Keep playing
     * added still after it (issue #1238): the player's own order mixed them in, and could put songs of the album
     * before the one playing, where they count as played.
     */
    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        if (shuffleModeEnabled && !exo.shuffleModeEnabled && exo.mediaItemCount > 1) {
            val order = QueueRules.listFirstShuffle(exo.mediaItemCount, exo.currentMediaItemIndex, { exo.getMediaItemAt(it).isAuto() })
            exo.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order, System.nanoTime()))
            AppLog.d("Player", "Shuffle on: ${order.count { !exo.getMediaItemAt(it).isAuto() }} songs of the list shuffled, the songs Keep playing added after them")
        }
        exo.shuffleModeEnabled = shuffleModeEnabled
    }

    // Songs added at the end while shuffled (Keep playing's) play after the rest, in their order, instead of at
    // random places among the songs still to come (issue #1238).
    override fun addMediaItems(mediaItems: MutableList<MediaItem>) = addMediaItems(exo.mediaItemCount, mediaItems)

    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
        val count = exo.mediaItemCount
        if (!exo.shuffleModeEnabled || index != count || count == 0 || mediaItems.isEmpty()) return exo.addMediaItems(index, mediaItems)
        val shuffled = shuffledOf(exo.shuffleOrder)
        exo.addMediaItems(index, mediaItems)
        exo.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(QueueRules.withAppended(shuffled, count, mediaItems.size), System.nanoTime()))
    }

    override fun seekTo(positionMs: Long) = seekTo(exo.currentMediaItemIndex, positionMs)

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex !in 0 until exo.mediaItemCount) return exo.seekTo(mediaItemIndex, positionMs)
        val item = exo.getMediaItemAt(mediaItemIndex)
        val uri = item.localConfiguration?.uri
        val serverSide = uri != null && converted(item.mediaId) && Streaming.canStartAt(uri)
        if (!serverSide) {
            // An original (or a download): seek inside the file; a start offset no longer applies.
            if (uri != null && Streaming.offsetMs(uri) > 0) swap(mediaItemIndex, item.withUri(Streaming.startingAt(uri, 0)))
            return exo.seekTo(mediaItemIndex, positionMs.coerceAtLeast(0))
        }
        val target = positionMs.coerceAtLeast(0)
        val start = Streaming.offsetMs(uri)
        // Already streaming from that very second: nothing to load again.
        if (mediaItemIndex == exo.currentMediaItemIndex && start > 0 && target / 1000 * 1000 == start && exo.currentPosition < 1_000) return
        val next = Streaming.startingAt(uri, target)
        AppLog.i("Stream", "Song ${item.mediaId}: converted, so the server starts it at ${target / 1000}s instead of the player reading up to there")
        swap(mediaItemIndex, item.withUri(next))
        // The new stream starts at that whole second and has no known length, so the player can't jump inside
        // it: it plays from its start (at most a second before where was asked).
        exo.seekTo(mediaItemIndex, 0)
    }

    override fun seekToDefaultPosition() = seekTo(exo.currentMediaItemIndex, 0)
    override fun seekToDefaultPosition(mediaItemIndex: Int) = seekTo(mediaItemIndex, 0)
    override fun seekBack() = seekTo((currentPosition - exo.seekBackIncrement).coerceAtLeast(0))
    override fun seekForward() = seekTo(currentPosition + exo.seekForwardIncrement)

    override fun seekToPrevious() {
        // Back to the start of this song (its real start, not the start of a stream that began part-way in).
        if (offset() > 0 && currentPosition > exo.maxSeekToPreviousPosition) return seekTo(exo.currentMediaItemIndex, 0)
        exo.seekToPrevious()
    }

    private fun resetOthers() {
        val current = exo.currentMediaItemIndex
        for (i in 0 until exo.mediaItemCount) {
            if (i == current) continue
            val item = exo.getMediaItemAt(i)
            val uri = item.localConfiguration?.uri ?: continue
            if (Streaming.offsetMs(uri) > 0) swap(i, item.withUri(Streaming.startingAt(uri, 0)))
        }
    }

    /**
     * Puts [item] in the place of the song at [index], keeping the shuffled order (issue #1202).
     *
     * The player's own swap adds the new copy and removes the old one, and the new copy took a random place in the
     * shuffled order. With shuffle on, removing the playing song then moved the player on to whatever song came next
     * in that order, and the seek brought it back: the song started again from its beginning (the hop to the other
     * song had set its stream back to the start, [resetOthers]), and the shuffled order was changed. Here the new
     * copy is put right after the old one in the shuffled order before the old one goes, so the player moves on to
     * it, and the order is as it was.
     */
    private fun swap(index: Int, item: MediaItem) {
        val shuffled = shuffledOf(exo.shuffleOrder)
        exo.addMediaItem(index + 1, item)
        exo.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(withCopyAfter(shuffled, index), System.nanoTime()))
        exo.removeMediaItem(index)
    }

    private fun MediaItem.withUri(uri: Uri): MediaItem = buildUpon().setUri(uri).build()

    companion object {
        /** Whether a stream is live for the media session: what the player says, except for a converted song. */
        fun live(playerSaysLive: Boolean, converted: Boolean): Boolean = playerSaysLive && !converted

        /** The songs' places in the queue, in the order shuffle plays them. */
        fun shuffledOf(order: ShuffleOrder): IntArray {
            val out = ArrayList<Int>(order.length)
            var i = order.firstIndex
            while (i != C.INDEX_UNSET && out.size < order.length) { out += i; i = order.getNextIndex(i) }
            return out.toIntArray()
        }

        /**
         * [shuffled] with a copy of the song at [index] added to the queue right after it (at index + 1): the songs
         * after it move up a place, and the copy is played right after the song in the shuffled order.
         */
        fun withCopyAfter(shuffled: IntArray, index: Int): IntArray {
            val out = ArrayList<Int>(shuffled.size + 1)
            for (i in shuffled) {
                out += if (i > index) i + 1 else i
                if (i == index) out += index + 1
            }
            return out.toIntArray()
        }
    }
}
