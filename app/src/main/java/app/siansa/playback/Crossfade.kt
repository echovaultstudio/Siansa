package app.siansa.playback

import app.siansa.data.AppSettings
import app.siansa.data.ReplayGainMode
import app.siansa.data.Song
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Crossfade between songs (issue #60): off, a fixed length, or smart. */
enum class CrossfadeMode(val label: String) {
    OFF("Off"),
    FIXED("Fixed"),
    SMART("Smart");

    companion object {
        fun parse(value: String?): CrossfadeMode = entries.firstOrNull { it.name == value } ?: OFF
    }
}

/**
 * How one change of song is blended: the next song starts [overlapMs] before the current one ends. The
 * current song fades out over the whole overlap; the next fades in over its first [fadeInMs]. In smart mode the
 * current song can end early: its last [cutMs] (silence, or the end of a long fade-out) isn't played (issue #628).
 * A [beat]-matched blend (issue #646) also says where the next song starts and how fast it plays.
 */
data class FadePlan(
    val overlapMs: Long,
    val fadeInMs: Long,
    val why: String,
    val cutMs: Long = 0L,
    val beat: BeatMatch.Plan? = null,
    /** In smart mode, what the plugin measured at both ends and why the blend wasn't beat-matched (issue #744). */
    val beatNote: String? = null,
    /**
     * A cold end (a hard ending into a hard start): no overlap. The old song plays to its last sound, then [gapMs]
     * of pause (none when both keep a beat), then the next song from its start.
     */
    val coldEnd: Boolean = false,
    /** A cold end's pause; 0 for every other blend, and for a cold end where both songs keep a beat. */
    val gapMs: Long = 0L,
)

/**
 * Plans each blend. Smart mode uses the songs' ramps (the MixRamp idea): the current song's quiet tail plus
 * the time the next song takes to get going is how long they overlap, so:
 * - a long fade-out starts blending early, and a hard ending doesn't blend at all;
 * - a slow intro fades in slowly, and a hard hit comes in at once;
 * - a hard ending into a hard start doesn't overlap at all: a short pause between them, as a radio DJ plays a cold end;
 * - songs that run straight on within one album (live albums, DJ mixes) aren't faded, so they stay gapless.
 * Without ramps, smart mode falls back to the fixed length (still keeping album runs gapless).
 */
object Crossfade {
    /**
     * Whether a song may blend into the next: never when either is a radio station or a podcast episode (issue #353).
     * A live stream has no end to fade out of, and talk shouldn't be faded or beat-matched.
     */
    fun blends(fromId: String, toId: String): Boolean =
        !app.siansa.data.OutsideMedia.isOutside(fromId) && !app.siansa.data.OutsideMedia.isOutside(toId)

    /** The level (dB below the song's own loudness) that counts as "quiet" for the ramps. */
    const val QUIET_DB = -18

    /** A song's end or start that stays quiet at least this long counts as a quiet one for a long DJ blend. */
    const val QUIET_END_MS = 6_000L

    /** The blend when a beatless end isn't a quiet one: long enough to be a blend, short enough not to cut the song. */
    const val BEATLESS_LOUD_MS = 6_000L

    /**
     * Whether a DJ blend with a beatless end runs the whole crossfade length: a beatless intro slides in under the
     * old song, as a DJ would; but a beatless song going out only gets that when it winds down (a quiet tail of
     * [QUIET_END_MS] or more), and two beatless songs only when one of them is quiet there.
     */
    fun beatlessRunsLong(outBeatless: Boolean, inBeatless: Boolean, tailMs: Long, headMs: Long): Boolean = when {
        inBeatless && !outBeatless -> true
        outBeatless && !inBeatless -> tailMs >= QUIET_END_MS
        else -> tailMs >= QUIET_END_MS || headMs >= QUIET_END_MS
    }

    /** Shorter overlaps than this just play the songs back to back. */
    const val MIN_OVERLAP_MS = 300L

    /** A song's end with a quiet tail shorter than this is a hard ending: loud to its last sound. */
    const val COLD_TAIL_MS = 1_000L

    /** A song's start that is loud within this long is a hard start: it hits at once. */
    const val COLD_HEAD_MS = 300L

    /** A cold end's pause between the two songs; [COLD_GAP_FAR_MS] when their tempos are far apart or unknown. */
    const val COLD_GAP_MS = 400L
    const val COLD_GAP_FAR_MS = 600L

    /** The old song's last sound is faded over at least this long in a cold end, only so it can't click. */
    const val COLD_OUT_MS = 50L

    /**
     * A cold end (0.52.0-dev: a loud math-rock ending laid half a second over a loud start sounded like a mistake): a
     * hard ending (a quiet tail under [COLD_TAIL_MS]) into a hard start (loud within [COLD_HEAD_MS]). Two loud edges
     * can't share an overlap, so none: the old song plays to its very last sound, a short pause, then the next song
     * from its start, as a radio DJ plays a cold end. Both songs stay whole. Null when either edge is soft, so every
     * other blend is as before.
     *
     * When both edges keep a beat ([bothBeat]: dance music, a DJ radio), no pause: a silence between two beats is a
     * gap in the music (0.52.0-dev, 20:15: house and drum and bass in a DJ radio had 0.4 to 0.6 s of silence between
     * songs). The next song starts right as the old one's last sound ends, still without overlap.
     */
    fun coldEnd(tailMs: Long, headMs: Long, cutMs: Long, outBpm: Double?, inBpm: Double?, bothBeat: Boolean = false): FadePlan? {
        if (tailMs >= COLD_TAIL_MS || headMs >= COLD_HEAD_MS) return null
        val gap = if (bothBeat) 0L else coldGap(outBpm, inBpm)
        val then = if (bothBeat) "then the next song straight on (both keep a beat, so no pause)" else "then a ${gap} ms pause"
        return FadePlan(
            overlapMs = tailMs.coerceAtLeast(COLD_OUT_MS),
            fadeInMs = MIN_FADE_IN_MS,
            why = "smart: a hard ending into a hard start (${tailMs} ms tail + ${headMs} ms intro), so no overlap: the old song plays to its last sound, $then",
            cutMs = cutMs,
            coldEnd = true,
            gapMs = gap,
        )
    }

    /** A cold end's pause: a little longer when the tempos are far apart (more than 15 %) or unknown, a bigger change. */
    fun coldGap(outBpm: Double?, inBpm: Double?): Long {
        if (outBpm == null || inBpm == null || outBpm <= 0 || inBpm <= 0) return COLD_GAP_FAR_MS
        val ratio = inBpm / outBpm
        return if (ratio in 0.87..1.15) COLD_GAP_MS else COLD_GAP_FAR_MS
    }

    /** The shortest fade-in, so even a hard hit doesn't click in. */
    const val MIN_FADE_IN_MS = 150L

    /** Below this (dB under the song's own loudness) a song's end is near-silent: dead air, never music (issue #628). */
    const val SILENT_DB = -48

    /** A fade-out is cut once it's this quiet; the rest goes, so the next song comes in while it's still fading. */
    const val FADED_DB = -24

    /** Silence at the end shorter than this is left alone: it's just the gap between songs. */
    const val MIN_SILENCE_MS = 1_000L

    /**
     * A fade-out's levels must be at least this far apart to count as one (getting steadily quieter): a quiet
     * ending that holds at one low level (a soft coda) isn't a fade-out, and is played (issue #628).
     */
    const val FADE_STEP_MS = 1_500L

    /** Never cut into the first three quarters of a song. */
    const val MAX_CUT_FRACTION = 0.25

    /**
     * How much of the end of a song to leave out in smart mode (issue #628), and why: the near-silence at its end
     * (below [SILENT_DB]), plus, when it ends in a real fade-out, the part of the fade quieter than [FADED_DB], at
     * most [maxMs] of it. Only what runs to the very end counts: the ramps measure backwards from the last moment,
     * so a silence or a quiet passage mid-song, with music after it, never does. Never more than a quarter of the
     * song. 0 when there's nothing to leave out.
     */
    fun endCut(profile: TrackProfile?, durationMs: Long, maxMs: Long): Pair<Long, String?> {
        if (profile == null || durationMs <= 0) return 0L to null
        val silence = (profile.lastAtExactlyMs(SILENT_DB) ?: 0L).takeIf { it >= MIN_SILENCE_MS } ?: 0L
        val f18 = profile.lastAtExactlyMs(QUIET_DB)
        val f24 = profile.lastAtExactlyMs(FADED_DB)
        val f36 = profile.lastAtExactlyMs(-36)
        // A fade-out passes through each level in turn, seconds apart: −18, then −24, then −36 dB, then silence.
        val fading = f18 != null && f24 != null && f36 != null &&
            f18 - f24 >= FADE_STEP_MS && f24 - f36 >= FADE_STEP_MS && f24 > silence
        val fadeCut = if (fading) (f24!! - silence).coerceAtMost(maxMs).coerceAtLeast(0L) else 0L
        val wanted = silence + fadeCut
        if (wanted <= 0L) return 0L to null
        val cut = wanted.coerceAtMost((durationMs * MAX_CUT_FRACTION).toLong())
        val why = listOfNotNull(
            silence.takeIf { it > 0 }?.let { "${seconds(it)} of silence" },
            fadeCut.takeIf { it > 0 }?.let { "${seconds(it)} of fade-out" },
        ).joinToString(" and ") + (if (cut < wanted) " (held to a quarter of the song)" else "")
        return cut to "left out the last $why"
    }

    private fun seconds(ms: Long): String = "%.1f s".format(java.util.Locale.ROOT, ms / 1000.0)

    /** A beatless end shorter than this is left to the other blends. */
    const val AFTER_BEAT_MIN_MS = 6_000L
    /** The longest blend after the last beat: a long ambient outro is joined for its last half minute. */
    const val AFTER_BEAT_MAX_MS = 30_000L
    /** The next song fades in over this, then plays at its own level over the outro. */
    const val AFTER_BEAT_FADE_IN_MS = 2_000L

    /**
     * A blend after the last beat, or null when the song doesn't end in a beatless outro worth one: the next song
     * comes in one beat after the last (so the last beat is heard whole), and the outro plays out under it to the
     * end of its sound, fade and all; only the silence after it is left out. At most [AFTER_BEAT_MAX_MS] (then it
     * comes in that long before the end) and a third of the shorter song.
     *
     * Only an outro that really winds down (quiet, under [QUIET_DB], for [QUIET_END_MS] or more before its sound ends)
     * gets that long blend. A beatless outro that stays loud (0.52.0-dev, 20:06: a loud track's 33.6 s outro played
     * 30 s under the next song's start, two songs at full volume) gets a short one at its very end, [BEATLESS_LOUD_MS],
     * as the DJ blends do ([beatlessRunsLong]). An outro whose loudness wasn't measured counts as loud.
     */
    fun afterLastBeat(profile: TrackProfile?, durationMs: Long, nextDurationMs: Long): FadePlan? {
        val lastBeat = profile?.beatsUntilMs ?: return null
        if (durationMs <= 0) return null
        val silence = (profile.lastAtExactlyMs(SILENT_DB) ?: 0L).takeIf { it >= MIN_SILENCE_MS } ?: 0L
        val soundEnds = durationMs - silence.coerceAtMost((durationMs * MAX_CUT_FRACTION).toLong())
        val beat = profile.outro?.beatLengthMs?.toLong() ?: 0L
        val comesIn = lastBeat + beat
        val tail = soundEnds - comesIn
        if (tail < AFTER_BEAT_MIN_MS) return null
        val shortest = listOf(soundEnds, nextDurationMs).filter { it > 0 }.minOrNull() ?: return null
        val cut = durationMs - soundEnds
        // How long the outro is quiet before its sound ends (the silence after it isn't counted).
        val quiet = ((profile.quietForLastMs(QUIET_DB) ?: 0L) - cut).coerceAtLeast(0L)
        val windsDown = quiet >= QUIET_END_MS
        val overlap = minOf(tail, if (windsDown) AFTER_BEAT_MAX_MS else BEATLESS_LOUD_MS, shortest / 3)
        if (overlap < AFTER_BEAT_MIN_MS) return null
        val why = "smart: after the last beat (${clockOf(lastBeat)}), over ${seconds(tail)} of beatless outro" +
            (if (!windsDown) " that stays loud (${seconds(quiet)} quiet at its end), so only its last ${seconds(overlap)}"
            else if (overlap < tail) ", its last ${seconds(overlap)}" else "") +
            (if (cut > 0) " · left out the last ${seconds(cut)} of silence" else "")
        return FadePlan(overlap, AFTER_BEAT_FADE_IN_MS.coerceAtMost(overlap), why, cut, beatNote = "the beat stops at ${clockOf(lastBeat)}, so no beat-matching is needed")
    }

    private fun clockOf(ms: Long): String = "%d:%02d".format(java.util.Locale.ROOT, ms / 60_000, ms / 1000 % 60)

    fun plan(
        mode: CrossfadeMode,
        maxMs: Long,
        current: Song,
        next: Song,
        currentDurationMs: Long,
        nextDurationMs: Long,
        currentProfile: TrackProfile? = null,
        nextProfile: TrackProfile? = null,
        beatMatch: Boolean = false,
        blendBars: Int = BeatMatch.BAR_CHOICES[1],
    ): FadePlan? {
        if (mode == CrossfadeMode.OFF || maxMs <= 0) return null
        // An album playing in order stays gapless, Smart or Fixed (the owner, 9 Nov).
        if (runsStraightOn(current, next)) return null

        // Smart: the dead air and the long fade-out at the end are left out first (issue #628); the blend is planned
        // from the song's new end.
        val (cut, cutWhy) = if (mode == CrossfadeMode.SMART) endCut(currentProfile, currentDurationMs, maxMs) else 0L to null
        // A beatless outro (plugin 0.5.0): the next song comes in after the last beat, and the outro, with its own
        // fade, plays out under it. Nothing to lay beat on beat, so no clash and no change of speed.
        if (mode == CrossfadeMode.SMART && !continuousMix(current) && !continuousMix(next)) {
            afterLastBeat(currentProfile, currentDurationMs, nextDurationMs)?.let { return it }
        }
        // Where the plugin found no bars or phrases, a track made on a grid has them assumed from its first beat (issue #820).
        val outro = currentProfile?.outro?.let { BeatMatch.assumedGrid(currentProfile.intro?.let(BeatMatch::gridded), BeatMatch.gridded(it)) }
        val intro = nextProfile?.intro?.let { BeatMatch.assumedIntro(BeatMatch.gridded(it)) }
        // Both ends keep a beat (steady or not): unless laid beat on beat, a long blend clashes (issue #796).
        val rhythmic = mode == CrossfadeMode.SMART && BeatMatch.wouldClash(outro, intro)
        // Beat-matched (issue #646): a DJ blend over whole bars, beat on beat, when both ends have a steady beat;
        // a quiet, steady intro with clear bars is laid on the beat too (issue #759), but doesn't count as a clash.
        // A track of a continuous mix already blends into its neighbours (issue #807): a third song laid over it clashes.
        // Siansa Dev: a track Siansa Beats found cut from a mixed CD counts too; with its lock known, a DJ blend is laid
        // inside it, kept short (the owner, 9 Nov), rather than never beat-matched.
        val currentMix = continuousMix(current) || currentProfile?.mixTrack == true
        val nextMix = continuousMix(next) || nextProfile?.mixTrack == true
        val insideLock = (!currentMix || outro?.lockMs != null) && (!nextMix || intro?.lockMs != null)
        // Fixed beat-matches too, in DJ playback, with the listener's bars (the owner, 9 Nov).
        val beatMode = mode == CrossfadeMode.SMART || (mode == CrossfadeMode.FIXED && beatMatch)
        val mixed = beatMode && (currentMix || nextMix) && !(beatMatch && insideLock)
        val matchable = beatMode && !mixed && outro?.matchable == true && intro?.matchableAsIntro == true
        // The song going out is let go where its lock ends, when that's before its end (the DJ's next record follows).
        val songEnd = currentDurationMs - cut
        val endMs = outro?.lockEndMs?.toLong()?.takeIf { it in 1 until songEnd } ?: songEnd
        val matched = if (matchable && beatMatch && currentDurationMs > 0) {
            val bars = BeatMatch.barsFor(outro!!, intro!!, blendBars).let { if (currentMix || nextMix) minOf(it, BeatMatch.MIX_TRACK_BARS) else it }
            BeatMatch.plan(outro, intro, endMs, BeatMatch.maxMsFor(outro, bars))
        } else null
        // A cold end: a hard ending into a hard start never overlaps (both songs' edges loud), unless it's laid beat on
        // beat, or it's a DJ blend that brings a beatless song in under the other for the whole crossfade length.
        if (mode == CrossfadeMode.SMART && matched == null && !mixed && currentProfile != null && nextProfile != null) {
            val tail = ((currentProfile.quietForLastMs(QUIET_DB) ?: 0L) - cut).coerceAtLeast(0L)
            val head = nextProfile.reachesAfterMs(QUIET_DB) ?: 0L
            val djLong = beatMatch && !rhythmic && (BeatMatch.beatless(outro) || BeatMatch.beatless(intro)) &&
                beatlessRunsLong(BeatMatch.beatless(outro), BeatMatch.beatless(intro), tail, head)
            if (!djLong) {
                coldEnd(tail, head, cut, outro?.bpm, intro?.bpm, bothBeat = BeatMatch.someBeat(outro) && BeatMatch.someBeat(intro))?.let { cold ->
                    val note = if (outro != null || intro != null) BeatMatch.note(outro, intro, beatMatch) else null
                    return cold.copy(why = cold.why + (cutWhy?.let { " · $it" } ?: ""), beatNote = note)
                }
            }
        }
        var plan = if (matched != null) {
            // A phrase-on-phrase blend may let the song go at the end of its last phrase (issue #757).
            val lockCut = songEnd - endMs
            FadePlan(matched.overlapMs, matched.overlapMs, matched.why + (if (lockCut > 0) ", let go where its beat stops locking (%.1f s before its end)".format(java.util.Locale.ROOT, (lockCut + cut) / 1000.0) else ""),
                cut + lockCut + matched.cutMs, matched)
        } else if (mode == CrossfadeMode.SMART && currentProfile != null && nextProfile != null) run {
            val tail = ((currentProfile.quietForLastMs(QUIET_DB) ?: 0L) - cut).coerceAtLeast(0L)
            val head = nextProfile.reachesAfterMs(QUIET_DB) ?: 0L
            var overlap = (tail + head).coerceAtMost(maxMs)
            var why = "smart: ${tail} ms tail + ${head} ms intro"
            if ((mixed || currentMix || nextMix) && rhythmic) {
                // A continuous mix can't be laid on a beat, and a cut into or out of one is heard: a short blend (issue #807).
                overlap = BeatMatch.CLASH_MS
                why += " · a continuous mix, so a short blend" + notStraightOn(current, next)
            } else if (rhythmic && overlap > BeatMatch.CLASH_MS) {
                // Both ends keep a beat that can't be laid on the other's: a long blend would clash, so a short one.
                overlap = BeatMatch.CLASH_MS
                why += " · beats would clash, so a short blend"
            } else if (beatMatch && !rhythmic && !mixed && overlap < maxMs && (BeatMatch.beatless(outro) || BeatMatch.beatless(intro))) {
                if (beatlessRunsLong(BeatMatch.beatless(outro), BeatMatch.beatless(intro), tail, head)) {
                    // A DJ blend where one end has no beat at all (0.52.0-dev: a beatless intro under a beat-driven
                    // outro got 3.3 s with 30 s set): nothing to clash with, so it runs the whole crossfade length, as
                    // a DJ brings a quiet intro in under the old song. The next song fades in over the first half of it.
                    overlap = maxMs
                    why += " · DJ: one end has no beat, nothing to clash, so the whole crossfade length"
                    return@run FadePlan(overlap, maxOf(head, overlap / 2), why, cut)
                }
                // The beatless end is the old song's, still going full on to its end (0.52.0-dev: a 30 s blend began
                // 32 s before the end of a loud song without a steady beat, and the next song's beat ran over it;
                // two folk songs overlapped for 15 s): no clash, but a long blend cuts the song short. A short one.
                overlap = overlap.coerceAtLeast(BEATLESS_LOUD_MS).coerceAtMost(maxMs)
                why += " · DJ: no beat to clash, but the song going out is still going, so a short blend"
            } else if (beatMatch && !rhythmic && !mixed && overlap > BeatMatch.CLASH_MS && BeatMatch.someBeat(outro) && BeatMatch.someBeat(intro)) {
                // Both ends have some beat, one just short of beat-driven (0.52.0-dev: a house remix's outro read
                // 0.46 into Cubicolor's beat ran 30 s out of step): a long blend would clash, so a short one.
                overlap = BeatMatch.CLASH_MS
                why += " · DJ: both ends have some beat, so a short blend"
            }
            FadePlan(overlap, head.coerceIn(MIN_FADE_IN_MS, overlap.coerceAtLeast(MIN_FADE_IN_MS)), why, cut)
        } else if (mode == CrossfadeMode.SMART && BeatMatch.beatDriven(outro) && nextProfile == null) {
            // The song going out keeps a beat and the next song's isn't known, so it can't be laid on it: a long
            // blend would clash for its whole length (20 and 30 s of two beats, issue #796), so a short one.
            val overlap = min(maxMs, BeatMatch.CLASH_MS)
            FadePlan(overlap, overlap, "smart: the next song's beat isn't known, so a short blend", cut)
        } else {
            FadePlan(maxMs, maxMs, if (mode == CrossfadeMode.SMART) "smart without profiles: fixed ${maxMs} ms" else "fixed ${maxMs} ms", cut)
        }
        if (mode == CrossfadeMode.SMART && (outro != null || intro != null)) {
            val note = BeatMatch.note(outro, intro, beatMatch)
            plan = plan.copy(
                beatNote = when {
                    mixed && !note.contains("not beat-matched") -> "$note · not beat-matched: ${if (currentMix) "the song going out" else "the next song"} is part of a continuous mix"
                    matched == null && matchable && beatMatch && !note.contains("not beat-matched") -> "$note · not beat-matched: not even one bar fits before the song's end"
                    else -> note
                },
            )
        }
        // Leaving out the end always needs a blend, however short, or the song would stop dead.
        if (cut > 0 && plan.overlapMs < MIN_OVERLAP_MS) plan = plan.copy(overlapMs = MIN_OVERLAP_MS, fadeInMs = MIN_FADE_IN_MS)
        if (cutWhy != null) plan = plan.copy(why = "${plan.why} · $cutWhy")

        // Never more than a third of either song, so short songs still get heard (a beat-matched blend that long
        // is dropped: its start must stay on the bar).
        val shortest = listOf(currentDurationMs - plan.cutMs, nextDurationMs).filter { it > 0 }.minOrNull()
        if (shortest != null && plan.overlapMs > shortest / 3) {
            val dropped = plan.beat != null
            // A beat-matched blend too long for a short song is laid again over fewer bars that fit (issue #796):
            // dropping it left two steady beats blending out of step for a third of the song.
            val third = listOf(currentDurationMs - cut, nextDurationMs).filter { it > 0 }.minOrNull()?.div(3) ?: 0L
            val shorter = if (dropped) BeatMatch.plan(outro!!, intro!!, currentDurationMs - cut, third)?.takeIf { it.overlapMs <= third } else null
            plan = if (shorter != null) {
                FadePlan(shorter.overlapMs, shorter.overlapMs, shorter.why + " · fewer bars, to fit a short song" + (cutWhy?.let { " · $it" } ?: ""), cut + shorter.cutMs, shorter, plan.beatNote)
            } else {
                // Two steady beats that can't be laid on each other blend only for a moment, as above.
                val overlap = if (dropped && rhythmic) min(shortest / 3, BeatMatch.CLASH_MS) else shortest / 3
                plan.copy(
                    overlapMs = overlap, fadeInMs = min(plan.fadeInMs, overlap).coerceAtLeast(MIN_FADE_IN_MS), beat = null,
                    // A phrase plan's cut (its last part-phrase) goes with it: without the plan it would only lose the ending.
                    cutMs = if (dropped) cut else plan.cutMs,
                    why = if (dropped) (if (overlap < shortest / 3) "smart: beats would clash, so a short blend" else "smart: a third of the shorter song") + (cutWhy?.let { " · $it" } ?: "") else plan.why,
                    beatNote = if (dropped) "${plan.beatNote} · not beat-matched: not even one bar fits in a third of a song" else plan.beatNote,
                )
            }
        }
        return if (plan.overlapMs < MIN_OVERLAP_MS) null else plan
    }

    /**
     * A track of a continuous DJ mix (issue #807), by its title or album: "(Mixed)", "[Mixed]", "Mixed by …",
     * "DJ Mix", "Continuous Mix". Its start and end already hold the neighbouring tracks blended in, so it's never
     * beat-matched: a third song laid over that clashes whatever the timing. Within its album it runs straight on.
     */
    fun continuousMix(song: Song): Boolean {
        val text = "${song.title} ${song.album}".lowercase(java.util.Locale.ROOT)
        return MIX_MARKS.any { it in text }
    }

    private val MIX_MARKS = listOf("(mixed)", "[mixed]", "- mixed", "mixed by", "dj mix", "continuous mix", "continuous dj", "(mix cut)", "[mix cut]")

    /**
     * For the log (issue #816): why two tracks of a continuous mix are blended and not run straight on, when they
     * look like they should. "" when they're not of one album.
     */
    fun notStraightOn(current: Song, next: Song): String {
        if (current.albumId == null || current.albumId != next.albumId || runsStraightOn(current, next)) return ""
        fun place(song: Song) = "track ${song.track ?: "?"}${song.disc?.let { " of disc $it" } ?: ""}"
        return " (one album, but ${place(next)} doesn't follow ${place(current)}, so they don't run straight on)"
    }

    /** The next song carries straight on from this one: same album, the next track on the same or next disc. */
    fun runsStraightOn(current: Song, next: Song): Boolean {
        if (current.albumId == null || current.albumId != next.albumId) return false
        val track = current.track ?: return false
        val nextTrack = next.track ?: return false
        val disc = current.disc ?: 1
        val nextDisc = next.disc ?: 1
        return (nextDisc == disc && nextTrack == track + 1) || (nextDisc == disc + 1 && nextTrack == 1)
    }

    /**
     * Whether a blend got [ready] is planned again because the next song's fade shape [arrived] (issue #796): only
     * when it was planned without one ([plannedWithNext] false), and not once the tail plays ([primed]) or with less
     * than [REPLAN_MS] to go ([untilFadeMs]), when planning again would call the blend off rather than better it.
     */
    fun planAgainForShape(ready: Boolean, plannedWithNext: Boolean, primed: Boolean, untilFadeMs: Long, arrived: Boolean): Boolean =
        ready && !plannedWithNext && !primed && untilFadeMs >= REPLAN_MS && arrived

    /** A blend is planned again for a fade shape that arrived only with at least this long to go. */
    const val REPLAN_MS = 3_000L

    /** The tail may take the song over when it's this close to the main player: further apart, the two copies
     *  played together for the handover would sound like one through a comb (issue #759). */
    const val HANDOVER_STEP_MS = 20L
    /** How long the fade may wait for the tail to get that close (a beat, in a beat-matched blend), before going
     *  ahead anyway. */
    const val HANDOVER_WAIT_MS = 600L
    /** How long the fade may wait for a tail that is still loading (it just jumped to catch up), before playing on
     *  without the fade (issue #767). */
    const val LOADING_WAIT_MS = 1_500L

    /**
     * Whether the fade, due [untilMs] from now (negative: overdue), waits a moment for the tail (issue #759, #767):
     * a primed tail that is still loading waits up to [LOADING_WAIT_MS]; one that plays but is [stepMs] apart from
     * the main player waits up to [waitMs] to get within [HANDOVER_STEP_MS]. Otherwise the fade begins now.
     */
    /**
     * Where the song a fade under way goes into is in the queue now (issue #822), or null when the fade can't go on:
     * the queue was cleared (the player stopped), or that song was taken out (the queue swapped for another).
     * Songs added or taken out around it (a radio topping up) move it, which the fade follows. Without this the
     * fade carried on with the old song playing from its silent copy after the player was stopped, with nothing on
     * screen to stop it.
     */
    fun incomingAfterQueueChange(queue: List<String>, incomingId: String?, incomingIndex: Int): Int? {
        if (queue.isEmpty() || incomingId == null) return null
        if (incomingIndex in queue.indices && queue[incomingIndex] == incomingId) return incomingIndex
        // The same song may be in the queue twice: the place nearest where it was.
        return queue.indices.filter { queue[it] == incomingId }.minByOrNull { kotlin.math.abs(it - incomingIndex) }
    }

    fun waitForTail(primed: Boolean, loading: Boolean, playing: Boolean, untilMs: Long, stepMs: Long, waitMs: Long): Boolean = when {
        !primed -> false
        loading -> untilMs > -LOADING_WAIT_MS
        playing -> untilMs > -waitMs && abs(stepMs) > HANDOVER_STEP_MS
        else -> false
    }

    /**
     * Why there's no fade between two songs, for the log (issue #355: "No fade" alone didn't say). Null when
     * there is one.
     */
    fun whyNoFade(
        mode: CrossfadeMode,
        maxMs: Long,
        current: Song,
        next: Song,
        currentProfile: TrackProfile?,
        nextProfile: TrackProfile?,
        beatMatch: Boolean = false,
    ): String? = when {
        mode == CrossfadeMode.OFF || maxMs <= 0 -> "crossfade is off"
        mode == CrossfadeMode.SMART && runsStraightOn(current, next) -> "they run straight on in one album"
        mode == CrossfadeMode.SMART && currentProfile != null && nextProfile != null -> {
            val tail = currentProfile.quietForLastMs(QUIET_DB) ?: 0L
            val silence = currentProfile.lastAtExactlyMs(SILENT_DB) ?: 0L
            val head = nextProfile.reachesAfterMs(QUIET_DB) ?: 0L
            val outro = currentProfile.outro
            val intro = nextProfile.intro
            val unmatched = when {
                !beatMatch || !BeatMatch.bothRhythmic(outro, intro) -> null
                BeatMatch.speed(outro!!, intro!!) == null -> "; no beat-matching: %.1f and %.1f BPM are too far apart".format(java.util.Locale.ROOT, outro.bpm, intro.bpm)
                else -> "; no beat-matching: not even one bar fits before the song's end"
            }
            if (tail + head < MIN_OVERLAP_MS && silence < MIN_SILENCE_MS) "a hard ending into a quick start (${tail} ms quiet tail + ${head} ms intro), played back to back${unmatched ?: ""}" else null
        }
        else -> null
    }

    /** Volume of the song fading out, [t] from 0 (start of the overlap) to 1 (its end): equal power. */
    fun fadeOut(t: Float): Float = cos(t.coerceIn(0f, 1f) * PI.toFloat() / 2)

    /** Volume of the song fading in, [t] from 0 to 1 over its fade-in: equal power. */
    fun fadeIn(t: Float): Float = sin(t.coerceIn(0f, 1f) * PI.toFloat() / 2)
}

/**
 * Volume levelling from measured loudness (issue #60): the song's level is brought towards [targetLufs].
 * Like ReplayGain through the player volume, it can only turn songs down; [preampDb] applies as with tags.
 */
object Levelling {
    /** Loudness targets: Spotify's default, and one closer to ReplayGain with more headroom. */
    val TARGETS = listOf(-14, -18)

    fun volume(profile: TrackProfile?, targetLufs: Int, preampDb: Float = 0f): Float? {
        val loudness = profile?.refLufs ?: return null
        val gainDb = targetLufs - loudness + preampDb
        return 10.0.pow(gainDb / 20.0).toFloat().coerceIn(0f, 1f)
    }

    /** What evens out a song's loudness (issue #214): one of these, never two at once. */
    enum class Source { NONE, PLUGIN, REPLAYGAIN }

    /**
     * How [song] is levelled: by the Siansa plugin's measurement or by its ReplayGain tags, never both, and
     * the player volume that gives. [why] says in plain words why that one was picked.
     */
    data class Decision(val source: Source, val volume: Float, val why: String) {
        /** The change in level, in dB (0 when nothing is applied). */
        val gainDb: Double get() = if (volume <= 0f) -96.0 else 20 * kotlin.math.log10(volume.toDouble())
    }

    /**
     * Picks the one thing that levels [song]. Levelling (Off, Track or Album) decides whether it's on. With
     * the Siansa plugin as the source, a song it has measured is levelled from that measurement alone; Album
     * mode keeps the album's tags when the song has them, since the plugin measures songs one by one. Every
     * other song uses its ReplayGain tags alone.
     */
    fun decide(song: Song?, settings: AppSettings, profile: TrackProfile?): Decision {
        if (song == null) return Decision(Source.NONE, 1f, "nothing playing")
        if (settings.replayGain == ReplayGainMode.OFF) return Decision(Source.NONE, 1f, "levelling is off")
        val fromPlugin = settings.loudnessSource == LoudnessSource.PLUGIN
        val measured = profile?.takeIf { fromPlugin && it.trackId == song.id }
        val albumTags = settings.replayGain == ReplayGainMode.ALBUM && song.albumGain != null
        if (measured != null && !albumTags) {
            volume(measured, settings.loudnessTarget)?.let { v ->
                return Decision(Source.PLUGIN, v, "measured by the Siansa plugin at ${"%.1f".format(measured.refLufs)} LUFS, brought to ${settings.loudnessTarget} LUFS")
            }
        }
        val hasTags = song.trackGain != null || song.albumGain != null
        val why = when {
            !fromPlugin -> "ReplayGain ${settings.replayGain.label.lowercase()} tags"
            albumTags -> "album mode keeps the album's ReplayGain tags"
            measured != null -> "the Siansa plugin has no loudness for this song, so its ReplayGain tags"
            else -> "the Siansa plugin hasn't measured this song yet, so its ReplayGain tags"
        }
        if (!hasTags) return Decision(Source.NONE, 1f, "$why: the song has none")
        return Decision(Source.REPLAYGAIN, replayGainVolume(song, settings), "$why, brought to ${settings.loudnessTarget} LUFS")
    }

    /** The player volume for [song]: from [decide], so the Siansa plugin and ReplayGain never both apply. */
    fun songVolume(song: Song?, settings: AppSettings, profile: TrackProfile?): Float = decide(song, settings, profile).volume
}
