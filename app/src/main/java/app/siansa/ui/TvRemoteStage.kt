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

/** What the remote does on the TV's now playing (issue #974), with no buttons on the screen; held, left and right wind ([RemoteHold]). */
enum class RemoteAction { PLAY_PAUSE, PREVIOUS, NEXT, MENU }

/** The [RemoteAction] for the remote's [keyCode]: OK plays and pauses, left and right skip (on letting go), down opens the menu. */
fun remoteAction(keyCode: Int): RemoteAction? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> RemoteAction.PLAY_PAUSE
    KeyEvent.KEYCODE_DPAD_LEFT -> RemoteAction.PREVIOUS
    KeyEvent.KEYCODE_DPAD_RIGHT -> RemoteAction.NEXT
    KeyEvent.KEYCODE_DPAD_DOWN -> RemoteAction.MENU
    else -> null
}

/**
 * Holding left or right on the TV's now playing (issue #984): a press skips (on letting go), held it winds back or
 * forward through the song, faster the longer it's held, as a video player's remote does. Fed the remote's keys with
 * their times, it says what each means ([Step]). Kept apart from the screen so it can be tested.
 */
class RemoteHold {
    sealed interface Step {
        /** A key that does something at once: OK, down, or a skip on letting go of left or right. */
        data class Act(val action: RemoteAction) : Step
        /** Held: wind by [deltaMs] (back when below 0) since the last step. */
        data class Wind(val deltaMs: Long) : Step
        /** Let go after winding: play from where it was wound to. */
        data object WindDone : Step
        /** Taken, nothing to do yet (left or right just pressed, or OK held). */
        data object Wait : Step
        /** Not the stage's: the page goes on as usual. */
        data object Pass : Step
    }

    private var held = 0
    private var downMs = 0L
    private var lastMs = 0L
    private var winding = false

    /** A key pressed ([repeat] above 0 while held, as Android repeats it) at [atMs]. */
    fun down(keyCode: Int, repeat: Int, atMs: Long): Step {
        val action = remoteAction(keyCode) ?: return Step.Pass
        if (action != RemoteAction.PREVIOUS && action != RemoteAction.NEXT) return if (repeat == 0) Step.Act(action) else Step.Wait
        if (repeat == 0 || held != keyCode) {
            held = keyCode
            downMs = atMs
            lastMs = atMs
            winding = false
            return Step.Wait
        }
        val heldFor = atMs - downMs
        if (heldFor < HOLD_MS) return Step.Wait
        winding = true
        val speed = if (heldFor < FASTER_AFTER_MS) SPEED else FAST_SPEED
        val delta = (atMs - lastMs).coerceAtLeast(0L) * speed
        lastMs = atMs
        return Step.Wind(if (action == RemoteAction.PREVIOUS) -delta else delta)
    }

    /** A key let go. */
    fun up(keyCode: Int): Step {
        if (keyCode != held) return if (remoteAction(keyCode) != null) Step.Wait else Step.Pass
        held = 0
        if (winding) {
            winding = false
            return Step.WindDone
        }
        return Step.Act(remoteAction(keyCode) ?: return Step.Pass)
    }

    companion object {
        /** Held this long, left or right winds instead of skipping. */
        const val HOLD_MS = 400L
        /** Winding goes ten times as fast as playing… */
        const val SPEED = 10L
        /** …and thirty times after a few seconds held, for long mixes. */
        const val FAST_SPEED = 30L
        const val FASTER_AFTER_MS = 3_000L
    }
}

/**
 * The TV's now playing as one place the remote rests on (issue #974), as on Siansa's Chromecast screen: nothing to
 * select, the remote's keys act straight away ([remoteAction]). [handles] says which it takes now (with a panel open,
 * right goes into the panel instead of to the next song); the rest go on as usual. Only while the remote rests here: in
 * something inside it (a list of songs) its keys move through that. The focus comes here as the page opens ([focus]
 * brings it back, as when a panel closes). Holding left or right winds through the song ([RemoteHold]): [onWind] with
 * how far each time, [onWindDone] on letting go.
 */
@Composable
fun TvRemoteStage(
    onAction: (RemoteAction) -> Unit,
    modifier: Modifier = Modifier,
    handles: (RemoteAction) -> Boolean = { true },
    focus: FocusRequester = remember { FocusRequester() },
    onWind: (Long) -> Unit = {},
    onWindDone: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val act by rememberUpdatedState(onAction)
    val takes by rememberUpdatedState(handles)
    val wind by rememberUpdatedState(onWind)
    val windDone by rememberUpdatedState(onWindDone)
    val hold = remember { RemoteHold() }
    var here by remember { mutableStateOf(false) }
    Box(
        modifier
            .focusRequester(focus)
            .onFocusChanged { here = it.isFocused }
            .onKeyEvent { e ->
                if (!here) return@onKeyEvent false
                val native = e.nativeKeyEvent
                if (remoteAction(native.keyCode)?.let(takes) != true) return@onKeyEvent false
                val step = when (e.type) {
                    KeyEventType.KeyDown -> hold.down(native.keyCode, native.repeatCount, native.eventTime)
                    KeyEventType.KeyUp -> hold.up(native.keyCode)
                    else -> RemoteHold.Step.Pass
                }
                when (step) {
                    is RemoteHold.Step.Act -> act(step.action)
                    is RemoteHold.Step.Wind -> wind(step.deltaMs)
                    RemoteHold.Step.WindDone -> windDone()
                    RemoteHold.Step.Wait -> Unit
                    RemoteHold.Step.Pass -> return@onKeyEvent false
                }
                true
            }
            .focusable()
            .testTag("tvStage"),
        content = content,
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
