package app.siansa.net

import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import app.siansa.data.Book
import app.siansa.data.BookProgress
import app.siansa.data.BookShelf
import app.siansa.data.Chapter
import app.siansa.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Audiobookshelf's own API (issue #521): login, libraries, the home shelves, books with chapters, progress.
 * One login for the whole app (the owner's decision), kept with AudioMuse's in the shared connections store.
 * Signs in with the API token when one is set, else the username and password; a session that expires is
 * renewed once and the call retried. Paths from the server's source (its API docs are out of date).
 */
class AudiobookshelfApi(private val http: OkHttpClient, private val settings: () -> AppSettings) {
    private val loginLock = Mutex()
    private var session: Pair<String, String>? = null // (login used, token)

    private fun base(): String = settings().audiobookshelfUrl.trimEnd('/')

    private suspend fun token(force: Boolean = false): String {
        val s = settings()
        if (s.audiobookshelfToken.isNotBlank()) return s.audiobookshelfToken
        return loginLock.withLock {
            val key = "${s.audiobookshelfUrl}|${s.audiobookshelfUser}|${s.audiobookshelfPassword.hashCode()}"
            session?.let { if (!force && it.first == key) return@withLock it.second }
            val body = buildJsonObject { put("username", s.audiobookshelfUser); put("password", s.audiobookshelfPassword) }
            val request = Request.Builder().url("${base()}/login").post(body.toString().toRequestBody(JSON)).build()
            val root = send(request, authed = false)
            val token = root.jsonObject.obj("user")?.str("token")
                ?: throw ApiException("Audiobookshelf accepted the login but sent no token")
            session = key to token
            token
        }
    }

    private class Unauthorized : Exception()

    /** A request with the login; signed in again once when the server says the session is gone. */
    private suspend fun call(path: String, body: JsonObject? = null, method: String = if (body == null) "GET" else "POST"): JsonElement {
        suspend fun attempt(force: Boolean): JsonElement {
            val t = token(force)
            val builder = Request.Builder().url("${base()}$path").header("Authorization", "Bearer $t")
            when (method) {
                "GET" -> builder.get()
                "DELETE" -> builder.delete()
                "PATCH" -> builder.patch((body ?: JsonObject(emptyMap())).toString().toRequestBody(JSON))
                else -> builder.post((body ?: JSON_EMPTY).toString().toRequestBody(JSON))
            }
            return send(builder.build(), authed = true)
        }
        return try {
            attempt(force = false)
        } catch (e: Unauthorized) {
            if (settings().audiobookshelfToken.isNotBlank()) throw ApiException("Audiobookshelf rejected the API token")
            attempt(force = true)
        }
    }

    private suspend fun send(request: Request, authed: Boolean): JsonElement = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401 && authed) throw Unauthorized()
            if (!response.isSuccessful) {
                AppLog.w("Audiobookshelf", "${request.method} ${request.url.encodedPath} failed: HTTP ${response.code}")
                throw ApiException(
                    when (response.code) {
                        401 -> "Audiobookshelf rejected the username or password"
                        403 -> "This Audiobookshelf user isn't allowed to do that"
                        404 -> "Not found on Audiobookshelf. Is the address its web address, without a path?"
                        else -> "Audiobookshelf answered HTTP ${response.code}"
                    },
                    response.code,
                )
            }
            if (text.isBlank()) JsonObject(emptyMap())
            else runCatching { AppJson.parseToJsonElement(text) }
                .getOrElse { throw ApiException("Audiobookshelf sent something that isn't JSON. Is the address its web address?") }
        }
    }

    /** The server's version, for the Test button. */
    suspend fun ping(): String {
        val me = call("/api/authorize", body = JsonObject(emptyMap())).jsonObject
        val name = me.obj("user")?.str("username").orEmpty()
        val version = me.obj("serverSettings")?.str("version") ?: me.str("version").orEmpty()
        return "Audiobookshelf $version as $name".trim()
    }

    /** Whether this login may ask the server to scan (#582): Audiobookshelf's admin and root users can. */
    suspend fun isAdmin(): Boolean = userMayScan(call("/api/authorize", body = JsonObject(emptyMap())).jsonObject)

    /**
     * Asks Audiobookshelf to scan every library for new and changed books (#582), as its web page's Scan does. The scan
     * runs on the server; this returns once it's asked, with how many libraries were asked. Admin users only.
     */
    suspend fun scanLibraries(): Int {
        val libs = libraries()
        libs.forEach { call("/api/libraries/${it.id}/scan", body = JsonObject(emptyMap())) }
        AppLog.i("Audiobookshelf", "Scan asked for ${libs.size} libraries")
        return libs.size
    }

    /** The libraries the user can see: books and podcasts. */
    suspend fun libraries(): List<AbsLibrary> = call("/api/libraries").jsonObject.arr("libraries").mapNotNull { l ->
        AbsLibrary(l.str("id") ?: return@mapNotNull null, l.str("name").orEmpty(), l.str("mediaType") == "podcast")
    }

    /** Audiobookshelf's own home shelves for a library: continue listening, recently added, series… */
    suspend fun shelves(libraryId: String): List<BookShelf> =
        (call("/api/libraries/$libraryId/personalized?limit=12") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.filter { it.str("type") == "book" || it.str("type") == "podcast" }
            ?.map { shelf ->
                BookShelf(
                    id = shelf.str("id").orEmpty(),
                    label = shelf.str("label").orEmpty(),
                    books = shelf.arr("entities").mapNotNull { parseBook(it) },
                )
            }.orEmpty()

    /** A page of a library's items, [sort] as Audiobookshelf names fields ("media.metadata.title", "addedAt"). */
    suspend fun items(libraryId: String, page: Int, limit: Int = 100, sort: String = "media.metadata.title", desc: Boolean = false): List<Book> {
        val root = call("/api/libraries/$libraryId/items?limit=$limit&page=$page&sort=$sort&desc=${if (desc) 1 else 0}").jsonObject
        return root.arr("results").mapNotNull { parseBook(it) }
    }

    /** Books or podcasts in one library whose title, author or narrator has [query] (issue #531). */
    suspend fun search(libraryId: String, query: String, limit: Int = 10): List<Book> =
        parseSearch(call("/api/libraries/$libraryId/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=$limit").jsonObject)

    /** One book with everything, chapters included. */
    suspend fun book(id: String): Book = parseBook(call("/api/items/$id?expanded=1").jsonObject)
        ?: throw ApiException("Book not found on Audiobookshelf")

    /** Books and podcasts the user is part-way through, newest first. */
    suspend fun inProgress(): List<Book> = call("/api/me/items-in-progress").jsonObject.arr("libraryItems").mapNotNull { parseBook(it) }

    /** Progress in one book, or null when it was never started. */
    suspend fun progress(bookId: String): BookProgress? = try {
        parseProgress(call("/api/me/progress/$bookId").jsonObject, bookId)
    } catch (e: ApiException) {
        if (e.code == 404) null else throw e
    }

    /** Every progress the user has, by book id, in one call (the user's own record). */
    suspend fun allProgress(): Map<String, BookProgress> =
        latestProgress(call("/api/me").jsonObject.arr("mediaProgress").mapNotNull { p -> p.str("libraryItemId")?.let { parseProgress(p, it) } })

    /** Sets where the listener is in a book, as the server's record. */
    suspend fun setProgress(bookId: String, currentSec: Double, durationSec: Double, finished: Boolean? = null) {
        val body = buildJsonObject {
            put("currentTime", currentSec)
            put("duration", durationSec)
            if (durationSec > 0) put("progress", (currentSec / durationSec).coerceIn(0.0, 1.0))
            finished?.let { put("isFinished", it) }
        }
        call("/api/me/progress/$bookId", body, method = "PATCH")
    }

    /**
     * Opens a playback session for [bookId]: Audiobookshelf's record of this listen, with the files to play (direct,
     * as the phone plays every format Siansa's decoder knows) and where the listener was.
     */
    suspend fun startSession(bookId: String, episodeId: String? = null): PlaySession {
        val body = buildJsonObject {
            put("deviceInfo", buildJsonObject {
                put("clientName", "Siansa")
                put("deviceId", "siansa-android")
                put("manufacturer", android.os.Build.MANUFACTURER)
                put("model", android.os.Build.MODEL)
                put("sdkVersion", android.os.Build.VERSION.SDK_INT)
            })
            put("mediaPlayer", "exoplayer")
            put("forceDirectPlay", true)
            put("supportedMimeTypes", kotlinx.serialization.json.buildJsonArray {
                listOf("audio/flac", "audio/mpeg", "audio/mp4", "audio/ogg", "audio/aac", "audio/webm", "audio/x-m4b").forEach { add(JsonPrimitive(it)) }
            })
        }
        val root = call("/api/items/$bookId/play" + (episodeId?.let { "/$it" } ?: ""), body).jsonObject
        val id = root.str("id") ?: throw ApiException("Audiobookshelf opened no playback session")
        val tracks = root.arr("audioTracks").mapIndexedNotNull { i, t ->
            PlayTrack(i, t.dbl("startOffset") ?: 0.0, t.dbl("duration") ?: 0.0, t.str("contentUrl") ?: return@mapIndexedNotNull null, t.str("mimeType"))
        }
        if (tracks.isEmpty()) throw ApiException("This book has no audio files Audiobookshelf can send")
        return PlaySession(id, root.dbl("startTime") ?: 0.0, root.dbl("duration") ?: 0.0, tracks)
    }

    /** Where the listener is in an open session, [listenedSec] since the last sync; [close] ends the session. */
    suspend fun syncSession(sessionId: String, currentSec: Double, listenedSec: Double, durationSec: Double, close: Boolean) {
        val body = buildJsonObject {
            put("currentTime", currentSec)
            put("timeListened", listenedSec)
            put("duration", durationSec)
        }
        call("/api/session/$sessionId/${if (close) "close" else "sync"}", body)
    }

    /** A track's address with the login, for the player ([PlayTrack.contentUrl] is a server path). */
    fun trackUrl(contentUrl: String): String {
        val t = settings().audiobookshelfToken.ifBlank { session?.second.orEmpty() }
        val url = (if (contentUrl.startsWith("http")) contentUrl else base() + contentUrl).toHttpUrl().newBuilder()
        if (t.isNotBlank()) url.addQueryParameter("token", t)
        return url.build().toString()
    }

    /** The listener's bookmarks in a book, by time. */
    suspend fun bookmarks(bookId: String): List<Bookmark> =
        (call("/api/me/bookmarks/$bookId") as? JsonArray)?.mapNotNull { it as? JsonObject }?.mapNotNull { b ->
            Bookmark(bookId, b.dbl("time") ?: return@mapNotNull null, b.str("title").orEmpty(), b.long("createdAt") ?: 0L)
        }?.sortedBy { it.timeSec }.orEmpty()

    suspend fun addBookmark(bookId: String, timeSec: Double, title: String): Bookmark {
        val body = buildJsonObject { put("time", timeSec.toInt()); put("title", title) }
        val b = call("/api/me/item/$bookId/bookmark", body).jsonObject
        return Bookmark(bookId, b.dbl("time") ?: timeSec, b.str("title") ?: title, b.long("createdAt") ?: System.currentTimeMillis())
    }

    suspend fun removeBookmark(bookId: String, timeSec: Double) {
        call("/api/me/item/$bookId/bookmark/${timeSec.toInt()}", method = "DELETE")
    }

    /** A podcast's episodes, newest first, with each one's progress when the user has any. */
    suspend fun episodes(podcastId: String): List<Episode> {
        val root = call("/api/items/$podcastId?expanded=1").jsonObject
        val media = root.obj("media") ?: return emptyList()
        return media.arr("episodes").mapNotNull { e ->
            val id = e.str("id") ?: return@mapNotNull null
            Episode(
                id = id,
                podcastId = podcastId,
                title = e.str("title") ?: "Episode",
                description = e.str("description"),
                publishedAt = e.long("publishedAt") ?: e.long("pubDate") ?: 0L,
                durationSec = (e.obj("audioFile")?.dbl("duration") ?: e.dbl("duration") ?: 0.0).toInt(),
                season = e.str("season"),
                number = e.str("episode"),
            )
        }.sortedByDescending { it.publishedAt }
    }

    /** Progress in a podcast episode, or null when never started. */
    suspend fun episodeProgress(podcastId: String, episodeId: String): BookProgress? = try {
        parseProgress(call("/api/me/progress/$podcastId/$episodeId").jsonObject, "$podcastId/$episodeId")
    } catch (e: ApiException) {
        if (e.code == 404) null else throw e
    }

    /** The address of a book's cover, for the cover loader ([Book.COVER_PREFIX] ids). */
    fun coverUrl(bookId: String, size: Int?): String {
        val address = "${base()}/api/items/$bookId/cover"
        // No Audiobookshelf address (its login removed, or never set): the address as it is, which the cover loader
        // fails on quietly and shows the placeholder, as for a song cover without a server. Parsing it crashed the
        // screen showing a book's cover, such as a book shortcut on Home.
        val url = address.toHttpUrlOrNull()?.newBuilder() ?: return address
        size?.let { url.addQueryParameter("width", it.toString()) }
        url.addQueryParameter("format", "webp")
        // Image loaders can't add a header: the token goes in the address, which Audiobookshelf accepts on GET.
        val s = settings()
        val t = s.audiobookshelfToken.ifBlank { session?.second.orEmpty() }
        if (t.isNotBlank()) url.addQueryParameter("token", t)
        return url.build().toString()
    }

    /** Makes sure there's a session, so [coverUrl] can carry its token. */
    suspend fun warmUp() { token() }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val JSON_EMPTY = JsonObject(emptyMap())

        /** A library item as a [Book]; null when it isn't a book or podcast. */
        /** A library search reply: its "book" and "podcast" lists, each entry's libraryItem, in that order. */
        fun parseSearch(root: JsonObject): List<Book> =
            (root.arr("book") + root.arr("podcast")).mapNotNull { hit -> hit.obj("libraryItem")?.let { parseBook(it) } }

        /** Whether an /api/authorize answer is an admin's: type "admin" or "root" (a plain "user" can't scan). */
        fun userMayScan(authorize: JsonObject): Boolean = authorize.obj("user")?.str("type") in setOf("admin", "root")

        fun parseBook(o: JsonObject): Book? {
            val id = o.str("id") ?: return null
            val mediaType = o.str("mediaType") ?: return null
            val media = o.obj("media") ?: return null
            val meta = media.obj("metadata") ?: return null
            val podcast = mediaType == "podcast"
            val authors = meta.arr("authors").mapNotNull { it.str("name") }.ifEmpty { listOfNotNull(meta.str("authorName") ?: meta.str("author")) }
            val narrators = strings(meta["narrators"]).ifEmpty { listOfNotNull(meta.str("narratorName")) }
            val series = meta.arr("series").firstOrNull()
            val chapters = media.arr("chapters").mapIndexedNotNull { i, c ->
                Chapter(i, c.str("title")?.takeIf { it.isNotBlank() } ?: "Chapter ${i + 1}", c.dbl("start") ?: return@mapIndexedNotNull null, c.dbl("end") ?: return@mapIndexedNotNull null)
            }
            return Book(
                id = id,
                title = meta.str("title") ?: "Untitled",
                subtitle = meta.str("subtitle"),
                authors = authors,
                narrators = narrators,
                series = series?.str("name") ?: meta.str("seriesName")?.substringBefore(" #")?.takeIf { it.isNotBlank() },
                sequence = series?.str("sequence"),
                year = meta.str("publishedYear")?.take(4)?.toIntOrNull(),
                description = meta.str("description"),
                genres = strings(meta["genres"]),
                durationSec = (media.dbl("duration") ?: 0.0).toInt(),
                chapters = chapters,
                libraryId = o.str("libraryId").orEmpty(),
                podcast = podcast,
                addedAt = o.long("addedAt") ?: 0L,
            )
        }

        fun parseProgress(p: JsonObject, bookId: String): BookProgress = BookProgress(
            bookId = bookId,
            currentSec = p.dbl("currentTime") ?: 0.0,
            durationSec = p.dbl("duration") ?: 0.0,
            fraction = p.dbl("progress") ?: 0.0,
            finished = p.bool("isFinished") == true,
            updatedAt = p.long("lastUpdate") ?: 0L,
            episodeId = p.str("episodeId"),
        )

        /**
         * One progress per book (beta #45): a podcast has one per episode, and the one that counts is the episode
         * last listened to that isn't finished, so "Continue" picks up where the listener was.
         */
        fun latestProgress(all: List<BookProgress>): Map<String, BookProgress> =
            all.groupBy { it.bookId }.mapValues { (_, list) ->
                list.filter { !it.finished }.maxByOrNull { it.updatedAt } ?: list.maxBy { it.updatedAt }
            }

        /** The episode a podcast plays from its ▶ (beta #45): the one in progress, else the newest. */
        fun episodeToPlay(episodes: List<Episode>, inProgressId: String?): Episode? =
            episodes.firstOrNull { it.id == inProgressId } ?: episodes.firstOrNull()

        private fun strings(e: JsonElement?): List<String> =
            (e as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf(String::isNotBlank) }.orEmpty()
    }
}

/** One of Audiobookshelf's libraries. */
data class AbsLibrary(val id: String, val name: String, val podcasts: Boolean)

/** An open playback session: the files to play one after another and where the listener was. */
data class PlaySession(val id: String, val startSec: Double, val durationSec: Double, val tracks: List<PlayTrack>)

/** A bookmark the listener set in a book, kept on the server (issue #521). */
data class Bookmark(val bookId: String, val timeSec: Double, val title: String, val createdAt: Long)

/** One episode of a podcast (issue #521). */
data class Episode(
    val id: String,
    val podcastId: String,
    val title: String,
    val description: String?,
    val publishedAt: Long,
    val durationSec: Int,
    val season: String?,
    val number: String?,
)

/** One of a book's files: where it starts in the whole book and how long it is, in seconds. */
data class PlayTrack(val index: Int, val startOffsetSec: Double, val durationSec: Double, val contentUrl: String, val mimeType: String?)
