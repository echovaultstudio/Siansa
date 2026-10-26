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

    /**
     * Home's "Today's mix" without making one (beta testers #37): the mix of this hour's vibe ([currentId]) when
     * it was made today, otherwise the vibe mix made last today ([vibeIds]), so the mix you have stays when the
     * hour's vibe moves on. Null before the day's first: then it's made. A new one otherwise only with ↻.
     */
    fun todaysMix(mixes: List<Mix>, today: String, currentId: String?, vibeIds: Set<String>): Mix? {
        val made = mixes.filter { it.date == today && it.selectorId in vibeIds }
        return made.firstOrNull { it.selectorId == currentId } ?: made.maxByOrNull { it.createdAt }
    }

    /**
     * Whether Home makes [carouselId]'s mixes by itself (beta testers #37): only for the day's first carousel,
     * and again when that carousel was changed in Vibes. When a later part of the day begins ("Your day" after
     * "Your morning"), its cards wait for a tap.
     */
    fun makesCarouselByItself(mixes: List<Mix>, today: String, carouselId: String): Boolean =
        mixes.none { m ->
            m.date == today && m.selectorId.startsWith(CAROUSEL_PREFIX) && !m.selectorId.startsWith("$CAROUSEL_PREFIX$carouselId-")
        }

    /** How a carousel's mixes are named: "carousel-<id>-v<version>-<index>" ([app.siansa.data.Carousel.selectorFor]). */
    private const val CAROUSEL_PREFIX = "carousel-"
}
