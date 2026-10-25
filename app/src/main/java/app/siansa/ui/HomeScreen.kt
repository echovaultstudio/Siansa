package app.siansa.ui

import app.siansa.data.normalizeKey
import app.siansa.mix.mixArtistRadios
import app.siansa.mix.ArtistRadioPicks
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.rounded.Casino

import app.siansa.mix.resumeStart
import app.siansa.mix.MixRules
import app.siansa.data.RecentPlay
import app.siansa.data.cover
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.AppGraph
import app.siansa.data.Album
import app.siansa.data.HomeSection
import app.siansa.data.Artist
import app.siansa.data.Mix
import app.siansa.data.Song
import app.siansa.data.Playlist
import app.siansa.data.Selector
import app.siansa.log.AppLog
import app.siansa.mix.moodRadios
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.produceState
import app.siansa.mix.MixGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(nav: NavController) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val launch = rememberPlayLauncher()
    val mixes by graph.mixes.state.collectAsStateWithLifecycle()
    val resumePoints by graph.mixResume.points.collectAsStateWithLifecycle()
    val recentPlays by graph.recentPlays.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.recentPlays.all() }
    // Albums played from libraries or users the source filter now hides drop out of the shortcuts (beta #17).
    val shownUsers by graph.guests.shown.collectAsStateWithLifecycle()
    val ownHidden by graph.library.ownHidden.collectAsStateWithLifecycle()
    val libraryVersion by graph.library.version.collectAsStateWithLifecycle()
    val shownRecentAlbums by produceState<Set<String>?>(null, recentPlays, shownUsers, ownHidden, libraryVersion) {
        value = graph.library.shownAlbums(recentPlays.orEmpty().mapNotNull { recentAlbumId(it.route) })
    }
    val customCards by graph.cards.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.cards.all() }
    val selectors by graph.selectors.state.collectAsStateWithLifecycle()
    val status by graph.library.status.collectAsStateWithLifecycle()
    val loaded by graph.library.loaded.collectAsStateWithLifecycle()
    val version by graph.library.version.collectAsStateWithLifecycle()
    val synced = status != null
    var current by remember { mutableStateOf<Mix?>(null) }
    var busySelector by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val homeSettings by graph.settings.state.collectAsStateWithLifecycle()
    val homeOrder = homeSettings.homeOrder
    val homeHidden = homeSettings.homeHidden
    var arranging by remember { mutableStateOf(false) }

    // While AudioMuse can't be reached (offline, or it's down), mixes aren't attempted: a clear message instead.
    val aiProblem = rememberAiProblem()
    // Mixes aren't attempted (and tiles say why) only when AudioMuse is set up but can't be used; Today's mix
    // shows the reason in either case.
    val aiDown = aiProblem.takeIf { homeSettings.audioMuseReady }

    fun build(selector: Selector?, force: Boolean, open: Boolean) {
        if (busySelector != null) return
        aiDown?.let { problem ->
            if (open) launch.notify("AI backend is currently not available :( $problem")
            return
        }
        // In the app's own scope, so leaving Home doesn't cancel a mix half-way.
        graph.scope.launch(Dispatchers.Main) {
            busySelector = selector?.id ?: "current"
            error = null
            try {
                val mix = if (selector == null) graph.generator.ensureCurrent(force) else graph.generator.ensure(selector, force)
                if (selector == null || selector.id == current?.selectorId) current = mix
                if (mix == null) error = "No vibe covers this hour. Add one in Settings, under Vibes."
                if (open && mix != null) nav.navigate("mix/${mix.id}")
                if (open && mix == null) launch.notify(error!!)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Home", "Mix failed: ${e.message}")
                error = e.message ?: "Couldn't build the mix"
                // Tapped on a tile further down: the message at the top of the page is out of sight.
                if (open) launch.notify(error!!)
            } finally {
                busySelector = null
            }
        }
    }

    // The hour, checked every minute, so the page moves on to the next vibe while it's open.
    val hour by produceState(LocalTime.now().hour) {
        while (true) {
            delay(60_000)
            value = LocalTime.now().hour
        }
    }
    var personalBusy by remember { mutableStateOf<String?>(null) }

    fun buildPersonal(kind: MixGenerator.Personal, open: Boolean, quiet: Boolean = false) {
        if (personalBusy != null) return
        aiDown?.let { problem ->
            if (open && !quiet) launch.notify("AI backend is currently not available :( $problem")
            return
        }
        graph.scope.launch(Dispatchers.Main) {
            personalBusy = kind.id
            try {
                val mix = graph.generator.ensurePersonal(kind)
                if (open) nav.navigate("mix/${mix.id}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Home", "${kind.title} failed: ${e.message}")
                if (!quiet) launch.notify(e.message ?: "Couldn't make ${kind.title}")
            } finally {
                personalBusy = null
            }
        }
    }

    // Mixes are made when they aren't there yet for today and this hour's vibe, or when you tap refresh; showing
    // other music in the source filter doesn't make them again (issue #486). AI mixes need AudioMuse.
    val aiMixes = MixRules.showsAiMixes(homeSettings.audioMuseReady)
    LaunchedEffect(hour, aiMixes) {
        graph.mixes.all()
        graph.selectors.all()
        current = null
        if (aiMixes) build(null, force = false, open = false)
    }
    // Today's personal mixes are made quietly in the background once the page is up.
    LaunchedEffect(aiMixes) {
        delay(1500)
        val today = LocalDate.now().toString()
        for (kind in MixGenerator.Personal.entries.filter { MixRules.showsPersonal(it, graph.settings.current.audioMuseReady) }) {
            if (graph.mixes.get("$today-${kind.id}") != null) continue
            while (busySelector != null || personalBusy != null) delay(500)
            buildPersonal(kind, open = false, quiet = true)
            delay(300)
        }
    }

    val today = LocalDate.now().toString()
    val personalIds = MixGenerator.Personal.entries.map { it.id }.toSet()
    // Only what fits now: the vibe for this hour and the personal mixes, not this morning's mix in the evening.
    // Vibes for this hour and today, the most narrowly set first (issue #348).
    val weekday = LocalDate.now().dayOfWeek.value
    val applicable = selectors.orEmpty().filter { it.enabled && it.appliesAt(hour, weekday) }.sortedByDescending { it.specificity }
    val todaysMixes = MixRules.todays(mixes.orEmpty(), today, personalIds, applicable.mapTo(HashSet()) { it.id })
    val playlists by loadState(version to "playlists") { graph.library.refreshPlaylists { graph.source.playlists() }.let(graph.playlists.removed::visible) }
    val recentAlbums by loadState(version to "recent") { recentlyPlayedAlbums(graph) }
    val recentArtists by loadState(version to "artists") { artistRadioArtists(graph) }
    val freshCovers by graph.artistPhotos.covers.collectAsStateWithLifecycle()
    val newest by loadState(version to "newest") {
        if (graph.library.isSynced) graph.library.albums("newest").take(20)
        else graph.source.albums("newest", 20, 0).distinctBy { it.name + "|" + it.artist }
    }
    val lovedBefore by loadState(version to today) { graph.insights.rediscover() }
    val rediscover by loadState(version to "random") {
        if (graph.library.isSynced) graph.library.randomAlbums(20)
        else graph.source.albums("random", 20, 0).distinctBy { it.name + "|" + it.artist }
    }
    val genres by loadState(version to "genres") { if (graph.library.isSynced) graph.library.genres(12) else emptyList() }

    // The Home tab tapped again: glide back to the top. From far down, jump most of the way first so it's quick.
    val listState = rememberLazyListState()
    val scrollRequests by HomeScrollToTop.requests.collectAsStateWithLifecycle()
    var handledScroll by rememberSaveable { mutableStateOf(scrollRequests) }
    LaunchedEffect(scrollRequests) {
        val pending = homeScrollPending(scrollRequests, handledScroll)
        handledScroll = scrollRequests
        if (!pending) return@LaunchedEffect
        if (listState.firstVisibleItemIndex > 4) listState.scrollToItem(4)
        listState.animateScrollToItem(0)
    }

    LazyColumn(
        Modifier.fillMaxSize().aboveKeyboard(),
        state = listState,
        contentPadding = PaddingValues(bottom = if (keyboardOpen()) Space.Page else bottomBarPadding(Space.Section)),
    ) {
        item {
            // One button for the source filter and arranging Home (issue #152): both are tabs of one panel.
            HomeHeader(greeting(), Modifier.statusBarsPadding()) {
                // Left of the source filter and Arrange (issue #347).
                DiceButton()
                SourceFilterButton { arranging = true }
            }
        }
        item(key = "sources") { SourceChips { arranging = true } }

        if (loaded && !synced) {
            item(key = "sync") { Card(Modifier.fillMaxWidth().padding(horizontal = Space.Page, vertical = Space.Small)) { SyncPrompt(nav) } }
        }

        // The rest in the order chosen under Arrange Home, without the hidden ones.
        for (section in HomeSection.visible(homeOrder, homeHidden)) {
            when (section) {
                HomeSection.QUICK -> {
                    // Quick access, like the top grid in Spotify (issue #271): Liked songs always first, then what
                    // you played last; until there's enough of that, today's mixes, playlists and recent albums.
                    item(key = "quick") {
                        val mixIds = mixes.orEmpty().mapTo(HashSet()) { it.id }
                        val fill = buildList {
                            todaysMixes.take(2).forEach { m -> add(RecentPlay("mix/${m.id}", m.title, m.songs.firstOrNull()?.cover)) }
                            playlists.valueOrNull().orEmpty().take(2).forEach { p -> add(RecentPlay("playlist/${p.id}", p.name, p.coverArt)) }
                            recentAlbums.valueOrNull().orEmpty().take(8).forEach { a -> add(RecentPlay("album/${a.id}", a.name, a.coverArt)) }
                        }
                        val played = shownRecents(recentPlays.orEmpty(), shownRecentAlbums)
                            .filter { !it.route.startsWith("mix/") || it.route.removePrefix("mix/") in mixIds }
                        val quick = buildList<Triple<String, String?, () -> Unit>> {
                            add(Triple("Liked songs", null) { nav.navigate("liked") })
                            quickShortcuts(played, fill, QUICK_COUNT - 1).forEach { r -> add(Triple(r.title, r.cover) { nav.navigate(r.route) }) }
                        }
                        if (quick.isNotEmpty()) {
                            Column(Modifier.padding(horizontal = Space.Page, vertical = Space.Medium), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                                quick.chunked(2).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                                        row.forEach { (title, cover, open) ->
                                            val liked = title == "Liked songs" && cover == null
                                            val colors = if (liked) Color(0xFF4B2BD6) to Color(0xFF8FD3C8) else tileColors(title)
                                            QuickTile(
                                                title, cover, colors, open, Modifier.weight(1f),
                                                icon = if (liked) Icons.Rounded.Favorite else null,
                                            )
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                HomeSection.TODAY -> {
                    item(key = "today") {
                        val mix = current
                        when {
                            mix != null -> HeroMix(
                                mix = mix,
                                busy = busySelector == "current",
                                onOpen = { nav.navigate("mix/${mix.id}") },
                                // Carries on where the mix was left (issue #269), and becomes a shortcut (issue #271).
                                onPlay = {
                                    graph.mixResume.play(mix)
                                    graph.scope.launch { graph.recentPlays.note("mix/${mix.id}", mix.title, mix.songs.firstOrNull()?.cover) }
                                },
                                resume = resumeStart(mix.songs, resumePoints[mix.id])?.let { mix.songs[it.first] },
                                onShuffle = { graph.player.play(mix.songs, shuffle = true) },
                                onRegenerate = { build(null, force = true, open = false) },
                            )
                            busySelector == "current" -> BuildingCard()
                            aiDown != null -> AiUnavailable(aiDown, Modifier.padding(horizontal = Space.Page, vertical = Space.Small))
                            else -> Unit
                        }
                    }
                    // While AudioMuse can't be reached the card above says so; no error and Try again as well. Without
                    // AudioMuse at all there's no today's mix (issue #486).
                    error?.takeIf { aiDown == null && aiMixes }?.let { message ->
                        item(key = "today_error") {
                            Column(Modifier.padding(horizontal = Space.Page)) {
                                ErrorText(message, Modifier)
                                TextButton(onClick = { build(null, force = false, open = false) }) { Text("Try again") }
                            }
                        }
                    }
                }
                HomeSection.CAROUSEL -> {
                    // Your own mixes for this part of the day, set in Vibes (issue #253): AI mixes, so only with
                    // AudioMuse (issue #486).
                    if (homeSettings.navidromeReady && aiMixes) item(key = "carousel") { HomeCarousel(nav, aiDown) }
                }
                HomeSection.MADE_FOR_YOU -> {
                    // Made for you: the vibe for this hour, then mixes from your own listening.
                    item(key = "made") {
                        val tiles: List<Pair<String, @Composable () -> Unit>> = buildList {
                            // The vibe's mix is an AI mix: only with AudioMuse (issue #486).
                            applicable.filter { aiMixes }.forEach { selector ->
                                add(selector.id to @Composable {
                                    val mix = todaysMixes.firstOrNull { it.selectorId == selector.id }
                                    // Just the title and track count (issue #338): the description is on the mix's page.
                                    GradientTile(
                                        title = mix?.title ?: "${selector.name} mix",
                                        colors = tileColors(selector.id),
                                        coverArt = mix?.songs?.firstOrNull()?.coverArt,
                                        caption = madeForYouCaption(mix?.songs?.size, aiDown != null, "Tap to make today's mix"),
                                        busy = busySelector == selector.id,
                                        onClick = { if (mix != null) nav.navigate("mix/${mix.id}") else build(selector, force = false, open = true) },
                                    )
                                })
                            }
                            MixGenerator.Personal.entries.filter { MixRules.showsPersonal(it, homeSettings.audioMuseReady) }.forEach { kind ->
                                add(kind.id to @Composable {
                                    val mix = todaysMixes.firstOrNull { it.selectorId == kind.id }
                                    GradientTile(
                                        title = kind.title,
                                        colors = tileColors(kind.id),
                                        coverArt = mix?.songs?.firstOrNull()?.coverArt,
                                        caption = madeForYouCaption(mix?.songs?.size, aiDown != null, "Tap to make it"),
                                        busy = personalBusy == kind.id,
                                        onClick = { if (mix != null) nav.navigate("mix/${mix.id}") else buildPersonal(kind, open = true) },
                                    )
                                })
                            }
                            // Your own cards, added and named in Settings › Made for you cards (issue #274).
                            customCards.orEmpty().forEach { card ->
                                add(card.id to @Composable {
                                    GradientTile(
                                        title = card.title,
                                        colors = tileColors(card.id),
                                        onClick = { nav.navigate("card/${card.id}") },
                                    )
                                })
                            }
                        }
                        Shelf("Made for you", tiles, key = { it.first }) { it.second() }
                    }
                }
                HomeSection.REDISCOVER -> {
                    // Albums you loved a while ago but haven't played since.
                    item(key = "rediscover") {
                        Shelf("Rediscover", lovedBefore.valueOrNull().orEmpty(), key = { it.album.id }, onOpenAll = nav.openAll(app.siansa.ui.HomeSection.Rediscover)) { r ->
                            AlbumTile(
                                name = r.album.name,
                                subtitle = rediscoverSubtitle(r),
                                coverArt = r.album.coverArt,
                                onClick = { nav.navigate("album/${r.album.id}") },
                                modifier = Modifier.width(150.dp),
                            )
                        }
                    }
                }
                HomeSection.RADIOS -> {
                    item(key = "radios") {
                        Shelf("Radios", moodRadios, key = { it.id }, onOpenAll = nav.openAll(app.siansa.ui.HomeSection.Radios)) { radio ->
                            GradientTile(
                                title = radio.name,
                                colors = Color(radio.colors.first) to Color(radio.colors.second),
                                size = 140.dp,
                                // Just the tile (issue #338).
                                onClick = { nav.openMoodRadio(radio.id) },
                            )
                        }
                    }
                }
                HomeSection.PLAYLISTS -> {
                    item(key = "playlists") {
                        Shelf("Your playlists", playlists.valueOrNull().orEmpty(), key = { it.id }, onOpenAll = nav.openAll(app.siansa.ui.HomeSection.Playlists)) { playlist ->
                            PlaylistTile(playlist) { nav.navigate("playlist/${playlist.id}") }
                        }
                    }
                }
                HomeSection.RECENT -> {
                    item(key = "recent") {
                        Shelf("Recently played", recentAlbums.valueOrNull().orEmpty(), key = { it.id }, onOpenAll = nav.openAll(app.siansa.ui.HomeSection.RecentlyPlayed)) { album ->
                            AlbumTile(album.name, album.artist, album.coverArt, { nav.navigate("album/${album.id}") }, Modifier.width(150.dp))
                        }
                    }
                }
                HomeSection.ARTIST_RADIOS -> {
                    item(key = "artist_radios") {
                        Shelf("Artist radios", recentArtists.valueOrNull().orEmpty(), key = { it.id }, onOpenAll = nav.openAll(app.siansa.ui.HomeSection.ArtistRadios)) { artist ->
                            AlbumTile(
                                name = "${artist.name} radio",
                                subtitle = "With similar artists",
                                coverArt = freshCovers[artist.id] ?: artist.coverArt,
                                onClick = {
                                    launch.progressive("${artist.name} radio", { graph.radios.artistStart(artist) }) { graph.radios.artistMore(artist, it) }
                                },
                                modifier = Modifier.width(140.dp),
                                round = true,
                            )
                        }
                    }
                }
                HomeSection.ADDED -> {
                    item(key = "added") {
                        Shelf("Recently added", newest.valueOrNull().orEmpty(), key = { it.id }, onOpenAll = nav.openAll(app.siansa.ui.HomeSection.RecentlyAdded)) { album ->
                            AlbumTile(album.name, album.artist, album.coverArt, { nav.navigate("album/${album.id}") }, Modifier.width(150.dp))
                        }
                    }
                }
                HomeSection.GENRES -> {
                    item(key = "genres") {
                        Shelf("Your genres", genres.valueOrNull().orEmpty(), key = { it.name }, onOpenAll = nav.openAll(app.siansa.ui.HomeSection.Genres)) { genre ->
                            GradientTile(
                                title = genre.name,
                                colors = tileColors(genre.name),
                                size = 130.dp,
                                // Just the tile (issue #338).
                                onClick = { nav.openGenreRadio(genre.name) },
                            )
                        }
                    }
                }
                HomeSection.RANDOM -> {
                    item(key = "random") {
                        Shelf("Random picks", rediscover.valueOrNull().orEmpty(), key = { it.id }, onOpenAll = nav.openAll(app.siansa.ui.HomeSection.RandomPicks)) { album ->
                            AlbumTile(album.name, album.artist, album.coverArt, { nav.navigate("album/${album.id}") }, Modifier.width(150.dp))
                        }
                    }
                }
            }
        }
    }
    if (arranging) {
        ArrangeHomeSheet(homeOrder, homeHidden, onChange = {
            AppLog.i("Home", "Arranged: ${HomeSection.format(it)}")
            graph.settings.setHomeOrder(it)
        }, onHiddenChange = {
            AppLog.i("Home", "Hidden: ${HomeSection.formatHidden(it).ifEmpty { "none" }}")
            graph.settings.setHomeHidden(it)
        }, onDismiss = { arranging = false }, startOnSources = true)
    }
}

internal suspend fun recentlyPlayedAlbums(graph: AppGraph, limit: Int = 15): List<Album> {
    if (!graph.library.isSynced) return emptyList()
    // The full list looks further back in the history than the row on Home.
    val heard = graph.library.songs(graph.history.recent(if (limit > 15) 2000 else 300).map { it.id }.distinct())
    // With "Only downloaded music" on, only songs on the phone count.
    val songs = graph.library.onlyDownloaded()?.songs(heard) ?: heard
    val heardAlbums = songs.mapNotNull { it.albumId }.distinct()
    // Only albums the source filter shows (beta #17): not a hidden library's or a hidden user's.
    val shown = graph.library.shownAlbums(heardAlbums)
    val albumIds = heardAlbums.filter { shown == null || it in shown }.take(limit)
    return albumIds.mapNotNull { graph.library.album(it) }.distinctBy { it.id }
}

internal suspend fun recentlyPlayedArtists(graph: AppGraph, limit: Int = 10): List<Artist> {
    if (!graph.library.isSynced) return emptyList()
    // The last few hundred plays first; when they're all a few artists (a long mix), further back (issue #377).
    for (plays in listOf(300, 2000)) {
        val played = graph.library.songs(graph.history.recent(plays).map { it.id }.distinct())
        // Only songs on albums the source filter shows (beta #17).
        val shown = graph.library.shownAlbums(played.mapNotNull { it.albumId })
        val heard = played.filter { s -> shown == null || s.albumId == null || s.albumId in shown }
        val songs = graph.library.onlyDownloaded()?.songs(heard) ?: heard
        val artists = songs.distinctBy { it.artistId ?: it.artist }.take(limit).mapNotNull { s ->
            s.artistId?.let { graph.library.artist(it) } ?: graph.library.artistByName(s.artist)
        }.distinctBy { it.id }
        if (artists.size >= limit || plays == 2000) return artists
    }
    return emptyList()
}

/**
 * Home's artist radios (issue #377): always [count] when the library has that many artists, mixing artists you
 * played lately with artists like them (the Siansa plugin's "Fans also like"), then the ones you play most.
 */
internal suspend fun artistRadioArtists(graph: AppGraph, count: Int = ArtistRadioPicks.HOME): List<Artist> {
    if (!graph.library.isSynced) return emptyList()
    val recent = recentlyPlayedArtists(graph, count)
    val similar = recent.take(ArtistRadioPicks.SIMILAR_FROM).map { artist ->
        runCatching { graph.similar.artists(artist.name) }.getOrDefault(emptyList()).mapNotNull { s ->
            val id = s.id ?: return@mapNotNull null
            graph.library.artist(id) ?: Artist(id, s.name, coverArt = s.coverArt)
        }
    }
    val library = graph.library.artists()
    val byKey = library.associateBy { normalizeKey(it.name) }
    val mostPlayed = graph.history.all().groupingBy { normalizeKey(it.artist) }.eachCount()
        .entries.sortedByDescending { it.value }.mapNotNull { byKey[it.key] }
    val picked = mixArtistRadios(recent, similar, mostPlayed + library.shuffled(), count)
    // Their photos as the server has them now, once per run (beta report 95a30366); the tiles pick them up at once.
    graph.scope.launch { graph.artistPhotos.refresh(picked) }
    AppLog.d("Home", "Artist radios: ${picked.size} (${recent.size} played lately, ${similar.sumOf { it.size }} like them)")
    return picked
}

/** Opens a Home row's full list; navigation stays on the main thread (it's called from a tap). */
private fun NavController.openAll(section: app.siansa.ui.HomeSection): () -> Unit = { navigate(section.route) }

private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
}

@Composable
fun PlaylistTile(playlist: Playlist, onClick: () -> Unit) {
    AlbumTile(
        name = playlist.name,
        subtitle = "${playlist.songCount} songs" + (playlist.owner?.let { " · $it" } ?: ""),
        coverArt = playlist.coverArt,
        onClick = onClick,
        modifier = Modifier.width(150.dp),
    )
}

@Composable
private fun HeroMix(
    mix: Mix,
    busy: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onRegenerate: () -> Unit,
    /** The song Play carries on from, when the mix was left part-way (issue #269). */
    resume: Song? = null,
) {
    val colors = tileColors(mix.selectorId)
    Box(
        Modifier
            .fillMaxWidth()
            // Panels on Home keep a little more room around them (issue #312).
            .padding(horizontal = Space.Page, vertical = Space.Medium)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(colors.first, colors.second)))
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(Space.Page)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverMosaic(mix, Modifier.size(104.dp))
                Spacer(Modifier.width(Space.Page))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (mix.aiNamed) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(14.dp), tint = Color.White)
                            Spacer(Modifier.width(Space.Tight))
                        }
                        Text("Today's mix", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                    }
                    Text(
                        mix.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (resume != null) "${mix.songs.size} tracks · Play carries on from ${resume.title}" else "${mix.songs.size} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (mix.description.isNotBlank()) {
                Spacer(Modifier.height(Space.Medium))
                Text(mix.description, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
            Spacer(Modifier.height(Space.Medium))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onRegenerate) { Icon(Icons.Rounded.Refresh, "Make a new mix", tint = Color.White) }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onShuffle) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = Color.White) }
                FilledIconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent, contentColor = OnAccent),
                ) {
                    Icon(Icons.Rounded.PlayArrow, "Play", Modifier.size(30.dp))
                }
            }
        }
    }
}

