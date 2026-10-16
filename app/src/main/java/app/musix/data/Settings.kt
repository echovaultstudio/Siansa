package app.musix.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLDecoder

enum class ReplayGainMode(val label: String) { TRACK("Track"), ALBUM("Album"), OFF("Off") }

data class AppSettings(
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
    val imageCacheMb: Int = 500,
    val logging: Boolean = false,
    val scrobbleNavidrome: Boolean = true,
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
) {
    val lastFmReady: Boolean get() = lastFmEnabled && lastFmApiKey.isNotBlank() && lastFmSecret.isNotBlank() && lastFmSession.isNotBlank()
    val listenBrainzReady: Boolean get() = listenBrainzEnabled && listenBrainzToken.isNotBlank()

    val navidromeReady: Boolean
        get() = navidromeUrl.isNotBlank() && navidromeUser.isNotBlank() && navidromePassword.isNotBlank()
    val audioMuseReady: Boolean
        get() = audioMuseUrl.isNotBlank()
    val aiReady: Boolean
        get() = aiTitles && openRouterKey.isNotBlank() && openRouterModel.isNotBlank()

    companion object {
        const val DEFAULT_MODEL = "google/gemini-2.5-flash"
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

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    private fun secret(key: String) = SecretBox.decrypt(prefs.getString(key, "") ?: "")

    private fun load(): AppSettings = AppSettings(
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
        logging = prefs.getBoolean("logging", false),
        scrobbleNavidrome = prefs.getBoolean("scrobble_nd", true),
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
            .putBoolean("scrobble_nd", s.scrobbleNavidrome)
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
            .apply()
        _state.value = s
    }

    fun setImageCacheMb(mb: Int) {
        prefs.edit().putInt("image_cache_mb", mb).apply()
        _state.value = _state.value.copy(imageCacheMb = mb)
    }

    fun setLogging(on: Boolean) {
        prefs.edit().putBoolean("logging", on).apply()
        _state.value = _state.value.copy(logging = on)
    }

    fun setSyncFolders(ids: Set<String>) {
        prefs.edit().putStringSet("sync_folders", ids).apply()
        _state.value = _state.value.copy(syncFolders = ids)
    }
}
