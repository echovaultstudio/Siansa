package app.musix.ui

import androidx.compose.material.icons.rounded.Shuffle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.musix.data.Lyrics
import app.musix.data.Playlist
import app.musix.data.PlaylistEditor
import app.musix.data.Song
import app.musix.data.cover
import app.musix.net.formatSampleRate
import app.musix.playback.leftMs
import app.musix.playback.sleepTimeShort
import kotlin.math.max

// ---- Pure parts, unit tested ----

/**
 * The lines shown on the lyrics preview card: for synced lyrics the line playing now and the next few
 * (from the start before the singing begins), otherwise the first lines. Blank lines are skipped.
 */
fun lyricsPreview(lyrics: Lyrics, positionMs: Long, count: Int = 4): List<String> {
    val lines = lyrics.lines.filter { it.text.isNotBlank() }
    if (lines.isEmpty()) return emptyList()
    if (!lyrics.synced) return lines.take(count).map { it.text }
    val current = lines.indexOfLast { (it.startMs ?: Long.MAX_VALUE) <= positionMs }.coerceAtLeast(0)
    val from = current.coerceAtMost(max(0, lines.size - count))
    return lines.drop(from).take(count).map { it.text }
}

/** Whether there are lyrics to show: at least one line with words (#294). */
fun hasLyrics(lyrics: Lyrics?): Boolean = lyrics?.lines?.any { it.text.isNotBlank() } == true

/** "12 albums · 148 songs in your library", or null when the artist isn't in the library. */
fun artistLibraryLine(albums: Int, songs: Int): String? {
    if (albums <= 0 && songs <= 0) return null
    val parts = listOfNotNull(
        albums.takeIf { it > 0 }?.let { if (it == 1) "1 album" else "$it albums" },
        songs.takeIf { it > 0 }?.let { if (it == 1) "1 song" else "$it songs" },
    )
    return parts.joinToString(" · ") + " in your library"
}

/** Playlists you can add songs to: your own, and not smart playlists (Navidrome only lets the owner change a playlist). */
fun editablePlaylists(playlists: List<Playlist>, user: String): List<Playlist> =
    playlists.filter { PlaylistEditor.canEdit(it, user) }.sortedBy { it.name.lowercase() }

// ---- Now playing's ⋮ menu ----

