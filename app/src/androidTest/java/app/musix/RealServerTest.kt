package app.musix

import android.Manifest
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.musix.data.Album
import app.musix.data.Song
import app.musix.log.AppLog
import app.musix.ui.AnimatedCover
import app.musix.ui.AnimatedCovers
import app.musix.ui.LocalGraph
import app.musix.ui.SMALL_COVER
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.IOException

/**
 * Runs against a real Navidrome (0.64.2, with ffmpeg) on the CI machine, reached through a proxy that
 * limits the connection to 4.3 Mbit/s like the phone's link (see scripts/test-server.sh). The library has
 * a 13 MB animated cover.gif album with a 5-minute FLAC, a small animated cover album, and a still album.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RealServerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val notifications: GrantPermissionRule =
        if (android.os.Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else GrantPermissionRule.grant()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as MusixApp
    private val graph get() = app.graph
    private val url = InstrumentationRegistry.getArguments().getString("navidromeUrl") ?: "http://10.0.2.2:5533"
    private val hogs = ArrayList<Call>()

    @Before
    fun setUp() {
        graph.settings.save(graph.settings.current.copy(navidromeUrl = url, navidromeUser = "admin", navidromePassword = "admin"))
        graph.settings.setLogging(true)
        AppLog.init(app, true)
        AppLog.setDetailed(true)
        // Nothing from earlier tests in the song cache.
        graph.songCache.keys.toList().forEach { graph.songCache.removeResource(it) }
    }

    @After
    fun tearDown() {
        hogs.forEach { it.cancel() }
        instrumentation.runOnMainSync { graph.player.stop() }
        println("---- Musix log ----")
        AppLog.tail(200).forEach { println(it) }
    }

    private fun albums(): Map<String, Album> = runBlocking {
        graph.subsonic.albums("alphabeticalByName", 20, 0).associateBy { it.name }
    }

    private fun songOf(album: Album): Song = runBlocking { graph.subsonic.album(album.id).songs.first() }

    @Test
    fun a_coverKinds() = runBlocking {
        val a = albums()
        val big = AnimatedCovers.kind(graph, a.getValue("Big Animated").coverArt!!)
        val small = AnimatedCovers.kind(graph, a.getValue("Small Animated").coverArt!!)
        val still = AnimatedCovers.kind(graph, a.getValue("Still Album").coverArt!!)
        println("Kinds: big=$big small=$small still=$still")
        assertEquals("13 MB GIF should use Navidrome's small animated version", AnimatedCovers.Kind.Animated(SMALL_COVER), big)
        // Navidrome sends original covers without a size, so a small GIF also goes through the 300 px version.
        assertTrue("Small GIF should be animated: $small", small is AnimatedCovers.Kind.Animated)
        assertEquals(AnimatedCovers.Kind.Still, still)
    }

    @Test
    fun b_animatedCoverMovesOnScreen() {
        val cover = albums().getValue("Big Animated").coverArt!!
        val size = (runBlocking { AnimatedCovers.kind(graph, cover) } as AnimatedCovers.Kind.Animated).size
        compose.setContent {
            CompositionLocalProvider(LocalGraph provides graph) {
                AnimatedCover(cover, size, Modifier.size(300.dp), RectangleShape) { }
            }
        }
        var found: ImageView? = null
        compose.waitUntil(60_000) {
            found = findImageView(compose.activity.window.decorView)
            found?.drawable != null
        }
        SystemClock.sleep(500)
        var running = false
        var type = ""
        compose.runOnUiThread {
            val d = found!!.drawable
            type = d.javaClass.name
            running = (d as? Animatable)?.isRunning == true
        }
        println("Animated cover drawable: $type running=$running")
        assertTrue("Cover isn't an animation: $type", type.contains("Scale") || type.contains(AnimatedImageDrawable::class.java.simpleName))
        assertTrue("Animation isn't running", running)
    }

    /** The new setup: play while browsing the animated album (as opening it and now playing would). */
    @Test
    fun c_playsSmoothlyWhileAnimatedAlbumIsOpen() {
        val a = albums()
        val big = a.getValue("Big Animated")
        val result = playAndWatch(songOf(big), seconds = 40) {
            // What the album page and now playing ask for now.
            runBlocking {
                val kind = AnimatedCovers.kind(graph, big.coverArt!!)
                if (kind is AnimatedCovers.Kind.Animated) AnimatedCovers.load(app, graph, big.coverArt!!, kind.size)
            }
            a.values.forEach { album -> loadCover(album.coverArt!!, SMALL_COVER) }
        }
        println("New setup: $result")
        assertTrue("Song took ${result.startMs} ms to start", result.startMs in 1..6_000)
        assertEquals("Song stopped to wait for data", 0, result.stalls)
        assertTrue("Only reached ${result.positionMs} ms", result.positionMs > 30_000)
    }

    /** Control: the old setup's full-size cover downloads, to show the test catches the problem. */
    @Test
    fun d_oldSetupBigCoverDownloadsStarveTheSong() {
        val a = albums()
        val song = songOf(a.getValue("Still Album"))
        val cover = a.getValue("Big Animated").coverArt!!
        val result = playAndWatch(song, seconds = 40) {
            repeat(3) { hog(cover) } // album page, now playing and the size check, each downloading the 13 MB original
        }
        println("Old setup (3 original covers downloading): $result")
        assertTrue("Expected the old setup to stall: $result", result.stalls > 0 || result.startMs > 6_000)
    }

    data class Watch(val startMs: Long, val stalls: Int, val positionMs: Long)

    private fun playAndWatch(song: Song, seconds: Int, alongside: () -> Unit): Watch {
        // The app in the foreground, as when someone is using it.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val started = SystemClock.elapsedRealtime()
            instrumentation.runOnMainSync { graph.player.play(listOf(song)) }
            Thread { alongside() }.start()
            var startMs = 0L
            var stalls = 0
            var wasPlaying = false
            var position = 0L
            val end = started + seconds * 1000L
            while (SystemClock.elapsedRealtime() < end) {
                SystemClock.sleep(100)
                val state = graph.player.state.value
                if (state.isPlaying && startMs == 0L) startMs = SystemClock.elapsedRealtime() - started
                if (wasPlaying && state.buffering) stalls++
                wasPlaying = state.isPlaying
            }
            instrumentation.runOnMainSync { position = graph.player.positionMs() }
            return Watch(startMs, stalls, position)
        } finally {
            scenario.close()
        }
    }

    private fun loadCover(id: String, size: Int) {
        val request = ImageRequest.Builder(app).data(graph.subsonic.coverUrl(id, size)).build()
        runBlocking { SingletonImageLoader.get(app).execute(request) }
    }

    /** Downloads the original cover file in full, like 0.0.7–0.0.9 did. */
    private fun hog(id: String) {
        val call = graph.imageHttp.newCall(Request.Builder().url(graph.subsonic.coverUrl(id, null)).build())
        hogs += call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) {
                Thread { runCatching { response.body?.source()?.readByteArray() }; response.close() }.start()
            }
        })
    }

    private fun findImageView(view: View): ImageView? {
        if (view is ImageView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findImageView(view.getChildAt(i))?.let { return it }
        return null
    }

    @Suppress("unused")
    private fun uri(s: String) = Uri.parse(s)
}
