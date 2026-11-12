package app.siansa.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.siansa.data.Album
import app.siansa.data.AlbumCategory
import app.siansa.data.category
import app.siansa.log.AppLog

/**
 * The release types an artist has (Albums, EPs, Singles, DJ-mixes…), in display order, for the chips on
 * their page. Empty when there's only one type, since a filter would change nothing.
 */
fun releaseTypeChoices(albums: List<Album>): List<AlbumCategory> {
    val types = albums.map { it.category }.distinct().sortedBy { it.ordinal }
    return if (types.size < 2) emptyList() else types
}

/** The chosen type, saved by name; nothing when it's unknown or the artist no longer has that type. */
fun chosenReleaseType(saved: String?, choices: List<AlbumCategory>): AlbumCategory? =
    choices.firstOrNull { it.name == saved }

/** The discography groups to show: all of them, or only the chosen type's. */
fun <T> onlyReleaseType(groups: List<Pair<AlbumCategory, T>>, type: AlbumCategory?): List<Pair<AlbumCategory, T>> =
    if (type == null) groups else groups.filter { it.first == type }

/** The space between the release-type tabs and the first heading under them. */
val TABS_GAP = Space.Small

/** The chosen release type's name, kept while the page is open (also across rotation), never saved for good. */
@Composable
fun rememberReleaseTypeFilter(): MutableState<String?> = rememberSaveable { mutableStateOf<String?>(null) }

/**
 * The artist's release types as tabs at the top of their page (issue #1029): **All**, then each type they have
 * (Albums, EPs, Singles, Live, DJ-mixes…). The chosen one is bold with a line under it in the accent colour. An
 * artist with many types scrolls them sideways, fading out at an edge that has more, and the chosen tab is
 * scrolled into view. [content] is the words' colour: the page's, or white under the title bar.
 */
@Composable
fun ReleaseTypeTabs(
    choices: List<AlbumCategory>,
    selected: AlbumCategory?,
    modifier: Modifier = Modifier,
    content: Color = MaterialTheme.colorScheme.onSurface,
    onSelect: (AlbumCategory?) -> Unit,
) {
    val pick: (AlbumCategory?) -> Unit = { type ->
        AppLog.d("Artist", "Release type tab: ${type?.name ?: "all"}")
        onSelect(type)
    }
    val scroll = rememberScrollState()
    val tabs = listOf<AlbumCategory?>(null) + choices
    // Where each tab starts and ends in the row, to bring the chosen one into view.
    val spans = remember(tabs) { mutableStateMapOf<Int, Pair<Int, Int>>() }
    val chosen = tabs.indexOf(selected).coerceAtLeast(0)
    LaunchedEffect(chosen, spans[chosen]) {
        val (from, to) = spans[chosen] ?: return@LaunchedEffect
        val view = scroll.viewportSize
        if (view > 0 && (from < scroll.value || to > scroll.value + view)) scroll.animateScrollTo((from - (view - (to - from)) / 2).coerceAtLeast(0))
    }
    val edge = with(LocalDensity.current) { TABS_EDGE.toPx() }
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Fade out at an edge with more tabs beyond it, so it's clear the row scrolls.
                val left = if (scroll.value > 0) edge else 0f
                val right = if (scroll.value < scroll.maxValue) edge else 0f
                if (left > 0f) drawRect(Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = 0f, endX = left), blendMode = BlendMode.DstIn)
                if (right > 0f) drawRect(Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = size.width - right, endX = size.width), blendMode = BlendMode.DstIn)
            }
            .horizontalScroll(scroll)
            .padding(horizontal = Space.Tight)
            .selectableGroup()
            .testTag("releaseTypeTabs"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { i, type ->
            TypeTab(
                text = type?.plural ?: "All",
                active = i == chosen,
                content = content,
                modifier = Modifier.onGloballyPositioned { spans[i] = it.positionInParent().x.toInt() to (it.positionInParent().x + it.size.width).toInt() },
            ) { pick(if (i == chosen) null else type) }
        }
    }
}

/** One tab: its name, with the accent line under it while chosen. Tapping the chosen tab goes back to All. */
@Composable
private fun TypeTab(text: String, active: Boolean, content: Color, modifier: Modifier, onClick: () -> Unit) {
    val line = Accent
    val lineWidth = with(LocalDensity.current) { TAB_LINE.toPx() }
    Box(
        modifier
            .selectable(selected = active, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Space.Medium, vertical = Space.Medium)
            .testTag(if (active) "releaseTypeTabChosen" else "releaseTypeTab"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontSize = 15.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) content else content.copy(alpha = INACTIVE_TAB),
            maxLines = 1,
            modifier = Modifier.drawBehind {
                if (active) {
                    val y = size.height + lineWidth * 2.5f
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = lineWidth, cap = StrokeCap.Round)
                }
            },
        )
    }
}

/** How faint a tab that isn't chosen is. */
private const val INACTIVE_TAB = 0.66f

/** The chosen tab's line. */
private val TAB_LINE = 3.dp

/** How wide the fade is at an edge of the tabs with more beyond it. */
private val TABS_EDGE = 28.dp
