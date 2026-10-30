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

    /** A user's row, first line (#571): their login name, or a note when not set up yet. */
    fun title(user: String): String = user.ifBlank { "New user" }

    /**
     * What kind of server (#571): OpenSubsonic, Emby… An OpenSubsonic server isn't called Navidrome, as it may be
     * Gonic, Airsonic or another; the app doesn't know which.
     */
    fun kindName(kind: SourceKind): String = when (kind) {
        SourceKind.OPENSUBSONIC -> "OpenSubsonic"
        SourceKind.EMBY -> "Emby"
        SourceKind.JELLYFIN -> "Jellyfin"
        SourceKind.LOCAL -> "This phone"
    }

    /**
     * A user's row, second line in smaller type (#571): the kind of server, then the name the user gave it (the pencil
     * in Settings › Connections), if any: "OpenSubsonic", "Emby · Living room". "Not connected yet" before set-up.
     */
    fun serverLine(kind: SourceKind, serverName: String?, connected: Boolean): String =
        if (!connected) "Not connected yet"
        else listOfNotNull(kindName(kind), serverName?.takeIf { it.isNotBlank() }).joinToString(" · ")

    /**
     * How a user is shown in the list: "anna on music.example.com", or "anna on Home Navidrome" when their server has
     * a name ([serverName]), or a note when not set up yet.
     */
    fun label(user: String, address: String, serverName: String? = null): String {
        val host = serverName?.takeIf { it.isNotBlank() } ?: address.toHttpUrlOrNull()?.host
        return when {
            user.isNotBlank() && host != null -> "$user on $host"
            user.isNotBlank() -> user
            else -> "New user, not connected yet"
        }
    }

    /**
     * Which server an address is, for "show together": scheme, host, port (the scheme's default when none) and path,
     * without case or a trailing slash. Null when it isn't an address.
     */
    fun serverKey(address: String): String? {
        val url = address.trim().toHttpUrlOrNull() ?: return null
        return "${url.scheme}://${url.host.lowercase()}:${url.port}${url.encodedPath.trimEnd('/').lowercase()}"
    }

    /**
     * Whether two users are on the same server, the only ones whose music can be shown together: the same kind
     * (Navidrome, Emby…) at the same address. Users on other servers are switched to instead.
     */
    fun sameServer(kindA: SourceKind, addressA: String, kindB: SourceKind, addressB: String): Boolean {
        val a = serverKey(addressA) ?: return false
        return kindA == kindB && a == serverKey(addressB)
    }

    /** Longest name a server can be given. */
    const val SERVER_NAME_MAX = 40

    /** A name as typed, made fit to show: one line, trimmed, not too long; blank for none. */
    fun cleanServerName(name: String): String =
        name.replace(Regex("\\s+"), " ").trim().take(SERVER_NAME_MAX).trim()

    /** Server names as stored, "<server key>\n<name>" each, by server key ([serverKey]). */
    fun parseServerNames(stored: Set<String>): Map<String, String> = stored.mapNotNull { entry ->
        val cut = entry.indexOf('\n')
        if (cut <= 0) null else entry.substring(0, cut) to cleanServerName(entry.substring(cut + 1))
    }.filter { it.second.isNotEmpty() }.toMap()

    fun formatServerNames(names: Map<String, String>): Set<String> =
        names.mapNotNull { (key, name) -> cleanServerName(name).takeIf { it.isNotEmpty() }?.let { "$key\n$it" } }.toSet()

    /**
     * The name shown for user [id]'s server: the one that user gave it ([userNames], by user id), else one kept by
     * server from before names were each user's own ([serverNames]).
     */
    fun nameFor(id: Int, server: String?, userNames: Map<String, String>, serverNames: Map<String, String>): String? =
        userNames[id.toString()] ?: server?.let { serverNames[it] }

    /**
     * The names after user [id] names their server [name]; blank shows its address again. A name kept by server
     * from before first goes to every user on that server without one of their own, so naming one user's server
     * doesn't rename it for the others. [servers] is each user's server key. Returns (by user, by server).
     */
    fun withUserName(
        id: Int,
        name: String,
        servers: Map<Int, String?>,
        userNames: Map<String, String>,
        serverNames: Map<String, String>,
    ): Pair<Map<String, String>, Map<String, String>> {
        val byUser = userNames.toMutableMap()
        val byServer = serverNames.toMutableMap()
        servers[id]?.let { server ->
            byServer.remove(server)?.let { old ->
                servers.forEach { (other, s) -> if (s == server) byUser.putIfAbsent(other.toString(), old) }
            }
        }
        val clean = cleanServerName(name)
        if (clean.isEmpty()) byUser.remove(id.toString()) else byUser[id.toString()] = clean
        return byUser to byServer
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
data class UserEntry(
    val id: Int,
    val label: String,
    val connected: Boolean,
    val emby: Boolean = false,
    /** Their server's address as a key ([UserFiles.serverKey]), for naming it; null when not set up. */
    val server: String? = null,
    /** The name this user gave their server, or null. */
    val serverName: String? = null,
    /** Their login name, the first line of their row (#571). */
    val user: String = "",
    val kind: SourceKind = SourceKind.OPENSUBSONIC,
) {
    /** Their row's first line: the user. */
    val title: String get() = UserFiles.title(user)
    /** Their row's second line: the kind of server and its name, if given. */
    val serverLine: String get() = UserFiles.serverLine(kind, serverName, connected)
}

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
    /** Names kept by server, from before each user named their own (still read until a user on it renames). */
    private const val KEY_SERVER_NAMES = "serverNames"
    /** The name each user gave their server, by user id. */
    private const val KEY_USER_NAMES = "userServerNames"

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

    fun all(context: Context): List<UserEntry> {
        val byServer = serverNames(context)
        val byUser = userNames(context)
        return ids(context).map { id -> entry(context, id, byUser, byServer) }
    }

    private fun entry(context: Context, id: Int, byUser: Map<String, String>, byServer: Map<String, String>): UserEntry {
        val settings = context.getSharedPreferences(UserFiles.scoped("settings", id), Context.MODE_PRIVATE)
        val user = settings.getString("nd_user", "").orEmpty()
        val address = settings.getString("nd_url", "").orEmpty()
        val server = UserFiles.serverKey(address)
        val name = UserFiles.nameFor(id, server, byUser, byServer)
        val kind = SourceKind.parse(settings.getString("source_kind", null))
        return UserEntry(
            id, UserFiles.label(user, address, name), connected = user.isNotBlank() && address.isNotBlank(),
            emby = kind.embyLike,
            server = server, serverName = name, user = user, kind = kind,
        )
    }

    /** Names kept by server key, from before each user named their own server. */
    private fun serverNames(context: Context): Map<String, String> =
        UserFiles.parseServerNames(prefs(context).getStringSet(KEY_SERVER_NAMES, null).orEmpty())

    /** The name each user gave their server, by user id. */
    private fun userNames(context: Context): Map<String, String> =
        UserFiles.parseServerNames(prefs(context).getStringSet(KEY_USER_NAMES, null).orEmpty())

    /**
     * Names user [id]'s server, for that user only: it tells logins apart in the app and changes nothing on the
     * server. A blank name shows its address again.
     */
    fun setServerName(context: Context, id: Int, name: String) {
        val servers = all(context).associate { it.id to it.server }
        val (byUser, byServer) = UserFiles.withUserName(id, name, servers, userNames(context), serverNames(context))
        prefs(context).edit()
            .putStringSet(KEY_USER_NAMES, UserFiles.formatServerNames(byUser))
            .putStringSet(KEY_SERVER_NAMES, UserFiles.formatServerNames(byServer))
            .commit()
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
            .putString(KEY_SHOWN, UserFiles.formatShown(shown(context) - id))
            .putStringSet(KEY_USER_NAMES, UserFiles.formatServerNames(userNames(context) - id.toString()))
            .commit()
        AppLog.i("Users", "Removed a user and their library, history and settings")
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
