package app.siansa.car

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.siansa.SiansaApp
import app.siansa.log.AppLog
import app.siansa.playback.loadCoverBitmap
import app.siansa.ui.SMALL_COVER
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

/**
 * Covers for Android Auto (issue #62), at content://<app>.covers/cover/<cover id>. The car can't use the server's
 * cover address: it carries the login, and the car may not reach a home server at all. So the phone loads the cover
 * (through its own image cache) and hands the car the picture. Only covers are served here, read-only.
 */
class CarCoverProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Covers can only be read")
        val context = context ?: throw FileNotFoundException("Not ready")
        val id = coverIdOf(uri) ?: throw FileNotFoundException("Not a cover")
        val file = fileFor(context, id)
        if (!file.exists()) {
            try {
                val graph = (context.applicationContext as SiansaApp).graph
                val bitmap = runBlocking { withTimeout(LOAD_TIMEOUT_MS) { loadCoverBitmap(context, Uri.parse(graph.source.coverUrl(id, SMALL_COVER))) } }
                val part = File(file.parentFile, file.name + ".part")
                part.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                part.renameTo(file)
                trim(file.parentFile!!)
            } catch (e: Exception) {
                AppLog.w("Car", "Cover $id for the car couldn't be loaded: ${e.message}")
                throw FileNotFoundException("Cover not loaded")
            }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val LOAD_TIMEOUT_MS = 15_000L
        /** How many covers are kept for the car; the oldest go first. */
        private const val KEEP = 400

        /** The address the car loads [coverId]'s picture from. */
        fun uriFor(context: Context, coverId: String): Uri =
            Uri.Builder().scheme("content").authority(context.packageName + ".covers").appendPath("cover").appendPath(coverId).build()

        /** The cover id in a cover address, or null when it isn't one. */
        fun coverIdOf(uri: Uri): String? {
            val path = uri.pathSegments
            return if (path.size == 2 && path[0] == "cover" && path[1].isNotBlank()) path[1] else null
        }

        private fun fileFor(context: Context, id: String): File {
            val dir = File(context.cacheDir, "car_covers").apply { mkdirs() }
            val name = MessageDigest.getInstance("SHA-1").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
            return File(dir, "$name.jpg")
        }

        private fun trim(dir: File) {
            val files = dir.listFiles { f -> f.name.endsWith(".jpg") } ?: return
            if (files.size <= KEEP) return
            files.sortedBy { it.lastModified() }.take(files.size - KEEP).forEach { it.delete() }
        }
    }
}
