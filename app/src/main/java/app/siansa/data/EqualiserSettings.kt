package app.siansa.data

import android.content.Context
import androidx.core.content.edit
import app.siansa.log.AppLog
import app.siansa.playback.EqPreset
import app.siansa.playback.EqSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The equaliser setting, kept with the other settings (so backups and each user carry it). */
class EqualiserRepository(context: Context) {
    private val prefs = context.getSharedPreferences(Users.scoped("settings"), Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<EqSettings> = _state.asStateFlow()

    private fun load() = EqSettings(
        enabled = prefs.getBoolean("eq_on", false),
        preset = runCatching { EqPreset.valueOf(prefs.getString("eq_preset", null) ?: "FLAT") }.getOrDefault(EqPreset.FLAT),
        custom = EqSettings.parseCustom(prefs.getString("eq_custom", null)),
        preampDb = prefs.getFloat("eq_preamp", 0f).coerceIn(-EqSettings.MAX_PREAMP_DB, EqSettings.MAX_PREAMP_DB),
    )

    /** Heard straight away; written to storage only when [save] is true (at the end of a slider drag). */
    fun set(eq: EqSettings, save: Boolean = true) {
        _state.value = eq
        if (!save) return
        prefs.edit {
            putBoolean("eq_on", eq.enabled)
            putString("eq_preset", eq.preset.name)
            putString("eq_custom", EqSettings.formatCustom(eq.custom))
            putFloat("eq_preamp", eq.preampDb)
        }
        AppLog.i("Equaliser", if (eq.enabled) "On · ${eq.preset.label} · pre-amp ${"%+.0f".format(eq.preampDb)} dB" else "Off")
    }
}
