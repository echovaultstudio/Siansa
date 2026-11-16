package app.siansa.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import app.siansa.BuildConfig
import app.siansa.log.AppLog
import app.siansa.update.UpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What an update brought, in short (the owner, 16 Nov: "flashes a summary changelog on home screen after the user
 * updates the app, only major changes and fixes and pointing to full changelog link"). [lines] are the headlines of
 * the biggest changes, new things first; [more] is how many other changes the full list has.
 */
data class UpdateSummary(val version: String, val lines: List<String>, val more: Int)

object UpdateNews {
    /** At most this many headlines: a summary, not the list. */
    const val MAX = 5
    private const val PREFS = "update_news"

    /** New things before fixes; a section not named here comes last. */
    private val SECTION_ORDER = listOf("highlights", "added", "changed", "fixed")

    /**
     * Whether this start is the first after an update. [seenCode] is the build the summary was last settled for (null
     * when never): a higher [code] is an update. Never settled, the phone's own dates tell an update (updated after it
     * was first installed) from a fresh install, which shows nothing.
     */
    fun updated(seenCode: Long?, code: Long, firstInstall: Long, lastUpdate: Long): Boolean =
        if (seenCode != null) code > seenCode else lastUpdate > firstInstall

    /**
     * The summary from the changelog's [blocks] for [installed]: the versions after [seen] (the version last
     * summarised) up to the installed one, so an update over several versions misses nothing; with [seen] unknown or
     * not older, the newest version alone. Only the changes that open with a bold headline count as big ones, and only
     * the headline is shown. Null when there's no such change.
     */
    fun summary(blocks: List<ChangelogBlock>, installed: String, seen: String?, max: Int = MAX): UpdateSummary? {
        // Each change with its version and section, in the file's order (newest version first).
        data class Change(val version: String, val section: String, val text: String)
        val changes = mutableListOf<Change>()
        var version = ""
        var section = ""
        for (block in blocks) {
            when (block) {
                is ChangelogBlock.Version -> { version = block.version; section = "" }
                is ChangelogBlock.Section -> section = block.title.trim().lowercase()
                is ChangelogBlock.Bullet -> if (block.level == 0 && version.isNotEmpty()) changes += Change(version, section, block.text)
                is ChangelogBlock.Paragraph -> Unit
            }
        }
        // Versions newer than the installed one aren't in this app (a changelog ahead of the build).
        val reached = changes.filter { !UpdateCheck.isNewer(it.version, installed) }
        val newest = reached.firstOrNull()?.version ?: return null
        val since = reached.filter { seen != null && UpdateCheck.isNewer(it.version, seen) }
        val picked = since.ifEmpty { reached.filter { it.version == newest } }
        val headlines = picked
            .mapNotNull { change -> headline(change.text)?.let { change.section to it } }
            .sortedBy { (section, _) -> SECTION_ORDER.indexOf(section).let { if (it < 0) SECTION_ORDER.size else it } }
            .map { it.second }
            .distinct()
        if (headlines.isEmpty()) return null
        val lines = headlines.take(max)
        return UpdateSummary(newest, lines, (picked.size - lines.size).coerceAtLeast(0))
    }

    /** A change's bold opening ("**Covers flashed.** They now…" gives "Covers flashed"), or null when it has none. */
    fun headline(text: String): String? {
        val first = changelogSpans(text).firstOrNull()?.takeIf { it.bold } ?: return null
        return first.text.trim().trimEnd('.', ':').trim().takeIf { it.isNotEmpty() }
    }

    /**
     * The summary to show now, or null: only on the first start after an update, never after a fresh install (which is
     * settled quietly), and not on the TV. Reads the changelog that comes with the app; nothing is fetched.
     */
    fun pending(context: Context): UpdateSummary? {
        if (BuildConfig.TV_APP) return null
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val seenCode = if (prefs.contains("code")) prefs.getLong("code", 0L) else null
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = BuildConfig.VERSION_CODE.toLong()
            if (!updated(seenCode, code, info.firstInstallTime, info.lastUpdateTime)) {
                if (seenCode == null) settle(context)
                return null
            }
            val blocks = parseChangelog(context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() })
            val summary = summary(blocks, BuildConfig.VERSION_NAME, prefs.getString("version", null))
            AppLog.i("WhatsNew", "Updated to ${BuildConfig.VERSION_NAME} ($code): ${summary?.let { "${it.lines.size} headlines, ${it.more} more" } ?: "nothing to sum up"}")
            if (summary == null) settle(context)
            summary
        } catch (e: Exception) {
            AppLog.w("WhatsNew", "No summary after the update: ${e.message}")
            null
        }
    }

    /** This build's summary is done with (seen, or there was none): it won't show again until the next update. */
    fun settle(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("code", BuildConfig.VERSION_CODE.toLong())
            .putString("version", BuildConfig.VERSION_NAME)
            .apply()
    }
}

/**
 * Home's part of it: once after an update, the summary comes up as a sheet over Home. Closing it any way settles it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewAfterUpdate() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var summary by remember { mutableStateOf<UpdateSummary?>(null) }
    LaunchedEffect(Unit) {
        summary = withContext(Dispatchers.IO) { UpdateNews.pending(context) }
    }
    val shown = summary ?: return
    fun done() {
        UpdateNews.settle(context)
        summary = null
    }
    ModalBottomSheet(onDismissRequest = ::done, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        WhatsNewPanel(
            shown,
            onOpenAll = {
                AppLog.i("WhatsNew", "Opening the full changelog on GitHub")
                try {
                    uriHandler.openUri(FULL_CHANGELOG_URL)
                } catch (e: Exception) {
                    AppLog.w("WhatsNew", "No app to open the changelog: ${e.message}")
                }
            },
            onDone = ::done,
            modifier = Modifier.navigationBarsPadding().padding(bottom = Space.Small),
        )
    }
}

/** The sheet's rows: its title and version, the headlines, and the way to every version's changes. */
@Composable
internal fun WhatsNewPanel(summary: UpdateSummary, onOpenAll: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("whatsNewPanel")) {
        Column(Modifier.padding(horizontal = Space.Page).testTag("whatsNewTitle")) {
            Text("What's new", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Siansa ${summary.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Space.Small))
        summary.lines.forEachIndexed { i, line ->
            // As a change on the What's new page: a dot in the accent colour, then the words.
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.Page, vertical = Space.Tight).testTag("whatsNewLine-$i")) {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = Space.Small))
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (summary.more > 0) {
            Text(
                if (summary.more == 1) "And 1 more change" else "And ${summary.more} more changes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.Page, end = Space.Page, top = Space.Tight).testTag("whatsNewMore"),
            )
        }
        Row(Modifier.fillMaxWidth().padding(start = Space.Small, end = Space.Small, top = Space.Small).testTag("whatsNewButtons"), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpenAll, modifier = Modifier.focusRing().testTag("whatsNewAll")) { Text("Every version's changes") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone, modifier = Modifier.focusRing().testTag("whatsNewDone")) { Text("Done") }
        }
    }
}
