package app.musix.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.musix.log.AppLog

/**
 * The phone's player, with seeking in converted (Opus) streams done by Navidrome (issue #162).
 *
 * Navidrome converts a song while it sends it, so the player can't jump inside that stream: it has to read
 * everything up to the new spot first (on a slow connection that's minutes, and the song looks stuck). Instead,
 * a seek in a converted song asks Navidrome for a new conversion starting there (`timeOffset`, in whole
 * seconds), and this player adds where that stream starts to every position it reports, so the seek bar, the
 * lock screen and scrobbling see the song's real position. Originals, downloads and cached songs seek as usual.
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
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = resetOthers()
        })
    }

    /** Where the current stream starts in its song (0 unless Navidrome was asked to start later). */
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

    override fun isCurrentMediaItemSeekable(): Boolean =
        exo.currentMediaItem?.mediaId?.let(converted) == true || exo.isCurrentMediaItemSeekable

    override fun seekTo(positionMs: Long) = seekTo(exo.currentMediaItemIndex, positionMs)

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex !in 0 until exo.mediaItemCount) return exo.seekTo(mediaItemIndex, positionMs)
        val item = exo.getMediaItemAt(mediaItemIndex)
        val uri = item.localConfiguration?.uri
        val serverSide = uri != null && converted(item.mediaId) && Streaming.canStartAt(uri)
        if (!serverSide) {
            // An original (or a download): seek inside the file; a start offset no longer applies.
            if (uri != null && Streaming.offsetMs(uri) > 0) exo.replaceMediaItem(mediaItemIndex, item.withUri(Streaming.startingAt(uri, 0)))
            return exo.seekTo(mediaItemIndex, positionMs.coerceAtLeast(0))
        }
        val target = positionMs.coerceAtLeast(0)
        val start = Streaming.offsetMs(uri)
        // Already streaming from that very second: nothing to load again.
        if (mediaItemIndex == exo.currentMediaItemIndex && start > 0 && target / 1000 * 1000 == start && exo.currentPosition < 1_000) return
        val next = Streaming.startingAt(uri, target)
        AppLog.i("Stream", "Song ${item.mediaId}: converted, so Navidrome starts it at ${target / 1000}s instead of the player reading up to there")
        exo.replaceMediaItem(mediaItemIndex, item.withUri(next))
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
            if (Streaming.offsetMs(uri) > 0) exo.replaceMediaItem(i, item.withUri(Streaming.startingAt(uri, 0)))
        }
    }

    private fun MediaItem.withUri(uri: Uri): MediaItem = buildUpon().setUri(uri).build()
}
