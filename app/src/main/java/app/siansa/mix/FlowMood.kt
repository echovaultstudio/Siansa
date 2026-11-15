package app.siansa.mix

import app.siansa.data.Genre
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Flow's mood pad in words and colour (the owner's pick, 15 Nov: "Mood pad" with the many-coloured palette). One
 * finger sets energy (up) and speed (right); the pad, the picks and Play take the colour of where it is, and a name
 * says what that mood is. Sounds and genres are picked from a list with +, never suggested: each pick is "more" or
 * "less" of it. Everything here is plain numbers and words, so it's tested without a screen.
 */
object FlowMood {
    /** A pick's dial: "more" and "less" of a sound or a genre ([VibeTuning.steerWeight] 3, the search's own default). */
    const val MORE = 3
    const val LESS = -3

    /** The most sounds and genres picked at once. */
    const val MOST_PICKS = 6

    /** The genres offered before anything is typed: the ones with most songs. Searching reaches all of them. */
    const val MAIN_GENRES = 24

    /** Below this far from the middle (of 1), the mood is "Balanced". */
    private const val MIDDLE = 0.16f
    private const val FAR = 0.55f

    /** One way counts as the mood's own when it's this many times the other. */
    private const val LEADS = 1.8f

    /** The pad's corners (red, green, blue): calm and slow, calm and fast, lively and slow, lively and fast. */
    private val CALM_SLOW = intArrayOf(43, 58, 143)
    private val CALM_FAST = intArrayOf(31, 138, 138)
    private val LIVELY_SLOW = intArrayOf(176, 64, 154)
    private val LIVELY_FAST = intArrayOf(255, 138, 61)

    /** The middle has a colour of its own: four bright corners blended alone meet in a muddy grey. */
    private val MIDDLE_COLOUR = intArrayOf(106, 91, 214)
    private const val MIDDLE_SHARE = 0.8f

    /** The mood's deep shade (the glow at the top of the page, the pad's far corners) is this much of its colour. */
    const val DEEP = 0.42f

    /** A colour lighter than this takes dark ink, a darker one white. */
    private const val LIGHT = 0.56f

    /** A pad step (−2…2) as the pad's own measure, −1…1. */
    fun at(step: Int): Float = step.coerceIn(-2, 2) / 2f

    /** The mood's name for [energy] and [tempo], each −1…1. */
    fun name(energy: Float, tempo: Float): String {
        val e = abs(energy)
        val t = abs(tempo)
        return when {
            max(e, t) < MIDDLE -> "Balanced"
            e > t * LEADS -> if (energy > 0) "Lift me up" else "Wind down"
            t > e * LEADS -> if (tempo > 0) "Keep it moving" else "Take it slow"
            energy > 0 -> if (tempo > 0) "Get moving" else "Slow burn"
            else -> if (tempo > 0) "Easy groove" else "Late-night drift"
        }
    }

    /** What the mood does to the music, in plain words: "Calmer and a bit faster". */
    fun feel(energy: Float, tempo: Float): String {
        val e = when {
            energy > FAR -> "High energy"
            energy > MIDDLE -> "Livelier"
            energy < -FAR -> "Very calm"
            energy < -MIDDLE -> "Calmer"
            else -> null
        }
        val t = when {
            tempo > FAR -> "fast"
            tempo > MIDDLE -> "a bit faster"
            tempo < -FAR -> "slow"
            tempo < -MIDDLE -> "a bit slower"
            else -> null
        }
        return when {
            e != null && t != null -> "$e and $t"
            e != null -> e
            t != null -> t.replaceFirstChar { it.uppercase() }
            else -> "Balanced"
        }
    }

    /** Under the mood's name: what it does, or where it is while nothing is moved. */
    fun about(energy: Float, tempo: Float): String =
        if (max(abs(energy), abs(tempo)) < MIDDLE) "Right in the middle: not too calm, not too busy." else feel(energy, tempo)

    /** Everything that's set, for the Play button: "Calmer and a bit faster · more piano · less vocals · new to you". */
    fun summary(tuning: VibeTuning): String = buildList {
        add(feel(at(tuning.energy), at(tuning.tempo)))
        val more = tuning.sounds.filter { it.value > 0 }
        val less = tuning.sounds.filter { it.value < 0 }
        if (more.isNotEmpty()) add("more " + more.joinToString(", ") { it.term.lowercase() })
        if (less.isNotEmpty()) add("less " + less.joinToString(", ") { it.term.lowercase() })
        if (tuning.familiar != 0) add(if (tuning.familiar > 0) "your favourites" else "new to you")
    }.joinToString(" · ")

