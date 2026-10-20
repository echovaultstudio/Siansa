package app.musix.ui

import androidx.compose.runtime.LaunchedEffect
import app.musix.data.cover
import app.musix.data.toPlaylist
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.musix.data.Album
import app.musix.data.AlbumCategory
import app.musix.data.category
import app.musix.log.AppLog
import app.musix.mix.MixGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/*
 * Your Library, laid out like Spotify's: filter chips at the top (Playlists, Mixes, Albums, Artists, Your
 * listening), each opening its own sub-filters, a sort menu (Recents first), a list/grid switch, and
 * everything in one list. "Recents" is built from what you actually play: Musix's own history plus
 * Navidrome's recently played albums, so the things you listen to come to the top.
 */

private enum class Kind(val label: String) {
    FAVOURITES("Favourites"), PLAYLIST("Playlists"), MIX("Mixes"), ALBUM("Albums"), ARTIST("Artists"), LISTENING("Your listening")
}

private enum class Sort(val label: String) {
    RECENTS("Recents"), ADDED("Recently added"), ALPHABETICAL("Alphabetical"), CREATOR("Creator"), PLAYS("Most played")
}

/** One row (or tile) in the library. */
private data class Entry(
    val key: String,
    val kind: Kind?,
    val title: String,
    val subtitle: String,
    val route: String,
    val coverArt: String? = null,
    val round: Boolean = false,
    /** A generated cover: gradient colours and an optional icon or big text. */
    val gradient: Pair<Color, Color>? = null,
    val badge: String? = null,
    val heart: Boolean = false,
    val recentAt: Long = 0,
    val addedAt: Long = 0,
    val plays: Long = 0,
    val creator: String = "",
    /** Sub-filter tags this entry matches (e.g. "By you", "EPs", "Vibes"). */
    val tags: Set<String> = emptySet(),
    val alwaysPinned: Boolean = false,
    /** Shown under the Favourites chip (issues #275, #289): the Favourite albums card and Liked songs. */
    val favourite: Boolean = false,
)

private class LibraryData(val entries: List<Entry>, val subFilters: Map<Kind, List<String>>)

