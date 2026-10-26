package app.siansa.data

/**
 * The parts of Home people can move around, in the order a new install shows them. "How are you feeling?"
 * moved to Dscvr (issue #339); saved orders that still name it simply drop it.
 */
enum class HomeSection(val id: String, val label: String) {
    QUICK("quick", "Shortcuts"),
    TODAY("today", "Today's mix"),
    CAROUSEL("carousel", "Your mixes carousel"),
    /** Audiobookshelf's books and podcasts (issue #521); only with a server set up. */
    BOOKS("books", "Your books"),
    PODCASTS("podcasts", "Your podcasts"),
    MADE_FOR_YOU("made", "Made for you"),
    REDISCOVER("rediscover", "Rediscover"),
    RADIOS("radios", "Radios"),
    PLAYLISTS("playlists", "Your playlists"),
    RECENT("recent", "Recently played"),
    ARTIST_RADIOS("artist_radios", "Artist radios"),
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

        /**
         * Reads the saved hidden sections ("radios,genres"), kept apart from the order so older saves still
         * load. Unknown ids are dropped; sections added in a later version aren't in it, so they show.
         */
        fun parseHidden(saved: String?): Set<HomeSection> {
            if (saved.isNullOrBlank()) return emptySet()
            return saved.split(',').mapNotNull { id -> entries.firstOrNull { it.id == id.trim() } }.toSet()
        }

        fun formatHidden(hidden: Set<HomeSection>): String = entries.filter { it in hidden }.joinToString(",") { it.id }

        /** The sections Home shows: [order] without the [hidden] ones. */
        fun visible(order: List<HomeSection>, hidden: Set<HomeSection>): List<HomeSection> = order.filter { it !in hidden }
    }
}

/** [this] with the item at [from] moved to [to]; the same list if either is out of range. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
