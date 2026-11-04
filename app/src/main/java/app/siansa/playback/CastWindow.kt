package app.siansa.playback

/**
 * The part of a long queue the TV holds (issue #114): Cast's messages are limited to 64 KB, and a 497-song queue sent
 * whole never reached the TV (0.52.0-dev, the owner's log: the TV kept its old 11-song queue). The phone keeps the
 * whole queue ([full]); the TV gets the current song with a few before it and the next [ahead], and more as it plays.
 *
 * Index arithmetic only, apart from the players, so it is tested on the computer: "local" indexes are the TV's,
 * "full" ones the phone's.
 */
class CastWindow<T>(
    val behind: Int = BEHIND,
    val ahead: Int = AHEAD,
) {
    /** The whole queue, as the phone has it. */
    val full: MutableList<T> = ArrayList()

    /** Where the TV's queue starts in [full], and how many songs it holds. */
    var start = 0
        private set
    var size = 0
        private set

    val end: Int get() = start + size

    /** A new queue, playing from [index]: the window around it that the TV gets. */
    fun set(items: List<T>, index: Int): IntRange {
        full.clear()
        full.addAll(items)
        return reset(index)
    }

    /** The TV gets a new window around [index] of the queue as it is (a jump far from what it holds). */
    fun reset(index: Int): IntRange {
        if (full.isEmpty()) {
            start = 0
            size = 0
            return IntRange.EMPTY
        }
        if (full.size <= behind + ahead + 1) {
            // A queue that fits goes whole, as before.
            start = 0
            size = full.size
            return start until end
        }
        val i = index.coerceIn(0, full.size - 1)
        start = maxOf(0, i - behind)
        size = minOf(full.size, i + ahead + 1) - start
        return start until end
    }

    /** The TV's index of the full queue's [index], or null when the TV doesn't hold it. */
    fun toLocal(index: Int): Int? = if (index in start until end) index - start else null

    fun toFull(local: Int): Int = start + local

    /** The songs the TV holds now. */
    fun items(): List<T> = full.subList(start, end).toList()

    /**
     * Songs added to the queue at [index]: where the TV adds them too (its index), or null when it's outside what the
     * TV holds (only the phone's queue changes; the window moves along if they were before it).
     */
    fun insert(index: Int, items: List<T>): Int? {
        val at = index.coerceIn(0, full.size)
        full.addAll(at, items)
        return when {
            at < start -> { start += items.size; null }
            at <= end && size > 0 -> { size += items.size; at - start }
            size == 0 -> { start = 0; size = full.size.coerceAtMost(ahead + 1); null }
            else -> null
        }
    }

    /** Songs [from] until [to] removed: the TV's range to remove too, or null when the TV holds none of them. */
    fun remove(from: Int, to: Int): IntRange? {
        val a = from.coerceIn(0, full.size)
        val b = to.coerceIn(a, full.size)
        if (a == b) return null
        full.subList(a, b).clear()
        val lo = maxOf(a, start)
        val hi = minOf(b, end)
        val local = if (lo < hi) (lo - start) until (hi - start) else null
        val beforeStart = (minOf(b, start) - a).coerceAtLeast(0)
        size -= if (lo < hi) hi - lo else 0
        start -= beforeStart
        return local
    }

    /** Songs [from] until [to] replaced by [items] (as many): the TV's range to replace too, or null. */
    fun replace(from: Int, to: Int, items: List<T>): IntRange? {
        if (items.size != to - from) return null
        for (i in from until to) full[i] = items[i - from]
        val lo = maxOf(from, start)
        val hi = minOf(to, end)
        return if (lo < hi) (lo - start) until (hi - start) else null
    }

    /**
     * Songs [from] until [to] moved to [newIndex] (as [androidx.media3.common.Player.moveMediaItems]): the TV's from and
     * new index when it can do the same move in what it holds, else null (a move across its edge).
     */
    fun move(from: Int, to: Int, newIndex: Int): Pair<Int, Int>? {
        val moved = full.subList(from, to).toList()
        full.subList(from, to).clear()
        full.addAll(minOf(newIndex, full.size), moved)
        val inside = from >= start && to <= end && newIndex >= start && newIndex + (to - from) <= end
        return if (inside) (from - start) to (newIndex - start) else null
    }

    /**
     * As the TV plays its song [local]: the songs to add at the end of its queue (so there are always [ahead] after
     * the current one), and how many to drop from its start (so it keeps [behind] before it). Applied with [grown].
     */
    fun topUp(local: Int): TopUp {
        val current = start + local
        val wantEnd = minOf(full.size, current + ahead + 1)
        val add = if (wantEnd - end >= MIN_ADD || (wantEnd == full.size && wantEnd > end)) end until wantEnd else IntRange.EMPTY
        val drop = (local - behind).coerceAtLeast(0).let { if (it >= MIN_DROP) it else 0 }
        return TopUp(add, drop)
    }

    /** [TopUp] sent to the TV: the window follows. */
    fun grown(t: TopUp) {
        size += t.add.count()
        start += t.drop
        size -= t.drop
    }

    /** What the TV should add (full indexes, at the end of its queue) and drop (from its start). */
    data class TopUp(val add: IntRange, val drop: Int) {
        val nothing: Boolean get() = add.isEmpty() && drop == 0
    }

    companion object {
        /** Songs before the current one the TV keeps, for previous. */
        const val BEHIND = 3
        /** Songs after the current one the TV holds: about 25 KB to send, far under Cast's 64 KB. */
        const val AHEAD = 25
        /** Added or dropped a handful at a time, not one by one. */
        const val MIN_ADD = 5
        const val MIN_DROP = 5
    }
}
