package app.siansa.net

import app.siansa.data.AppSettings

/**
 * What Siansa Beats answers about how songs sound (Siansa Beats 0.25, the owner, 11 Nov): songs like a song, song
 * paths, blends, mood radios, "your sound", and each song's tempo, key, moods and energy. Until then only
 * AudioMuse-AI answered them. Siansa Beats' capabilities list which it answers ([answers], by the names
 * AudioMuse-AI's API gives them, which the app's code asks by); the app asks it first for those and AudioMuse-AI
 * otherwise, so AudioMuse-AI needn't be set up in the app when Siansa Beats has the songs' sound.
 *
 * Where each is asked: at Siansa Beats' own paths under `/api/v1/sound/` when its capabilities say them ([where],
 * Siansa Beats 0.44, the owner, 13 Nov), else under [PREFIX], as every Siansa Beats before answered them.
 *
 * The list also says whether it takes listens ([BeatsListens.PATH], 0.29). It is kept from one run to the next ([onChanged], [restore]), so what the app offers doesn't wait for the
 * first answer after a start.
 */
object BeatsSound {
    /** Where a Siansa Beats from before 0.44 answers: AudioMuse-AI's paths under this. */
    const val PREFIX = "/api/v1/audiomuse"

    /** AudioMuse-AI's search by words, which Siansa Beats answers from 0.26. */
    const val WORDS = "/api/clap/search"

    @Volatile
    var answers: Set<String> = emptySet()
        private set

    /** Siansa Beats' own path for each answer (`/api/similar_tracks` → `/api/v1/sound/similar`); empty before 0.44. */
    @Volatile
    var where: Map<String, String> = emptyMap()
        private set

    @Volatile
    var onChanged: ((Set<String>, Map<String, String>) -> Unit)? = null

    /** Whether Siansa Beats has the sound of songs at all (what else it takes, such as listens, doesn't count). */
    val available: Boolean get() = answers.any { it != BeatsListens.PATH }

    /** Whether Siansa Beats answers AudioMuse-AI's [path] (`/api/similar_tracks`). */
    fun answers(path: String): Boolean = path.substringBefore('?') in answers

    /** What Siansa Beats' capabilities just said it answers, and where ([own]: its own path for each, from 0.44). */
    fun heard(paths: Set<String>, own: Map<String, String> = emptyMap()) {
        val kept = own.filter { (name, path) -> name in paths && path.startsWith("/api/") }
        if (paths == answers && kept == where) return
        answers = paths
        where = kept
        onChanged?.invoke(paths, kept)
    }

    fun restore(paths: Set<String>, own: Map<String, String> = emptyMap()) {
        answers = paths
        where = own
    }

    /** Where Siansa Beats answers [pathAndQuery] (`/api/similar_tracks?item_id=…`): its own path, or under [PREFIX]. */
    fun path(pathAndQuery: String): String {
        val name = pathAndQuery.substringBefore('?')
        val own = where[name] ?: return PREFIX + pathAndQuery
        return own + pathAndQuery.removePrefix(name)
    }
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
