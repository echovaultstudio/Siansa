package app.siansa.tvapp

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.siansa.SiansaApp
import app.siansa.ui.LocalGraph
import app.siansa.ui.LocalNav
import app.siansa.ui.LocalSnack
import app.siansa.ui.SiansaTheme
import app.siansa.ui.ThemeChoice
import app.siansa.ui.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The remote's sweep of the TV app's settings pages (the owner, 12 Nov: buttons that didn't register at all, the
 * Remove button for Koito among them). Each page is opened as the TV shows it (the phone's pages in the TV build,
 * TV_APP true), the remote is pressed down from the top until the focus stops moving, and every button, chip, switch
 * and row on the page must have had the focus on the way: a control the remote can't reach can't be pressed.
 * A folded section is opened as the remote passes its heading, so what's inside counts too.
 */
@RunWith(AndroidJUnit4::class)
class TvRemoteSweepTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SiansaApp).graph

    @Before
    fun setUp() {
        // A server, Last.fm signed in and a Koito set, so every button of those pages shows (Sign out, Remove…).
        val s = graph.settings.current
        graph.settings.save(
            s.copy(
                navidromeUrl = "http://10.0.2.2:1", navidromeUser = "sweep", navidromePassword = "sweep",
                lastFmSession = "session", lastFmUser = "sweep", lastFmApiKey = "key", lastFmSecret = "secret",
                listenBrainzUrl = "http://10.0.2.2:2", listenBrainzToken = "token", listenBrainzEnabled = true,
                logging = true,
            ),
        )
    }

    private fun page(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalGraph provides graph) {
                SiansaTheme(ThemeChoice(mode = ThemeMode.DARK)) {
                    val nav = rememberNavController()
                    CompositionLocalProvider(LocalNav provides nav, LocalSnack provides SnackbarHostState()) {
                        TvSurface { content() }
                    }
                }
            }
        }
        compose.waitForIdle()
        instrumentation.setInTouchMode(false)
        compose.waitForIdle()
    }

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    /** A node's own words and its children's (a row that the remote stops on holds its button's words in a child). */
    private fun wordsOf(node: SemanticsNode): List<String> = buildList {
        fun add(n: SemanticsNode) {
            n.config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
            n.config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { add(it) }
            n.config.getOrNull(SemanticsProperties.EditableText)?.let { add(it.text) }
            n.children.forEach { add(it) }
        }
        add(node)
    }

    /** The focused node, after the focus has settled (a row passes it on into itself a frame later). */
    private fun focused(): SemanticsNode? {
        android.os.SystemClock.sleep(150)
        compose.waitForIdle()
        return compose.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()
    }

    /** Where the control with [words] is now (the one nearest to where it was, when there are several alike). */
    private fun nowAt(words: String, was: androidx.compose.ui.geometry.Rect): androidx.compose.ui.geometry.Rect =
        compose.onAllNodes(hasClickAction() or isFocusable()).fetchSemanticsNodes()
            .filter { label(it) == words }
            .minByOrNull { kotlin.math.abs(it.boundsInRoot.left - was.left) + kotlin.math.abs(it.boundsInRoot.top - was.top) }
            ?.boundsInRoot ?: was

    /** Puts the focus back on the control at [bounds] (the row a left or right press just left). */
    private fun refocus(bounds: androidx.compose.ui.geometry.Rect) {
        runCatching {
            // The one that takes focus: a folded card's frame has the same bounds as its heading and takes none.
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus) and SemanticsMatcher("at $bounds") { it.boundsInRoot == bounds })
                .onFirst().requestFocus()
        }
        compose.waitForIdle()
    }

    private fun label(node: SemanticsNode): String =
        wordsOf(node).joinToString(" | ").ifBlank { node.config.getOrNull(SemanticsProperties.TestTag) ?: "(no words)" }

    /**
     * Presses down until the focus stops moving, and along each row it lands in presses right to the row's end (chips,
     * buttons side by side), as the remote is used; the words of every control the focus was on, and the path it took.
     */
    private fun sweep(path: MutableList<String>): Set<String> {
        val reached = LinkedHashSet<String>()
        var last: Pair<String, androidx.compose.ui.geometry.Rect>? = null
        var still = 0
        // Where the page puts the remote first (the user in use on the hub) counts before any press.
        focused()?.let { reached += wordsOf(it); path += label(it) + " @${it.boundsInRoot.top.toInt()} (first)" }
        repeat(120) {
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            var node = focused() ?: run { path += "(nothing focused)"; return@repeat }
            val here = label(node) to node.boundsInRoot
            reached += wordsOf(node)
            if (here == last) {
                still++
                if (still >= 3) {
                    // What the remote never got to, by position, so a stall can be traced.
                    val below = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
                        .filter { it.boundsInRoot.top > node.boundsInRoot.bottom }
                        .sortedBy { it.boundsInRoot.top }
                    path += "(stopped; below: " + below.take(5).joinToString("; ") { label(it) + " @" + it.boundsInRoot.top.toInt() } + ")"
                    return reached
                }
            } else {
                still = 0
                path += here.first + " @${node.boundsInRoot.top.toInt()}"
            }
            last = here
            // Along the row, as the remote is used: left to its start, then right to its end. A press that lands on
            // another line (the Back arrow above the first row, say) is undone by putting the focus back on the row,
            // so the sweep carries on down from where it was rather than circling.
            for (key in intArrayOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
                val arrow = if (key == KeyEvent.KEYCODE_DPAD_LEFT) "← " else "→ "
                var row = node.boundsInRoot
                var rowLabel = label(node)
                var steps = 0
                while (steps++ < 30) {
                    press(key)
                    val next = focused() ?: break
                    if (next.boundsInRoot == row) break
                    // The page may have scrolled to show what the focus landed on: the row is measured where it is now.
                    row = nowAt(rowLabel, row)
                    if (kotlin.math.abs(next.boundsInRoot.top - row.top) > 8f) {
                        path += "($arrow${label(next)}, back)"
                        refocus(row)
                        break
                    }
                    reached += wordsOf(next)
                    path += arrow + label(next)
                    node = next
                    row = next.boundsInRoot
                    rowLabel = label(next)
                }
            }
            // A folded section (its heading ends in -header, its body in -body): opened, so its controls count.
            val tag = node.config.getOrNull(SemanticsProperties.TestTag)
            if (tag != null && tag.endsWith("-header")) {
                val body = tag.removeSuffix("-header") + "-body"
                if (compose.onAllNodesWithTag(body).fetchSemanticsNodes().isEmpty()) {
                    press(KeyEvent.KEYCODE_DPAD_CENTER)
                    val opened = compose.onAllNodesWithTag(body).fetchSemanticsNodes().isNotEmpty()
                    path += if (opened) "(opened $body)" else "($body didn't open on OK; focus now on ${focused()?.let(::label)})"
                    if (!opened) {
                        press(KeyEvent.KEYCODE_ENTER)
                        path += if (compose.onAllNodesWithTag(body).fetchSemanticsNodes().isNotEmpty()) "(opened on Enter)" else "(nor on Enter)"
                    }
                }
            }
        }
        return reached
    }

    /** Every control on the page (a click or toggle action and some words) that the sweep never had the focus on. */
    private fun missed(reached: Set<String>): List<String> {
        val controls = compose.onAllNodes(
            hasClickAction() or SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick),
        ).fetchSemanticsNodes()
        return controls.mapNotNull { node ->
            val words = wordsOf(node).filter { it.isNotBlank() }
            if (words.isEmpty()) return@mapNotNull null
            if (words.any { it in reached }) null else words.joinToString(" | ")
        }.distinct()
    }

    private fun check(name: String, content: @Composable () -> Unit) {
        page(content)
        val path = mutableListOf<String>()
        val reached = sweep(path)
        val missed = missed(reached)
        println("Sweep $name: reached ${reached.size} controls; missed: ${missed.ifEmpty { listOf("none") }}")
        println("Sweep $name path: ${path.joinToString(" → ")}")
        assertTrue("$name: the remote never reaches ${missed.joinToString("; ")}", missed.isEmpty())
    }

    @Test fun settingsHub() = check("Settings") { TvSettingsHub() }
    @Test fun passthrough() = check("Audio passthrough") { TvPassthrough() }
    @Test fun sources() = check("Show music from") { TvSources() }
    @Test fun scrobbling() = check("Scrobbling and history") { app.siansa.ui.ScrobblingScreen(LocalNav.current) }
    @Test fun playback() = check("Playback") { app.siansa.ui.PlaybackSettingsScreen(LocalNav.current) }
    @Test fun crossfade() = check("Crossfade and levelling") { app.siansa.ui.CrossfadeScreen(LocalNav.current) }
    @Test fun connections() = check("Connections") { app.siansa.ui.ConnectionsHub(LocalNav.current) }
    @Test fun sync() = check("Library sync") { app.siansa.ui.SyncScreen(LocalNav.current) }
    @Test fun report() = check("Report a problem") { app.siansa.ui.ProblemReportScreen(LocalNav.current) }
    @Test fun themes() = check("Colours") { app.siansa.ui.ThemesScreen(LocalNav.current) }
    @Test fun logs() = check("Show the log") { app.siansa.ui.LogsScreen(LocalNav.current) }
    @Test fun about() = check("About") { app.siansa.ui.AboutScreen(LocalNav.current) }
}
