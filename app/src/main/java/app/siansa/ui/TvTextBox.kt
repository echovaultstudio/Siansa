package app.siansa.ui

import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.siansa.log.AppLog

/** What's typed into a [TvTextBox], for Android's keyboard. */
enum class TvTextKind(val inputType: Int) {
    TEXT(InputType.TYPE_CLASS_TEXT),
    ADDRESS(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS),
    SECRET(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
    LONG_TEXT(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES),
    SEARCH(InputType.TYPE_CLASS_TEXT),
}

/**
 * A text field for the TV app (issue #860). It shows what's in it; OK (or, with [opensOnFocus], just reaching it) opens
 * Android's own text box over the page, with the keyboard up. Typed with the remote, the TV's microphone, or a phone:
 * the Google TV app's keyboard and pasting from the phone work there, as in other apps, which they didn't in Siansa's own
 * fields. What's typed shows straight away ([onValueChange] on every change); OK or the keyboard's Done closes the box,
 * Back closes it too, and Cancel puts back what was there before.
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
    /** The keyboard's Done (or Search): after the box closes, with what's in it. */
    onDone: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val change by rememberUpdatedState(onValueChange)
    val done by rememberUpdatedState(onDone)
    val current by rememberUpdatedState(value)
    var focused by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<AlertDialog?>(null) }
    // When the box last closed: reaching the field again straight after (the focus coming back to it) doesn't reopen it.
    var closedAt by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    DisposableEffect(Unit) { onDispose { open?.dismiss() } }

    fun type() {
        if (open?.isShowing == true) return
        open = typingBox(context, label, current, kind, onChange = { change(it) }, onDone = { done?.invoke(it) },
            onClosed = { closedAt = android.os.SystemClock.elapsedRealtime() })
    }

    val shape = RoundedCornerShape(Space.Small)
    val shown = when {
        value.isEmpty() -> placeholder ?: if (focused) "Press OK to type" else ""
        kind == TvTextKind.SECRET -> "•".repeat(value.length.coerceAtMost(SECRET_DOTS))
        else -> value
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.Tight)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            shown,
            style = MaterialTheme.typography.bodyLarge,
            color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = if (kind == TvTextKind.LONG_TEXT) LONG_LINES else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    val was = focused
                    focused = it.isFocused
                    val justClosed = android.os.SystemClock.elapsedRealtime() - closedAt < REOPEN_AFTER_MS
                    if (opensOnFocus && it.isFocused && !was && !justClosed) type()
                }
                .border(if (focused) FOCUSED_BORDER else BORDER, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
                .background(Color.Transparent, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { type() }
                .padding(horizontal = Space.Page, vertical = Space.Medium),
        )
    }
}

/** Android's own text box, over the page, with the keyboard up. */
private fun typingBox(
    context: Context,
    title: String,
    value: String,
    kind: TvTextKind,
    onChange: (String) -> Unit,
    onDone: (String) -> Unit,
    onClosed: () -> Unit,
): AlertDialog {
    val density = context.resources.displayMetrics.density
    val edit = EditText(context).apply {
        setText(value)
        setSelection(value.length)
        inputType = kind.inputType
        if (kind == TvTextKind.LONG_TEXT) {
            setMinLines(LONG_LINES)
            setMaxLines(LONG_LINES * 2)
        } else {
            setSingleLine(true)
        }
        imeOptions = (if (kind == TvTextKind.SEARCH) EditorInfo.IME_ACTION_SEARCH else EditorInfo.IME_ACTION_DONE) or EditorInfo.IME_FLAG_NO_FULLSCREEN
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = onChange(s?.toString().orEmpty())
        })
    }
    val frame = FrameLayout(context).apply {
        val side = (Space.Section.value * density).toInt()
        setPadding(side, (Space.Small.value * density).toInt(), side, 0)
        addView(edit)
    }
    var finished = false
    val dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        .setTitle(title)
        .setView(frame)
        .setPositiveButton("OK") { _, _ -> }
        .setNegativeButton("Cancel") { _, _ -> finished = true; onChange(value) }
        .create()
    fun finish() {
        if (finished) return
        finished = true
        val text = edit.text.toString()
        dialog.dismiss()
        onDone(text)
    }
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener { finish() }
        edit.requestFocus()
        edit.post { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT) }
    }
    edit.setOnEditorActionListener { _, action, _ ->
        if (action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_GO) {
            finish()
            true
        } else {
            false
        }
    }
    dialog.setOnDismissListener { onClosed() }
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    runCatching { dialog.show() }.onFailure { AppLog.w("Tv", "Couldn't open the text box for $title: ${it.message}") }
    return dialog
}

private val BORDER = 1.dp
private val FOCUSED_BORDER = 2.dp
private const val SECRET_DOTS = 12
private const val LONG_LINES = 3
private const val REOPEN_AFTER_MS = 1_000L
