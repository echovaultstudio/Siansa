package app.siansa.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import app.siansa.AppGraph
import app.siansa.data.Book
import app.siansa.data.BookProgress
import app.siansa.data.Chapter
import app.siansa.log.AppLog
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the book player is doing (issue #521), for the book page, the mini player and the player page. */
data class BookPlayerState(
    val book: Book? = null,
    /** Where in the whole book, in seconds. */
    val positionSec: Double = 0.0,
    val playing: Boolean = false,
    val speed: Float = 1f,
    /** Still loading or seeking. */
    val buffering: Boolean = false,
) {
    val chapter: Chapter? get() = book?.chapterAt(positionSec)
}

/**
 * Plays audiobooks and podcasts (issue #521), apart from the music player: no queue, shuffle, repeat, crossfade
 * or scrobbling. The audio runs in [PlaybackService] as a second session ([BookSession]), so Android's
 * notification and headphone buttons work; this is the app's side, a MediaController on that session.
 *
 * Progress is Audiobookshelf's record: read when a book opens, synced while it plays (every 15 s, on pause and at
 * the end), so Audiobookshelf's own apps show the same place.
 */
class BookPlayer(private val graph: AppGraph, private val context: Context = graph.app) {
    private val _state = MutableStateFlow(BookPlayerState())
    val state: StateFlow<BookPlayerState> = _state.asStateFlow()
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()
    private val handler = Handler(Looper.getMainLooper())
    private var sessionId: String? = null
    private var lastSynced = 0L
    private var listenedSinceSync = 0.0

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    /** Pauses when the chapter ends (issue #521): the book's sleep timer. Off again once it has. */
    var sleepAtChapterEnd: Boolean = false
        private set
    private var sleepChapter: Int? = null

    fun toggleSleepAtChapterEnd() {
        sleepAtChapterEnd = !sleepAtChapterEnd
        sleepChapter = if (sleepAtChapterEnd) _state.value.chapter?.index else null
        AppLog.i("BookPlayer", "Sleep at the end of the chapter: ${if (sleepAtChapterEnd) "on" else "off"}")
        _state.value = _state.value.copy()
    }

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            val s = _state.value
            if (s.playing) {
                listenedSinceSync += TICK_MS / 1000.0 * s.speed
                if (System.currentTimeMillis() - lastSynced >= SYNC_MS) sync(reason = "playing")
                val at = sleepChapter
                if (sleepAtChapterEnd && at != null && (s.chapter?.index ?: at) != at) {
                    AppLog.i("BookPlayer", "Chapter ${at + 1} ended: pausing for the night")
                    sleepAtChapterEnd = false
                    sleepChapter = null
                    pause()
                }
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token)
            .setConnectionHints(Bundle().apply { putBoolean(BookSession.HINT_BOOKS, true) })
            .buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull()
            if (c == null) { future = null; return@addListener }
            controller = c
            c.addListener(listener)
            handler.removeCallbacks(tick)
            handler.post(tick)
            refresh()
            val queued = pending.toList(); pending.clear(); queued.forEach { it(c) }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null && c.isConnected) { block(c); return }
        if (c != null) { c.removeListener(listener); controller = null; future = null }
        pending += block
        connect()
    }

    /** The state from the controller: the position across the book's files, playing, speed. */
    private fun refresh() {
        val c = controller ?: return
        val book = _state.value.book ?: return
        val sec = positionOf(c, book)
        _state.value = _state.value.copy(
            positionSec = sec,
            playing = c.isPlaying || (c.playWhenReady && c.playbackState == Player.STATE_BUFFERING),
            buffering = c.playbackState == Player.STATE_BUFFERING,
            speed = c.playbackParameters.speed,
        )
        if (c.playbackState == Player.STATE_ENDED && !_state.value.book?.let { it.id == endedId }.let { it == true }) {
            endedId = book.id
            AppLog.i("BookPlayer", "\"${book.title}\" finished")
            graph.books.noteProgress(progressAt(book, book.durationSec.toDouble(), finished = true))
            sync(reason = "finished", finished = true)
        }
    }
    private var endedId: String? = null

    /** Where in the whole book the controller is: the file's start offset plus the position in it. */
    private fun positionOf(c: MediaController, book: Book): Double {
        val item = c.currentMediaItem ?: return _state.value.positionSec
        val offset = item.mediaMetadata.extras?.getDouble(BookSession.EXTRA_OFFSET_SEC) ?: 0.0
        return offset + c.currentPosition / 1000.0
    }

    /** The episode playing, when it's a podcast (issue #521); null for a book. */
    private var episode: app.siansa.net.Episode? = null

    /** Plays one episode of a podcast: its own session on the server, its own progress. */
    fun playEpisode(podcast: Book, ep: app.siansa.net.Episode) {
        episode = ep
        val asBook = podcast.copy(title = ep.title, subtitle = podcast.title, durationSec = ep.durationSec, chapters = emptyList())
        graph.scope.launch {
            val p = runCatching { graph.audiobookshelf.episodeProgress(podcast.id, ep.id) }.getOrNull()
            handler.post { play(asBook, p?.currentSec?.takeIf { !p.finished } ?: 0.0, ep.id) }
        }
    }

    /** Starts [book] at [fromSec]: the first chapter, where the listener was, or a chapter they tapped. */
    fun play(book: Book, fromSec: Double, episodeId: String? = null) {
        AppLog.i("BookPlayer", "Play \"${book.title}\" from ${fromSec.toInt()} s")
        if (episodeId == null) episode = null
        endedId = null
        _state.value = BookPlayerState(book = book, positionSec = fromSec, playing = true, speed = graph.bookSpeeds.speedOf(book.id))
        graph.books.noteProgress(progressAt(book, fromSec))
        // A shortcut at the top of Home, like an album played (#548).
        val shortcut = app.siansa.data.bookShortcut(book, episode = episodeId != null)
        graph.scope.launch { graph.recentPlays.note(shortcut.route, shortcut.title, shortcut.cover) }
        // Music and a book never play at once (issue #521).
        graph.player.pause()
        graph.scope.launch {
            try {
                val session = graph.audiobookshelf.startSession(book.id, episodeId)
                sessionId = session.id
                lastSynced = System.currentTimeMillis()
                listenedSinceSync = 0.0
                val offline = graph.bookDownloads.isDownloaded(book.id)
                val items = session.tracks.map { t ->
                    // From the phone when the whole book is downloaded, else streamed (issue #521).
                    val uri = graph.bookDownloads.fileFor(book.id, t.index)?.let { android.net.Uri.fromFile(it).toString() }
                        ?: graph.audiobookshelf.trackUrl(t.contentUrl)
                    MediaItem.Builder()
                        .setMediaId("${BookSession.ID_PREFIX}${book.id}:${t.index}")
                        .setUri(uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(book.title)
                                .setArtist(book.author.ifBlank { book.narrator })
                                .setArtworkUri(android.net.Uri.parse(graph.audiobookshelf.coverUrl(book.id, 300)))
                                .setExtras(Bundle().apply {
                                    putDouble(BookSession.EXTRA_OFFSET_SEC, t.startOffsetSec)
                                    putDouble(BookSession.EXTRA_DURATION_SEC, t.durationSec)
                                    putString(BookSession.EXTRA_BOOK_ID, book.id)
                                })
                                .build(),
                        )
                        .build()
                }
                val (index, inFile) = BookSession.locate(session.tracks.map { it.startOffsetSec to it.durationSec }, fromSec)
                handler.post {
                    withController { c ->
                        c.setMediaItems(items, index, (inFile * 1000).toLong())
                        c.setPlaybackSpeed(_state.value.speed)
                        c.prepare()
                        c.play()
                    }
                }
                AppLog.i("BookPlayer", "Session ${session.id}: ${items.size} files${if (offline) " from the phone" else ""}, starting in file ${index + 1} at ${inFile.toInt()} s")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("BookPlayer", "Couldn't start \"${book.title}\": ${e.message}")
                _state.value = _state.value.copy(playing = false)
                graph.player.notice(e.message ?: "Couldn't play the book")
            }
        }
    }

    fun togglePlay() = withController { c -> if (c.isPlaying) { c.pause(); sync("paused") } else c.play() }
    fun pause() = withController { c -> if (c.isPlaying) { c.pause(); sync("paused") } }

    /** Jumps to [sec] in the whole book, across its files. */
    fun seekTo(sec: Double) {
        val book = _state.value.book ?: return
        withController { c ->
            val tracks = (0 until c.mediaItemCount).map { i ->
                val e = c.getMediaItemAt(i).mediaMetadata.extras
                (e?.getDouble(BookSession.EXTRA_OFFSET_SEC) ?: 0.0) to (e?.getDouble(BookSession.EXTRA_DURATION_SEC) ?: 0.0)
            }
            val target = sec.coerceIn(0.0, book.durationSec.toDouble())
            val (index, inFile) = BookSession.locate(tracks, target)
            c.seekTo(index, (inFile * 1000).toLong())
            _state.value = _state.value.copy(positionSec = target)
        }
    }

    fun skipBack() = seekTo(_state.value.positionSec - graph.settings.current.bookSkipBackSec)
    fun skipForward() = seekTo(_state.value.positionSec + graph.settings.current.bookSkipForwardSec)

    /** The previous chapter's start (or this one's, when more than a few seconds in), the next one's. */
    fun previousChapter() {
        val s = _state.value; val book = s.book ?: return
        val now = s.chapter ?: return seekTo(0.0)
        val target = if (s.positionSec - now.startSec > CHAPTER_RESTART_SEC) now else book.chapters.getOrNull(now.index - 1) ?: now
        seekTo(target.startSec)
    }
    fun nextChapter() {
        val s = _state.value; val book = s.book ?: return
        val next = s.chapter?.let { book.chapters.getOrNull(it.index + 1) } ?: return
        seekTo(next.startSec)
    }

    /** The speed for this book, remembered for it. */
    fun setSpeed(speed: Float) {
        val book = _state.value.book ?: return
        val s = speed.coerceIn(0.5f, 3f)
        graph.bookSpeeds.remember(book.id, s)
        withController { c -> c.setPlaybackSpeed(s) }
        _state.value = _state.value.copy(speed = s)
        AppLog.i("BookPlayer", "Speed ${"%.2f".format(s)}× for \"${book.title}\"")
    }

    /** Stops, syncing where it got to, and closes the server's session. */
    fun stop() {
        sync("stopped", close = true)
        withController { c -> c.stop(); c.clearMediaItems() }
        _state.value = BookPlayerState(speed = _state.value.speed)
    }

    private fun progressAt(book: Book, sec: Double, finished: Boolean = false) = BookProgress(
        bookId = book.id,
        currentSec = sec,
        durationSec = book.durationSec.toDouble(),
        fraction = if (book.durationSec > 0) (sec / book.durationSec).coerceIn(0.0, 1.0) else 0.0,
        finished = finished,
        updatedAt = System.currentTimeMillis(),
    )

    /** Tells Audiobookshelf where the listener is: the open session, else the progress record. */
    private fun sync(reason: String, finished: Boolean = false, close: Boolean = false) {
        val s = _state.value; val book = s.book ?: return
        val sec = s.positionSec
        val listened = listenedSinceSync
        listenedSinceSync = 0.0
        lastSynced = System.currentTimeMillis()
        val id = sessionId
        if (close) sessionId = null
        graph.books.noteProgress(progressAt(book, sec, finished))
        graph.scope.launch {
            try {
                if (id != null) graph.audiobookshelf.syncSession(id, sec, listened, book.durationSec.toDouble(), close)
                else graph.audiobookshelf.setProgress(book.id, sec, book.durationSec.toDouble(), finished)
                if (finished) graph.audiobookshelf.setProgress(book.id, sec, book.durationSec.toDouble(), true)
                AppLog.d("BookPlayer", "Synced \"${book.title}\" at ${sec.toInt()} s ($reason)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("BookPlayer", "Couldn't sync \"${book.title}\" ($reason): ${e.message}")
            }
        }
    }

    /** A bookmark at the current time, named for the chapter and time, kept on the server (issue #521). */
    fun bookmark(onDone: (String) -> Unit) {
        val s = _state.value; val book = s.book ?: return
        val title = (s.chapter?.let { "Chapter ${it.index + 1} · " } ?: "") + app.siansa.ui.formatDuration(s.positionSec.toLong())
        graph.scope.launch {
            try {
                graph.audiobookshelf.addBookmark(book.id, s.positionSec, title)
                AppLog.i("BookPlayer", "Bookmark \"$title\" in \"${book.title}\"")
                handler.post { onDone("Bookmarked $title") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("BookPlayer", "Couldn't bookmark: ${e.message}")
                handler.post { onDone(e.message ?: "Couldn't add the bookmark") }
            }
        }
    }

    /** Marks a book finished, or not: on the server and in the screens. */
    fun markFinished(book: Book, finished: Boolean) {
        val now = graph.books.progress.value[book.id]
        val sec = if (finished) book.durationSec.toDouble() else now?.currentSec?.takeIf { !now.finished } ?: 0.0
        graph.books.noteProgress(progressAt(book, sec, finished))
        graph.scope.launch {
            try {
                graph.audiobookshelf.setProgress(book.id, sec, book.durationSec.toDouble(), finished)
                AppLog.i("BookPlayer", "\"${book.title}\" marked ${if (finished) "finished" else "unfinished"}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("BookPlayer", "Couldn't mark \"${book.title}\": ${e.message}")
            }
        }
    }

    companion object {
        const val TICK_MS = 1000L
        /** How often Audiobookshelf hears where the listener is while a book plays. */
        const val SYNC_MS = 15_000L
        /** Previous chapter goes to this one's start when more than this far into it, like a CD player. */
        const val CHAPTER_RESTART_SEC = 3.0
    }
}
