package app.siansa.playback

import app.siansa.data.AppSettings

/**
 * How songs change, from the crossfade setting (Siansa Dev, the owner's rework of 9 Nov):
 * - **Smart**: the phone decides every change. Nothing to set: fades up to [SMART_MAX_MS], blends of up to
 *   [SMART_BARS] bars (fewer where the songs need it), always with the bass swap.
 * - **Fixed**: every change the same, with the listener's length and, in DJ playback, blend length in bars.
 *
 * Beat-matched blends happen in DJ playback only ([beatMatched]), with either one on: DJ radio, Keep playing in DJ
 * mode, and a DJ mix started from an album or playlist. Anything else gets fades, and an album playing in order
 * stays gapless.
 */
data class Blending(
    val maxMs: Long,
    val blendBars: Int,
    val bassSwap: Boolean,
    val echoTail: Boolean,
    val keepKey: Boolean,
) {
    companion object {
        /** The longest a smart fade is: a beatless intro under a DJ blend gets it all. */
        const val SMART_MAX_MS = 20_000L
        /** The bars a smart blend aims at: two 8-bar phrases. */
        const val SMART_BARS = 16

        fun of(s: AppSettings): Blending = when (s.crossfade) {
            CrossfadeMode.SMART -> Blending(SMART_MAX_MS, SMART_BARS, bassSwap = true, echoTail = s.echoTail, keepKey = s.keepKey)
            else -> Blending(s.crossfadeSeconds * 1000L, s.blendBars, bassSwap = true, echoTail = s.echoTail, keepKey = s.keepKey)
        }

        /** Whether a change is beat-matched: in DJ playback, with crossfade on (Smart or Fixed). */
        fun beatMatched(s: AppSettings, djPlayback: Boolean): Boolean = s.crossfade != CrossfadeMode.OFF && djPlayback
    }
}
