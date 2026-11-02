package app.siansa.playback

import android.os.Handler
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.siansa.data.AppSettings
import app.siansa.data.Song
import app.siansa.log.AppLog
import app.siansa.playback.engine.AudioEngine
import app.siansa.playback.engine.DeckStream
import app.siansa.playback.engine.MixCore
import kotlin.math.ceil
import kotlin.math.max

/**
 * Crossfades on the [AudioEngine] (issue #826): the same fades as [Crossfader] (planned by [Crossfade.plan], beat-matched
 * by [BeatMatch]), but with both songs on one output, so every placement is arithmetic on its clock instead of
 * steering two players by positions that are off for seconds after each start or seek.
 *
 * 1. A few seconds before the fade, a second "tail" player gets the current song ready a moment before the
 *    handover point, paused; its sound waits in its deck.
 * 2. The tail is cued: its copy starts at the exact mix frame the main player reaches the handover point, and the
 *    main player falls silent at that very frame. The same samples carry on from the other copy: nothing to hear.
 * 3. The main player moves to the next song, cued to start at the exact mix frame the old song reaches the blend's
 *    start: on the old song's bar or phrase for a beat-matched blend, at the speed that matches its tempo. Its sound
 *    arrives in its own time before then; it waits, silent, to come in on that frame. Played at a steady speed on the
 *    same clock, it stays there: nothing is nudged or jumped.
 * 4. The tail fades out with the old song's bass swapped out, the next song fades in, and after a beat-matched blend
 *    the next song eases back to its own speed.
 *
 * The handover is a couple of seconds before the blend ([LEAD_MS]), so the next song's start has time to load.
 */
