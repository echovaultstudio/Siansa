package app.siansa.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import app.siansa.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where help and the source code are asked for. */
internal const val HELP_EMAIL = "help@siansa.app"

/**
 * The name a version's source has in the beta F-Droid repo, next to its APK (deploy/scripts/siansa-source keeps it
 * under F-Droid's own name for a version's source).
 */
internal fun sourceFileName(versionCode: Long): String = "app.siansa_${versionCode}_src.tar.gz"

/** What the email asking for the source says, so the right version is sent. */
internal fun sourceRequestSubject(version: String, versionCode: Long): String =
    "Siansa source code for ${version.ifEmpty { "?" }} ($versionCode)"

/**
 * Settings → About and license: the version, the GPL in plain words, where to get this version's source and the full
 * license text (from the APK's assets, put there by the build).
 */
@Composable
fun AboutScreen(nav: NavController) {
    val context = LocalContext.current
    val (version, code) = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            info.versionName.orEmpty() to (if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong())
        }.getOrDefault("" to 0L)
    }
    var showLicense by remember { mutableStateOf(false) }
    var license by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(showLicense) {
        if (!showLicense || license != null) return@LaunchedEffect
        license = withContext(Dispatchers.IO) {
            try {
                context.assets.open("LICENSE.txt").bufferedReader().use { it.readText() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("About", "License text not readable: ${e.message}")
                "The license text isn't in this version. Read it at https://www.gnu.org/licenses/gpl-3.0.html"
            }
        }
    }

    fun askForSource() {
        AppLog.i("About", "Asking for the source of $version ($code)")
        val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$HELP_EMAIL")).apply {
            putExtra(Intent.EXTRA_SUBJECT, sourceRequestSubject(version, code))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(mail)
        } catch (e: Exception) {
            AppLog.w("About", "No email app: ${e.message}")
        }
    }

    Screen(title = "About and license", nav = nav) { padding ->
        AboutContent(
            version, code, showLicense, license,
            onToggleLicense = { showLicense = !showLicense },
            onAskForSource = ::askForSource,
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
        )
    }
}

/** The page itself, apart from loading, so its spacing can be checked (SpacingTest). */
@Composable
internal fun AboutContent(
    version: String,
    code: Long,
    showLicense: Boolean,
    license: String?,
    onToggleLicense: () -> Unit,
    onAskForSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.Page, vertical = Space.Page),
        verticalArrangement = Arrangement.spacedBy(Space.Medium),
    ) {
        Text("Siansa ${version.ifEmpty { "?" }}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Siansa is free software. You may use, share and change it under the GNU General Public License, " +
                "version 3 or any later version. It comes with no warranty.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Source code", style = MaterialTheme.typography.titleSmall)
        Text(
            "Everyone who has Siansa can get its source code. Beta testers find each version's source next to it " +
                "in their F-Droid repo, as ${sourceFileName(code)}. Or email us and we'll send it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("sourceText"),
        )
        OutlinedButton(onClick = onAskForSource, modifier = Modifier.testTag("askForSource")) { Text("Email for the source") }
        Text("Other software", style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("otherSoftware"))
        Text(
            "Siansa is built with open-source libraries, among them AndroidX Media3 and Jetpack Compose (Apache 2.0), " +
                "Coil (Apache 2.0) and Jellyfin's FFmpeg decoder (GPL 3).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onToggleLicense, modifier = Modifier.testTag("showLicense")) {
            Text(if (showLicense) "Hide the license" else "Read the license")
        }
        if (showLicense) {
            SelectionContainer {
                Text(
                    license ?: "Loading…",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
