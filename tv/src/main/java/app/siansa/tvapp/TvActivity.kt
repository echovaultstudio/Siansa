package app.siansa.tvapp

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.SiansaApp
import app.siansa.log.AppLog
import app.siansa.ui.LocalGraph
import app.siansa.ui.SiansaTheme
import app.siansa.ui.ThemeMode
import app.siansa.ui.themeChoiceOf
import kotlinx.coroutines.flow.MutableStateFlow

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
        setContent {
            val settings by graph.settings.state.collectAsStateWithLifecycle()
            // The user's colours, always dark: a TV is watched in a dim room.
            val choice = themeChoiceOf(settings).copy(mode = ThemeMode.DARK)
            CompositionLocalProvider(LocalGraph provides graph) {
                SiansaTheme(choice) {
                    TvRoot(searches)
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
}
