package app.siansa.mix

import app.siansa.data.normalizeKey

/**
 * Every song in the library by its title, for matching many songs from Koito or Last.fm at once (the time
 * machine checks up to 150). A library search per song took 10–100 ms each, about 12 s for one month on a
 * 40,000-song library; this is built once and then each match is a lookup.
 *
 * A song matches when its title is the same (in any case, accents and punctuation aside) and its artist is the
 * same, or one contains the other ("alt-J" and "alt-J feat. Conrad Sewell"), or they're the same but for "The"
 * and spaces ("The Beatles" and "Beatles"). The album never matters. When no title is the same, looser titles are
 * tried, in this order (issue #661: songs in the library showed as missing):
 * 1. the same but for spaces and apostrophes ("I'm" and "Im", "Don't" and "Dont");
 * 2. with [versions] only: the same once a version is left out: "- Remastered 2009", "(Mono)", "(feat. Jay-Z)",
 *    "[Radio Edit]", "(Single Version)", "(Deluxe)"… ([VERSION]);
 * 3. with [versions] only: the same before any "(…)", "[…]" or " - …" at all: "Song (Live at Wembley)" finds "Song"
 *    when nothing closer does.
 * Within a step, the same artist exactly comes first. [versions] is for the time machine, where any version of a
 * played song in the library counts; finding the very song AudioMuse-AI means (an acoustic version is another
 * song there) leaves it off.
 */
class SongIndex(songs: List<Entry>, private val versions: Boolean = false) {
    /** A song as the index needs it. */
    data class Entry(val id: String, val title: String, val artist: String)

    private class Keyed(val id: String, val artist: String)

    /** One map per step, strictest first. */
    private val keys = if (versions) TITLE_KEYS else TITLE_KEYS.take(2)
    private val steps: List<Map<String, List<Keyed>>> = keys.map { key ->
        songs.groupBy({ key(it.title) }, { Keyed(it.id, normalizeKey(it.artist)) }).filterKeys { it.isNotEmpty() }
    }

    val size: Int = songs.size

    /** The id of the library's song with this [title] by this [artist], or null when there's none. */
    fun find(artist: String, title: String): String? {
        val a = normalizeKey(artist)
        for ((i, step) in steps.withIndex()) {
            val candidates = step[keys[i](title)] ?: continue
            if (a.isEmpty()) return candidates.first().id
            candidates.firstOrNull { it.artist == a }?.let { return it.id }
            candidates.firstOrNull { sameArtist(it.artist, a) }?.let { return it.id }
        }
        return null
    }

    companion object {
        /** The title keys of each step, strictest first. */
        private val TITLE_KEYS: List<(String) -> String> = listOf(
            { normalizeKey(it) },
            { compactKey(it) },
            { compactKey(withoutVersion(it)) },
            { compactKey(baseTitle(it)) },
        )

        /** Words that make a bracket or a " - …" part a version of the same song, not another song. */
        private val VERSION = Regex(
            "remaster|re-master|mono|stereo|version|edit|single|deluxe|bonus|anniversary|expanded|feat|ft\\.|featuring|" +
                "explicit|clean|original mix|extended|from |soundtrack",
            RegexOption.IGNORE_CASE,
        )
        private val BRACKETS = Regex("\\s*[(\\[]([^)\\]]*)[)\\]]")
        private val DASH = Regex("\\s+[-–—]\\s+(.*)$")
        private val FEAT_OUTSIDE = Regex("\\s+(feat\\.?|ft\\.|featuring)\\s+.*$", RegexOption.IGNORE_CASE)

        /** The normalised title without spaces, so "I'm" ("i m") and "Im" are the same. */
        internal fun compactKey(title: String): String = normalizeKey(title).replace(" ", "")

        /** [title] without the brackets and " - …" parts that only say which version it is. */
        internal fun withoutVersion(title: String): String {
            var out = BRACKETS.replace(title) { m -> if (VERSION.containsMatchIn(m.groupValues[1])) "" else m.value }
            out = DASH.replace(out) { m -> if (VERSION.containsMatchIn(m.groupValues[1])) "" else m.value }
            out = FEAT_OUTSIDE.replace(out, "")
            return out.trim().ifEmpty { title }
        }

        /** [title] before any bracket or " - …" part. */
        internal fun baseTitle(title: String): String {
            val cut = listOf(title.indexOf('('), title.indexOf('['), DASH.find(title)?.range?.first ?: -1).filter { it > 0 }.minOrNull()
            return (if (cut == null) title else title.substring(0, cut)).trim().ifEmpty { title }
        }

        /** The same artist, give or take: one in the other ("alt-J feat. …"), or the same but for "The" and spaces. */
        internal fun sameArtist(library: String, played: String): Boolean {
            if (library.isEmpty() || played.isEmpty()) return false
            if (library.contains(played) || played.contains(library)) return true
            fun bare(a: String) = a.removePrefix("the ").replace(" ", "")
            return bare(library) == bare(played)
        }
    }
}
