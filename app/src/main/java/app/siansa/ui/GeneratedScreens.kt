package app.siansa.ui

import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.toArgb
import app.siansa.data.LocalPlaylists
import app.siansa.net.AudioMuseApi
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.siansa.net.ApiException
import androidx.compose.foundation.layout.Column
import app.siansa.mix.moodRadios

fun NavController.openMoodRadio(id: String) = navigate("radio/mood/${Uri.encode(id)}")
fun NavController.openGenreRadio(name: String) = navigate("radio/genre/${Uri.encode(name)}")
fun NavController.openCentreRadio(key: String) = navigate("radio/centre/${Uri.encode(key)}")
fun NavController.openMoodPlaylist(text: String) = navigate("mood/${Uri.encode(text)}")

/**
 * The colours of a page whose cover Siansa draws (a radio, a mood playlist, More like this playlist) from its [route],
 * as Home's shortcut to it shows them: its own cover, not one of its songs' (the owner, 10 Nov). Null for any other page.
 */
fun generatedCoverColors(route: String): Pair<Color, Color>? {
    val parts = route.split('/')
    return when {
        parts.size == 3 && parts[0] == "radio" -> {
            val key = java.net.URLDecoder.decode(parts[2], "UTF-8")
            moodRadios.firstOrNull { it.id == key }?.let { Color(it.colors.first) to Color(it.colors.second) }
                ?: if (parts[1] == "genre") genreColors(key) else tileColors(key)
        }
        parts.size == 2 && parts[0] == "blend" -> tileColors("blend-${java.net.URLDecoder.decode(parts[1], "UTF-8")}")
        parts.size == 2 && parts[0] == "mood" -> tileColors(java.net.URLDecoder.decode(parts[1], "UTF-8"))
        else -> null
    }
}

/**
 * A genre's page (the owner, 10 Nov): every song of it on the phone (the ones Siansa Beats hears in it, or the songs' genre tags), to
 * look through. Play, Shuffle or a song start the genre's radio: [GenreRadio.PICKS] songs at random (the song first),
 * and more of the genre as the queue comes to its end; never the whole genre in the queue, as some have thousands.
 */
@Composable
fun GenreScreen(nav: NavController, name: String) {
    GenreScreenContent(nav, name)
}

/**
 * A genre's description on its page: its first [ABOUT_LINES] lines, and "Show more" for the rest when there is more
 * (the owner, 10 Nov: the page shouldn't be filled with text), then where it's from, linked.
 */
@Composable
internal fun GenreAboutText(summary: String, source: String, onSource: (() -> Unit)?) {
    var expanded by rememberSaveable(summary) { mutableStateOf(false) }
    var cut by remember(summary) { mutableStateOf(false) }
    Column(Modifier.animateContentSize()) {
        Text(
            summary,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded) Int.MAX_VALUE else ABOUT_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) cut = it.hasVisualOverflow },
            modifier = Modifier.testTag("genreAbout").then(if (cut || expanded) Modifier.clickable { expanded = !expanded } else Modifier),
        )
        Row(Modifier.padding(top = Space.Small), horizontalArrangement = Arrangement.spacedBy(Space.Page), verticalAlignment = Alignment.CenterVertically) {
            if (cut || expanded) {
                Text(
                    if (expanded) "Show less" else "Show more",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { expanded = !expanded }.testTag("genreAboutMore"),
                )
            }
            Text(
                "From $source",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (onSource != null) Modifier.clickable(onClick = onSource) else Modifier,
            )
        }
    }
}

/** Lines of a genre's description shown before "Show more". */
const val ABOUT_LINES = 3

