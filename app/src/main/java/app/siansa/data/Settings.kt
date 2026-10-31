package app.siansa.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import app.siansa.playback.BeatMatchMode
import app.siansa.playback.CrossfadeMode
import app.siansa.playback.LoudnessSource
import app.siansa.mix.ContinuousMode
import app.siansa.mix.MixShape
import app.siansa.mix.RadioDrift
import app.siansa.situation.SituationOptions
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLDecoder
import app.siansa.net.SourceKind

enum class ReplayGainMode(val label: String) { TRACK("Track"), ALBUM("Album"), OFF("Off") }

/** The phone's top bar, with the clock and battery (issue #681, Settings › Style). */
enum class TopBarStyle(val label: String, val hint: String) {
    CLEAR("See-through", "The app shows behind the phone's top bar."),
    BLACK("Black", "A black top bar, so nothing shows behind the clock and icons."),
    HIDDEN("Hidden", "Full screen: swipe down from the top to see the bar for a moment.");

    companion object {
        fun parse(value: String?): TopBarStyle = entries.firstOrNull { it.name == value } ?: CLEAR
    }
}

/** "Your colour" starts as a teal until someone picks their own (issue #148). */
const val DEFAULT_THEME_SEED: Int = 0xFF0E9AA7.toInt()

/** A fresh install's theme (issue #728): Mono colours with the Amber accent, to match the black icon (#726). */
const val DEFAULT_THEME_COLOURS = "MONO"
const val DEFAULT_THEME_SELECTION = "AMBER"

/**
 * The accent when none was saved (issue #728): Amber on a fresh install, where no colours were saved either. Someone who
 * already picked their colours ([savedColours]) keeps "Match theme", the accent they've had until now.
 */
internal fun defaultSelectionFor(savedColours: String?): String = if (savedColours == null) DEFAULT_THEME_SELECTION else "THEME"

