package app.siansa.ui

import app.siansa.log.AppLog
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

/** What's typed into a [TvTextBox], for Android's keyboard. */
enum class TvTextKind(val inputType: Int) {
    TEXT(InputType.TYPE_CLASS_TEXT),
    ADDRESS(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS),
    SECRET(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
    LONG_TEXT(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES),
    SEARCH(InputType.TYPE_CLASS_TEXT),
}

/**
 * A text field for the TV app (issue #860): Android's own text field, in the page. The remote moves to it like to any
 * other control (its border lights up), and OK brings up the keyboard, as in other TV apps; with [opensOnFocus] the
 * keyboard comes up as soon as the page opens. Typed with the remote, the TV's microphone, or a phone: the Google TV
 * app's keyboard and pasting from the phone go to Android's field directly, as they do in other apps (Compose's own
 * fields didn't take them, and a box popping up over the page to type in was in the way). What's typed shows straight
 * away ([onValueChange] on every change); the keyboard's Done (or Search) calls [onDone].
 */
@Composable
fun TvTextBox(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    kind: TvTextKind = TvTextKind.TEXT,
    opensOnFocus: Boolean = false,
    /** The keyboard's Done (or Search), with what's in the field. */
    onDone: ((String) -> Unit)? = null,
) {
    val change by rememberUpdatedState(onValueChange)
    val done by rememberUpdatedState(onDone)
    val focusManager by rememberUpdatedState(androidx.compose.ui.platform.LocalFocusManager.current)
    var focused by remember { mutableStateOf(false) }
    // The way out to the page's own controls (issue #955). From Android's field the remote only reached another such
    // field: the button under the last one (Test under the AudioMuse login) was skipped. Leaving, the focus goes to an
    // invisible point at the field's top or bottom edge, and from there to the control above or below; the points take
    // the focus only then, so moving around the page never stops on them.
    var leaving by remember { mutableStateOf(false) }
    val top = remember { androidx.compose.ui.focus.FocusRequester() }
    val bottom = remember { androidx.compose.ui.focus.FocusRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var field by remember { mutableStateOf<EditText?>(null) }
    // Once the remote has left the field for a control of the page, the field turns down any request for the focus for a
    // moment (issue #955): on the TV (NVIDIA Shield, Android 11) the focus reached Test under the Password field and was
    // pulled straight back into the field, again and again, though the move itself worked ("moved on" in the log). The
    // field stays focusable all along: made unfocusable for that moment, up from Test never got back into it.
    val hold = remember { longArrayOf(0L) }
    val refused = remember { intArrayOf(0) }
    fun holdOut(edit: EditText) {
        hold[0] = android.os.SystemClock.uptimeMillis() + HOLD_OUT_MS
        refused[0] = 0
        scope.launch {
            kotlinx.coroutines.delay(HOLD_OUT_MS)
            AppLog.d("TvTextBox", "\"$label\" takes the focus again (has it: ${edit.hasFocus()}, pulls back turned down: ${refused[0]})")
        }
    }
    fun leave(way: androidx.compose.ui.focus.FocusDirection, field: EditText) {
        leaving = true
        scope.launch {
            // A frame first: the points can take the focus only once Compose has seen [leaving] (the emulator test: asked
            // at once, they refused, and the focus stayed in the field).
            androidx.compose.runtime.withFrameNanos { }
            val point = if (way == androidx.compose.ui.focus.FocusDirection.Down) bottom else top
            val moved = runCatching { point.requestFocus(); focusManager.moveFocus(way) }.getOrDefault(false)
            if (moved) holdOut(field)
            AppLog.d("TvTextBox", "Leaving \"$label\" ${if (way == androidx.compose.ui.focus.FocusDirection.Down) "down" else "up"}: ${if (moved) "moved on" else "nothing that way, stays"}")
            if (!moved) field.requestFocus()
            // Once the focus has moved on, the points go back to not taking it.
            leaving = false
        }
    }
    val shape = RoundedCornerShape(Space.Small)
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val textSize = MaterialTheme.typography.bodyLarge.fontSize.value
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        // Compose sees the remote's keys before Android's field does (the emulator test: the field's own key listener
        // never heard up or down, and the focus didn't move), so up and down are taken here first. The move starts from
        // the field's invisible edge point ([leave]), not the field: from Android's field Compose took the field to be
        // somewhere else, and down from Password went up to Username (an emulator run) or nowhere (the TV, issue #955).
        androidx.compose.foundation.layout.Box(
            Modifier.onPreviewKeyEvent { event ->
                if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val way = textBoxExit(event.nativeKeyEvent.keyCode, kind == TvTextKind.LONG_TEXT) ?: return@onPreviewKeyEvent false
                val edit = field ?: return@onPreviewKeyEvent false
                leave(way, edit)
                true
            },
        ) {
            AndroidView(
                factory = { context ->
                    object : EditText(context) {
                        override fun requestFocus(direction: Int, previouslyFocusedRect: android.graphics.Rect?): Boolean {
                            if (android.os.SystemClock.uptimeMillis() < hold[0]) {
                                refused[0]++
                                return false
                            }
                            return super.requestFocus(direction, previouslyFocusedRect)
                        }
                    }.apply {
                        field = this
                        setText(value)
                        setSelection(value.length)
                        if (kind == TvTextKind.LONG_TEXT) {
                            setMinLines(LONG_LINES)
                            setMaxLines(LONG_LINES * 2)
                        } else {
                            setSingleLine(true)
                        }
                        // After setSingleLine, which puts its own way of showing text in place of the password dots: keys,
                        // tokens and passwords showed as typed (issue #957).
                        inputType = kind.inputType
                        if (kind == TvTextKind.SECRET) transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                        imeOptions = (if (kind == TvTextKind.SEARCH) EditorInfo.IME_ACTION_SEARCH else EditorInfo.IME_ACTION_DONE) or EditorInfo.IME_FLAG_NO_FULLSCREEN
                        hint = placeholder ?: "Press OK to type"
                        background = null
                        setTextColor(textColor)
                        setHintTextColor(hintColor)
                        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSize)
                        isFocusable = true
                        isFocusableInTouchMode = true
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                            override fun afterTextChanged(s: Editable?) {
                                val text = s?.toString().orEmpty()
                                if (text != (tag as? String)) {
                                    tag = text
                                    change(text)
                                }
                            }
                        })
                        setOnFocusChangeListener { _, has -> focused = has }
                        // Up and down leave the field for the control above or below it, the page's own (issue #955): from
                        // Android's field the remote didn't reach a button under it, such as Test under the AudioMuse login.
                        // A field of several lines keeps up and down for its lines.
                        setOnKeyListener { view, keyCode, event ->
                            val way = textBoxExit(keyCode, kind == TvTextKind.LONG_TEXT) ?: return@setOnKeyListener false
                            if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                            // Another of these fields that way: straight to it (left to Android, the focus didn't move at all
                            // inside Compose's page on the emulator).
                            val next = view.focusSearch(if (way == androidx.compose.ui.focus.FocusDirection.Down) android.view.View.FOCUS_DOWN else android.view.View.FOCUS_UP)
                            if (next is EditText && next !== view && next.requestFocus()) return@setOnKeyListener true
                            leave(way, this)
                            true
                        }
                        setOnEditorActionListener { view, action, _ ->
                            if (action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_GO) {
                                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(view.windowToken, 0)
                                done?.invoke(text.toString())
                                true
                            } else {
                                false
                            }
                        }
                        tag = value
                        if (opensOnFocus) {
                            post {
                                requestFocus()
                                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                            }
                        }
                    }
                },
                update = { edit ->
                    // A value changed from outside (a search Google TV handed over, a field filled by sign-in) shows here;
                    // the field's own typing is left as it is, so the cursor doesn't jump.
                    if (edit.tag as? String != value) {
                        edit.tag = value
                        edit.setText(value)
                        edit.setSelection(value.length)
                    }
                },
                onRelease = { edit ->
                    (edit.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(edit.windowToken, 0)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .border(if (focused) FOCUSED_BORDER else BORDER, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
                    .background(Color.Transparent, shape)
                    .padding(horizontal = Space.Small, vertical = Space.Tight),
            )
            // The invisible ways out at the field's top and bottom edges (see [leave]).
            androidx.compose.foundation.layout.Box(
                Modifier.align(androidx.compose.ui.Alignment.TopStart)
                    .focusProperties { canFocus = leaving }.focusRequester(top).focusable(),
            )
            androidx.compose.foundation.layout.Box(
                Modifier.align(androidx.compose.ui.Alignment.BottomStart)
                    .focusProperties { canFocus = leaving }.focusRequester(bottom).focusable(),
            )
        }
    }
}

private val BORDER = 1.dp
private val FOCUSED_BORDER = 2.dp
private const val LONG_LINES = 3

/** How long a TV text box takes no focus after the remote has left it (issue #955): past the key's release. */
internal const val HOLD_OUT_MS = 400L

/**
 * Which way the remote's [keyCode] leaves a TV text box (issue #955): up and down to the control above or below, unless
 * the box has several lines ([multiLine]), which keep them. Left, right and OK stay in the box.
 */
internal fun textBoxExit(keyCode: Int, multiLine: Boolean): androidx.compose.ui.focus.FocusDirection? = when {
    multiLine -> null
    keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN -> androidx.compose.ui.focus.FocusDirection.Down
    keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP -> androidx.compose.ui.focus.FocusDirection.Up
    else -> null
}
