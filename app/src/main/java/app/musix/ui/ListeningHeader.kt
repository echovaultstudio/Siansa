package app.musix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController

/**
 * The top of every Your listening page (Your month, Time machine, Other artists you might like), so they
 * look like one family: Musix's violet fading into the page, back, a big title and a line under it, then
 * the page's own controls ([extra]: range chips, the year and months), all on the page's edges.
 */
@Composable
fun ListeningHeader(nav: NavController, title: String, subtitle: String, extra: @Composable ColumnScope.() -> Unit = {}) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Brand.VioletDeep, Brand.AmberDeep.copy(alpha = 0.5f), Brand.Plum)))
            .statusBarsPadding()
            .padding(bottom = Space.Page),
    ) {
        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier = Modifier.padding(horizontal = Space.Page),
        )
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = Space.Page).padding(top = Space.Tight),
            )
        }
        extra()
    }
}