/** Switches bottom tab the way the tab bar does. */
private fun NavController.goToTab(route: String) = navigate(route) {
    popUpTo("home") { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun pins(context: Context) = context.getSharedPreferences(app.musix.data.Users.scoped("library_pins"), Context.MODE_PRIVATE)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YourLibraryScreen(nav: NavController) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val status by graph.library.status.collectAsStateWithLifecycle()
    val loaded by graph.library.loaded.collectAsStateWithLifecycle()
    val version by graph.library.version.collectAsStateWithLifecycle()
    val mixes by graph.mixes.state.collectAsStateWithLifecycle()
    val settings by graph.settings.state.collectAsStateWithLifecycle()

    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var sub by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(Sort.RECENTS.name) }
    // Grid or list, kept for next time (issue #289).
    var grid by rememberGridMode("library", default = false)
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var pinVersion by remember { mutableStateOf(0) }
    // The item being held: highlighted with its pin button showing. One at a time.
    var held by remember { mutableStateOf<String?>(null) }
    val pinned = remember(pinVersion) { pins(context).getStringSet("keys", emptySet()).orEmpty() }

    val favouriteIds by graph.favouriteAlbums.ids.collectAsStateWithLifecycle()
    // From the phone straight away, then again once Navidrome has answered (issue #161).
    // Built again when the connection comes back, for what only Navidrome has.
    val connection by graph.connection.state.collectAsStateWithLifecycle()
    val online = !connection.offline
    val data by produceState<Load<LibraryData>>(Load.Loading, version, mixes?.size, settings.navidromeUser, favouriteIds, online) {
        val me = settings.navidromeUser
        value = loadLibrary(graph, me)
        if (refreshFromServer(graph, me)) value = loadLibrary(graph, me)
    }

    val selectedKind = kind?.let { runCatching { Kind.valueOf(it) }.getOrNull() }
    val selectedSort = runCatching { Sort.valueOf(sort) }.getOrDefault(Sort.RECENTS)
    val all = data.valueOrNull()
    val ordered = remember(all, selectedKind, sub, selectedSort, query, pinned) {
        val list = all?.entries.orEmpty()
            .filter {
                selectedKind == null || it.kind == selectedKind || (it.alwaysPinned && selectedKind == Kind.PLAYLIST && it.heart) ||
                    (selectedKind == Kind.FAVOURITES && it.favourite)
            }
            .filter { e -> sub.let { it == null || it in e.tags } }
            .filter { query.isBlank() || it.title.contains(query, true) || it.subtitle.contains(query, true) }
        val sorted = when (selectedSort) {
            Sort.RECENTS -> list.sortedWith(compareByDescending<Entry> { it.recentAt }.thenByDescending { it.addedAt })
            Sort.ADDED -> list.sortedByDescending { it.addedAt }
            Sort.ALPHABETICAL -> AlphabetIndex.sort(list, { it.title })
            Sort.CREATOR -> AlphabetIndex.sort(list, { it.creator }, { it.title })
            Sort.PLAYS -> list.sortedWith(compareByDescending<Entry> { it.plays }.thenByDescending { it.recentAt })
        }
        // Pinned first (Liked songs and your listening always), then the rest in the chosen order.
        val (top, rest) = sorted.partition { it.alwaysPinned || it.key in pinned }
        top.sortedBy { if (it.alwaysPinned) 0 else 1 } to rest
    }
    val shown = remember(ordered) { ordered.first + ordered.second }
    // A to Z bar for long lists sorted by name (or creator): where each letter starts in `shown`.
    val letterStarts = remember(ordered, selectedSort) {
        val name: ((Entry) -> String)? = when (selectedSort) {
            Sort.ALPHABETICAL -> { e -> e.title }
            Sort.CREATOR -> { e -> e.creator }
            else -> null
        }
        val rest = ordered.second
        if (name == null || !AlphabetIndex.shouldShow(rest.size, AlphabetIndex.starts(rest.map(name)))) null
        else AlphabetIndex.starts(rest.map(name)).mapValues { it.value + ordered.first.size }
    }
    val letterOfShown = remember(letterStarts) {
        letterStarts?.let { starts -> starts.entries.sortedBy { it.value }.map { it.value to it.key } }
    }
    val scope = rememberCoroutineScope()
    val launcher = rememberPlayLauncher()
    var naming by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    fun togglePin(e: Entry) {
        if (e.alwaysPinned) return
        val set = pinned.toMutableSet()
        if (!set.add(e.key)) set.remove(e.key)
        pins(context).edit().putStringSet("keys", set).apply()
        pinVersion++
        held = null
    }

    Column(Modifier.fillMaxSize()) {
        LibraryHeader(
            initial = settings.navidromeUser.firstOrNull()?.uppercase() ?: "M",
            searching = searching,
            onAvatar = { nav.goToTab("settings") },
            onSearch = { searching = !searching; if (!searching) query = "" },
            onSync = { nav.navigate("sync") },
            onSoundSearch = { nav.goToTab("search") },
            onDownloads = { nav.navigate("downloads") },
            onNewPlaylist = { naming = true },
            onImportPlaylists = { importing = true },
        )
        if (importing) {
            ImportPlaylistsDialog { message ->
                importing = false
                message?.let { launcher.notify(it) }
            }
        }
        if (naming) {
            NewPlaylistDialog(
                onDismiss = { naming = false },
                onCreate = { name, onPhone ->
                    if (!creating) {
                        creating = true
                        scope.launch {
                            graph.playlists.create(name, onPhone = onPhone)
                                .onSuccess { made ->
                                    naming = false
                                    launcher.notify("Made $name")
                                    if (made != null) nav.navigate("playlist/${made.id}")
                                }
                                .onFailure { launcher.notify(it.message?.takeIf { m -> m.isNotBlank() } ?: "Couldn't make the playlist") }
                            creating = false
                        }
                    }
                },
            )
        }
        if (searching) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search in Your Library") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.Page, vertical = Space.Tight),
            )
        }
        FilterRow(
            selected = selectedKind,
            sub = sub,
            subFilters = selectedKind?.let { all?.subFilters?.get(it) }.orEmpty(),
            onKind = { k -> kind = k?.name; sub = null },
            onSub = { s -> sub = if (sub == s) null else s },
        )
        when {
            status == null && loaded -> SyncPrompt(nav)
            all == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                // While an item is held, a tap anywhere on a row only puts it back.
                val open = { e: Entry -> if (held != null) held = null else nav.navigate(e.route) }
                val sortBar: @Composable () -> Unit = {
                    SortBar(selectedSort, grid, onSort = { sort = it.name }, onGrid = { grid = !grid })
                }
                val covers = remember(shown) { shown.map { it.coverArt } }
                if (grid) {
                    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                    PrefetchCovers(gridState.firstVisibleItemIndex, gridState.layoutInfo.visibleItemsInfo.size, covers, 300)
                    Box(Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            // Three across, like every grid (issue #289).
                            columns = GridCells.Fixed(GRID_COLUMNS),
                            state = gridState,
                            contentPadding = PaddingValues(start = Space.Small, end = if (letterStarts != null) AlphabetBarWidth + Space.Tight else Space.Small, bottom = bottomBarPadding(Space.Page)),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) { sortBar() }
                            items(shown, key = { it.key }) { e ->
                                GridEntry(e, pinned = e.alwaysPinned || e.key in pinned, held = held == e.key, onClick = { open(e) }, onHold = { held = e.key }, onTogglePin = { togglePin(e) })
                            }
                        }
                        if (letterStarts != null && letterOfShown != null) {
                            LibraryAlphabetBar(letterStarts, letterOfShown, { gridState.firstVisibleItemIndex }) { i -> scope.launch { gridState.scrollToItem(i) } }
                        }
                    }
                } else {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    PrefetchCovers(listState.firstVisibleItemIndex, listState.layoutInfo.visibleItemsInfo.size, covers, 150)
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(end = if (letterStarts != null) AlphabetBarWidth else 0.dp, bottom = bottomBarPadding(Space.Page))) {
                            item { sortBar() }
                            items(shown, key = { it.key }) { e ->
                                RowEntry(e, pinned = e.alwaysPinned || e.key in pinned, held = held == e.key, onClick = { open(e) }, onHold = { held = e.key }, onTogglePin = { togglePin(e) })
                            }
                            if (shown.isEmpty()) {
                                item {
                                    Text(
                                        if (query.isNotBlank()) "Nothing in your library matches “$query”." else "Nothing here yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(Space.Page),
                                    )
                                }
                            }
                        }
                        if (letterStarts != null && letterOfShown != null) {
                            LibraryAlphabetBar(letterStarts, letterOfShown, { listState.firstVisibleItemIndex }) { i -> scope.launch { listState.scrollToItem(i) } }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The A to Z bar over the right edge of Your Library. Positions are in `shown`; the list's first item is
 * the sort bar, so list positions are one more.
 */
@Composable
private fun BoxScope.LibraryAlphabetBar(
    starts: Map<Char, Int>,
    letterAt: List<Pair<Int, Char>>,
    firstVisible: () -> Int,
    scrollTo: (Int) -> Unit,
) {
    val current by remember(letterAt) {
        derivedStateOf {
            val top = firstVisible() - 1
            letterAt.lastOrNull { it.first <= top }?.second
        }
    }
    AlphabetBar(
        present = starts.keys,
        current = current,
        // The first letter goes right to the top, so the sort row and pinned items stay in view.
        onPick = { letter -> AlphabetIndex.target(letter, starts)?.let { scrollTo(if (it == starts.values.min()) 0 else it + 1) } },
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 56.dp, bottom = bottomBarPadding(Space.Small)),  // spacing: exact (below the top bar)
    )
}

