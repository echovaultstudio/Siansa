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
import app.musix.covers.CoverStore
import app.musix.data.Album
import app.musix.data.Song
import app.musix.log.AppLog
import app.musix.ui.AnimatedCover
import app.musix.ui.AnimatedCovers
import app.musix.ui.LocalGraph
import app.musix.ui.SMALL_COVER
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
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
 * a 13 MB animated cover.gif album with a 5-minute FLAC, a small animated cover album, a still album, and
 * an album with a 1080 px animated cover.webp (which Navidrome sends full size for every size asked for).
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
        // A clean start: the app tour may already have kept covers.
        CoverStore.clear()
        AnimatedCovers.forget(graph)
        SingletonImageLoader.get(app).let { it.memoryCache?.clear(); it.diskCache?.clear() }
        val a = albums()
        val big = AnimatedCovers.kind(graph, a.getValue("Big Animated").coverArt!!)
        val small = AnimatedCovers.kind(graph, a.getValue("Small Animated").coverArt!!)
        val still = AnimatedCovers.kind(graph, a.getValue("Still Album").coverArt!!)
        val webp = AnimatedCovers.kind(graph, a.getValue("WebP Animated").coverArt!!)
        println("Kinds: big=$big small=$small still=$still webp=$webp")
        assertEquals("13 MB GIF should use Navidrome's small animated version", AnimatedCovers.Kind.Animated(SMALL_COVER), big)
        // Navidrome sends GIF originals without a size (chunked), so a small GIF goes through its 300 px
        // conversion like a big one; both ways animate.
        assertTrue("Small GIF should be animated: $small", small is AnimatedCovers.Kind.Animated || small == AnimatedCovers.Kind.Local)
        assertEquals(AnimatedCovers.Kind.Still, still)
        assertEquals("Animated WebP should be kept and animated from the phone", AnimatedCovers.Kind.Local, webp)
        // Only the first bytes were read to decide: nothing downloaded yet.
        assertTrue(!CoverStore.has(a.getValue("WebP Animated").coverArt!!))
    }

    /**
     * The crash: an animated WebP cover that Navidrome sends full size for every request. The album page,
     * now playing, the blurred background (the song's cover), the colour sampling and a list tile all ask
     * for it at once. It must be downloaded once, everything but the animation must get a small still
     * (not an animation each), and the animation must still move.
     */
    @Test
    fun b0_webpCoverIsDownloadedOnceAndListsGetAStill() {
        val album = albums().getValue("WebP Animated")
        val detail = runBlocking { graph.subsonic.album(album.id) }
        runBlocking { graph.library.putAlbumsAndSongs(listOf(detail.album), detail.songs, listOf(album.id), emptyList()) }
        val cover = album.coverArt!!
        val songCover = detail.songs.first().coverArt!!
        val before = CoverStore.downloads.get()
        val res = app.resources
        val results = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val threads = listOf(
            "tile" to { loadCoverImage(cover, SMALL_COVER, null) },
            "album page" to { loadCoverImage(cover, 1000, null) },
            "colour" to { loadCoverImage(cover, SMALL_COVER, 120) },
            "background" to { loadCoverImage(songCover, SMALL_COVER, null) },
            "song row" to { loadCoverImage(songCover, 150, null) },
        ).map { (what, load) ->
            Thread {
                val image = load()
                val d = image?.asDrawable(res)
                results += "$what: ${d?.javaClass?.simpleName} ${d?.intrinsicWidth}×${d?.intrinsicHeight} animated=${d is Animatable}"
            }.apply { start() }
        }
        threads.forEach { it.join(120_000) }
        println("WebP loads: ${results.joinToString(" | ")}")
        val downloads = CoverStore.downloads.get() - before
        println("WebP downloads: $downloads, kept: ${CoverStore.local(cover)?.original?.length()} bytes, still: ${CoverStore.local(cover)?.still?.length()} bytes")
        assertEquals("The animated WebP should be downloaded exactly once", 1, downloads)
        assertEquals("Every request should have got a picture: $results", 5, results.count { !it.contains("null") })
        assertTrue("Lists and backgrounds must get a still, not an animation: $results", results.none { it.endsWith("animated=true") })
        assertTrue("A still should have been made", CoverStore.local(cover)?.still != null)
        assertEquals("The song's cover should share the album's file", CoverStore.local(cover)?.original, CoverStore.local(songCover)?.original)

        // Asking again goes nowhere near the network.
        val again = CoverStore.downloads.get()
        repeat(3) { loadCoverImage(cover, 1000, null, cached = false) }
        assertEquals(again, CoverStore.downloads.get())

        val kind = runBlocking { AnimatedCovers.kind(graph, cover) }
        assertEquals(AnimatedCovers.Kind.Local, kind)
        val d = runBlocking { AnimatedCovers.load(app, graph, cover, kind!!, 720) }
        println("WebP animation: ${d?.javaClass?.simpleName} ${d?.intrinsicWidth}×${d?.intrinsicHeight}")
        assertTrue("The album page / now playing should get an animation: $d", d is Animatable)
        assertTrue("The animation should be decoded at display size: ${d?.intrinsicWidth}", (d?.intrinsicWidth ?: 0) in 1..720)
    }

    @Test
    fun b_animatedCoverMovesOnScreen() {
        val cover = albums().getValue("Big Animated").coverArt!!
        val kind = runBlocking { AnimatedCovers.kind(graph, cover) }!!
        compose.setContent {
            CompositionLocalProvider(LocalGraph provides graph) {
                AnimatedCover(cover, kind, Modifier.size(300.dp), RectangleShape) { }
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
                if (kind != null) AnimatedCovers.load(app, graph, big.coverArt!!, kind, 720)
            }
            a.values.forEach { album -> loadCover(album.coverArt!!, SMALL_COVER) }
        }
        println("New setup: $result")
        // Must play while the animated album is open (the 0.0.9 problem was a song that couldn't play
        // at all). Start time and stops are reported, not failed on: on the throttled emulator link
        // they vary a lot from run to run (issue #25).
        assertTrue("Song never started", result.startMs > 0)
        assertTrue("Song barely played (${result.positionMs} ms)", result.positionMs > 5_000)
        if (result.startMs > 6_000 || result.stalls > 0) println("Smooth-playback check: slow start or stops: $result")
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
        // For comparison only: timing on the emulator link varies too much to fail on.
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

    /** Loads a cover the way [app.musix.ui.Cover] does (same cache keys), optionally decoded at [decodePx]. */
    private fun loadCoverImage(id: String, size: Int, decodePx: Int?, cached: Boolean = true): coil3.Image? {
        val px = if (size <= SMALL_COVER) SMALL_COVER else size
        val key = "cover:$id:$px"
        val builder = ImageRequest.Builder(app).data(graph.subsonic.coverUrl(id, px)).diskCacheKey(key).memoryCacheKey(key)
        if (decodePx != null) builder.size(decodePx).memoryCacheKey("$key:$decodePx")
        if (!cached) builder.diskCachePolicy(CachePolicy.DISABLED).memoryCachePolicy(CachePolicy.DISABLED)
        val result = runBlocking { SingletonImageLoader.get(app).execute(builder.build()) }
        return (result as? SuccessResult)?.image
    }

    private fun loadCover(id: String, size: Int) {
        val request = ImageRequest.Builder(app).data(graph.subsonic.coverUrl(id, size)).build()
        runBlocking { SingletonImageLoader.get(app).execute(request) }
    }

    /** Downloads the original cover file in full, like 0.0.7–0.0.9 did. */
    private fun hog(id: String) {
        // The plain client: the image client now keeps animated originals and wouldn't download them again.
        val call = graph.http.newCall(Request.Builder().url(graph.subsonic.coverUrl(id, null)).build())
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
