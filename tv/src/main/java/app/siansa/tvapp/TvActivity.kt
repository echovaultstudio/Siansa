package app.siansa.tvapp

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.AppGraph
import app.siansa.SiansaApp
import app.siansa.log.AppLog
import app.siansa.playback.Passthrough
import app.siansa.playback.PassthroughRenderer
import app.siansa.ui.LocalGraph
import app.siansa.ui.SiansaTheme
import app.siansa.ui.ThemeMode
import app.siansa.ui.themeChoiceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Siansa on Android TV and Google TV (issue #860): the one screen of the TV app. The phone app's code does the rest
 * (the server, the library, mixes and the playback service); what's here is built for the remote.
 */
class TvActivity : ComponentActivity() {
    /** A search from Google TV ("Play … on Siansa"), for the Search page to pick up. */
    private val searches = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as SiansaApp).graph
        graph.player.connect()
        searches.value = queryOf(intent)
        AppLog.i("Tv", "TV app opened")
        logOutput(graph)
        setContent {
            val settings by graph.settings.state.collectAsStateWithLifecycle()
            // The user's colours, always dark: a TV is watched in a dim room.
            val choice = themeChoiceOf(settings).copy(mode = ThemeMode.DARK)
            CompositionLocalProvider(LocalGraph provides graph) {
                SiansaTheme(choice) {
                    TvSurface { TvRoot(searches) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        queryOf(intent)?.let { searches.value = it }
    }

    private fun queryOf(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_SEARCH }?.getStringExtra(SearchManager.QUERY)?.trim()?.takeIf { it.isNotEmpty() }

    /** What the TV's output takes as a bitstream, once at the start: the first line to read in a report about Dolby or DTS. */
    private fun logOutput(graph: AppGraph) {
        graph.scope.launch(Dispatchers.Default) {
            val takes = Passthrough.bitstreamMimes.filter { runCatching { PassthroughRenderer.outputTakes(applicationContext, it) }.getOrDefault(false) }
            AppLog.i(
                "Passthrough",
                "The TV's output takes as a bitstream: ${takes.joinToString { Passthrough.name(it) }.ifEmpty { "nothing" }} · " +
                    "passthrough ${graph.settings.current.audioPassthrough.label}",
                event = "passthrough.output", fields = mapOf("takes" to takes.joinToString(","), "mode" to graph.settings.current.audioPassthrough.name),
            )
        }
    }
}

/**
 * The TV's surface: text in the theme's colour wherever a page doesn't set one (without it, it was black on the dark
 * background), and the focused item lit in the accent colour on every page, the phone's pages included, so the remote's
 * place can be seen from the sofa.
 */
@Composable
fun TvSurface(content: @Composable () -> Unit) {
    val focusRipple = RippleConfiguration(
        color = MaterialTheme.colorScheme.primary,
        rippleAlpha = RippleAlpha(draggedAlpha = 0.16f, focusedAlpha = FOCUSED_ALPHA, hoveredAlpha = 0.08f, pressedAlpha = 0.24f),
    )
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        CompositionLocalProvider(LocalRippleConfiguration provides focusRipple, content = content)
    }
}

private const val FOCUSED_ALPHA = 0.32f
