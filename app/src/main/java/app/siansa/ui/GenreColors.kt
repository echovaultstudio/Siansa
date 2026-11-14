package app.siansa.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * How a genre feels, for its colours (the owner, 10 Nov): hard and fast music red, calm music blue or green, the rest
 * warm in between. Read from the genre's name: the first word list it matches decides, calm ones first, so "Liquid
 * Funk" and "Deep House" are calm though their family isn't.
 */
enum class GenreEnergy { CALM, WARM, HARD }

private val CALM_WORDS = listOf(
    "ambient", "chill", "downtempo", "lounge", "jazz", "classical", "piano", "folk", "acoustic", "lofi", "lo-fi", "new age",
    "soundtrack", "score", "liquid", "deep house", "deep", "minimal", "trip hop", "trip-hop", "soul", "blues", "country",
    "reggae", "dub", "bossa", "easy listening", "meditation", "sleep", "neo-classical", "neoclassical", "dream", "shoegaze",
    "balearic", "organic", "idm", "atmospheric", "post-rock", "post rock", "singer", "ballad", "contemporary", "orchestral",
)
private val HARD_WORDS = listOf(
    "drum and bass", "drum & bass", "drum n bass", "drum'n'bass", "dnb", "d&b", "jungle", "neurofunk", "halftime", "dubstep",
    "riddim", "grime", "hardcore", "hardstyle", "gabber", "hard", "metal", "punk", "thrash", "grindcore", "industrial", "techno",
    "trance", "psy", "breakbeat", "breaks", "bass", "drill", "trap", "crunk", "noise", "speed", "frenchcore", "uptempo", "rave",
    "emo", "screamo", "garage rock",
)

/** The energy of genre [name], from the word lists; warm when nothing matches. */
fun genreEnergy(name: String): GenreEnergy {
    val n = " " + name.lowercase().replace('/', ' ').replace('-', ' ').replace(Regex("\\s+"), " ").trim() + " "
    fun has(word: String) = n.contains(" " + word.replace('-', ' ') + " ") || (word.length > 4 && n.contains(word.replace('-', ' ')))
    return when {
        CALM_WORDS.any(::has) -> GenreEnergy.CALM
        HARD_WORDS.any(::has) -> GenreEnergy.HARD
        else -> GenreEnergy.WARM
    }
}

private val PALETTES = mapOf(
    // Reds, crimson to deep orange-red.
    GenreEnergy.HARD to listOf(
        0xFFE53935 to 0xFF5C0A12, 0xFFD32F4F to 0xFF4A0A22, 0xFFEF5A2A to 0xFF6B1410, 0xFFC62828 to 0xFF3A0A0A,
        0xFFE0433F to 0xFF4D0F1F, 0xFFB71C3C to 0xFF3D0716,
    ),
    // Warm: amber, orange, magenta, purple.
    GenreEnergy.WARM to listOf(
        0xFFF2B33D to 0xFFD9542B, 0xFFE8862A to 0xFF7A2A15, 0xFFD6338A to 0xFF4A1470, 0xFF8E44AD to 0xFF2C0F3D,
        0xFFC2416B to 0xFF4D0F2A, 0xFFE0703A to 0xFF5A1A3A,
    ),
    // Calm: blues, teals and greens.
    GenreEnergy.CALM to listOf(
        0xFF2E8B9A to 0xFF143A52, 0xFF4B7BD6 to 0xFF1C2655, 0xFF3A9A5B to 0xFF0F3B2A, 0xFF00897B to 0xFF0B2E3A,
        0xFF3F8FC4 to 0xFF0F2A45, 0xFF5BA07A to 0xFF16362A,
    ),
)

/** A genre's tile colours: a gradient in its energy's colours, the same each time for the same genre. */
fun genreColors(name: String): Pair<Color, Color> {
    val palette = PALETTES.getValue(genreEnergy(name))
    val (a, b) = palette[abs(name.lowercase().hashCode()) % palette.size]
    return Color(a) to Color(b)
}
