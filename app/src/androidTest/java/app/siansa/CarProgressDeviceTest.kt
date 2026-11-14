package app.siansa

import android.Manifest
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.siansa.data.Song
import app.siansa.log.AppLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The progress bar in Android Auto (issue #1039), against the real Navidrome: the car draws it from the song's length
 * and the position the media session gives it ([CarConnection] reads both as the car does). A song streamed as the
 * original file and one converted to Opus must both have them.
 */
@RunWith(AndroidJUnit4::class)
class CarProgressDeviceTest {
    @get:Rule val notifications: GrantPermissionRule =
        if (android.os.Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else GrantPermissionRule.grant()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as SiansaApp
    private val graph get() = app.graph
    private val url = InstrumentationRegistry.getArguments().getString("navidromeUrl") ?: "http://10.0.2.2:5533"

    private fun waitFor(ms: Long, what: () -> String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + ms
        while (SystemClock.elapsedRealtime() < end) {
            if (condition()) return
            SystemClock.sleep(200)
        }
        throw AssertionError("Waited ${ms / 1000} s for ${what()}. ${AppLog.tail(120)}")
    }

    /** Plays [song] at [kbps] (0: the original file) and checks what the car is told about it. */
    private fun playAndAsk(song: Song, kbps: Int, car: CarConnection) {
        val what = if (kbps > 0) "a song converted to Opus $kbps" else "a song as the original file"
        graph.settings.save(graph.settings.current.copy(transcodeWifiKbps = kbps, transcodeMobileKbps = kbps))
        instrumentation.runOnMainSync { graph.player.play(listOf(song)) }
        // The same song may still be playing from the round before, part-way in and at the other quality: wait for
        // this round's own stream, from its start. A jump sent before it loads is undone when it does.
        waitFor(30_000, { "$what to play (now Opus ${graph.player.state.value.transcodeKbps})" }) {
            graph.player.state.value.let { it.isPlaying && it.current?.id == song.id && it.transcodeKbps == kbps }
        }
        fun length() = car.controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        fun state() = car.controller.playbackState
        val told = { "$what: the car is told length ${length()} ms, position ${state()?.position} ms, speed ${state()?.playbackSpeed}, seeking ${(state()?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO != 0L}" }
        waitFor(20_000, told) {
            val s = state() ?: return@waitFor false
            length() > 0 && s.state == PlaybackState.STATE_PLAYING && s.position in 0 until 30_000 && s.playbackSpeed > 0f && s.actions and PlaybackState.ACTION_SEEK_TO != 0L
        }
        println("Car test setup: " + told())
        val real = song.durationSec * 1000L
        assertTrue("$what is ${real} ms long, the car is told ${length()} ms", kotlin.math.abs(length() - real) < 2_000)
        // After a jump the car is told the new position, which for a converted song is a new stream from there.
        instrumentation.runOnMainSync { graph.player.seekTo(60_000) }
        waitFor(15_000, { "the position after a jump to 1:00. " + told() }) { (state()?.position ?: -1) in 59_000..75_000 && state()?.state == PlaybackState.STATE_PLAYING }
    }

    @Test fun theCarIsToldASongsLengthAndPosition() {
        val before = graph.settings.current
        graph.settings.save(before.copy(navidromeUrl = url, navidromeUser = "admin", navidromePassword = "admin"))
        graph.settings.setLogging(true)
        AppLog.init(app, true)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val album = runBlocking { graph.subsonic.albums("alphabeticalByName", 20, 0) }.first { it.name == "Still Album" }
            val song = runBlocking { graph.subsonic.album(album.id) }.songs.first()
            assertTrue("The test song is ${song.durationSec} s long", song.durationSec > 90)
            CarConnection().open().use { car ->
                playAndAsk(song, 0, car)
                playAndAsk(song, 160, car)
            }
        } finally {
            instrumentation.runOnMainSync { graph.player.stop() }
            scenario.close()
            graph.settings.save(graph.settings.current.copy(transcodeWifiKbps = before.transcodeWifiKbps, transcodeMobileKbps = before.transcodeMobileKbps))
        }
    }
}
