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
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.musix.data.SettingsRepository
import app.musix.data.SyncPhase
import app.musix.data.Users
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
            step("source filter") {
                tab("Home")
                compose.onAllNodesWithContentDescription("Source filter").onFirst().performClick()
                idle(1200)
                waitText("Only downloaded music")
                shot("01b-source-filter", popups = true)
                // A second user (the same login here), shown alongside the first.
                val second = 1
                instrumentation.runOnMainSync {
                    app.getSharedPreferences("users", android.content.Context.MODE_PRIVATE).edit().putString("ids", "0,$second").commit()
                    SettingsRepository(app, second).save(graph.settings.current)
                }
                try {
                    closeSheet()
                    graph.guests.setShown(second, true)
                    val end = SystemClock.elapsedRealtime() + 60_000
                    while (SystemClock.elapsedRealtime() < end && graph.guests.state.value[second]?.let { !it.running && (it.songs != null || it.error != null) } != true) SystemClock.sleep(300)
                    println("Tour: second user ${graph.guests.state.value[second]}")
                    graph.guests.state.value[second]?.error?.let { problems += "source filter: the second user's music didn't load: $it" }
                    idle(800)
                    shot("01c-home-two-users")
                    compose.onAllNodesWithContentDescription("Source filter").onFirst().performClick()
                    idle(1200)
                    waitText("Users and libraries")
                    shot("01d-source-filter-two-users", popups = true)
                    click("Only downloaded music")
                    idle(800)
                    shot("01e-only-downloaded-on", popups = true)
                    closeSheet()
                    idle(1000)
                    shot("01f-home-only-downloaded")
                    libraryAll()
                    idle(1000)
                    shot("01g-library-only-downloaded")
                } finally {
                    graph.settings.setDownloadedOnly(false)
                    graph.guests.setShown(second, false)
                    SystemClock.sleep(1500)
                    instrumentation.runOnMainSync { Users.remove(app, second) }
                    tab("Home")
                }
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
            step("now playing menu, cards and lyrics") {
                // Paused, so the seek bar isn't redrawn every frame and the screen can settle for the checks.
                pausePlayback()
                compose.onAllNodesWithTag("miniPlayer").onFirst().performClick()
                idle(1500)
                // The ⋮ menu.
                compose.onAllNodesWithContentDescription("Song options").onFirst().performClick()
                idle(1200)
                shot("10b-now-playing-menu")
                click("Lyrics")
                idle(1500)
                shot("10c-lyrics")
                back()
                idle(600)
                // Scrolled down to the cards under the player.
                compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = top + bottom * 0.1f) }
                idle(1500)
                shot("10d-now-playing-cards")
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
            step("other artists you might like") {
                libraryAll()
                click("Your listening")
                click("Other artists you might like")
                idle(2500)
                shot("14b-other-artists")
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
                tab("Settings")
                click("Vibes")
                idle(1000)
                shot("17-vibes")
            }
            step("settings") {
                tab("Settings")
                idle(800)
                shot("18-settings")
                listOf("Connections", "Library sync", "Playback", "Themes", "Downloads", "Scrobbling", "Storage", "Logs").forEachIndexed { i, page ->
                    tab("Settings")
                    click(page)
                    idle(1200)
                    shot("19-settings-${i + 1}-" + page.lowercase().replace(' ', '-'))
                    back()
                    idle(400)
                }
            }
            step("themes") {
                // Two presets shown on Home and on the Themes page, then back to Musix colours.
                try {
                    graph.settings.setTheme("LIGHT", "OCEAN", false)
                    tab("Home")
                    idle(1200)
                    shot("19c-home-ocean-light")
                    graph.settings.setTheme("DARK", "SUNSET", true)
                    idle(1200)
                    shot("19d-home-sunset-pure-black")
                    tab("Settings")
                    click("Themes")
                    waitText("Pure black")
                    idle(800)
                    shot("19e-themes-sunset-dark")
                    graph.settings.setTheme("DARK", "MUSIX", false)
                    idle(800)
                    shot("19f-themes-musix-dark")
                    back()
                } finally {
                    graph.settings.setTheme("AUTO", "MUSIX", false)
                    idle(800)
                }
            }
            step("equaliser and ReplayGain") {
                tab("Settings")
                click("Playback")
                click("Equaliser and ReplayGain")
                idle(1200)
                shot("19b-equaliser")
                back()
                back()
            }
            step("sound search") {
                tab("Search")
                click("Sound")
                idle(800)
                shot("15b-sound-search")
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

    /** Closes an open sheet: a real back key goes to the sheet's own window, not the app behind it. */
    private fun closeSheet() {
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        idle(800)
    }

    private fun pausePlayback() {
        instrumentation.runOnMainSync { if (graph.player.state.value.isPlaying) graph.player.togglePlay() }
        SystemClock.sleep(500)
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
    /** [popups]: also draws open sheets, menus and dialogs, which live in windows of their own. */
    private fun shot(name: String, popups: Boolean = false) {
        runCatching { shotNow(name, popups) }.onFailure { println("Tour: screenshot $name failed: $it") }
    }

    /** The app's other windows (sheets, menus, dialogs), drawn over [target] where they sit on screen. */
    private fun drawPopups(target: Bitmap, activityRoot: android.view.View) {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val field = global.getDeclaredField("mViews").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val views = (field.get(instance) as List<android.view.View>).toList()
        val canvas = android.graphics.Canvas(target)
        val base = IntArray(2).also { activityRoot.getLocationOnScreen(it) }
        views.filter { it !== activityRoot && it.isShown && it.width > 0 && it.height > 0 }.forEach { v ->
            val at = IntArray(2).also { v.getLocationOnScreen(it) }
            canvas.save()
            canvas.translate((at[0] - base[0]).toFloat(), (at[1] - base[1]).toFloat())
            v.draw(canvas)
            canvas.restore()
            println("Tour: drew a popup window ${v.javaClass.simpleName} ${v.width}x${v.height}")
        }
    }

    private fun shotNow(name: String, popups: Boolean = false) {
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
        if (popups) {
            val drawn = java.util.concurrent.CountDownLatch(1)
            scenario.onActivity { activity ->
                runCatching { drawPopups(full, activity.window.decorView) }.onFailure { println("Tour: popups for $name: $it") }
                drawn.countDown()
            }
            drawn.await(5, java.util.concurrent.TimeUnit.SECONDS)
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
