package app.siansa.ui

import androidx.compose.foundation.border
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * On the TV app, a ring in the accent colour around a button while the remote is on it, as the TV's own buttons have
 * (issue #926). The phone's pages the TV shows (Connections, Playback, Help…) light their rows when the remote reaches
 * them, but their buttons ("Test", "Save") showed nothing, so OK pressed something that couldn't be seen. On a phone it
 * does nothing.
 */
@Composable
fun Modifier.focusRing(shape: Shape = ButtonDefaults.shape): Modifier {
    if (!app.siansa.BuildConfig.TV_APP) return this
    var focused by remember { mutableStateOf(false) }
    val ring = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent
    return this.onFocusChanged { focused = it.hasFocus }.border(FOCUS_RING_WIDTH, ring, shape)
}

/** As thick as the TV's own focus ring. */
private val FOCUS_RING_WIDTH = 3.dp // spacing: exact
