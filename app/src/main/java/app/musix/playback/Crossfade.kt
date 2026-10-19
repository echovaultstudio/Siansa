package app.musix.playback

import app.musix.data.AppSettings
import app.musix.data.ReplayGainMode
import app.musix.data.Song
import kotlin.math.PI
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
 * current song fades out over the whole overlap; the next fades in over its first [fadeInMs].
 */
data class FadePlan(val overlapMs: Long, val fadeInMs: Long, val why: String)

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

        var plan = if (mode == CrossfadeMode.SMART && currentProfile != null && nextProfile != null) {
            val tail = currentProfile.quietForLastMs(QUIET_DB) ?: 0L
            val head = nextProfile.reachesAfterMs(QUIET_DB) ?: 0L
            val overlap = (tail + head).coerceAtMost(maxMs)
            FadePlan(overlap, head.coerceIn(MIN_FADE_IN_MS, overlap.coerceAtLeast(MIN_FADE_IN_MS)), "smart: ${tail} ms tail + ${head} ms intro")
        } else {
            FadePlan(maxMs, maxMs, if (mode == CrossfadeMode.SMART) "smart without profiles: fixed ${maxMs} ms" else "fixed ${maxMs} ms")
        }

        // Never more than a third of either song, so short songs still get heard.
        val shortest = listOf(currentDurationMs, nextDurationMs).filter { it > 0 }.minOrNull()
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
     * How [song] is levelled: by the Musix plugin's measurement or by its ReplayGain tags, never both, and
     * the player volume that gives. [why] says in plain words why that one was picked.
     */
    data class Decision(val source: Source, val volume: Float, val why: String) {
        /** The change in level, in dB (0 when nothing is applied). */
        val gainDb: Double get() = if (volume <= 0f) -96.0 else 20 * kotlin.math.log10(volume.toDouble())
    }

    /**
     * Picks the one thing that levels [song]. Levelling (Off, Track or Album) decides whether it's on. With
     * the Musix plugin as the source, a song it has measured is levelled from that measurement alone; Album
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
                return Decision(Source.PLUGIN, v, "measured by the Musix plugin at ${"%.1f".format(measured.refLufs)} LUFS, brought to ${settings.loudnessTarget} LUFS")
            }
        }
        val hasTags = song.trackGain != null || song.albumGain != null
        val why = when {
            !fromPlugin -> "ReplayGain ${settings.replayGain.label.lowercase()} tags"
            albumTags -> "album mode keeps the album's ReplayGain tags"
            measured != null -> "the Musix plugin has no loudness for this song, so its ReplayGain tags"
            else -> "the Musix plugin hasn't measured this song yet, so its ReplayGain tags"
        }
        if (!hasTags) return Decision(Source.NONE, 1f, "$why: the song has none")
        return Decision(Source.REPLAYGAIN, replayGainVolume(song, settings), why)
    }

    /** The player volume for [song]: from [decide], so the Musix plugin and ReplayGain never both apply. */
    fun songVolume(song: Song?, settings: AppSettings, profile: TrackProfile?): Float = decide(song, settings, profile).volume
}