@Composable
private fun LibraryHeader(
    initial: String,
    searching: Boolean,
    onAvatar: () -> Unit,
    onSearch: () -> Unit,
    onSync: () -> Unit,
    onSoundSearch: () -> Unit,
    onDownloads: () -> Unit,
    onNewPlaylist: () -> Unit,
    onImportPlaylists: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = Space.Page, end = Space.Tight, top = Space.Medium, bottom = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(Brush.linearGradient(BrandGradient))
                .clickable(onClick = onAvatar),
            contentAlignment = Alignment.Center,
        ) { Text(initial, color = Color.White, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(Space.Medium))
        Text(
            "Your Library",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDownloads) { Icon(Icons.Outlined.ArrowCircleDown, "Downloads") }
        IconButton(onClick = onSearch) { Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Search in Your Library") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.Add, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("New playlist") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                    onClick = { menu = false; onNewPlaylist() },
                )
                DropdownMenuItem(
                    text = { Text("Import from Navidrome") },
                    leadingIcon = { Icon(Icons.Rounded.CloudDownload, null) },
                    onClick = { menu = false; onImportPlaylists() },
                )
                DropdownMenuItem(
                    text = { Text("Mix from a sound description") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    onClick = { menu = false; onSoundSearch() },
                )
                DropdownMenuItem(
                    text = { Text("Sync library") },
                    leadingIcon = { Icon(Icons.Rounded.Sync, null) },
                    onClick = { menu = false; onSync() },
                )
            }
        }
    }
}

