package app.siansa.tvapp

import android.Manifest
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import android.view.KeyEvent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.siansa.SiansaApp
import app.siansa.data.AiProvider
import app.siansa.data.AppJson
import app.siansa.data.PlayEvent
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
 * The website's and README's pictures of the TV app: the TV app on an Android TV emulator with the made-up showcase
 * library and the AudioMuse and AI stand-ins (scripts/showcase-server.sh), as the phone's ShowcaseTest does. Each page is
 * written into the log in pieces (SHOWCASE name part/parts data), which .github/workflows/showcase.yml turns into
 * pictures: tv-NAME.jpg at full size, and the frames of the animations (anim-tv-NAME-NN-MS.jpg, half size, each shown
 * MS milliseconds). The remote is the remote's own keys, sent to the app as a TV's would be.
 *
 * Only runs when asked (the instrumentation argument showcase=true).
 */
@RunWith(AndroidJUnit4::class)
class TvShowcaseTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val notifications: GrantPermissionRule =
        if (android.os.Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else GrantPermissionRule.grant()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val app get() = instrumentation.targetContext.applicationContext as SiansaApp
    private val graph get() = app.graph
    private lateinit var scenario: ActivityScenario<TvActivity>
    private val frameCount = HashMap<String, Int>()

    @Before
    fun onlyWhenAsked() {
        assumeTrue("Showcase screenshots only run when asked (showcase=true)", args.getString("showcase") == "true")
    }

    @Test
    fun showcase() {
        val server = args.getString("navidromeUrl") ?: "http://10.0.2.2:4533"
        val services = args.getString("servicesUrl") ?: "http://10.0.2.2:8000"
        // Starting something to play stays on the page, so each page is taken with the song in the top bar.
        TvPrefs.setOpensPlayer(instrumentation.targetContext, false)

        scenario = ActivityScenario.launch(TvActivity::class.java)
        try {
            // Signing in first, before a server is set: the code a phone scans.
            step("sign in") {
                waitText("Welcome to Siansa", 30_000)
                idle(3000)
                shot("tv-signin")
            }

            graph.settings.save(
                graph.settings.current.copy(
                    navidromeUrl = server, navidromeUser = "admin", navidromePassword = "admin",
                    audioMuseUrl = services,
                    aiProvider = AiProvider.OTHER, aiAddress = "$services/v1", aiModel = "showcase", aiKey = "",
                ),
            )
            graph.sync.start(full = true)
            val syncEnd = SystemClock.elapsedRealtime() + 180_000
            while (graph.sync.progress.value.phase !in setOf(SyncPhase.DONE, SyncPhase.FAILED) && SystemClock.elapsedRealtime() < syncEnd) SystemClock.sleep(500)
            println("Showcase: sync ${graph.sync.progress.value.phase}")
            val songs = runBlocking { graph.library.allSongs() }
            println("Showcase: ${songs.size} songs")
            seedHistory(songs)
            runBlocking {
                runCatching { graph.generator.ensureToday() }.onFailure { println("Showcase: today's mix: $it") }
                MixGenerator.Personal.entries.forEach { kind ->
                    runCatching { graph.generator.ensurePersonal(kind) }.onFailure { println("Showcase: ${kind.title}: $it") }
                }
            }
            // Opened again, so Home is made with the library and the mixes there.
            scenario.close()
            scenario = ActivityScenario.launch(TvActivity::class.java)

            val album = songs.filter { it.album == "Low Tide" }.sortedBy { it.track ?: 0 }
            val start = album.indexOfFirst { it.title == "Harbour Lights" }.coerceAtLeast(0)
            fun play(atMs: Long) {
                instrumentation.runOnMainSync { graph.player.play(album, start, startMs = atMs) }
                val until = SystemClock.elapsedRealtime() + 20_000
                while (SystemClock.elapsedRealtime() < until && !graph.player.state.value.isPlaying) SystemClock.sleep(200)
            }
            runCatching { play(20_000) }.onFailure { println("Showcase: playing: $it") }

            step("home") {
                waitText("Today's mixes", 90_000)
                idle(5000)
                shot("tv-home")
                // The animation: the remote moving along Home's rows, the focused cover growing.
                frame("home", 1200)
                key(KeyEvent.KEYCODE_DPAD_DOWN)
                idle(500)
                frame("home", 700)
                repeat(4) {
                    key(KeyEvent.KEYCODE_DPAD_RIGHT)
                    idle(350)
                    frame("home", 600)
                }
                repeat(2) {
                    key(KeyEvent.KEYCODE_DPAD_DOWN)
                    idle(600)
                    frame("home", 900)
                    key(KeyEvent.KEYCODE_DPAD_RIGHT)
                    idle(350)
                    frame("home", 600)
                }
                idle(800)
                shot("tv-home-more")
                repeat(4) {
                    key(KeyEvent.KEYCODE_DPAD_DOWN)
                    idle(500)
                }
                idle(1500)
                shot("tv-home-rows")
            }
            step("library") {
                click("Library")
                waitText("Albums")
                idle(3000)
                shot("tv-library")
            }
            step("search") {
                click("Search")
                idle(1500)
                // Android's own text box: typing starts as Search opens; the keys go to it as a keyboard's would.
                runCatching { compose.onAllNodes(hasContentDescription("Search,", substring = true)).onFirst().performClick() }
                idle(800)
                instrumentation.sendStringSync("harbour")
                idle(1500)
                closeKeyboard()
                waitText("Harbour Lights")
                idle(2500)
                shot("tv-search")
            }
            step("settings") {
                click("Settings")
                idle(2500)
                shot("tv-settings")
            }
            step("now playing") {
                if (graph.player.state.value.current?.title != "Harbour Lights") play(20_000)
                click("Now playing")
                waitTag("tvRemoteHint")
                idle(3000)
                shot("tv-now-playing")
                // The animation: the song playing on, then the lyrics coming in beside it, following the song.
                frame("player", 1400)
                menu("Lyrics")
                idle(600)
                repeat(14) {
                    SystemClock.sleep(650)
                    frame("player", 650)
                }
                frame("player", 1600)
                shot("tv-lyrics")
                menu("Up next")
                idle(2000)
                shot("tv-up-next")
            }
            step("album") {
                menu("Go to the album")
                waitText("Harbour Lights")
                idle(3000)
                shot("tv-album")
            }
            step("artist") {
                click("Glass Harbour")
                waitText("Top songs")
                idle(3000)
                shot("tv-artist")
            }
        } finally {
            scenario.close()
        }
    }

    // A year of plays, so Home has its rows of what's played (Jump back in, Recently played, Rediscover).
    private fun seedHistory(songs: List<Song>) {
        if (songs.isEmpty()) return
        val rnd = Random(860)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val favourites = listOf("Glass Harbour", "Neon Parish", "Velvet Coast", "Mara Lux", "Kel Arden", "Iris Okafor", "Sunborn")
        val pool = songs.flatMap { s -> List(favourites.indexOf(s.artist).let { if (it < 0) 1 else 12 - it }) { s } }
        val hours = listOf(8, 12, 17, 18, 19, 19, 20, 20, 20, 21, 21, 21, 22, 22, 23)
        val events = (0 until 1500).map {
            val s = pool[rnd.nextInt(pool.size)]
            val day = today.minusDays((Math.pow(rnd.nextDouble(), 1.4) * 365).toLong())
            val at = day.atTime(hours[rnd.nextInt(hours.size)], rnd.nextInt(60), rnd.nextInt(60)).atZone(zone).toInstant().toEpochMilli()
            PlayEvent(s.id, s.title, s.artist, s.genre, at.coerceAtMost(now - 60_000), listenedMs = s.durationSec * 1000L)
        }.sortedByDescending { it.at }
        val file = File(app.filesDir, Users.scoped("history.json"))
        file.writeText(AppJson.encodeToString(ListSerializer(PlayEvent.serializer()), events))
        // The store keeps what it read in memory: give it the same list.
        val json = graph.history.javaClass.getDeclaredField("json").apply { isAccessible = true }.get(graph.history)
        val state = json.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(json)
        @Suppress("UNCHECKED_CAST")
        (state as MutableStateFlow<Any?>).value = events
        println("Showcase: ${events.size} plays")
    }

    private fun step(name: String, block: () -> Unit) {
        try {
            block()
            println("Showcase: OK $name")
        } catch (e: Throwable) {
            val at = e.stackTrace.firstOrNull { it.className.startsWith("app.siansa") }?.let { " at ${it.fileName}:${it.lineNumber}" }.orEmpty()
            println("Showcase: FAILED tv $name: ${e.message?.lineSequence()?.firstOrNull()}$at")
            runCatching { shot("FAIL-tv-" + name.replace(' ', '-')) }
        }
    }

    /** A key of the remote, sent to the app as the TV sends it. */
    private fun key(code: Int) {
        instrumentation.sendKeyDownUpSync(code)
        compose.waitForIdle()
    }

    /** The on-screen keyboard closed, if it's open (Back with no keyboard would leave the page). */
    private fun closeKeyboard() {
        val shown = shell("dumpsys input_method").lineSequence().any { it.trim().startsWith("mInputShown=true") }
        if (shown) key(KeyEvent.KEYCODE_BACK)
        idle(800)
    }

    private fun shell(command: String): String =
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    private fun idle(ms: Long = 300) {
        compose.waitForIdle()
        SystemClock.sleep(ms)
        compose.waitForIdle()
    }

    /** Now playing's menu (the remote's down), and [choice] in it. */
    private fun menu(choice: String) {
        if (!exists(choice)) {
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            idle(600)
        }
        click(choice)
    }

    private fun waitTag(tag: String, timeoutMs: Long = 20_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun exists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun waitText(text: String, timeoutMs: Long = 20_000) = compose.waitUntil(timeoutMs) { exists(text) }

    private fun node(text: String): SemanticsNodeInteraction = compose.onAllNodes(hasText(text)).onFirst()

    private fun click(text: String) {
        waitText(text)
        node(text).performClick()
        idle(600)
    }

    private fun frame(name: String, holdMs: Int) {
        val n = frameCount.getOrDefault(name, 0)
        frameCount[name] = n + 1
        shot("anim-tv-$name-${"%02d".format(n)}-$holdMs", half = true)
    }

    /** The whole window, as a JPEG, written to the log in pieces: SHOWCASE name part/parts data. */
    private fun shot(name: String, half: Boolean = false) {
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
        val picture = if (half) Bitmap.createScaledBitmap(full, full.width / 2, full.height / 2, true) else full
        val bytes = ByteArrayOutputStream().also { picture.compress(Bitmap.CompressFormat.JPEG, if (half) 88 else 92, it) }.toByteArray()
        val parts = Base64.encodeToString(bytes, Base64.NO_WRAP).chunked(3000)
        parts.forEachIndexed { i, part ->
            println("SHOWCASE $name ${i + 1}/${parts.size} $part")
            SystemClock.sleep(6)  // the log drops lines that come faster than it's read
        }
        println("Showcase: shot $name ${picture.width}x${picture.height}, ${bytes.size / 1024} KB")
    }
}
