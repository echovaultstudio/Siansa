package app.musix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.musix.ui.LocalGraph
import app.musix.ui.MusixRoot
import app.musix.ui.MusixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as MusixApp).graph
        graph.player.connect()
        setContent {
            CompositionLocalProvider(LocalGraph provides graph) {
                MusixTheme { MusixRoot() }
            }
        }
    }
}
