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
import kotlin.math.abs
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
    /** Whether the song playing goes to the receiver as a bitstream (TV passthrough), past the mix (issue #939). */
    private val bitstream: () -> Boolean = { false },
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
    /** When the listener last moved in the song ([SystemClock.elapsedRealtime]), and in which: Smart blends quickly just after. */
    private var userSeekAt = 0L
    private var userSeekIn: String? = null
    /** The last blend's technique: Smart doesn't use the same twice running when another fits. */
    private var lastTechnique: Technique? = null
    private var skipped: String? = null
    private var baseSpeed = 1f
    private var plannedWithNext = true
    private var stepStats = BeatMatch.StepStats()
    private var loggedStage = 0
    private var lateLogged = false
    private var easingFrom = 0f
    private var easingSince = 0L
    private var easeChanges = 0

    override fun start() {
        main.addListener(this)
        poke()
    }

    override fun release() {
        handler.removeCallbacks(watch)
        handler.removeCallbacks(fadeTick)
        handler.removeCallbacks(soundCheck)
        stopEasing()
        main.removeListener(this)
        releaseTail()
        phase = Phase.IDLE
    }

    override fun cancel(why: String) {
        when (phase) {
            Phase.FADING -> {
                AppLog.i("Crossfade", "Fade stopped: $why")
                // Stopped or skipped: the echo stops with it rather than ringing over what comes next.
                tailMix?.echo?.clear()
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
            duration == C.TIME_UNSET || duration <= 0 || nextIndex == C.INDEX_UNSET || item.mediaId == skipped ||
            // Radio stations and podcast episodes (issue #353) never blend, into or out of anything.
            !Crossfade.blends(item.mediaId, main.getMediaItemAt(nextIndex).mediaId)
        if (unwanted) {
            if (phase == Phase.READY) disarm()
            return LOOK_MS
        }
        val position = main.currentPosition
        val blending = Blending.of(s)
        val maxMs = blending.maxMs
        val beatMatch = Blending.beatMatched(s, inDjRadio())
        val cut = if (s.crossfade == CrossfadeMode.SMART) Crossfade.endCut(profiles.cached(item.mediaId), duration, maxMs).first else 0L
        val beatLook = if (beatMatch) max(maxMs, profiles.cached(item.mediaId)?.outro?.let { BeatMatch.maxMsFor(it, TechniquePicker.lookBars(s.crossfade == CrossfadeMode.SMART, blending.blendBars)) } ?: 0L) else maxMs
        // A blend after the last beat can be longer than the setting: it's planned early enough for that.
        val lookMs = if (s.crossfade == CrossfadeMode.SMART && profiles.cached(item.mediaId)?.beatsUntilMs != null) max(beatLook, Crossfade.AFTER_BEAT_MAX_MS) else beatLook
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
                blendBars = blending.blendBars,
                sinceSeekMs = if (userSeekIn == current.id && userSeekAt > 0) SystemClock.elapsedRealtime() - userSeekAt else null,
                lastTechnique = lastTechnique,
            )
            if (planned == null) {
                val why = Crossfade.whyNoFade(s.crossfade, maxMs, current, next, profiles.cached(current.id), profiles.cached(next.id), beatMatch)
                    ?: "one of them is too short to share a fade"
                AppLog.d("Crossfade", "No fade from ${current.id} into ${next.id}: $why")
                skipped = item.mediaId
                return LOOK_MS
            }
            var fade: FadePlan = planned
            var at = duration - fade.cutMs - fade.overlapMs
            var handover = EngineFade.handoverAt(at, position)
            if (handover - position < MIN_PREPARE_MS) {
                // Too late for the blend picked (the listener moved near the end, or the song was only just known):
                // a quick one that still fits, not no blend at all.
                val quick = Crossfade.plan(
                    mode = s.crossfade, maxMs = maxMs, current = current, next = next, currentDurationMs = duration,
                    nextDurationMs = next.durationSec * 1000L, currentProfile = profiles.cached(current.id),
                    nextProfile = profiles.cached(next.id), beatMatch = beatMatch, blendBars = blending.blendBars,
                    sinceSeekMs = 0, lastTechnique = lastTechnique,
                )
                var quickAt = quick?.let { duration - it.cutMs - it.overlapMs }
                var quickHandover = quickAt?.let { EngineFade.handoverAt(it, position) }
                var chosen = quick
                if (quick != null && quickHandover != null && quickHandover - position < MIN_PREPARE_MS && quick.beat == null && !quick.coldEnd) {
                    // Not even that fits (the owner's log, 12 Nov: moved to the last half minute, the songs changed with
                    // no blend at all): a plain blend of what's left, however short, never none.
                    val left = duration - quick.cutMs - position - MIN_PREPARE_MS - EngineFade.LEAD_MS
                    if (left >= SHORTEST_LEFT_MS) {
                        chosen = quick.copy(overlapMs = left, fadeInMs = minOf(quick.fadeInMs, left).coerceAtLeast(Crossfade.MIN_FADE_IN_MS),
                            why = "smart: a blend of what's left (%.1f s) after moving near the end · ${quick.why}".format(java.util.Locale.ROOT, left / 1000.0))
                        quickAt = duration - chosen.cutMs - chosen.overlapMs
                        quickHandover = EngineFade.handoverAt(quickAt, position)
                    }
                }
                if (chosen == null || quickAt == null || quickHandover == null || quickHandover - position < MIN_PREPARE_MS) {
                    AppLog.i("Crossfade", "Too close to the end of ${current.id} to get a fade ready (${fade.why}): the songs change without one")
                    skipped = item.mediaId
                    return LOOK_MS
                }
                AppLog.i("Crossfade", "Too close to the end of ${current.id} for the blend picked (${fade.why}): ${if (chosen === quick) "a quick one" else "what's left"} instead")
                fade = chosen
                at = quickAt
                handover = quickHandover
            }
            plannedWithNext = profiles.cached(next.id) != null
            arm(item, fade, at, handover)
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
        // What the clock says against what the player says: far apart, the song's time is misread on one of them, and
        // a handover by it would silence the song at the wrong moment. No fade then, rather than a gap.
        val speedNow = main.playbackParameters.speed.takeIf { it > 0f } ?: 1f
        val byClockMs = ((gate - engine.heardMix()) * 1000 / engine.outRate).toLong()
        val byPlayerMs = ((handoverAt - main.currentPosition) / speedNow).toLong()
        if (abs(byClockMs - byPlayerMs) > DISAGREE_MS) {
            AppLog.w("Crossfade", "The handover of ${item.mediaId} would be in $byClockMs ms by the mixer's clock but $byPlayerMs ms by the player; playing on without a crossfade")
            skipped = item.mediaId
            disarm()
            return
        }
        baseSpeed = main.playbackParameters.speed.takeIf { it > 0f } ?: 1f
        t.playbackParameters = BeatMatch.parameters(baseSpeed, baseSpeed, Blending.of(settings()).keepKey)
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
            main.playbackParameters = BeatMatch.parameters(baseSpeed * beat.speed, baseSpeed, Blending.of(settings()).keepKey)
            if (Blending.of(settings()).bassSwap) {
                mainMix.filter.shelfDb = DeckFilter.SHELF_START_DB
                AppLog.d("Crossfade", "Bass swap over the first ${SWAP_FRACTION_PERCENT} % of the blend: next song's bass held back ${-DeckFilter.SHELF_START_DB.toInt()} dB, old song's cut up to ${DeckFilter.LOW_CUT_HZ.toInt()} Hz")
            }
            if (planned.filterHz > 0) AppLog.d("Crossfade", "Filter fade: the old song's high-pass sweeps up to ${planned.filterHz.toInt()} Hz over the blend")
            if (planned.swapFrom > 0f) AppLog.d("Crossfade", "Bass swap from ${(planned.swapFrom * 100).toInt()} % to ${(planned.swapTo * 100).toInt()} % of the blend")
            if (Blending.of(settings()).keepKey && beat.speed != 1f) AppLog.d("Crossfade", "Keep the key: the next song is stretched in time, its pitch kept")
        }
        echoBeatMs(planned)?.let { beatMs ->
            tailMix?.let { d ->
                val echo = engine.core.echoOf(d)
                echo.clear()
                echo.delayMs = beatMs
                // An echo out's repeats are few and faint: gone about a beat after the next song drops in.
                echo.feedback = if (planned.technique == Technique.ECHO_OUT) ECHO_OUT_FEEDBACK else app.siansa.playback.engine.Echo.FEEDBACK
                AppLog.d("Crossfade", "Echo at the end: over the old song's last bar, repeats a beat apart (${beatMs.toInt()} ms)")
            }
        }
        planned.technique?.let { lastTechnique = it }
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
        LastBlend.record(item.mediaId, incomingId ?: "", against, planned, dj = inDjRadio())
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
            // An echo out plays the old song's last bar at full level and stops it dead on the bar line.
            val outLevel = if (planned.technique == Technique.ECHO_OUT) 1f else Crossfade.fadeOut(out)
            t.volume = if (tailDone) 0f else tailBase * sleepLevel() * outLevel
            val beat = planned.beat
            if (beat != null && Blending.of(settings()).bassSwap) swapBass(out, tMix, planned)
            else if (planned.filterHz > 0) tMix.filter.lowCutHz = DeckFilter.filterAt(out.toDouble(), planned.filterHz)
            echoBeatMs(planned)?.let { beatMs -> tMix.echo?.let { echoOut(it, planned, beatMs, tailMs, tailDone) } }
            val onNext = main.currentMediaItemIndex == incomingIndex
            // How long the next song has been heard, on the mixer's own clock: never a song time that can be misread,
            // so the fade-in can't stay at nothing (0.52.0-dev).
            val heard = if (onNext && heardMix >= incomingMix) ((heardMix - incomingMix) * 1000 / engine.outRate).toLong() else 0L
            val fadedIn = (heard.toFloat() / planned.fadeInMs).coerceIn(0f, 1f)
            level = if (planned.buildUp) Crossfade.buildIn(fadedIn) else Crossfade.fadeIn(fadedIn)
            onLevel()
            fallbackReason(onNext, heardMix)?.let { why ->
                fallBack(why)
                return
            }
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

    /**
     * Why the blend can't go on as planned, or null when it can: its cue was given up, or the next song is still
     * silent well after it was due. Then the default: the next song plays on its own, at full level ([fallBack]).
     */
    private fun fallbackReason(onNext: Boolean, heardMix: Double): String? {
        val stream = mainMix.current ?: return null
        if (stream.cueGivenUp) return "its cue was given up (the next song's time was misread)"
        val dueFor = (heardMix - incomingMix) * 1000 / engine.outRate
        if (onNext && dueFor > SILENT_AFTER_DUE_MS && stream.stuckMs(engine.outRate) > SILENT_AFTER_DUE_MS) {
            return "the next song was still silent ${dueFor.toLong()} ms after it was due (${stream.describe()})"
        }
        return null
    }

    /**
     * The default when a blend can't be made (issue #826): the next song plays from where it is, at full level, and
     * the old song fades out over [FALLBACK_FADE_MS]. Never a song shown playing with no sound.
     */
    private fun fallBack(why: String) {
        AppLog.w("Crossfade", "The blend couldn't be made: $why; the next song plays on its own")
        mainMix.current?.freeHold()
        level = 1f
        onLevel()
        val t = tail
        val from = t?.volume ?: 0f
        val began = SystemClock.elapsedRealtime()
        handler.removeCallbacks(fadeTick)
        handler.post(object : Runnable {
            override fun run() {
                if (phase != Phase.FADING || tail !== t) return
                val done = (SystemClock.elapsedRealtime() - began).toFloat() / FALLBACK_FADE_MS
                if (done >= 1f || t == null) {
                    finish()
                    return
                }
                t.volume = from * (1f - done)
                handler.postDelayed(this, TICK_MS)
            }
        })
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

    /** The bass swap over its span of the blend ([FadePlan.swapFrom] to [FadePlan.swapTo]), with a filter fade's sweep. */
    private fun swapBass(out: Float, tMix: MixCore.Deck, planned: FadePlan) {
        val span = (planned.swapTo - planned.swapFrom).coerceAtLeast(0.01f)
        val t = ((out - planned.swapFrom) / span).toDouble().coerceIn(0.0, 1.0)
        tMix.filter.lowCutHz = maxOf(DeckFilter.lowCutAt(t), DeckFilter.filterAt(out.toDouble(), planned.filterHz))
        mainMix.filter.shelfDb = DeckFilter.shelfAt(t)
    }

    /** The echo's beat, when this blend echoes out: an echo out's own, or a beat-matched blend's with the echo on. */
    private fun echoBeatMs(planned: FadePlan): Double? =
        planned.echoBeatMs ?: planned.beat?.takeIf { Blending.of(settings()).echoTail }?.outgoing?.beatLengthMs

    // ---- After a beat-matched blend: back to the song's own speed ----

    private val easeTick = object : Runnable {
        override fun run() {
            if (easingFrom == 0f) return
            val elapsed = SystemClock.elapsedRealtime() - easingSince
            BeatMatch.easeStep(easingFrom, elapsed, main.playbackParameters.speed, baseSpeed)?.let {
                easeChanges++
                main.playbackParameters = BeatMatch.parameters(it, baseSpeed, Blending.of(settings()).keepKey)
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
            main.playbackParameters = BeatMatch.parameters(baseSpeed, baseSpeed, Blending.of(settings()).keepKey)
        }
    }

    // ---- Ending ----

    /**
     * With no fade under way, the main player is never left silenced or held for a cue (a short song can end
     * while its copy is silenced for a handover, and gapless, the songs after it would stay silent).
     */
    private fun freeMain() {
        if (level != 1f) {
            level = 1f
            onLevel()
            AppLog.w("Crossfade", "The main player was left turned down by a fade that's gone; back at full level")
        }
        val stream = mainMix.current
        if (stream != null && stream.held) {
            AppLog.w("Crossfade", "The main player was left silenced by a fade that's gone; its sound is back (${stream.describe()})")
            stream.freeHold()
        } else if (stream != null && main.isPlaying && stream.stuckMs(engine.outRate) > STUCK_MS) {
            // Playing, sound waiting, none given, with no fade under way: whatever holds it, it plays again.
            AppLog.w("Crossfade", "The main player was playing without sound for ${stream.stuckMs(engine.outRate)} ms (${stream.describe()}); freed")
            stream.freeHold()
        }
        if (!main.isPlaying || sleepLevel() < 0.99f || main.volume < 0.01f) return
        // A Dolby or DTS song sent to the receiver unchanged never goes through the mix, so the mix is silent by design:
        // taking that for lost sound made the decoder again 3 s into every Atmos song that followed straight on from the
        // one before (issue #939), a gap each time.
        if (bitstream()) return
        // The player hands over sound, but none goes out (0.52.0-dev: Djrum's Waxcap, Camo & Krooked's Passion, both
        // right after a song that ended back to back): whatever holds it, it's logged in full and put right.
        // Fed for longer than the stream holds plus the limit: a song that starts silent isn't taken for this.
        val fed = mainMix.feedingMs() > FED_MS
        val silent = engine.core.silentMs()
        val muted = stream?.mutedMs(engine.outRate) ?: 0
        val sinceBackToBack = if (backToBackAt == 0L) Long.MAX_VALUE else (System.nanoTime() - backToBackAt) / 1_000_000
        when {
            // Songs changed back to back and the player has handed over nothing but silence since: its decoder is
            // made again (0.52.0-dev: FFmpeg handed over silence after such a change).
            sinceBackToBack in MUTED_MS..BACK_TO_BACK_WATCH_MS && mainMix.feedingMs() == 0L && silent > MUTED_MS ->
                recover("the songs changed back to back and the player has handed over only silence since", rebuild = true)
            fed && silent > MUTED_MS -> recover("the player hands over sound but the mix has gone out silent for $silent ms")
            muted > MUTED_MS -> recover("the song has sound but none of it reaches the mix for $muted ms")
            engine.stalledMs() > STALLED_MS -> recover("the output has played without moving on for ${engine.stalledMs()} ms")
        }
    }

    /**
     * Puts sound that should be playing but isn't right, a step at a time and at most twice a song (so it can't
     * loop): first the song starts again where it is (a new stream), then the output is opened again.
     */
    private fun recover(why: String, rebuild: Boolean = false) {
        val song = main.currentMediaItem?.mediaId
        if (song != recoverySong) {
            recoverySong = song
            recoverySteps = 0
        }
        AppLog.w("Crossfade", "No sound: $why · song $song at ${main.currentPosition} ms, player volume ${main.volume}, level $level · ${engine.core.describe()} · ${engine.status()}")
        engine.core.resetSilence()
        when (recoverySteps++) {
            0 -> if (rebuild) {
                AppLog.w("Crossfade", "Making the player's decoder again and going on from where the song is")
                val index = main.currentMediaItemIndex
                val at = main.currentPosition
                backToBackAt = 0L
                main.stop()
                main.seekTo(index, at)
                main.prepare()
                main.play()
            } else {
                AppLog.w("Crossfade", "Starting the song again where it is, so it plays with sound")
                main.seekTo(main.currentPosition)
            }
            1 -> {
                AppLog.w("Crossfade", "Still no sound: opening the output again")
                engine.reopen()
            }
            else -> Unit
        }
    }

    /** When (System.nanoTime()) the songs last changed on their own with no fade; 0 when the last change wasn't that. */
    private var backToBackAt = 0L

    /** The song sound was last put right for, and how many steps it took (at most two a song). */
    private var recoverySong: String? = null
    private var recoverySteps = 0

    /** A few seconds into each song, what the mixer is doing, for the log: a silence shows up even if nothing caught it. */
    private val soundCheck = Runnable {
        if (main.isPlaying) {
            val fed = mainMix.feedingMs() > 0
            AppLog.d("Engine", "Sound check, ${main.currentMediaItem?.mediaId} at ${main.currentPosition} ms: player ${if (fed) "hands over sound" else "hands over silence"} · ${engine.core.describe()} · ${engine.status()}")
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
                main.playbackParameters = BeatMatch.parameters(baseSpeed, baseSpeed, Blending.of(settings()).keepKey)
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

    /**
     * The echo at the end (issue #759): more and more of the old song goes to the echo over its last bar, before its
     * volume, so its last beats repeat on the beat after it's gone; once it's done, nothing more is sent and the
     * repeats die away by themselves.
     */
    private fun echoOut(echo: app.siansa.playback.engine.Echo, planned: FadePlan, beatMs: Double, tailMs: Long, tailDone: Boolean) {
        if (tailDone) {
            echo.send = 0f
            return
        }
        val bar = beatMs * BeatMatch.BEATS_PER_BAR
        val left = planned.overlapMs - (tailMs - startAt)
        val most = if (planned.technique == Technique.ECHO_OUT) ECHO_OUT_SEND else ECHO_SEND
        echo.send = ((bar - left) / bar).toFloat().coerceIn(0f, 1f) * most * tailBase * sleepLevel()
    }

    private fun releaseTail() {
        // The repeats ring out by themselves; nothing more goes in.
        tailMix?.echo?.send = 0f
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
        userSeekAt = SystemClock.elapsedRealtime()
        userSeekIn = main.currentMediaItem?.mediaId
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
        backToBackAt = if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) System.nanoTime() else 0L
        handler.removeCallbacks(soundCheck)
        handler.postDelayed(soundCheck, SOUND_CHECK_MS)
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
        /** A blend of what's left after moving near a song's end is at least this long; less, and the songs just change. */
        const val SHORTEST_LEFT_MS = 1_500L
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
        /** The mixer's clock and the player's position may disagree by this much about a handover, no more. */
        const val DISAGREE_MS = 1_500L
        /** The next song still silent this long after it was due: the blend is dropped for the default. */
        const val SILENT_AFTER_DUE_MS = 1_500.0
        /** How quickly the old song fades out when a blend is dropped. */
        const val FALLBACK_FADE_MS = 1_000L
        /** With no fade under way, the main player silent this long with sound waiting is freed. */
        const val STUCK_MS = 2_000L
        /** How long a song may have sound with none reaching the output, outside a fade, before it starts again. */
        const val MUTED_MS = 3_000L
        /** How much of the old song goes to the echo by the end of its last bar (as the old engine's hall, #759). */
        const val ECHO_SEND = 0.75f
        /** An echo out's echo: quieter and shorter than the blend's, a faint trail under the next song. */
        const val ECHO_OUT_SEND = 0.5f
        const val ECHO_OUT_FEEDBACK = 0.25f
        /** An output playing this long without its position moving on is stuck. */
        const val STALLED_MS = 3_000L
        /** The player has handed over sound this long without a break: what it handed first should be heard by now. */
        const val FED_MS = 5_000L
        /** How long after a change of songs back to back silence from the player is taken as the player's fault. */
        const val BACK_TO_BACK_WATCH_MS = 15_000L
        /** How far into each song the mixer's state is logged. */
        const val SOUND_CHECK_MS = 4_000L
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
     * downbeat or phrase with the blend's start for a beat-matched blend, its start a pause after the old song's end
     * for a cold end, else its start with the fade's.
     */
    fun incomingCue(plan: FadePlan, startAtMs: Long): Pair<Long, Long> {
        val beat = plan.beat
        return when {
            beat != null -> beat.incomingStartMs to beat.startMs
            // A cold end: after the old song's last sound and the pause (the point is past its end, on its clock).
            plan.coldEnd -> plan.incomingFromMs to startAtMs + plan.overlapMs + plan.gapMs
            else -> 0L to startAtMs
        }
    }

    /** How far the next song's beats are from the old song's (ms; ahead is positive), from where each is heard (µs). */
    fun beatErrorMs(beat: BeatMatch.Plan, outgoingUs: Long, incomingUs: Long): Long =
        Math.round(incomingUs / 1000.0 - (beat.incomingStartMs + (outgoingUs / 1000.0 - beat.startMs) * beat.speed))
}
