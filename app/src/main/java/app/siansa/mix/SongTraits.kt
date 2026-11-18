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
    class Trait(
        val label: String,
        val words: String,
        val group: String,
        val share: (Map<String, Double>) -> Double?,
        /**
         * AudioMuse-AI's words for it among a song's five strongest tags (MusiCNN's, `mood_vector`: the owner, 18 Nov),
         * from whichever service answers about songs' sound: Siansa Beats when it's on, else AudioMuse-AI, never both.
         */
        val tags: List<String> = emptyList(),
        /** Whether Siansa Beats' trained models give it ([share]); a line of tags only has none. */
        val trained: Boolean = true,
    ) {
        /**
         * The song's share of it: its trained trait where it has one, else 1 when one of [tags] is among its strongest
         * tags and 0 when not; null when it has neither.
         */
        fun shareOf(features: app.siansa.net.TrackFeatures?): Double? {
            features ?: return null
            if (trained && features.traits.isNotEmpty()) share(features.traits)?.let { return it }
            if (tags.isEmpty() || features.genres.isEmpty()) return null
            val strongest = features.genres.keys.mapTo(HashSet()) { it.lowercase() }
            return if (tags.any { it.lowercase() in strongest }) 1.0 else 0.0
        }
    }

    /** The picker's headings, in its order. */
    const val VOICE = "Voice"
    const val MOOD = "Mood"
    // "Style", not "Sound": the picker has a Sounds tab of its own (the owner, 17 Nov).
    const val STYLE = "Style"
    /** AudioMuse-AI's own mood words, from its tags (the owner, 18 Nov). */
    const val TAGS = "Tags"

    private fun of(key: String): (Map<String, Double>) -> Double? = { it[key] }

    /** A trait at or over this holds for the song. */
    const val HOLDS = 0.5

    /** In the order the picker lists them: the voice, then moods, then the style, each under its heading ([Trait.group]). */
    val ALL: List<Trait> = listOf(
        Trait("Vocals", "vocals singing", VOICE, of("voice")),
        Trait("Instrumental", "instrumental", VOICE, { t -> t["voice"]?.let { 1 - it } }),
        // Whose voice: of the voice, so an instrumental song is neither.
        Trait("Female vocals", "female vocals", VOICE, { t -> t["voice"]?.let { v -> t["female"]?.let { v * it } } }),
        Trait("Male vocals", "male vocals", VOICE, { t -> t["voice"]?.let { v -> t["female"]?.let { v * (1 - it) } } }),
        Trait("Happy", "happy", MOOD, of("happy"), tags = listOf("happy")),
        Trait("Sad", "sad", MOOD, of("sad"), tags = listOf("sad")),
        Trait("Relaxed", "relaxed", MOOD, of("relaxed")),
        Trait("Party", "party", MOOD, of("party"), tags = listOf("party")),
        Trait("Aggressive", "aggressive", MOOD, of("aggressive")),
        Trait("Cheerful", "cheerful fun", MOOD, of("mirex_cheerful")),
        Trait("Bittersweet", "wistful bittersweet", MOOD, of("mirex_bittersweet")),
        Trait("Passionate", "passionate rousing", MOOD, of("mirex_passionate")),
        Trait("Intense", "intense fiery", MOOD, of("mirex_intense")),
        Trait("Quirky", "quirky playful", MOOD, of("mirex_humorous")),
        Trait("Danceable", "danceable", STYLE, of("danceable")),
        Trait("Acoustic", "acoustic", STYLE, of("acoustic")),
        Trait("Electronic", "electronic", STYLE, of("electronic")),
        // AudioMuse-AI's mood words from its tags, which no trained model gives.
        tagged("Beautiful", "beautiful", "beautiful"),
        tagged("Chill", "chill chillout", "chill", "chillout"),
        tagged("Mellow", "mellow", "mellow"),
        tagged("Easy listening", "easy listening", "easy listening"),
        tagged("Catchy", "catchy", "catchy"),
        tagged("Sexy", "sexy sensual", "sexy"),
    )

    private fun tagged(label: String, words: String, vararg tags: String) =
        Trait(label, words, TAGS, { null }, tags = tags.toList(), trained = false)

    /**
     * The lines Flow's Moods offers: with Siansa Beats' trained models ([trained]) all of them; with only tags
     * ([tags]: AudioMuse-AI's, or a Siansa Beats without traits) those its tags can say.
     */
    fun offered(trained: Boolean, tags: Boolean): List<Trait> = ALL.filter { (trained && it.trained) || (tags && it.tags.isNotEmpty()) }

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
        for (t in ALL.drop(4)) if (t.trained && t.label.lowercase() in words && t !in found) found += t
        return found
    }
}