@Composable
private fun BuildingCard() {
    Card(Modifier.fillMaxWidth().padding(horizontal = Space.Page, vertical = Space.Small)) {
        Row(Modifier.padding(Space.Page), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(28.dp))
            Spacer(Modifier.width(Space.Page))
            Text("Picking today's mix…", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun CoverMosaic(mix: Mix, modifier: Modifier = Modifier) {
    val covers = mix.songs.mapNotNull { it.cover }.distinct().take(4)
    if (covers.size < 4) {
        Cover(covers.firstOrNull(), 300, modifier)
        return
    }
    Column(modifier) {
        for (row in 0 until 2) {
            Row(Modifier.weight(1f)) {
                for (col in 0 until 2) {
                    val shape = RoundedCornerShape(
                        topStart = if (row == 0 && col == 0) 8.dp else 0.dp,
                        topEnd = if (row == 0 && col == 1) 8.dp else 0.dp,
                        bottomStart = if (row == 1 && col == 0) 8.dp else 0.dp,
                        bottomEnd = if (row == 1 && col == 1) 8.dp else 0.dp,
                    )
                    Cover(covers[row * 2 + col], 150, Modifier.weight(1f).fillMaxSize(), shape)
                }
            }
        }
    }
}

@Composable
fun MixRow(mix: Mix, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.Page, vertical = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverMosaic(mix, Modifier.size(56.dp))
        Spacer(Modifier.width(Space.Medium))
        Column(Modifier.weight(1f)) {
            Text(mix.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${mix.songs.size} tracks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Home's top line: the greeting, and the one button for the source filter and arranging Home. */
/** Roll the dice (issue #347): plays a song from your library you've never played, straight away. */
@Composable
private fun DiceButton() {
    val graph = LocalGraph.current
    val launch = rememberPlayLauncher()
    val scope = rememberCoroutineScope()
    var rolling by remember { mutableStateOf(false) }
    IconButton(
        enabled = !rolling,
        onClick = {
            if (graph.connection.current.offline) {
                launch.notify("You're offline: rolling the dice needs the server")
                return@IconButton
            }
            rolling = true
            scope.launch {
                try {
                    val song = graph.dice.roll()
                    if (song == null) launch.notify("No songs you haven't played turned up")
                    else {
                        graph.player.play(listOf(song))
                        launch.notify("Rolled: ${song.title}" + if (song.artist.isNotBlank()) " – ${song.artist}" else "")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.w("Dice", "Couldn't roll: ${e.message}")
                    launch.notify("Couldn't roll the dice: ${e.message}")
                } finally {
                    rolling = false
                }
            }
        },
        modifier = Modifier.testTag("dice"),
    ) {
        if (rolling) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) // spacing: exact
        else Icon(Icons.Rounded.Casino, "Roll the dice: play a song you've never played")
    }
}

@Composable
internal fun HomeHeader(title: String, modifier: Modifier = Modifier, action: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(start = Space.Page, end = Space.Tight, top = Space.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action()
    }
}

/**
 * The line under a Made for you tile (issue #338): the track count once the mix is made, otherwise what a
 * tap does ([prompt]) or why it can't be made now. Never the mix's description: that's on its own page.
 */
internal fun madeForYouCaption(trackCount: Int?, aiDown: Boolean, prompt: String): String = when {
    trackCount != null -> if (trackCount == 1) "1 track" else "$trackCount tracks"
    aiDown -> "AI backend not available :("
    else -> prompt
}

/** Shortcuts on Home in all, Liked songs included (issue #271). */
internal const val QUICK_COUNT = 8

/**
 * The shortcuts after Liked songs: what was played last first, then [fill] (today's mixes, playlists, recent
 * albums) until there are [count], each page once. Liked songs is never among them: it always comes first.
 */
/** The album a shortcut opens ("album/123"), or null for any other page. */
internal fun recentAlbumId(route: String): String? =
    route.takeIf { it.startsWith("album/") }?.removePrefix("album/")?.substringBefore('?')?.takeIf { it.isNotBlank() }

/** Shortcuts without albums the source filter hides; [shownAlbums] null (not synced yet) keeps them all. */
internal fun shownRecents(played: List<RecentPlay>, shownAlbums: Set<String>?): List<RecentPlay> =
    if (shownAlbums == null) played else played.filter { r -> recentAlbumId(r.route)?.let { it in shownAlbums } ?: true }

internal fun quickShortcuts(played: List<RecentPlay>, fill: List<RecentPlay>, count: Int): List<RecentPlay> =
    (played + fill).filter { it.route != "liked" }.distinctBy { it.route }.take(count)