    /** The mood's colour (red, green, blue, 0–255) for [energy] and [tempo], each −1…1. */
    fun colour(energy: Float, tempo: Float): IntArray {
        val e = energy.coerceIn(-1f, 1f)
        val t = tempo.coerceIn(-1f, 1f)
        val across = (t + 1) / 2
        val up = (e + 1) / 2
        val middle = (1 - abs(e)) * (1 - abs(t)) * MIDDLE_SHARE
        return IntArray(3) { i ->
            val corners = (CALM_SLOW[i] * (1 - across) + CALM_FAST[i] * across) * (1 - up) +
                (LIVELY_SLOW[i] * (1 - across) + LIVELY_FAST[i] * across) * up
            (corners * (1 - middle) + MIDDLE_COLOUR[i] * middle).roundToInt()
        }
    }

    /** True when text and icons on [colour] are dark: it's a light colour. */
    fun darkInk(colour: IntArray): Boolean = (0.2126f * colour[0] + 0.7152f * colour[1] + 0.0722f * colour[2]) / 255f > LIGHT

    /** The sounds and genres picked: the dials that are set. */
    fun picks(tuning: VibeTuning): List<SoundDial> = tuning.sounds.filter { it.value != 0 }

    /** True while one more can be picked. */
    fun hasRoom(tuning: VibeTuning): Boolean = picks(tuning).size < MOST_PICKS

    /** True when [name] is picked, as more or less. */
    fun picked(tuning: VibeTuning, name: String): Boolean = tuning.sounds.any { it.value != 0 && it.term.equals(name, ignoreCase = true) }

    /**
     * [tuning] with [name] picked as "more" ([genre]: a genre, which re-ranks the songs; a sound steers the search).
     * Already picked, or no room left: unchanged.
     */
    fun pick(tuning: VibeTuning, name: String, genre: Boolean): VibeTuning {
        if (picked(tuning, name) || !hasRoom(tuning)) return tuning
        val rest = tuning.sounds.filterNot { it.term.equals(name, ignoreCase = true) }
        return tuning.copy(sounds = rest + SoundDial(name, MORE, genre))
    }

    /**
     * A tap on a pick: more, then less, then gone. A pick that's gone stays as a dial in the middle, so a vibe made
     * with the AI's own sounds doesn't get them back when its last pick is taken off ([MixGenerator.vibeSongs]).
     */
    fun tap(tuning: VibeTuning, name: String): VibeTuning = tuning.copy(
        sounds = tuning.sounds.map { if (it.term.equals(name, ignoreCase = true)) it.copy(value = if (it.value > 0) LESS else 0) else it },
    )

    /** [tuning] without [name]: its dial in the middle. */
    fun drop(tuning: VibeTuning, name: String): VibeTuning =
        tuning.copy(sounds = tuning.sounds.map { if (it.term.equals(name, ignoreCase = true)) it.copy(value = 0) else it })

    /**
     * The picks of [mine] laid over a vibe's starting dials ([start], the AI's own sounds): mine first and as I set
     * them, then the AI's that I didn't pick, with energy, speed and artists as I set them.
     */
    fun over(start: VibeTuning, mine: VibeTuning): VibeTuning {
        val picked = picks(mine)
        val taken = picked.map { it.term.lowercase() }.toSet()
        return start.copy(
            energy = mine.energy, tempo = mine.tempo, familiar = mine.familiar,
            sounds = picked + start.sounds.filter { it.term.lowercase() !in taken },
        )
    }

    /** Artists, as the three choices: 0 Favourites, 1 A mix, 2 New to me. */
    fun artists(familiar: Int): Int = if (familiar > 0) 0 else if (familiar < 0) 2 else 1

    /** The Niche ↔ Familiar slider for a choice of [artists]. */
    fun familiar(choice: Int): Int = when (choice) {
        0 -> VibeTuning.SLIDER_MAX
        2 -> -VibeTuning.SLIDER_MAX
        else -> 0
    }

    /**
     * The names to pick from, for what's typed ([find]): without it the first [most] of [all] (the main ones first, as
     * they come); with it every one that has the words in it, those starting with them first.
     */
    fun <T> offered(all: List<T>, find: String, most: Int, name: (T) -> String): List<T> {
        val words = find.trim().lowercase()
        if (words.isEmpty()) return all.take(most)
        val found = all.filter { name(it).lowercase().contains(words) }
        return found.filter { name(it).lowercase().startsWith(words) } + found.filterNot { name(it).lowercase().startsWith(words) }
    }

    /** The genres to pick from: the main ones (most songs), or every one found for what's typed. */
    fun genres(all: List<Genre>, find: String): List<Genre> = offered(all, find, MAIN_GENRES) { it.name }
}

/** A song's genre tags (as the server gives them, "Rock; Indie") as genres to rank by, when Siansa Beats says none. */
fun tagGenres(tag: String?): List<SongGenre> =
    tag.orEmpty().split(';', '/', ',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.map { SongGenre(it, 1.0) }
