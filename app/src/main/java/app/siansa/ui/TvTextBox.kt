package app.siansa.ui

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.background
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
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Space.Small)
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val textSize = MaterialTheme.typography.bodyLarge.fontSize.value
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        AndroidView(
            factory = { context ->
                EditText(context).apply {
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
    }
}

private val BORDER = 1.dp
private val FOCUSED_BORDER = 2.dp
private const val LONG_LINES = 3
