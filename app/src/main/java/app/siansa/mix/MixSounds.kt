package app.siansa.mix

import app.siansa.net.Steer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs

/** A genre a song is in by its sound, as Siansa Beats hears it, and how much ([score], 0 to 1). */
data class SongGenre(
    val name: String,
    val score: Double,
    /** Discogs' genre the style is of ("Electronic", "Rock"), and the style itself; empty from a Siansa Beats that doesn't say. */
    val genre: String = "",
    val style: String = "",
)

/** A sound a mix has to offer: a genre [songs] of its songs are in, while others aren't. */
data class MixSound(val name: String, val songs: Int)

/** A song's place on the sound pad, 0 to 1 each way: [x] from slow to fast, [y] from calm to full of energy. */
data class PadDot(val x: Float, val y: Float)

/** What a vibe's songs have to offer for shaping it: its own [sounds], and each song's place on the pad ([dots]). */
data class MixOffer(val sounds: List<MixSound> = emptyList(), val dots: List<PadDot> = emptyList())

object MixSounds {
    /** The most sounds a mix offers to pick from. */
    const val MOST = 8
    /** Genres are only told apart in a mix where this many songs have any. */
    const val MIN_KNOWN = 6
    /** A sound is worth a dial when at least this many songs have it, */
    const val MIN_SONGS = 3
    /** and between this share and [HIGH] of them: a dial for a sound nearly every song has, or nearly none, moves nothing. */
    const val LOW = 0.12
    const val HIGH = 0.8

    /** The pad's edges: a tempo from [SLOW] to [FAST] beats a minute, */
    const val SLOW = 60.0
    const val FAST = 180.0
    /** and AudioMuse's energy from [CALM] to [LIVELY] (the range it scales a song's energy to). */
    const val CALM = 0.01
    const val LIVELY = 0.15
    /** A song's arousal (Siansa Beats 0.35), from the calmest to the most driving the owner's trial library held. */
    const val SETTLED = 3.0
    const val DRIVING = 7.5
}

/**
 * The sounds a mix has to offer (the owner, 11 Nov: an ambient mix needs no dial for more rock): the genres its songs
 * are in ([genres]: each song's, by its id), keeping only those where the songs differ, the ones that split the mix
 * most evenly first. A genre every song is in, or hardly any, is left out.
 */
fun mixSounds(genres: Map<String, List<SongGenre>>, most: Int = MixSounds.MOST): List<MixSound> {
    val known = genres.count { it.value.isNotEmpty() }
    if (known < MixSounds.MIN_KNOWN) return emptyList()
    val counts = LinkedHashMap<String, Int>()
    genres.values.forEach { song -> song.map { it.name }.distinct().forEach { counts[it] = (counts[it] ?: 0) + 1 } }
    return counts.entries
        .filter { (_, n) -> n >= MixSounds.MIN_SONGS && n.toDouble() / known in MixSounds.LOW..MixSounds.HIGH }
        .sortedWith(compareBy<Map.Entry<String, Int>> { abs(it.value.toDouble() / known - 0.5) }.thenByDescending { it.value }.thenBy { it.key })
        .take(most)
        .map { MixSound(it.key, it.value) }
}

/**
 * Where a song of [tempo] (beats a minute) sits on the sound pad, and how high: by its [arousal] (Siansa Beats 0.35:
 * how energetic it is, from [MixSounds.SETTLED] to [MixSounds.DRIVING]) where there is one, else by [energy]
 * (AudioMuse's). Null without a tempo and one of the two.
 */
fun padPlace(tempo: Double?, energy: Double?, arousal: Double? = null): PadDot? {
    if (tempo == null || tempo <= 0.0) return null
    val y = when {
        arousal != null -> (arousal - MixSounds.SETTLED) / (MixSounds.DRIVING - MixSounds.SETTLED)
        energy != null -> (energy - MixSounds.CALM) / (MixSounds.LIVELY - MixSounds.CALM)
        else -> return null
    }
    val x = ((tempo - MixSounds.SLOW) / (MixSounds.FAST - MixSounds.SLOW)).coerceIn(0.0, 1.0)
    return PadDot(x.toFloat(), y.coerceIn(0.0, 1.0).toFloat())
}

/** Siansa Beats' answer to `songs/genres`: each song's genres by its id, its best first. */
fun songGenresOf(answer: JsonObject?): Map<String, List<SongGenre>> {
    val songs = answer?.get("songs") as? JsonObject ?: return emptyMap()
    return songs.mapValues { (_, list) ->
        (list as? JsonArray).orEmpty().mapNotNull { g ->
            val o = g as? JsonObject ?: return@mapNotNull null
            val name = (o["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SongGenre(
                name, (o["score"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0,
                (o["genre"] as? JsonPrimitive)?.content.orEmpty(), (o["style"] as? JsonPrimitive)?.content.orEmpty(),
            )
        }
    }.filterValues { it.isNotEmpty() }
}
