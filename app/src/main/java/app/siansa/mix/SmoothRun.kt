package app.siansa.mix

import kotlin.math.abs
import kotlin.math.min

/**
 * Smooth run (issue #1389; the owner, 26 Nov): songs in an order that flows, key to key and tempo to tempo, as a DJ
 * would play them. The same rules as Siansa Beats' own (its `smooth_chain`), for the phone's mix.
 */
object SmoothRun {
    /** The next song's tempo within this share of the one before (or half or double it). */
    const val TEMPO_LIMIT = 0.06

    /** A run shorter than this isn't worth a mix. */
    const val FEWEST = 8

    /** A key's place on the Camelot wheel: its number (1–12) and A for minor, B for major. */
    data class Camelot(val number: Int, val minor: Boolean)

    private val NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLATS = mapOf("Db" to "C#", "Eb" to "D#", "Gb" to "F#", "Ab" to "G#", "Bb" to "A#")

    /** "C#" and "minor" as [Camelot], or null when either isn't known. */
    fun camelot(key: String?, scale: String?): Camelot? {
        val name = key?.trim()?.let { FLATS[it] ?: it } ?: return null
        val index = NAMES.indexOf(name).takeIf { it >= 0 } ?: return null
        val minor = when (scale?.trim()?.lowercase()) { "minor" -> true; "major" -> false; else -> return null }
        val pc = index - if (minor) 9 else 0  // A minor and C major are 8A and 8B
        return Camelot(Math.floorMod(pc * 7 + 7, 12) + 1, minor)
    }

    /** 0 the same key, 1 a step round the wheel or to the relative major or minor; null a move out of key. */
    fun keyStep(a: Camelot?, b: Camelot?): Int? {
        if (a == null || b == null) return null
        if (a == b) return 0
        val apart = min(Math.floorMod(a.number - b.number, 12), Math.floorMod(b.number - a.number, 12))
        return if ((a.minor == b.minor && apart == 1) || a.number == b.number) 1 else null
    }

    /** How far tempo [b] is from [a], as a share, half and double time counting as the same beat; null unknown. */
    fun tempoGap(a: Double?, b: Double?): Double? {
        if (a == null || b == null || a <= 0 || b <= 0) return null
        return listOf(0.5, 1.0, 2.0).minOf { abs(b * it - a) / a }
    }

    /** A song as the run sees it: its id, tempo, key and how near it is to the mix's sound (0 nearest). */
    data class Step(val id: String, val bpm: Double?, val key: Camelot?, val distance: Double = 0.0)

    /**
     * [songs] as a run of up to [size]: from the one nearest the middle tempo of those known (the first of [preferred]
     * at that tempo when it can), each next the best fit of those left; it stops when nothing fits rather than jolt.
     * Songs without a tempo or a key aren't in it.
     */
    fun order(songs: List<Step>, size: Int, preferred: Set<String> = emptySet()): List<String> {
        val known = songs.filter { it.bpm != null && it.key != null }.distinctBy { it.id }
        if (known.isEmpty()) return emptyList()
        val middle = known.map { it.bpm!! }.sorted().let { it[it.size / 2] }
        val start = known.minWith(compareBy<Step>({ it.id !in preferred }, { abs(it.bpm!! - middle) }, { it.id }))
        val chain = mutableListOf(start)
        val left = known.filter { it != start }.toMutableList()
        while (chain.size < size && left.isNotEmpty()) {
            val last = chain.last()
            val next = left.mapNotNull { s ->
                val step = keyStep(last.key, s.key) ?: return@mapNotNull null
                val gap = tempoGap(last.bpm, s.bpm)?.takeIf { it <= TEMPO_LIMIT } ?: return@mapNotNull null
                s to step * 0.5 + gap / TEMPO_LIMIT + s.distance * 3
            }.minWithOrNull(compareBy<Pair<Step, Double>>({ it.second }, { it.first.id }))?.first ?: break
            chain += next
            left -= next
        }
        return chain.map { it.id }
    }
}
