package app.siansa.data

/**
 * The parts of Home people can move around, in the order a new install shows them. "How are you feeling?"
 * moved to Dscvr (issue #339); saved orders that still name it simply drop it.
 */
enum class HomeSection(val id: String, val label: String) {
    /** A row of chips: what needs you, what you're partway through, what's new (issue #784). */
    NOTICES("notices", "Notices"),
    QUICK("quick", "Shortcuts"),
    TODAY("today", "Today's mix"),
    CAROUSEL("carousel", "Your mixes carousel"),
    /** Audiobookshelf's books and podcasts (issue #521); only with a server set up. */
    BOOKS("books", "Your books"),
    PODCASTS("podcasts", "Your podcasts"),
    MADE_FOR_YOU("made", "Made for you"),
    /** Custom cards pinned to Home (issue #622); each shows in its own look, in the order set in Settings › Notices and Cards › Cards. */
    CARDS("cards", "Your cards"),
    REDISCOVER("rediscover", "Rediscover"),
    RADIOS("radios", "Radios"),
    PLAYLISTS("playlists", "Your playlists"),
    RECENT("recent", "Recently played"),
    ARTIST_RADIOS("artist_radios", "Artist radios"),
    ADDED("added", "Recently added"),
    GENRES("genres", "Your genres"),
    RANDOM("random", "Random picks");

    /** Its look can be changed under Arrange Home (issue #655); Your cards each have their own, in Settings › Notices and Cards › Cards. */
    val lookable: Boolean get() = this != CARDS && this != NOTICES

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

/**
 * How one of Home's rows looks (issue #655), picked with the cog under Arrange Home: Home's own look, or one of the
 * custom cards' looks (issue #622). Song tiles and Album row are both a row of covers for Home's rows (their items
 * are albums, mixes, radios…), so they're one choice here: Tiles.
 */
enum class HomeLook(val id: String, val label: String, val hint: String) {
    AS_IS("as_is", "As it is", "Home's own look for this row"),
    TILES("tiles", "Tiles", "A row of covers to swipe through"),
    BANNER("banner", "Banner", "One wide card with the first covers and a button"),
    LIST("list", "List", "The first five listed, the rest a tap away"),
    BUTTON("button", "Play button", "A slim card: one tap plays it, or opens it"),
    COVER_ROWS("cover_rows", "Cover rows", "Covers with their details beside them, three to a column, to swipe through"),
    // The owner, 10 Nov: any row as the Shortcuts card is, two small tiles a row, 2 to 8 of them.
    GRID("grid", "Shortcut grid", "Small tiles two to a row, as Shortcuts: 2 to 8 of them");

    companion object {
        /** Reads the saved looks ("recent:list,added:banner"); unknown sections and looks are dropped. */
        fun parse(saved: String?): Map<HomeSection, HomeLook> {
            if (saved.isNullOrBlank()) return emptyMap()
            return saved.split(',').mapNotNull { pair ->
                val (section, look) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val s = HomeSection.entries.firstOrNull { it.id == section.trim() } ?: return@mapNotNull null
                val l = entries.firstOrNull { it.id == look.trim() } ?: return@mapNotNull null
                if (s.lookable && l != AS_IS) s to l else null
            }.toMap()
        }

        /** The most tiles of a [GRID] row, and the choices: up to four rows of two, as Shortcuts. */
        const val GRID_MOST = 8
        val GRID_SIZES = listOf(2, 4, 6, 8)

        /** Reads the saved grid sizes ("recent:4,genres:8"); unknown sections and sizes out of 2..8 are dropped. */
        fun parseGridSizes(saved: String?): Map<HomeSection, Int> {
            if (saved.isNullOrBlank()) return emptyMap()
            return saved.split(',').mapNotNull { pair ->
                val (section, n) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val s = HomeSection.entries.firstOrNull { it.id == section.trim() } ?: return@mapNotNull null
                val size = n.trim().toIntOrNull()?.takeIf { it in 2..GRID_MOST } ?: return@mapNotNull null
                s to size
            }.toMap()
        }

        fun formatGridSizes(sizes: Map<HomeSection, Int>): String =
            HomeSection.entries.mapNotNull { s -> sizes[s]?.let { "${s.id}:$it" } }.joinToString(",")

        fun format(looks: Map<HomeSection, HomeLook>): String =
            HomeSection.entries.mapNotNull { s -> looks[s]?.takeIf { it != AS_IS }?.let { "${s.id}:${it.id}" } }.joinToString(",")
    }
}

/**
 * Whether moving a row from [from] to [to] changes which row is first on screen ([firstVisible]) (#614). A lazy list
 * keeps the row that was first in place by its key, so moving a row up to the top would leave the moved row above the
 * screen, out of sight: the list then has to be told to stay where it is, by position.
 */
fun movesFirstVisible(from: Int, to: Int, firstVisible: Int): Boolean = from != to && (from == firstVisible || to == firstVisible)

/** [this] with the item at [from] moved to [to]; the same list if either is out of range. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * How the coloured cards on Home are painted (the owner, 13 Nov: some were too dominant): Today's mix, the book and
 * podcast cards, Made for you, Radios and the other generated tiles. Never the shortcut grid. One choice for the whole
 * of Home under Arrange Home, and a card's own under its cog ([AS_HOME] follows Home's).
 */
enum class CardLook(val id: String, val label: String, val hint: String) {
    AS_HOME("as_home", "As Home", "The choice made for the whole of Home"),
    VIVID("vivid", "Vivid", "Its own full colour"),
    SOFT("soft", "Soft", "Its colour at a third of its strength over the dark surface"),
    PLAIN("plain", "Plain", "The same grey as the shortcut tiles; colour only in the artwork"),
    ARTWORK("artwork", "From the artwork", "A muted colour taken from its own artwork, as the listening pages' headers");

    companion object {
        /** The choices for the whole of Home: all but [AS_HOME]. */
        val forHome: List<CardLook> = entries.filter { it != AS_HOME }
        val DEFAULT = VIVID

        fun of(id: String?): CardLook? = entries.firstOrNull { it.id == id }

        /** Reads the saved cards' looks ("today:soft,radios:plain"); unknown sections and looks are dropped. */
        fun parse(saved: String?): Map<HomeSection, CardLook> {
            if (saved.isNullOrBlank()) return emptyMap()
            return saved.split(',').mapNotNull { pair ->
                val (section, look) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val s = HomeSection.entries.firstOrNull { it.id == section.trim() } ?: return@mapNotNull null
                val l = of(look.trim()) ?: return@mapNotNull null
                if (l != AS_HOME) s to l else null
            }.toMap()
        }

        fun format(looks: Map<HomeSection, CardLook>): String =
            HomeSection.entries.mapNotNull { s -> looks[s]?.let { "${s.id}:${it.id}" } }.joinToString(",")
    }
}
