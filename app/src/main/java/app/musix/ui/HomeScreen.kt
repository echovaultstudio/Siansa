package app.musix.ui

import app.musix.data.cover
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
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
import app.musix.AppGraph
import app.musix.data.Album
import app.musix.data.Artist
import app.musix.data.Mix
import app.musix.data.Playlist
import app.musix.data.Selector
import app.musix.log.AppLog
import app.musix.mix.moodRadios
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.produceState
import app.musix.mix.MixGenerator
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
    val selectors by graph.selectors.state.collectAsStateWithLifecycle()
    val status by graph.library.status.collectAsStateWithLifecycle()
    val loaded by graph.library.loaded.collectAsStateWithLifecycle()
    val version by graph.library.version.collectAsStateWithLifecycle()
    val synced = status != null
    var current by remember { mutableStateOf<Mix?>(null) }
    var busySelector by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun build(selector: Selector?, force: Boolean, open: Boolean) {
        if (busySelector != null) return
        // In the app's own scope, so leaving Home doesn't cancel a mix half-way.
        graph.scope.launch(Dispatchers.Main) {
            busySelector = selector?.id ?: "current"
            error = null
            try {
                val mix = if (selector == null) graph.generator.ensureCurrent(force) else graph.generator.ensure(selector, force)
                if (selector == null || selector.id == current?.selectorId) current = mix
                if (mix == null) error = "No vibe covers this hour. Add one under Vibes."
                if (open && mix != null) nav.navigate("mix/${mix.id}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Home", "Mix failed: ${e.message}")
                error = e.message ?: "Couldn't build the mix"
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

    LaunchedEffect(hour) {
        graph.mixes.all()
        graph.selectors.all()
        current = null
        build(null, force = false, open = false)
    }
    // Today's personal mixes are made quietly in the background once the page is up.
    LaunchedEffect(Unit) {
        delay(1500)
        val today = LocalDate.now().toString()
        for (kind in MixGenerator.Personal.entries) {
            if (graph.mixes.get("$today-${kind.id}") != null) continue
            while (busySelector != null || personalBusy != null) delay(500)
            buildPersonal(kind, open = false, quiet = true)
            delay(300)
        }
    }

    val today = LocalDate.now().toString()
    val personalIds = MixGenerator.Personal.entries.map { it.id }.toSet()
    // Only what fits now: the vibe for this hour and the personal mixes, not this morning's mix in the evening.
    val applicable = selectors.orEmpty().filter { it.enabled && it.covers(hour) }
    val todaysMixes = mixes.orEmpty().filter { m ->
        m.date == today && (m.selectorId in personalIds || applicable.any { it.id == m.selectorId })
    }
    val playlists by loadState("playlists") {
        runCatching { graph.subsonic.playlists() }
            .onSuccess { graph.library.updatePlaylists(it) }
            .getOrElse { if (graph.library.isSynced) graph.library.playlists() else throw it }
    }
    val recentAlbums by loadState(version to "recent") { recentlyPlayedAlbums(graph) }
    val recentArtists by loadState(version to "artists") { recentlyPlayedArtists(graph) }
    val newest by loadState(version to "newest") {
        if (graph.library.isSynced) graph.library.albums("newest").take(20)
        else graph.subsonic.albums("newest", 20, 0).distinctBy { it.name + "|" + it.artist }
    }
    val lovedBefore by loadState(today) { graph.insights.rediscover() }
    val rediscover by loadState(version to "random") {
        if (graph.library.isSynced) graph.library.randomAlbums(20)
        else graph.subsonic.albums("random", 20, 0).distinctBy { it.name + "|" + it.artist }
    }
    val genres by loadState(version to "genres") { if (graph.library.isSynced) graph.library.genres(12) else emptyList() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomBarPadding(24.dp))) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(greeting(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                CastButton()
            }
        }

        // Quick access, like the top grid in Spotify: today's mixes, playlists and recent albums.
        item {
            val quick = buildList<Triple<String, String?, () -> Unit>> {
                add(Triple("Liked songs", null) { nav.navigate("liked") })
                todaysMixes.take(2).forEach { m -> add(Triple(m.title, m.songs.firstOrNull()?.cover) { nav.navigate("mix/${m.id}") }) }
                playlists.valueOrNull().orEmpty().take(2).forEach { p -> add(Triple(p.name, p.coverArt) { nav.navigate("playlist/${p.id}") }) }
                recentAlbums.valueOrNull().orEmpty().take(6).forEach { a -> add(Triple(a.name, a.coverArt) { nav.navigate("album/${a.id}") }) }
            }.take(6)
            if (quick.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (title, cover, open) ->
                                val colors = if (title == "Liked songs") Color(0xFF4B2BD6) to Color(0xFF8FD3C8) else tileColors(title)
                                QuickTile(title, cover, colors, open, Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item {
            val mix = current
            when {
                mix != null -> HeroMix(
                    mix = mix,
                    busy = busySelector == "current",
                    onOpen = { nav.navigate("mix/${mix.id}") },
                    onPlay = { graph.player.play(mix.songs) },
                    onShuffle = { graph.player.play(mix.songs, shuffle = true) },
                    onRegenerate = { build(null, force = true, open = false) },
                )
                busySelector == "current" -> BuildingCard()
                else -> Unit
            }
        }
        error?.let { message ->
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ErrorText(message, Modifier)
                    TextButton(onClick = { build(null, force = false, open = false) }) { Text("Try again") }
                }
            }
        }

        item { MoodBox(nav, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

        if (loaded && !synced) {
            item { Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { SyncPrompt(nav) } }
        }

        // Made for you: the vibe for this hour, then mixes from your own listening.
        item {
            val tiles: List<Pair<String, @Composable () -> Unit>> = buildList {
                applicable.forEach { selector ->
                    add(selector.id to @Composable {
                        val mix = todaysMixes.firstOrNull { it.selectorId == selector.id }
                        GradientTile(
                            title = mix?.title ?: "${selector.name} mix",
                            colors = tileColors(selector.id),
                            coverArt = mix?.songs?.firstOrNull()?.coverArt,
                            subtitle = selector.name,
                            caption = if (mix != null) "${mix.songs.size} tracks · until %02d:00".format(selector.endHour) else "Tap to make today's mix",
                            busy = busySelector == selector.id,
                            onClick = { if (mix != null) nav.navigate("mix/${mix.id}") else build(selector, force = false, open = true) },
                        )
                    })
                }
                MixGenerator.Personal.entries.forEach { kind ->
                    add(kind.id to @Composable {
                        val mix = todaysMixes.firstOrNull { it.selectorId == kind.id }
                        GradientTile(
                            title = kind.title,
                            colors = tileColors(kind.id),
                            coverArt = mix?.songs?.firstOrNull()?.coverArt,
                            subtitle = "For you",
                            caption = if (mix != null) "${mix.songs.size} tracks · ${mix.description}" else "Tap to make it",
                            busy = personalBusy == kind.id,
                            onClick = { if (mix != null) nav.navigate("mix/${mix.id}") else buildPersonal(kind, open = true) },
                        )
                    })
                }
            }
            Shelf("Made for you", tiles, key = { it.first }) { it.second() }
        }

        // Albums you loved a while ago but haven't played since.
        item {
            Shelf("Rediscover", lovedBefore.valueOrNull().orEmpty(), key = { it.album.id }) { r ->
                val ago = r.lastPlayed?.let { java.time.temporal.ChronoUnit.MONTHS.between(it, LocalDate.now()) }
                AlbumTile(
                    name = r.album.name,
                    subtitle = when {
                        ago != null && ago >= 12 -> "Last played ${ago / 12} year${if (ago >= 24) "s" else ""} ago"
                        ago != null -> "Last played $ago months ago"
                        else -> r.album.artist
                    },
                    coverArt = r.album.coverArt,
                    onClick = { nav.navigate("album/${r.album.id}") },
                    modifier = Modifier.width(150.dp),
                )
            }
        }

        item {
            Shelf("Radios", moodRadios, key = { it.id }) { radio ->
                GradientTile(
                    title = radio.name,
                    colors = Color(radio.colors.first) to Color(radio.colors.second),
                    size = 140.dp,
                    caption = "Sound radio",
                    onClick = { nav.openMoodRadio(radio.id) },
                )
            }
        }

        item {
            Shelf("Your playlists", playlists.valueOrNull().orEmpty(), key = { it.id }) { playlist ->
                PlaylistTile(playlist) { nav.navigate("playlist/${playlist.id}") }
            }
        }

        item {
            Shelf("Recently played", recentAlbums.valueOrNull().orEmpty(), key = { it.id }) { album ->
                AlbumTile(album.name, album.artist, album.coverArt, { nav.navigate("album/${album.id}") }, Modifier.width(150.dp))
            }
        }

        item {
            Shelf("Artist radios", recentArtists.valueOrNull().orEmpty(), key = { it.id }) { artist ->
                AlbumTile(
                    name = "${artist.name} radio",
                    subtitle = "With similar artists",
                    coverArt = artist.coverArt,
                    onClick = {
                        launch.progressive("${artist.name} radio", { graph.radios.artistStart(artist) }) { graph.radios.artistMore(artist, it) }
                    },
                    modifier = Modifier.width(140.dp),
                    round = true,
                )
            }
        }

        item {
            Shelf("Recently added", newest.valueOrNull().orEmpty(), key = { it.id }) { album ->
                AlbumTile(album.name, album.artist, album.coverArt, { nav.navigate("album/${album.id}") }, Modifier.width(150.dp))
            }
        }

        item {
            Shelf("Your genres", genres.valueOrNull().orEmpty(), key = { it.name }) { genre ->
                GradientTile(
                    title = genre.name,
                    colors = tileColors(genre.name),
                    size = 130.dp,
                    caption = "${genre.songCount} songs",
                    onClick = { nav.openGenreRadio(genre.name) },
                )
            }
        }

        item {
            Shelf("Random picks", rediscover.valueOrNull().orEmpty(), key = { it.id }) { album ->
                AlbumTile(album.name, album.artist, album.coverArt, { nav.navigate("album/${album.id}") }, Modifier.width(150.dp))
            }
        }
    }
}

private suspend fun recentlyPlayedAlbums(graph: AppGraph): List<Album> {
    if (!graph.library.isSynced) return emptyList()
    val songs = graph.library.songs(graph.history.recent(300).map { it.id }.distinct())
    val albumIds = songs.mapNotNull { it.albumId }.distinct().take(15)
    return albumIds.mapNotNull { graph.library.album(it) }.distinctBy { it.id }
}

private suspend fun recentlyPlayedArtists(graph: AppGraph): List<Artist> {
    if (!graph.library.isSynced) return emptyList()
    val songs = graph.library.songs(graph.history.recent(300).map { it.id }.distinct())
    return songs.distinctBy { it.artistId ?: it.artist }.take(10).mapNotNull { s ->
        s.artistId?.let { graph.library.artist(it) } ?: graph.library.artistByName(s.artist)
    }.distinctBy { it.id }
}

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
) {
    val colors = tileColors(mix.selectorId)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(colors.first, colors.second)))
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverMosaic(mix, Modifier.size(104.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (mix.aiNamed) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(14.dp), tint = Color.White)
                            Spacer(Modifier.width(4.dp))
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
                    Text("${mix.songs.size} tracks", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }
            }
            if (mix.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(mix.description, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
            Spacer(Modifier.height(12.dp))
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
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent, contentColor = Color(0xFF2A1700)),
                ) {
                    Icon(Icons.Rounded.PlayArrow, "Play", Modifier.size(30.dp))
                }
            }
        }
    }
}

@Composable
private fun BuildingCard() {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverMosaic(mix, Modifier.size(56.dp))
        Spacer(Modifier.width(12.dp))
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
