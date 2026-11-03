package app.siansa.local

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import app.siansa.log.AppLog
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/** A file found in a picked folder (issue #352). */
data class FoundFile(val uri: String, val path: String, val size: Long, val modified: Long, val mime: String?, val folderPictures: List<Uri>)

/**
 * Walks a folder picked with Android's file picker (issue #352) and reads its music files. Through the picker's
 * folder permission only: no permission to read all of the phone's storage is needed.
 */
class LocalScanner(private val context: Context, private val coversDir: File) {
    private val resolver: ContentResolver get() = context.contentResolver

    /** Every music file in [tree], with its path inside it ("Album/01 Song.flac"). */
    fun list(tree: Uri, stop: () -> Boolean = { false }): List<FoundFile> {
        val out = ArrayList<FoundFile>()
        val root = DocumentsContract.getTreeDocumentId(tree)
        walk(tree, root, "", out, stop, depth = 0)
        return out
    }

    private fun walk(tree: Uri, docId: String, path: String, out: MutableList<FoundFile>, stop: () -> Boolean, depth: Int) {
        if (depth > MAX_DEPTH || stop()) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val folders = ArrayList<Pair<String, String>>()
        val files = ArrayList<FoundFile>()
        val pictures = ArrayList<Pair<String, Uri>>()
        runCatching {
            resolver.query(children, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    if (name.startsWith(".")) continue
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    when {
                        mime == DocumentsContract.Document.MIME_TYPE_DIR -> folders += id to name
                        isMusic(name, mime) -> files += FoundFile(
                            uri.toString(), path + name, if (c.isNull(3)) 0L else c.getLong(3), if (c.isNull(4)) 0L else c.getLong(4), mime, emptyList(),
                        )
                        isPicture(name) -> pictures += name.lowercase(Locale.ROOT) to uri
                    }
                }
            }
        }.onFailure { AppLog.w("Local", "Couldn't list the folder \"$path\": ${it.message}") }
        // The folder's own picture ("cover.jpg", "folder.jpg"…), best first, for files without one inside.
        val folderPictures = pictures.sortedBy { (name, _) -> PICTURE_NAMES.indexOfFirst { name.startsWith(it) }.let { if (it < 0) 99 else it } }.map { it.second }
        files.forEach { out += it.copy(folderPictures = folderPictures) }
        folders.sortedBy { it.second.lowercase(Locale.ROOT) }.forEach { (id, name) -> walk(tree, id, "$path$name/", out, stop, depth + 1) }
    }

    /** Reads [file]'s tags (its own and Android's), saving its album's picture as [coverFile] when there's none yet. */
    fun read(file: FoundFile, folder: String, coverFor: (LocalTrack) -> File): LocalTrack {
        val uri = Uri.parse(file.uri)
        val tags = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { stream -> TagReader.read(ChannelRead(stream.channel)) }
            }
        }.onFailure { AppLog.w("Local", "Couldn't read the tags of ${file.path}: ${it.message}") }.getOrNull().orEmpty()
        val retriever = MediaMetadataRetriever()
        var picture: ByteArray? = null
        val fallback = try {
            retriever.setDataSource(context, uri)
            fun meta(key: Int) = runCatching { retriever.extractMetadata(key) }.getOrNull()
            picture = runCatching { retriever.embeddedPicture }.getOrNull()
            RetrieverTags(
                title = meta(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = meta(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = meta(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = meta(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                track = meta(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                disc = meta(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                year = meta(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: meta(MediaMetadataRetriever.METADATA_KEY_DATE),
                genre = meta(MediaMetadataRetriever.METADATA_KEY_GENRE),
                durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                compilation = meta(MediaMetadataRetriever.METADATA_KEY_COMPILATION),
            )
        } catch (e: Exception) {
            AppLog.w("Local", "Android couldn't read ${file.path}: ${e.message}")
            RetrieverTags()
        } finally {
            runCatching { retriever.release() }
        }
        val track = LocalTags.track(file.uri, folder, file.path, file.size, file.modified, file.mime, tags, fallback)
        val cover = coverFor(track)
        val hasCover = cover.isFile || saveCover(picture, file.folderPictures, cover)
        return track.copy(hasCover = hasCover)
    }

    /** The album's picture, from the file or else its folder, made small enough for lists and now playing. */
    private fun saveCover(embedded: ByteArray?, folderPictures: List<Uri>, to: File): Boolean {
        val bytes = embedded ?: folderPictures.firstNotNullOfOrNull { uri ->
            runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        } ?: return false
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= COVER_PX) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
            coversDir.mkdirs()
            val temp = File(to.path + ".tmp")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            temp.renameTo(to)
        }.onFailure { AppLog.w("Local", "Couldn't keep a cover: ${it.message}") }.getOrDefault(false)
    }

    companion object {
        private const val MAX_DEPTH = 12
        private const val COVER_PX = 600
        private val PICTURE_NAMES = listOf("cover", "folder", "front", "album")
        val MUSIC_EXTENSIONS = setOf("mp3", "flac", "ogg", "oga", "opus", "m4a", "mp4", "aac", "alac", "wav", "wave", "wma", "aiff", "aif", "ape", "wv", "mka", "webm")

        fun isMusic(name: String, mime: String?): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (ext in setOf("m3u", "m3u8", "pls", "cue", "lrc", "txt", "nfo", "log")) return false
            return ext in MUSIC_EXTENSIONS || (mime != null && mime.startsWith("audio/"))
        }

        fun isPicture(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("jpg", "jpeg", "png", "webp")
    }
}
