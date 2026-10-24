package app.siansa.data

import android.content.Context
import android.content.Intent
import app.siansa.log.AppLog
import app.siansa.net.SourceKind
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File

/**
 * File names for each Navidrome user. The first user keeps the original names (so nothing moves when
 * several users arrive); every other user's settings, library and history get their own copy, with
 * `__u<id>` before the extension: `history.json` → `history__u2.json`, `settings` → `settings__u2`.
 */
object UserFiles {
    const val FIRST = 0

    private val SUFFIX = Regex("__u(\\d+)(?=\\.|$)")

    fun scoped(name: String, user: Int): String {
        if (user == FIRST) return name
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) "${name}__u$user" else "${name.substring(0, dot)}__u$user${name.substring(dot)}"
    }

    /** The name shared by every user's copy: `history__u2.json` → `history.json`. */
    fun base(name: String): String = name.replace(SUFFIX, "")

    /** Which user a file belongs to. */
    fun userOf(name: String): Int = SUFFIX.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: FIRST

    /** Settings stores kept per user. */
    val PREFS = listOf("settings", "library_pins", "artist_suggestions", "emby")

    /** Files in the app's files folder kept per user. */
    val FILES = listOf("history.json", "mixes.json", "selectors.json", "pending_scrobbles.json")

    /** Files in the app's cache folder kept per user. */
    val CACHE_FILES = listOf("top_songs.json")

    const val DATABASE = "library.db"

    /** How a user is shown in the list: "anna on music.example.com", or a note when not set up yet. */
    fun label(user: String, address: String): String {
        val host = address.toHttpUrlOrNull()?.host
        return when {
            user.isNotBlank() && host != null -> "$user on $host"
            user.isNotBlank() -> user
            else -> "New user, not connected yet"
        }
    }

    /** The stored list of users; before a second user is added, just the first. */
    fun parseIds(stored: String?): List<Int> =
        stored?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.distinct()?.sorted()?.ifEmpty { null } ?: listOf(FIRST)

    /** The id for a new user: one more than the highest so far. */
    fun nextId(existing: Collection<Int>): Int = (existing.maxOrNull() ?: FIRST) + 1

    /**
     * Whose music shows (issue #141), from what's stored: always the user in use, plus any others shown
     * with them that still exist.
     */
    fun parseShown(stored: String?, active: Int, ids: Collection<Int>): Set<Int> =
        (stored?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty().filter { it in ids } + active).toSortedSet()

    fun formatShown(shown: Set<Int>): String = shown.sorted().joinToString(",")

    /**
     * Who shows after switching from [old] to [new] in the users list: when only [old] was shown, only
     * [new]; when several were shown together, they stay and [new] joins them.
     */
    fun shownAfterSwitch(shown: Set<Int>, old: Int, new: Int): Set<Int> =
        if (shown - old - new == emptySet<Int>() && new !in shown) setOf(new) else shown + new

    /**
     * Hiding the user in use: the app switches to another shown user, who then becomes the one in use.
     * Null when nobody else is shown (someone has to stay).
     */
    fun afterHidingActive(shown: Set<Int>, active: Int): Pair<Int, Set<Int>>? {
        val rest = shown - active
        return rest.minOrNull()?.let { it to rest }
    }
}

/** One Navidrome login on this phone. */
data class UserEntry(val id: Int, val label: String, val connected: Boolean, val emby: Boolean = false)

/**
 * Several Navidrome logins (on the same or different servers), each with its own settings, library and
 * history. One is in use at a time (its history, playlists and settings); switching restarts the app with
 * the chosen user's files. Other users' music can show alongside it ([shown]).
 */
object Users {
    private const val PREFS = "users"
    private const val KEY_ACTIVE = "active"
    private const val KEY_IDS = "ids"
    private const val KEY_SHOWN = "shown"

    /** The user whose files are in use, read once when the app starts. */
    @Volatile var active: Int = UserFiles.FIRST
        private set

    fun init(context: Context) {
        val stored = prefs(context).getInt(KEY_ACTIVE, UserFiles.FIRST)
        val ids = ids(context)
        active = if (stored in ids) stored else ids.first()
        if (active != UserFiles.FIRST) AppLog.i("Users", "Using user ${active + 1} of ${ids(context).size}")
    }

    /** This user's copy of a settings store or file name. */
    fun scoped(name: String): String = UserFiles.scoped(name, active)

    fun ids(context: Context): List<Int> = UserFiles.parseIds(prefs(context).getString(KEY_IDS, null))

    fun all(context: Context): List<UserEntry> = ids(context).map { id ->
        val settings = context.getSharedPreferences(UserFiles.scoped("settings", id), Context.MODE_PRIVATE)
        val user = settings.getString("nd_user", "").orEmpty()
        val address = settings.getString("nd_url", "").orEmpty()
        UserEntry(
            id, UserFiles.label(user, address), connected = user.isNotBlank() && address.isNotBlank(),
            emby = SourceKind.parse(settings.getString("source_kind", null)) == SourceKind.EMBY,
        )
    }

    /** Users whose music shows; always includes the user in use (issue #141). */
    fun shown(context: Context): Set<Int> =
        UserFiles.parseShown(prefs(context).getString(KEY_SHOWN, null), active, ids(context))

    /** Other users whose music shows alongside the user in use. */
    fun guests(context: Context): Set<Int> = shown(context) - active

    fun setShown(context: Context, ids: Set<Int>) {
        prefs(context).edit().putString(KEY_SHOWN, UserFiles.formatShown(ids + active)).commit()
        AppLog.i("Users", "Showing music of ${(ids + active).size} user(s)")
    }

    /** Adds an empty user and switches to it, where the app asks for its Navidrome login. */
    fun addAndSwitch(context: Context) {
        val ids = ids(context)
        val id = UserFiles.nextId(ids)
        prefs(context).edit().putString(KEY_IDS, (ids + id).joinToString(",")).commit()
        AppLog.i("Users", "Added user ${id + 1}")
        switchTo(context, id)
    }

    /** Restarts the app as [id]. */
    fun switchTo(context: Context, id: Int) {
        switchTo(context, id, UserFiles.shownAfterSwitch(shown(context), active, id))
    }

    /** Restarts the app as [id], showing the music of [shown] users. */
    fun switchTo(context: Context, id: Int, shown: Set<Int>) {
        if (id == active || id !in ids(context)) return
        prefs(context).edit().putInt(KEY_ACTIVE, id).putString(KEY_SHOWN, UserFiles.formatShown(shown + id)).commit()
        AppLog.i("Users", "Switching user; the app restarts")
        val app = context.applicationContext
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        val component = launch.component ?: return
        app.startActivity(Intent.makeRestartActivityTask(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Runtime.getRuntime().exit(0)
    }

    /** Removes another user and everything kept for them on this phone. The user in use can't be removed. */
    fun remove(context: Context, id: Int) {
        if (id == active) return
        val ids = ids(context)
        if (id !in ids) return
        UserFiles.PREFS.forEach { context.deleteSharedPreferences(UserFiles.scoped(it, id)) }
        UserFiles.FILES.forEach { File(context.filesDir, UserFiles.scoped(it, id)).delete() }
        UserFiles.CACHE_FILES.forEach { File(context.cacheDir, UserFiles.scoped(it, id)).delete() }
        context.deleteDatabase(UserFiles.scoped(UserFiles.DATABASE, id))
        prefs(context).edit().putString(KEY_IDS, (ids - id).joinToString(","))
            .putString(KEY_SHOWN, UserFiles.formatShown(shown(context) - id)).commit()
        AppLog.i("Users", "Removed a user and their library, history and settings")
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
