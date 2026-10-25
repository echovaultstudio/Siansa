package app.siansa.scrobble

import android.content.Context
import app.siansa.data.JsonFile
import app.siansa.data.SettingsRepository
import app.siansa.data.Song
import app.siansa.data.Users
import app.siansa.log.AppLog
import app.siansa.net.LastFmApi
import app.siansa.net.ListenBrainzApi
import app.siansa.net.MusicSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** A finished listen that still has to reach one or more services (kept across restarts). */
@Serializable
data class PendingListen(val song: Song, val startedAtSec: Long, val targets: Set<String>)

/**
 * Sends "now playing" and finished listens to Navidrome, Last.fm and ListenBrainz/Koito.
 * A listen counts after half the song or four minutes, for songs longer than 30 seconds (Last.fm's rule).
 * Listens that fail (offline, server down) are kept and retried with the next one.
 */
class Scrobbler(
    context: Context,
    private val settings: SettingsRepository,
    private val source: MusicSource,
    private val lastFm: LastFmApi,
    private val listenBrainz: ListenBrainzApi,
    private val scope: CoroutineScope,
    /** Whose library a song is from: the user in use, or another user whose music shows with theirs (issue #356). */
    private val sourceOf: suspend (songId: String) -> Int = { Users.active },
) {
    private val pending = JsonFile(File(context.filesDir, Users.scoped("pending_scrobbles.json")), ListSerializer(PendingListen.serializer())) { emptyList() }
    private val flushLock = Mutex()

    fun nowPlaying(song: Song) {
        val s = settings.current
        scope.launch {
            if (!scrobbles(song, s.scrobbleOffUsers)) return@launch
            if (s.scrobbleNavidrome) runCatching { source.scrobble(song.id, submission = false) }
            if (s.lastFmReady) runCatching { lastFm.nowPlaying(lastFmKeys(), song) }
                .onFailure { AppLog.w("Scrobble", "Last.fm now playing failed: ${it.message}") }
            if (s.listenBrainzReady) runCatching { listenBrainz.nowPlaying(s.listenBrainzUrl, s.listenBrainzToken, song) }
                .onFailure { AppLog.w("Scrobble", "ListenBrainz now playing failed: ${it.message}") }
        }
    }

    /**
     * Where the playing song is (play, pause, and every so often), for servers that follow playback as it
     * happens (Emby, issue #113). Goes with "Scrobble to the server": it's off, nothing is sent.
     */
    fun progress(song: Song, positionMs: Long, paused: Boolean) = timeline(song) { source.playbackProgress(song.id, positionMs, paused) }

    /** The song stopped being the current one, [positionMs] in; for servers with a playback timeline. */
    fun stopped(song: Song, positionMs: Long) = timeline(song) { source.playbackStopped(song.id, positionMs) }

    private fun timeline(song: Song, send: suspend () -> Unit) {
        val s = settings.current
        if (!s.scrobbleNavidrome) return
        scope.launch {
            if (!scrobbles(song, s.scrobbleOffUsers)) return@launch
            runCatching { send() }.onFailure { AppLog.d("Scrobble", "Playback timeline for ${song.id} not sent: ${it.message}") }
        }
    }

    /** Called when a song stops being the current one, with how long it was actually heard. */
    fun finished(song: Song, startedAtMs: Long, listenedMs: Long) {
        val durationMs = song.durationSec * 1000L
        val percent = settings.current.scrobblePercent
        val needed = scrobbleNeededMs(durationMs, percent)
        if (durationMs in 1..30_000 || listenedMs < needed || listenedMs < 10_000) {
            AppLog.i("Scrobble", "Not counted: ${song.title} (heard ${listenedMs / 1000}s of ${song.durationSec}s, needs $percent% or 4 min)")
            return
        }
        val s = settings.current
        val targets = buildSet {
            if (s.scrobbleNavidrome) add(NAVIDROME)
            if (s.lastFmReady) add(LASTFM)
            if (s.listenBrainzReady) add(LISTENBRAINZ)
        }
        if (targets.isEmpty()) return
        scope.launch {
            if (!scrobbles(song, s.scrobbleOffUsers)) {
                AppLog.i("Scrobble", "Not sent: ${song.title} is from user ${sourceOf(song.id) + 1}, whose songs aren't scrobbled")
                return@launch
            }
            pending.update { it + PendingListen(song, startedAtMs / 1000, targets) }
            flush()
        }
    }

    /** Sends every waiting listen, oldest first; whatever still fails stays for next time. */
    suspend fun flush() = flushLock.withLock {
        val queue = pending.read()
        if (queue.isEmpty()) return@withLock
        val left = ArrayList<PendingListen>()
        for (item in queue) {
            val failed = item.targets.filterNot { target -> send(target, item) }.toSet()
            if (failed.isNotEmpty()) left += item.copy(targets = failed)
        }
        // Keep at most a few thousand, so a long offline stretch can't grow the file without limit.
        pending.update { current -> (left + current.drop(queue.size)).takeLast(5000) }
        AppLog.i("Scrobble", "Sent ${queue.size - left.size} listen(s), ${left.size} waiting")
    }

    private suspend fun send(target: String, item: PendingListen): Boolean {
        val s = settings.current
        return runCatching {
            when (target) {
                NAVIDROME -> source.scrobble(item.song.id, submission = true, timeMs = item.startedAtSec * 1000)
                LASTFM -> if (s.lastFmReady) lastFm.scrobble(lastFmKeys(), item.song, item.startedAtSec) else Unit
                LISTENBRAINZ -> if (s.listenBrainzReady) listenBrainz.listen(s.listenBrainzUrl, s.listenBrainzToken, item.song, item.startedAtSec) else Unit
                else -> Unit
            }
        }.onFailure {
            val name = if (target == NAVIDROME) "Your server (${source.kind.name.lowercase()})" else target
            AppLog.w("Scrobble", "$name failed for ${item.song.title}: ${it.message}")
        }.isSuccess
    }

    /** Whether [song] may be scrobbled: not from a user whose songs are set not to be (issue #356). */
    private suspend fun scrobbles(song: Song, off: Set<Int>): Boolean =
        off.isEmpty() || scrobblesFrom(runCatching { sourceOf(song.id) }.getOrDefault(Users.active), off)

    private fun lastFmKeys(): LastFmApi.Keys {
        val s = settings.current
        return LastFmApi.Keys(s.lastFmApiKey, s.lastFmSecret, s.lastFmSession)
    }

    companion object {
        const val NAVIDROME = "navidrome"
        const val LASTFM = "lastfm"
        const val LISTENBRAINZ = "listenbrainz"
    }
}

/** The choices for how much of a song must be heard before it counts (issue #270). */
val SCROBBLE_PERCENTS = listOf(25, 40, 50, 60, 75, 90)

/**
 * How long a song of [durationMs] must be heard to be scrobbled: [percent] of it (10–100), or four minutes,
 * whichever comes first, as Last.fm asks.
 */
fun scrobbleNeededMs(durationMs: Long, percent: Int): Long =
    minOf(durationMs * percent.coerceIn(10, 100) / 100, 240_000L)

/** Whether songs from [user]'s library are scrobbled, given the users set not to be (issue #356). */
fun scrobblesFrom(user: Int, off: Set<Int>): Boolean = user !in off
