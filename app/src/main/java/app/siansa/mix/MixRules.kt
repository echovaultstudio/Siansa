package app.siansa.mix

import app.siansa.data.Mix

/**
 * When Home makes and shows the daily mixes (issue #486). A user's mixes stay as they were made: they're made again
 * only when the user asks (refresh) or when the vibe's own rules call for one (a new day, a new vibe for the hour),
 * never because the source filter shows other music. AI mixes need AudioMuse-AI; without it they're hidden.
 */
object MixRules {
    /** Vibe mixes (today's mix, the carousel, the vibe tiles): only with AudioMuse set up for the user in use. */
    fun showsAiMixes(audioMuseReady: Boolean): Boolean = audioMuseReady

    /** A mix from your listening: those that need AudioMuse (Your sound, Discover for you) only with it. */
    fun showsPersonal(kind: MixGenerator.Personal, audioMuseReady: Boolean): Boolean = audioMuseReady || !kind.needsAudioMuse

    /**
     * Today's mixes Home lists: made [today], for a vibe that applies now ([applicableIds]) or from your listening
     * ([personalIds]), whatever music was shown when they were made.
     */
    fun todays(mixes: List<Mix>, today: String, personalIds: Set<String>, applicableIds: Set<String>): List<Mix> =
        mixes.filter { m -> m.date == today && (m.selectorId in personalIds || m.selectorId in applicableIds) }
}
