package app.siansa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.siansa.data.Album
import app.siansa.data.AlbumCategory
import kotlinx.coroutines.launch
import app.siansa.data.category
import app.siansa.log.AppLog
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer

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
 * The artist's release types at the top of their page (issue #1029), as one bar of pills like Your Library's (the owner,
 * 14 Nov): **All**, then each type they have (Albums, EPs, Singles, Live, DJ-mixes…). The chosen one is filled in the
 * accent colour. The first pill starts at the page's edge; with more than fit, the row scrolls sideways, and while some
 * are hidden off the right, it fades out there into [fade] (the page's colour, or the title bar's when pinned) with a ›
 * that scrolls on, [moreInset] in from the right edge (on the artist page, under the Play button). The chosen one is
 * scrolled into view. Tapping the chosen one goes back to All.
 */
@Composable
fun ReleaseTypeTabs(
    choices: List<AlbumCategory>,
    selected: AlbumCategory?,
    modifier: Modifier = Modifier,
    // What the row's end fades into before the arrow: the page behind it, or null when that isn't one colour (pinned
    // under the title bar, on its wash): the pills themselves fade out there.
    fade: Color? = MaterialTheme.colorScheme.surface,
    moreInset: Dp = Space.Small,
    onSelect: (AlbumCategory?) -> Unit,
) {
    val pick: (AlbumCategory?) -> Unit = { type ->
        AppLog.d("Artist", "Release type: ${type?.name ?: "all"}")
        onSelect(type)
    }
    val scroll = rememberScrollState()
    val tabs = listOf<AlbumCategory?>(null) + choices
    // Where each pill starts and ends in the row, to bring the chosen one into view.
    val spans = remember(tabs) { mutableStateMapOf<Int, Pair<Int, Int>>() }
    val chosen = tabs.indexOf(selected).coerceAtLeast(0)
    LaunchedEffect(chosen, spans[chosen]) {
        val (from, to) = spans[chosen] ?: return@LaunchedEffect
        val view = scroll.viewportSize
        if (view > 0 && (from < scroll.value || to > scroll.value + view)) scroll.animateScrollTo((from - (view - (to - from)) / 2).coerceAtLeast(0))
    }
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (fade == null && scroll.canScrollForward) Modifier
                        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val width = (moreInset + MORE_BUTTON + MORE_BLEND).toPx()
                            drawRect(
                                Brush.horizontalGradient(0f to Color.Black, 0.55f to Color.Transparent, 1f to Color.Transparent, startX = size.width - width, endX = size.width),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                            )
                        }
                    else Modifier,
                )
                .horizontalScroll(scroll)
                // Little above and below: each pill is tapped in a 48 dp area round its 36, which adds the rest.
                .padding(horizontal = Space.Page, vertical = Space.Tight)
                .selectableGroup()
                .testTag("releaseTypeTabs"),
            horizontalArrangement = Arrangement.spacedBy(Space.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { i, type ->
                TypePill(
                    text = type?.plural ?: "All",
                    active = i == chosen,
                    modifier = Modifier.onGloballyPositioned { spans[i] = it.positionInParent().x.toInt() to (it.positionInParent().x + it.size.width).toInt() },
                ) { pick(if (i == chosen) null else type) }
            }
        }
        if (scroll.canScrollForward) {
            val rowScope = rememberCoroutineScope()
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                // Clear to [fade] from the middle of the blend: the ›'s space, nothing shows through it.
                if (fade != null) Box(
                    Modifier.fillMaxHeight().width(moreInset + MORE_BUTTON + MORE_BLEND).testTag("releaseTypesFade")
                        .background(Brush.horizontalGradient(0f to fade.copy(alpha = 0f), 0.45f to fade, 1f to fade)),
                )
            }
            Surface(
                onClick = { rowScope.launch { scroll.animateScrollBy(scroll.viewportSize * 0.7f) } },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = moreInset).size(MORE_BUTTON).testTag("releaseTypesMore"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "More release types", Modifier.size(MORE_ICON), tint = Accent)
                }
            }
        }
    }
}

/** One pill, as Your Library's: filled in the accent colour while chosen. */
@Composable
private fun TypePill(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        selected = active,
        onClick = onClick,
        shape = CircleShape,
        color = if (active) Selected else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (active) OnSelected else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics { role = Role.Tab }.testTag(if (active) "releaseTypeTabChosen" else "releaseTypeTab"),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontSize = PILL_FONT,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = Space.Page, vertical = Space.Small),
        )
    }
}

/** The › that scrolls the pills on, and its arrow. */
internal val MORE_BUTTON = 32.dp
private val MORE_ICON = 20.dp

/** How far the pills blend out before the ›'s own space. */
private val MORE_BLEND = 64.dp
