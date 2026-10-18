package app.musix

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.musix.ui.LocalGraph
import app.musix.ui.MusixRoot
import app.musix.ui.MusixTheme
import app.musix.ui.isDark
import app.musix.ui.themeChoiceOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as MusixApp).graph
        graph.player.connect()
        setContent {
            val settings by graph.settings.state.collectAsStateWithLifecycle()
            val choice = themeChoiceOf(settings)
            val dark = isDark(choice)
            // The status and navigation bar icons follow the app's light or dark, not only the phone's.
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            CompositionLocalProvider(LocalGraph provides graph) {
                MusixTheme(choice) { MusixRoot() }
            }
        }
    }
}
