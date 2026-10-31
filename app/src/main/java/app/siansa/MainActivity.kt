package app.siansa

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.testTag
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.siansa.data.TopBarStyle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.ui.LocalGraph
import app.siansa.ui.SiansaRoot
import app.siansa.ui.SiansaTheme
import app.siansa.ui.isDark
import app.siansa.ui.themeChoiceOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as SiansaApp).graph
        graph.player.connect()
        setContent {
            val settings by graph.settings.state.collectAsStateWithLifecycle()
            val choice = themeChoiceOf(settings)
            val dark = isDark(choice)
            val topBar = settings.topBar
            // The status and navigation bar icons follow the app's light or dark, not only the phone's; on a black
            // top bar they're light (issue #681).
            LaunchedEffect(dark, topBar) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                val top = if (topBar == TopBarStyle.BLACK) SystemBarStyle.dark(Color.TRANSPARENT) else style
                enableEdgeToEdge(statusBarStyle = top, navigationBarStyle = style)
                val bars = WindowInsetsControllerCompat(window, window.decorView)
                if (topBar == TopBarStyle.HIDDEN) {
                    bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    bars.hide(WindowInsetsCompat.Type.statusBars())
                } else {
                    bars.show(WindowInsetsCompat.Type.statusBars())
                }
            }
            CompositionLocalProvider(LocalGraph provides graph) {
                SiansaTheme(choice) {
                    // Hidden: the pages move up into the bar's space, but stay clear of a camera cut-out there.
                    val root = if (topBar == TopBarStyle.HIDDEN) {
                        Modifier.fillMaxSize().background(ComposeColor.Black).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                    } else Modifier.fillMaxSize()
                    Box(root) {
                        SiansaRoot()
                        // Since Android 15 the bar itself can't be coloured, so the app paints under it.
                        if (topBar == TopBarStyle.BLACK) {
                            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(ComposeColor.Black).testTag("blackTopBar"))
                        }
                    }
                }
            }
        }
    }
}
