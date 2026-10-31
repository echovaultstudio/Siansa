package app.siansa

import android.Manifest
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.siansa.data.AiProvider
import app.siansa.data.AppJson
import app.siansa.data.PlayEvent
import app.siansa.data.SavedVibe
import app.siansa.data.Song
import app.siansa.data.SyncPhase
import app.siansa.data.Users
import app.siansa.mix.MixGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * The README's and website's screenshots (issue #741): the app against the made-up showcase library and the
 * AudioMuse and AI stand-ins (scripts/showcase-server.sh), each page drawn full size into the log in pieces
 * (SHOWCASE lines), which .github/workflows/showcase.yml turns into pictures.
 *
 * Only runs when asked (the instrumentation argument showcase=true): the other emulator runs skip it.
 */
@RunWith(AndroidJUnit4::class)
class ShowcaseTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val notifications: GrantPermissionRule =
        if (android.os.Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else GrantPermissionRule.grant()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val app get() = instrumentation.targetContext.applicationContext as SiansaApp
    private val graph get() = app.graph
    private val problems = ArrayList<String>()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun onlyWhenAsked() {
        assumeTrue("Showcase screenshots only run when asked (showcase=true)", args.getString("showcase") == "true")
    }

    @Test
    fun showcase() {
        val server = args.getString("navidromeUrl") ?: "http://10.0.2.2:4533"
        val services = args.getString("servicesUrl") ?: "http://10.0.2.2:8000"
        graph.settings.save(
            graph.settings.current.copy(
                navidromeUrl = server, navidromeUser = "admin", navidromePassword = "admin",
                audioMuseUrl = services,
                aiProvider = AiProvider.OTHER, aiAddress = "$services/v1", aiModel = "showcase", aiKey = "",
                themeMode = "DARK",
            ),
        )
        graph.sync.start(full = true)
        val syncEnd = SystemClock.elapsedRealtime() + 180_000
        while (graph.sync.progress.value.phase !in setOf(SyncPhase.DONE, SyncPhase.FAILED) && SystemClock.elapsedRealtime() < syncEnd) SystemClock.sleep(500)
        println("Showcase: sync ${graph.sync.progress.value.phase}")
        val songs = runBlocking { graph.library.allSongs() }
        println("Showcase: ${songs.size} songs")
        seedHistory(songs)
        seedVibes(songs)
        runBlocking {
            runCatching { graph.generator.ensureToday() }.onFailure { println("Showcase: today's mix: $it") }
            MixGenerator.Personal.entries.forEach { kind ->
                runCatching { graph.generator.ensurePersonal(kind) }.onFailure { println("Showcase: ${kind.title}: $it") }
            }
        }

        scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            step("home") {
                tab("Home")
                waitText("Today's mix", 90_000)
                idle(5000)
                shot("home")
                swipe()
                idle(2500)
                shot("home-more")
                swipe()
                idle(2500)
                shot("home-more-2")
                compose.onRoot().performTouchInput { repeat(3) { swipeDown(startY = top + bottom * 0.2f, endY = bottom * 0.85f) } }
                idle(800)
            }
            step("arrange") {
                tab("Home")
                compose.onAllNodesWithContentDescription("Source filter and Arrange Home").onFirst().performClick()
                idle(1200)
                click("Arrange")
                idle(1200)
                shot("arrange", popups = true)
                closeSheet()
            }
            step("album") {
                libraryAll()
                chip("Music")
                chip("Albums")
                scrollTo("Low Tide")
                click("Low Tide")
                waitText("Harbour Lights")
                idle(2500)
                shot("album")
            }
            step("now playing") {
                val album = songs.filter { it.album == "Low Tide" }.sortedBy { it.track ?: 0 }
                val start = album.indexOfFirst { it.title == "Harbour Lights" }.coerceAtLeast(0)
                instrumentation.runOnMainSync { graph.player.play(album, start, startMs = 72_000) }
                val until = SystemClock.elapsedRealtime() + 20_000
                while (SystemClock.elapsedRealtime() < until && !graph.player.state.value.isPlaying) SystemClock.sleep(200)
                idle(1500)
                compose.onAllNodesWithTag("miniPlayer").onFirst().performClick()
                idle(3000)
                shot("now-playing")
                compose.onAllNodesWithContentDescription("Lyrics").onFirst().performClick()
                idle(2500)
                shot("lyrics")
                back()
                idle(800)
                settle()
            }
            step("search") {
                tab("Search")
                waitText("Radios")
                idle(2500)
                shot("search")
            }
            step("flow") {
                tab("Flow")
                idle(3000)
                shot("flow")
            }
            step("your listening") {
                libraryAll()
                chip("Your listening")
                click("Your ${LocalDate.now().year}")
                idle(4000)
                shot("listening")
                swipe()
                idle(2000)
                shot("listening-more")
                swipe()
                idle(2000)
                shot("listening-clock")
                back()
            }
            step("library") {
                libraryAll()
                idle(1500)
                shot("library")
                chip("Music")
                chip("Albums")
                compose.onAllNodesWithContentDescription("Show as grid").onFirst().performClick()
                idle(2500)
                shot("library-grid")
                compose.onAllNodesWithContentDescription("Show as list").onFirst().performClick()
            }
            step("style") {
                tab("Settings")
                click("Style")
                idle(2000)
                shot("style")
                back()
            }
            step("settings") {
                tab("Settings")
                idle(1500)
                shot("settings")
            }
        } finally {
            scenario.close()
        }
        if (problems.isNotEmpty()) println("Showcase: ${problems.size} step(s) failed: ${problems.joinToString("; ")}")
    }

    // ---- The made-up listening past -------------------------------------------------------------------------------

    /**
     * Plays over this year, most of them the favourite artists, mostly in the evening: what Your listening, the
     * listening clock and On repeat are made from. Written straight to the history file, and to the store's memory.
     */
    private fun seedHistory(songs: List<Song>) {
        if (songs.isEmpty()) return
        val rnd = Random(741)
        val favourites = listOf("Glass Harbour", "Neon Parish", "Velvet Coast", "Mara Lux", "Kel Arden", "Iris Okafor", "Sunborn")
        val weighted = songs.flatMap { s -> List(favourites.indexOf(s.artist).let { if (it < 0) 1 else 12 - it }) { s } }
        val hours = listOf(7, 8, 8, 9, 12, 13, 17, 18, 18, 19, 19, 20, 20, 20, 21, 21, 21, 21, 22, 22, 22, 23, 23, 0)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val firstDay = LocalDate.of(today.year, 1, 1)
        val days = (today.toEpochDay() - firstDay.toEpochDay()).toInt().coerceAtLeast(1)
        val now = System.currentTimeMillis()
        val events = (0 until 2950).map {
            val s = weighted[rnd.nextInt(weighted.size)]
            // More plays lately: the month and the year both look busy.
            val day = (Math.pow(rnd.nextDouble(), 1.4) * days).toInt()
            val at = today.minusDays(day.toLong()).atTime(hours[rnd.nextInt(hours.size)], rnd.nextInt(60), rnd.nextInt(60)).atZone(zone).toInstant().toEpochMilli()
            PlayEvent(s.id, s.title, s.artist, s.genre, at.coerceAtMost(now - 60_000), listenedMs = s.durationSec * 1000L)
        }.sortedByDescending { it.at }
        val file = File(app.filesDir, Users.scoped("history.json"))
        file.writeText(AppJson.encodeToString(ListSerializer(PlayEvent.serializer()), events))
        // The store keeps what it read in memory: give it the same list.
        val json = graph.history.javaClass.getDeclaredField("json").apply { isAccessible = true }.get(graph.history)
        val state = json.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(json)
        @Suppress("UNCHECKED_CAST")
        (state as MutableStateFlow<Any?>).value = events
        println("Showcase: ${events.size} plays this year")
    }

    /** A few vibes described in Flow, two of them pinned to Home. */
    private fun seedVibes(songs: List<Song>) {
        fun of(vararg genres: String) = songs.filter { it.genre in genres }.shuffled(Random(genres.joinToString().hashCode())).take(40)
        val vibes = listOf(
            Triple("headlights in the tunnel, driving home late", "Headlights in the Tunnel", of("Synthwave", "Techno", "Electropop")),
            Triple("rainy sunday, slow coffee, nowhere to be", "Rainy Sunday, Slow Coffee", of("Soul", "Neo-Soul", "Jazz", "Lo-Fi")),
            Triple("deep focus, no words", "Deep Focus, No Words", of("Ambient", "Modern Classical", "Lo-Fi", "Post-Rock")),
            Triple("first warm day of spring", "First Warm Day", of("Indie Pop", "Afrobeat", "Chillwave")),
            Triple("dancing in the kitchen", "Dancing in the Kitchen", of("House", "Electropop", "Afrobeat")),
        )
        runBlocking {
            vibes.forEachIndexed { i, (text, title, picked) ->
                graph.savedVibes.save(
                    SavedVibe(
                        id = "", text = text, title = title, description = "Songs that sound like $text.", songs = picked,
                        madeAt = System.currentTimeMillis() - i * 86_400_000L, pinned = i < 2, query = text, ai = true,
                    ),
                )
            }
        }
    }

    // ---- Helpers --------------------------------------------------------------------------------------------------

    private fun step(name: String, block: () -> Unit) {
        runCatching { settle() }
        try {
            block()
            println("Showcase: OK $name")
        } catch (e: Throwable) {
            val at = e.stackTrace.firstOrNull { it.className.startsWith("app.siansa") }?.let { " at ${it.fileName}:${it.lineNumber}" }.orEmpty()
            problems += "$name: ${e.javaClass.simpleName}: ${e.message?.lineSequence()?.firstOrNull()}$at"
            println("Showcase: FAILED $name: ${e.message?.lineSequence()?.firstOrNull()}$at")
            runCatching { shot("FAIL-" + name.replace(' ', '-')) }
            repeat(3) { if (popupWindows() > 0) runCatching { closeSheet() } }
            println("Showcase: on screen after $name: " + runCatching { onScreenTexts().take(400) }.getOrDefault("?"))
        }
    }

    /** Closes what a step left open: sheets and menus (windows of their own), then now playing. */
    private fun settle() {
        repeat(3) { if (popupWindows() > 0) closeSheet() }
        repeat(2) {
            if (compose.onAllNodesWithContentDescription("Song options").fetchSemanticsNodes().isNotEmpty()) back()
        }
    }

    /** How many of the app's windows besides the main one are showing (sheets, menus, dialogs). */
    private fun popupWindows(): Int {
        var count = 0
        instrumentation.runOnMainSync {
            val global = Class.forName("android.view.WindowManagerGlobal")
            val instance = global.getMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val views = (global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<android.view.View>).toList()
            count = views.count { it.isShown } - 1
        }
        return count.coerceAtLeast(0)
    }

    private fun onScreenTexts(): String = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Text))
        .fetchSemanticsNodes().joinToString(" | ") { it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString(" / ") }

    /** Scrolls the page's list until [text] is drawn. */
    private fun scrollTo(text: String) {
        if (exists(text)) return
        compose.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction()).onFirst()
            .performScrollToNode(onPage(text))
        idle(500)
    }

    private fun idle(ms: Long = 300) {
        compose.waitForIdle()
        SystemClock.sleep(ms)
        compose.waitForIdle()
    }

    private fun onPage(text: String) = hasText(text) and !hasTestTag("miniPlayer") and !hasAnyAncestor(hasTestTag("miniPlayer"))

    private fun exists(text: String) = compose.onAllNodes(onPage(text)).fetchSemanticsNodes().isNotEmpty()

    private fun waitText(text: String, timeoutMs: Long = 20_000) = compose.waitUntil(timeoutMs) { exists(text) }

    private fun node(text: String): SemanticsNodeInteraction {
        waitText(text)
        return compose.onAllNodes(onPage(text)).onFirst()
    }

    private fun click(text: String) {
        node(text).performSemanticsAction(SemanticsActions.OnClick)
        idle()
    }

    private fun chip(text: String) {
        node(text).performScrollTo().performClick()
        idle()
    }

    private fun tab(label: String) {
        waitText(label)
        val nodes = compose.onAllNodesWithText(label)
        nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
        idle(500)
    }

    private fun libraryAll() {
        tab("Your Library")
        tab("Your Library")
        repeat(2) {
            val clear = compose.onAllNodesWithContentDescription("Clear filter")
            if (clear.fetchSemanticsNodes().isNotEmpty()) clear.onFirst().performClick()
            idle(200)
        }
    }

    private fun swipe() = compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.78f, endY = top + bottom * 0.22f) }

    private fun back() {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        idle(500)
    }

    private fun closeSheet() {
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        idle(800)
    }

    /** The app's other windows (sheets, menus), drawn over [target] where they sit on screen. */
    private fun drawPopups(target: Bitmap, root: android.view.View) {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val views = (global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<android.view.View>).toList()
        val canvas = android.graphics.Canvas(target)
        val base = IntArray(2).also { root.getLocationOnScreen(it) }
        views.filter { it !== root && it.isShown && it.width > 0 && it.height > 0 }.forEach { v ->
            val at = IntArray(2).also { v.getLocationOnScreen(it) }
            canvas.save()
            canvas.translate((at[0] - base[0]).toFloat(), (at[1] - base[1]).toFloat())
            v.draw(canvas)
            canvas.restore()
        }
    }

    /** The whole window at full size, as a JPEG, written to the log in pieces: SHOWCASE name part/parts data. */
    private fun shot(name: String, popups: Boolean = false) {
        compose.waitForIdle()
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
            println("Showcase: screenshot $name: PixelCopy result $outcome")
            return
        }
        if (popups) {
            val drawn = java.util.concurrent.CountDownLatch(1)
            scenario.onActivity { activity ->
                runCatching { drawPopups(full, activity.window.decorView) }.onFailure { println("Showcase: popups for $name: $it") }
                drawn.countDown()
            }
            drawn.await(5, java.util.concurrent.TimeUnit.SECONDS)
        }
        val bytes = ByteArrayOutputStream().also { full.compress(Bitmap.CompressFormat.JPEG, 94, it) }.toByteArray()
        val parts = Base64.encodeToString(bytes, Base64.NO_WRAP).chunked(3000)
        parts.forEachIndexed { i, part ->
            println("SHOWCASE $name ${i + 1}/${parts.size} $part")
            SystemClock.sleep(6)  // the log drops lines that come faster than it's read
        }
        println("Showcase: shot $name ${full.width}x${full.height}, ${bytes.size / 1024} KB")
    }
}