/** Spotify's chip row: all kinds, or ✕ + the chosen kind + its own sub-filters. */
@Composable
private fun FilterRow(selected: Kind?, sub: String?, subFilters: List<String>, onKind: (Kind?) -> Unit, onSub: (String) -> Unit) {
    // Back to the start when the filter changes, so the clear (×) chip at the left is in view (a chip far to the
    // right, like Your listening, otherwise left the row scrolled with × out of sight).
    val scroll = rememberScrollState()
    LaunchedEffect(selected) { scroll.scrollTo(0) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = Space.Page, vertical = Space.Small),
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected == null) {
            Kind.entries.forEach { k -> Pill(k.label, active = false) { onKind(k) } }
        } else {
            Surface(
                onClick = { onKind(null) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(36.dp),
            ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "Clear filter", Modifier.size(20.dp)) } }
            Pill(selected.label, active = true) { onKind(null) }
            subFilters.forEach { s -> Pill(s, active = sub == s) { onSub(s) } }
        }
    }
}

@Composable
private fun Pill(text: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) Selected else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (active) OnSelected else MaterialTheme.colorScheme.onSurface,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontSize = 14.sp, modifier = Modifier.padding(horizontal = Space.Page, vertical = Space.Small))
    }
}

@Composable
private fun SortBar(sort: Sort, grid: Boolean, onSort: (Sort) -> Unit, onGrid: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = Space.Small, end = Space.Tight, top = Space.Tight, bottom = Space.Tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(Space.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.SwapVert, null, Modifier.size(20.dp))
                Spacer(Modifier.width(Space.Small))
                Text(sort.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                Text(
                    "Sort by",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.Page, vertical = Space.Small),
                )
                Sort.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label, color = if (s == sort) Accent else MaterialTheme.colorScheme.onSurface) },
                        trailingIcon = { if (s == sort) Icon(Icons.Rounded.Check, null, tint = Accent) },
                        onClick = { open = false; onSort(s) },
                    )
                }
            }
        }
        IconButton(onClick = onGrid) {
            Icon(if (grid) Icons.Rounded.ViewList else Icons.Rounded.GridView, if (grid) "Show as list" else "Show as grid")
        }
    }
}