data class AppSettings(
    /** Where the music comes from (issue #113); the server fields below are its address and login. */
    val sourceKind: SourceKind = SourceKind.OPENSUBSONIC,
    val navidromeUrl: String = "",
    val navidromeUser: String = "",
    val navidromePassword: String = "",
    val proxyUser: String = "",
    val proxyPassword: String = "",
    val audioMuseUrl: String = "",
    val audioMuseToken: String = "",
    val audioMuseUser: String = "",
    val audioMusePassword: String = "",
    /**
     * Which of AudioMuse's media servers is this one (AudioMuse 3 can serve several: Navidrome, Emby…), by the name
     * it has in AudioMuse; empty for AudioMuse's default server.
     */
    val audioMuseServer: String = "",
    /** The AI service that writes mix titles and sound searches, its key and model (issue #665: was OpenRouter only). */
    val aiProvider: AiProvider = AiProvider.OPENROUTER,
    val aiKey: String = "",
    val aiModel: String = DEFAULT_MODEL,
    /** The address of an AI service on your network or another one ([AiProvider.ownAddress]). */
    val aiAddress: String = "",
    /** The Audiobookshelf server (issue #521): one login for the whole app, like AudioMuse. Empty: no books. */
    val audiobookshelfUrl: String = "",
    val audiobookshelfToken: String = "",
    val audiobookshelfUser: String = "",
    val audiobookshelfPassword: String = "",
    /** Skipping in the book player, in seconds (issue #521): back 10, forward 30 by default; 10 to 90. */
    val bookSkipBackSec: Int = 10,
    val bookSkipForwardSec: Int = 30,
    val aiTitles: Boolean = true,
    val replayGain: ReplayGainMode = ReplayGainMode.TRACK,
    val preampDb: Float = 0f,
    val syncFolders: Set<String> = emptySet(),
    /** Image cache limit in MB; 0 = no limit. */
    val imageCacheMb: Int = 500,
    val animatedCovers: Boolean = true,
    /** Download every album, artist and playlist cover in the background. */
    val coverDownload: Boolean = false,
    /** ...on any Wi-Fi, rather than only on the same local network as Navidrome. */
    val coverDownloadAnyWifi: Boolean = false,
    /** Covers only on Wi-Fi (never over a VPN or mobile data); off, on any connection (issue #299). */
    val coverDownloadWifiOnly: Boolean = true,
    val logging: Boolean = false,
    /** What of the moment the vibes may use for today's mix (Vibes panel, issue #247). */
    val situation: SituationOptions = SituationOptions(),
    /** Parts of the app whose detailed log entries are left out (Settings › Logs › Detail, issue #237). */
    val scrobbleNavidrome: Boolean = true,
    /** How much of a song must be heard, in percent, before it's scrobbled (issue #270). */
    val scrobblePercent: Int = 50,
    /**
     * Users on this phone whose songs aren't scrobbled (issue #356): playing music from their library, shown with
     * yours, sends nothing to Navidrome, Last.fm or ListenBrainz, as Symfonium's "scrobble as advertisement" does.
     */
    val scrobbleOffUsers: Set<Int> = emptySet(),
    val lastFmEnabled: Boolean = false,
    val lastFmApiKey: String = "",
    val lastFmSecret: String = "",
    val lastFmUser: String = "",
    val lastFmSession: String = "",
    val listenBrainzEnabled: Boolean = false,
    val listenBrainzUrl: String = "",
    val listenBrainzToken: String = "",
    val transcodeWifiKbps: Int = 0,
    val transcodeMobileKbps: Int = 0,
    val songCacheMb: Int = 2048,
    /** Upcoming songs loaded ahead on Wi-Fi and on mobile data (issue #254, see [app.siansa.playback.Preload]). */
    val preloadWifi: Int = 2,
    val preloadMobile: Int = 1,
    /** Downloads for offline listening wait for Wi-Fi. */
    val downloadWifiOnly: Boolean = true,
    /** Most space downloads may use, in MB; 0 = no limit. */
    val downloadLimitMb: Int = 0,
    /** Opus kbit/s for downloads, or 0 for the original file. */
    val downloadKbps: Int = 0,
    val homeOrder: List<HomeSection> = HomeSection.default,
    /** Settings › Style (issue #346): the mini player's buttons, the bottom bar's tabs, and now playing's lyrics button. */
    val miniButtons: List<MiniButton> = MiniButton.default,
    val navTabs: List<NavTab> = NavTab.default,
    /** On: now playing's lyrics, device and queue buttons (issue #294). Off: the earlier look, lyrics card peeking in. */
    val nowPlayingLyricsButton: Boolean = true,
    val topBar: TopBarStyle = TopBarStyle.CLEAR,
    val homeHidden: Set<HomeSection> = emptySet(),
    /** Home's rows in another look than their own (issue #655); a row not in it looks as it is. */
    val homeLooks: Map<HomeSection, HomeLook> = emptyMap(),
    /** The gap between Home's sections (#554), as a step of the spacing scale: 0 tight … 4 airy. 2 is the default. */
    val homeGap: Int = HOME_GAP_DEFAULT,
    /** Show only music on the phone, across the whole app (issue #141). */
    val downloadedOnly: Boolean = false,
    /** The time machine lists songs that aren't in your library, marked (issue #234). */
    val timeMachineMissing: Boolean = true,
    /** Theme (issue #148): "AUTO", "LIGHT" or "DARK"; colours by name ("MONO", "OCEAN"…); pure black when dark. */
    val themeMode: String = "AUTO",
    val themeColours: String = DEFAULT_THEME_COLOURS,
    val pureBlack: Boolean = false,
    /** The seed for "Your colour" (issue #148, step 2), as ARGB. */
    val themeSeed: Int = DEFAULT_THEME_SEED,
    /** The accent colour, by name ("AMBER", "THEME"…), issues #313, #728. */
    val themeSelection: String = DEFAULT_THEME_SELECTION,
    /** Where song loudness and fade shapes come from (issue #60): ReplayGain tags, or the Siansa plugin. */
    val loudnessSource: LoudnessSource = LoudnessSource.TAGS,
    /** The loudness, in LUFS, that measured levelling brings songs towards. */
    val loudnessTarget: Int = DEFAULT_LOUDNESS_TARGET,
    val crossfade: CrossfadeMode = CrossfadeMode.OFF,
    /** How far a song radio may wander from its first song (issue #256). */
    val radioDrift: RadioDrift = RadioDrift.SOME,
    /** What plays when the queue is about to end (issue #337): nothing, songs like the last one, or a DJ-ordered set. */
    val continuousPlay: ContinuousMode = ContinuousMode.OFF,
    /** How many calm songs the wind down queues when it's switched on without a sleep timer (issue #330). */
    val windDownSongs: Int = DEFAULT_WIND_DOWN_SONGS,
    /** The crossfade length; in smart mode, the longest a fade may be. */
    val crossfadeSeconds: Int = DEFAULT_CROSSFADE_SECONDS,
    /** Where smart crossfades are beat-matched like a DJ's (issue #646, experimental): off, DJ radio, everywhere. */
    val beatMatch: BeatMatchMode = BeatMatchMode.DJ_RADIO,
    /** The shape of a DJ mix (issue #649): what the energy does over the next songs. */
    val mixShape: MixShape = MixShape.STEADY,
    /** How far a DJ mix may stray from the last song, 0 (close neighbours only) to 100 (adventurous). */
    val mixSurprise: Int = DEFAULT_MIX_SURPRISE,
) {
    val lastFmReady: Boolean get() = lastFmEnabled && lastFmApiKey.isNotBlank() && lastFmSecret.isNotBlank() && lastFmSession.isNotBlank()
    val listenBrainzReady: Boolean get() = listenBrainzEnabled && listenBrainzToken.isNotBlank()

    /**
     * The login AudioMuse uses to find whose play counts make "Your sound": the Navidrome one. With Emby (issue
     * #113) none is sent, and AudioMuse reads the plays of the Emby user it's set up with.
     */
    val fingerprintUser: String get() = if (sourceKind == SourceKind.OPENSUBSONIC) navidromeUser else ""
    val fingerprintPassword: String get() = if (sourceKind == SourceKind.OPENSUBSONIC) navidromePassword else ""

    val navidromeReady: Boolean
        get() = navidromeUrl.isNotBlank() && navidromeUser.isNotBlank() && navidromePassword.isNotBlank()
    val audioMuseReady: Boolean
        get() = audioMuseUrl.isNotBlank()
    val aiReady: Boolean
        get() = aiTitles && aiModel.isNotBlank() && (aiKey.isNotBlank() || !aiProvider.needsKey) &&
            aiCompletionsUrl(aiProvider, aiAddress) != null
    /**
     * These settings with the Connections page's fields taken from [page]: the music server, its proxy login,
     * AudioMuse, the AI service and Audiobookshelf. Everything else keeps its value here.
     */
    fun withConnectionsFrom(page: AppSettings, kind: SourceKind = sourceKind): AppSettings = copy(
        sourceKind = kind,
        navidromeUrl = page.navidromeUrl, navidromeUser = page.navidromeUser, navidromePassword = page.navidromePassword,
        proxyUser = page.proxyUser, proxyPassword = page.proxyPassword,
        audioMuseUrl = page.audioMuseUrl, audioMuseToken = page.audioMuseToken,
        audioMuseUser = page.audioMuseUser, audioMusePassword = page.audioMusePassword, audioMuseServer = page.audioMuseServer,
        aiProvider = page.aiProvider, aiKey = page.aiKey, aiModel = page.aiModel, aiAddress = page.aiAddress, aiTitles = page.aiTitles,
        audiobookshelfUrl = page.audiobookshelfUrl, audiobookshelfToken = page.audiobookshelfToken,
        audiobookshelfUser = page.audiobookshelfUser, audiobookshelfPassword = page.audiobookshelfPassword,
    )

    /**
     * These settings with the music server's fields taken from [page] (another user's card on the Connections page,
     * issue #687): its address, login, proxy login and name in AudioMuse. Everything else, AudioMuse and AI included,
     * keeps its value here.
     */
    fun withServerFrom(page: AppSettings): AppSettings = copy(
        navidromeUrl = page.navidromeUrl, navidromeUser = page.navidromeUser, navidromePassword = page.navidromePassword,
        proxyUser = page.proxyUser, proxyPassword = page.proxyPassword, audioMuseServer = page.audioMuseServer,
    )

    /** Whether [other] has a different music server or login from these settings. */
    fun serverDiffers(other: AppSettings): Boolean = withServerFrom(other) != this

    /** Books show once an Audiobookshelf server with a login is set (issue #521). */
    val booksReady: Boolean
        get() = audiobookshelfUrl.isNotBlank() && (audiobookshelfToken.isNotBlank() || (audiobookshelfUser.isNotBlank() && audiobookshelfPassword.isNotBlank()))

    companion object {
        const val HOME_GAP_DEFAULT = 2
        const val HOME_GAP_MAX = 4

        /** The skips the book player offers, in seconds (issue #521). */
        val BOOK_SKIP_CHOICES = listOf(10, 15, 30, 45, 60, 90)
        const val DEFAULT_MODEL = "google/gemini-2.5-flash"
        /** Close to what ReplayGain tags aim for, so switching where loudness comes from doesn't change the overall level. */
        const val DEFAULT_LOUDNESS_TARGET = -18
        const val DEFAULT_CROSSFADE_SECONDS = 6
        /** A DJ mix strays a little by default (issue #649). */
        const val DEFAULT_MIX_SURPRISE = 20
        val CROSSFADE_SECONDS = 1..12
        const val DEFAULT_WIND_DOWN_SONGS = 20
        val WIND_DOWN_CHOICES = listOf(10, 20, 30)
    }
}

