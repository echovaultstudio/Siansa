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
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.onFocusChanged
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
 * A text field for the TV app (issue #860): Android's own text field, in the page. Typed with the remote, the TV's
 * microphone, or a phone: the Google TV app's keyboard and pasting from the phone go to Android's field directly, as
 * they do in other apps (Compose's own fields didn't take them). What's typed shows straight away ([onValueChange] on
 * every change); the keyboard's Done (or Search) calls [onDone].
 *
 * Moving around the page, the box is one of the page's own controls (issue #955): the remote stops on it like on a
 * button and its border lights up, and up and down go on to the control above or below, the Test button under a login
 * too. Android's field takes the focus only to type: OK brings it up with the keyboard ([opensOnFocus]: as soon as the
 * page opens). While typing, up and down (a box of one line) or Back hand the focus back to the box and go on from it.
 * Mixing the two before, the remote skipped the button under the last field, then on the TV was pulled back into it,
 * as Compose took Android's field to be somewhere else.
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
    /**
     * In a form (issue #978): up and down go to the control before or after it in the form, as a narrow button (Test)
     * under a wide box lost to a wide control further away. Off for a box beside other parts of the page (search).
     */
    inForm: Boolean = true,
    /** The keyboard's Done (or Search), with what's in the field. */
    onDone: ((String) -> Unit)? = null,
) {
    val change by rememberUpdatedState(onValueChange)
    val done by rememberUpdatedState(onDone)
    val focusManager by rememberUpdatedState(androidx.compose.ui.platform.LocalFocusManager.current)
    val box = remember { androidx.compose.ui.focus.FocusRequester() }
    var boxFocused by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf<EditText?>(null) }
    val multiLine = kind == TvTextKind.LONG_TEXT
    fun keyboard(edit: EditText, show: Boolean) {
        val imm = edit.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        if (show) imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT) else imm.hideSoftInputFromWindow(edit.windowToken, 0)
    }
    fun startTyping() {
        val edit = field ?: return
        edit.isFocusable = true
        edit.isFocusableInTouchMode = true
        typing = edit.requestFocus()
        AppLog.d("TvTextBox", "\"$label\": typing ${if (typing) "starts" else "didn't start"}")
        if (typing) keyboard(edit, true)
    }
    // Back to the box; then on to the control [way] of it, if any.
    fun stopTyping(way: androidx.compose.ui.focus.FocusDirection? = null) {
        val edit = field
        typing = false
        runCatching { box.requestFocus() }
        if (edit != null) {
            keyboard(edit, false)
            edit.isFocusable = false
            edit.isFocusableInTouchMode = false
        }
        val moved = way?.let { runCatching { focusManager.moveFocus(it) }.getOrDefault(false) }
        AppLog.d("TvTextBox", "\"$label\": typing stops" + when (moved) { null -> ""; true -> ", moved on"; false -> ", nothing that way" })
    }
    val shape = RoundedCornerShape(Space.Small)
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val textSize = MaterialTheme.typography.bodyLarge.fontSize.value
    val lit = boxFocused || typing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .border(if (lit) FOCUSED_BORDER else BORDER, if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
                .background(Color.Transparent, shape)
                .focusRequester(box)
                .onFocusChanged { boxFocused = it.isFocused }
                // Keys while typing reach this first (Compose has them before Android's field): up and down leave.
                .onPreviewKeyEvent { event ->
                    if (!typing || event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val way = textBoxExit(event.nativeKeyEvent.keyCode, multiLine, inForm) ?: return@onPreviewKeyEvent false
                    stopTyping(way)
                    true
                }
                // On the box: OK types; in a form, up and down go to the control before or after it.
                .onKeyEvent { event ->
                    if (typing || event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onKeyEvent false
                    val key = event.nativeKeyEvent.keyCode
                    if (textBoxTypes(key)) {
                        startTyping()
                        return@onKeyEvent true
                    }
                    val way = textBoxMove(key, inForm) ?: return@onKeyEvent false
                    val moved = runCatching { focusManager.moveFocus(way) }.getOrDefault(false)
                    AppLog.d("TvTextBox", "\"$label\": ${if (way == androidx.compose.ui.focus.FocusDirection.Next) "down" else "up"} to the ${if (moved) "next control" else "page's own choice"}")
                    moved
                }
                .semantics {
                    contentDescription = "$label, ${if (kind == TvTextKind.SECRET) (if (value.isEmpty()) "empty" else "filled") else value.ifEmpty { "empty" }}"
                    onClick(label = "Type") { startTyping(); true }
                }
                .focusable()
                .testTag("tvTextBox:$label"),
        ) {
            AndroidView(
                factory = { context ->
                    EditText(context).apply {
                        field = this
                        setText(value)
                        setSelection(value.length)
                        if (multiLine) {
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
                        // Only while typing: moving around, the box is the control (see above).
                        isFocusable = false
                        isFocusableInTouchMode = false
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
                        setOnFocusChangeListener { _, has -> if (!has && typing) typing = false }
                        // Should a key reach Android's field first: up and down (one line) and Back leave typing too.
                        setOnKeyListener { _, keyCode, event ->
                            if (event.action != android.view.KeyEvent.ACTION_DOWN || !typing) return@setOnKeyListener false
                            if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                                stopTyping()
                                return@setOnKeyListener true
                            }
                            val way = textBoxExit(keyCode, multiLine, inForm) ?: return@setOnKeyListener false
                            stopTyping(way)
                            true
                        }
                        setOnEditorActionListener { view, action, _ ->
                            if (action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_GO) {
                                stopTyping()
                                done?.invoke(text.toString())
                                true
                            } else {
                                false
                            }
                        }
                        tag = value
                        if (opensOnFocus) post { startTyping() }
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
                onRelease = { edit -> keyboard(edit, false) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.Small, vertical = Space.Tight),
            )
        }
    }
}

private val BORDER = 1.dp
private val FOCUSED_BORDER = 2.dp
private const val LONG_LINES = 3

/** Whether the remote's [keyCode] on a TV text box starts typing in it (issue #955): OK, or Enter on a keyboard. */
internal fun textBoxTypes(keyCode: Int): Boolean = keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
    keyCode == android.view.KeyEvent.KEYCODE_ENTER || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER

/**
 * Which way the remote's [keyCode] leaves a TV text box while typing (issue #955): up and down to the control above or
 * below, unless the box has several lines ([multiLine]), which keep them. Left, right and OK stay in the box. In a form
 * ([inForm]) as [textBoxMove].
 */
internal fun textBoxExit(keyCode: Int, multiLine: Boolean, inForm: Boolean = true): androidx.compose.ui.focus.FocusDirection? =
    if (multiLine) null else textBoxMove(keyCode, inForm)

/**
 * Which way the remote's [keyCode] moves on from a TV text box (issue #978). In a form ([inForm]) down and up go to
 * the next and the previous control in it, in the form's order: going by distance, on the TV's wide page a narrow
 * button under a wide box (Test under a login) lost to a wide control further down, and the remote skipped it. Beside
 * other parts of the page, down and up go by distance as usual.
 */
internal fun textBoxMove(keyCode: Int, inForm: Boolean): androidx.compose.ui.focus.FocusDirection? = when (keyCode) {
    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> if (inForm) androidx.compose.ui.focus.FocusDirection.Next else androidx.compose.ui.focus.FocusDirection.Down
    android.view.KeyEvent.KEYCODE_DPAD_UP -> if (inForm) androidx.compose.ui.focus.FocusDirection.Previous else androidx.compose.ui.focus.FocusDirection.Up
    else -> null
}
