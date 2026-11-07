package app.siansa.tvapp

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.siansa.AppGraph
import app.siansa.car.latestMixes
import app.siansa.data.Album
import app.siansa.data.Artist
import app.siansa.data.Genre
import app.siansa.data.Mix
import app.siansa.data.Playlist
import app.siansa.data.RecentPlay
import app.siansa.data.SyncPhase
import app.siansa.data.cover
import app.siansa.log.AppLog
import app.siansa.mix.MixGenerator
import app.siansa.mix.MixRules
import app.siansa.mix.MoodRadio
import app.siansa.mix.moodRadios
import app.siansa.ui.LocalGraph
import app.siansa.ui.LocalNav
import app.siansa.ui.LocalSnack
import app.siansa.ui.Space
import app.siansa.ui.artistRadioArtists
import app.siansa.ui.recentlyPlayedAlbums
import app.siansa.ui.rediscoverSubtitle
import app.siansa.ui.tileColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.siansa.data.HomeSection as Section

/** One thing in a row on Home or a grid in the Library: OK opens it, or plays it (a radio). */
sealed interface TvItem {
    val key: String
    data class OfAlbum(val album: Album, val subtitle: String? = album.artist) : TvItem { override val key get() = "al:" + album.id }
    data class OfArtist(val artist: Artist) : TvItem { override val key get() = "ar:" + artist.id }
    /** An artist's radio: the artist with similar ones, played at once. */
    data class OfArtistRadio(val artist: Artist) : TvItem { override val key get() = "arr:" + artist.id }
    data class OfPlaylist(val playlist: Playlist) : TvItem { override val key get() = "pl:" + playlist.id }
    data class OfMix(val mix: Mix) : TvItem { override val key get() = "mx:" + mix.id }
    data class OfMood(val radio: MoodRadio) : TvItem { override val key get() = "md:" + radio.id }
    data class OfGenre(val genre: Genre) : TvItem { override val key get() = "gn:" + genre.name }
    /** Something played lately as a whole (Home's shortcuts on the phone): an album, a playlist, a mix… */
    data class OfShortcut(val play: RecentPlay) : TvItem { override val key get() = "sc:" + play.route }
    /** Today's personal mix of a kind (Your sound, On repeat, Discover for you): made when picked, then played. */
    data class OfPersonal(val kind: MixGenerator.Personal) : TvItem { override val key get() = "pm:" + kind.id }
    /** Today's mix isn't made yet: making it, then playing it. */
    data object MakeToday : TvItem { override val key get() = "make-today" }
}

data class TvRow(val title: String, val items: List<TvItem>)

/** How many covers a row on Home holds. */
private const val ROW_SIZE = 24

/**
 * Home's rows (issue #860), the phone's Home for the TV: in the order Home has on the phone (Settings › Home there), the
 * sections hidden there left out, and the ones only a phone can show (notices, cards, books…) skipped. Each row is read
 * on its own, so one that fails leaves the others.
 */
suspend fun homeRows(graph: AppGraph): List<TvRow> {
    val settings = graph.settings.current
    val rows = mutableListOf<TvRow>()
    for (section in Section.visible(settings.homeOrder, settings.homeHidden)) {
        val row = try {
            homeRow(graph, section)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Tv", "Home row ${section.id} failed: ${e.message}")
            null
        }
        if (row != null && row.items.isNotEmpty()) rows += row
    }
    return rows
}

private suspend fun homeRow(graph: AppGraph, section: Section): TvRow? {
    val library = graph.library
    fun albums(list: List<Album>) = list.distinctBy { it.id }.take(ROW_SIZE).map { TvItem.OfAlbum(it) }
    return when (section) {
        Section.QUICK -> TvRow("Jump back in", graph.recentPlays.all().filter { shortcutOpens(it.route) }.take(ROW_SIZE).map { TvItem.OfShortcut(it) })
        // Today's personal mixes first (AudioMuse's need it set up), then the mixes made so far.
        // The daily mixes made today (Today's mix and the ones for each time of day, Settings › Auto Generated Mixes).
        Section.TODAY -> {
            val made = todaysMixes(graph)
            TvRow("Today's mixes", if (made.isEmpty()) listOf(TvItem.MakeToday) else made.map { TvItem.OfMix(it) })
        }
        Section.MADE_FOR_YOU -> TvRow(
            "Your mixes",
            MixGenerator.Personal.entries.filter { MixRules.showsPersonal(it, settings(graph).audioMuseReady) }.map { TvItem.OfPersonal(it) } +
                latestMixes(graph.mixes.all()).filter { mix -> MixGenerator.Personal.entries.none { mix.selectorId == it.id } && !madeToday(mix) }
                    .take(ROW_SIZE).map { TvItem.OfMix(it) },
        )
        Section.REDISCOVER -> TvRow("Rediscover", graph.insights.rediscover().take(ROW_SIZE).map { TvItem.OfAlbum(it.album, rediscoverSubtitle(it)) })
        Section.RADIOS -> if (settings(graph).audioMuseReady) TvRow("Radios", moodRadios.map { TvItem.OfMood(it) }) else null
        Section.PLAYLISTS -> TvRow("Your playlists", graph.playlists.removed.visible(library.playlists()).take(ROW_SIZE).map { TvItem.OfPlaylist(it) })
        Section.RECENT -> TvRow("Recently played", albums(recentlyPlayedAlbums(graph, ROW_SIZE)))
        Section.ARTIST_RADIOS -> TvRow("Artist radios", artistRadioArtists(graph, ROW_SIZE).map { TvItem.OfArtistRadio(it) })
        Section.ADDED -> TvRow("Recently added", albums(library.albums("newest")))
        Section.GENRES -> TvRow("Your genres", library.genres(ROW_SIZE).map { TvItem.OfGenre(it) })
        Section.RANDOM -> TvRow(RANDOM_PICKS, albums(library.randomAlbums(ROW_SIZE)))
        // Notices, the carousel, cards, books and podcasts are the phone's.
        else -> null
    }
}

