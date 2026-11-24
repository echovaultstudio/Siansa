package app.siansa.net

import app.siansa.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val available: Boolean get() = answers.any { it != BeatsListens.PATH && it != BeatsListens.SKIPS }

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

    /**
     * Whether Siansa Beats has each song's voice and moods (its traits, 0.51), as its capabilities last said. Kept, so
     * Flow offers them at once: asked for when the list was opened, the answer took seconds and the third list came
     * late (the owner, 17 Nov: "it doesn't show mood until I click on genres").
     */
    private val hasTraits = MutableStateFlow(false)
    val traits: StateFlow<Boolean> = hasTraits.asStateFlow()

    @Volatile
    var onTraits: ((Boolean) -> Unit)? = null

    /** What Siansa Beats' capabilities just said of its traits. */
    fun traits(has: Boolean) {
        if (hasTraits.value == has) return
        hasTraits.value = has
        onTraits?.invoke(has)
    }

    fun restoreTraits(has: Boolean) {
        hasTraits.value = has
    }

    /** Whether Siansa Beats makes mixes from the listening (0.53), as its capabilities last said; kept as [traits] is. */
    private val hasMixes = MutableStateFlow(false)
    val mixes: StateFlow<Boolean> = hasMixes.asStateFlow()

    @Volatile
    var onMixes: ((Boolean) -> Unit)? = null

    fun mixes(has: Boolean) {
        if (hasMixes.value == has) return
        hasMixes.value = has
        onMixes?.invoke(has)
    }

    fun restoreMixes(has: Boolean) {
        hasMixes.value = has
    }

    /**
     * Whether Siansa Beats plans the app's playlists with its own AI (0.57, `POST /api/v1/plan`: the owner, 18 Nov), as
     * its capabilities last said; kept as [traits] is. While it does, the phone's own AI isn't used ([beatsPlansReady]).
     */
    private val hasPlans = MutableStateFlow(false)
    val plans: StateFlow<Boolean> = hasPlans.asStateFlow()

    @Volatile
    var onPlans: ((Boolean) -> Unit)? = null

    fun plans(has: Boolean) {
        if (hasPlans.value == has) return
        hasPlans.value = has
        onPlans?.invoke(has)
    }

    fun restorePlans(has: Boolean) {
        hasPlans.value = has
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
val AppSettings.soundReady: Boolean get() = audioMuseReady || (beatsSoundReady && BeatsSound.available)

/**
 * Whether music can be found by describing it in words (mixes from a description, the mood radios, vibes and their
 * sliders, cards made from a sound, the sound search): AudioMuse-AI does it, and Siansa Beats from 0.26, with the
 * same models, once it has the songs' vectors for it.
 */
val AppSettings.wordsReady: Boolean get() = audioMuseReady || (beatsSoundReady && BeatsSound.answers(BeatsSound.WORDS))

/**
 * Whether the playlists' plans (the title and sound search of a vibe, Today's mix, Flow's words and the mood radios)
 * come from Siansa Beats' AI (0.57, the owner, 18 Nov: "if beats is selected and configured then AI on the phone can be
 * auto disabled"). While they do, the phone's AI is switched off by itself and not listed.
 */
val AppSettings.beatsPlansReady: Boolean get() = beatsReady && BeatsSound.plans.value

/**
 * Whether a playlist can be planned by an AI: Siansa Beats' when it plans them, else the phone's own (for AudioMuse-AI
 * without Siansa Beats, or a Siansa Beats without an AI set up).
 */
val AppSettings.plansReady: Boolean get() = beatsPlansReady || aiReady
