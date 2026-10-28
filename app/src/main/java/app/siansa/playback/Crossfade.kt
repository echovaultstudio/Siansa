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
 */
data class FadePlan(val overlapMs: Long, val fadeInMs: Long, val why: String, val cutMs: Long = 0L)

/**
 * Plans each blend. Smart mode uses the songs' ramps (the MixRamp idea): the current song's quiet tail plus
 * the time the next song takes to get going is how long they overlap, so:
 * - a long fade-out starts blending early, and a hard ending doesn't blend at all;
 * - a slow intro fades in slowly, and a hard hit comes in at once;
 * - songs that run straight on within one album (live albums, DJ mixes) aren't faded, so they stay gapless.
 * Without ramps, smart mode falls back to the fixed length (still keeping album runs gapless).
 */
object Crossfade {
    /** The level (dB below the song's own loudness) that counts as "quiet" for the ramps. */
    const val QUIET_DB = -18

    /** Shorter overlaps than this just play the songs back to back. */
    const val MIN_OVERLAP_MS = 300L

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

    fun plan(
        mode: CrossfadeMode,
        maxMs: Long,
        current: Song,
        next: Song,
        currentDurationMs: Long,
        nextDurationMs: Long,
        currentProfile: TrackProfile? = null,
        nextProfile: TrackProfile? = null,
    ): FadePlan? {
        if (mode == CrossfadeMode.OFF || maxMs <= 0) return null
        if (mode == CrossfadeMode.SMART && runsStraightOn(current, next)) return null

        // Smart: the dead air and the long fade-out at the end are left out first (issue #628); the blend is planned
        // from the song's new end.
        val (cut, cutWhy) = if (mode == CrossfadeMode.SMART) endCut(currentProfile, currentDurationMs, maxMs) else 0L to null
        var plan = if (mode == CrossfadeMode.SMART && currentProfile != null && nextProfile != null) {
            val tail = ((currentProfile.quietForLastMs(QUIET_DB) ?: 0L) - cut).coerceAtLeast(0L)
            val head = nextProfile.reachesAfterMs(QUIET_DB) ?: 0L
            val overlap = (tail + head).coerceAtMost(maxMs)
            FadePlan(overlap, head.coerceIn(MIN_FADE_IN_MS, overlap.coerceAtLeast(MIN_FADE_IN_MS)), "smart: ${tail} ms tail + ${head} ms intro", cut)
        } else {
            FadePlan(maxMs, maxMs, if (mode == CrossfadeMode.SMART) "smart without profiles: fixed ${maxMs} ms" else "fixed ${maxMs} ms", cut)
        }
        // Leaving out the end always needs a blend, however short, or the song would stop dead.
        if (cut > 0 && plan.overlapMs < MIN_OVERLAP_MS) plan = plan.copy(overlapMs = MIN_OVERLAP_MS, fadeInMs = MIN_FADE_IN_MS)
        if (cutWhy != null) plan = plan.copy(why = "${plan.why} · $cutWhy")

        // Never more than a third of either song, so short songs still get heard.
        val shortest = listOf(currentDurationMs - plan.cutMs, nextDurationMs).filter { it > 0 }.minOrNull()
        if (shortest != null && plan.overlapMs > shortest / 3) {
            val overlap = shortest / 3
            plan = plan.copy(overlapMs = overlap, fadeInMs = min(plan.fadeInMs, overlap).coerceAtLeast(MIN_FADE_IN_MS))
        }
        return if (plan.overlapMs < MIN_OVERLAP_MS) null else plan
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
     * The silent tail's speed while it gets in step (issue #355): [behindMs] behind the main player, it plays up to
     * 6% faster to catch up over about half a second (slower when ahead); within 10 ms it's in step.
     */
    fun tailSpeed(behindMs: Long): Float =
        if (abs(behindMs) <= IN_STEP_MS) 1f else (1f + behindMs / 500f).coerceIn(0.94f, 1.06f)

    /** Closer than this, the tail and the main player count as in step. */
    const val IN_STEP_MS = 10L

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
    ): String? = when {
        mode == CrossfadeMode.OFF || maxMs <= 0 -> "crossfade is off"
        mode == CrossfadeMode.SMART && runsStraightOn(current, next) -> "they run straight on in one album"
        mode == CrossfadeMode.SMART && currentProfile != null && nextProfile != null -> {
            val tail = currentProfile.quietForLastMs(QUIET_DB) ?: 0L
            val silence = currentProfile.lastAtExactlyMs(SILENT_DB) ?: 0L
            val head = nextProfile.reachesAfterMs(QUIET_DB) ?: 0L
            if (tail + head < MIN_OVERLAP_MS && silence < MIN_SILENCE_MS) "a hard ending into a quick start (${tail} ms quiet tail + ${head} ms intro), played back to back" else null
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
        return Decision(Source.REPLAYGAIN, replayGainVolume(song, settings), why)
    }

    /** The player volume for [song]: from [decide], so the Siansa plugin and ReplayGain never both apply. */
    fun songVolume(song: Song?, settings: AppSettings, profile: TrackProfile?): Float = decide(song, settings, profile).volume
}
