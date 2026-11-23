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
    // One page per service (the owner, 17 Nov): its card alone; "server" stays the servers page.
    page("settings/connections/{open}") {
        val open = it.arguments?.getString("open")
        ConnectionsHub(nav, open = open, only = open?.takeIf { o -> o in SERVICE_PAGES })
    }
    page("settings/beats/mixes") { BeatsMixSettingsScreen(nav) }
    // Settings regrouped (the owner, 13 Nov): Services, a search, and twelve pages. Old addresses still lead somewhere.
    page("settings/services") { ServicesScreen(nav) }
    page("settings/find") { SettingsSearchScreen(nav) }
    page("settings/sound") { CrossfadeScreen(nav) }
    page("settings/playback") { CrossfadeScreen(nav) }
    page("settings/crossfade") { CrossfadeScreen(nav) }
    page("settings/radios") { RadiosScreen(nav) }
    page("settings/themes") { ThemesScreen(nav) }
    page("settings/style") { ThemesScreen(nav) }
    page("settings/now-playing") { NowPlayingStyleScreen(nav) }
    page("settings/style/now-playing") { NowPlayingStyleScreen(nav) }
    page("settings/style/mini-player") { NowPlayingStyleScreen(nav) }
    page("settings/style/dj-bar") { NowPlayingStyleScreen(nav) }
    page("settings/bottom-bar") { NavBarStyleScreen(nav) }
    page("settings/style/nav-bar") { NavBarStyleScreen(nav) }
    page("settings/style/home") { HomeSettingsScreen(nav) }
    page("settings/style/home-menu") { HomeSettingsScreen(nav) }
    page("settings/equaliser") { EqualiserScreen(nav) }
    page("settings/storage") { StreamingDataScreen(nav) }
    page("settings/streaming") { StreamingDataScreen(nav) }
    // Book skips are in Playback now (the owner, 17 Nov).
    page("settings/books") { CrossfadeScreen(nav) }
    page("settings/discovery") { DiscoveryScreen(nav) }
    page("settings/home/arrange") { ArrangeHomePage(nav) }
    page("settings/home") { HomeSettingsScreen(nav) }
    page("settings/home/notices") { HomeSettingsScreen(nav) }
    page("downloads") { DownloadsScreen(nav) }
    page("downloads/add") { DownloadPickerScreen(nav) }
    page("downloads/songs") { DownloadedSongsScreen(nav) }
    page("settings/help") { HelpScreen(nav) }
    page("settings/audiobookshelf") { ConnectionsHub(nav, open = "books", only = "books") }
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
        "songpath/{from}/{to}/{length}?live={live}",
        arguments = listOf(
            androidx.navigation.navArgument("live") { defaultValue = "false" },
        ),
    ) {
        val args = it.arguments
        SongPathScreen(
            nav,
            args?.getString("from").orEmpty(),
            args?.getString("to").orEmpty(),
            args?.getString("length")?.toIntOrNull() ?: 20,
            args?.getString("live") == "true",
        )
    }
    page("sync") { SyncScreen(nav) }
    page("report/{range}") { ReportScreen(nav, it.arguments?.getString("range").orEmpty()) }
    page("discover/artists") { ArtistDiscoveryScreen(nav) }
    // A month or a year to open at (Home's Time Machine card, issue #1341): month 0 is the whole year; without them,
    // this month a year ago.
    page(
        "timemachine?year={year}&month={month}",
        arguments = listOf(
            androidx.navigation.navArgument("year") { type = androidx.navigation.NavType.IntType; defaultValue = 0 },
            androidx.navigation.navArgument("month") { type = androidx.navigation.NavType.IntType; defaultValue = -1 },
        ),
    ) {
        val year = it.arguments?.getInt("year")?.takeIf { y -> y > 0 }
        TimeMachineScreen(nav, year, it.arguments?.getInt("month")?.takeIf { m -> year != null && m >= 0 })
    }
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
