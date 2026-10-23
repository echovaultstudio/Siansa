package app.musix.playback

import android.os.Handler
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.musix.data.AppSettings
import app.musix.data.Song
import app.musix.log.AppLog
import kotlin.math.abs
import kotlin.math.min

/**
 * Crossfades on the phone (issue #60), keeping the one main player behind the media session, so the queue,
 * lock screen, casting, sleep timer and scrobbling work as without crossfade.
 *
 * A few seconds before a fade, a second "tail" player gets the current song ready a little before the point where
 * the fade starts (from the song cache or a download, so it costs no new download in the usual case). Shortly
 * before the fade it starts playing, silent, and keeps itself in step with the main player by playing a touch
 * faster or slower (it can't be heard, so that changes nothing). When the fade starts, the tail is already
 * sounding in time with the main player and takes the song over in a short equal-power blend (issue #355: it
 * used to start at that moment, and its start-up delay made a short dip and a repeated sliver of sound), then
 * fades it out while the main player moves on to the next song and fades it in. Volumes combine with levelling (ReplayGain or measured
 * loudness) and the sleep timer: the main player's fade-in is [level], which the service multiplies in.
 *
 * The fade stops at once (the tail falls silent, the next song plays at full level) when you pause, skip or
 * seek, when another app takes the sound, or when the music moves to another device. Fades are skipped with
 * repeat-one, at the end of the queue, while the sleep timer finishes a song, and when the song couldn't be
 * made ready in time (for example a stream converted by Navidrome that can't jump to a point).
 */
