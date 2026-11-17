package app.siansa.mix

/**
 * What a song is like by Siansa Beats' trained models (0.51): sung or not, whose voice, its moods, danceable. The owner
 * (16 Nov): vibes and the search by words should find vocal songs, a woman's or a man's voice and the feeling behind
 * them. Each trait is a share from 0 to 1 that Siansa Beats gives every song it has heard (`traits` in its sound
 * answers, [app.siansa.net.TrackFeatures.traits]); none from AudioMuse-AI or an older Siansa Beats.
 */
object SongTraits {
    /**
     * One trait to pick in a vibe: [label] as shown and as the dial's term, the [words] it adds to the sound search
     * when picked as "more", the [group] it's listed under, and a song's [share] of it from its traits (null when
     * it hasn't them).
     */
    class Trait(val label: String, val words: String, val group: String, val share: (Map<String, Double>) -> Double?)

    /** The picker's headings, in its order. */
    const val VOICE = "Voice"
    const val MOOD = "Mood"
    const val SOUND = "Sound"

    private fun of(key: String): (Map<String, Double>) -> Double? = { it[key] }

    /** A trait at or over this holds for the song. */
    const val HOLDS = 0.5

    /** In the order the picker lists them: the voice, then moods, then the sound, each under its heading ([Trait.group]). */
    val ALL: List<Trait> = listOf(
        Trait("Vocals", "vocals singing", VOICE, of("voice")),
        Trait("Instrumental", "instrumental", VOICE, { t -> t["voice"]?.let { 1 - it } }),
        // Whose voice: of the voice, so an instrumental song is neither.
        Trait("Female vocals", "female vocals", VOICE, { t -> t["voice"]?.let { v -> t["female"]?.let { v * it } } }),
        Trait("Male vocals", "male vocals", VOICE, { t -> t["voice"]?.let { v -> t["female"]?.let { v * (1 - it) } } }),
        Trait("Happy", "happy", MOOD, of("happy")),
        Trait("Sad", "sad", MOOD, of("sad")),
        Trait("Relaxed", "relaxed", MOOD, of("relaxed")),
        Trait("Party", "party", MOOD, of("party")),
        Trait("Aggressive", "aggressive", MOOD, of("aggressive")),
        Trait("Cheerful", "cheerful fun", MOOD, of("mirex_cheerful")),
        Trait("Bittersweet", "wistful bittersweet", MOOD, of("mirex_bittersweet")),
        Trait("Passionate", "passionate rousing", MOOD, of("mirex_passionate")),
        Trait("Intense", "intense fiery", MOOD, of("mirex_intense")),
        Trait("Quirky", "quirky playful", MOOD, of("mirex_humorous")),
        Trait("Danceable", "danceable", SOUND, of("danceable")),
        Trait("Acoustic", "acoustic", SOUND, of("acoustic")),
        Trait("Electronic", "electronic", SOUND, of("electronic")),
    )

    fun named(label: String): Trait? = ALL.firstOrNull { it.label.equals(label.trim(), ignoreCase = true) }

    /** The feelings both AudioMuse-AI (from CLAP) and Siansa Beats' trained models give: the trained one is used. */
    val TRAINED_FEELINGS = listOf("happy", "sad", "relaxed", "party", "aggressive", "danceable")

    // Words in a typed description that ask for a trait, longest first so "female vocals" isn't read as "vocals".
    private val ASKED = listOf(
        Regex("""\b(female|women'?s?|woman'?s?|girl)\s+(vocals?|voices?|singers?|singing)\b""") to "Female vocals",
        Regex("""\b(male|men'?s?|man'?s?)\s+(vocals?|voices?|singers?|singing)\b""") to "Male vocals",
        Regex("""\b(instrumental|no vocals|without vocals|no singing|no voice)\b""") to "Instrumental",
        Regex("""\b(vocals?|singing|sung|singer|with voice)\b""") to "Vocals",
    )

    /**
     * The traits a typed description asks for by name (the owner: the search by words should find vocal songs and a
     * woman's or a man's voice): "chill female vocals" asks for Female vocals, "sad instrumental piano" for
     * Instrumental and Sad. Each once, in the order found.
     */
    fun askedIn(text: String): List<Trait> {
        // "relaxed tempo" is the tempo slider's own words (sliderQuery), not the mood: a vibe made from the sliders alone
        // mustn't pick Relaxed for the listener.
        var rest = " " + text.lowercase().replace(Regex("""\brelaxed\s+tempo\b"""), " ") + " "
        val found = ArrayList<Trait>()
        for ((pattern, label) in ASKED) {
            if (pattern.containsMatchIn(rest)) {
                if (label == "Vocals" && found.any { it.label != "Instrumental" }) continue // a voice was named already
                named(label)?.let { if (it !in found) found += it }
                rest = pattern.replace(rest, " ")
            }
        }
        val words = Regex("""[a-z]+""").findAll(rest).map { it.value }.toSet()
        for (t in ALL.drop(4)) if (t.label.lowercase() in words && t !in found) found += t
        return found
    }
}
