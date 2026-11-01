package app.siansa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
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
import app.siansa.data.HomeSection
import app.siansa.log.AppLog
import app.siansa.mix.NoticeKind
import app.siansa.mix.NoticeMode
import app.siansa.mix.NoticePrefs

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
 * Settings › Home (issue #780): the notices and the cards. The source filter and Arrange Home are only behind the
 * button at the top of Home, where they act on what's in front of you.
 */
@Composable
fun HomeSettingsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    Screen(title = "Home", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            // Source filter and Arrange Home are only behind the button at the top of Home.
            PageLink("Notices", noticesLine(HomeSection.NOTICES !in settings.homeHidden, settings.notices), "homeNotices") {
                nav.navigate("settings/home/notices")
            }
            HorizontalDivider()
            PageLink("Cards", "Made for you and your own cards", "homeCards") { nav.navigate("settings/cards") }
        }
    }
}

/**
 * Settings › Home › Notices (issue #784): the chips at the top of Home, shown or not, and each kind pinned (always
 * first, in the order pinned), automatic (ordered by what matters and what you tap) or off.
 */
@Composable
fun NoticesSettingsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val shown = HomeSection.NOTICES !in settings.homeHidden
    Screen(title = "Notices", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            SwitchRow("Show notices on Home", shown) { on ->
                AppLog.i("Settings", "Notices on Home: $on")
                graph.settings.setHomeHidden(if (on) settings.homeHidden - HomeSection.NOTICES else settings.homeHidden + HomeSection.NOTICES)
            }
            Hint("A row of chips at the top of Home. Automatic puts what needs you first, then what you're in the middle of, then what's new. Pinned ones always come first.")
            if (shown) {
                NoticeKind.entries.forEach { kind ->
                    HorizontalDivider()
                    Column(Modifier.testTag("noticeKind"), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                        Column {
                            Heading(kind.label)
                            Hint(kind.hint)
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                            NoticeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.notices.modeOf(kind) == mode,
                                    onClick = {
                                        AppLog.i("Settings", "Notices › ${kind.label}: ${mode.label}")
                                        graph.settings.setNoticeMode(kind, mode)
                                    },
                                    label = { Text(mode.label) },
                                    modifier = Modifier.testTag("notice-${kind.id}-${mode.id}"),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Shown · 1 pinned · 2 off", "Hidden": what's set, under Notices' row. */
internal fun noticesLine(shown: Boolean, prefs: NoticePrefs): String {
    if (!shown) return "Hidden"
    val off = NoticeKind.entries.count { prefs.modeOf(it) == NoticeMode.OFF }
    return listOfNotNull(
        "Shown",
        prefs.pinned.size.takeIf { it > 0 }?.let { "$it pinned" },
        off.takeIf { it > 0 }?.let { "$it off" },
    ).joinToString(" · ")
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
