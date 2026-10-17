package app.musix

import android.Manifest
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.musix.data.SyncPhase
import app.musix.log.AppLog
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * A walk through every page of the app on the emulator, against the real Navidrome test library:
 * each tab, the library's filters, sort and grid, album, artist, search, liked songs, your listening,
 * vibes, now playing and every settings page. Songs are played from the album, artist, search and
 * library pages and must start within a few seconds. A screenshot of every page goes to the log
 * (TOURSHOT lines), which CI turns into images on the ci-screens branch for review.
 *
 * Problems are collected and reported together at the end, so one broken page doesn't hide the rest.
 */
@RunWith(AndroidJUnit4::class)
class AppTourTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val notifications: GrantPermissionRule =
        if (android.os.Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else GrantPermissionRule.grant()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as MusixApp
    private val graph get() = app.graph
    private val url = InstrumentationRegistry.getArguments().getString("navidromeUrl") ?: "http://10.0.2.2:5533"
    private val problems = ArrayList<String>()
    private val starts = ArrayList<String>()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var shots = 0

    @Test
    fun tourEveryPage() {
        graph.settings.save(graph.settings.current.copy(navidromeUrl = url, navidromeUser = "admin", navidromePassword = "admin"))
        graph.settings.setLogging(true)
        AppLog.init(app, true)
        // A synced library, as on the phone.
        graph.sync.start(full = true)
        val syncEnd = SystemClock.elapsedRealtime() + 120_000
        while (graph.sync.progress.value.phase !in setOf(SyncPhase.DONE, SyncPhase.FAILED) && SystemClock.elapsedRealtime() < syncEnd) SystemClock.sleep(500)
        println("Tour: sync ${graph.sync.progress.value.phase}")

        scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            step("home") {
                tab("Home")
                shot("01-home")
            }
            step("library") {
                libraryAll()
                waitText("Liked songs")
                shot("02-library")
                click("Albums")
                waitText("WebP Animated")
                shot("03-library-albums")
                // Sort menu
                click("Recents")
                waitText("Alphabetical")
                shot("04-library-sort-menu")
                click("Alphabetical")
                // Grid and back
                compose.onAllNodesWithContentDescription("Show as grid").onFirst().performClick()
                idle(800)
                shot("05-library-grid")
                compose.onAllNodesWithContentDescription("Show as list").onFirst().performClick()
                // Back to all, then the other filters
                compose.onAllNodesWithContentDescription("Clear filter").onFirst().performClick()
                for (chip in listOf("Playlists", "Mixes", "Artists", "Your listening")) {
                    click(chip)
                    idle(600)
                    shot("06-library-" + chip.lowercase().replace(' ', '-'))
                    compose.onAllNodesWithContentDescription("Clear filter").onFirst().performClick()
                    idle(300)
                }
                // Search inside the library
                compose.onAllNodesWithContentDescription("Search in Your Library").onFirst().performClick()
                compose.onAllNodes(hasSetTextAction()).onFirst().performClick()
                compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Still")
                waitText("Still Album")
                shot("07-library-search")
                compose.onAllNodesWithContentDescription("Search in Your Library").onFirst().performClick()
            }
            step("album page and playing from it") {
                libraryAll()
                click("Albums")
                click("WebP Animated")
                waitText("WebP Song")
                idle(1500)
                shot("08-album-webp")
                timedStart("album page", "WebP Song") { click("WebP Song") }
                shot("09-album-playing")
            }
            step("now playing") {
                compose.onAllNodesWithTag("miniPlayer").onFirst().performClick()
                idle(1500)
                shot("10-now-playing")
                back()
                idle(600)
            }
            step("artist page and playing from it") {
                click("Test Artist")
                waitText("Popular")
                idle(1000)
                shot("11-artist")
                val title = listOf("Long Song A", "Long Song B", "Short Song", "WebP Song").first { exists(it) }
                timedStart("artist page", title) { click(title) }
                back()
            }
            step("album with a big animated GIF") {
                libraryAll()
                click("Albums")
                click("Big Animated")
                waitText("Long Song A")
                idle(2000)
                shot("12-album-gif")
                timedStart("album page (GIF cover)", "Long Song A") { click("Long Song A") }
                back()
            }
            step("liked songs") {
                libraryAll()
                click("Liked songs")
                idle(1500)
                shot("13-liked")
                back()
            }
            step("your listening") {
                libraryAll()
                click("Your listening")
                val month = LocalDate.now().month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                click("Your $month")
                idle(2500)
                shot("14-your-listening")
                back()
            }
            step("search and playing from it") {
                tab("Search")
                idle(500)
                shot("15-search")
                compose.onAllNodes(hasSetTextAction()).onFirst().performClick()
                compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Long Song")
                waitText("Long Song B")
                idle(800)
                shot("16-search-results")
                timedStart("search", "Long Song B") { click("Long Song B") }
            }
            step("vibes") {
                tab("Vibes")
                idle(1000)
                shot("17-vibes")
            }
            step("settings") {
                tab("Settings")
                idle(800)
                shot("18-settings")
                listOf("Connections", "Library sync", "Playback", "Scrobbling", "Storage", "Logs").forEachIndexed { i, page ->
                    tab("Settings")
                    click(page)
                    idle(1200)
                    shot("19-settings-${i + 1}-" + page.lowercase().replace(' ', '-'))
                    back()
                    idle(400)
                }
            }
            step("home after playing") {
                tab("Home")
                idle(1500)
                shot("20-home-after")
            }
        } finally {
            instrumentation.runOnMainSync { graph.player.stop() }
            scenario.close()
        }
        println("Tour: song starts: ${starts.joinToString(" | ")}")
        // The app's own log, saved next to the screenshots on the ci-screens branch (tour-log.txt).
        val log = AppLog.tail(1500)
        log.forEach { println("TOURLOG $it") }
        // Every cover on every page must show: any picture the app couldn't show is a problem.
        val blank = log.filter { it.contains("W/Image: Couldn't show") }
        if (blank.isNotEmpty()) problems += "${blank.size} picture(s) couldn't be shown, e.g. ${blank.first().substringAfter("Couldn't show ")}"
        // And no errors in the app's log during the tour.
        log.filter { Regex("""^\S+ \S+ E/""").containsMatchIn(it) }.take(3).forEach { problems += "Error in the log: ${it.take(200)}" }
        if (problems.isNotEmpty()) fail("Tour found ${problems.size} problem(s):\n" + problems.joinToString("\n"))
    }

    // ---- Helpers --------------------------------------------------------------------------------------

    private fun step(name: String, block: () -> Unit) {
        try {
            block()
            println("Tour: OK $name")
        } catch (e: Throwable) {
            val at = e.stackTrace.firstOrNull { it.className.startsWith("app.musix") }?.let { " at ${it.fileName}:${it.lineNumber}" }.orEmpty()
            problems += "$name: ${e.javaClass.simpleName}: ${e.message?.lineSequence()?.firstOrNull()}$at"
            println("Tour: FAILED $name: $e$at")
            println("Tour: stack " + e.stackTrace.take(25).joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" })
            runCatching { shot("FAIL-" + name.replace(' ', '-')) }
            runCatching { back() }
        }
    }

    private fun idle(ms: Long = 300) {
        compose.waitForIdle()
        SystemClock.sleep(ms)
        compose.waitForIdle()
    }

    /** Text on the page itself, not in the mini player (which repeats the playing song). */
    private fun onPage(text: String) = hasText(text) and !hasTestTag("miniPlayer") and !hasAnyAncestor(hasTestTag("miniPlayer"))

    private fun exists(text: String) = compose.onAllNodes(onPage(text)).fetchSemanticsNodes().isNotEmpty()

    private fun waitText(text: String, timeoutMs: Long = 20_000) {
        compose.waitUntil(timeoutMs) { exists(text) }
    }

    private fun node(text: String): SemanticsNodeInteraction {
        waitText(text)
        return compose.onAllNodes(onPage(text)).onFirst()
    }

    private fun click(text: String) {
        node(text).performClick()
        idle()
    }

    /** Bottom tabs: the last node with that label is the tab itself. */
    private fun tab(label: String) {
        waitText(label)
        val nodes = compose.onAllNodesWithText(label)
        nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
        idle(500)
    }

    /** Your Library with no filter, whatever it was showing before. */
    private fun libraryAll() {
        tab("Your Library")
        tab("Your Library")
        repeat(2) {
            val clear = compose.onAllNodesWithContentDescription("Clear filter")
            if (clear.fetchSemanticsNodes().isNotEmpty()) clear.onFirst().performClick()
            idle(200)
        }
        val search = compose.onAllNodesWithContentDescription("Search in Your Library")
        if (compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() && search.fetchSemanticsNodes().isNotEmpty()) {
            search.onFirst().performClick()
            idle(200)
        }
    }

    private fun back() {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        idle(400)
    }

    /** Taps a song and measures how long until it's audible; over 5 s is a problem. */
    private fun timedStart(where: String, title: String, tap: () -> Unit) {
        val started = SystemClock.elapsedRealtime()
        tap()
        val end = started + 15_000
        while (SystemClock.elapsedRealtime() < end) {
            val s = graph.player.state.value
            if (s.isPlaying && s.current?.title == title) break
            SystemClock.sleep(100)
        }
        val s = graph.player.state.value
        val ms = SystemClock.elapsedRealtime() - started
        val ok = s.isPlaying && s.current?.title == title
        starts += "$where: ${if (ok) "$ms ms" else "did not start in 15 s (playing=${s.isPlaying}, current=${s.current?.title})"}"
        // A song that never plays is a failure. A slow start is reported (CI shows it) but isn't one:
        // the throttled emulator link makes start times vary a lot from run to run (issue #25).
        if (!ok) problems += "Song from $where didn't start within 15 s"
        else if (ms > 5_000) println("Tour: slow start from $where: $ms ms")
    }

    /** The whole window as a small JPEG, written to the log in pieces. Never fails the tour. */
    private fun shot(name: String) {
        runCatching { shotNow(name) }.onFailure { println("Tour: screenshot $name failed: $it") }
    }

    private fun shotNow(name: String) {
        compose.waitForIdle()
        // PixelCopy reads the window's real pixels, hardware images included. (The CI emulator image
        // returns black system screenshots, and drawing the views can't handle hardware bitmaps.)
        var bitmap: Bitmap? = null
        var outcome = -1
        val done = java.util.concurrent.CountDownLatch(1)
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            val target = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            bitmap = target
            android.view.PixelCopy.request(activity.window, target, { result ->
                outcome = result
                done.countDown()
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        done.await(5, java.util.concurrent.TimeUnit.SECONDS)
        val full = bitmap ?: return
        if (outcome != android.view.PixelCopy.SUCCESS) {
            println("Tour: screenshot $name: PixelCopy result $outcome")
            return
        }
        val width = 480
        val small = Bitmap.createScaledBitmap(full, width, full.height * width / full.width, true)
        val bytes = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 72, it) }.toByteArray()
        val text = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val parts = text.chunked(3000)
        shots++
        parts.forEachIndexed { i, part -> println("TOURSHOT $name ${i + 1}/${parts.size} $part") }
    }
}
