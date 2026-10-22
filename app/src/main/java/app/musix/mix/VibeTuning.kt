package app.musix.mix

import app.musix.net.ClapHit
import app.musix.net.Steer
import app.musix.net.TrackFeatures
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * The sliders on Dscvr (issue #339): how a vibe is tuned. Moving them re-runs the sound search and re-ranks
 * what it finds, without asking the AI again.
 *
 * - [energy] and [tempo] run from -2 (calmer, slower) to +2 (more energy, faster). They re-rank by AudioMuse's
 *   own energy and tempo for each song (`/api/sync`).
 * - [familiar] runs from -2 (niche: artists you rarely play) to +2 (familiar: artists you play most).
 * - [sounds]: up to [MAX_SOUNDS] of AudioMuse's sound concepts, each a dial from -5 to +5 that steers the
 *   search away from or towards the concept. AudioMuse takes a steering weight of 1, 2, 3, 5 or 10, so the
 *   five steps either side of the middle are those weights ([STEER_WEIGHTS]).
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

    /** The sound dials as steering for AudioMuse's search: the ones set, each with its weight. */
    fun steering(): List<Steer> = sounds.filter { it.value != 0 }.map { Steer(it.term, it.value > 0, steerWeight(it.value)) }

    /** A short description for the log and the vibe page: "more energy · faster · +piano ×3". */
    fun summary(): String = buildList {
        if (energy != 0) add(if (energy > 0) "more energy" else "calmer")
        if (tempo != 0) add(if (tempo > 0) "faster" else "slower")
        if (familiar != 0) add(if (familiar > 0) "familiar" else "niche")
        sounds.filter { it.value != 0 }.forEach { add((if (it.value > 0) "+" else "−") + it.term + " ×" + steerWeight(it.value)) }
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

/** One sound dial: an AudioMuse concept and how far towards (+) or away from (−) it. */
@Serializable
data class SoundDial(val term: String, val value: Int = 0)

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
        tuning.sounds.filter { it.value > 0 }.sortedByDescending { it.value }.forEach { add(it.term) }
    }
    if (words.isEmpty()) return if (tuning.familiar != 0 || tuning.sounds.any { it.value != 0 }) "music" else null
    return words.joinToString(" ") + " music"
}

/**
 * The search's songs re-ranked by the sliders (issue #339). Each song starts from its place in AudioMuse's
 * results (best first) and moves by how well it fits: higher or lower energy and tempo than the rest of the
 * results, and an artist you play often or rarely. Songs AudioMuse has no energy or tempo for, or sliders in
 * the middle, leave the order as it is. [familiarity] is plays per artist (lowercase name); [jitter] adds a
 * little variety per song (0 in tests).
 */
fun rankVibe(
    hits: List<ClapHit>,
    features: Map<String, TrackFeatures>,
    familiarity: Map<String, Int>,
    tuning: VibeTuning,
    jitter: (Int) -> Double = { 0.0 },
): List<ClapHit> {
    if (hits.isEmpty()) return hits
    val energy = spread(hits.mapNotNull { features[it.id]?.energy })
    val tempo = spread(hits.mapNotNull { features[it.id]?.tempo })
    val most = familiarity.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val n = hits.size.toDouble()
    return hits.mapIndexed { i, hit ->
        val f = features[hit.id]
        var score = 1.0 - i / n + jitter(i)
        score += SLIDER_PULL * pull(tuning.energy, f?.energy, energy)
        score += SLIDER_PULL * pull(tuning.tempo, f?.tempo, tempo)
        if (tuning.familiar != 0) {
            val known = (familiarity[hit.artist.lowercase()] ?: 0).toDouble() / most
            score += SLIDER_PULL * (tuning.familiar.toDouble() / VibeTuning.SLIDER_MAX) * (known * 2 - 1)
        }
        hit to score
    }.sortedByDescending { it.second }.map { it.first }
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
