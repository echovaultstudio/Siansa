package app.siansa.data

import android.content.Context
import app.siansa.mix.NoticeKind
import app.siansa.mix.Notices

/**
 * What the notices on Home remember (issue #784), for each user: how often each kind was tapped, so Automatic puts
 * the ones you use first, and the notices you hid.
 */
class NoticeStore(context: Context) {
    private val prefs by lazy { context.getSharedPreferences(Users.scoped("notices"), Context.MODE_PRIVATE) }

    fun taps(): Map<NoticeKind, Int> = parseTaps(prefs.getString(KEY_TAPS, null))

    fun tap(kind: NoticeKind) {
        val taps = taps().toMutableMap().apply { this[kind] = (this[kind] ?: 0) + 1 }
        prefs.edit().putString(KEY_TAPS, formatTaps(taps)).apply()
    }

    fun hidden(): Set<String> = hiddenList().toSet()

    fun hide(key: String) {
        prefs.edit().putString(KEY_HIDDEN, Notices.hide(hiddenList(), key).joinToString("\n")).apply()
    }

    private fun hiddenList(): List<String> = prefs.getString(KEY_HIDDEN, null).orEmpty().split('\n').filter { it.isNotBlank() }

    companion object {
        private const val KEY_TAPS = "taps"
        private const val KEY_HIDDEN = "hidden"

        /** "carry_on:3,month:1"; unknown kinds and bad counts are dropped. */
        fun parseTaps(saved: String?): Map<NoticeKind, Int> = saved.orEmpty().split(',').mapNotNull { part ->
            val kind = NoticeKind.byId(part.substringBefore(':')) ?: return@mapNotNull null
            val n = part.substringAfter(':', "").trim().toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            kind to n
        }.toMap()

        fun formatTaps(taps: Map<NoticeKind, Int>): String =
            NoticeKind.entries.mapNotNull { k -> taps[k]?.takeIf { it > 0 }?.let { "${k.id}:$it" } }.joinToString(",")
    }
}
