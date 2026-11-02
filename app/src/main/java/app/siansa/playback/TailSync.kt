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
 * one jump, not a chase. After the tail starts (or jumps) its position isn't real for a while ([SETTLE_MS]: for
 * about 2.3 s a track just started reports where its buffer is, then snaps by 100 to 270 ms to what's heard;
 * issue #813), so nothing is judged until then. Then the middle of a few readings is the gap; a jump to just ahead
 * of the main player by [lead] closes it, and what's left after that jump says what a jump costs on this phone,
 * which the next one allows for ([learnedLead]). At most [MAX_JUMPS] jumps; after them, and for anything under
 * [JUMP_MS], a nudge: the tail runs [NUDGE_RATE] faster or slower for exactly as long as closes the gap, then
 * back at the main player's speed, and is only judged again once that has shown ([nudgeFor]). Re-judging every
 * 50 ms against a reading seconds old was what left the tail 20 to 50 ms off at the handover.
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
    /** A nudge in flight: its speed factor and when it ends. */
    private var nudgeFactor = 1f
    private var nudgeUntil = 0L

    /** The tail just started playing silently at [now]. */
    fun primed(now: Long) {
        settledAt = now + SETTLE_MS
        recent.clear()
        jumps = 0
        jumpLead = null
        nudgeFactor = 1f
        nudgeUntil = 0L
    }

    val jumpsMade: Int get() = jumps

    /**
     * One check at [now]: the tail is [behindMs] behind the main player (ahead is negative), which is at
     * [mainPositionMs] of the song.
     */
    fun step(now: Long, behindMs: Long, mainPositionMs: Long): Action {
        if (now < nudgeUntil) return Action.Speed(nudgeFactor, behindMs)
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
        if (abs(gap) <= IN_STEP_MS) return Action.Speed(1f, gap)
        // Open loop: run faster or slower for just as long as closes the gap, then judge again once it has shown.
        nudgeFactor = if (gap > 0) 1f + NUDGE_RATE else 1f - NUDGE_RATE
        nudgeUntil = now + nudgeFor(gap)
        settledAt = nudgeUntil + SETTLE_MS
        recent.clear()
        return Action.Speed(nudgeFactor, gap)
    }

    companion object {
        /** After the tail starts, jumps or changes speed, how long before its position is trusted (issue #813). */
        const val SETTLE_MS = 3_000L
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
        /** How much faster or slower a nudge runs: the tail is silent, so nobody hears it. */
        const val NUDGE_RATE = 0.03f

        /** How long a nudge at [NUDGE_RATE] takes to close a gap of [behindMs] (either way): 66 ms takes 2.2 s. */
        fun nudgeFor(behindMs: Long): Long = (abs(behindMs) / NUDGE_RATE).toLong()
    }
}
