package app.siansa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import app.siansa.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * On the TV app (issue #860), a text field opens the keyboard as soon as the remote reaches it, instead of waiting for
 * OK. With the keyboard open, the field also takes what's typed on a phone (the Google TV app's keyboard on the phone
 * types into the TV's keyboard, so it had nowhere to go until the keyboard was opened). Does nothing on the phone app,
 * where a tap opens the keyboard.
 */
@Composable
fun Modifier.keyboardOnTvFocus(): Modifier {
    if (!BuildConfig.TV_APP) return this
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    return onFocusChanged { focus ->
        if (focus.isFocused) {
            scope.launch {
                // Once the field has started taking text: asked for any sooner, the keyboard stays shut.
                delay(KEYBOARD_DELAY_MS)
                keyboard?.show()
            }
        }
    }
}

private const val KEYBOARD_DELAY_MS = 100L
