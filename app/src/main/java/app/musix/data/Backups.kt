package app.musix.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.musix.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** What a restore brought back. */
data class RestoreResult(val stores: Int, val files: Int, val createdAt: Long)

/**
 * Saves and restores a backup of everything you set up in Musix (see [BackupData]), to a file you pick or,
 * once a day, to a folder you choose. Passwords, tokens and API keys are never in a backup.
 */
class Backups(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("backup", Context.MODE_PRIVATE)

    /** The folder chosen for automatic backups, or null when they're off. */
    val folder: Uri? get() = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)
    val lastAutoAt: Long get() = prefs.getLong(KEY_LAST_AT, 0)
    val lastAutoError: String? get() = prefs.getString(KEY_LAST_ERROR, null)

    /** The backup as text. */
    suspend fun create(): String = withContext(Dispatchers.IO) {
        val names = File(app.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
            .map { it.name }
            .filter { it.endsWith(".xml") }
            .map { it.removeSuffix(".xml") }
            .filter { BackupRules.includesPrefs(it) }
        val stores = names.associateWith { app.getSharedPreferences(it, Context.MODE_PRIVATE).all }
        val files = app.filesDir.listFiles().orEmpty()
            .filter { it.isFile && BackupRules.includesFile(it.name) }
            .associate { it.name to it.readText() }
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty()
        BackupRules.pack(stores, files, System.currentTimeMillis(), version).also {
            AppLog.i("Backup", "Made a backup: ${stores.size} settings stores, ${files.size} files, ${it.length / 1024} KB")
        }
    }

    suspend fun saveTo(uri: Uri) = withContext(Dispatchers.IO) {
        val text = create()
        val out = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("The file couldn't be opened")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /**
     * Replaces settings and on-phone data with the backup's. Passwords, tokens and keys already on the phone
     * are kept. The app must restart afterwards, since much of this is kept in memory.
     */
    suspend fun restoreFrom(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val text = app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw BackupException("The file couldn't be opened")
        val data = BackupRules.unpack(text)
        data.prefs.forEach { (name, values) ->
            val store = app.getSharedPreferences(name, Context.MODE_PRIVATE)
            val edit = store.edit()
            store.all.keys.filterNot { BackupRules.isSecret(name, it) }.forEach { edit.remove(it) }
            values.forEach { (key, saved) ->
                when (val value = BackupRules.decodeValue(saved)) {
                    is String -> edit.putString(key, value)
                    is Boolean -> edit.putBoolean(key, value)
                    is Int -> edit.putInt(key, value)
                    is Long -> edit.putLong(key, value)
                    is Float -> edit.putFloat(key, value)
                    is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
                    else -> AppLog.w("Backup", "Skipped a setting that couldn't be read: $name/$key")
                }
            }
            edit.commit()
        }
        data.files.forEach { (name, json) ->
            val file = File(app.filesDir, name)
            val tmp = File(app.filesDir, "$name.tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
        AppLog.i("Backup", "Restored a backup from ${Date(data.createdAt)}: ${data.prefs.size} settings stores, ${data.files.size} files")
        RestoreResult(data.prefs.size, data.files.size, data.createdAt)
    }

    /** Turns automatic backups on for [tree] (a folder picked in the system file picker), or off with null. */
    fun setFolder(tree: Uri?) {
        val resolver = app.contentResolver
        folder?.let { old ->
            if (old != tree) runCatching { resolver.releasePersistableUriPermission(old, RW) }
        }
        if (tree == null) {
            prefs.edit().remove(KEY_FOLDER).remove(KEY_LAST_ERROR).apply()
            WorkManager.getInstance(app).cancelUniqueWork(WORK)
            AppLog.i("Backup", "Automatic backups off")
            return
        }
        resolver.takePersistableUriPermission(tree, RW)
        prefs.edit().putString(KEY_FOLDER, tree.toString()).remove(KEY_LAST_ERROR).apply()
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        AppLog.i("Backup", "Automatic backups on, once a day")
    }

    /** Writes a backup to the chosen folder and deletes the oldest automatic ones beyond [BackupRules.KEEP]. */
    suspend fun backUpToFolder(): Boolean = withContext(Dispatchers.IO) {
        val tree = folder ?: return@withContext false
        try {
            val resolver = app.contentResolver
            val parentId = DocumentsContract.getTreeDocumentId(tree)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
            val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ROOT).format(Date())
            val doc = DocumentsContract.createDocument(resolver, parent, "application/json", BackupRules.autoName(stamp))
                ?: throw IOException("The folder can't be written to")
            val text = create()
            val out = resolver.openOutputStream(doc, "wt") ?: throw IOException("The backup file couldn't be opened")
            out.use { it.write(text.toByteArray(Charsets.UTF_8)) }

            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            val existing = HashMap<String, String>()
            resolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    existing[name] = id
                }
            }
            BackupRules.autoBackupsToDelete(existing.keys.toList()).forEach { name ->
                val id = existing[name] ?: return@forEach
                runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
                    .onFailure { AppLog.w("Backup", "Couldn't delete an old backup: ${it.message}") }
            }
            prefs.edit().putLong(KEY_LAST_AT, System.currentTimeMillis()).remove(KEY_LAST_ERROR).apply()
            AppLog.i("Backup", "Automatic backup saved")
            true
        } catch (e: Exception) {
            prefs.edit().putString(KEY_LAST_ERROR, e.message ?: e.javaClass.simpleName).apply()
            AppLog.w("Backup", "Automatic backup failed: ${e.message ?: e.javaClass.simpleName}")
            false
        }
    }

    private companion object {
        const val WORK = "daily-backup"
        const val KEY_FOLDER = "folder"
        const val KEY_LAST_AT = "last_at"
        const val KEY_LAST_ERROR = "last_error"
        const val RW = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

/** The daily automatic backup. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val backups = Backups(applicationContext)
        if (backups.folder == null) return Result.success()
        // A failure is shown in Settings → Backup; the next day's run tries again.
        backups.backUpToFolder()
        return Result.success()
    }
}
