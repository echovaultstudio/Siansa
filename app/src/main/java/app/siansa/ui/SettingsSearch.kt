package app.siansa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation.NavController

/**
 * A setting the search can find (the owner, 13 Nov): its name, the page it's on (as the page is named), the page's
 * route, the folding section it's in (the section's tag, opened and lit when the result is tapped) and other words
 * that find it ("battery" for Pure black).
 */
data class SettingEntry(val name: String, val page: String, val route: String, val section: String? = null, val words: String = "")

/** Every setting, for the search. Names and pages as they're shown; words are what people say when they look for it. */
object SettingsIndex {
    val entries: List<SettingEntry> = listOf(
        SettingEntry("Music server", "Connections", "settings/connections", words = "navidrome emby jellyfin login address user password api key"),
        SettingEntry("Library sync", "Connections", "settings/connections", words = "refresh new music hours"),
        SettingEntry("Add a music server", "Connections", "settings/connections", words = "another login user"),
        SettingEntry("Siansa Beats", "Services", "settings/services", "service-beats", "beats loudness keys genres similar listening"),
        SettingEntry("AudioMuse-AI", "Services", "settings/services", "service-audiomuse", "sound similar search by words"),
        SettingEntry("Audiobookshelf", "Services", "settings/services", "service-books", "books podcasts"),
        SettingEntry("Last.fm", "Services", "settings/services", "service-lastfm", "scrobble listening"),
        SettingEntry("ListenBrainz / Koito", "Services", "settings/services", "service-listenbrainz", "scrobble listening"),
        SettingEntry("AI", "Services", "settings/services", "service-ai", "titles sound search openrouter openai anthropic gemini mistral groq ollama"),
        SettingEntry("Equaliser", "Sound", "settings/equaliser", words = "eq bands preset bass pre-amp"),
        SettingEntry("Levelling", "Sound", "settings/sound", "levelling", "loudness volume LUFS replaygain target track album"),
        SettingEntry("Where loudness and fades come from", "Sound", "settings/sound", words = "tags siansa beats plugin measured source"),
        SettingEntry("Crossfade", "Sound", "settings/sound", "crossfade", "fade blend smart fixed DJ bars"),
        SettingEntry("Radio wandering", "Radios & mixes", "settings/radios", "radio-wandering", "song radio drift stay close wander"),
        SettingEntry("When the queue ends", "Radios & mixes", "settings/radios", "when-the-queue-ends", "keep playing continuous steady DJ off"),
        SettingEntry("Wind down", "Radios & mixes", "settings/radios", "wind-down", "calm sleep timer songs"),
        SettingEntry("Today's mix and vibes", "Radios & mixes", "vibes", words = "daily auto update morning day evening night moment"),
        SettingEntry("Streaming quality", "Streaming & storage", "settings/streaming", "streaming-quality", "wi-fi mobile data opus original kbit bitrate"),
        SettingEntry("Load songs ahead", "Streaming & storage", "settings/streaming", "load-songs-ahead", "preload buffer next"),
        SettingEntry("Song cache", "Streaming & storage", "settings/streaming", "song-cache", "size clear offline kept"),
        SettingEntry("Downloads", "Streaming & storage", "settings/streaming", "downloads", "wi-fi only quality storage limit remove all offline"),
        SettingEntry("Covers on the phone", "Streaming & storage", "settings/streaming", "covers-on-the-phone", "image cache download all covers artist pictures clear"),
        SettingEntry("Other data on this phone", "Streaming & storage", "settings/streaming", "other-data-on-this-phone", "database logs space"),
        SettingEntry("Skips", "Audiobooks & podcasts", "settings/books", "skips", "back forward seconds book player"),
        SettingEntry("Shortcuts", "Home page", "settings/home", "top-of-home", "played last quick"),
        SettingEntry("Notices", "Home page", "settings/home", "top-of-home", "chips pinned automatic new music carry on downloads"),
        SettingEntry("Home menu", "Home page", "settings/home", "home-menu", "order roll the dice play on sources arrange"),
        SettingEntry("Cards", "Home page", "settings/cards", words = "made for you your cards custom"),
        SettingEntry("Light or dark", "Theme", "settings/themes", words = "auto theme mode night"),
        SettingEntry("Pure black", "Theme", "settings/themes", words = "battery OLED amoled dark"),
        SettingEntry("Colour scheme", "Theme", "settings/themes", words = "colours accent your colour wallpaper pages and cards"),
        SettingEntry("Dynamic colours", "Theme", "settings/themes", "dynamic-colours", "cover colour play button seek bar hearts"),
        SettingEntry("Status bar", "Theme", "settings/themes", "status-bar", "phone's top bar clock battery see-through black hidden full screen"),
        SettingEntry("Animated covers", "Theme", "settings/themes", "animated-covers", "gif webp moving"),
        SettingEntry("Lyrics button", "Now playing", "settings/now-playing", "buttons", "device queue card classic"),
        SettingEntry("Play button slides", "Now playing", "settings/now-playing", "play-button", "swipe up down left right DJ mix steady radio song path sonic match record grooves"),
        SettingEntry("DJ mix bar", "Now playing", "settings/now-playing", "dj-mix-bar", "order places steady vibes build euphoric wind down"),
        SettingEntry("Mini player", "Now playing", "settings/now-playing", "mini-player", "buttons order play next add to playlist like"),
        SettingEntry("Bottom bar", "Bottom bar", "settings/bottom-bar", words = "tabs navigation nav bar order hide"),
        SettingEntry("When a song counts", "Scrobbling & history", "settings/scrobbling", "when-a-song-counts", "percent minutes scrobble"),
        SettingEntry("Your server", "Scrobbling & history", "settings/scrobbling", "your-server", "play count record plays"),
        SettingEntry("Last.fm scrobbling", "Scrobbling & history", "settings/scrobbling", "last-fm", "sign in"),
        SettingEntry("ListenBrainz / Koito listens", "Scrobbling & history", "settings/scrobbling", "listenbrainz", "token"),
        SettingEntry("Time Machine", "Scrobbling & history", "settings/scrobbling", "time-machine", "songs not in your library history"),
        SettingEntry("Save backup", "Backup & restore", "settings/backup", words = "restore export import file"),
        SettingEntry("Automatic backup", "Backup & restore", "settings/backup", words = "daily folder keep 7"),
        SettingEntry("Debug logs", "Help & feedback", "settings/help", words = "log detail trace"),
        SettingEntry("Report a problem", "Help & feedback", "settings/help", words = "bug issue log"),
        SettingEntry("Bad blend button", "Help & feedback", "settings/help", words = "DJ blend wrong report"),
        SettingEntry("What's new", "About", "settings/about", words = "version changes release"),
        SettingEntry("Open-source licences", "About", "settings/about", words = "privacy docs"),
    )