@Composable
private fun GenreScreenContent(nav: NavController, name: String) {
    val graph = LocalGraph.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val colors = genreColors(name)
    val songs by loadState("genre-songs-$name") { graph.genreRadio.songsOf(name) }
    val aboutState by loadState("genre-about-$name") { graph.beatsGenres.about(name) }
    // The description kept on the phone shows at once (the owner, 10 Nov); the asking above only fills in a new one.
    val keptAbout = remember(name) { graph.beatsGenres.keptAbout(name) }
    val list = songs.valueOrNull()
    CollectionPage(
        nav = nav,
        title = name,
        color = lerp(colors.first, Color.Black, 0.25f),
        songs = list,
        error = (songs as? Load.Err)?.message ?: if (list != null && list.isEmpty()) "No songs of $name on the phone" else null,
        shortcutCover = false,
        djMix = false,
        cover = { m -> GeneratedCover(name, colors, m) },
        radioColour = colors.first,
        // Its name is on its cover: under it only what the genre is, with a little history (Siansa Beats, from
        // Wikipedia, the owner, 10 Nov), or how many songs it has when there's no description.
        showTitle = false,
        coverColour = colors.first,
        barExtras = { RadioHeart(nav, name, colors) },
        header = {
            val about = aboutState.valueOrNull() ?: keptAbout
            if (about != null) {
                val uri = androidx.compose.ui.platform.LocalUriHandler.current
                GenreAboutText(about.summary, about.source, onSource = about.url?.let { url -> { runCatching { uri.openUri(url) } } })
            }
        },
        // One row under it (the owner, 10 Nov): how many songs at the page's left edge, with or without a description,
        // and the heart beside the radio on the right. Both go into the title bar once the row has scrolled under it.
        radioRow = {
            Box(Modifier.weight(1f)) { CollectionByline(null, null, null, list?.let { "%,d songs".format(it.size) } ?: "") }
            RadioHeart(nav, name, colors, onBar = false)
            Spacer(Modifier.width(Space.Small))
        },
        playAs = { first ->
            val all = list.orEmpty()
            if (all.isNotEmpty()) {
                graph.genreRadio.start(name, all, first)
                scope.launch { snack.showSnackbar("$name radio: ${minOf(all.size, app.siansa.mix.GenreRadio.PICKS)} songs, more as they play") }
            }
        },
    )
}

/** A mood or genre radio shown as a playlist first, so you can look, play or shuffle. */
@Composable
fun RadioScreen(nav: NavController, kind: String, key: String) {
    // A genre opens its page of songs, with its radio from there (the owner, 10 Nov).
    if (kind == "genre") return GenreScreen(nav, key)
    val graph = LocalGraph.current
    var round by remember { mutableIntStateOf(0) }
    val mood = moodRadios.firstOrNull { it.id == key }
    // One of AudioMuse's own mood centres (issue #208): "relaxed-2".
    val centre by loadState("centre-$kind-$key") {
        if (kind != "centre") null else graph.audioMuse.moodCentres().firstOrNull { AudioMuseApi.centreKey(it) == key }
    }
    val title = when (kind) {
        "mood" -> mood?.name ?: key
        "centre" -> centre.valueOrNull()?.let { AudioMuseApi.centreTitle(it) } ?: key.substringBefore('-').replaceFirstChar { it.uppercase() }
        else -> key
    }
    // A genre radio in its genre's colours (red for hard, blue or green for calm), as its tile.
    val colors = if (mood != null) Color(mood.colors.first) to Color(mood.colors.second) else if (kind == "genre") genreColors(key) else tileColors(key)
    val songs by loadState(Triple(kind, key, round)) {
        when (kind) {
            "mood" -> {
                graph.connection.current.aiProblem(graph.settings.current)?.let { throw ApiException("AI backend is currently not available :( $it") }
                graph.radios.mood(mood ?: throw IllegalStateException("Unknown radio"))
            }
            "centre" -> {
                val c = graph.audioMuse.moodCentres().firstOrNull { AudioMuseApi.centreKey(it) == key }
                    ?: throw IllegalStateException("AudioMuse doesn't have this mood any more")
                graph.radios.centre(c)
            }
            else -> graph.radios.genre(key)
        }
    }
    CollectionPage(
        nav = nav,
        title = "$title radio",
        color = lerp(colors.first, Color.Black, 0.25f),
        songs = songs.valueOrNull(),
        error = (songs as? Load.Err)?.message,
        saveAsPlaylist = true,
        shortcutCover = false,
        cover = { m -> GeneratedCover(title, colors, m) },
        // Its name is on its cover (the owner, 10 Nov); Play in the cover's colour.
        showTitle = false,
        coverColour = colors.first,
        barExtras = { RadioHeart(nav, "$title radio", colors) },
        header = {
            PicksRow(
                (when (kind) { "mood" -> "Sound radio"; "centre" -> "AudioMuse mood radio"; else -> "Genre radio" }) + (songs.valueOrNull()?.let { " · ${it.size} songs" } ?: ""),
                onNewPicks = { round++ },
            ) { RadioHeart(nav, "$title radio", colors, onBar = false) }
        },
    )
}

/**
 * Song Alchemy (issue #208): new songs that sound like a playlist's songs blended together, made by AudioMuse.
 * Works for Navidrome's playlists and the phone's own.
 */
