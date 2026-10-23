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
