package app.siansa.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusProperties
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

/**
 * On the TV app, a row of chips or buttons the remote can't miss (the owner, 12 Nov: the Remove button for Koito and
 * others didn't register at all). The remote moves by distance, weighted 13:1 towards the direction it moves in but
 * measured from the middle of each control: on a wide page a narrow button or chip at the left counts as further away
 * than a full-width row below it, so down went past it. Here the row itself is a focusable the page's width, so the
 * remote lands on it, and the moment it does the focus is passed on into the row, to its first chip or button; from
 * there left and right move along the row, and up or down leave it. On a phone it does nothing.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun Modifier.tvFocusGroup(): Modifier {
    if (!app.siansa.BuildConfig.TV_APP) return this
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var landed by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(landed) {
        if (landed > 0) focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Enter)
    }
    return this
        .fillMaxWidth()
        .onFocusChanged { if (it.isFocused) landed++ }
        .focusGroup()
        .focusable()
}

/** As thick as the TV's own focus ring. */
private val FOCUS_RING_WIDTH = 3.dp // spacing: exact

/**
 * A row with a button at its start (Test under a login, issue #978) that the remote reaches as the whole width of the
 * page on the TV. The remote moves by distance, and on the TV's wide page a narrow button on the left counts as further
 * away than a wide control beyond it: down from the last box went past Test to Save, and up from Save past it too.
 * Here the row is what the remote stops on, OK on it does [onClick], and the button in it ([content]'s modifier, for
 * the button) shows the ring. On a phone, an ordinary row whose button is pressed as usual.
 */
@Composable
fun TvActionRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tv: Boolean = app.siansa.BuildConfig.TV_APP,
    content: @Composable androidx.compose.foundation.layout.RowScope.(button: Modifier) -> Unit,
) {
    val arrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.Medium)
    val middle = androidx.compose.ui.Alignment.CenterVertically
    if (!tv) {
        androidx.compose.foundation.layout.Row(modifier, horizontalArrangement = arrangement, verticalAlignment = middle) { content(Modifier) }
        return
    }
    var focused by remember { mutableStateOf(false) }
    val ring = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent
    androidx.compose.foundation.layout.Row(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        horizontalArrangement = arrangement,
        verticalAlignment = middle,
    ) {
        content(Modifier.focusProperties { canFocus = false }.border(FOCUS_RING_WIDTH, ring, ButtonDefaults.shape))
    }
}