@Composable
fun BlendScreen(nav: NavController, playlistId: String) {
    val graph = LocalGraph.current
    var round by remember { mutableIntStateOf(0) }
    val source by loadState("blend-source-$playlistId") {
        if (LocalPlaylists.isLocal(playlistId)) {
            val local = graph.localPlaylists.get(playlistId) ?: throw IllegalStateException("This playlist isn't on this phone any more")
            local.name to local.songs
        } else {
            val detail = graph.playlistCache.get(playlistId)
            detail.playlist.name to detail.songs
        }
    }
    val name = source.valueOrNull()?.first
    // Starts once the playlist itself has loaded; "New picks" asks again.
    val loaded = source.valueOrNull()
    val songs by loadState(Triple(playlistId, round, loaded != null)) {
        loaded?.let { graph.radios.blend(it.second) }
    }
    val title = name?.let { "More like $it" } ?: "More like this playlist"
    val colors = tileColors("blend-$playlistId")
    CollectionPage(
        nav = nav,
        title = title,
        color = lerp(colors.first, Color.Black, 0.25f),
        songs = songs.valueOrNull(),
        error = (source as? Load.Err)?.message ?: (songs as? Load.Err)?.message,
        saveAsPlaylist = true,
        shortcutCover = false,
        cover = { m -> GeneratedCover(title, colors, m) },
        // Its name is on its cover (the owner, 10 Nov); Play in the cover's colour.
        showTitle = false,
        coverColour = colors.first,
        barExtras = { RadioHeart(nav, title, colors) },
        header = {
            PicksRow("Song alchemy" + (songs.valueOrNull()?.let { " · ${it.size} songs" } ?: ""), onNewPicks = { round++ }) {
                RadioHeart(nav, title, colors, onBar = false)
            }
        },
    )
}

/**
 * A playlist made on the spot from what the listener typed about their mood. Its box moved to Dscvr (issue #339);
 * the page stays for Home shortcuts made to it before.
 */
@Composable
fun MoodScreen(nav: NavController, text: String) {
    val graph = LocalGraph.current
    var round by remember { mutableIntStateOf(0) }
    val result by loadState(text to round) {
        graph.connection.current.aiProblem(graph.settings.current)?.let { throw ApiException("AI backend is currently not available :( $it") }
        graph.generator.moodPlaylist(text)
    }
    val r = result.valueOrNull()
    val colors = tileColors(text)
    CollectionPage(
        nav = nav,
        title = r?.title ?: "Making your playlist…",
        color = lerp(colors.first, Color.Black, 0.25f),
        songs = r?.songs,
        error = (result as? Load.Err)?.message,
        saveAsPlaylist = true,
        shortcutCover = false,
        cover = { m -> GeneratedCover(r?.title ?: text, colors, m) },
        // Its name is on its cover, and Play takes the cover's colour, as every page with a drawn cover (the owner, 10 Nov).
        showTitle = false,
        coverColour = colors.first,
        header = {
            if (r != null) {
                Text(r.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Space.Small))
            }
            PicksRow("From “$text”" + (r?.let { " · ${it.songs.size} songs" } ?: ""), onNewPicks = { round++ })
        },
    )
}

/**
 * A radio's heart (the owner, 10 Nov): keeps the radio whose page this is among your favourites, in Your Library ›
 * Favourites, where a Home card can be made of it. Red when it's one; on its page an outline, in the bar a white one.
 */
@Composable
internal fun RadioHeart(nav: NavController, title: String, colors: Pair<Color, Color>, onBar: Boolean = true) {
    val graph = LocalGraph.current
    val route = remember(nav.currentBackStackEntry) { nav.currentBackStackEntry?.let { routeOf(it.destination.route, it.arguments) } } ?: return
    val all by graph.favouriteRadios.all.collectAsStateWithLifecycle()
    val on = all.any { it.route == route }
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    IconButton(
        onClick = {
            graph.favouriteRadios.toggle(route, title, colors.first.toArgb().toLong() and 0xFFFFFFFFL, colors.second.toArgb().toLong() and 0xFFFFFFFFL)
            // Hearted: it's in Your Library › Favourites, and a Home card can be made of it straight away.
            if (!on) scope.launch {
                val r = snack.showSnackbar("$title is in your favourites", actionLabel = "Make a Home card", duration = androidx.compose.material3.SnackbarDuration.Short)
                if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) nav.navigate("settings/cards/edit/radio:${android.net.Uri.encode(route)}")
            }
        },
        modifier = Modifier.testTag(if (onBar) "radioHeartBar" else "radioHeart"),
    ) {
        Icon(
            if (on) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (on) "Remove from favourite radios" else "Add to favourite radios",
            tint = if (on) FavouriteRed else if (onBar) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}
