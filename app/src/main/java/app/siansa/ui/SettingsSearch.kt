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
        SettingEntry("Music server", "Your server", "settings/connections", words = "navidrome emby jellyfin login address user password api key"),
        SettingEntry("Library sync", "Your server", "settings/connections", words = "refresh new music hours"),
        SettingEntry("Add a music server", "Your server", "settings/connections", words = "another login user"),
        SettingEntry("Siansa Beats", "Services", "settings/connections/beats", null, "beats loudness keys genres similar listening"),
        SettingEntry("Mix settings", "Mixes", "settings/beats/mixes", words = "beats mixes for now made for you on repeat rediscover new leave out genres artists songs per mix"),
        SettingEntry("AudioMuse-AI", "Services", "settings/connections/audiomuse", null, "sound similar search by words"),
        SettingEntry("Audiobookshelf", "Services", "settings/connections/books", null, "books podcasts"),
        SettingEntry("Last.fm", "Scrobbling", "settings/scrobbling", "last-fm", "scrobble listening"),
        SettingEntry("ListenBrainz / Koito", "Scrobbling", "settings/scrobbling", "listenbrainz", "scrobble listening"),
        SettingEntry("AI", "Services", "settings/connections/ai", null, "titles sound search openrouter openai anthropic gemini mistral groq ollama"),
        SettingEntry("Equaliser", "Playback", "settings/equaliser", words = "eq bands preset bass pre-amp"),
        SettingEntry("Levelling", "Playback", "settings/sound", "levelling", "loudness volume LUFS replaygain target track album"),
        SettingEntry("Where loudness and fades come from", "Playback", "settings/sound", words = "tags siansa beats plugin measured source"),
        SettingEntry("Crossfade and DJ blends", "Playback", "settings/sound", "crossfade-dj-blends", "fade blend smart fixed DJ bars"),
        SettingEntry("Radio wandering", "Mixes", "settings/radios", "radio-wandering", "song radio drift stay close wander"),
        SettingEntry("When the queue ends", "Mixes", "settings/radios", "when-the-queue-ends", "keep playing continuous steady DJ off"),
        SettingEntry("Wind down", "Mixes", "settings/radios", "wind-down", "calm sleep timer songs"),
        SettingEntry("Today's mix and vibes", "Mixes", "vibes", words = "daily auto update morning day evening night moment"),
        SettingEntry("Streaming quality", "Storage", "settings/streaming", "streaming-quality", "wi-fi mobile data opus original kbit bitrate"),
        SettingEntry("Load songs ahead", "Storage", "settings/streaming", "load-songs-ahead", "preload buffer next"),
        SettingEntry("Song cache", "Storage", "settings/streaming", "song-cache", "size clear offline kept"),
        SettingEntry("Downloads", "Storage", "settings/streaming", "downloads", "wi-fi only quality storage limit remove all offline"),
        SettingEntry("Covers on the phone", "Storage", "settings/streaming", "covers-on-the-phone", "image cache download all covers artist pictures clear"),
        SettingEntry("Library on the phone", "Storage", "settings/streaming", "other-data-on-this-phone", "database space"),
        SettingEntry("Book skips", "Playback", "settings/sound", "book-skips", "back forward seconds book player"),
        SettingEntry("Shortcuts", "Home", "settings/home", "top-of-home", "played last quick"),
        SettingEntry("Notices", "Home", "settings/home", "top-of-home", "chips pinned automatic new music carry on downloads"),
        SettingEntry("Your own notices", "Home", "settings/home", "top-of-home", "custom notice add a notice rules filter new releases"),
        SettingEntry("Home menu", "Home", "settings/home", "home-menu", "order roll the dice play on sources arrange"),
        SettingEntry("Arrange Home", "Home", "settings/home/arrange", words = "order rows show hide look title spacing cards colour"),
        SettingEntry("Home's mixes", "Mixes", "settings/radios", "homesMixes", "siansa beats this phone for now made for you more of you who makes"),
        SettingEntry("Genres by sound", "Discovery", "settings/discovery", words = "genres tags siansa beats"),
        SettingEntry("Similar music and search by words", "Discovery", "settings/discovery", words = "sound radio blends similar"),
        SettingEntry("Moods in Flow", "Discovery", "settings/discovery", words = "vocals voice female male feel"),
        SettingEntry("Scrobble to Siansa Beats", "Scrobbling", "settings/scrobbling", "siansa-beats", "history listens beats"),
        SettingEntry("Your cards", "Home", "settings/cards", words = "made for you your cards custom"),
        SettingEntry("Light or dark", "Theme", "settings/themes", words = "auto theme mode night"),
        SettingEntry("Pure black", "Theme", "settings/themes", words = "battery OLED amoled dark"),
        SettingEntry("Colour scheme", "Theme", "settings/themes", words = "colours accent your colour wallpaper pages and cards"),
        SettingEntry("Dynamic colours", "Theme", "settings/themes", "dynamic-colours", "cover colour play button seek bar hearts"),
        SettingEntry("Status bar", "Theme", "settings/themes", "status-bar", "phone's top bar clock battery see-through black hidden full screen"),
        SettingEntry("Animated covers", "Theme", "settings/themes", "animated-covers", "gif webp moving"),
        SettingEntry("Lyrics button", "Player", "settings/now-playing", "buttons", "device queue card classic"),
        SettingEntry("How the song is playing", "Player", "settings/now-playing", "about-this-song", "stream info quality transcoded converted opus original downloaded cache bitrate"),
        SettingEntry("Star ratings", "Player", "settings/now-playing", "star-ratings", "stars rate rating five under the title about this song"),
        SettingEntry("Play button slides", "Player", "settings/now-playing", "play-button", "swipe up down left right DJ mix steady radio song path sonic match record grooves"),
        SettingEntry("DJ mix bar", "Player", "settings/now-playing", "dj-mix-bar", "order places steady vibes build euphoric wind down"),
        SettingEntry("Mini player", "Player", "settings/now-playing", "mini-player", "buttons order play next add to playlist like"),
        SettingEntry("Bottom bar", "Navigation", "settings/bottom-bar", words = "tabs navigation nav bar order hide"),
        SettingEntry("When a song counts", "Scrobbling", "settings/scrobbling", "when-a-song-counts", "percent minutes scrobble"),
        SettingEntry("Your server", "Scrobbling", "settings/scrobbling", "your-server", "play count record plays"),
        SettingEntry("Last.fm scrobbling", "Scrobbling", "settings/scrobbling", "last-fm", "sign in"),
        SettingEntry("ListenBrainz / Koito listens", "Scrobbling", "settings/scrobbling", "listenbrainz", "token"),
        SettingEntry("Time Machine", "Scrobbling", "settings/scrobbling", "time-machine", "songs not in your library history"),
        SettingEntry("Save backup", "Backup", "settings/backup", words = "restore export import file"),
        SettingEntry("Automatic backup", "Backup", "settings/backup", words = "daily folder keep 7"),
        SettingEntry("Debug logs", "Help", "settings/help", words = "log detail trace debugging logs size save share clear"),
        SettingEntry("Report a problem", "Help", "settings/help", words = "bug issue log"),
        SettingEntry("Bad blend button", "Help", "settings/help", words = "DJ blend wrong report"),
        SettingEntry("What's new", "About", "settings/about", words = "version changes release"),
        SettingEntry("Open-source licences", "About", "settings/about", words = "privacy docs"),
    )

    /** The entries [query] finds, by name first, then by page or other words. Nothing for a blank query. */
    fun find(query: String): List<SettingEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val byName = entries.filter { it.name.lowercase().contains(q) }
        val byWords = entries.filter {
            it !in byName && (it.page.lowercase().contains(q) || FORMERLY[it.page]?.contains(q) == true || it.words.lowercase().contains(q))
        }
        return byName + byWords
    }

    /**
     * What the pages were called before they took one word each (the owner, 20 Nov), so the old names still find them:
     * "feedback" finds Help, "radios" Mixes.
     */
    val FORMERLY = mapOf(
        "Playback" to "sound & playback", "Mixes" to "mixes & radios", "Scrobbling" to "listening",
        "Storage" to "storage & downloads", "Backup" to "backup & restore", "Home" to "home page",
        "Player" to "now playing", "Navigation" to "bottom bar", "Help" to "help & feedback",
    )
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
