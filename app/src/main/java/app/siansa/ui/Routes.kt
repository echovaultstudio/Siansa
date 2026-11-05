package app.siansa.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Every page of the app, by its route: the phone's NavHost (SiansaRoot) and the TV app's (issue #860) both hold them,
 * so a page that opens another works in either. The TV app puts its own pages in place of some ([except]): its Home,
 * Library, Search and Settings, and its album, artist, playlist and mix pages, built for the remote.
 */
fun NavGraphBuilder.siansaRoutes(nav: NavController, except: Set<String> = emptySet()) {
    fun page(route: String, arguments: List<NamedNavArgument> = emptyList(), content: @Composable (NavBackStackEntry) -> Unit) {
        if (route !in except) composable(route, arguments) { content(it) }
    }

    page("home") { HomeScreen(nav) }
    page("search") { SearchScreen(nav) }
    page(
        "search/more/{kind}?q={q}",
        arguments = listOf(androidx.navigation.navArgument("q") { defaultValue = "" }),
    ) { SearchMoreScreen(nav, it.arguments?.getString("kind"), it.arguments?.getString("q").orEmpty()) }
    page("library") { YourLibraryScreen(nav) }
    page("vibes") { VibesScreen(nav) }
    page("dscvr") { DscvrScreen(nav) }
    page("vibe/{id}") { VibeScreen(nav, it.arguments?.getString("id").orEmpty()) }
    page("settings/cards") { CardsSettingsScreen(nav) }
    page("settings/cards/edit/{id}") { CardEditorScreen(nav, it.arguments?.getString("id") ?: "new") }
    page("card/{id}") { CardScreen(nav, it.arguments?.getString("id").orEmpty()) }
    page("pinned/{id}") { PinnedCardScreen(nav, it.arguments?.getString("id").orEmpty()) }
    page("settings") { SettingsHub(nav) }
    page(
        "settings/connections?source={source}&extras={extras}",
        arguments = listOf(
            androidx.navigation.navArgument("source") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null },
            androidx.navigation.navArgument("extras") { defaultValue = "true" },
        ),
    ) {
        val source = it.arguments?.getString("source")
        // Setting up a server (the first run, or a user being added) is its own form; after that, one page
        // for every server and service (issue #665).
        if (source == null && LocalGraph.current.settings.current.navidromeReady) ConnectionsHub(nav)
        else ConnectionsScreen(nav, source, it.arguments?.getString("extras") != "false")
    }
    page("welcome") { SourcePickerScreen(nav) }
    page("welcome/extras/{source}") { SmartExtrasScreen(nav, it.arguments?.getString("source").orEmpty()) }
    page("settings/users") { ConnectionsHub(nav, open = "server") }
    page("settings/playback") { PlaybackSettingsScreen(nav) }
    page("settings/themes") { ThemesScreen(nav) }
    page("settings/style") { StyleScreen(nav) }
    page("settings/style/now-playing") { NowPlayingStyleScreen(nav) }
    page("settings/style/mini-player") { MiniPlayerStyleScreen(nav) }
    page("settings/style/nav-bar") { NavBarStyleScreen(nav) }
    page("settings/equaliser") { EqualiserScreen(nav) }
    page("settings/crossfade") { CrossfadeScreen(nav) }
    // Storage became Streaming and data (issue #780); the old address opens it.
    page("settings/storage") { StreamingDataScreen(nav) }
    page("settings/streaming") { StreamingDataScreen(nav) }
    page("settings/books") { BooksSettingsScreen(nav) }
    page("settings/home") { HomeSettingsScreen(nav) }
    page("settings/home/notices") { NoticesSettingsScreen(nav) }
    page("downloads") { DownloadsScreen(nav) }
    page("downloads/add") { DownloadPickerScreen(nav) }
    page("downloads/songs") { DownloadedSongsScreen(nav) }
    page("settings/help") { HelpScreen(nav) }
    page("settings/audiobookshelf") { ConnectionsHub(nav, open = "books") }
    page("settings/logs") { LogsScreen(nav) }
    page("settings/report") { ProblemReportScreen(nav) }
    page("settings/backup") { BackupScreen(nav) }
    page("settings/scrobbling") { ScrobblingScreen(nav) }
    page("settings/whatsnew") { WhatsNewScreen(nav) }
    page("settings/about") { AboutScreen(nav) }
    page("home/{section}") { HomeSectionScreen(nav, it.arguments?.getString("section").orEmpty()) }
    page("liked") { LikedScreen(nav) }
    page("radio/{kind}/{key}") {
        RadioScreen(nav, it.arguments?.getString("kind").orEmpty(), it.arguments?.getString("key").orEmpty())
    }
    page("blend/{id}") { BlendScreen(nav, it.arguments?.getString("id").orEmpty()) }
    page("mood/{text}") { MoodScreen(nav, it.arguments?.getString("text").orEmpty()) }
    page(
        "songpath/{id}?live={live}",
        arguments = listOf(androidx.navigation.navArgument("live") { defaultValue = "false" }),
    ) { SongPathPicker(nav, it.arguments?.getString("id").orEmpty(), it.arguments?.getString("live") == "true") }
    page(
        "songpath/{from}/{to}/{length}?lyrics={lyrics}&live={live}",
        arguments = listOf(
            androidx.navigation.navArgument("lyrics") { defaultValue = "false" },
            androidx.navigation.navArgument("live") { defaultValue = "false" },
        ),
    ) {
        val args = it.arguments
        SongPathScreen(
            nav,
            args?.getString("from").orEmpty(),
            args?.getString("to").orEmpty(),
            args?.getString("length")?.toIntOrNull() ?: 20,
            args?.getString("lyrics") == "true",
            args?.getString("live") == "true",
        )
    }
    page("sync") { SyncScreen(nav) }
    page("report/{range}") { ReportScreen(nav, it.arguments?.getString("range").orEmpty()) }
    page("discover/artists") { ArtistDiscoveryScreen(nav) }
    page("timemachine") { TimeMachineScreen(nav) }
    page("favourite-albums") { FavouriteAlbumsScreen(nav) }
    // A podcast added by its feed (issue #353); stations themselves are in Your Library.
    page("podcast/{id}") { PodcastScreen(nav, it.arguments?.getString("id").orEmpty()) }
    page("favourite-artists") { FavouriteArtistsScreen(nav) }
    page("fingerprint") { FingerprintScreen(nav) }
    page("song/{id}/details") { SongDetailsScreen(nav, it.arguments?.getString("id").orEmpty()) }
    page("album/{id}") { AlbumScreen(nav, it.arguments?.getString("id").orEmpty()) }
    // Every album with a genre or record label, from an album page's tags (#558).
    page(
        "albums/{tag}?value={value}",
        arguments = listOf(androidx.navigation.navArgument("value") { defaultValue = "" }),
    ) { AlbumsWithTagScreen(nav, it.arguments?.getString("tag"), it.arguments?.getString("value").orEmpty()) }
    page("similar/songs/{id}") { SimilarSongsScreen(nav, it.arguments?.getString("id").orEmpty()) }
    page("similar/albums/{id}") { SimilarAlbumsScreen(nav, it.arguments?.getString("id").orEmpty()) }
    page("artist/{id}") { ArtistScreen(nav, it.arguments?.getString("id").orEmpty()) }
    page("book/{id}") { BookScreen(nav, it.arguments?.getString("id").orEmpty()) }
    page("artist/{id}/appears-on") { AppearsOnScreen(nav, it.arguments?.getString("id").orEmpty()) }
    page("playlist/{id}") { PlaylistScreen(nav, it.arguments?.getString("id").orEmpty()) }
    page("mix/{id}") { MixScreen(nav, it.arguments?.getString("id").orEmpty()) }
    page("selector/{id}") { SelectorEditor(nav, it.arguments?.getString("id").orEmpty()) }
    page("carousel/{id}") { CarouselEditor(nav, it.arguments?.getString("id").orEmpty()) }
}