/** A server address split into a clean base URL and any login written into it (user:pass@host). */
data class ServerAddress(val url: String, val user: String?, val password: String?)

class AddressException(message: String) : Exception(message)

/**
 * Accepts what people paste: no scheme, a login in the address, a trailing slash,
 * or Navidrome's web address (…/app/#/album/…). Throws [AddressException] with a readable reason.
 */
fun parseAddress(raw: String): ServerAddress {
    var text = raw.trim()
    if (text.isEmpty()) return ServerAddress("", null, null)
    if (!text.startsWith("http://", true) && !text.startsWith("https://", true)) text = "http://$text"
    val schemeEnd = text.indexOf("://") + 3
    val scheme = text.substring(0, schemeEnd).lowercase()
    var rest = text.substring(schemeEnd).substringBefore('#').substringBefore('?')

    // A login sits before the last '@' of the host part, so passwords may contain ':' '/' or '@'.
    var user: String? = null
    var password: String? = null
    val at = rest.lastIndexOf('@')
    if (at >= 0) {
        val credentials = rest.substring(0, at)
        rest = rest.substring(at + 1)
        val colon = credentials.indexOf(':')
        user = decode(if (colon >= 0) credentials.substring(0, colon) else credentials)
        password = if (colon >= 0) decode(credentials.substring(colon + 1)) else ""
    }

    var path = rest.substringAfter('/', "").trimEnd('/')
    val host = rest.substringBefore('/')
    if (path == "app" || path.endsWith("/app")) path = path.removeSuffix("app").trimEnd('/')
    val url = scheme + host + if (path.isNotEmpty()) "/$path" else ""

    val parsed = url.toHttpUrlOrNull()
        ?: throw AddressException(
            "\"$host\" isn't a valid server address. Use the form 192.168.1.10:4533, with the port as a number."
        )
    if (parsed.host.isBlank()) throw AddressException("The address is missing a server name or IP")
    return ServerAddress(url, user?.takeIf { it.isNotEmpty() }, password)
}

private fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

/** The store for AudioMuse, the AI service and Audiobookshelf, shared by every user and server (issue #416). */
private const val CONNECTIONS = "connections"
private const val SEEDED = "seeded"
private val CONNECTION_STRINGS = listOf("am_url", "am_token", "am_user", "am_pass", "am_server", "or_key", "or_model", "abs_url", "abs_token", "abs_user", "abs_pass")

/** One user's settings: by default the user in use, or another user's (to show their music too). */
class SettingsRepository(context: Context, user: Int = Users.active) {
    private val prefs = context.getSharedPreferences(UserFiles.scoped("settings", user), Context.MODE_PRIVATE)

    /**
     * AudioMuse and the AI service are the same for every user and every music server (issue #416): they live in one
     * store, whatever Navidrome or Emby account is in use. The first time, they're taken from this user's own settings.
     */
    private val shared = context.getSharedPreferences(CONNECTIONS, Context.MODE_PRIVATE).also { store ->
        if (!store.contains(SEEDED) && (prefs.getString("am_url", "").orEmpty().isNotBlank() || prefs.getString("or_key", "").orEmpty().isNotBlank())) {
            val edit = store.edit().putBoolean(SEEDED, true)
            for (key in CONNECTION_STRINGS) prefs.getString(key, null)?.let { edit.putString(key, it) }
            edit.apply()
        }
    }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    private fun secret(key: String) = SecretBox.decrypt(prefs.getString(key, "") ?: "")

