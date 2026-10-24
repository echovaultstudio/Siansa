package app.musix.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import app.musix.playback.CrossfadeMode
import app.musix.playback.LoudnessSource
import app.musix.mix.ContinuousMode
import app.musix.mix.RadioDrift
import app.musix.situation.SituationOptions
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLDecoder
import app.musix.net.SourceKind

enum class ReplayGainMode(val label: String) { TRACK("Track"), ALBUM("Album"), OFF("Off") }

/** "Your colour" starts as a teal until someone picks their own (issue #148). */
const val DEFAULT_THEME_SEED: Int = 0xFF0E9AA7.toInt()

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
    val openRouterKey: String = "",
    val openRouterModel: String = DEFAULT_MODEL,
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
    val logQuiet: Set<String> = emptySet(),
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
    /** Upcoming songs loaded ahead on Wi-Fi and on mobile data (issue #254, see [app.musix.playback.Preload]). */
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
    val homeHidden: Set<HomeSection> = emptySet(),
    /** Show only music on the phone, across the whole app (issue #141). */
    val downloadedOnly: Boolean = false,
    /** The time machine lists songs that aren't in your library, marked (issue #234). */
    val timeMachineMissing: Boolean = true,
    /** Theme (issue #148): "AUTO", "LIGHT" or "DARK"; colours by name ("MUSIX", "OCEAN"…); pure black when dark. */
    val themeMode: String = "AUTO",
    val themeColours: String = "MUSIX",
    val pureBlack: Boolean = false,
    /** The seed for "Your colour" (issue #148, step 2), as ARGB. */
    val themeSeed: Int = DEFAULT_THEME_SEED,
    /** The colour of selected items, by name ("THEME", "AMBER"…), issue #313. */
    val themeSelection: String = "THEME",
    /** Where song loudness and fade shapes come from (issue #60): ReplayGain tags, or the Musix plugin. */
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
        get() = aiTitles && openRouterKey.isNotBlank() && openRouterModel.isNotBlank()

    companion object {
        const val DEFAULT_MODEL = "google/gemini-2.5-flash"
        /** Close to what ReplayGain tags aim for, so switching where loudness comes from doesn't change the overall level. */
        const val DEFAULT_LOUDNESS_TARGET = -18
        const val DEFAULT_CROSSFADE_SECONDS = 6
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

/** One user's settings: by default the user in use, or another user's (to show their music too). */
class SettingsRepository(context: Context, user: Int = Users.active) {
    private val prefs = context.getSharedPreferences(UserFiles.scoped("settings", user), Context.MODE_PRIVATE)
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
        audioMuseUrl = prefs.getString("am_url", "") ?: "",
        audioMuseToken = secret("am_token"),
        audioMuseUser = prefs.getString("am_user", "") ?: "",
        audioMusePassword = secret("am_pass"),
        openRouterKey = secret("or_key"),
        openRouterModel = prefs.getString("or_model", AppSettings.DEFAULT_MODEL) ?: AppSettings.DEFAULT_MODEL,
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
        logQuiet = prefs.getString("log_quiet", "").orEmpty().split(',').filter { it.isNotBlank() }.toSet(),
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
        homeHidden = HomeSection.parseHidden(prefs.getString("home_hidden", null)),
        downloadedOnly = prefs.getBoolean("downloaded_only", false),
        timeMachineMissing = prefs.getBoolean("time_machine_missing", true),
        themeMode = prefs.getString("theme_mode", "AUTO") ?: "AUTO",
        themeColours = prefs.getString("theme_colours", "MUSIX") ?: "MUSIX",
        pureBlack = prefs.getBoolean("pure_black", false),
        themeSeed = prefs.getInt("theme_seed", DEFAULT_THEME_SEED),
        themeSelection = prefs.getString("theme_selection", "THEME") ?: "THEME",
        loudnessSource = LoudnessSource.parse(prefs.getString("loudness_source", null)),
        loudnessTarget = prefs.getInt("loudness_target", AppSettings.DEFAULT_LOUDNESS_TARGET),
        crossfade = CrossfadeMode.parse(prefs.getString("crossfade", null)),
        radioDrift = RadioDrift.parse(prefs.getString("radio_drift", null)),
        continuousPlay = ContinuousMode.parse(prefs.getString("continuous_play", null)),
        windDownSongs = prefs.getInt("wind_down_songs", AppSettings.DEFAULT_WIND_DOWN_SONGS)
            .takeIf { it in AppSettings.WIND_DOWN_CHOICES } ?: AppSettings.DEFAULT_WIND_DOWN_SONGS,
        crossfadeSeconds = prefs.getInt("crossfade_s", AppSettings.DEFAULT_CROSSFADE_SECONDS).coerceIn(AppSettings.CROSSFADE_SECONDS),
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
            openRouterKey = input.openRouterKey.trim(),
            openRouterModel = input.openRouterModel.trim(),
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
            .putString("am_url", s.audioMuseUrl)
            .putString("am_token", SecretBox.encrypt(s.audioMuseToken))
            .putString("am_user", s.audioMuseUser)
            .putString("am_pass", SecretBox.encrypt(s.audioMusePassword))
            .putString("or_key", SecretBox.encrypt(s.openRouterKey))
            .putString("or_model", s.openRouterModel)
            .putBoolean("ai_titles", s.aiTitles)
            .putString("rg", s.replayGain.name)
            .putFloat("preamp", s.preampDb)
            .putStringSet("sync_folders", s.syncFolders)
            .putInt("image_cache_mb", s.imageCacheMb)
            .putBoolean("logging", s.logging)
            .putString("log_quiet", s.logQuiet.sorted().joinToString(","))
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

    /** Leaves out (or keeps) the detailed log entries of [component] (issue #237). */
    fun setLogQuiet(quiet: Set<String>) {
        prefs.edit().putString("log_quiet", quiet.sorted().joinToString(",")).apply()
        _state.value = _state.value.copy(logQuiet = quiet)
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

    fun setNowPlayingLyricsButton(on: Boolean) {
        prefs.edit().putBoolean("now_playing_lyrics_button", on).apply()
        _state.value = _state.value.copy(nowPlayingLyricsButton = on)
    }

    fun setHomeOrder(order: List<HomeSection>) {
        prefs.edit().putString("home_order", HomeSection.format(order)).apply()
        _state.value = _state.value.copy(homeOrder = order)
    }

    fun setHomeHidden(hidden: Set<HomeSection>) {
        prefs.edit().putString("home_hidden", HomeSection.formatHidden(hidden)).apply()
        _state.value = _state.value.copy(homeHidden = hidden)
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
}
