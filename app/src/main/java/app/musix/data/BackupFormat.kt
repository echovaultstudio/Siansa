package app.musix.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class BackupException(message: String) : Exception(message)

/** One saved setting, with its type so it's restored exactly as it was. */
@Serializable
data class BackupValue(val type: String, val value: JsonElement)

/**
 * Everything Musix keeps on the phone that you chose or built up: every settings store and every JSON file
 * in the app's files, except caches. New settings and new on-phone data (likes, ratings, …) kept in a
 * SharedPreferences store or a JSON file are included without changing this, unless listed as a cache in
 * [BackupRules]. The synced library isn't included: it comes back with the next sync.
 */
@Serializable
data class BackupData(
    val app: String = BackupRules.APP,
    val format: Int = BackupRules.FORMAT,
    val createdAt: Long = 0,
    val appVersion: String = "",
    val prefs: Map<String, Map<String, BackupValue>> = emptyMap(),
    val files: Map<String, JsonElement> = emptyMap(),
)

object BackupRules {
    const val APP = "musix"
    const val FORMAT = 1
    /** Automatic backups kept in the chosen folder. */
    const val KEEP = 7

    /** Settings stores that only hold caches or details of this phone. */
    private val SKIPPED_PREFS = setOf("animated_covers", "artist_suggestions", "cover_aliases", "diagnostics", "backup")

    /** Encrypted with a key that never leaves this phone, so they're never saved; restoring keeps the current ones. */
    private val SECRET_KEYS = mapOf(
        "settings" to setOf("nd_pass", "proxy_pass", "am_token", "am_pass", "or_key", "lfm_secret", "lfm_session", "lb_token"),
    )

    /** Files that are only a queue or a cache: restoring them later would do things twice. */
    private val SKIPPED_FILES = setOf("pending_scrobbles.json")

    private val PREFS_NAME = Regex("[A-Za-z0-9_]+")
    private val FILE_NAME = Regex("[A-Za-z0-9_-]+\\.json")
    private val AUTO_NAME = Regex("musix-backup-\\d{4}-\\d{2}-\\d{2}-\\d{4}\\.json")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Library stores (WorkManager, …) and caches are left out; anything else the app keeps is included.
     * Each Navidrome user's copy (`settings__u2`, `history__u2.json`) follows the same rules as the first's.
     */
    fun includesPrefs(name: String): Boolean = PREFS_NAME.matches(name) && UserFiles.base(name) !in SKIPPED_PREFS

    fun includesFile(name: String): Boolean = FILE_NAME.matches(name) && UserFiles.base(name) !in SKIPPED_FILES

    fun isSecret(prefs: String, key: String): Boolean = key in SECRET_KEYS[UserFiles.base(prefs)].orEmpty()

    fun encodeValue(value: Any?): BackupValue? = when (value) {
        is String -> BackupValue("string", JsonPrimitive(value))
        is Boolean -> BackupValue("boolean", JsonPrimitive(value))
        is Int -> BackupValue("int", JsonPrimitive(value))
        is Long -> BackupValue("long", JsonPrimitive(value))
        is Float -> BackupValue("float", JsonPrimitive(value))
        is Set<*> -> BackupValue("stringSet", JsonArray(value.filterIsInstance<String>().sorted().map { JsonPrimitive(it) }))
        else -> null
    }

    /** The saved value as SharedPreferences stores it, or null when it can't be read. */
    fun decodeValue(saved: BackupValue): Any? = runCatching {
        when (saved.type) {
            "string" -> saved.value.jsonPrimitive.content
            "boolean" -> saved.value.jsonPrimitive.booleanOrNull
            "int" -> saved.value.jsonPrimitive.longOrNull?.toInt()
            "long" -> saved.value.jsonPrimitive.longOrNull
            "float" -> saved.value.jsonPrimitive.doubleOrNull?.toFloat()
            "stringSet" -> (saved.value as JsonArray).map { it.jsonPrimitive.content }.toSet()
            else -> null
        }
    }.getOrNull()

    /** Builds the backup text from settings stores (name → values) and files (name → JSON text). */
    fun pack(prefs: Map<String, Map<String, Any?>>, files: Map<String, String>, createdAt: Long, appVersion: String): String {
        val savedPrefs = prefs.filterKeys { includesPrefs(it) }.mapValues { (name, values) ->
            values.filterKeys { !isSecret(name, it) }
                .mapNotNull { (key, value) -> encodeValue(value)?.let { key to it } }
                .toMap().toSortedMap()
        }.toSortedMap()
        val savedFiles = files.filterKeys { includesFile(it) }
            .mapNotNull { (name, text) -> runCatching { json.parseToJsonElement(text) }.getOrNull()?.let { name to it } }
            .toMap().toSortedMap()
        return json.encodeToString(BackupData.serializer(), BackupData(APP, FORMAT, createdAt, appVersion, savedPrefs, savedFiles))
    }

    /** Reads a backup file. Throws [BackupException] when it isn't one Musix can restore. */
    fun unpack(text: String): BackupData {
        val data = try {
            json.decodeFromString(BackupData.serializer(), text)
        } catch (e: Exception) {
            throw BackupException("This file isn't a Musix backup")
        }
        if (data.app != APP) throw BackupException("This file isn't a Musix backup")
        if (data.format > FORMAT) throw BackupException("This backup is from a newer Musix. Update the app first.")
        return data.copy(
            prefs = data.prefs.filterKeys { includesPrefs(it) }.mapValues { (name, values) -> values.filterKeys { !isSecret(name, it) } },
            files = data.files.filterKeys { includesFile(it) },
        )
    }

    fun autoName(stamp: String): String = "musix-backup-$stamp.json"

    /** Older automatic backups to delete so only the newest [keep] stay. Other files are never touched. */
    fun autoBackupsToDelete(names: List<String>, keep: Int = KEEP): List<String> =
        names.filter { AUTO_NAME.matches(it) }.sortedDescending().drop(keep)
}
