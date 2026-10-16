package app.musix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.musix.data.Song

/**
 * The page used for albums, playlists, mixes and radios, laid out like Spotify: the cover's colour
 * runs down from the top into the page, the cover shrinks and fades away as you scroll, and the
 * title bar takes on the cover's colour once the cover is gone.
 */
@Composable
fun CollectionPage(
    nav: NavController,
    title: String,
    color: Color,
    songs: List<Song>?,
    error: String? = null,
    cover: @Composable (Modifier) -> Unit,
    header: @Composable ColumnScope.() -> Unit,
    numbered: Boolean = false,
    showCovers: Boolean = true,
    footer: LazyListScope.() -> Unit = {},
) {
    val graph = LocalGraph.current
    val player by graph.player.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val coverSize = (LocalConfiguration.current.screenWidthDp * 0.62f).dp
    val coverPx = with(density) { coverSize.toPx() }
    val scrolled by remember {
        derivedStateOf { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 100_000f }
    }
    val progress = (scrolled / coverPx).coerceIn(0f, 1f)
    val barAlpha = ((scrolled - coverPx * 0.8f) / (coverPx * 0.3f)).coerceIn(0f, 1f)
    val surface = MaterialTheme.colorScheme.surface

    Box(Modifier.fillMaxSize().background(surface)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomBarPadding(32.dp))) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(0f to color, 0.75f to color.copy(alpha = 0.35f), 1f to surface))
                        .statusBarsPadding()
                        .padding(top = 56.dp, start = 16.dp, end = 16.dp),
                ) {
                    // The cover stays a little behind the scroll, shrinking and fading as it goes.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = scrolled.coerceAtMost(coverPx) * 0.45f
                                val scale = 1f - progress * 0.22f
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - progress * progress
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        cover(Modifier.size(coverSize).shadow(18.dp, RoundedCornerShape(6.dp)))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    header()
                    Spacer(Modifier.height(4.dp))
                    ActionRow(songs.orEmpty())
                }
            }
            when {
                songs == null && error == null -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
                }
                error != null -> item { ErrorText(error) }
                songs != null -> {
                    val multiDisc = numbered && songs.mapNotNull { it.disc }.distinct().size > 1
                    itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }) { index, song ->
                        SongRow(
                            song = song,
                            onClick = { graph.player.play(songs, index) },
                            leading = if (numbered) song.track?.let { t -> if (multiDisc && song.disc != null) "${song.disc}.$t" else "$t" } ?: "${index + 1}" else null,
                            highlighted = song.id == player.current?.id,
                            showCover = showCovers,
                        )
                    }
                }
            }
            footer()
        }

        // Title bar: just a back button over the colour, then a solid bar in the cover's colour.
        Row(
            Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = barAlpha))
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { nav.popBackStack() },
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.3f * (1f - barAlpha))),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = barAlpha },
            )
        }
    }
}

/** Queue all on the left; shuffle and the big round play button on the right. */
@Composable
private fun ActionRow(songs: List<Song>) {
    val graph = LocalGraph.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { songs.forEach { graph.player.addToQueue(it) } }, enabled = songs.isNotEmpty()) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add all to queue", Modifier.size(28.dp))
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { graph.player.play(songs, shuffle = true) }, enabled = songs.isNotEmpty()) {
            Icon(Icons.Rounded.Shuffle, "Shuffle", tint = Accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (songs.isNotEmpty()) Accent else Accent.copy(alpha = 0.4f))
                .clickable(enabled = songs.isNotEmpty()) { graph.player.play(songs) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, "Play", tint = Color(0xFF2A1700), modifier = Modifier.size(32.dp))
        }
    }
}

/** A small "meta" line under the title: artist avatar and name, then details. */
@Composable
fun CollectionByline(name: String?, avatarCover: String?, onClick: (() -> Unit)?, details: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!name.isNullOrBlank()) {
            Row(
                Modifier.clickable(enabled = onClick != null) { onClick?.invoke() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (avatarCover != null) {
                    Cover(avatarCover, 80, Modifier.size(26.dp), CircleShape)
                    Spacer(Modifier.width(8.dp))
                }
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
        Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A gradient square with a title, for generated lists that have no cover of their own. */
@Composable
fun GeneratedCover(title: String, colors: Pair<Color, Color>, modifier: Modifier) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.linearGradient(listOf(colors.first, colors.second))),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = Color.White,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
        )
    }
}