    private fun load(): AppSettings = AppSettings(
        sourceKind = SourceKind.parse(prefs.getString("source_kind", null)),
        navidromeUrl = prefs.getString("nd_url", "") ?: "",
        navidromeUser = prefs.getString("nd_user", "") ?: "",
        navidromePassword = secret("nd_pass"),
        proxyUser = prefs.getString("proxy_user", "") ?: "",
        proxyPassword = secret("proxy_pass"),
        audioMuseUrl = shared.getString("am_url", "") ?: "",
        audioMuseToken = SecretBox.decrypt(shared.getString("am_token", "") ?: ""),
        audioMuseUser = shared.getString("am_user", "") ?: "",
        audioMusePassword = SecretBox.decrypt(shared.getString("am_pass", "") ?: ""),
        // Which of AudioMuse's servers is this user's (beta report 810dc85c): per user, as a Navidrome user and an
        // Emby user are different servers there. The shared value from before is the fallback.
        audioMuseServer = prefs.getString("am_server", null) ?: shared.getString("am_server", "") ?: "",
        aiKey = SecretBox.decrypt(shared.getString("or_key", "") ?: ""),
        aiModel = shared.getString("or_model", AppSettings.DEFAULT_MODEL) ?: AppSettings.DEFAULT_MODEL,
        aiProvider = AiProvider.parse(shared.getString("ai_provider", null)),
        aiAddress = shared.getString("ai_address", "") ?: "",
        audiobookshelfUrl = shared.getString("abs_url", "") ?: "",
        audiobookshelfToken = SecretBox.decrypt(shared.getString("abs_token", "") ?: ""),
        audiobookshelfUser = shared.getString("abs_user", "") ?: "",
        audiobookshelfPassword = SecretBox.decrypt(shared.getString("abs_pass", "") ?: ""),
        bookSkipBackSec = prefs.getInt("book_skip_back", 10),
        bookSkipForwardSec = prefs.getInt("book_skip_fwd", 30),
        aiTitles = prefs.getBoolean("ai_titles", true),
        replayGain = runCatching { ReplayGainMode.valueOf(prefs.getString("rg", "TRACK") ?: "TRACK") }
            .getOrDefault(ReplayGainMode.TRACK),
        preampDb = prefs.getFloat("preamp", 0f),
        syncFolders = prefs.getStringSet("sync_folders", emptySet())?.toSet() ?: emptySet(),
        imageCacheMb = prefs.getInt("image_cache_mb", 500),
        animatedCovers = prefs.getBoolean("animated_covers", true),
        coverDownload = prefs.getBoolean("cover_download", false),
        coverDownloadAnyWifi = prefs.getBoolean("cover_download_any_wifi", false),
        coverDownloadWifiOnly = prefs.getBoolean("cover_download_wifi_only", true),
        logging = prefs.getBoolean("logging", false),
        situation = SituationOptions(
            weather = prefs.getBoolean("situation_weather", false),
            movement = prefs.getBoolean("situation_movement", false),
            light = prefs.getBoolean("situation_light", false),
            charging = prefs.getBoolean("situation_charging", false),
            device = prefs.getBoolean("situation_device", true),
        ),
        scrobbleNavidrome = prefs.getBoolean("scrobble_nd", true),
        scrobblePercent = prefs.getInt("scrobble_pct", 50),
        scrobbleOffUsers = prefs.getString("scrobble_off_users", null).orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet(),
        lastFmEnabled = prefs.getBoolean("lfm_on", false),
        lastFmApiKey = prefs.getString("lfm_key", "") ?: "",
        lastFmSecret = secret("lfm_secret"),
        lastFmUser = prefs.getString("lfm_user", "") ?: "",
        lastFmSession = secret("lfm_session"),
        listenBrainzEnabled = prefs.getBoolean("lb_on", false),
        listenBrainzUrl = prefs.getString("lb_url", "") ?: "",
        listenBrainzToken = secret("lb_token"),
        transcodeWifiKbps = prefs.getInt("tc_wifi", 0),
        transcodeMobileKbps = prefs.getInt("tc_mobile", 0),
        songCacheMb = prefs.getInt("song_cache_mb", 2048),
        preloadWifi = prefs.getInt("preload_wifi", 2),
        preloadMobile = prefs.getInt("preload_mobile", 1),
        downloadWifiOnly = prefs.getBoolean("download_wifi_only", true),
        downloadLimitMb = prefs.getInt("download_limit_mb", 0),
        downloadKbps = prefs.getInt("download_kbps", 0),
        homeOrder = HomeSection.parse(prefs.getString("home_order", null)),
        miniButtons = MiniButton.parse(prefs.getString("mini_buttons", null)),
        navTabs = NavTab.parse(prefs.getString("nav_tabs", null)),
        nowPlayingLyricsButton = prefs.getBoolean("now_playing_lyrics_button", true),
        topBar = TopBarStyle.parse(prefs.getString("top_bar", null)),
        homeHidden = HomeSection.parseHidden(prefs.getString("home_hidden", null)),
        homeLooks = HomeLook.parse(prefs.getString("home_looks", null)),
        homeGap = prefs.getInt("home_gap", AppSettings.HOME_GAP_DEFAULT).coerceIn(0, AppSettings.HOME_GAP_MAX),
        downloadedOnly = prefs.getBoolean("downloaded_only", false),
        timeMachineMissing = prefs.getBoolean("time_machine_missing", true),
        themeMode = prefs.getString("theme_mode", "AUTO") ?: "AUTO",
        themeColours = prefs.getString("theme_colours", null) ?: DEFAULT_THEME_COLOURS,
        pureBlack = prefs.getBoolean("pure_black", false),
        themeSeed = prefs.getInt("theme_seed", DEFAULT_THEME_SEED),
        themeSelection = prefs.getString("theme_selection", null) ?: defaultSelectionFor(prefs.getString("theme_colours", null)),
        loudnessSource = LoudnessSource.parse(prefs.getString("loudness_source", null)),
        loudnessTarget = prefs.getInt("loudness_target", AppSettings.DEFAULT_LOUDNESS_TARGET),
        crossfade = CrossfadeMode.parse(prefs.getString("crossfade", null)),
        radioDrift = RadioDrift.parse(prefs.getString("radio_drift", null)),
        continuousPlay = ContinuousMode.parse(prefs.getString("continuous_play", null)),
        windDownSongs = prefs.getInt("wind_down_songs", AppSettings.DEFAULT_WIND_DOWN_SONGS)
            .takeIf { it in AppSettings.WIND_DOWN_CHOICES } ?: AppSettings.DEFAULT_WIND_DOWN_SONGS,
        crossfadeSeconds = prefs.getInt("crossfade_s", AppSettings.DEFAULT_CROSSFADE_SECONDS).coerceIn(AppSettings.CROSSFADE_SECONDS),
        beatMatch = BeatMatchMode.parse(prefs.getString("beat_match", null)),
        mixShape = MixShape.parse(prefs.getString("mix_shape", null)),
        mixSurprise = prefs.getInt("mix_surprise", AppSettings.DEFAULT_MIX_SURPRISE).coerceIn(0, 100),
    )