/**
 * The ⋮ menu in now playing, a sheet like Spotify's: the song at the top, then what you can do with it.
 * Share isn't offered (the music is on your own server), and the queue opens from its own button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMenu(
    song: Song,
    onDismiss: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenDetails: () -> Unit,
    onOpenSongPath: () -> Unit,
    onOpenSimilarSongs: () -> Unit,
    onOpenSimilarAlbums: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    notify: (String) -> Unit,
) {
    val graph = LocalGraph.current
    val heartChanges by graph.favorites.changes.collectAsStateWithLifecycle()
    val liked = heartChanges.let { graph.favorites.isLiked(song) }
    val radioSeed by graph.songRadio.seed.collectAsStateWithLifecycle()
    var pickPlaylist by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    MusixDarkTheme {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Space.Small)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.Page, vertical = Space.Tight), verticalAlignment = Alignment.CenterVertically) {
                    Cover(song.cover, 150, Modifier.size(48.dp), RoundedCornerShape(4.dp))
                    Spacer(Modifier.width(Space.Medium))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(Modifier.padding(top = Space.Medium, bottom = Space.Tight))
                MenuRow(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Remove from Liked songs" else "Add to Liked songs", tint = if (liked) Accent else null) {
                    graph.favorites.toggle(song)
                    onDismiss()
                }
                MenuRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") { pickPlaylist = true }
                MenuRow(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") {
                    graph.player.addToQueue(song)
                    notify("Added to the queue")
                    onDismiss()
                }
                // Shuffle moved here from beside the controls (issue #370); lit while it's on.
                val shuffled = graph.player.state.collectAsStateWithLifecycle().value.shuffle
                MenuRow(Icons.Rounded.Shuffle, if (shuffled) "Shuffle: on" else "Shuffle: off", tint = if (shuffled) Accent else null, tag = "menuShuffle") {
                    graph.player.toggleShuffle()
                    notify(if (shuffled) "Shuffle off" else "Shuffle on")
                    onDismiss()
                }
                MenuDivider()
                if (song.albumId != null) MenuRow(Icons.Rounded.Album, "Go to album") { onDismiss(); onOpenAlbum() }
                if (song.artistId != null || song.artists.isNotEmpty()) {
                    MenuRow(Icons.Rounded.Person, if (song.artists.size > 1) "Go to artists" else "Go to artist") { onDismiss(); onOpenArtist() }
                }
                MenuRow(Icons.Rounded.Radio, if (radioSeed != null) "Stop song radio" else "Start song radio", tint = if (radioSeed != null) Accent else null) {
                    if (radioSeed != null) {
                        graph.songRadio.stop()
                        notify("Song radio off")
                    } else if (graph.connection.current.aiProblem(graph.settings.current) != null && graph.settings.current.audioMuseReady) {
                        notify("AI backend is currently not available :( ${graph.connection.current.aiProblem(graph.settings.current)}")
                    } else {
                        // Steady or DJ first (issue #337); the chooser starts the radio.
                        RadioChooser.ask(song)
                    }
                    onDismiss()
                }
                if (graph.settings.current.audioMuseReady) {
                    MenuRow(Icons.Rounded.AltRoute, "Song path…") { onDismiss(); onOpenSongPath() }
                }
                MenuRow(Icons.Rounded.GraphicEq, "Similar songs") { onDismiss(); onOpenSimilarSongs() }
                if (graph.settings.current.audioMuseReady && song.albumId != null) {
                    MenuRow(rememberSimilarAlbumsIcon(), "Similar albums") { onDismiss(); onOpenSimilarAlbums() }
                }
                MenuDivider()
                MenuRow(Icons.Rounded.Subtitles, "Lyrics") { onDismiss(); onOpenLyrics() }
                MenuRow(Icons.Rounded.Info, "Song details") { onDismiss(); onOpenDetails() }
                val timer by graph.sleepTimer.state.collectAsStateWithLifecycle()
                val now = rememberTimerNow(timer != null)
                MenuRow(
                    Icons.Rounded.Bedtime,
                    "Sleep timer",
                    tint = if (timer != null) Accent else null,
                    tag = timer?.leftMs(now)?.let { sleepTimeShort(it) } ?: if (timer != null) "On" else null,
                ) { onDismiss(); onOpenSleepTimer() }
            }
        }
        if (pickPlaylist) {
            AddToPlaylistDialog(listOf(song), onDone = { message ->
                pickPlaylist = false
                message?.let(notify)
                if (message != null) onDismiss()
            })
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, tint: Color? = null, enabled: Boolean = true, tag: String? = null, onClick: () -> Unit) =
    MenuRow(rememberVectorPainter(icon), label, tint, enabled, tag, onClick)

/** A row of the menu with a drawn icon, such as [SimilarAlbumsPainter]. */
@Composable
private fun MenuRow(icon: Painter, label: String, tint: Color? = null, enabled: Boolean = true, tag: String? = null, onClick: () -> Unit) {
    val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .height(MENU_ROW_HEIGHT)
            .padding(horizontal = Space.Page),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: color.copy(alpha = if (enabled) 0.85f else 0.45f), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(Space.Page))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint ?: color, modifier = Modifier.weight(1f))
        if (tag != null) {
            Text(
                tag.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f))
                    .padding(horizontal = Space.Small, vertical = Space.Tight),
            )
        }
    }
}

internal val MENU_ROW_HEIGHT = 48.dp

@Composable
private fun MenuDivider() {
    HorizontalDivider(Modifier.padding(horizontal = Space.Page, vertical = Space.Tight), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

// ---- Cards under the player ----

private val CardShape = RoundedCornerShape(12.dp)
private val CardSurface = Color(0xFF1C1630)

/** The lyrics preview card under the player, in a colour from the cover; hidden when the song has no lyrics. */
@Composable
fun LyricsPreviewCard(lyrics: Lyrics?, tint: Color, onOpen: () -> Unit) {
    val loaded = lyrics ?: return
    val position by rememberPosition()
    val lines = lyricsPreview(loaded, position)
    if (lines.isEmpty()) return
    Column(
        Modifier
            .padding(horizontal = Space.Page)
            .fillMaxWidth()
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(lerp(tint, Color.Black, 0.15f), lerp(tint, Color.Black, 0.45f))))
            .clickable(onClick = onOpen)
            .padding(Space.Page),
    ) {
        CardTitle("Lyrics preview")
        lines.forEachIndexed { i, line ->
            Text(
                line,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = if (i == lines.lastIndex) 0.4f else 0.95f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = Space.Small),
            )
        }
        Spacer(Modifier.height(Space.Small))
        Pill("Show lyrics", filled = true, onClick = onOpen)
    }
}

