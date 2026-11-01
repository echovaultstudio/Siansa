package app.siansa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.log.AppLog

/**
 * Settings › Audiobooks and podcasts (issue #780): the book player's skip buttons (moved here from Playback) and the
 * way to the Audiobookshelf server, which is set up with the other servers.
 */
@Composable
fun BooksSettingsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    Screen(title = "Audiobooks and podcasts", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            FoldSection("Skip buttons", "Back ${settings.bookSkipBackSec} s · forward ${settings.bookSkipForwardSec} s") {
                Hint("How far the book player's skip buttons jump.")
                Text("Back", style = MaterialTheme.typography.bodyMedium)
                SkipChips(settings.bookSkipBackSec, "bookSkipBack") { graph.settings.save(graph.settings.current.copy(bookSkipBackSec = it)) }
                Text("Forward", style = MaterialTheme.typography.bodyMedium)
                SkipChips(settings.bookSkipForwardSec, "bookSkipForward") { graph.settings.save(graph.settings.current.copy(bookSkipForwardSec = it)) }
            }
            HorizontalDivider()
            PageLink(
                "Audiobookshelf",
                if (settings.booksReady) "Signed in. Change it with your other servers." else "Not set up yet. Add it with your other servers.",
                "booksServer",
            ) { nav.navigate("settings/audiobookshelf") }
        }
    }
}

/**
 * Settings › Home (issue #780): whose music shows (the source filter), Home's sections and their looks (Arrange
 * Home), and the cards. The first two open the same panel as Home's own button.
 */
@Composable
fun HomeSettingsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val shown by graph.guests.shown.collectAsStateWithLifecycle()
    var panel by remember { mutableStateOf<Boolean?>(null) }  // true: Sources, false: Arrange
    Screen(title = "Home", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            PageLink(
                "Source filter",
                homeSourcesLine(shown.size, settings.downloadedOnly),
                "homeSources",
            ) {
                AppLog.i("Settings", "Home › Source filter")
                panel = true
            }
            HorizontalDivider()
            PageLink(
                "Arrange Home",
                homeArrangeLine(settings.homeHidden.size, homeGapLabel(settings.homeGap)),
                "homeArrange",
            ) {
                AppLog.i("Settings", "Home › Arrange Home")
                panel = false
            }
            HorizontalDivider()
            PageLink("Cards", "Made for you and your own cards", "homeCards") { nav.navigate("settings/cards") }
        }
    }
    panel?.let { sources -> HomeSettingsSheet(nav, startOnSources = sources) { panel = null } }
}

/** "Your music and 1 other user · only downloaded music": what the source filter shows, under its row. */
internal fun homeSourcesLine(shownUsers: Int, downloadedOnly: Boolean): String {
    val who = when (val others = shownUsers - 1) {
        in Int.MIN_VALUE..0 -> "Your music"
        1 -> "Your music and 1 other user"
        else -> "Your music and $others other users"
    }
    return if (downloadedOnly) "$who · only downloaded music" else who
}

/** "Every section shown · Normal spacing", "2 sections hidden · Compact spacing". */
internal fun homeArrangeLine(hidden: Int, spacing: String): String {
    val sections = when (hidden) {
        0 -> "Every section shown"
        1 -> "1 section hidden"
        else -> "$hidden sections hidden"
    }
    return "$sections · $spacing spacing"
}

/** A row on a settings page that opens another page or panel: its name, a line of what's set, and an arrow. */
@Composable
internal fun PageLink(title: String, line: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Open $title", onClick = onClick).padding(vertical = Space.Tight).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Heading(title)
            Hint(line)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
