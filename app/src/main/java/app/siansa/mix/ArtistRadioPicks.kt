package app.siansa.mix

import app.siansa.data.Artist

/**
 * Which artist radios Home offers (issue #377): always [count] of them when the library has enough artists, a mix
 * of artists you played lately and artists like them, so one long album by a single artist no longer leaves Home
 * with one radio.
 *
 * Up to [recentShare] of [recent] (most recent first) alternate with artists from [similar] (one list per recent
 * artist, taken in turn so each recent artist brings its own), then the rest of [recent], then [fill] (artists you
 * play most, then any). Each artist once.
 */
fun mixArtistRadios(
    recent: List<Artist>,
    similar: List<List<Artist>>,
    fill: List<Artist>,
    count: Int = ArtistRadioPicks.HOME,
    recentShare: Int = ArtistRadioPicks.RECENT_SHARE,
): List<Artist> {
    val seen = HashSet<String>()
    fun fresh(a: Artist) = seen.add(a.id)
    val r = recent.distinctBy { it.id }.take(recentShare).onEach { seen.add(it.id) }
    // Similar artists, one from each recent artist's list in turn.
    val s = ArrayList<Artist>()
    val lists = similar.map { it.toMutableList() }
    while (lists.any { it.isNotEmpty() }) {
        for (list in lists) {
            while (list.isNotEmpty()) {
                val a = list.removeAt(0)
                if (fresh(a)) { s += a; break }
            }
        }
    }
    val out = ArrayList<Artist>()
    val ri = r.iterator()
    val si = s.iterator()
    while ((ri.hasNext() || si.hasNext()) && out.size < count) {
        if (ri.hasNext()) out += ri.next()
        if (si.hasNext() && out.size < count) out += si.next()
    }
    for (a in recent) if (out.size < count && a !in out && fresh(a)) out += a
    for (a in fill) if (out.size < count && a !in out && fresh(a)) out += a
    return out
}

object ArtistRadioPicks {
    /** Artist radios on Home. */
    const val HOME = 10
    /** At most this many of them are artists you played lately; the rest are like them. */
    const val RECENT_SHARE = 6
    /** Recent artists whose similar artists are looked up. */
    const val SIMILAR_FROM = 3
}

/** Whose artists Home's Artist radios are (the owner, 26 Nov: "replace artists radios with ones beats suggest?"). */
enum class ArtistsFrom(val id: String, val label: String) {
    MINE("mine", "Your listening"),
    BEATS("beats", "Siansa Beats"),
    BOTH("both", "Both");

    companion object {
        fun of(id: String?): ArtistsFrom = entries.firstOrNull { it.id == id } ?: BOTH

        /**
         * Artist radios from [mine] (played lately and like them) and Siansa Beats' [beats] (Forgotten artists), [count]
         * in all, each once: Both is yours first, then up to half of them Beats'; Siansa Beats alone falls back on yours
         * while it has none. With each, whether it's Beats'.
         */
        fun <A> pick(from: ArtistsFrom, mine: List<A>, beats: List<A>, count: Int, id: (A) -> String): List<Pair<A, Boolean>> {
            val theirs = beats.distinctBy(id)
            val picked = when {
                from == MINE || theirs.isEmpty() -> mine.take(count).map { it to false }
                from == BEATS -> theirs.take(count).map { it to true }
                else -> {
                    val beatsShare = minOf(theirs.size, count / 2)
                    val ids = theirs.take(beatsShare).mapTo(HashSet(), id)
                    val yours = mine.filter { id(it) !in ids }.take(count - beatsShare)
                    yours.map { it to false } + theirs.take(count - yours.size).map { it to true }
                }
            }
            return picked.distinctBy { id(it.first) }
        }
    }
}

