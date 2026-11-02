package app.siansa.playback

import kotlin.math.abs

/**
 * Keeps the silent tail in step with the main player before a fade (issue #807). Pure maths; [Crossfader] drives
 * the player.
 *
 * It used to play up to a quarter faster or slower by how far behind it was, re-judged every 50 ms. A change of
 * speed takes a few hundred ms to show in the measured position (the sound already in the phone's and the
 * Bluetooth buffers plays at the old speed), so that overshot and swung: +50, +200, -30, -150, -220 ms over the
 * 3 s it had, and most fades began with the tail 40 to 150 ms off, still at 0.75×.
 *
 * Both play at the same speed, so the gap between them is a constant, not a drift: it needs one measurement and
 * one jump, not a chase. After the tail starts (or jumps) its position isn't real for a moment ([SETTLE_MS]: a
 * newly started track reports where the buffer is, not what's heard), so nothing is judged until then. Then the
 * middle of a few readings is the gap; a jump to just ahead of the main player by [lead] closes it, and what's
 * left after that jump says what a jump costs on this phone, which the next one allows for ([learnedLead]). At
 * most [MAX_JUMPS] jumps; after them, and for anything under [JUMP_MS], the speed nudges it in step over a few
 * seconds ([nudge]): tiny, so a nudge can't overshoot by more than it corrects.
 */
class TailSync(lead: Long = DEFAULT_LEAD_MS) {
    sealed interface Action {
        /** Nothing yet: play at the main player's speed. */
        data object Wait : Action
        /** Jump to this position of the song (just ahead of the main player). */
        data class Jump(val targetMs: Long, val jumps: Int) : Action
        /** Play at the main player's speed times this. */
        data class Speed(val factor: Float, val behindMs: Long) : Action
    }

    /** What a jump costs on this phone, learned: how far ahead of the main player to send the tail. */
    var learnedLead: Long = lead.coerceIn(0L, MAX_LEAD_MS)
        private set
    private var settledAt = 0L
    private val recent = ArrayDeque<Long>()
    private var jumps = 0
    /** The lead of the jump in flight, until its settled gap has been measured. */
    private var jumpLead: Long? = null

    /** The tail just started playing silently at [now]. */
    fun primed(now: Long) {
        settledAt = now + SETTLE_MS
        recent.clear()
        jumps = 0
        jumpLead = null
    }

    val jumpsMade: Int get() = jumps

    /**
     * One check at [now]: the tail is [behindMs] behind the main player (ahead is negative), which is at
     * [mainPositionMs] of the song.
     */
    fun step(now: Long, behindMs: Long, mainPositionMs: Long): Action {
        if (now < settledAt) return Action.Wait
        recent.addLast(behindMs)
        if (recent.size > READINGS) recent.removeFirst()
        if (recent.size < READINGS) return Action.Wait
        val gap = recent.sorted()[recent.size / 2]
        jumpLead?.let { lead ->
            // The jump went [lead] ahead and left the tail [gap] behind, so it cost lead + gap.
            val cost = lead + gap
            if (cost in 0..MAX_LEAD_MS) learnedLead = cost
            jumpLead = null
        }
        if (abs(gap) > JUMP_MS && jumps < MAX_JUMPS) {
            jumps++
            jumpLead = learnedLead
            settledAt = now + SETTLE_MS
            recent.clear()
            return Action.Jump(mainPositionMs + learnedLead, jumps)
        }
        return Action.Speed(nudge(gap), gap)
    }

    companion object {
        /** After the tail starts or jumps, how long before its position is trusted. */
        const val SETTLE_MS = 800L
        /** How many checks (50 ms apart) the gap is the middle of: one reading can be well off and back the next. */
        const val READINGS = 5
        /** Further off than this, the tail jumps (while it may); closer, the speed brings it in. */
        const val JUMP_MS = 40L
        const val MAX_JUMPS = 2
        /** How far ahead of the main player a jump is sent at first, before this phone's cost is known. */
        const val DEFAULT_LEAD_MS = 100L
        const val MAX_LEAD_MS = 600L
        /** Within this, in step: no nudge. */
        const val IN_STEP_MS = 10L
        /** A nudge brings the tail in step over about this long (it's silent, so a little quicker than the next
         *  song's [BeatMatch.NUDGE_OVER_MS]), never more than [NUDGE_MAX] off the main player's speed. */
        const val NUDGE_OVER_MS = 3_000f
        const val NUDGE_MAX = 0.015f

        /** The speed that brings a tail [behindMs] behind in step: a touch faster when behind, slower when ahead. */
        fun nudge(behindMs: Long): Float =
            if (abs(behindMs) <= IN_STEP_MS) 1f else (1f + behindMs / NUDGE_OVER_MS).coerceIn(1f - NUDGE_MAX, 1f + NUDGE_MAX)
    }
}