private fun settings(graph: AppGraph) = graph.settings.current

/** A mix made today: mixes are kept by date ("2026-11-05-…"). */
fun madeToday(mix: Mix, today: String = java.time.LocalDate.now().toString()): Boolean = mix.id.startsWith(today)

private suspend fun todaysMixes(graph: AppGraph): List<Mix> =
    graph.mixes.all().filter { madeToday(it) && MixGenerator.Personal.entries.none { p -> it.selectorId == p.id } }.sortedByDescending { it.id }

/** The shortcuts the TV can open: albums, playlists, mixes, artists and radios (books and cards are the phone's). */
fun shortcutOpens(route: String): Boolean =
    listOf("album/", "playlist/", "mix/", "artist/", "radio/").any { route.startsWith(it) }

@Composable
fun TvHome() {
    val graph = LocalGraph.current
    val version by graph.library.version.collectAsStateWithLifecycle()
    val sync by graph.sync.progress.collectAsStateWithLifecycle()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    var rows by remember { mutableStateOf<List<TvRow>?>(null) }
    // Read again when the library changes, but only once it has stopped changing for a moment: a sync changes it many
    // times a minute, and reading Home again each time made the rows jump and the remote lose its place.
    // While a sync runs, nothing is read again (issue #953): Home is read once it's done, and rows that didn't change
    // stay as they are, so the TV doesn't redraw them.
    LaunchedEffect(version, settings.homeOrder, settings.homeHidden, sync.running) {
        if (rows != null) {
            if (sync.running) return@LaunchedEffect
            delay(RELOAD_PAUSE_MS)
        }
        val next = steadyRows(rows, homeRows(graph))
        if (next !== rows) rows = next
    }
    val play = rememberTvPlay()
    val nav = LocalNav.current
    // The phone's notices (issue #784): what needs you, what you're partway through, what's new; shown or hidden in
    // Settings › Notices and Cards › Notices, as on the phone.
    val notices = app.siansa.ui.rememberNotices(graph, settings, shown = app.siansa.data.HomeSection.NOTICES !in settings.homeHidden)
    val loaded = rows
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Space.Small, bottom = Space.Large),
        verticalArrangement = Arrangement.spacedBy(Space.Section),
    ) {
        // One bar of pills (issue #996): Today's mix, Shuffle and Liked songs, then the notices, side by side in one
        // track instead of a row of notices over a row of buttons.
        item("quick") {
            TvPillBar(Modifier.testTag("tvPillBar")) {
                item("today") { TvButton("Today's mix", Icons.Rounded.AutoAwesome, strong = true, onClick = { play.todaysMix() }, modifier = Modifier.initialFocus()) }
                item("shuffle") { TvButton("Shuffle your library", Icons.Rounded.Shuffle, quiet = true, onClick = { play.label("your library") { graph.library.randomSongs(SHUFFLE_SONGS) } }) }
                item("liked") {
                    TvButton("Liked songs", Icons.Rounded.Favorite, quiet = true, onClick = {
                        play.label("your liked songs", shuffle = true) { app.siansa.ui.likedSongs(graph) }
                    })
                }
                if (notices.isNotEmpty()) {
                    item("divider") { TvPillDivider() }
                }
                items(notices, key = { "notice:" + it.key }) { notice ->
                    TvButton(notice.text, app.siansa.ui.noticeIcon(notice.kind), quiet = true, modifier = Modifier.testTag("tvNotice"), onClick = {
                        AppLog.i("Tv", "Notice opened: ${notice.kind.id}")
                        graph.notices.tap(notice.kind)
                        runCatching { nav.navigate(notice.route) }.onFailure { AppLog.w("Tv", "Couldn't open notice ${notice.route}: ${it.message}") }
                    })
                }
            }
        }
        if (sync.phase != SyncPhase.IDLE && !sync.quiet) {
            item("sync") {
                Text(
                    "Syncing your library: ${sync.albums} albums, ${sync.songs} songs so far.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = TV_EDGE),
                )
            }
        }
        if (loaded.isEmpty()) {
            item("empty") {
                Text(
                    "Nothing here yet. Your albums, mixes and radios show here once your library has synced.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = TV_EDGE),
                )
            }
        }
        items(loaded, key = { it.title }) { row -> TvItemRow(row) }
    }
}

