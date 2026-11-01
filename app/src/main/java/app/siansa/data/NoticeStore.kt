package app.siansa.data

import android.content.Context
import app.siansa.mix.NoticeKind

/**
 * What the notices on Home remember (issue #784), for each user: how often each kind was tapped, so Automatic puts
 * the ones you use first.
 */
class NoticeStore(context: Context) {
    /**
     * The notices last shown, kept in memory (issue #784): Home's row starts from them when it comes back into view,
     * so the chips don't vanish and pop back, pushing the page down, each time you scroll past or come back to Home.
     */
    @Volatile var shown: List<app.siansa.mix.Notice> = emptyList()

    private val prefs by lazy { context.getSharedPreferences(Users.scoped("notices"), Context.MODE_PRIVATE) }

    fun taps(): Map<NoticeKind, Int> = parseTaps(prefs.getString(KEY_TAPS, null))

    fun tap(kind: NoticeKind) {
        val taps = taps().toMutableMap().apply { this[kind] = (this[kind] ?: 0) + 1 }
        prefs.edit().putString(KEY_TAPS, formatTaps(taps)).apply()
    }

    companion object {
        private const val KEY_TAPS = "taps"

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