@OptIn(UnstableApi::class)
internal class MixedCrossfader(
    private val main: ExoPlayer,
    private val handler: Handler,
    private val engine: AudioEngine,
    private val mainMix: MixCore.Deck,
    private val newTail: () -> PlayerFactory.Built,
    private val settings: () -> AppSettings,
    private val profiles: ProfileStore,
    private val levelOf: (Song) -> Float,
    private val sleepLevel: () -> Float,
    private val allowed: () -> Boolean,
    private val inDjRadio: () -> Boolean = { false },
    private val onLevel: () -> Unit,
) : Player.Listener, Fader {

    override var level = 1f
        private set

    private enum class Phase { IDLE, READY, CUED, FADING }

    private var phase = Phase.IDLE
    private var tail: ExoPlayer? = null
    private var tailMix: MixCore.Deck? = null
    private var outgoing: String? = null
    private var plan: FadePlan? = null
    /** The fade's start (ms of the old song), and the handover point a little before it. */
    private var startAt = 0L
    private var handoverAt = 0L
    /** The mix frame the tail takes the old song over at, and the main player's stream silenced there. */
    private var handoverMix = 0L
    private var cutStream: DeckStream? = null
    private var incomingIndex = C.INDEX_UNSET
    private var incomingId: String? = null
    /** Where the next song comes in (ms of its own), and the mix frame it does. */
    private var incomingFrom = 0L
    private var incomingMix = 0L
    private var tailBase = 1f
    private var startedAt = 0L
    private var expectSeek = false
    private var skipped: String? = null
    private var baseSpeed = 1f
    private var plannedWithNext = true
    private var stepStats = BeatMatch.StepStats()
    private var loggedStage = 0
    private var lateLogged = false
    private var easingFrom = 0f
    private var easingSince = 0L
    private var easeChanges = 0
    private var echoNoted = false

    override fun start() {
        main.addListener(this)
        poke()
    }

    override fun release() {
        handler.removeCallbacks(watch)
        handler.removeCallbacks(fadeTick)
        stopEasing()
        main.removeListener(this)
        releaseTail()
        phase = Phase.IDLE
    }

    override fun cancel(why: String) {
        when (phase) {
            Phase.FADING -> {
                AppLog.i("Crossfade", "Fade stopped: $why")
                finish()
            }
            Phase.READY, Phase.CUED -> disarm()
            Phase.IDLE -> Unit
        }
    }

    private fun poke() {
        handler.removeCallbacks(watch)
        handler.post(watch)
    }

    private val watch = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            val delay = look()
            if (delay > 0) handler.postDelayed(this, delay)
        }
    }

    /** Gets the next fade ready, cues it and starts it. Returns when to look again (ms), or 0 to stop looking. */
    private fun look(): Long {
        if (phase == Phase.FADING || !main.isPlaying) return 0
        if (phase == Phase.IDLE) freeMain()
        if (phase == Phase.CUED) {
            // The tail takes over at its mix frame; once the mixer is past it, the next song is cued.
            if (engine.writtenMix > handoverMix) {
                begin()
                return 0
            }
            return CUED_POLL_MS
        }
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
        val cut = if (s.crossfade == CrossfadeMode.SMART) Crossfade.endCut(profiles.cached(item.mediaId), duration, maxMs).first else 0L
        val lookMs = if (beatMatch) max(maxMs, profiles.cached(item.mediaId)?.outro?.let { BeatMatch.maxMsFor(it, s.blendBars) } ?: 0L) else maxMs
        if (duration - cut - position > lookMs + LEAD_MS + PREPARE_MS) {
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
            val handover = EngineFade.handoverAt(at, position)
            if (handover - position < MIN_PREPARE_MS) {
                AppLog.d("Crossfade", "Too close to the end of ${current.id} to get a fade ready")
                skipped = item.mediaId
                return LOOK_MS
            }
            plannedWithNext = profiles.cached(next.id) != null
            arm(item, planned, at, handover)
        }
        val speed = main.playbackParameters.speed.takeIf { it > 0f } ?: 1f
        val untilCue = ((handoverAt - CUE_AHEAD_MS - position) / speed).toLong()
        if (untilCue <= 0) {
            cueTail()
            return if (phase == Phase.CUED) CUED_POLL_MS else LOOK_MS
        }
        return minOf(LOOK_MS, untilCue)
    }

    /** The tail gets the old song ready, paused, a moment before the handover point; its sound waits in its deck. */
    private fun arm(item: MediaItem, planned: FadePlan, at: Long, handover: Long) {
        val built = newTail()
        val t = built.player
        tail = t
        tailMix = built.mix
        t.volume = 0f
        t.playWhenReady = false
        t.setMediaItem(item, (handover - SEEK_BACK_MS).coerceAtLeast(0L))
        t.prepare()
        outgoing = item.mediaId
        plan = planned
        startAt = at
        handoverAt = handover
        phase = Phase.READY
        val length = main.duration.takeIf { it > 0 }
        AppLog.i(
            "Crossfade",
            "Getting the fade out of ${item.mediaId} ready (${planned.why})" +
                (length?.let { " · starts at ${clock(at)} of ${clock(it)}, handed over at ${clock(handover)}" }.orEmpty()) +
                (planned.beatNote?.let { " · $it" } ?: ""),
        )
    }

    /**
     * Step 2: the tail starts at the exact mix frame the main player reaches the handover point, and the main
     * player falls silent at that frame. Given up (no fade) when the tail isn't loaded or it's too late.
     */
    private fun cueTail() {
        val t = tail ?: return
        val tMix = tailMix ?: return
        val item = main.currentMediaItem ?: return
        val stream = tMix.current
        val mainStream = mainMix.current
        val why = when {
            t.playbackState != Player.STATE_READY || stream == null -> "the song's second copy hasn't loaded (state ${t.playbackState})"
            mainStream == null -> "the main player has no sound to hand over"
            else -> null
        }
        if (why != null) {
            if (main.currentPosition >= handoverAt - GIVE_UP_BEFORE_MS) {
                AppLog.w("Crossfade", "Song ${item.mediaId} wasn't ready to fade in time: $why; playing on without a crossfade")
                skipped = item.mediaId
                disarm()
            }
            return
        }
        val at = mainMix.mixFrameAt(handoverAt * 1000, engine.outRate)
        val gate = at?.let { ceil(it).toLong() }
        if (gate == null || gate - engine.writtenMix < MIN_CUE_FRAMES) {
            AppLog.w("Crossfade", "Too late to hand ${item.mediaId} over at ${clock(handoverAt)} (mix frame $gate, written ${engine.writtenMix}); playing on without a crossfade")
            skipped = item.mediaId
            disarm()
            return
        }
        baseSpeed = main.playbackParameters.speed.takeIf { it > 0f } ?: 1f
        t.playbackParameters = BeatMatch.parameters(baseSpeed, baseSpeed, settings().keepKey)
        val cued = tMix.cueNow(handoverAt * 1000, gate, engine.outRate)
        if (cued == null) {
            AppLog.w("Crossfade", "The song's second copy couldn't be cued; playing on without a crossfade")
            skipped = item.mediaId
            disarm()
            return
        }
        tailBase = levelOf(item.toSong())
        t.volume = tailBase * sleepLevel()
        mainStream!!.cutAt(gate)
        cutStream = mainStream
        handoverMix = gate
        t.play()
        phase = Phase.CUED
        val inMs = ((gate - engine.heardMix()) * 1000 / engine.outRate).toLong()
        AppLog.d(
            "Crossfade",
            "Second copy cued: takes ${item.mediaId} over at ${clock(handoverAt)} on mix frame $gate (in $inMs ms)" +
                if (cued.missingFrames > 0) "; ${cued.missingFrames} frames of it were already gone" else "",
        )
    }

    /** Step 3: the old song plays from the tail; the main player moves to the next song, cued on the old song's grid. */
    private fun begin() {
        val t = tail
        val planned = plan
        val tMix = tailMix
        val nextIndex = main.nextMediaItemIndex
        val item = main.currentMediaItem
        if (t == null || planned == null || tMix == null || item == null || item.mediaId != outgoing || nextIndex == C.INDEX_UNSET) {
            disarm()
            return
        }
        incomingIndex = nextIndex
        incomingId = main.getMediaItemAt(nextIndex).mediaId
        startedAt = SystemClock.elapsedRealtime()
        stepStats = BeatMatch.StepStats()
        loggedStage = 0
        lateLogged = false
        stopEasing()
        phase = Phase.FADING
        level = 0f
        onLevel()
        val beat = planned.beat
        val (from, against) = EngineFade.incomingCue(planned, startAt)
        val at = tMix.mixFrameAt(against * 1000, engine.outRate)
        if (at == null) {
            AppLog.w("Crossfade", "The old song's copy can't say where it'll be; the next song comes in now")
        }
        incomingFrom = from
        incomingMix = at?.let { ceil(it).toLong() } ?: (engine.writtenMix + MIN_CUE_FRAMES)
        if (beat != null) {
            main.playbackParameters = BeatMatch.parameters(baseSpeed * beat.speed, baseSpeed, settings().keepKey)
            if (settings().bassSwap) {
                mainMix.filter.shelfDb = DeckFilter.SHELF_START_DB
                AppLog.d("Crossfade", "Bass swap over the first ${SWAP_FRACTION_PERCENT} % of the blend: next song's bass held back ${-DeckFilter.SHELF_START_DB.toInt()} dB, old song's cut up to ${DeckFilter.LOW_CUT_HZ.toInt()} Hz")
            }
            if (settings().echoTail && !echoNoted) {
                echoNoted = true
                AppLog.d("Crossfade", "Echo at the end: not in the new audio engine yet")
            }
            if (settings().keepKey) AppLog.d("Crossfade", "Keep the key: not in the new audio engine yet; the pitch follows the speed")
        }
        // Sent a little before its point: a jump lands on the start of a decoded block at or after where it's
        // sent, so the point itself is then in what's decoded and the cue is exact.
        val seekTo = (from - INCOMING_SEEK_BACK_MS).coerceAtLeast(0L)
        mainMix.cueNext(from * 1000, incomingMix, engine.outRate, seekUs = seekTo * 1000)
        expectSeek = true
        main.seekTo(incomingIndex, seekTo)
        val inMs = ((incomingMix - engine.heardMix()) * 1000 / engine.outRate).toLong()
        AppLog.i(
            "Crossfade",
            "Fading ${item.mediaId} into $incomingId: ${planned.why} · old song handed over on mix frame $handoverMix · " +
                "next song comes in at ${clock(from)} (${from} ms) on mix frame $incomingMix, with ${clock(against)} of the old song (in $inMs ms)",
        )
        handler.post(fadeTick)
    }

    private val fadeTick = object : Runnable {
        override fun run() {
            if (phase != Phase.FADING) return
            val t = tail
            val planned = plan
            val tMix = tailMix
            if (t == null || planned == null || tMix == null) {
                finish()
                return
            }
            val heardMix = engine.heardMix()
            val tailMs = tMix.mediaUsAt(heardMix)?.let { it / 1000 } ?: t.currentPosition
            val out = ((tailMs - startAt).toFloat() / planned.overlapMs).coerceIn(0f, 1f)
            val tailDone = out >= 1f || t.playbackState == Player.STATE_ENDED || t.playbackState == Player.STATE_IDLE
            t.volume = if (tailDone) 0f else tailBase * sleepLevel() * Crossfade.fadeOut(out)
            val beat = planned.beat
            if (beat != null && settings().bassSwap) swapBass(out, tMix)
            val onNext = main.currentMediaItemIndex == incomingIndex
            val heard = if (onNext && heardMix >= incomingMix) {
                (mainMix.mediaUsAt(heardMix)?.let { it / 1000 } ?: main.currentPosition) - incomingFrom
            } else 0L
            val fadedIn = (heard.toFloat() / planned.fadeInMs).coerceIn(0f, 1f)
            level = Crossfade.fadeIn(fadedIn)
            onLevel()
            if (beat != null && onNext && heardMix >= incomingMix && !tailDone) check(beat, tMix, heardMix)
            if (onNext && !lateLogged && heardMix >= incomingMix) {
                lateLogged = true
                if (mainMix.current?.cueGivenUp == true) AppLog.w("Crossfade", "The next song's cue was given up (seconds of its sound came before the point it was cued to); it plays from where it is")
                mainMix.lastCued?.let { if (it.missingFrames > 0) AppLog.w("Crossfade", "The next song's start came ${it.missingFrames} frames late; it came in that far on, on the grid") }
            }
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            if (tailDone && (fadedIn >= 1f || elapsed > planned.overlapMs + LEAD_MS + GIVE_UP_MS)) {
                finish()
                return
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    /** How far apart the two songs' beats are, from where each is on the one clock: it should be under a ms. */
    private fun check(beat: BeatMatch.Plan, tMix: MixCore.Deck, heardMix: Double) {
        val oldUs = tMix.mediaUsAt(heardMix) ?: return
        val newUs = mainMix.mediaUsAt(heardMix) ?: return
        val error = EngineFade.beatErrorMs(beat, oldUs, newUs)
        stepStats.add(error)
        stepStats.addSettled(error)
        val bars4 = (beat.outgoing.beatLengthMs * BeatMatch.BEATS_PER_BAR * 4).toLong().coerceAtLeast(1L)
        val stage = ((oldUs / 1000 - beat.startMs) / bars4).toInt()
        if (stage > loggedStage) {
            loggedStage = stage
            AppLog.d("Crossfade", "Beat-matching, ${stage * 4} bars in: ${if (error >= 0) "+" else ""}$error ms out of step, next song at %.4f×".format(java.util.Locale.ROOT, main.playbackParameters.speed))
        }
    }

    private fun swapBass(out: Float, tMix: MixCore.Deck) {
        val t = (out / SWAP_FRACTION).toDouble()
        tMix.filter.lowCutHz = DeckFilter.lowCutAt(t)
        mainMix.filter.shelfDb = DeckFilter.shelfAt(t)
    }

    // ---- After a beat-matched blend: back to the song's own speed ----

    private val easeTick = object : Runnable {
        override fun run() {
            if (easingFrom == 0f) return
            val elapsed = SystemClock.elapsedRealtime() - easingSince
            BeatMatch.easeStep(easingFrom, elapsed, main.playbackParameters.speed, baseSpeed)?.let {
                easeChanges++
                main.playbackParameters = BeatMatch.parameters(it, baseSpeed, settings().keepKey)
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
            "Beat-matched blend done (${beat.shiftPercent} %): ${stepStats.describe()}, on one clock (new audio engine); " +
                "easing back to the song's own speed over ${BeatMatch.EASE_MS / 1000} s",
        )
        handler.post(easeTick)
    }

    private fun stopEasing() {
        handler.removeCallbacks(easeTick)
        if (easingFrom != 0f) {
            easingFrom = 0f
            main.playbackParameters = BeatMatch.parameters(baseSpeed, baseSpeed, settings().keepKey)
        }
    }

    // ---- Ending ----

    /**
     * With no fade under way, the main player is never left silenced or held for a cue (a short song can end
     * while its copy is silenced for a handover, and gapless, the songs after it would stay silent).
     */
    private fun freeMain() {
        val stream = mainMix.current ?: return
        if (stream.held) {
            stream.freeHold()
            AppLog.w("Crossfade", "The main player was left silenced by a fade that's gone; its sound is back")
        }
    }

    private fun disarm() {
        if (phase == Phase.FADING) return
        uncut()
        releaseTail()
        outgoing = null
        plan = null
        phase = Phase.IDLE
        freeMain()
    }

    /** The main player's stream sounds again, if a cue silenced it and the fade didn't go ahead. */
    private fun uncut() {
        cutStream?.let { if (!it.released) it.cutAt(Long.MAX_VALUE) }
        cutStream = null
    }

    private fun finish() {
        handler.removeCallbacks(fadeTick)
        val beat = plan?.beat
        if (phase == Phase.FADING) {
            if (beat != null && main.currentMediaItemIndex == incomingIndex) {
                startEasing(beat)
            } else if (beat != null) {
                main.playbackParameters = BeatMatch.parameters(baseSpeed, baseSpeed, settings().keepKey)
            }
            // Stopped before the next song came in: the old song's main copy sounds again.
            if (main.currentMediaItemIndex != incomingIndex) uncut()
        }
        cutStream = null
        phase = Phase.IDLE
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

    private fun releaseTail() {
        mainMix.filter.reset()
        tailMix?.filter?.reset()
        tail?.let { t ->
            runCatching { t.stop() }
            t.release()
        }
        tail = null
        tailMix = null
    }

    // ---- What the main player does ----

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason != Player.DISCONTINUITY_REASON_SEEK && reason != Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) return
        // Where the player was sent, so its deck can tell a misread song time after the seek.
        mainMix.expectSeek(newPosition.positionMs * 1000)
        if (phase == Phase.FADING && expectSeek && newPosition.mediaItemIndex == incomingIndex) {
            expectSeek = false
            return
        }
        when (phase) {
            Phase.FADING -> cancel("you skipped or moved in the song")
            Phase.READY, Phase.CUED -> disarm()
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

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_IDLE) cancel("the player stopped")
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) poke()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (phase == Phase.FADING) {
            if (mediaItem == null || (mediaItem.mediaId != incomingId && mediaItem.mediaId != outgoing)) cancel("another song was chosen")
            return
        }
        stopEasing()
        skipped = null
        if ((phase == Phase.READY || phase == Phase.CUED) && mediaItem?.mediaId != outgoing) disarm()
        poke()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
        if (phase == Phase.FADING) {
            val queue = (0 until main.mediaItemCount).map { main.getMediaItemAt(it).mediaId }
            val moved = Crossfade.incomingAfterQueueChange(queue, incomingId, incomingIndex)
            if (moved == null) cancel(if (queue.isEmpty()) "the queue was cleared" else "the queue changed") else incomingIndex = moved
            return
        }
        if (phase == Phase.CUED) return  // the handover is set; the next song is picked when it happens
        skipped = null
        disarm()
        poke()
    }

    override fun profilesArrived(ids: Set<String>) {
        val next = main.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { main.getMediaItemAt(it).mediaId } ?: return
        if (!Crossfade.planAgainForShape(phase == Phase.READY, plannedWithNext, false, startAt - main.currentPosition, next in ids)) return
        AppLog.d("Crossfade", "The next song's fade shape arrived: planning the blend again")
        replan()
    }

    override fun onRepeatModeChanged(repeatMode: Int) = replan()

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = replan()

    private fun replan() {
        if (phase == Phase.FADING || phase == Phase.CUED) return
        skipped = null
        disarm()
        poke()
    }

    private fun clock(ms: Long): String = "%d:%02d".format(java.util.Locale.ROOT, ms / 60_000, ms / 1000 % 60)

    companion object {
        const val LOOK_MS = 1_000L
        /** The tail is made ready this long before the handover. */
        const val PREPARE_MS = 10_000L
        const val MIN_PREPARE_MS = 2_500L
        /** The handover is this long before the blend: the next song's start loads meanwhile. */
        const val LEAD_MS = EngineFade.LEAD_MS
        /** The tail starts loading this much before the handover point (a seek lands a little early or late). */
        const val SEEK_BACK_MS = 400L
        /** The tail is cued this long before the handover: well ahead of what the mixer has written. */
        const val CUE_AHEAD_MS = 1_200L
        /** A cue closer than this to what the mixer has already written can't be met. */
        const val MIN_CUE_FRAMES = AudioEngine.OUTPUT_RATE / 5
        /** A tail not loaded this close to the handover is given up on. */
        const val GIVE_UP_BEFORE_MS = 300L
        const val CUED_POLL_MS = 20L
        /** The next song is sent this far before its point (a jump lands up to a decoded block past it). */
        const val INCOMING_SEEK_BACK_MS = 300L
        const val TICK_MS = 20L
        const val GIVE_UP_MS = 10_000L
        const val EASE_TICK_MS = 200L
        const val SWAP_FRACTION = 0.5f
        const val SWAP_FRACTION_PERCENT = 50
    }
}

/** The arithmetic of a fade on the [AudioEngine] (issue #826), apart from the players so it can be tested. */
internal object EngineFade {
    /** The handover is this long before the blend starts: the next song's start loads meanwhile. */
    const val LEAD_MS = 2_500L
    /** And never sooner than this from now. */
    const val MIN_LEAD_MS = 1_500L

    /** Where (ms of the old song) the tail takes the old song over, for a fade starting at [startAtMs] heard at [positionMs]. */
    fun handoverAt(startAtMs: Long, positionMs: Long): Long =
        (startAtMs - LEAD_MS).coerceAtLeast(positionMs + MIN_LEAD_MS).coerceAtMost(startAtMs)

    /**
     * Where the next song comes in (ms of its own) and the point of the old song it comes in with (ms): its first
     * downbeat or phrase with the blend's start for a beat-matched blend, else its start with the fade's.
     */
    fun incomingCue(plan: FadePlan, startAtMs: Long): Pair<Long, Long> {
        val beat = plan.beat
        return if (beat != null) beat.incomingStartMs to beat.startMs else 0L to startAtMs
    }

    /** How far the next song's beats are from the old song's (ms; ahead is positive), from where each is heard (µs). */
    fun beatErrorMs(beat: BeatMatch.Plan, outgoingUs: Long, incomingUs: Long): Long =
        Math.round(incomingUs / 1000.0 - (beat.incomingStartMs + (outgoingUs / 1000.0 - beat.startMs) * beat.speed))
}