    /**
     * Cleans addresses and moves a login typed into an address into its own fields:
     * Navidrome's goes to the proxy login, AudioMuse's to the AudioMuse login.
     * Throws [AddressException] when an address can't be used.
     */
    fun clean(input: AppSettings): AppSettings {
        val nd = parseAddress(input.navidromeUrl)
        val am = parseAddress(input.audioMuseUrl)
        return input.copy(
            navidromeUrl = nd.url,
            navidromeUser = input.navidromeUser.trim(),
            proxyUser = nd.user ?: input.proxyUser.trim(),
            proxyPassword = nd.password ?: input.proxyPassword,
            audioMuseUrl = am.url,
            audioMuseToken = input.audioMuseToken.trim(),
            audioMuseUser = am.user ?: input.audioMuseUser.trim(),
            audioMusePassword = am.password ?: input.audioMusePassword,
            audioMuseServer = input.audioMuseServer.trim(),
            aiKey = input.aiKey.trim(),
            aiModel = input.aiModel.trim(),
            aiAddress = input.aiAddress.trim(),
            audiobookshelfUrl = parseAddress(input.audiobookshelfUrl).url,
            audiobookshelfToken = input.audiobookshelfToken.trim(),
            audiobookshelfUser = input.audiobookshelfUser.trim(),
            bookSkipBackSec = input.bookSkipBackSec.coerceIn(10, 90),
            bookSkipForwardSec = input.bookSkipForwardSec.coerceIn(10, 90),
        )
    }

