package app.musix.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.musix.data.Mix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(nav: NavController) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val mixes by graph.mixes.state.collectAsStateWithLifecycle()
    var current by remember { mutableStateOf<Mix?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun build(force: Boolean) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                current = graph.generator.ensureCurrent(force)
                if (current == null) error = "No vibe covers this hour. Add one under Vibes."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Couldn't build the mix"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        graph.mixes.all()
        build(force = false)
    }

    val today = LocalDate.now().toString()
    val otherToday = mixes.orEmpty().filter { it.date == today && it.id != current?.id }
    val library by graph.library.index.collectAsStateWithLifecycle()
    val loaded by graph.library.loaded.collectAsStateWithLifecycle()
    // From the synced library when there is one; straight from Navidrome otherwise.
    val newest by loadState(library to "newest") {
        library?.sortedAlbums("newest")?.take(20) ?: graph.subsonic.albums("newest", 20, 0).distinctBy { it.name + "|" + it.artist }
    }
    val random by loadState(library to "random") {
        library?.sortedAlbums("random")?.take(20) ?: graph.subsonic.albums("random", 20, 0).distinctBy { it.name + "|" + it.artist }
    }

    Screen(
        title = greeting(),
        nav = null,
        actions = {
            IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Rounded.Settings, "Settings") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            item {
                val mix = current
                when {
                    mix != null -> HeroMix(
                        mix = mix,
                        busy = busy,
                        onOpen = { nav.navigate("mix/${mix.id}") },
                        onPlay = { graph.player.play(mix.songs) },
                        onShuffle = { graph.player.play(mix.songs, shuffle = true) },
                        onRegenerate = { build(force = true) },
                    )
                    busy -> BuildingCard()
                    else -> Unit
                }
            }
            error?.let { message ->
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        ErrorText(message, Modifier)
                        TextButton(onClick = { build(force = false) }) { Text("Try again") }
                    }
                }
            }
            if (otherToday.isNotEmpty()) {
                item { SectionTitle("Earlier today") }
                items(otherToday, key = { it.id }) { mix ->
                    MixRow(mix) { nav.navigate("mix/${mix.id}") }
                }
            }
            if (loaded && library == null) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SyncPrompt(nav)
                    }
                }
            }
            item { SectionTitle("Recently added") }
            item { AlbumStrip(newest, nav) }
            item { SectionTitle("Rediscover") }
            item { AlbumStrip(random, nav) }
        }
    }
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
private fun HeroMix(
    mix: Mix,
    busy: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverMosaic(mix, Modifier.size(96.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    if (mix.aiNamed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                null,
                                Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Today's mix", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Text("Today's mix", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        mix.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${mix.songs.size} tracks", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (mix.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(mix.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onPlay) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Play")
                }
                FilledTonalButton(onClick = onShuffle) {
                    Icon(Icons.Rounded.Shuffle, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Shuffle")
                }
                Spacer(Modifier.weight(1f))
                if (busy) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onRegenerate) { Icon(Icons.Rounded.Refresh, "Make a new mix") }
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
    val covers = mix.songs.mapNotNull { it.coverArt }.distinct().take(4)
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

@Composable
private fun AlbumStrip(load: Load<List<app.musix.data.Album>>, nav: NavController) {
    LoadBox(load) { albums ->
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
            items(albums, key = { it.id }) { album ->
                Box(Modifier.width(150.dp)) {
                    AlbumTile(
                        name = album.name,
                        subtitle = album.artist,
                        coverArt = album.coverArt,
                        onClick = { nav.navigate("album/${album.id}") },
                    )
                }
            }
        }
    }
}
