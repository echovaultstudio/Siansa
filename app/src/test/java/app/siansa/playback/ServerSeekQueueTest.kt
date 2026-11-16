package app.siansa.playback

import android.app.Application
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A seek in a converted song, on the real player (issue #1202: with shuffle on, a seek sometimes started the song
 * again from its beginning). The player hopped to another song and back, and the hop set the stream back to the
 * start; the shuffled order changed too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ServerSeekQueueTest {
    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Application>()).build()
    private val player = ServerSeekPlayer(exo, converted = { true }, durationMsOf = { 300_000L })
    private val moves = mutableListOf<String>()

    @After fun release() = exo.release()

    private fun song(id: String) = MediaItem.Builder().setMediaId(id)
        .setUri("http://nd.example:4533/rest/stream?u=max&id=$id&format=opus&maxBitRate=256").build()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    init {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { moves += "${mediaItem?.mediaId}" }
        })
    }

    private fun queue(shuffle: Boolean, at: Int) {
        exo.setMediaItems(List(8) { song("s$it") })
        exo.shuffleModeEnabled = shuffle
        exo.seekTo(at, 0)
        idle()
    }

    private fun ids() = List(exo.mediaItemCount) { exo.getMediaItemAt(it).mediaId }
    private fun shuffledIds() = ServerSeekPlayer.shuffledOf(exo.shuffleOrder).map { exo.getMediaItemAt(it).mediaId }
    private fun offset() = Streaming.offsetMs(exo.currentMediaItem?.localConfiguration?.uri)

    private fun seekStaysInTheSong(shuffle: Boolean) {
        repeat(8) { at ->
            queue(shuffle, at)
            moves.clear()
            val queue = ids()
            val order = shuffledIds()
            player.seekTo(120_000)
            idle()
            assertEquals("shuffle $shuffle, song $at: no other song played", listOf("s$at"), moves.distinct())
            assertEquals("s$at", exo.currentMediaItem?.mediaId)
            assertEquals("from the spot asked for", 120_000L, offset())
            assertEquals(120_000L, player.currentPosition)
            player.seekTo(200_000)
            idle()
            assertEquals(listOf("s$at"), moves.distinct())
            assertEquals(200_000L, offset())
            assertEquals("the queue is as it was", queue, ids())
            assertEquals("and so is the shuffled order", order, shuffledIds())
            player.seekTo(0)
            idle()
            assertEquals("back to the start", 0L, offset())
            assertEquals(order, shuffledIds())
        }
    }

    @Test fun withShuffleOnASeekStaysInTheSong() = seekStaysInTheSong(shuffle = true)

    @Test fun withShuffleOffASeekStaysInTheSong() = seekStaysInTheSong(shuffle = false)

    /** Another quality (or Opus when the phone can't play the original) is swapped in the same way. */
    @Test fun anotherQualityStaysInTheSong() {
        repeat(8) { at ->
            queue(shuffle = true, at)
            moves.clear()
            val order = shuffledIds()
            val item = exo.getMediaItemAt(at)
            player.replaceMediaItem(at, item.buildUpon().setUri(Streaming.rewrite(item.localConfiguration!!.uri, 128)).build())
            idle()
            assertEquals("song $at", listOf("s$at"), moves.distinct())
            assertEquals("s$at", exo.currentMediaItem?.mediaId)
            assertEquals(order, shuffledIds())
        }
    }

    @Test fun theCopyComesRightAfterTheSongInTheShuffledOrder() {
        // Queue places 0..4 played as 3, 0, 4, 1, 2; a copy of 1 goes in at place 2.
        assertArrayEquals(intArrayOf(4, 0, 5, 1, 2, 3), ServerSeekPlayer.withCopyAfter(intArrayOf(3, 0, 4, 1, 2), 1))
        // The last song played, and the last in the queue.
        assertArrayEquals(intArrayOf(0, 1, 2, 3), ServerSeekPlayer.withCopyAfter(intArrayOf(0, 1, 2), 2))
        // With the old one removed, the order is the one there was.
        val removed = ServerSeekPlayer.withCopyAfter(intArrayOf(3, 0, 4, 1, 2), 1).filter { it != 1 }.map { if (it > 1) it - 1 else it }
        assertEquals(listOf(3, 0, 4, 1, 2), removed)
    }
}