    /** The entries [query] finds, by name first, then by page or other words. Nothing for a blank query. */
    fun find(query: String): List<SettingEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val byName = entries.filter { it.name.lowercase().contains(q) }
        val byWords = entries.filter { it !in byName && (it.page.lowercase().contains(q) || it.words.lowercase().contains(q)) }
        return byName + byWords
    }
}

/**
 * Which folding section a search result asks for: the page opens with it open and lit for a moment
 * ([FoldSection] takes it).
 */
object SettingsFocus {
    @Volatile private var pending: String? = null

    fun ask(section: String?) { pending = section }

    /** True once, for the section with [tag]. */
    fun take(tag: String): Boolean {
        if (pending != tag) return false
        pending = null
        return true
    }
}

/** Settings › Find a setting (the owner, 13 Nov): type, and tap a result to land on its page with its section open. */
@Composable
fun SettingsSearchScreen(nav: NavController) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val hits = remember(query) { SettingsIndex.find(query) }
    Screen(title = "Find a setting", nav = nav) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().padding(horizontal = Space.Page)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Find a setting") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("findSetting"),
            )
            Hint(
                when {
                    query.isBlank() -> "Matches a setting's name, its page and the words people use for it: \"battery\" finds Pure black, \"loud\" finds Levelling."
                    hits.isEmpty() -> "Nothing called that. Try another word."
                    else -> "${hits.size} ${if (hits.size == 1) "setting" else "settings"} · tap one to go straight there"
                },
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.Small)) {
                items(hits, key = { it.name + it.route }) { entry ->
                    Column(
                        Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${entry.name}") {
                            SettingsFocus.ask(entry.section)
                            nav.navigate(entry.route)
                        }.padding(vertical = Space.Medium).testTag("find-result"),
                        verticalArrangement = Arrangement.spacedBy(Space.Tight),
                    ) {
                        Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                        Text(entry.page, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
