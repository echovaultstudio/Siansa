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
import app.siansa.data.HomeMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp

/**
 * Settings › Home page (the owner, 13 Nov; Notices and Cards, Style › Home and Style › Home Menu before): the top of
 * Home (how many shortcuts, the notices and each kind), the order of Home's menu, and the cards' page. The source
 * filter and Arrange Home are only behind the button at the top of Home, where they act on what's in front of you.
 */
@Composable
fun HomeSettingsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val shown = HomeSection.NOTICES !in settings.homeHidden
    Screen(title = "Home page", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            FoldSection("Top of Home", "${settings.homeShortcuts} shortcuts · notices ${if (shown) "on" else "off"}") {
                Hint("The shortcuts at the top of Home are what you played last, newest first.")
                ShortcutCountRow(settings.homeShortcuts, edge = 0.dp) {
                    AppLog.i("Style", "Home shortcuts: $it")
                    graph.settings.setHomeShortcuts(it)
                }
                SwitchRow("Notices", shown) { on ->
                    AppLog.i("Settings", "Notices on Home: $on")
                    graph.settings.setHomeHidden(if (on) settings.homeHidden - HomeSection.NOTICES else settings.homeHidden + HomeSection.NOTICES)
                }
                Hint("A row of chips at the top of Home. Automatic puts what needs you first, then what you're in the middle of, then what's new. Pinned ones always come first.")
                if (shown) NoticeKinds(settings.notices) { kind, mode ->
                    AppLog.i("Settings", "Notices › ${kind.label}: ${mode.label}")
                    graph.settings.setNoticeMode(kind, mode)
                }
            }
            HorizontalDivider()
            FoldSection("Home menu", "${settings.homeMenu.size} options") {
                Hint("The round button at the top of Home. Hold a row and drag it: top to bottom as they open under the button.")
                ArrangeList(
                    order = settings.homeMenu,
                    shown = settings.homeMenu.toSet(),
                    label = { it.label },
                    icon = { it.icon() },
                    key = { it.id },
                    canHide = { false },
                    canShowMore = false,
                    onChange = { items, _ ->
                        AppLog.i("Style", "Home menu: ${HomeMenuItem.format(items)}")
                        graph.settings.setHomeMenu(items)
                    },
                    eyes = false,
                    edge = 0.dp,
                )
                if (settings.homeMenu != HomeMenuItem.default) {
                    TextButton(onClick = { graph.settings.setHomeMenu(HomeMenuItem.default) }) { Text("Reset the order") }
                }
            }
            HorizontalDivider()
            PageLink("Cards", "Made for you and your own cards", "homeCards") { nav.navigate("settings/cards") }
        }
    }
}

/** Each kind of notice with its mode (pinned, automatic, off), as Home page shows them. */
@Composable
internal fun NoticeKinds(prefs: NoticePrefs, onPick: (NoticeKind, NoticeMode) -> Unit) {
    NoticeKind.entries.forEach { kind ->
        Column(Modifier.testTag("noticeKind"), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
            Column {
                Text(kind.label, style = MaterialTheme.typography.bodyLarge)
                Hint(kind.hint)
            }
            Row(Modifier.tvFocusGroup().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                NoticeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = prefs.modeOf(kind) == mode,
                        onClick = { onPick(kind, mode) },
                        label = { Text(mode.label) },
                        modifier = Modifier.testTag("notice-${kind.id}-${mode.id}"),
                    )
                }
            }
        }
    }
}

/**
 * Notices on their own page (issue #784; since the owner's 13 Nov they're on Home page too, and this page is kept for
 * old links): the chips at the top of Home, shown or not, and each kind pinned (always first, in the order pinned),
 * automatic (ordered by what matters and what you tap) or off.
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
                        Row(Modifier.tvFocusGroup().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
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
    // A kind that's off until you add it (Liked songs) isn't counted as turned off.
    val off = NoticeKind.entries.count { prefs.modeOf(it) == NoticeMode.OFF && it.defaultMode != NoticeMode.OFF }
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
