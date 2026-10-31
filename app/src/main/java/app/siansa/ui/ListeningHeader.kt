package app.siansa.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
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
 * look like one family: soft violet, rose and amber light fading into the page (issue #325), back, a big title and a line under it, then
 * the page's own controls ([extra]: range chips, the year and months), all on the page's edges.
 */
@Composable
fun ListeningHeader(nav: NavController, title: String, subtitle: String, extra: @Composable ColumnScope.() -> Unit = {}) {
    Column(
        Modifier
            .fillMaxWidth()
            .listeningBackdrop(MaterialTheme.colorScheme.background)
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

/**
 * The Your listening header's background (issue #325): the page's own colour ([page]; grey, issue #761) lit by soft glows, a violet one from the top
 * left, a warm amber one from the right and a hint of rose between them, fading smoothly into the page at the
 * bottom so there's no edge. Like a stage light on a dark backdrop, instead of one flat band of colour.
 */
fun Modifier.listeningBackdrop(page: Color): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    drawRect(page)
    drawRect(Brush.radialGradient(listOf(Brand.VioletDeep.copy(alpha = 0.95f), Color.Transparent), center = Offset(w * 0.12f, h * 0.05f), radius = w * 1.05f))
    drawRect(Brush.radialGradient(listOf(Color(0xFFE5508F).copy(alpha = 0.38f), Color.Transparent), center = Offset(w * 0.62f, h * 0.18f), radius = w * 0.62f))
    drawRect(Brush.radialGradient(listOf(Brand.Amber.copy(alpha = 0.42f), Color.Transparent), center = Offset(w * 1.02f, h * 0.48f), radius = w * 0.78f))
    // A faint light along the top edge, then into the page's own colour at the bottom.
    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.06f), 0.35f to Color.Transparent))
    drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to page))
}
