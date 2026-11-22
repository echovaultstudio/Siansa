package app.siansa.data

/**
 * Star ratings on Now Playing (issue #1043; Settings › Player): off by default, as the top of Now Playing is full
 * already. On, the stars show in About this song (the lower part), under the title, or both: always in one place at
 * least. A song's menu has "Rate" whenever ratings are on.
 */
data class StarRatings(val on: Boolean = false, val underTitle: Boolean = false, val inAbout: Boolean = true) {
    val showsUnderTitle: Boolean get() = on && underTitle
    val showsInAbout: Boolean get() = on && inAbout

    /** With "under the title" switched: taking the last place away puts the stars in About this song. */
    fun withUnderTitle(shown: Boolean): StarRatings = if (!shown && !inAbout) copy(underTitle = false, inAbout = true) else copy(underTitle = shown)

    /** With "in About this song" switched: taking the last place away puts the stars under the title. */
    fun withInAbout(shown: Boolean): StarRatings = if (!shown && !underTitle) copy(inAbout = false, underTitle = true) else copy(inAbout = shown)

    /** One line for the settings section's heading. */
    val summary: String get() = when {
        !on -> "Off"
        underTitle && inAbout -> "Under the title and in About this song"
        underTitle -> "Under the title"
        else -> "In About this song"
    }

    companion object {
        const val MOST = 5

        /** The rating after a tap on [star]: that many stars, or none when it's the rating already. */
        fun tapped(current: Int, star: Int): Int = if (star == current) 0 else star.coerceIn(0, MOST)

        /** "Not rated", "1 star", "4 stars". */
        fun words(stars: Int): String = when (stars) {
            0 -> "Not rated"
            1 -> "1 star"
            else -> "$stars stars"
        }
    }
}