    fun save(input: AppSettings) {
        val s = clean(input)
        prefs.edit()
            .putString("source_kind", s.sourceKind.name)
            .putString("nd_url", s.navidromeUrl)
            .putString("nd_user", s.navidromeUser)
            .putString("nd_pass", SecretBox.encrypt(s.navidromePassword))
            .putString("proxy_user", s.proxyUser)
            .putString("proxy_pass", SecretBox.encrypt(s.proxyPassword))
            .putBoolean("ai_titles", s.aiTitles)
            .putString("rg", s.replayGain.name)
            .putFloat("preamp", s.preampDb)
            .putStringSet("sync_folders", s.syncFolders)
            .putInt("image_cache_mb", s.imageCacheMb)
            .putBoolean("logging", s.logging)
            .putBoolean("situation_weather", s.situation.weather)
            .putBoolean("situation_movement", s.situation.movement)
            .putBoolean("situation_light", s.situation.light)
            .putBoolean("situation_charging", s.situation.charging)
            .putBoolean("situation_device", s.situation.device)
            .putBoolean("scrobble_nd", s.scrobbleNavidrome)
            .putInt("scrobble_pct", s.scrobblePercent)
            .putString("scrobble_off_users", s.scrobbleOffUsers.sorted().joinToString(","))
            .putBoolean("lfm_on", s.lastFmEnabled)
            .putString("lfm_key", s.lastFmApiKey.trim())
            .putString("lfm_secret", SecretBox.encrypt(s.lastFmSecret.trim()))
            .putString("lfm_user", s.lastFmUser.trim())
            .putString("lfm_session", SecretBox.encrypt(s.lastFmSession))
            .putBoolean("lb_on", s.listenBrainzEnabled)
            .putString("lb_url", s.listenBrainzUrl.trim())
            .putString("lb_token", SecretBox.encrypt(s.listenBrainzToken.trim()))
            .putInt("tc_wifi", s.transcodeWifiKbps)
            .putInt("tc_mobile", s.transcodeMobileKbps)
            .putInt("song_cache_mb", s.songCacheMb)
            .putInt("preload_wifi", s.preloadWifi)
            .putInt("preload_mobile", s.preloadMobile)
            .putInt("wind_down_songs", s.windDownSongs)
            .apply()
        shared.edit()
            .putBoolean(SEEDED, true)
            .putString("am_url", s.audioMuseUrl)
            .putString("am_token", SecretBox.encrypt(s.audioMuseToken))
            .putString("am_user", s.audioMuseUser)
            .putString("am_pass", SecretBox.encrypt(s.audioMusePassword))
            .putString("or_key", SecretBox.encrypt(s.aiKey))
            .putString("or_model", s.aiModel)
            .putString("ai_provider", s.aiProvider.name)
            .putString("ai_address", s.aiAddress)
            .putString("abs_url", s.audiobookshelfUrl)
            .putString("abs_token", SecretBox.encrypt(s.audiobookshelfToken))
            .putString("abs_user", s.audiobookshelfUser)
            .putString("abs_pass", SecretBox.encrypt(s.audiobookshelfPassword))
            .apply()
        prefs.edit().putString("am_server", s.audioMuseServer)
            .putInt("book_skip_back", s.bookSkipBackSec)
            .putInt("book_skip_fwd", s.bookSkipForwardSec)
            .apply()
        _state.value = s
    }

    fun setImageCacheMb(mb: Int) {
        prefs.edit().putInt("image_cache_mb", mb).apply()
        _state.value = _state.value.copy(imageCacheMb = mb)
    }


    fun setCoverDownload(
        on: Boolean,
        anyWifi: Boolean = _state.value.coverDownloadAnyWifi,
        wifiOnly: Boolean = _state.value.coverDownloadWifiOnly,
    ) {
        prefs.edit().putBoolean("cover_download", on).putBoolean("cover_download_any_wifi", anyWifi)
            .putBoolean("cover_download_wifi_only", wifiOnly).apply()
        _state.value = _state.value.copy(coverDownload = on, coverDownloadAnyWifi = anyWifi, coverDownloadWifiOnly = wifiOnly)
    }

    fun setDownloads(
        wifiOnly: Boolean = _state.value.downloadWifiOnly,
        limitMb: Int = _state.value.downloadLimitMb,
        kbps: Int = _state.value.downloadKbps,
    ) {
        prefs.edit().putBoolean("download_wifi_only", wifiOnly).putInt("download_limit_mb", limitMb).putInt("download_kbps", kbps).apply()
        _state.value = _state.value.copy(downloadWifiOnly = wifiOnly, downloadLimitMb = limitMb, downloadKbps = kbps)
    }

    fun setTimeMachineMissing(on: Boolean) {
        prefs.edit().putBoolean("time_machine_missing", on).apply()
        _state.value = _state.value.copy(timeMachineMissing = on)
    }

    fun setAnimatedCovers(on: Boolean) {
        prefs.edit().putBoolean("animated_covers", on).apply()
        _state.value = _state.value.copy(animatedCovers = on)
    }

    fun setLogging(on: Boolean) {
        prefs.edit().putBoolean("logging", on).apply()
        _state.value = _state.value.copy(logging = on)
    }

    /** What of the moment the vibes use (issue #247). */
    fun setRadioDrift(drift: RadioDrift) {
        prefs.edit().putString("radio_drift", drift.name).apply()
        _state.value = _state.value.copy(radioDrift = drift)
    }

    /** Keep playing when the queue ends (issue #337). */
    fun setContinuousPlay(mode: ContinuousMode) {
        prefs.edit().putString("continuous_play", mode.name).apply()
        _state.value = _state.value.copy(continuousPlay = mode)
    }

    fun setSituation(options: SituationOptions) {
        prefs.edit()
            .putBoolean("situation_weather", options.weather)
            .putBoolean("situation_movement", options.movement)
            .putBoolean("situation_light", options.light)
            .putBoolean("situation_charging", options.charging)
            .putBoolean("situation_device", options.device)
            .apply()
        _state.value = _state.value.copy(situation = options)
    }


