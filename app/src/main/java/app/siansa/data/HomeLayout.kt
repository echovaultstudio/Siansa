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
    // "For now", as Siansa Beats' Mixes page calls its mixes for this part of the day (the owner, 20 Nov: the same
    // names in both). Its id stays "carousel", so saved orders and looks keep it.
    CAROUSEL("carousel", "For now"),
    /** Audiobookshelf's books and podcasts (issue #521); only with a server set up. */
    BOOKS("books", "Your books"),
    PODCASTS("podcasts", "Your podcasts"),
    MADE_FOR_YOU("made", "Made for you"),
    /** Siansa Beats' On repeat, Rediscover and New for you (the owner, 17 Nov: its own row); empty without them. */
    MORE_OF_YOU("more_of_you", "More of you"),
    /**
     * Your listening (issues #1341, #1343): one card with a page each for hot now, top songs, this year's artists and
     * genres, and the Time Machine's look back lists. Its look is fixed: it's moved and hidden like any row, and its
     * cog has its colour alone (issue #1347). Empty without a listening history. Its id is from when it was the Time Machine card.
     */
    TIME_MACHINE("time_machine", "Your listening"),
    /**
     * On this day (the owner, 27 Nov: "similar to how photo apps show photos from the year before"): this day, this
     * month and last year as memory cards, from Your listening's look back lists. Its look is fixed and it has no cog.
     */
    ON_THIS_DAY("on_this_day", "On this day"),
    /** Custom cards pinned to Home (issue #622); each shows in its own look, in the order set in Settings › Home › Cards. */
    CARDS("cards", "Your cards"),
    REDISCOVER("rediscover", "Rediscover"),
    RADIOS("radios", "Radios"),
    PLAYLISTS("playlists", "Your playlists"),
    RECENT("recent", "Recently played"),
    ARTIST_RADIOS("artist_radios", "Artist radios"),
    ADDED("added", "Recently added"),
    GENRES("genres", "Your genres"),
    RANDOM("random", "Random picks");

    /** Its look can be changed under Arrange Home (issue #655); Your cards each have their own, in Settings › Home › Cards. */
    val lookable: Boolean get() = this != CARDS && this != NOTICES && this != TIME_MACHINE && this != ON_THIS_DAY

    /** It has a cog under Arrange Home: every row but On this day, which has nothing to set. */
    val hasCog: Boolean get() = this != ON_THIS_DAY

    /** It shows the look back lists the phone makes each day ([app.siansa.mix.TimeCards]). */
    val usesTimeCard: Boolean get() = this == TIME_MACHINE || this == ON_THIS_DAY

    /** Its look can't be changed: its cog in Arrange Home has its colour alone (issue #1347). */
    val fixed: Boolean get() = this == TIME_MACHINE

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
    GRID("grid", "Shortcut grid", "Small tiles two to a row, as Shortcuts: 2 to 8 of them"),
    // The owner, 17 Nov: Siansa Beats' row of mixes for any row ("ideally every card always available").
    CAROUSEL("carousel", "Carousel", "Big covers to flick through, the one in the middle in focus"),
    // The owner, 19 Nov (#1263): the cards in a grid across the screen, each with its own picture, as many columns and
    // rows as chosen, the squares as big as the width allows.
    COVER_GRID("cover_grid", "Cover grid", "Covers in a grid across the screen: choose its columns and rows");

    companion object {
        /** The looks with a Size choice under the row's cog (the owner, 21 Nov: tiles too, not only the carousel). */
        val SIZED = setOf(CAROUSEL, TILES)
        /** The looks whose cards have pictures with names, so the Names choice is theirs (the owner, 21 Nov). */
        val NAMED = setOf(AS_IS, TILES, CAROUSEL, COVER_GRID)

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
 * The colour of Home's coloured cards (the owner, 13 Nov: some were too dominant; 25 Nov: the same choices, names and
 * swatches as Your listening's card, [ListenColour]): Today's mix, the book and podcast cards, Made for you, Radios and
 * the other generated tiles. One choice for the whole of Home under Arrange Home, and a row's own under its cog
 * ([AS_HOME] follows Home's). Each is [colour]'s fade, top to foot; [COVER] takes it from the card's own picture.
 * [VIVID] (the owner, 26 Nov: "missing the vivid option") has no colour of its own: each card in its own full colours.
 */
enum class CardLook(val id: String, val colour: ListenColour?) {
    AS_HOME("as_home", null),
    VIVID("vivid", null),
    TEAL("teal", ListenColour.TEAL),
    PLUM("plum", ListenColour.PLUM),
    INK("ink", ListenColour.INK),
    EMBER("ember", ListenColour.EMBER),
    MOSS("moss", ListenColour.MOSS),
    COVER("cover", ListenColour.COVER);

    val label: String get() = colour?.label ?: if (this == VIVID) "Vivid" else "As Home"

    /** What it paints with: [AS_HOME] alone, with nothing narrower given, as [DEFAULT]. */
    val resolved: CardLook get() = if (this == AS_HOME) DEFAULT else this

    companion object {
        /** The choices for the whole of Home: all but [AS_HOME]. */
        val forHome: List<CardLook> = entries.filter { it != AS_HOME }
        /** Vivid, as Home's cards always were before 25 Nov (the owner, 26 Nov). */
        val DEFAULT = VIVID

        /**
         * The look saved as [id]; the strengths chosen before 25 Nov are read as the nearest colour: Soft and Plain as
         * Ink, From the artwork as From its cover. Vivid is a choice again.
         */
        fun of(id: String?): CardLook? = entries.firstOrNull { it.id == id } ?: when (id) {
            "soft", "plain" -> INK
            "artwork" -> COVER
            else -> null
        }

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

/**
 * A [HomeLook.COVER_GRID] row's size (#1263): [columns] across the screen, [rows] down; each card a square as wide as a
 * column, with its own picture (an album's cover, a mix's four covers, an artist's round photo).
 */
data class CoverGridSize(val columns: Int, val rows: Int) {
    /** How many cards it shows. */
    val count: Int get() = columns * rows

    /** Names show under the pictures up to [NAMED_COLUMNS] columns; more make the squares too small for them. */
    val named: Boolean get() = columns <= NAMED_COLUMNS

    val label: String get() = "$columns×$rows"

    companion object {
        val COLUMNS = 2..6
        val ROWS = 1..6
        const val NAMED_COLUMNS = 3
        val DEFAULT = CoverGridSize(3, 2)

        /** Reads the saved sizes ("recent:3x2,radios:5x3", columns by rows); unknown sections and sizes are dropped. */
        fun parse(saved: String?): Map<HomeSection, CoverGridSize> {
            if (saved.isNullOrBlank()) return emptyMap()
            return saved.split(',').mapNotNull { pair ->
                val (section, size) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val s = HomeSection.entries.firstOrNull { it.id == section.trim() } ?: return@mapNotNull null
                val (c, r) = size.trim().split('x').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 } ?: return@mapNotNull null
                if (c in COLUMNS && r in ROWS) s to CoverGridSize(c, r) else null
            }.toMap()
        }

        fun format(sizes: Map<HomeSection, CoverGridSize>): String =
            HomeSection.entries.mapNotNull { s -> sizes[s]?.let { "${s.id}:${it.columns}x${it.rows}" } }.joinToString(",")
    }
}

/**
 * A look's own way of laying its row out (the owner, 20 Nov, from the mockups), one per row and kept with it: the
 * shortcut tiles ([HomeLook.GRID], and Shortcuts in its own look) three a row, big or as pill chips; a cover grid
 * with one featured; a banner as a hero. A row whose look isn't the variant's [look] draws as it otherwise would.
 */
enum class LookVariant(val id: String, val label: String, val look: HomeLook) {
    TWO("two", "Two a row", HomeLook.GRID),
    THREE("three", "Three a row", HomeLook.GRID),
    BIG("big", "Big tiles", HomeLook.GRID),
    CHIPS("chips", "Pill chips", HomeLook.GRID),
    EVEN("even", "Even", HomeLook.COVER_GRID),
    FEATURED("featured", "One featured", HomeLook.COVER_GRID),
    CARD("card", "Card", HomeLook.BANNER),
    HERO("hero", "Hero", HomeLook.BANNER),
    // The hero with the row's next cards at its foot, on the same gradient (the owner, 22 Nov, from the mockup).
    HERO_THREE("hero3", "Hero and 3 cards", HomeLook.BANNER),
    HERO_FOUR("hero4", "Hero and 4 cards", HomeLook.BANNER);

    /** How many of the row's next cards sit at a hero's foot; 0 for a look without. */
    val cardsUnder: Int get() = when (this) { HERO_THREE -> 3; HERO_FOUR -> 4; else -> 0 }

    /** Whether the row is drawn as a hero, alone or with cards under it. */
    val hero: Boolean get() = this == HERO || cardsUnder > 0

    companion object {
        /** The variants of [look]: the first is how it looked before there were any. */
        fun of(look: HomeLook): List<LookVariant> = entries.filter { it.look == look }

        /** Reads the saved variants ("quick:chips,made_for_you:featured"); unknown sections and variants are dropped. */
        fun parse(saved: String?): Map<HomeSection, LookVariant> {
            if (saved.isNullOrBlank()) return emptyMap()
            return saved.split(',').mapNotNull { pair ->
                val (section, id) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val s = HomeSection.entries.firstOrNull { it.id == section.trim() } ?: return@mapNotNull null
                val v = entries.firstOrNull { it.id == id.trim() } ?: return@mapNotNull null
                s to v
            }.toMap()
        }

        fun format(variants: Map<HomeSection, LookVariant>): String =
            HomeSection.entries.mapNotNull { s -> variants[s]?.let { "${s.id}:${it.id}" } }.joinToString(",")
    }
}

/**
 * How a card's picture shows (the owner, 20 Nov: "some do not look good with the cover mosaic"), per row, under its cog
 * in Arrange Home. [AUTO] is how it was: four covers two by two, now only when they sit well together.
 */
enum class PictureStyle(val id: String, val label: String, val blurb: String) {
    AUTO("auto", "Auto", "Mosaic when its covers sit well together, else one cover"),
    MOSAIC("mosaic", "Mosaic", "Four covers"),
    COVER("cover", "One cover", "Its top album"),
    // The sleek ones (the owner, 20 Nov: the loud colour cards "don't suit the sleek style of the app"): black first,
    // its colour as a hint. Ink keeps Colour's id, so a row set to Colour shows Ink.
    // Round three (the owner, 21 Nov: "more outstanding and dynamic", after Apple Music and Tidal). Aura keeps
    // Colour's id, so a row set to Colour or Ink shows Aura; a row set to Line goes back to Auto.
    AURA("colour", "Aura", "Its colours drifting, slow for a calm mix, quicker for a loud one"),
    FROST("frost", "Frost", "Its cover blurred and breathing, its name on glass"),
    POSTER("poster", "Poster", "Its covers washed in one bold colour, its name in heavy type"),
    GLOW("glow", "Glow", "One cover on its own light"),
    PILE("pile", "Stickers", "Its covers stuck on at angles"),
    TAPED("taped", "Taped", "Its covers taped on paper"),
    // Shape (issue #153) was taken out on 27 Nov (the owner): a row set to it shows Auto.
    CUTOUTS("cutouts", "Cut-outs", "Circles and squares, like a scrapbook");

    companion object {
        /** Reads the saved styles ("made_for_you:glow,quick:colour"); unknown sections and styles are dropped. */
        fun parse(saved: String?): Map<HomeSection, PictureStyle> {
            if (saved.isNullOrBlank()) return emptyMap()
            return saved.split(',').mapNotNull { pair ->
                val (section, id) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val s = HomeSection.entries.firstOrNull { it.id == section.trim() } ?: return@mapNotNull null
                val v = entries.firstOrNull { it.id == id.trim() } ?: return@mapNotNull null
                s to v
            }.toMap()
        }

        fun format(styles: Map<HomeSection, PictureStyle>): String =
            HomeSection.entries.mapNotNull { s -> styles[s]?.takeIf { it != AUTO }?.let { "${s.id}:${it.id}" } }.joinToString(",")
    }
}

/**
 * Where a row's picture colours come from (the owner, 20 Nov: "it doesn't always need to come from the covers"): its
 * covers' own, made brighter; the moods of its songs; or a set of colours. Each card of a set takes one of its pairs.
 */
enum class PictureColours(val id: String, val label: String) {
    COVERS("covers", "From covers"),
    MOOD("mood", "By mood"),
    SIANSA("siansa", "Siansa"),
    MONO("mono", "Mono");

    companion object {
        /** Reads the saved choices ("made_for_you:mood,quick:neon"); unknown sections and sets are dropped. */
        fun parse(saved: String?): Map<HomeSection, PictureColours> {
            if (saved.isNullOrBlank()) return emptyMap()
            return saved.split(',').mapNotNull { pair ->
                val (section, id) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val s = HomeSection.entries.firstOrNull { it.id == section.trim() } ?: return@mapNotNull null
                val v = entries.firstOrNull { it.id == id.trim() } ?: return@mapNotNull null
                s to v
            }.toMap()
        }

        fun format(chosen: Map<HomeSection, PictureColours>): String =
            HomeSection.entries.mapNotNull { s -> chosen[s]?.takeIf { it != COVERS }?.let { "${s.id}:${it.id}" } }.joinToString(",")
    }
}

/**
 * Where a row's cards show their names (the owner, 21 Nov: the same choice for every kind of card): [AUTO] as each look
 * does by itself, under the picture, or on it.
 */
enum class CardNames(val id: String, val label: String) {
    AUTO("auto", "Auto"),
    UNDER("under", "Under the picture"),
    ON("on", "On the picture");

    companion object {
        fun parse(saved: String?): Map<HomeSection, CardNames> {
            if (saved.isNullOrBlank()) return emptyMap()
            return saved.split(',').mapNotNull { pair ->
                val (section, id) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val s = HomeSection.entries.firstOrNull { it.id == section.trim() } ?: return@mapNotNull null
                val v = entries.firstOrNull { it.id == id.trim() } ?: return@mapNotNull null
                s to v
            }.toMap()
        }

        fun format(chosen: Map<HomeSection, CardNames>): String =
            HomeSection.entries.mapNotNull { s -> chosen[s]?.takeIf { it != AUTO }?.let { "${s.id}:${it.id}" } }.joinToString(",")
    }
}

/**
 * The colour of Your listening's card on Home (issue #1347; the owner, 23 Nov: "allow choosing the colours for this
 * card"), chosen under its cog in Arrange Home: [top] at its top fading to [foot], with a soft [glow] in a corner.
 * [COVER] takes them from the top song's cover instead (its own are what shows until the cover is read).
 */
enum class ListenColour(val id: String, val label: String, val top: Long, val foot: Long, val glow: Long) {
    TEAL("teal", "Teal", 0xFF1C3A40, 0xFF0F181B, 0xFF3FA39A),
    PLUM("plum", "Plum", 0xFF3A2040, 0xFF150F1B, 0xFFB05AA8),
    INK("ink", "Ink", 0xFF1E1E22, 0xFF0D0D0F, 0xFF8A8A96),
    EMBER("ember", "Ember", 0xFF40261C, 0xFF1B120F, 0xFFD89A3C),
    MOSS("moss", "Moss", 0xFF22382A, 0xFF0F1B13, 0xFF5FA86A),
    COVER("cover", "From its cover", 0xFF2A2A30, 0xFF101013, 0xFF8A8A96);

    companion object {
        fun of(id: String?): ListenColour = entries.firstOrNull { it.id == id } ?: TEAL
    }
}
