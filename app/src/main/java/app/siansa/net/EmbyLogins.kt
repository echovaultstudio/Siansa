package app.siansa.net

import android.content.Context
import app.siansa.data.SecretBox
import app.siansa.data.Users
import java.util.UUID

/**
 * Keeps the Emby login between runs (issue #113), per Siansa user: the access token is encrypted like the
 * passwords in the settings. Also this phone's id for Emby's list of devices.
 */
object EmbyLogins {
    private fun prefs(context: Context) = context.getSharedPreferences(Users.scoped("emby"), Context.MODE_PRIVATE)

    fun deviceId(context: Context): String {
        val p = prefs(context)
        return p.getString("device_id", null) ?: UUID.randomUUID().toString().also { p.edit().putString("device_id", it).apply() }
    }

    fun recall(context: Context): EmbyApi.Login? {
        val p = prefs(context)
        val key = p.getString("key", null) ?: return null
        val user = p.getString("user_id", null) ?: return null
        val token = runCatching { SecretBox.decrypt(p.getString("token", "") ?: "") }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return EmbyApi.Login(key, user, token)
    }

    fun remember(context: Context, login: EmbyApi.Login) {
        prefs(context).edit()
            .putString("key", login.key)
            .putString("user_id", login.userId)
            .putString("token", SecretBox.encrypt(login.token))
            .apply()
    }
}