    fun setMiniButtons(buttons: List<MiniButton>) {
        prefs.edit().putString("mini_buttons", MiniButton.format(buttons)).apply()
        _state.value = _state.value.copy(miniButtons = buttons)
    }

    fun setNavTabs(tabs: List<NavTab>) {
        val kept = NavTab.parse(NavTab.format(tabs))
        prefs.edit().putString("nav_tabs", NavTab.format(kept)).apply()
        _state.value = _state.value.copy(navTabs = kept)
    }

    fun setTopBar(style: TopBarStyle) {
        prefs.edit().putString("top_bar", style.name).apply()
        _state.value = _state.value.copy(topBar = style)
    }

    fun setNowPlayingLyricsButton(on: Boolean) {
        prefs.edit().putBoolean("now_playing_lyrics_button", on).apply()
        _state.value = _state.value.copy(nowPlayingLyricsButton = on)
    }

    fun setHomeOrder(order: List<HomeSection>) {
        prefs.edit().putString("home_order", HomeSection.format(order)).apply()
        _state.value = _state.value.copy(homeOrder = order)
    }

    fun setHomeGap(step: Int) {
        val gap = step.coerceIn(0, AppSettings.HOME_GAP_MAX)
        prefs.edit().putInt("home_gap", gap).apply()
        _state.value = _state.value.copy(homeGap = gap)
    }

    fun setHomeHidden(hidden: Set<HomeSection>) {
        prefs.edit().putString("home_hidden", HomeSection.formatHidden(hidden)).apply()
        _state.value = _state.value.copy(homeHidden = hidden)
    }

    /** The look of one of Home's rows (issue #655); [HomeLook.AS_IS] puts it back. */
    fun setHomeLook(section: HomeSection, look: HomeLook) {
        val looks = (_state.value.homeLooks - section) + (if (look != HomeLook.AS_IS && section.lookable) mapOf(section to look) else emptyMap())
        prefs.edit().putString("home_looks", HomeLook.format(looks)).apply()
        _state.value = _state.value.copy(homeLooks = looks)
    }

    /** Every row back in its own look. */
    fun resetHomeLooks() {
        prefs.edit().remove("home_looks").apply()
        _state.value = _state.value.copy(homeLooks = emptyMap())
    }

    fun setSyncFolders(ids: Set<String>) {
        prefs.edit().putStringSet("sync_folders", ids).apply()
        _state.value = _state.value.copy(syncFolders = ids)
    }

    fun setDownloadedOnly(on: Boolean) {
        prefs.edit().putBoolean("downloaded_only", on).apply()
        _state.value = _state.value.copy(downloadedOnly = on)
    }

    fun setSelectionColour(name: String) {
        prefs.edit().putString("theme_selection", name).apply()
        _state.value = _state.value.copy(themeSelection = name)
    }

    fun setTheme(
        mode: String = _state.value.themeMode,
        colours: String = _state.value.themeColours,
        pureBlack: Boolean = _state.value.pureBlack,
        seed: Int = _state.value.themeSeed,
    ) {
        prefs.edit().putString("theme_mode", mode).putString("theme_colours", colours).putBoolean("pure_black", pureBlack)
            .putInt("theme_seed", seed).apply()
        _state.value = _state.value.copy(themeMode = mode, themeColours = colours, pureBlack = pureBlack, themeSeed = seed)
    }

    fun setLoudness(source: LoudnessSource = _state.value.loudnessSource, target: Int = _state.value.loudnessTarget) {
        prefs.edit().putString("loudness_source", source.name).putInt("loudness_target", target).apply()
        _state.value = _state.value.copy(loudnessSource = source, loudnessTarget = target)
    }

    fun setCrossfade(mode: CrossfadeMode = _state.value.crossfade, seconds: Int = _state.value.crossfadeSeconds) {
        val length = seconds.coerceIn(AppSettings.CROSSFADE_SECONDS)
        prefs.edit().putString("crossfade", mode.name).putInt("crossfade_s", length).apply()
        _state.value = _state.value.copy(crossfade = mode, crossfadeSeconds = length)
    }

    fun setBeatMatch(mode: BeatMatchMode) {
        prefs.edit().putString("beat_match", mode.name).apply()
        _state.value = _state.value.copy(beatMatch = mode)
    }

    /** The shape and surprise of a DJ mix (issue #649). */
    fun setMixShape(shape: MixShape = _state.value.mixShape, surprise: Int = _state.value.mixSurprise) {
        val s = surprise.coerceIn(0, 100)
        prefs.edit().putString("mix_shape", shape.name).putInt("mix_surprise", s).apply()
        _state.value = _state.value.copy(mixShape = shape, mixSurprise = s)
    }
}
