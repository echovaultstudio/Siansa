package app.siansa.net

import android.content.Context
import app.siansa.data.SecretBox
import app.siansa.data.UserFiles
import app.siansa.data.Users
import java.util.UUID

/**
 * Keeps the Emby login between runs (issue #113), per Siansa user: the access token is encrypted like the
 * passwords in the settings. Also this phone's id for Emby's list of devices. Another user shown next to the one
 * in use (issue #427) keeps theirs in their own store.
 */
object EmbyLogins {
    private fun prefs(context: Context, user: Int = Users.active) =
        context.getSharedPreferences(UserFiles.scoped(STORE, user), Context.MODE_PRIVATE)

    const val STORE = "emby"

    fun deviceId(context: Context): String {
        val p = prefs(context)
        return p.getString("device_id", null) ?: UUID.randomUUID().toString().also { p.edit().putString("device_id", it).apply() }
    }

    fun recall(context: Context, user: Int = Users.active): EmbyApi.Login? {
        val p = prefs(context, user)
        val key = p.getString("key", null) ?: return null
        val userId = p.getString("user_id", null) ?: return null
        val token = runCatching { SecretBox.decrypt(p.getString("token", "") ?: "") }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return EmbyApi.Login(key, userId, token)
    }

    fun remember(context: Context, login: EmbyApi.Login, user: Int = Users.active) {
        prefs(context, user).edit()
            .putString("key", login.key)
            .putString("user_id", login.userId)
            .putString("token", SecretBox.encrypt(login.token))
            .apply()
    }
}
