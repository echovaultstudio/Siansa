package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A box to find something in the list under it as you type: a pill with a magnifier, [hint] while it's empty, and a
 * cross to empty it again. For lists that are all on the phone already (Flow's sounds and genres, the page of all
 * genres): nothing is asked of a server.
 */
@Composable
internal fun FindField(text: String, hint: String, tag: String, modifier: Modifier = Modifier, onText: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .height(FIND_FIELD)
            .clip(CircleShape)
            .background(scheme.surfaceContainer)
            .padding(start = Space.Medium, end = Space.Tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = text,
                onValueChange = { onText(it.take(FIND_MOST)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().testTag(tag),
            )
        }
        Box(Modifier.size(40.dp).clip(CircleShape).then(if (text.isEmpty()) Modifier else Modifier.clickable(onClickLabel = "Clear") { onText("") }), contentAlignment = Alignment.Center) {
            if (text.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear", Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
        }
    }
}

private val FIND_FIELD = 48.dp
private const val FIND_MOST = 40