/** About the artist: their photo, how much of them is in the library, the start of the biography. */
@Composable
fun AboutArtistCard(song: Song, onOpenArtist: () -> Unit) {
    val graph = LocalGraph.current
    val artistId = song.artistId ?: song.artists.firstOrNull()?.id ?: return
    val local by loadState("artist-$artistId") {
        graph.library.artist(artistId)?.let { a -> Triple(a, graph.library.albumsOf(a).size, graph.library.songsOf(a).size) }
    }
    val info by loadState("info-$artistId") { graph.subsonic.artistInfo(artistId) }
    val artist = local.valueOrNull()
    val name = artist?.first?.name ?: song.artists.firstOrNull()?.name ?: song.artist
    Column(Modifier.padding(horizontal = Space.Page).fillMaxWidth().clip(CardShape).background(CardSurface).clickable(onClick = onOpenArtist)) {
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            // Navidrome's artist image through its cover endpoint (issue #206).
            Cover(artist?.first?.coverArt ?: "ar-$artistId", LARGE_COVER, Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.45f to Color.Transparent, 1f to CardSurface),
                ),
            )
            CardTitle("About the artist", Modifier.padding(Space.Page))
        }
        Column(Modifier.padding(start = Space.Page, end = Space.Page, bottom = Space.Page)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(Space.Medium))
                Pill("Open artist", filled = false, onClick = onOpenArtist)
            }
            artist?.let { (_, albums, songs) -> artistLibraryLine(albums, songs) }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.62f), modifier = Modifier.padding(top = Space.Tight))
            }
            info.valueOrNull()?.biography?.let { bio ->
                Text(
                    bio,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Space.Medium),
                )
            }
        }
    }
}

/** About this song: what Song details knows, in a small grid. Hidden until Navidrome answers. */
@Composable
fun AboutSongCard(song: Song, onOpenDetails: () -> Unit) {
    val graph = LocalGraph.current
    val details by loadState("details-${song.id}") { graph.subsonic.songDetails(song.id) }
    val d = details.valueOrNull() ?: return
    val cells = listOfNotNull(
        d.codec?.let { codec -> "Format" to listOfNotNull(codec, d.bitDepth?.let { "$it-bit" }).joinToString(" · ") },
        d.sampleRateHz?.let { "Sample rate" to formatSampleRate(it) },
        d.bitRateKbps?.let { "Bit rate" to "%,d kbit/s".format(it) },
        d.playCount?.let { "Plays" to "$it" },
    )
    if (cells.isEmpty()) return
    Column(Modifier.padding(horizontal = Space.Page).fillMaxWidth().clip(CardShape).background(CardSurface).clickable(onClick = onOpenDetails).padding(Space.Page)) {
        CardTitle("About this song")
        cells.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = Space.Medium)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.45f), letterSpacing = 0.4.sp)
                        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, modifier = modifier.padding(bottom = Space.Medium))
}

@Composable
private fun Pill(text: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (filled) Color(0xFF111111) else Color.White,
        modifier = Modifier
            .clip(shape)
            .then(if (filled) Modifier.background(Color.White) else Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), shape))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.Page, vertical = Space.Small),
    )
}

/** The small header that appears once the player scrolls up to the cards: song, heart and play/pause. */
@Composable
fun PlayerMiniHeader(song: Song?, isPlaying: Boolean, progress: Float, liked: Boolean, onHeart: () -> Unit, onPlayPause: () -> Unit, onTop: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF221C2E)).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onTop).padding(start = Space.Page, end = Space.Tight, top = Space.Small, bottom = Space.Small), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(song?.title.orEmpty(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song?.artist.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.62f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onHeart, enabled = song != null) {
                Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Remove from liked songs" else "Like", tint = if (liked) Accent else Color.White)
            }
            IconButton(onClick = onPlayPause) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(Color.White))
        }
    }
}

// ---- Lyrics, opened ----

/**
 * Lyrics over the whole screen, on a colour from the cover: the line playing now white, lines already sung
 * dimmed, and the ones to come in a colour that stays readable on that background ([lyricsColors]). Synced lyrics follow the song and a tap on a line jumps there; plain
 * lyrics just scroll. The seek bar and controls stay at the bottom.
 */
