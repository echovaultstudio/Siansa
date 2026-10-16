package app.musix.scrobble

import android.content.Context
import app.musix.data.JsonFile
import app.musix.data.SettingsRepository
import app.musix.data.Song
import app.musix.log.AppLog
import app.musix.net.LastFmApi
import app.musix.net.ListenBrainzApi
import app.musix.net.SubsonicApi
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
    private val subsonic: SubsonicApi,
    private val lastFm: LastFmApi,
    private val listenBrainz: ListenBrainzApi,
    private val scope: CoroutineScope,
) {
    private val pending = JsonFile(File(context.filesDir, "pending_scrobbles.json"), ListSerializer(PendingListen.serializer())) { emptyList() }
    private val flushLock = Mutex()

    fun nowPlaying(song: Song) {
        val s = settings.current
        scope.launch {
            if (s.scrobbleNavidrome) runCatching { subsonic.scrobble(song.id, submission = false) }
            if (s.lastFmReady) runCatching { lastFm.nowPlaying(lastFmKeys(), song) }
                .onFailure { AppLog.w("Scrobble", "Last.fm now playing failed: ${it.message}") }
            if (s.listenBrainzReady) runCatching { listenBrainz.nowPlaying(s.listenBrainzUrl, s.listenBrainzToken, song) }
                .onFailure { AppLog.w("Scrobble", "ListenBrainz now playing failed: ${it.message}") }
        }
    }

    /** Called when a song stops being the current one, with how long it was actually heard. */
    fun finished(song: Song, startedAtMs: Long, listenedMs: Long) {
        val durationMs = song.durationSec * 1000L
        val needed = minOf(durationMs / 2, 240_000L)
        if (durationMs in 1..30_000 || listenedMs < needed || listenedMs < 10_000) {
            AppLog.i("Scrobble", "Not counted: ${song.title} (heard ${listenedMs / 1000}s of ${song.durationSec}s)")
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
                NAVIDROME -> subsonic.scrobble(item.song.id, submission = true, timeMs = item.startedAtSec * 1000)
                LASTFM -> if (s.lastFmReady) lastFm.scrobble(lastFmKeys(), item.song, item.startedAtSec) else Unit
                LISTENBRAINZ -> if (s.listenBrainzReady) listenBrainz.listen(s.listenBrainzUrl, s.listenBrainzToken, item.song, item.startedAtSec) else Unit
                else -> Unit
            }
        }.onFailure { AppLog.w("Scrobble", "$target failed for ${item.song.title}: ${it.message}") }.isSuccess
    }

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
