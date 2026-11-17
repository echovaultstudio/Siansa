package app.siansa.mix

import app.siansa.net.ClapHit
import app.siansa.net.Steer
import app.siansa.net.TrackFeatures
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * The sliders on Dscvr (issue #339): how a vibe is tuned. Moving them re-runs the sound search and re-ranks
 * what it finds, without asking the AI again.
 *
 * - [energy] and [tempo] run from -2 (calmer, slower) to +2 (more energy, faster). They re-rank by AudioMuse's
 *   own energy and tempo for each song (`/api/sync`).
 * - [familiar] runs from -2 (niche: artists you rarely play) to +2 (familiar: artists you play most).
 * - [sounds]: the sounds and genres picked ([FlowMood]), each a dial from -5 to +5: a sound (one of the search's
 *   own concepts) steers the search away from or towards it. AudioMuse takes a steering weight of 1, 2, 3, 5 or 10,
 *   so the five steps either side of the middle are those weights ([STEER_WEIGHTS]). A genre ([SoundDial.genre])
 *   re-ranks the search's songs by how much each is of that genre.
 */
@Serializable
data class VibeTuning(
    val energy: Int = 0,
    val tempo: Int = 0,
    val familiar: Int = 0,
    val sounds: List<SoundDial> = emptyList(),
) {
    /** Nothing moved: the search runs as planned. */
    val isNeutral: Boolean get() = energy == 0 && tempo == 0 && familiar == 0 && sounds.all { it.value == 0 }

    /** Only what's set: without the dials in the middle. Two tunings with the same [set] find the same songs. */
    val set: VibeTuning get() = copy(sounds = sounds.filter { it.value != 0 })

    /** The sound dials as steering for AudioMuse's search: the ones set, each with its weight. */
    fun steering(): List<Steer> = sounds.filter { it.value != 0 && !it.genre && !it.trait }.map { Steer(it.term, it.value > 0, steerWeight(it.value)) }

    /** Every slider and dial back in the middle, keeping which sounds the dials are for (issue #369). */
    fun cleared(): VibeTuning = VibeTuning(sounds = sounds.map { it.copy(value = 0) })

    /** A short description for the log and the vibe page: "more energy · faster · +piano ×3". */
    fun summary(): String = buildList {
        if (energy != 0) add(if (energy > 0) "more energy" else "calmer")
        if (tempo != 0) add(if (tempo > 0) "faster" else "slower")
        if (familiar != 0) add(if (familiar > 0) "familiar" else "niche")
        sounds.filter { it.value != 0 }.forEach {
            add((if (it.value > 0) "+" else "−") + it.term + " ×" + if (it.genre) abs(it.value) else steerWeight(it.value))
        }
    }.joinToString(" · ")

    companion object {
        const val SLIDER_MAX = 2
        const val DIAL_MAX = 5
        const val MAX_SOUNDS = 3

        /** AudioMuse's steering weights, one per step of a sound dial away from the middle. */
        val STEER_WEIGHTS = listOf(1, 2, 3, 5, 10)

        /** Concepts offered as sound dials when the AI picked fewer than [MAX_SOUNDS], most useful first. */
        val DEFAULT_SOUNDS = listOf("aggressive", "acoustic", "vocals", "piano", "electronic", "guitar", "dreamy", "dark")

        /** A dial's position (-5…5) as AudioMuse's weight (1, 2, 3, 5, 10); 0 is no steering. */
        fun steerWeight(value: Int): Int = if (value == 0) 0 else STEER_WEIGHTS[(abs(value).coerceAtMost(DIAL_MAX)) - 1]

        /** The dial position for a steering weight (the AI's concepts start at AudioMuse's default, 3). */
        fun dialFor(steer: Steer): Int {
            val step = STEER_WEIGHTS.indexOf(steer.weight ?: DEFAULT_WEIGHT).let { if (it < 0) STEER_WEIGHTS.indexOf(DEFAULT_WEIGHT) else it } + 1
            return if (steer.more) step else -step
        }

        private const val DEFAULT_WEIGHT = 3

        /**
         * The starting tuning for a vibe: the AI's [steering] as dials, then [concepts] from [DEFAULT_SOUNDS]
         * to make up [MAX_SOUNDS] dials, set to the middle. Only concepts AudioMuse lists are offered.
         */
        fun start(steering: List<Steer>, concepts: List<String>): VibeTuning {
            val known = concepts.map { it.lowercase() }.toSet()
            val fromAi = steering.distinctBy { it.term.lowercase() }.take(MAX_SOUNDS).map { SoundDial(it.term, dialFor(it)) }
            val taken = fromAi.map { it.term.lowercase() }.toSet()
            val extra = DEFAULT_SOUNDS.filter { it in known && it !in taken }.take(MAX_SOUNDS - fromAi.size).map { SoundDial(it, 0) }
            return VibeTuning(sounds = fromAi + extra)
        }
    }
}

/**
 * One sound dial: an AudioMuse concept and how far towards (+) or away from (−) it. [genre]: one of the mix's own
 * sounds instead, a genre Siansa Beats hears in some of its songs, which re-ranks the songs and doesn't steer the search.
 * [trait]: one of Siansa Beats' traits ([SongTraits]: vocals, a woman's voice, sad…), which puts the songs that have it
 * first ("more") or last ("less").
 */
@Serializable
data class SoundDial(val term: String, val value: Int = 0, val genre: Boolean = false, val trait: Boolean = false)

/**
 * The search words for a vibe made from the sliders alone (no description typed): energy, tempo and the
 * sounds turned up, e.g. "calm slow acoustic piano". Null when nothing is set.
 */
fun sliderQuery(tuning: VibeTuning): String? {
    val words = buildList {
        when {
            tuning.energy >= 2 -> add("energetic powerful")
            tuning.energy == 1 -> add("upbeat")
            tuning.energy == -1 -> add("mellow")
            tuning.energy <= -2 -> add("calm gentle")
        }
        when {
            tuning.tempo >= 2 -> add("fast tempo")
            tuning.tempo == 1 -> add("lively")
            tuning.tempo == -1 -> add("relaxed tempo")
            tuning.tempo <= -2 -> add("slow tempo")
        }
        tuning.sounds.filter { it.value > 0 }.sortedByDescending { it.value }.forEach { add(if (it.trait) SongTraits.named(it.term)?.words ?: it.term else it.term) }
    }
    if (words.isEmpty()) return if (tuning.familiar != 0 || tuning.sounds.any { it.value != 0 }) "music" else null
    return words.joinToString(" ") + " music"
}

/**
 * The search's songs re-ranked by the sliders (issue #339). Each song starts from its place in AudioMuse's
 * results (best first) and moves by how well it fits: higher or lower energy and tempo than the rest of the
 * results, and an artist you play often or rarely. Songs AudioMuse has no energy or tempo for, or sliders in
 * the middle, leave the order as it is. [familiarity] is plays per artist (lowercase name); [jitter] adds a
 * little variety per song (0 in tests). The mix's own sound dials ([SoundDial.genre]) move a song by how much it is of
 * that genre ([genres]: each song's, by its id), against the others found.
 */
fun rankVibe(
    hits: List<ClapHit>,
    features: Map<String, TrackFeatures>,
    familiarity: Map<String, Int>,
    tuning: VibeTuning,
    genres: Map<String, List<SongGenre>> = emptyMap(),
    jitter: (Int) -> Double = { 0.0 },
): List<ClapHit> {
    if (hits.isEmpty()) return hits
    // Each genre dial that's set: how much every song found is of that genre (0 for one that isn't), and their range.
    val dials = tuning.sounds.filter { it.genre && it.value != 0 }.map { dial ->
        val scores = hits.associate { hit -> hit.id to (genres[hit.id]?.firstOrNull { it.name.equals(dial.term, ignoreCase = true) }?.score ?: 0.0) }
        Triple(dial, scores, spread(scores.values.toList()))
    }
    val energy = spread(hits.mapNotNull { features[it.id]?.drive })
    val tempo = spread(hits.mapNotNull { features[it.id]?.tempo })
    val most = familiarity.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val n = hits.size.toDouble()
    return hits.mapIndexed { i, hit ->
        val f = features[hit.id]
        var score = 1.0 - i / n + jitter(i)
        score += SLIDER_PULL * pull(tuning.energy, f?.drive, energy)
        score += SLIDER_PULL * pull(tuning.tempo, f?.tempo, tempo)
        if (tuning.familiar != 0) {
            val known = (familiarity[hit.artist.lowercase()] ?: 0).toDouble() / most
            score += SLIDER_PULL * (tuning.familiar.toDouble() / VibeTuning.SLIDER_MAX) * (known * 2 - 1)
        }
        dials.forEach { (dial, scores, range) ->
            if (range != null) {
                val position = (scores.getValue(hit.id) - range.start) / (range.endInclusive - range.start)
                score += SLIDER_PULL * (dial.value.toDouble() / VibeTuning.DIAL_MAX) * (position * 2 - 1)
            }
        }
        hit to score
    }.sortedByDescending { it.second }.map { it.first }.let { traitsFirst(it, features, tuning) }
}

/**
 * [ranked] with the traits picked ([SoundDial.trait]) put in their place: songs that have every trait picked as "more"
 * first, songs that have one picked as "less" last, each group in its order. Songs Siansa Beats has no traits for sit
 * between. A trait, unlike a sound, isn't a matter of degree to a listener (a song is sung or it isn't), so it sorts.
 */
fun traitsFirst(ranked: List<ClapHit>, features: Map<String, TrackFeatures>, tuning: VibeTuning): List<ClapHit> {
    val more = tuning.sounds.filter { it.trait && it.value > 0 }.mapNotNull { SongTraits.named(it.term) }
    val less = tuning.sounds.filter { it.trait && it.value < 0 }.mapNotNull { SongTraits.named(it.term) }
    if (more.isEmpty() && less.isEmpty()) return ranked
    fun place(hit: ClapHit): Int {
        val traits = features[hit.id]?.traits.orEmpty()
        if (traits.isEmpty()) return 1
        if (less.any { (it.share(traits) ?: 0.0) >= SongTraits.HOLDS }) return 2
        return if (more.all { (it.share(traits) ?: 0.0) >= SongTraits.HOLDS }) 0 else 1
    }
    return ranked.sortedBy(::place) // stable: each group keeps its order
}

/** How much a slider at either end can move a song against its place in the search (1.0 = the whole list). */
private const val SLIDER_PULL = 0.6

private fun spread(values: List<Double>): ClosedFloatingPointRange<Double>? =
    if (values.size < 2) null else (values.min()..values.max()).takeIf { it.endInclusive > it.start }

/** -1…1: how far [slider] (−2…2) wants this value, given where it sits among the results. */
private fun pull(slider: Int, value: Double?, range: ClosedFloatingPointRange<Double>?): Double {
    if (slider == 0 || value == null || range == null) return 0.0
    val position = (value - range.start) / (range.endInclusive - range.start) // 0 = lowest, 1 = highest
    return (slider.toDouble() / VibeTuning.SLIDER_MAX) * (position * 2 - 1)
}

/**
 * [tuning] with the traits [query] asks for by name ([SongTraits.askedIn]) as "more", where neither picked nor set aside
 * already, while there's room ([FlowMood.MOST_PICKS]).
 */
fun withAskedTraits(tuning: VibeTuning, query: String): VibeTuning {
    val have = tuning.sounds.filter { it.value != 0 }.map { it.term.lowercase() }.toSet()
    val asked = SongTraits.askedIn(query).filter { it.label.lowercase() !in have }
    val room = (FlowMood.MOST_PICKS - have.size).coerceAtLeast(0)
    if (asked.isEmpty() || room == 0) return tuning
    val added = asked.take(room).map { SoundDial(it.label, FlowMood.MORE, trait = true) }
    return tuning.copy(sounds = tuning.sounds.filterNot { d -> added.any { it.term.equals(d.term, ignoreCase = true) } } + added)
}

/** [query] with the words of each trait picked as "more" ([SongTraits.Trait.words]) that it doesn't say already. */
fun withTraitWords(query: String, tuning: VibeTuning): String {
    val words = tuning.sounds.filter { it.trait && it.value > 0 }.mapNotNull { SongTraits.named(it.term)?.words }
        .filter { !query.contains(it, ignoreCase = true) }
    return (listOf(query.trim()) + words).filter { it.isNotEmpty() }.joinToString(" ")
}
