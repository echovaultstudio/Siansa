package app.siansa.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import app.siansa.log.AppLog

/**
 * Whether the song playing has a "stretched" file: one whose header claims far more time than the song has
 * (some E-AC3 .m4a, beta #12), so its audio carries times many times too long. Kept up to date on the main
 * thread, read on the player's own thread by [StretchedLoadControl].
 */
class StretchedSong {
    @Volatile private var stretched = false

    fun now(): Boolean = stretched

    fun follow(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) = check(player)
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) = check(player)
            override fun onPlaybackStateChanged(playbackState: Int) = check(player)
        })
    }

    private fun check(player: Player) {
        val serverSec = player.currentMediaItem?.toSong()?.durationSec ?: 0
        val playerMs = player.duration
        val now = isStretched(playerMs, serverSec)
        if (now != stretched) {
            stretched = now
            if (now) AppLog.i("Audio", "File says ${playerMs / 1000}s for a ${serverSec}s song: loading the next song by size, so it starts without a gap")
        }
    }

    companion object {
        /** The file's length is far above the server's (the same test as [songDurationMs]). */
        fun isStretched(playerMs: Long, serverSec: Int): Boolean =
            playerMs != C.TIME_UNSET && playerMs > 0 && serverSec > 0 && playerMs > serverSec * 1000L * 3 / 2
    }
}

/**
 * Media3's buffer limits count time. A stretched file (see [StretchedSong]) looks like thousands of seconds
 * buffered after a few seconds of audio, so loading stopped there and the next song was never loaded ahead:
 * it started from nothing, with a gap (beta #16). While such a song plays, loading also goes on until
 * [STRETCHED_BYTES] are held, so the rest of it and the start of the next song are ready in time.
 */
@OptIn(UnstableApi::class)
class StretchedLoadControl(
    private val delegate: LoadControl,
    private val stretched: () -> Boolean,
    /** The bytes the player holds now (its allocator's). */
    private val heldBytes: () -> Long,
) : LoadControl by delegate {
    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean =
        delegate.shouldContinueLoading(parameters) ||
            keepLoading(stretched(), heldBytes())

    companion object {
        /** About 4 minutes of E-AC3 at 768 kbit/s on top of what's playing; well under the phone's memory budget. */
        const val STRETCHED_BYTES = 32L * 1024 * 1024

        fun keepLoading(stretched: Boolean, heldBytes: Long): Boolean = stretched && heldBytes < STRETCHED_BYTES
    }
}
