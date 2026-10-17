package app.musix.data

/** The parts of Home people can move around, in the order a new install shows them. */
enum class HomeSection(val id: String, val label: String) {
    QUICK("quick", "Shortcuts"),
    TODAY("today", "Today's mix"),
    MOOD("mood", "How are you feeling?"),
    MADE_FOR_YOU("made", "Made for you"),
    REDISCOVER("rediscover", "Rediscover"),
    RADIOS("radios", "Radios"),
    PLAYLISTS("playlists", "Your playlists"),
    RECENT("recent", "Recently played"),
    ARTIST_RADIOS("artist_radios", "Artist radios"),
    SUGGESTED_ARTISTS("suggested_artists", "Artists you might like"),
    ADDED("added", "Recently added"),
    GENRES("genres", "Your genres"),
    RANDOM("random", "Random picks");

    companion object {
        val default: List<HomeSection> = entries.toList()

        /**
         * Reads a saved order ("today,quick,…"). Unknown ids are dropped, and sections the saved order
         * doesn't have yet (added in a later version) go back in after the section they follow by default.
         */
        fun parse(saved: String?): List<HomeSection> {
            if (saved.isNullOrBlank()) return default
            val order = saved.split(',').mapNotNull { id -> entries.firstOrNull { it.id == id.trim() } }.distinct().toMutableList()
            for (section in entries) {
                if (section in order) continue
                val before = entries.subList(0, section.ordinal).lastOrNull { it in order }
                order.add(if (before == null) 0 else order.indexOf(before) + 1, section)
            }
            return order
        }

        fun format(order: List<HomeSection>): String = order.joinToString(",") { it.id }
    }
}

/** [this] with the item at [from] moved to [to]; the same list if either is out of range. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