@OptIn(UnstableApi::class)
internal class Crossfader(
    private val main: ExoPlayer,
    private val handler: Handler,
    private val newTail: () -> ExoPlayer,
    private val settings: () -> AppSettings,
    private val profiles: ProfileStore,
    /** The levelling volume of a song (ReplayGain or measured), as the main player would play it. */
    private val levelOf: (Song) -> Float,
    private val sleepLevel: () -> Float,
    /** False while casting or while the sleep timer lets the song end. */
    private val allowed: () -> Boolean,
    /** The main player's [level] changed: apply its volume again. */
    private val onLevel: () -> Unit,
) : Player.Listener {

    /** How far the main player has faded in its song: 1 except during a fade. */
    var level = 1f
        private set

    private enum class Phase { IDLE, READY, FADING }

    private var phase = Phase.IDLE
    private var tail: ExoPlayer? = null
    private var outgoing: String? = null
    private var plan: FadePlan? = null
    private var startAt = 0L
    private var incomingIndex = C.INDEX_UNSET
    private var tailBase = 1f
    private var startedAt = 0L
    private var switched = false
    private var expectSeek = false
    /** Where the tail starts, a little before [startAt]; once the main player gets there the tail plays, silent. */
    private var primeAt = 0L
    /** The tail is playing silently, keeping in step with the main player. */
    private var primed = false
    private var lastCoarseAlign = 0L
    /** A song whose fade was given up or isn't wanted, until the queue moves on or changes. */
    private var skipped: String? = null

    fun start() {
        main.addListener(this)
        poke()
    }

    fun release() {
        handler.removeCallbacks(watch)
        handler.removeCallbacks(fadeTick)
        main.removeListener(this)
        releaseTail()
        phase = Phase.IDLE
    }

    /** Stops a fade or a fade being prepared, leaving the main player at full level. */
    fun cancel(why: String) {
        when (phase) {
            Phase.FADING -> {
                AppLog.i("Crossfade", "Fade stopped: $why")
                finish()
            }
            Phase.READY -> disarm()
            Phase.IDLE -> Unit
        }
    }

    private fun poke() {
        handler.removeCallbacks(watch)
        handler.post(watch)
    }

    // ---- Watching for the next fade: once a second while playing, exactly on time near the start ----

    private val watch = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            val delay = look()
            if (delay > 0) handler.postDelayed(this, delay)
        }
    }

    /** Gets the next fade ready and starts it on time. Returns when to look again (ms), or 0 to stop looking. */
    private fun look(): Long {
        if (phase == Phase.FADING || !main.isPlaying) return 0
        val s = settings()
        val item = main.currentMediaItem ?: return 0
        val duration = main.duration
        val nextIndex = main.nextMediaItemIndex
        val unwanted = s.crossfade == CrossfadeMode.OFF || !allowed() || main.repeatMode == Player.REPEAT_MODE_ONE ||
            duration == C.TIME_UNSET || duration <= 0 || nextIndex == C.INDEX_UNSET || item.mediaId == skipped
        if (unwanted) {
            if (phase == Phase.READY) disarm()
            return LOOK_MS
        }
        val position = main.currentPosition
        val maxMs = s.crossfadeSeconds * 1000L
        if (duration - position > maxMs + PREPARE_MS) {
            if (phase == Phase.READY) disarm()
            return LOOK_MS
        }
        if (phase != Phase.READY || outgoing != item.mediaId) {
            disarm()
            val current = item.toSong()
            val next = main.getMediaItemAt(nextIndex).toSong()
            val planned = Crossfade.plan(
                mode = s.crossfade,
                maxMs = maxMs,
                current = current,
                next = next,
                currentDurationMs = duration,
                nextDurationMs = next.durationSec * 1000L,
                currentProfile = profiles.cached(current.id),
                nextProfile = profiles.cached(next.id),
            )
            if (planned == null) {
                val why = Crossfade.whyNoFade(s.crossfade, maxMs, current, next, profiles.cached(current.id), profiles.cached(next.id))
                    ?: "one of them is too short to share a fade"
                AppLog.d("Crossfade", "No fade from ${current.id} into ${next.id}: $why")
                skipped = item.mediaId
                return LOOK_MS
            }
            val at = duration - planned.overlapMs
            if (at - position < MIN_PREPARE_MS) {
                AppLog.d("Crossfade", "Too close to the end of ${current.id} to get a fade ready")
                skipped = item.mediaId
                return LOOK_MS
            }
            arm(item, planned, at, primeFrom(position, at))
        }
        val speed = main.playbackParameters.speed.takeIf { it > 0f } ?: 1f
        if (!primed && position >= primeAt - ON_TIME_MS) prime()
        if (primed) align(speed)
        val until = ((startAt - position) / speed).toLong()
        if (until <= ON_TIME_MS) {
            begin()
            return 0
        }
        // While primed, check the step often; before that, wake when it's time to prime.
        val untilPrime = if (primed) ALIGN_MS else ((primeAt - position) / speed).toLong().coerceAtLeast(ON_TIME_MS)
        return minOf(LOOK_MS, until, untilPrime)
    }

    /** Where the tail should start: [PREROLL_MS] before the fade, but never before the main player is there. */
    private fun primeFrom(position: Long, at: Long): Long = (at - PREROLL_MS).coerceAtLeast(position + MIN_PREPARE_MS / 2).coerceAtMost(at)

    /** Starts the tail playing, silent, so it's sounding and in step when the fade begins. */
    private fun prime() {
        val t = tail ?: return
        if (t.playbackState != Player.STATE_READY) return
        t.volume = 0f
        t.play()
        primed = true
        AppLog.d("Crossfade", "Tail playing silently from ${primeAt} ms, ${startAt - primeAt} ms before the fade")
    }

    /**
     * Keeps the silent tail in step with the main player: far off (more than [COARSE_MS]), it jumps to just ahead
     * of it; otherwise it plays a few percent faster or slower until they match ([tailSpeed]).
     */
    private fun align(mainSpeed: Float) {
        val t = tail ?: return
        if (!t.isPlaying) return
        val behind = main.currentPosition - t.currentPosition
        val now = SystemClock.elapsedRealtime()
        if (abs(behind) > COARSE_MS && now - lastCoarseAlign > 600) {
            lastCoarseAlign = now
            t.seekTo(main.currentPosition + COARSE_LEAD_MS)
            return
        }
        t.setPlaybackSpeed(mainSpeed * Crossfade.tailSpeed(behind))
    }

    private fun arm(item: MediaItem, planned: FadePlan, at: Long, from: Long) {
        val t = tail ?: newTail().also { tail = it }
        t.volume = 0f
        t.playWhenReady = false
        t.setMediaItem(item, from)
        t.prepare()
        outgoing = item.mediaId
        plan = planned
        startAt = at
        primeAt = from
        primed = false
        phase = Phase.READY
        AppLog.i("Crossfade", "Getting the fade out of ${item.mediaId} ready (${planned.why})")
    }

    private fun disarm() {
        if (phase == Phase.FADING) return
        primed = false
        releaseTail()
        outgoing = null
        plan = null
        phase = Phase.IDLE
    }

    private fun releaseTail() {
        tail?.let { t ->
            runCatching { t.stop() }
            t.release()
        }
        tail = null
    }

    // ---- The fade ----

    private fun begin() {
        val t = tail
        val planned = plan
        val item = main.currentMediaItem
        val nextIndex = main.nextMediaItemIndex
        if (t == null || planned == null || item == null || item.mediaId != outgoing || nextIndex == C.INDEX_UNSET) {
            disarm()
            return
        }
        // Only a tail that can really carry on from the same point; a stream that can't jump would start over.
        // One that didn't get to play silently in time jumps to where the main player is, as before #355.
        if (t.playbackState == Player.STATE_READY && t.isCurrentMediaItemSeekable && !(primed && t.isPlaying)) {
            t.seekTo(main.currentPosition)
        }
        val ready = t.playbackState == Player.STATE_READY && t.isCurrentMediaItemSeekable && abs(t.currentPosition - main.currentPosition) < 1_500
        if (!ready) {
            AppLog.w(
                "Crossfade",
                "Song ${item.mediaId} wasn't ready to fade in time (state ${t.playbackState}, seekable ${t.isCurrentMediaItemSeekable}); playing on without a crossfade",
            )
            skipped = item.mediaId
            disarm()
            return
        }
        tailBase = levelOf(item.toSong())
        incomingIndex = nextIndex
        startedAt = SystemClock.elapsedRealtime()
        switched = false
        phase = Phase.FADING
        val wasPrimed = primed && t.isPlaying
        val drift = t.currentPosition - main.currentPosition
        t.setPlaybackSpeed(main.playbackParameters.speed.takeIf { it > 0f } ?: 1f)
        t.volume = 0f
        if (!t.isPlaying) t.play()
        AppLog.i(
            "Crossfade",
            "Fading ${item.mediaId} into ${main.getMediaItemAt(nextIndex).mediaId}: ${planned.why} · " +
                if (wasPrimed) "tail in step (${if (drift >= 0) "+" else ""}$drift ms)" else "tail started now (not primed in time)",
        )
        handler.post(fadeTick)
    }

    private val fadeTick = object : Runnable {
        override fun run() {
            if (phase != Phase.FADING) return
            val t = tail
            val planned = plan
            if (t == null || planned == null) {
                finish()
                return
            }
            val sleep = sleepLevel()
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            if (!switched) {
                // The rest of the song moves from the main player to the tail in a short equal-power blend of the
                // two copies; they play in step, so it's heard as one.
                val h = (elapsed.toFloat() / HANDOVER_MS).coerceIn(0f, 1f)
                t.volume = tailBase * sleep * Crossfade.fadeIn(h)
                level = Crossfade.fadeOut(h)
                onLevel()
                if (elapsed >= HANDOVER_MS && t.isPlaying) {
                    switched = true
                    level = 0f
                    onLevel()
                    expectSeek = true
                    main.seekTo(incomingIndex, 0)
                } else if (elapsed > HANDOVER_MS + 1_000) {
                    AppLog.w("Crossfade", "The fade-out didn't start playing; carrying on without a crossfade")
                    skipped = outgoing
                    finish()
                    return
                }
            } else {
                val out = ((t.currentPosition - startAt).toFloat() / planned.overlapMs).coerceIn(0f, 1f)
                val tailDone = out >= 1f || t.playbackState == Player.STATE_ENDED || t.playbackState == Player.STATE_IDLE
                t.volume = if (tailDone) 0f else tailBase * sleep * Crossfade.fadeOut(out)
                val heard = if (main.currentMediaItemIndex == incomingIndex) main.currentPosition else 0L
                val fadedIn = (heard.toFloat() / planned.fadeInMs).coerceIn(0f, 1f)
                level = Crossfade.fadeIn(fadedIn)
                onLevel()
                // Done when both are; or anyway a while after the old song ended, if the new one is stuck loading.
                if (tailDone && (fadedIn >= 1f || elapsed > planned.overlapMs + GIVE_UP_MS)) {
                    finish()
                    return
                }
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun finish() {
        handler.removeCallbacks(fadeTick)
        phase = Phase.IDLE
        primed = false
        releaseTail()
        outgoing = null
        plan = null
        expectSeek = false
        if (level != 1f) {
            level = 1f
            onLevel()
        }
        poke()
    }

    // ---- What the main player does ----

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason != Player.DISCONTINUITY_REASON_SEEK && reason != Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) return
        if (phase == Phase.FADING && expectSeek && newPosition.mediaItemIndex == incomingIndex) {
            expectSeek = false
            return
        }
        when (phase) {
            Phase.FADING -> cancel("you skipped or moved in the song")
            Phase.READY -> disarm()
            Phase.IDLE -> Unit
        }
        poke()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) cancel("paused") else poke()
    }

    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
        if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) cancel("another app took the sound")
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) poke()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (phase == Phase.FADING) return
        skipped = null
        if (phase == Phase.READY && mediaItem?.mediaId != outgoing) disarm()
        poke()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED || phase == Phase.FADING) return
        // The next song may be another one now: plan again.
        skipped = null
        disarm()
        poke()
    }

    override fun onRepeatModeChanged(repeatMode: Int) = replan()

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = replan()

    private fun replan() {
        if (phase == Phase.FADING) return
        skipped = null
        disarm()
        poke()
    }

    companion object {
        /** How often to look while a song plays. */
        const val LOOK_MS = 1_000L
        /** The tail is made ready this long before the longest possible fade. */
        const val PREPARE_MS = 8_000L
        /** A fade needs at least this long to get ready, or that song plays on without one. */
        const val MIN_PREPARE_MS = 1_500L
        /** Starting this close to the planned moment is on time. */
        const val ON_TIME_MS = 15L
        /** The blend as the tail takes the song over from the main player. */
        const val HANDOVER_MS = 120L
        /** The tail starts playing, silent, this long before the fade, to be sounding and in step by then. */
        const val PREROLL_MS = 2_000L
        /** While the tail plays silently, how often its step is checked. */
        const val ALIGN_MS = 50L
        /** Further apart than this, the tail jumps to the main player rather than catching up by speed. */
        const val COARSE_MS = 250L
        /** How far ahead of the main player the tail jumps, as it takes a moment to start again. */
        const val COARSE_LEAD_MS = 100L
        /** Volume steps during a fade: 50 a second. */
        const val TICK_MS = 20L
        /** How long after the old song has faded out to wait for the new one to start fading in. */
        const val GIVE_UP_MS = 10_000L
    }
}