@Composable
fun LyricsScreen(song: Song?, tint: Color, onClose: () -> Unit, menu: () -> Unit, controls: @Composable () -> Unit) {
    BackHandler(onBack = onClose)
    val graph = LocalGraph.current
    val position by rememberPosition()
    val lyrics by loadState("lyrics-${song?.id}") { song?.let { graph.subsonic.lyrics(it) } }
    val colors = LyricsTextColors
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(lyricsBackground(tint)))
            // Covers the player completely: touches don't reach it.
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.Tight, vertical = Space.Tight), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close lyrics", Modifier.size(32.dp)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(song?.title.orEmpty(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song?.artist.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = menu) { Icon(Icons.Rounded.MoreVert, "More") }
        }
        // The lines fade out at the top and bottom instead of being cut off.
        val fadePx = with(LocalDensity.current) { 40.dp.toPx() }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val stops = lyricsFadeStops(size.height, fadePx).map { (at, alpha) -> at to Color.Black.copy(alpha = alpha) }
                    drawRect(Brush.verticalGradient(*stops.toTypedArray()), blendMode = BlendMode.DstIn)
                }
                .padding(horizontal = Space.Section),
        ) {
            LoadBox(lyrics) { loaded ->
                if (loaded == null || loaded.lines.isEmpty()) {
                    Text("No lyrics for this song", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.Center))
                    return@LoadBox
                }
                if (!loaded.synced) {
                    Text(
                        loaded.lines.joinToString("\n") { it.text },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.Page),
                    )
                    return@LoadBox
                }
                val listState = rememberLazyListState()
                val current = loaded.lines.indexOfLast { (it.startMs ?: Long.MAX_VALUE) <= position }
                LaunchedEffect(current) { if (current >= 0) listState.animateScrollToItem(max(0, current - 2)) }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = Space.Page)) {
                    itemsIndexed(loaded.lines) { index, line ->
                        // Word-timed lyrics: the line playing now fills in word by word (or syllable by syllable).
                        if (index == current && line.words.isNotEmpty()) {
                            KaraokeLine(line, colors, Modifier.fillMaxWidth().clickable { line.startMs?.let { graph.player.seekTo(it) } }.padding(vertical = Space.Small))
                            return@itemsIndexed
                        }
                        Text(
                            line.text.ifBlank { "♪" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                index == current -> colors.current
                                index < current -> colors.sung
                                else -> colors.upcoming
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { line.startMs?.let { graph.player.seekTo(it) } }
                                .padding(vertical = Space.Small),
                        )
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = Space.Section).padding(bottom = Space.Page)) { controls() }
    }
}

/** A small round button with a circle behind it, for the queue at the bottom right of the player. */
/**
 * The lyrics button under the controls: a speech bubble with quote marks. Greyed out, and not tappable,
 * when the song has no lyrics (#294).
 */
@Composable
fun LyricsButton(enabled: Boolean, onClick: () -> Unit) {
    val color = Color.White.copy(alpha = if (enabled) 0.9f else 0.3f)
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, onClickLabel = "Show lyrics", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.ChatBubble, if (enabled) "Lyrics" else "No lyrics for this song", tint = color)
        // The quote marks, cut out of the bubble in the player's dark colour, a little above centre where the
        // bubble's body is.
        Icon(
            Icons.Rounded.FormatQuote,
            null,
            tint = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier.size(14.dp).offset(y = (-2).dp), // spacing: exact
        )
    }
}

@Composable
fun RoundIcon(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White.copy(alpha = 0.9f))
    }
}

/** How a word-timed line looks at [positionMs]: how many of its words have been sung. */
fun sungWords(line: app.musix.data.LyricLine, positionMs: Long): Int = line.words.count { it.startMs <= positionMs }

/**
 * The line playing now, for lyrics timed word by word: sung words white, the rest of the line in the colour
 * of the lines to come. It
 * reads the player's position every few frames, so the words keep up with the singing.
 */
@Composable
private fun KaraokeLine(line: app.musix.data.LyricLine, colors: LyricsColors, modifier: Modifier) {
    val graph = LocalGraph.current
    var position by remember { mutableStateOf(graph.player.positionMs()) }
    LaunchedEffect(line) {
        while (true) {
            position = graph.player.positionMs()
            kotlinx.coroutines.delay(40)
        }
    }
    val sung = sungWords(line, position)
    val text = androidx.compose.ui.text.buildAnnotatedString {
        line.words.forEachIndexed { i, word ->
            withStyle(androidx.compose.ui.text.SpanStyle(color = if (i < sung) colors.current else colors.upcoming)) { append(word.text) }
        }
    }
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, modifier = modifier)
}
