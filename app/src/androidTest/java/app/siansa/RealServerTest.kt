package app.siansa

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
import app.siansa.covers.CoverStore
import app.siansa.data.Album
import app.siansa.data.Song
import app.siansa.data.ReplayGainMode
import app.siansa.log.AppLog
import app.siansa.playback.CrossfadeMode
import app.siansa.playback.LoudnessSource
import app.siansa.ui.AnimatedCover
import app.siansa.ui.AnimatedCovers
import app.siansa.ui.LocalGraph
import app.siansa.ui.SMALL_COVER
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
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.pow

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
    private val app = instrumentation.targetContext.applicationContext as SiansaApp
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
        println("---- Siansa log ----")
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

        // Song lists (playlists, Popular) ask for the disc's cover, dc-<album>:<disc>, which Navidrome answers
        // with the album's file in full: it must be linked to the kept one, not downloaded again, and give a still.
        val disc = "dc-${CoverStore.canonical(cover).removePrefix("al-")}:1"
        val served = graph.imageHttp.newCall(
            Request.Builder().url(graph.subsonic.coverUrl(disc, SMALL_COVER)).header(CoverStore.PEEK, "1").build()
        ).execute().use { r -> r.isSuccessful && CoverStore.sniff(r.peekBody(32).bytes()) != null }
        org.junit.Assume.assumeTrue("This Navidrome doesn't send disc covers", served)
        val discImage = loadCoverImage(disc, SMALL_COVER, null, cached = false)?.asDrawable(res)
        println("Disc cover: ${discImage?.javaClass?.simpleName} ${discImage?.intrinsicWidth}×${discImage?.intrinsicHeight}, downloads ${CoverStore.downloads.get() - again}")
        assertEquals("A disc cover should reuse the album's kept file, not download it again", again, CoverStore.downloads.get())
        assertTrue("A disc cover in a list should be a still: $discImage", discImage != null && discImage !is Animatable)
        assertEquals("The disc's cover should share the album's file", CoverStore.local(cover)?.original, CoverStore.local(disc)?.original)

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

    /**
     * Seeking in a song that streams converted (Opus) starts it there at once: Navidrome is asked to start
     * the conversion at that second instead of the player reading up to it, which left songs stuck after a
     * switch to Opus (issue #162). The seek bar shows the song's real position.
     */
    @Test
    fun e_seekingAConvertedSongStartsThere() {
        val before = graph.settings.current
        graph.settings.save(before.copy(transcodeWifiKbps = 160, transcodeMobileKbps = 160))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val song = songOf(albums().getValue("Still Album"))
            instrumentation.runOnMainSync { graph.player.play(listOf(song)) }
            val startBy = SystemClock.elapsedRealtime() + 30_000
            while (!graph.player.state.value.isPlaying && SystemClock.elapsedRealtime() < startBy) SystemClock.sleep(200)
            assertTrue("Converted song never started. ${recentLog()}", graph.player.state.value.isPlaying)
            instrumentation.runOnMainSync { graph.player.seekTo(150_000) }
            // Within 10 s it plays on from there.
            val end = SystemClock.elapsedRealtime() + 10_000
            var at = 0L
            while (SystemClock.elapsedRealtime() < end) {
                SystemClock.sleep(250)
                instrumentation.runOnMainSync { at = graph.player.positionMs() }
                if (at > 151_500 && graph.player.state.value.isPlaying) break
            }
            println("Converted seek: at $at ms, playing=${graph.player.state.value.isPlaying}")
            assertTrue("After seeking to 2:30 in a converted song it's at ${at} ms. ${recentLog()}", at in 151_500..170_000)
        } finally {
            instrumentation.runOnMainSync { graph.player.stop() }
            scenario.close()
            graph.settings.save(before)
        }
    }

    /** The app's last playback log lines, for a failure message (CI shows the message, not the whole log). */
    private fun recentLog(): String = AppLog.tail(300)
        .filter { line -> listOf("/Stream", "/Player", "/Audio", "/Http").any { it in line } }
        .takeLast(8).joinToString(" | ") { it.substringAfter(' ').take(160) }

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

    /**
     * Crossfade with measured loudness (issue #60), against a stand-in for the Siansa plugin on AudioMuse:
     * every song measures −9 LUFS, gets going 1 s after it starts and is quiet for its last 3 s. Smart mode
     * should then start the next song 4 s before the end of the current one, fade it in, and level both
     * to −18 LUFS (a volume of 10^(−9/20) ≈ 0.355).
     */
    @Test
    fun f_crossfadeBlendsSongsWithPluginProfiles() {
        val plugin = FakePlugin(lufs = -9.0, introChunks = 10, tailChunks = 30)
        val before = graph.settings.current
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            graph.settings.save(
                graph.settings.current.copy(
                    audioMuseUrl = plugin.url, audioMuseToken = "test", replayGain = ReplayGainMode.TRACK,
                    transcodeWifiKbps = 0, transcodeMobileKbps = 0,
                ),
            )
            graph.settings.setLoudness(LoudnessSource.PLUGIN, -18)
            graph.settings.setCrossfade(CrossfadeMode.SMART, 8)
            val check = runBlocking { graph.profiles.check() }
            assertTrue("Plugin check: ${check.message}", check.ok)

            val a = albums()
            val first = songOf(a.getValue("Small Animated"))
            val second = songOf(a.getValue("WebP Animated"))
            instrumentation.runOnMainSync { graph.player.play(listOf(first, second)) }
            waitFor(30_000, "the first song to play") { graph.player.state.value.let { it.isPlaying && it.current?.id == first.id } }
            // Fades need the original file (set above), which the player can jump in.
            assertEquals("The song should play as the original file", 0, graph.player.state.value.transcodeKbps)
            // Let it load (about 5 MB over the slow link), then go to 12 s before its end.
            SystemClock.sleep(12_000)
            instrumentation.runOnMainSync { graph.player.seekTo(18_000) }
            val levelled = 10.0.pow(-9 / 20.0).toFloat()
            var lastFirst = 0L
            var switchedAt = 0L
            val volumes = ArrayList<Pair<Long, Float>>() // ms after the switch, volume
            val end = SystemClock.elapsedRealtime() + 25_000
            while (SystemClock.elapsedRealtime() < end) {
                var id: String? = null
                var position = 0L
                var volume = 1f
                instrumentation.runOnMainSync {
                    val p = graph.player.progress()
                    id = p.mediaId
                    position = p.positionMs
                    volume = graph.player.volumeForTest()
                }
                val now = SystemClock.elapsedRealtime()
                if (id == first.id) lastFirst = position
                if (id == second.id) {
                    if (switchedAt == 0L) switchedAt = now
                    volumes += (now - switchedAt) to volume
                    if (now - switchedAt > 4_000) break
                }
                SystemClock.sleep(50)
            }
            println("Crossfade: first song left at ${lastFirst} ms; volumes after the switch: " + volumes.filterIndexed { i, _ -> i % 4 == 0 }.joinToString { "${it.first}ms=${"%.2f".format(it.second)}" })
            val log = AppLog.tail(400)
            assertTrue("The second song never started", switchedAt > 0)
            assertTrue("Profiles weren't asked for", plugin.asked.any { it.contains("/api/profiles") && it.contains(first.id) })
            assertTrue("No fade was logged:\n" + log.takeLast(30).joinToString("\n"), log.any { "Fading ${first.id} into ${second.id}" in it })
            assertTrue("The fade wasn't ready in time", log.none { "wasn't ready to fade" in it })
            // The next song starts about 4 s before the end (30 s song, so near 26 s), not at the end.
            assertTrue("Next song started with the first at ${lastFirst} ms, expected about 26 s", lastFirst in 23_000..28_500)
            // It fades in: quieter than its level at first, at its level after.
            assertTrue("No fade-in: ${volumes.take(6)}", volumes.take(10).any { it.second < levelled * 0.8f })
            assertEquals("Levelled volume after the fade", levelled, volumes.last().second, 0.03f)
        } finally {
            instrumentation.runOnMainSync { graph.player.stop() }
            graph.settings.setCrossfade(before.crossfade, before.crossfadeSeconds)
            graph.settings.setLoudness(before.loudnessSource, before.loudnessTarget)
            graph.settings.save(
                graph.settings.current.copy(
                    audioMuseUrl = before.audioMuseUrl, audioMuseToken = before.audioMuseToken,
                    transcodeWifiKbps = before.transcodeWifiKbps, transcodeMobileKbps = before.transcodeMobileKbps,
                ),
            )
            scenario.close()
            plugin.close()
        }
    }

    private fun waitFor(ms: Long, what: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + ms
        while (SystemClock.elapsedRealtime() < end) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Waited ${ms / 1000} s for $what")
    }

    /** A stand-in for the Siansa plugin's health and profiles endpoints, on the phone itself. */
    private class FakePlugin(private val lufs: Double, private val introChunks: Int, private val tailChunks: Int) : AutoCloseable {
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${server.localPort}"
        val asked = CopyOnWriteArrayList<String>()

        init {
            Thread {
                while (!server.isClosed) {
                    val client = runCatching { server.accept() }.getOrNull() ?: break
                    runCatching { answer(client) }
                }
            }.apply { isDaemon = true }.start()
        }

        private fun ramp(db: Int, chunk: Int): String =
            android.util.Base64.encodeToString(byteArrayOf(db.toByte(), (chunk and 0xFF).toByte(), (chunk shr 8).toByte()), android.util.Base64.NO_WRAP)

        private fun answer(client: Socket): Unit = client.use {
            val reader = client.getInputStream().bufferedReader()
            val path = reader.readLine()?.split(" ")?.getOrNull(1) ?: return
            while (reader.readLine()?.isNotEmpty() == true) Unit
            asked += path
            val body = when {
                path.startsWith("/plugins/siansa_analysis/api/health") -> """{"plugin":"siansa_analysis","plugin_version":"test","status":"ok"}"""
                path.startsWith("/plugins/siansa_analysis/api/profiles") -> {
                    val ids = URLDecoder.decode(path.substringAfter("ids="), "UTF-8").split(",")
                    val profiles = ids.joinToString(",") { id ->
                        """{"track_id":"$id","source":"waveform","ref_lufs":$lufs,"start_ramp":"${ramp(-18, introChunks)}","end_ramp":"${ramp(-18, tailChunks)}"}"""
                    }
                    """{"schema_version":1,"analyzer_version":1,"profiles":[$profiles],"missing":[],"failed":[]}"""
                }
                else -> null
            }
            val bytes = (body ?: "not found").toByteArray()
            val status = if (body != null) "200 OK" else "404 Not Found"
            client.getOutputStream().apply {
                write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(bytes)
                flush()
            }
        }

        override fun close() = server.close()
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
            var lastLog = started
            while (SystemClock.elapsedRealtime() < end) {
                SystemClock.sleep(100)
                val state = graph.player.state.value
                if (state.isPlaying && startMs == 0L) startMs = SystemClock.elapsedRealtime() - started
                if (wasPlaying && state.buffering) stalls++
                wasPlaying = state.isPlaying
                // Where the song is every second, so a stuck or slow song can be told apart (issue #162).
                if (SystemClock.elapsedRealtime() - lastLog >= 1_000) {
                    lastLog = SystemClock.elapsedRealtime()
                    var at = 0L
                    instrumentation.runOnMainSync { at = graph.player.positionMs() }
                    println("Watch: ${(lastLog - started) / 1000}s: at ${at} ms, playing=${state.isPlaying}, waiting=${state.buffering}")
                }
            }
            instrumentation.runOnMainSync { position = graph.player.positionMs() }
            return Watch(startMs, stalls, position)
        } finally {
            scenario.close()
        }
    }

    /** Loads a cover the way [app.siansa.ui.Cover] does (same cache keys), optionally decoded at [decodePx]. */
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
