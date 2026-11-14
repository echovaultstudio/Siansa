package app.siansa

import android.Manifest
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.siansa.car.CarButtons
import app.siansa.log.AppLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Like, Shuffle and Repeat in Android Auto's player (issue #1042), against the real Navidrome: the car draws the
 * buttons the media session lists and sends back the one pressed ([CarConnection] does both as the car does).
 */
@RunWith(AndroidJUnit4::class)
class CarButtonsDeviceTest {
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

    @Test fun theCarHasLikeShuffleAndRepeatAndTheyWork() {
        graph.settings.save(graph.settings.current.copy(navidromeUrl = url, navidromeUser = "admin", navidromePassword = "admin"))
        graph.settings.setLogging(true)
        AppLog.init(app, true)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val album = runBlocking { graph.subsonic.albums("alphabeticalByName", 20, 0) }.first { it.name == "Still Album" }
            val song = runBlocking { graph.subsonic.album(album.id) }.songs.first()
            instrumentation.runOnMainSync { graph.player.play(listOf(song)) }
            waitFor(30_000, { "the song to play" }) { graph.player.state.value.let { it.isPlaying && it.current?.id == song.id } }
            CarConnection().open().use { car ->
                fun state() = car.controller.playbackState
                fun buttons() = state()?.customActions.orEmpty()
                fun names() = buttons().map { it.name.toString() }
                fun press(action: String) = instrumentation.runOnMainSync { car.controller.transportControls.sendCustomAction(action, null) }
                val seen = { "the car's buttons, now ${names()} (${buttons().map { it.action }})" }

                waitFor(20_000, seen) { names().size == 3 }
                assertEquals(listOf(CarButtons.LIKE, CarButtons.SHUFFLE, CarButtons.REPEAT), buttons().map { it.action })
                assertTrue(seen(), names()[0] in listOf("Like", "Liked"))
                assertEquals(listOf("Shuffle is off", "Repeat is off"), names().drop(1))
                assertTrue("Each button has a picture", buttons().all { it.icon != 0 })
                // The new buttons are beside play, previous and next, not in their place.
                val actions = state()!!.actions
                assertTrue("Play and pause are still offered", actions and PlaybackState.ACTION_PLAY_PAUSE != 0L)
                assertTrue("Previous is still offered", actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L)

                press(CarButtons.SHUFFLE)
                waitFor(10_000, seen) { names().getOrNull(1) == "Shuffle is on" && graph.player.state.value.shuffle }
                press(CarButtons.SHUFFLE)
                waitFor(10_000, seen) { names().getOrNull(1) == "Shuffle is off" && !graph.player.state.value.shuffle }

                for (name in listOf("Repeat all is on", "Repeat this song is on", "Repeat is off")) {
                    press(CarButtons.REPEAT)
                    waitFor(10_000, seen) { names().getOrNull(2) == name }
                }
                assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, graph.player.state.value.repeatMode)
            }
        } finally {
            instrumentation.runOnMainSync { graph.player.stop() }
            scenario.close()
        }
    }
}
