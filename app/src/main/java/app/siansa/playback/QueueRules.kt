package app.siansa.playback

import androidx.media3.common.Player

/** What a new queue starts with (beta testers #42). */
object QueueRules {
    /**
     * Whether a change of repeat or shuffle asked for by the app [asker] is taken: only Siansa's own ([own]). The
     * owner's repeat came on by itself at a song change with the phone on his car's Bluetooth, never tapped (12 Nov):
     * a car or headset can ask the phone to change it, and Siansa has no repeat or shuffle button outside the app.
     */
    fun takesModeChange(asker: String, own: String): Boolean = asker == own

    /** Repeat is for the list it was set on: a new list starts with it off, whatever the last one had. */
    fun repeatForNewQueue(@Player.RepeatMode current: Int): Int = Player.REPEAT_MODE_OFF

    /**
     * Where a new list of [size] songs starts when [start] was picked: that song, with the whole list queued around
     * it in order (issue #1238, the owner, 19 Nov: an album started at its fifth song has its first four above it, so
     * shuffle in the player shuffles the whole album). Shuffled, any song. Before that, the songs before the one
     * picked weren't queued (12 Nov), so shuffle only had the rest of the album to shuffle.
     */
    fun startOf(size: Int, start: Int, shuffle: Boolean, random: kotlin.random.Random = kotlin.random.Random): Int = when {
        size <= 0 -> 0
        shuffle -> random.nextInt(size)
        else -> start.coerceIn(0, size - 1)
    }

    /**
     * The order shuffle plays a queue of [count] songs in when it's turned on while [current] plays (issue #1238):
     * the song playing, then the list's other songs in a random order, then the songs Keep playing added ([auto]),
     * as it added them, so they still follow the list instead of being shuffled into it. When a song Keep playing
     * added is playing, the list is over: its songs stay behind (as played) and only the added songs after it are
     * shuffled.
     */
    fun listFirstShuffle(count: Int, current: Int, auto: (Int) -> Boolean, random: kotlin.random.Random = kotlin.random.Random): IntArray {
        if (count <= 0) return IntArray(0)
        val now = current.coerceIn(0, count - 1)
        if (auto(now)) {
            val played = (0 until now).toList()
            val after = (now + 1 until count).shuffled(random)
            return (played + now + after).toIntArray()
        }
        val others = (0 until count).filter { it != now }
        val list = others.filter { !auto(it) }.shuffled(random)
        val added = others.filter { auto(it) }
        return (listOf(now) + list + added).toIntArray()
    }

    /**
     * The moves (from, to), one after another, that turn a queue into [order] (its places, in their new order): each
     * place filled in turn with the song meant for it.
     */
    fun moves(order: IntArray): List<Pair<Int, Int>> {
        val slots = MutableList(order.size) { it }
        val out = ArrayList<Pair<Int, Int>>()
        for (to in order.indices) {
            val from = slots.indexOf(order[to])
            if (from != to) {
                out += from to to
                slots.add(to, slots.removeAt(from))
            }
        }
        return out
    }

    /**
     * The order (the queue's places) that puts a shuffled queue ([now], its songs' ids) back as it was before ([before]):
     * the songs that were there in their old order, then any added since, in theirs. A song twice keeps both places.
     */
    fun unshuffledOrder(now: List<String>, before: List<String>): IntArray {
        val wanted = HashMap<String, ArrayDeque<Int>>()
        before.forEachIndexed { i, id -> wanted.getOrPut(id) { ArrayDeque() }.addLast(i) }
        val placed = now.indices.map { i -> i to (wanted[now[i]]?.removeFirstOrNull()) }
        val known = placed.filter { it.second != null }.sortedBy { it.second }.map { it.first }
        val added = placed.filter { it.second == null }.map { it.first }
        return (known + added).toIntArray()
    }
}