@Composable
private fun EntryCover(e: Entry, modifier: Modifier, big: Boolean) {
    val shape = if (e.round) CircleShape else RoundedCornerShape(if (big) 8.dp else 4.dp)
    val g = e.gradient
    if (g != null) {
        Box(modifier.clip(shape).background(Brush.linearGradient(listOf(g.first, g.second))), contentAlignment = Alignment.Center) {
            when {
                e.heart -> Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(if (big) 44.dp else 26.dp))
                e.coverArt != null -> Cover(e.coverArt, if (big) 300 else 150, Modifier.fillMaxSize(0.62f), RoundedCornerShape(4.dp))
                e.badge != null -> Text(
                    e.badge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (big) 16.sp else 11.sp,
                    lineHeight = if (big) 18.sp else 12.sp,
                    maxLines = 3,
                    modifier = Modifier.padding(Space.Small),
                )
                e.kind == Kind.LISTENING -> Icon(Icons.AutoMirrored.Rounded.ShowChart, null, tint = Color.White)
            }
        }
    } else {
        Cover(e.coverArt, if (big) 300 else 150, modifier, shape)
    }
}

@Composable
private fun Subtitle(e: Entry, pinned: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (pinned) {
            Icon(Icons.Rounded.PushPin, "Pinned", tint = Accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(Space.Tight))
        }
        Text(
            e.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowEntry(e: Entry, pinned: Boolean, held: Boolean, onClick: () -> Unit, onHold: () -> Unit, onTogglePin: () -> Unit) = LongerHold {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (held) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .holdToReveal(e, onClick, onHold)
            .padding(horizontal = Space.Page, vertical = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryCover(e, Modifier.size(64.dp), big = false)
        Spacer(Modifier.width(Space.Medium))
        // Takes what's left, so a long title shortens to make room for the pin button.
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Space.Tight))
            Subtitle(e, pinned)
        }
        if (held) {
            Spacer(Modifier.width(Space.Small))
            PinButton(pinned, onTogglePin)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridEntry(e: Entry, pinned: Boolean, held: Boolean, onClick: () -> Unit, onHold: () -> Unit, onTogglePin: () -> Unit) = LongerHold {
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (held) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .holdToReveal(e, onClick, onHold)
            .padding(Space.Small),
        horizontalAlignment = if (e.round) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box {
            EntryCover(e, Modifier.fillMaxWidth().aspectRatio(1f), big = true)
            if (held) PinButton(pinned, onTogglePin, Modifier.align(Alignment.TopEnd).padding(Space.Tight))
        }
        Spacer(Modifier.height(Space.Small))
        Text(e.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Subtitle(e, pinned)
    }
}

/**
 * Tap opens; press and hold highlights the item and shows a pin button on it, so a slow tap never
 * changes the library by itself. Liked songs and your listening are always pinned, so holding them
 * does nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.holdToReveal(e: Entry, onClick: () -> Unit, onHold: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    return combinedClickable(
        onClick = onClick,
        onLongClick = if (e.alwaysPinned) null else {
            {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onHold()
            }
        },
    )
}

/** A hold here takes 0.8 s instead of Android's usual 0.4 s, so a slow tap stays a tap. */
@Composable
private fun LongerHold(content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val slower = remember(base) {
        object : ViewConfiguration by base {
            override val longPressTimeoutMillis = 800L
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides slower, content = content)
}

@Composable
private fun PinButton(pinned: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (pinned) MaterialTheme.colorScheme.surface else Accent,
        contentColor = if (pinned) MaterialTheme.colorScheme.onSurface else OnAccent,
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = Space.Medium, vertical = Space.Small), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PushPin, null, Modifier.size(16.dp))
            Spacer(Modifier.width(Space.Small))
            Text(if (pinned) "Unpin" else "Pin", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ---- Building the library from the phone's data and your listening -----------------------------------

private fun parseTime(text: String?): Long =
    text?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

/**
 * Navidrome's recently played albums (other apps too) from its last answer, for whose they are. The list
 * never waits for Navidrome: offline, each request took up to 15 seconds to give up (issue #161).
 */
@Volatile private var lastRecent: Pair<String, List<Album>>? = null

private suspend fun loadLibrary(graph: app.musix.AppGraph, me: String): Load<LibraryData> = try {
    Load.Ok(buildLibrary(graph, me))
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppLog.w("Library", "Loading the library failed: ${e.message}")
    Load.Err(e.message ?: e.javaClass.simpleName)
}

/**
 * Asks Navidrome for recently played albums, fresh playlists (stored with the library) and favourite albums, all at once.
 * False when offline, so nothing changed and the list needn't be built again.
 */
private suspend fun refreshFromServer(graph: app.musix.AppGraph, me: String): Boolean {
    if (graph.connection.current.offline) return false
    val started = System.currentTimeMillis()
    val recentOk = coroutineScope {
        val recent = async { runCatching { graph.subsonic.albums("recent", 100, 0) }.getOrNull() }
        val playlists = async { runCatching { graph.library.refreshPlaylists { graph.subsonic.playlists() } } }
        val favourites = async { graph.favouriteAlbums.refresh() }
        playlists.await()
        favourites.await()
        recent.await()?.also { lastRecent = me to it } != null
    }
    AppLog.d("Library", "Navidrome part of the library ${if (recentOk) "fetched" else "not reachable"} in ${System.currentTimeMillis() - started} ms")
    return true
}

private suspend fun buildLibrary(graph: app.musix.AppGraph, me: String): LibraryData {
    val entries = ArrayList<Entry>()
    val now = LocalDate.now()
    // Favourite albums as Navidrome last sent them (refreshed in refreshFromServer), or the phone's copy (issue #275).
    val favouriteAlbums = graph.favouriteAlbums.albums

    // What you've played: Musix's own history, and Navidrome's recently played albums (other apps too).
    val history = graph.history.all()
    val heard = runCatching { graph.library.songs(history.map { it.id }.distinct()) }.getOrDefault(emptyList()).associateBy { it.id }
    val albumRecent = HashMap<String, Long>()
    val artistRecent = HashMap<String, Long>()
    val artistPlays = HashMap<String, Long>()
    history.forEach { ev ->
        val song = heard[ev.id] ?: return@forEach
        song.albumId?.let { albumRecent.merge(it, ev.at) { a, b -> maxOf(a, b) } }
        val artistKey = song.artistId ?: song.artist.lowercase()
        artistRecent.merge(artistKey, ev.at) { a, b -> maxOf(a, b) }
        if (!ev.skipped) artistPlays.merge(artistKey, 1L) { a, b -> a + b }
    }
    val recentAlbums = lastRecent?.takeIf { it.first == me }?.second.orEmpty()
    graph.library.keepSyncedAlbums(recentAlbums, "Recently played").forEach { a ->
        val at = parseTime(a.played)
        if (at > 0) a.allIds.forEach { id -> albumRecent.merge(id, at) { x, y -> maxOf(x, y) } }
    }

    // Pinned: Liked songs.
    entries += Entry(
        key = "liked",
        kind = Kind.PLAYLIST,
        title = "Liked songs",
        subtitle = "Playlist · ${me.ifBlank { "You" }}",
        route = "liked",
        gradient = Color(0xFF4B2BD6) to Color(0xFF8FD3C8),
        heart = true,
        alwaysPinned = true,
        tags = setOf("By you"),
        favourite = true,
    )

    // Your listening: this month pinned in the full list; all of them under their own filter.
    val month = { d: LocalDate -> d.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
    val short = { d: LocalDate -> d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).trimEnd('.') }
    listOf(
        Triple("month", "Your ${month(now)}", "This month") to short(now),
        Triple("last-month", "Your ${month(now.minusMonths(1))}", "Last month") to short(now.minusMonths(1)),
        Triple("year", "Your ${now.year}", "This year") to now.year.toString(),
        Triple("year-ago", "This time last year", "This time last year") to "${short(now.minusYears(1))} ${now.year - 1}",
    ).forEach { (t, tileText) ->
        val (id, title, period) = t
        entries += Entry(
            key = "report-$id",
            kind = Kind.LISTENING,
            title = title,
            subtitle = "Your listening · ${period.lowercase().replaceFirstChar { it.uppercase() }}",
            route = "report/$id",
            gradient = tileColors("report-$id"),
            // A short word on the small tile ("Sep", "2026") so it never wraps.
            badge = tileText,
            alwaysPinned = id == "month",
            recentAt = System.currentTimeMillis(),
            tags = setOf(period),
        )
    }

    // The time machine (issue #186): your most played songs from any month or year.
    entries += Entry(
        key = "time-machine",
        kind = Kind.LISTENING,
        title = "Time machine",
        subtitle = "Your listening · Any month or year",
        route = "timemachine",
        gradient = Color(0xFF2B6AA8) to Color(0xFFB86A00),
        badge = "${now.year - 1}",
        recentAt = System.currentTimeMillis() - 2,
        tags = setOf("Time machine"),
    )

    // Your sonic fingerprint (issue #255, was on Home): what you play most sounds like, with its numbers.
    if (graph.settings.current.audioMuseReady) {
        entries += Entry(
            key = "sonic-fingerprint",
            kind = Kind.LISTENING,
            title = "Your sonic fingerprint",
            subtitle = "Your listening · How your music sounds",
            route = "fingerprint",
            gradient = Brand.VioletDeep to Brand.Plum,
            badge = "Sound",
            recentAt = System.currentTimeMillis() - 3,
            tags = setOf("Sound"),
        )
    }

    // Artists you don't have yet, like the ones you play most (made once a day), with why.
    entries += Entry(
        key = "discover-artists",
        kind = Kind.LISTENING,
        title = "Other artists you might like",
        subtitle = "Your listening · Discover",
        route = "discover/artists",
        gradient = Color(0xFF5B3FD0) to Color(0xFFFFB547),
        badge = "New",
        recentAt = System.currentTimeMillis() - 1,
        tags = setOf("Discover"),
    )

    // Playlists kept only on this phone.
    graph.localPlaylists.all().forEach { local ->
        val p = local.toPlaylist()
        entries += Entry(
            key = "playlist-${p.id}",
            kind = Kind.PLAYLIST,
            title = p.name,
            subtitle = "Playlist · On this phone",
            route = "playlist/${p.id}",
            coverArt = p.coverArt,
            recentAt = local.changedAt,
            addedAt = local.createdAt,
            creator = me,
            tags = setOf("By you", "On this phone"),
        )
    }

    // Playlists from Navidrome.
    val playlists = graph.library.playlists().let(graph.playlists.removed::visible)
    playlists.forEach { p ->
        val mine = me.isNotBlank() && p.owner.equals(me, ignoreCase = true)
        entries += Entry(
            key = "playlist-${p.id}",
            kind = Kind.PLAYLIST,
            title = p.name,
            subtitle = playlistSubtitle(p, fallbackOwner = "Navidrome"),
            route = "playlist/${p.id}",
            coverArt = p.coverArt,
            recentAt = parseTime(p.changed),
            addedAt = parseTime(p.changed),
            creator = p.owner.orEmpty(),
            tags = setOf(if (mine) "By you" else "By others"),
        )
    }

    // Today's mixes, newest of each kind.
    val personal = MixGenerator.Personal.entries.map { it.id }.toSet()
    graph.mixes.all().groupBy { it.selectorId }.values.mapNotNull { list -> list.maxByOrNull { it.createdAt } }.forEach { m ->
        val isPersonal = m.selectorId in personal
        entries += Entry(
            key = "mix-${m.id}",
            kind = Kind.MIX,
            title = m.title,
            subtitle = "Mix · ${if (isPersonal) "Made for you" else "Vibe"} · ${m.songs.size} songs",
            route = "mix/${m.id}",
            coverArt = m.songs.firstOrNull()?.cover,
            gradient = tileColors(m.selectorId),
            recentAt = m.createdAt,
            addedAt = m.createdAt,
            creator = "Musix",
            tags = setOf(if (isPersonal) "Made for you" else "Vibes", if (m.date == now.toString()) "Today" else "Earlier"),
        )
    }

    // Albums.
    graph.library.albums("name").forEach { a ->
        val recent = a.allIds.maxOfOrNull { albumRecent[it] ?: 0L } ?: 0L
        val category = a.category
        entries += Entry(
            key = "album-${a.id}",
            kind = Kind.ALBUM,
            title = a.name,
            subtitle = (if (category == AlbumCategory.ALBUM) "Album" else category.single) + " · " + a.artist,
            route = "album/${a.id}",
            coverArt = a.coverArt,
            recentAt = recent,
            addedAt = parseTime(a.created),
            plays = a.playCount,
            creator = a.artist,
            tags = buildSet {
                add(category.plural)
                if (recent > 0) add("Played")
                if (a.playCount == 0L && recent == 0L) add("Not played yet")
            },
        )
    }
    // One card for all favourite albums (issue #289): its page lists them, as a grid or a list.
    entries += Entry(
        key = "favourite-albums",
        kind = null,
        title = "Favourite albums",
        subtitle = "Albums · " + when (favouriteAlbums.size) {
            0 -> "tap the heart on an album to add it"
            1 -> "1 album"
            else -> "${favouriteAlbums.size} albums"
        },
        route = "favourite-albums",
        gradient = Color(0xFFB8336A) to Color(0xFFFFB547),
        heart = true,
        recentAt = System.currentTimeMillis() - 4,
        favourite = true,
    )

    // Artists.
    graph.library.artists().forEach { ar ->
        val recent = artistRecent[ar.id] ?: artistRecent[ar.name.lowercase()] ?: 0L
        val plays = artistPlays[ar.id] ?: artistPlays[ar.name.lowercase()] ?: 0L
        entries += Entry(
            key = "artist-${ar.id}",
            kind = Kind.ARTIST,
            title = ar.name,
            subtitle = "Artist" + if (ar.albumCount > 0) " · ${ar.albumCount} album${if (ar.albumCount == 1) "" else "s"}" else "",
            route = "artist/${ar.id}",
            coverArt = ar.coverArt ?: "ar-${ar.id}",
            round = true,
            recentAt = recent,
            plays = plays,
            creator = ar.name,
            tags = if (recent > 0) setOf("Played") else setOf("Not played yet"),
        )
    }

    // Sub-filters per kind, only those that have something in them, in a sensible order.
    fun present(kind: Kind, order: List<String>): List<String> {
        val have = entries.filter { it.kind == kind }.flatMap { it.tags }.toSet()
        return order.filter { it in have } + (have - order.toSet()).sorted()
    }
    val albumOrder = listOf("Played", "Not played yet") + AlbumCategory.entries.map { it.plural }
    val subFilters = mapOf(
        Kind.PLAYLIST to present(Kind.PLAYLIST, listOf("By you", "By others", "On this phone")),
        Kind.MIX to present(Kind.MIX, listOf("Today", "Made for you", "Vibes", "Earlier")),
        Kind.ALBUM to present(Kind.ALBUM, albumOrder),
        Kind.ARTIST to present(Kind.ARTIST, listOf("Played", "Not played yet")),
        Kind.LISTENING to present(Kind.LISTENING, listOf("This month", "Last month", "This year", "This time last year")),
    )
    return LibraryData(entries, subFilters)
}
