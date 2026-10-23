package app.siansa.mix

import app.siansa.net.ClapHit
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Picks a vibe's songs for the day (issue #236), with less of what you always hear and more surprise:
 *
 * - your most played songs ([leaveOut]) and songs you skip never come in; songs played in the last days only
 *   when nothing else is left;
 * - [surprisePercent] of the mix comes from the [surprise] pool (AudioMuse alchemy: the vibe's sound, away from
 *   your most played), from artists you don't play much;
 * - artists you play a lot ([familiar], 0..1) and artists in yesterday's mix ([lastArtists]) weigh less;
 * - one artist at most [perArtistCap] times, and a random factor so two days never come out the same.
 */
class MixPicker(
    private val length: Int,
    private val surprisePercent: Int,
    private val leaveOut: Set<String>,
    private val recent: Set<String>,
    private val skipped: Set<String>,
    private val familiar: (String) -> Double,
    private val lastArtists: Set<String>,
    private val random: Random = Random.Default,
) {
    val perArtistCap: Int get() = max(2, length / 13)

    fun pick(core: List<ClapHit>, surprise: List<ClapHit>): List<ClapHit> {
        val allowed = { hit: ClapHit -> hit.id !in leaveOut && hit.id !in skipped }
        val coreOk = core.filter(allowed).distinctBy { it.id }
        val coreIds = coreOk.mapTo(HashSet()) { it.id }
        val surpriseOk = surprise.filter(allowed).filter { it.id !in coreIds && familiar(it.artist) < FAMILIAR }.distinctBy { it.id }
        // Songs heard lately only when there aren't enough others.
        val coreFresh = coreOk.filter { it.id !in recent }.let { if (it.size >= length) it else coreOk }
        val surpriseFresh = surpriseOk.filter { it.id !in recent }

        val wantSurprise = (length * surprisePercent.coerceIn(0, 100) / 100.0).roundToInt()
        val perArtist = HashMap<String, Int>()
        val picked = LinkedHashMap<String, ClapHit>()
        fun take(from: List<ClapHit>, count: Int, capped: Boolean) {
            var taken = 0
            for (hit in ranked(from)) {
                if (taken >= count || picked.size >= length) return
                if (hit.id in picked) continue
                val key = hit.artist.lowercase()
                val n = perArtist[key] ?: 0
                if (capped && n >= perArtistCap) continue
                perArtist[key] = n + 1
                picked[hit.id] = hit
                taken++
            }
        }
        take(surpriseFresh, wantSurprise, capped = true)
        take(coreFresh, length - picked.size, capped = true)
        // Still short (a small library): fill up without the artist cap.
        if (picked.size < length) take(coreFresh + surpriseFresh, length - picked.size, capped = false)
        return picked.values.toList()
    }

    private fun ranked(hits: List<ClapHit>): List<ClapHit> =
        hits.map { it to score(it) }.sortedByDescending { it.second }.map { it.first }

    /** One score per song up front (a sort whose keys change mid-sort fails on Android). */
    private fun score(hit: ClapHit): Double {
        val known = familiar(hit.artist).coerceIn(0.0, 1.0)
        val yesterday = if (hit.artist.lowercase() in lastArtists) 0.6 else 1.0
        return hit.similarity * (0.75 + random.nextDouble() * 0.25) * (1 - 0.35 * known) * yesterday
    }

    companion object {
        /** Above this the artist counts as one you play a lot, and is kept out of the surprise part. */
        const val FAMILIAR = 0.3
    }
}

/** Mix titles that say little ("Unwind with mellow electronic beats") or repeat an earlier one (issue #236). */
object MixTitles {
    val GENERIC = setOf(
        "unwind", "unwinding", "chill", "chilled", "chillout", "vibe", "vibes", "beats", "mellow", "relax", "relaxing",
        "groove", "grooves", "journey", "soundscape", "soundscapes", "tunes", "jams", "melodies", "playlist", "mix",
    )
    private val small = setOf("the", "a", "an", "and", "for", "of", "to", "with", "in", "on", "at", "your", "my")

    private fun words(title: String) = title.lowercase().split(Regex("[^\\p{L}\\p{N}']+")).filter { it.isNotBlank() }

    /** Why [title] isn't good enough, or null when it's fine. */
    fun problem(title: String, previous: List<String>): String? {
        val w = words(title)
        w.firstOrNull { it in GENERIC }?.let { return "it uses the worn-out word \"$it\"" }
        val content = w.filter { it !in small }.toSet()
        for (p in previous) {
            val shared = content intersect words(p).filter { it !in small }.toSet()
            if (shared.size >= 2) return "it shares \"${shared.joinToString(" ")}\" with the earlier title \"$p\""
        }
        return null
    }
}
