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

    /** The address songs are made with: Navidrome's, unless a test sets Emby's and Jellyfin's (issue #1204). */
    private var address: (String) -> String = { id -> "http://nd.example:4533/rest/stream?u=max&id=$id&format=opus&maxBitRate=256" }

    private fun song(id: String) = MediaItem.Builder().setMediaId(id).setUri(address(id)).build()

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

    /** Emby's and Jellyfin's converted songs seek the same way, with their own start time in the address. */
    @Test fun onEmbyAndJellyfinASeekStaysInTheSong() {
        address = { id -> "http://emby.example:8096/Audio/$id/stream?Static=true&api_key=t" }
        seekStaysInTheSong(shuffle = true)
        assertEquals(null, exo.currentMediaItem?.localConfiguration?.uri?.getQueryParameter(Streaming.OFFSET_PARAM))
        player.seekTo(64_000)
        idle()
        assertEquals("640000000", exo.currentMediaItem?.localConfiguration?.uri?.getQueryParameter(Streaming.TICKS_PARAM))
    }

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

    private fun added(id: String) = song(id).buildUpon()
        .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setExtras(android.os.Bundle().apply { putBoolean("siansa.auto", true) }).build())
        .build()

    /**
     * Issue #1238: an album started at its fifth song, with songs Keep playing added after it. Shuffle turned on in the
     * player reorders the queue itself (the owner: the queue showed the album in order): the song playing first and
     * still playing, the album's other nine in some order, then the added songs in theirs; songs added later come after.
     * Turned off, the album is back in its order. A new list while shuffle is on is shuffled at once.
     */
    @Test fun shuffleReordersTheQueueAndPutsItBack() {
        repeat(5) {
            exo.setMediaItems(List(10) { song("a$it") } + List(3) { added("k$it") })
            exo.seekTo(4, 0)
            idle()
            player.shuffleModeEnabled = true
            idle()
            val queue = ids()
            assertEquals("a4", queue.first())
            assertEquals((0 until 10).map { "a$it" }.toSet() - "a4", queue.subList(1, 10).toSet())
            assertEquals(listOf("k0", "k1", "k2"), queue.subList(10, 13))
            assertEquals(0, exo.currentMediaItemIndex)
            assertEquals("a4", exo.currentMediaItem?.mediaId)
            assertEquals(queue, shuffledIds())  // played as the queue stands
            player.addMediaItems(mutableListOf(added("k3"), added("k4")))
            idle()
            assertEquals(queue + listOf("k3", "k4"), ids())
            assertEquals(ids(), shuffledIds())
            player.shuffleModeEnabled = false
            idle()
            assertEquals(List(10) { "a$it" } + List(5) { "k$it" }, ids())
            assertEquals("a4", exo.currentMediaItem?.mediaId)
        }
        player.shuffleModeEnabled = true
        player.setMediaItems(mutableListOf(song("b0"), song("b1"), song("b2"), song("b3")), 2, 0L)
        idle()
        assertEquals("b2", ids().first())
        assertEquals(setOf("b0", "b1", "b3"), ids().drop(1).toSet())
        player.shuffleModeEnabled = false
        idle()
        assertEquals(listOf("b0", "b1", "b2", "b3"), ids())
    }
}
