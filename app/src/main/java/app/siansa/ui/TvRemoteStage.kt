package app.siansa.ui

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag

/** What the remote does on the TV's now playing (issue #974), with no buttons on the screen. */
enum class RemoteAction { PLAY_PAUSE, PREVIOUS, NEXT, MENU }

/** The [RemoteAction] for the remote's [keyCode]: OK plays and pauses, left and right skip, down opens the menu. */
fun remoteAction(keyCode: Int): RemoteAction? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> RemoteAction.PLAY_PAUSE
    KeyEvent.KEYCODE_DPAD_LEFT -> RemoteAction.PREVIOUS
    KeyEvent.KEYCODE_DPAD_RIGHT -> RemoteAction.NEXT
    KeyEvent.KEYCODE_DPAD_DOWN -> RemoteAction.MENU
    else -> null
}

/**
 * The TV's now playing as one place the remote rests on (issue #974), as on Siansa's Chromecast screen: nothing to
 * select, the remote's keys act straight away ([remoteAction]). [handles] says which it takes now (with a panel open,
 * right goes into the panel instead of to the next song); the rest go on as usual. Only while the remote rests here: in
 * something inside it (a list of songs) its keys move through that. The focus comes here as the page opens ([focus]
 * brings it back, as when a panel closes).
 */
@Composable
fun TvRemoteStage(
    onAction: (RemoteAction) -> Unit,
    modifier: Modifier = Modifier,
    handles: (RemoteAction) -> Boolean = { true },
    focus: FocusRequester = remember { FocusRequester() },
    content: @Composable BoxScope.() -> Unit,
) {
    val act by rememberUpdatedState(onAction)
    val takes by rememberUpdatedState(handles)
    var here by remember { mutableStateOf(false) }
    Box(
        modifier
            .focusRequester(focus)
            .onFocusChanged { here = it.isFocused }
            .onKeyEvent { e ->
                if (!here || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val action = remoteAction(e.nativeKeyEvent.keyCode)?.takeIf(takes) ?: return@onKeyEvent false
                act(action)
                true
            }
            .focusable()
            .testTag("tvStage"),
        content = content,
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
