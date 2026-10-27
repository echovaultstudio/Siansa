package app.siansa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import app.siansa.log.AppLog

/** Where About's links go (#576). */
internal object AboutLinks {
    /** The libraries in the app and their licences, on the website (deploy/site/licenses.html). */
    const val LICENCES = "https://siansa.app/licenses"
    /** The user docs. They're in the public beta testers repository until docs.siansa.app is up. */
    const val DOCS = "https://github.com/echovaultstudio/siansa-beta-testers/tree/main/docs"
    const val PRIVACY = "https://siansa.app/privacy"

    /** The links in the order About shows them: what it's called, what it says under it, where it goes. */
    val all = listOf(
        Triple("Open-source licences", "The libraries Siansa is built on", LICENCES),
        Triple("Docs", "How Siansa works, step by step", DOCS),
        Triple("Privacy policy", "What Siansa keeps, and where", PRIVACY),
    )
}

/**
 * Settings › About (#576): the version, and links to the open-source licences on siansa.app, the docs and the privacy
 * policy. Each opens in the browser.
 */
@Composable
fun AboutScreen(nav: NavController) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    Screen(title = "About", nav = nav) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = Space.Page, vertical = Space.Small)) {
                Text("Siansa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Version $version",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("aboutVersion"),
                )
            }
            AboutLinks.all.forEachIndexed { i, (title, line, url) ->
                val icon = listOf(Icons.Rounded.Code, Icons.AutoMirrored.Rounded.MenuBook, Icons.Rounded.PrivacyTip)[i]
                AboutRow(icon, title, line, "aboutLink-$i") {
                    AppLog.i("About", "Open $title")
                    runCatching { uri.openUri(url) }.onFailure { AppLog.w("About", "Couldn't open $url: ${it.message}") }
                }
            }
        }
    }
}

/** A link on About: its icon, name and a line under it, the whole row opening it. */
@Composable
private fun AboutRow(icon: ImageVector, title: String, line: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Open $title", onClick = onClick).padding(horizontal = Space.Page, vertical = Space.Medium).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = Space.Page)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
