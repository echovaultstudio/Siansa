package app.musix.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReplayGainMode(val label: String) { TRACK("Track"), ALBUM("Album"), OFF("Off") }

data class AppSettings(
    val navidromeUrl: String = "",
    val navidromeUser: String = "",
    val navidromePassword: String = "",
    val audioMuseUrl: String = "",
    val audioMuseToken: String = "",
    val openRouterKey: String = "",
    val openRouterModel: String = DEFAULT_MODEL,
    val aiTitles: Boolean = true,
    val replayGain: ReplayGainMode = ReplayGainMode.TRACK,
    val preampDb: Float = 0f,
) {
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

/** Adds http:// when no scheme was typed and drops trailing slashes. */
fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ""
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
}

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    private fun load(): AppSettings = AppSettings(
        navidromeUrl = prefs.getString("nd_url", "") ?: "",
        navidromeUser = prefs.getString("nd_user", "") ?: "",
        navidromePassword = SecretBox.decrypt(prefs.getString("nd_pass", "") ?: ""),
        audioMuseUrl = prefs.getString("am_url", "") ?: "",
        audioMuseToken = SecretBox.decrypt(prefs.getString("am_token", "") ?: ""),
        openRouterKey = SecretBox.decrypt(prefs.getString("or_key", "") ?: ""),
        openRouterModel = prefs.getString("or_model", AppSettings.DEFAULT_MODEL) ?: AppSettings.DEFAULT_MODEL,
        aiTitles = prefs.getBoolean("ai_titles", true),
        replayGain = runCatching { ReplayGainMode.valueOf(prefs.getString("rg", "TRACK") ?: "TRACK") }
            .getOrDefault(ReplayGainMode.TRACK),
        preampDb = prefs.getFloat("preamp", 0f),
    )

    fun save(input: AppSettings) {
        val s = input.copy(
            navidromeUrl = normalizeUrl(input.navidromeUrl),
            navidromeUser = input.navidromeUser.trim(),
            audioMuseUrl = normalizeUrl(input.audioMuseUrl),
            audioMuseToken = input.audioMuseToken.trim(),
            openRouterKey = input.openRouterKey.trim(),
            openRouterModel = input.openRouterModel.trim(),
        )
        prefs.edit()
            .putString("nd_url", s.navidromeUrl)
            .putString("nd_user", s.navidromeUser)
            .putString("nd_pass", SecretBox.encrypt(s.navidromePassword))
            .putString("am_url", s.audioMuseUrl)
            .putString("am_token", SecretBox.encrypt(s.audioMuseToken))
            .putString("or_key", SecretBox.encrypt(s.openRouterKey))
            .putString("or_model", s.openRouterModel)
            .putBoolean("ai_titles", s.aiTitles)
            .putString("rg", s.replayGain.name)
            .putFloat("preamp", s.preampDb)
            .apply()
        _state.value = s
    }
}
