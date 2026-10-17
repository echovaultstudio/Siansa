package app.musix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.musix.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Settings → What's new: the installed version (opens its GitHub release) and the changes in each version. */
@Composable
fun WhatsNewScreen(nav: NavController) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    var blocks by remember { mutableStateOf<List<ChangelogBlock>?>(null) }
    LaunchedEffect(Unit) {
        val read = withContext(Dispatchers.IO) {
            try {
                parseChangelog(context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("WhatsNew", "Changelog not readable: ${e.message}")
                emptyList<ChangelogBlock>()
            }
        }
        AppLog.d("WhatsNew", "Changelog: ${read.count { it is ChangelogBlock.Version }} versions")
        blocks = read
    }

    fun openRelease(url: String) {
        AppLog.i("WhatsNew", "Opening a release page on GitHub")
        try {
            uriHandler.openUri(url)
        } catch (e: Exception) {
            AppLog.w("WhatsNew", "No app to open the release page: ${e.message}")
        }
    }

    Screen(title = "What's new", nav = nav) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
        ) {
            item(key = "version") { VersionCard(version) { openRelease(releaseUrl(version)) } }
            val list = blocks
            when {
                list == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                list.isEmpty() -> item(key = "empty") {
                    Text(
                        "The list of changes isn't available in this version.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                else -> itemsIndexed(list) { _, block ->
                    ChangelogItem(block, current = version) { openRelease(releaseUrl(it)) }
                }
            }
        }
    }
}

@Composable
private fun VersionCard(version: String, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Musix ${version.ifEmpty { "?" }}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "You're on this version. Tap to see it on GitHub.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open on GitHub", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ChangelogItem(block: ChangelogBlock, current: String, onOpenVersion: (String) -> Unit) {
    when (block) {
        is ChangelogBlock.Version -> Row(
            Modifier
                .fillMaxWidth()
                .clickable { onOpenVersion(block.version) }
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(block.version, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (block.version == current) {
                Text(
                    "Installed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (block.date.isNotEmpty()) {
                Text(block.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is ChangelogBlock.Section -> Text(
            block.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        is ChangelogBlock.Bullet -> Row(
            Modifier.fillMaxWidth().padding(start = 16.dp + 18.dp * block.level, end = 16.dp, top = 3.dp, bottom = 3.dp),
        ) {
            Text(
                if (block.level == 0) "•" else "◦",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(styled(block.text), style = MaterialTheme.typography.bodyMedium)
        }
        is ChangelogBlock.Paragraph -> Text(
            styled(block.text),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

private fun styled(text: String): AnnotatedString = buildAnnotatedString {
    for (span in changelogSpans(text)) {
        if (span.bold) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(span.text) }
        else append(span.text)
    }
}
