package app.siansa.net

import app.siansa.data.AppSettings

/**
 * What Siansa Beats answers about how songs sound (Siansa Beats 0.25, the owner, 11 Nov): songs like a song, song
 * paths, blends, mood radios, "your sound", and each song's tempo, key, moods and energy. Until then only
 * AudioMuse-AI answered them. Siansa Beats answers them at AudioMuse-AI's own paths, under [PREFIX], and its
 * capabilities list which ([answers]); the app asks it first for those and AudioMuse-AI otherwise, so AudioMuse-AI
 * needn't be set up in the app when Siansa Beats has the songs' sound.
 *
 * The list is kept from one run to the next ([onChanged], [restore]), so what the app offers doesn't wait for the
 * first answer after a start.
 */
object BeatsSound {
    const val PREFIX = "/api/v1/audiomuse"

    /** AudioMuse-AI's search by words, which Siansa Beats answers from 0.26. */
    const val WORDS = "/api/clap/search"

    @Volatile
    var answers: Set<String> = emptySet()
        private set

    @Volatile
    var onChanged: ((Set<String>) -> Unit)? = null

    /** Whether Siansa Beats has the sound of songs at all. */
    val available: Boolean get() = answers.isNotEmpty()

    /** Whether Siansa Beats answers AudioMuse-AI's [path] (`/api/similar_tracks`). */
    fun answers(path: String): Boolean = path.substringBefore('?') in answers

    /** What Siansa Beats' capabilities just said it answers. */
    fun heard(paths: Set<String>) {
        if (paths == answers) return
        answers = paths
        onChanged?.invoke(paths)
    }

    fun restore(paths: Set<String>) {
        answers = paths
    }

    /** Where Siansa Beats answers AudioMuse-AI's [pathAndQuery]. */
    fun path(pathAndQuery: String): String = PREFIX + pathAndQuery
}

/**
 * Whether what's found from how songs sound can be asked for: of AudioMuse-AI when it's set up, or of Siansa Beats
 * once it has the songs' sound. The search by words is [wordsReady].
 */
val AppSettings.soundReady: Boolean get() = audioMuseReady || (beatsReady && BeatsSound.available)

/**
 * Whether music can be found by describing it in words (mixes from a description, the mood radios, vibes and their
 * sliders, cards made from a sound, the sound search): AudioMuse-AI does it, and Siansa Beats from 0.26, with the
 * same models, once it has the songs' vectors for it.
 */
val AppSettings.wordsReady: Boolean get() = audioMuseReady || (beatsReady && BeatsSound.answers(BeatsSound.WORDS))