/** How a row of [count] covers fills the screen (issue #996). */
enum class RowLook { WIDE_CARDS, FILLED, SCROLLING }

/**
 * Six covers fill a TV's width: a row with fewer used to leave the rest empty (issue #996). Up to [WIDE_SLOTS], they're
 * wide cards, a third of the width each; four or five, covers sharing the width; six or more, covers that scroll.
 */
internal fun rowLook(count: Int): RowLook = when {
    count <= WIDE_SLOTS -> RowLook.WIDE_CARDS
    count < GRID_COLUMNS -> RowLook.FILLED
    else -> RowLook.SCROLLING
}

/** A wide card is a third of the screen: one or two leave room for the rest, as a third card would. */
internal const val WIDE_SLOTS = 3

/** A titled row of covers that fills the screen, scrolling sideways as the remote moves along it when there are many. */
@Composable
fun TvItemRow(row: TvRow) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        Text(row.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = TV_EDGE))
        when (rowLook(row.items.size)) {
            RowLook.SCROLLING -> LazyRow(
                contentPadding = PaddingValues(horizontal = TV_EDGE, vertical = Space.Small),
                horizontalArrangement = Arrangement.spacedBy(Space.Page),
            ) {
                items(row.items, key = { it.key }) { item -> TvItemTile(item) }
            }
            RowLook.FILLED -> Row(
                Modifier.fillMaxWidth().padding(horizontal = TV_EDGE, vertical = Space.Small),
                horizontalArrangement = Arrangement.spacedBy(Space.Page),
            ) {
                row.items.forEach { item -> TvItemTile(item, Modifier.weight(1f), width = Dp.Unspecified) }
            }
            RowLook.WIDE_CARDS -> Row(
                Modifier.fillMaxWidth().padding(horizontal = TV_EDGE, vertical = Space.Small).testTag("tvWideRow"),
                horizontalArrangement = Arrangement.spacedBy(Space.Page),
            ) {
                row.items.forEach { item -> TvItemTile(item, Modifier.weight(1f), wide = true) }
                repeat(WIDE_SLOTS - row.items.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** One item as a cover (or a [wide] card): OK opens it (an album, a playlist…) or plays it (a radio). */
@Composable
fun TvItemTile(item: TvItem, modifier: Modifier = Modifier, width: Dp = TILE_WIDTH, wide: Boolean = false) {
    val nav = LocalNav.current
    val play = rememberTvPlay()
    when (item) {
        is TvItem.OfAlbum -> TvTile(item.album.name, item.subtitle, item.album.coverArt, { nav.navigate(TvRoutes.album(item.album.id)) }, modifier, width = width, wide = wide)
        is TvItem.OfArtist -> TvTile(item.artist.name, null, item.artist.coverArt, { nav.navigate(TvRoutes.artist(item.artist.id)) }, modifier, width = width, round = true, wide = wide)
        is TvItem.OfArtistRadio -> TvTile("${item.artist.name} radio", "With similar artists", item.artist.coverArt, { play.artistRadio(item.artist) }, modifier, width = width, round = true, wide = wide)
        is TvItem.OfPlaylist -> TvTile(item.playlist.name, "${item.playlist.songCount} songs", item.playlist.coverArt, { nav.navigate(TvRoutes.playlist(item.playlist.id)) }, modifier, width = width, wide = wide)
        is TvItem.OfMix -> TvTile(item.mix.title, "${item.mix.songs.size} songs", item.mix.songs.firstOrNull()?.cover, { nav.navigate(TvRoutes.mix(item.mix.id)) }, modifier, width = width, wide = wide)
        is TvItem.OfMood -> TvTile(item.radio.name, "Sound radio", null, { play.moodRadio(item.radio) }, modifier, width = width,
            colors = Color(item.radio.colors.first) to Color(item.radio.colors.second), wide = wide)
        is TvItem.OfGenre -> TvTile(item.genre.name, "${item.genre.songCount} songs", null, { play.genreRadio(item.genre.name) }, modifier, width = width, colors = tileColors(item.genre.name), wide = wide)
        is TvItem.OfShortcut -> TvTile(item.play.title, shortcutKind(item.play.route), item.play.cover, { nav.navigate(item.play.route) }, modifier, width = width, wide = wide)
        TvItem.MakeToday -> TvTile("Make today's mix", "From what you play at this time of day", null, { play.todaysMix() }, modifier, width = width,
            colors = tileColors("Today's mix"), wide = wide)
        is TvItem.OfPersonal -> TvTile(item.kind.title, "Made for you today", null, { play.personal(item.kind) }, modifier, width = width, colors = tileColors(item.kind.title), wide = wide)
    }
}

private fun shortcutKind(route: String): String = when {
    route.startsWith("album/") -> "Album"
    route.startsWith("playlist/") -> "Playlist"
    route.startsWith("mix/") -> "Mix"
    route.startsWith("artist/") -> "Artist"
    else -> "Radio"
}

/**
 * Starting playback from the TV's pages: a list that takes a moment to build (a radio, Today's mix) says so while it
 * loads, and says why if it fails, the way the phone's Home does.
 */
class TvPlay(private val graph: AppGraph, private val launcher: app.siansa.ui.PlayLauncher, private val scope: kotlinx.coroutines.CoroutineScope, private val notify: (String) -> Unit) {
    fun label(what: String, shuffle: Boolean = false, songs: suspend () -> List<app.siansa.data.Song>) {
        scope.launch {
            val list = runCatching { songs() }.onFailure { AppLog.w("Tv", "Couldn't start $what: ${it.message}") }.getOrDefault(emptyList())
            if (list.isEmpty()) notify("Nothing to play in $what yet.") else graph.player.play(list, shuffle = shuffle)
        }
    }

    fun todaysMix() {
        scope.launch {
            notify("Making today's mix…")
            val mix = runCatching { graph.generator.ensureToday() }.onFailure { AppLog.w("Tv", "Today's mix failed: ${it.message}") }.getOrNull()
            if (mix == null || mix.songs.isEmpty()) {
                notify("Today's mix isn't ready yet. Try again once your library has synced.")
                return@launch
            }
            graph.player.play(mix.songs)
            graph.recentPlays.note("mix/${mix.id}", mix.title, mix.songs.firstOrNull()?.cover)
        }
    }

    /** Today's personal mix of [kind], made if it isn't yet (it can take a moment with AudioMuse), then played. */
    fun personal(kind: MixGenerator.Personal) {
        scope.launch {
            notify("Making ${kind.title}…")
            val mix = runCatching { graph.generator.ensurePersonal(kind) }.onFailure { AppLog.w("Tv", "${kind.title} failed: ${it.message}") }
            val made = mix.getOrNull()
            if (made == null || made.songs.isEmpty()) {
                notify(mix.exceptionOrNull()?.message ?: "${kind.title} isn't ready yet.")
                return@launch
            }
            graph.player.play(made.songs)
            graph.recentPlays.note("mix/${made.id}", made.title, made.songs.firstOrNull()?.cover)
        }
    }

    fun artistRadio(artist: Artist) =
        launcher.progressivePicks("${artist.name} radio", { graph.radios.artistStart(artist) }) { graph.radios.artistMore(artist, it) }

    fun moodRadio(radio: MoodRadio) = launcher(radio.name) { graph.radios.mood(radio) }

    fun genreRadio(name: String) = launcher("$name radio") { graph.radios.genre(name) }
}

@Composable
fun rememberTvPlay(): TvPlay {
    val graph = LocalGraph.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    return remember(graph, snack, scope) {
        TvPlay(graph, app.siansa.ui.PlayLauncher(graph, snack, scope), scope) { message -> scope.launch { snack.showSnackbar(message) } }
    }
}

private const val SHUFFLE_SONGS = 200
private const val RELOAD_PAUSE_MS = 4_000L

/** The row of random albums: they stay the same until Home is opened again (issue #953). */
internal const val RANDOM_PICKS = "Random picks"

/**
 * Home's rows read again ([new]) with what's shown ([old]) kept where nothing changed (issue #953): a row equal to the
 * one shown stays that same row, Random picks keeps its albums, and when nothing changed at all the old list comes
 * back, so the screen isn't drawn again for nothing.
 */
internal fun steadyRows(old: List<TvRow>?, new: List<TvRow>): List<TvRow> {
    if (old == null) return new
    val before = old.associateBy { it.title }
    val kept = new.map { row ->
        val was = before[row.title] ?: return@map row
        if (was == row || row.title == RANDOM_PICKS) was else row
    }
    return if (kept == old) old else kept
}
