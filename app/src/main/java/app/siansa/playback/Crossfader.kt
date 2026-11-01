package app.siansa.playback

import android.os.Handler
import android.os.SystemClock
import androidx.annotation.OptIn
import android.media.audiofx.PresetReverb
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.siansa.data.AppSettings
import app.siansa.data.Song
import app.siansa.log.AppLog
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

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
 *
 * A beat-matched blend (issue #646, [BeatMatch]) starts on a bar of the old song and the new song on its first
 * downbeat, at the speed that matches the old tempo. Through the blend the new song is kept in step: a jump
 * when it landed well off (the seek takes a moment), tiny speed nudges otherwise. Afterwards it eases back to
 * its own speed over [BeatMatch.EASE_MS], too slowly to hear; a skip or a seek ends that at once.
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
    /** A DJ radio or DJ keep-playing is on: where beat-matched blends apply in their default setting. */
    private val inDjRadio: () -> Boolean = { false },
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
    /** The fade is waiting for a tail that is still loading (logged once, issue #767). */
    private var waitingForTail = false
    private var lastCoarseAlign = 0L
    /** A song whose fade was given up or isn't wanted, until the queue moves on or changes. */
    private var skipped: String? = null
    /** The main player's own speed before a beat-matched blend changed it. */
    private var baseSpeed = 1f
    /** During a beat-matched blend: how often the next song was jumped into step (at most [MAX_JUMPS]). */
    private var jumps = 0
    /**
     * How long getting the next song to a point takes on this phone (issue #747), learned from each jump in a blend
     * and kept while the app runs; and the lead of the seek in flight, until its first settled position is measured.
     */
    private var seekLag = BeatMatch.DEFAULT_SEEK_LAG_MS
    private var seekLead: Long? = null
    /** Where the last jump was sent; the player reports that spot until the jump lands, so it isn't a position yet. */
    private var seekTarget = -1L
    /** How far apart the two songs' beats were through this blend (issue #744). */
    private var stepStats = BeatMatch.StepStats()
    /** The step error smoothed over the last few readings, which decides jumps and nudges (issue #759). */
    private var smoothed = BeatMatch.SmoothedError()
    private var switchedAt = 0L
    /** The blend got ready knew the next song's fade shape; without it, it's planned again when that arrives. */
    private var plannedWithNext = true
    /** How often the silent tail jumped to catch up before this blend. */
    private var tailJumps = 0
    /** After a beat-matched blend: the matched speed being eased back from, and since when. */
    private var easingFrom = 0f
    private var easingSince = 0L
    private var easeChanges = 0
    /** The last 4-bar stage of a beat-matched blend logged. */
    private var loggedStage = 0
    /** When the silent tail's catch-up was last logged. */
    private var tailLoggedAt = 0L
    /**
     * The echo at the end of a beat-matched blend (issue #759): a hall reverb on the phone's output mix that the
     * tail is sent to, more and more over its last bar, as a DJ echoes a track out; then the tail is paused and the
     * hall rings until [echoUntil].
     */
    private var reverb: PresetReverb? = null
    private var echoSend = -1f
    private var echoUntil = 0L

    fun start() {
        main.addListener(this)
        poke()
    }

    fun release() {
        handler.removeCallbacks(watch)
        handler.removeCallbacks(fadeTick)
        stopEasing()
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
        val beatMatch = s.crossfade == CrossfadeMode.SMART && s.beatMatch.appliesTo(inDjRadio())
        // The end a smart fade leaves out (silence, a long fade-out; issue #628) comes off the time left.
        val cut = if (s.crossfade == CrossfadeMode.SMART) Crossfade.endCut(profiles.cached(item.mediaId), duration, maxMs).first else 0L
        // A beat-matched blend has its own length in bars (issue #759), often longer than the crossfade length.
        val lookMs = if (beatMatch) max(maxMs, profiles.cached(item.mediaId)?.outro?.let { BeatMatch.maxMsFor(it, s.blendBars) } ?: 0L) else maxMs
        if (duration - cut - position > lookMs + PREPARE_MS) {
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
                beatMatch = beatMatch,
                blendBars = s.blendBars,
            )
            if (planned == null) {
                val why = Crossfade.whyNoFade(s.crossfade, maxMs, current, next, profiles.cached(current.id), profiles.cached(next.id), beatMatch)
                    ?: "one of them is too short to share a fade"
                AppLog.d("Crossfade", "No fade from ${current.id} into ${next.id}: $why")
                skipped = item.mediaId
                return LOOK_MS
            }
            val at = duration - planned.cutMs - planned.overlapMs
            if (at - position < MIN_PREPARE_MS) {
                AppLog.d("Crossfade", "Too close to the end of ${current.id} to get a fade ready")
                skipped = item.mediaId
                return LOOK_MS
            }
            plannedWithNext = profiles.cached(next.id) != null
            arm(item, planned, at, primeFrom(position, at))
        }
        val speed = main.playbackParameters.speed.takeIf { it > 0f } ?: 1f
        // A beat-matched blend lands the new song on the bar: its tail must be in step, so it's primed early.
        if (!primed && position >= primeAt - ON_TIME_MS) prime()
        if (primed) align(speed)
        val until = ((startAt - position) / speed).toLong()
        if (until <= ON_TIME_MS) {
            // A primed tail takes over only once it's in step, or a while later anyway (issue #759).
            val t = tail
            val wait = min(Crossfade.HANDOVER_WAIT_MS, plan?.beat?.outgoing?.beatLengthMs?.toLong() ?: Crossfade.HANDOVER_WAIT_MS)
            // A tail still loading (it just jumped to catch up) gets a moment too, or the blend would be called off
            // and the next song come in with a cut (issue #767).
            if (t != null) {
                val loading = t.playbackState == Player.STATE_BUFFERING
                if (Crossfade.waitForTail(primed, loading, t.isPlaying, until, main.currentPosition - t.currentPosition, wait)) {
                    if (loading && !waitingForTail) {
                        waitingForTail = true
                        AppLog.d("Crossfade", "Waiting for the tail to load (it jumped to catch up)")
                    }
                    return ALIGN_MS
                }
            }
            waitingForTail = false
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
            tailJumps++
            t.seekTo(main.currentPosition + COARSE_LEAD_MS)
            return
        }
        t.setPlaybackSpeed(mainSpeed * Crossfade.tailSpeed(behind))
        // Twice a second, how the catch-up goes (issue #796: the tail was ~160 ms behind at three handovers).
        if (abs(behind) > Crossfade.HANDOVER_STEP_MS && now - tailLoggedAt >= 500) {
            tailLoggedAt = now
            AppLog.d("Crossfade", "Tail catching up: $behind ms behind, playing at %.2f×".format(java.util.Locale.ROOT, t.playbackParameters.speed))
        }
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
        tailJumps = 0
        phase = Phase.READY
        val length = main.duration.takeIf { it > 0 }
        val where = length?.let { " · starts at ${clock(at)} of ${clock(it)}" }.orEmpty()
        AppLog.i("Crossfade", "Getting the fade out of ${item.mediaId} ready (${planned.why})$where" + (planned.beatNote?.let { " · $it" } ?: ""))
    }

    private fun disarm() {
        if (phase == Phase.FADING) return
        primed = false
        waitingForTail = false
        releaseTail()
        outgoing = null
        plan = null
        phase = Phase.IDLE
    }

    private fun releaseTail() {
        closeEcho()
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
        jumps = 0
        stepStats = BeatMatch.StepStats()
        smoothed = BeatMatch.SmoothedError()
        loggedStage = 0
        seekLead = null
        seekTarget = -1L
        stopEasing()
        baseSpeed = main.playbackParameters.speed.takeIf { it > 0f } ?: 1f
        phase = Phase.FADING
        if (planned.beat != null && settings().echoTail) openEcho(t)
        val wasPrimed = primed && t.isPlaying
        val drift = t.currentPosition - main.currentPosition
        // Out of step at the handover, the two copies blend apart for a moment, heard as a smear or a wobble
        // (issue #796): what the tail was doing, to see next time why it didn't catch up.
        val catchUp = if (wasPrimed && abs(drift) > Crossfade.HANDOVER_STEP_MS) {
            " · not caught up: playing at %.2f×, %d jump%s, %s".format(
                java.util.Locale.ROOT, t.playbackParameters.speed, tailJumps, if (tailJumps == 1) "" else "s",
                if (t.playbackState == Player.STATE_BUFFERING) "loading" else "ready",
            )
        } else ""
        t.setPlaybackSpeed(main.playbackParameters.speed.takeIf { it > 0f } ?: 1f)
        t.volume = 0f
        if (!t.isPlaying) t.play()
        AppLog.i(
            "Crossfade",
            "Fading ${item.mediaId} into ${main.getMediaItemAt(nextIndex).mediaId}: ${planned.why} · " +
                if (wasPrimed) "tail in step (${if (drift >= 0) "+" else ""}$drift ms)$catchUp" else "tail started now (not primed in time)",
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
                // To where the fade-out already is, when the handover came late (it waited for the tail to be in step).
                val out = ((t.currentPosition - startAt).toFloat() / planned.overlapMs).coerceIn(0f, 1f)
                t.volume = tailBase * sleep * Crossfade.fadeIn(h) * Crossfade.fadeOut(out)
                level = Crossfade.fadeOut(h) * Crossfade.fadeOut(out)
                onLevel()
                if (elapsed >= HANDOVER_MS && t.isPlaying) {
                    switched = true
                    switchedAt = SystemClock.elapsedRealtime()
                    level = 0f
                    onLevel()
                    expectSeek = true
                    val beat = planned.beat
                    if (beat != null) {
                        // The new song comes in at the matched speed, where it should be right now against the old
                        // song's beats (the old song's position is the tail's).
                        main.setPlaybackSpeed(baseSpeed * beat.speed)
                        // Sent ahead by what getting there takes on this phone, so it lands on the beat (issue #747).
                        val lead = seekLag
                        seekLead = lead
                        seekTarget = beat.incomingAt(t.currentPosition) + lead
                        main.seekTo(incomingIndex, seekTarget)
                    } else {
                        main.seekTo(incomingIndex, 0)
                    }
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
                val beat = planned.beat
                if (beat != null && reverb != null) echo(t, planned, beat, tailDone, tailBase * sleep)
                val heard = if (main.currentMediaItemIndex == incomingIndex) (main.currentPosition - (beat?.incomingStartMs ?: 0L)).coerceAtLeast(0L) else 0L
                val fadedIn = (heard.toFloat() / planned.fadeInMs).coerceIn(0f, 1f)
                level = Crossfade.fadeIn(fadedIn)
                onLevel()
                if (beat != null && !tailDone) keepInStep(beat, t)
                // Done when both are (and the echo has rung out); or anyway a while after the old song ended, if the
                // new one is stuck loading.
                val rung = reverb == null || SystemClock.elapsedRealtime() >= echoUntil
                if (tailDone && rung && (fadedIn >= 1f || elapsed > planned.overlapMs + GIVE_UP_MS)) {
                    finish()
                    return
                }
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    /**
     * Keeps the new song's beats on the old song's through a beat-matched blend: well off (the seek landed late,
     * or the stream couldn't jump), it jumps to where it should be, a few times at most; otherwise its speed is
     * nudged a little, which can't be heard.
     */
    private fun keepInStep(beat: BeatMatch.Plan, t: ExoPlayer) {
        if (main.currentMediaItemIndex != incomingIndex || !main.isPlaying) return
        val now = SystemClock.elapsedRealtime()
        if (now - switchedAt < SETTLE_MS) return  // the seek is still landing; its position isn't real yet
        val position = main.currentPosition
        // Until the jump lands the player says it's at the spot it was sent to, which isn't a position yet.
        if (position == seekTarget || main.playbackState != Player.STATE_READY) return
        val reading = beat.error(t.currentPosition, position)
        stepStats.add(reading)
        // One reading can be 90 ms off and back the next moment: the middle of the last few decides (issue #759).
        val error = smoothed.add(reading) ?: return
        stepStats.addSettled(error)
        // Every 4 bars, how far apart the beats are and the speed (issue #796): a report then says when in the blend
        // it went out of step, not only that it did.
        val bars4 = (beat.outgoing.beatLengthMs * BeatMatch.BEATS_PER_BAR * 4).toLong().coerceAtLeast(1L)
        val stage = ((t.currentPosition - beat.startMs) / bars4).toInt()
        if (stage > loggedStage) {
            loggedStage = stage
            AppLog.d("Crossfade", "Beat-matching, ${stage * 4} bars in: ${if (error > 0) "+" else ""}$error ms out of step, next song at %.4f×".format(java.util.Locale.ROOT, main.playbackParameters.speed))
        }
        seekLead?.let { lead ->
            // The first settled error after a jump says how long getting there took on this phone (issue #747);
            // nothing has nudged the song yet, so the readings it's the middle of are of one and the same error.
            seekLag = BeatMatch.learnedLag(seekLag, lead, error)
            seekLead = null
        }
        // A jump is a cut in the sound: only while the new song is still quiet, early in the blend. After that
        // the speed brings it in step, which can't be heard.
        val early = now - switchedAt < JUMP_WINDOW_MS && jumps < MAX_JUMPS
        if (abs(error) > BeatMatch.JUMP_MS && early && main.isCurrentMediaItemSeekable) {
            jumps++
            switchedAt = now
            smoothed = BeatMatch.SmoothedError()
            val lead = seekLag
            seekLead = lead
            // Our own jump, not the listener moving in the song: the blend carries on (issue #747).
            expectSeek = true
            val target = beat.incomingAt(t.currentPosition) + lead
            seekTarget = target
            AppLog.d("Crossfade", "Beat-matching: the next song is ${if (error > 0) "+" else ""}$error ms out of step; jumping to $target ms (jump $jumps)")
            main.seekTo(incomingIndex, target)
            return
        }
        // Only a change worth hearing is sent: re-tuning the time-stretcher every 50 ms is heard as a flutter (#773).
        val wanted = baseSpeed * beat.speed * BeatMatch.nudge(error)
        if (BeatMatch.worthChanging(main.playbackParameters.speed, wanted)) main.setPlaybackSpeed(wanted)
    }

    /** The hall for the echo, on the output mix, with the tail sent to it at nothing yet (issue #759). */
    private fun openEcho(t: ExoPlayer) {
        closeEcho()
        reverb = runCatching {
            PresetReverb(0, 0).apply {
                preset = PresetReverb.PRESET_LARGEHALL
                enabled = true
            }
        }.onFailure { AppLog.w("Crossfade", "No echo at the end: the phone's reverb couldn't be opened (${it.message})") }.getOrNull()
        reverb?.let { t.setAuxEffectInfo(AuxEffectInfo(it.id, 0f)) }
        echoSend = 0f
        echoUntil = 0L
    }

    /**
     * Sends more of the tail to the hall through its last bar; once the tail is done, pauses it and lets the hall
     * ring for [ECHO_RING_MS] before the fade ends.
     */
    private fun echo(t: ExoPlayer, planned: FadePlan, beat: BeatMatch.Plan, tailDone: Boolean, level: Float) {
        val id = reverb?.id ?: return
        if (tailDone) {
            if (echoUntil == 0L) {
                echoUntil = SystemClock.elapsedRealtime() + ECHO_RING_MS
                if (t.isPlaying) t.pause()
            }
            return
        }
        val bar = beat.outgoing.beatLengthMs * BeatMatch.BEATS_PER_BAR
        val left = planned.overlapMs - (t.currentPosition - startAt)
        // The hall gets the song before its volume (levelling, the sleep timer): [level] goes into the send.
        val send = ((bar - left) / bar).toFloat().coerceIn(0f, 1f) * ECHO_SEND * level
        if (abs(send - echoSend) >= 0.02f) {
            echoSend = send
            t.setAuxEffectInfo(AuxEffectInfo(id, send))
        }
    }

    private fun closeEcho() {
        reverb?.let { r ->
            runCatching { tail?.setAuxEffectInfo(AuxEffectInfo(0, 0f)) }
            runCatching { r.enabled = false }
            r.release()
        }
        reverb = null
        echoSend = -1f
        echoUntil = 0L
    }

    /** After a beat-matched blend, the new song eases back to its own speed. */
    private val easeTick = object : Runnable {
        override fun run() {
            if (easingFrom == 0f) return
            val elapsed = SystemClock.elapsedRealtime() - easingSince
            // Only steps worth hearing as a tempo change: each one re-tunes the time-stretcher (issue #796).
            BeatMatch.easeStep(easingFrom, elapsed, main.playbackParameters.speed, baseSpeed)?.let {
                easeChanges++
                main.setPlaybackSpeed(it)
            }
            if (elapsed >= BeatMatch.EASE_MS) {
                easingFrom = 0f
                AppLog.d("Crossfade", "Beat-matching: back at the song's own speed, in $easeChanges steps")
            } else {
                handler.postDelayed(this, EASE_TICK_MS)
            }
        }
    }

    private fun startEasing(beat: BeatMatch.Plan) {
        easingFrom = beat.speed
        easingSince = SystemClock.elapsedRealtime()
        easeChanges = 0
        AppLog.i(
            "Crossfade",
            "Beat-matched blend done (${beat.shiftPercent} %): ${stepStats.describe()}, $jumps jump${if (jumps == 1) "" else "s"}, " +
                "next song sent ${seekLag} ms ahead on this phone; " +
                "easing back to the song's own speed over ${BeatMatch.EASE_MS / 1000} s",
        )
        handler.post(easeTick)
    }

    /** Ends any easing at once, back at the song's own speed (a skip, a seek, a new fade). */
    private fun stopEasing() {
        handler.removeCallbacks(easeTick)
        if (easingFrom != 0f) {
            easingFrom = 0f
            main.setPlaybackSpeed(baseSpeed)
        }
    }

    private fun finish() {
        handler.removeCallbacks(fadeTick)
        val beat = plan?.beat
        if (beat != null && phase == Phase.FADING && switched) {
            if (main.currentMediaItemIndex == incomingIndex) startEasing(beat) else main.setPlaybackSpeed(baseSpeed)
        }
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
        stopEasing()
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
        stopEasing()
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

    /**
     * Fade shapes arrived for [ids] (issue #796): a blend got ready before the next song's arrived is planned
     * again, so it can take that song's beat into account.
     */
    fun profilesArrived(ids: Set<String>) {
        val next = main.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { main.getMediaItemAt(it).mediaId } ?: return
        if (!Crossfade.planAgainForShape(phase == Phase.READY, plannedWithNext, primed, startAt - main.currentPosition, next in ids)) return
        AppLog.d("Crossfade", "The next song's fade shape arrived: planning the blend again")
        replan()
    }

    override fun onRepeatModeChanged(repeatMode: Int) = replan()

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = replan()

    private fun replan() {
        if (phase == Phase.FADING) return
        skipped = null
        disarm()
        poke()
    }

    /** A position for the log: "3:21". */
    private fun clock(ms: Long): String = "%d:%02d".format(java.util.Locale.ROOT, ms / 60_000, ms / 1000 % 60)

    companion object {
        /** How often to look while a song plays. */
        const val LOOK_MS = 1_000L
        /** The tail is made ready this long before the longest possible fade. */
        const val PREPARE_MS = 8_000L
        /** A fade needs at least this long to get ready, or that song plays on without one. */
        const val MIN_PREPARE_MS = 1_500L
        /** Starting this close to the planned moment is on time. */
        const val ON_TIME_MS = 15L
        /** The blend as the tail takes the song over from the main player: short, as two copies a few ms apart
         *  sound like one through a comb while both play (issue #759). */
        const val HANDOVER_MS = 60L
        /** The tail starts playing, silent, this long before the fade, to be sounding and in step by then. */
        const val PREROLL_MS = 3_000L
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
        /** After a seek in a beat-matched blend, how long before the new song's position is trusted. */
        const val SETTLE_MS = 250L
        /** How often the new song may be jumped into step in one blend, and only this soon after it came in. */
        const val MAX_JUMPS = 2
        const val JUMP_WINDOW_MS = 1_500L
        /** How often the speed moves while easing back after a beat-matched blend. */
        const val EASE_TICK_MS = 200L
        /** The echo at the end (issue #759): how much of the tail goes to the hall by the end of its last bar, and
         *  how long the hall rings after it. */
        const val ECHO_SEND = 0.75f
        const val ECHO_RING_MS = 1_800L
    }
}
