package app.siansa.playback

import app.siansa.data.AppSettings
import app.siansa.data.Song
import app.siansa.net.SubsonicApi
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The window of the queue the TV gets fits in one Cast message (issue #114): Cast's limit is 64 KB, and a 497-song
 * queue sent whole never arrived (0.52.0-dev). Measured with the real queue items: long names, covers, and the
 * originals' addresses for lossless.
 */
@RunWith(RobolectricTestRunner::class)
class CastMessageSizeTest {
    private val api = SubsonicApi(OkHttpClient()) {
        AppSettings(navidromeUrl = "http://192.168.100.106:30043", navidromeUser = "someone.with.a.long.name", navidromePassword = "a long password of forty characters!!")
    }

    private fun song(i: Int) = Song(
        id = "7rXH75B5gBE0gsHoyBdoP$i",
        title = "A very long song title with a featured artist and a remix name (Extended Mix) part $i",
        artist = "Some Artist feat. Another Artist & A Third One",
        album = "The Album With A Long Name (Deluxe Edition, Remastered 2026)",
        albumId = "2qeaTTIqs18WevOiNducc$i",
        coverArt = "dc-6nurfmltXE2clUXcx63QM3:$i",
        durationSec = 300,
        track = i,
        suffix = if (i % 2 == 0) "flac" else "m4a",
    )

    @Test fun theWindowIsFarUnderCastsLimit() {
        val items = CastItems(api)
        val window = (0 until CastWindow.BEHIND + CastWindow.AHEAD + 1).map { song(it).toMediaItem(api) }
        val bytes = window.sumOf { items.toMediaQueueItem(it).toJson().toString().toByteArray().size }
        println("Cast queue window: ${window.size} songs, $bytes bytes")
        assertTrue("$bytes bytes for ${window.size} songs", bytes < 48_000)
        // And why it's needed: a 497-song queue would be several times the limit.
        val one = items.toMediaQueueItem(song(1).toMediaItem(api)).toJson().toString().length
        assertTrue("$one bytes a song", one * 497 > 64 * 1024)
    }
}
