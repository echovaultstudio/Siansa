package app.siansa.data

/**
 * What this build offers, so a message can't send someone to set up a service they have no page for. Siansa Beats
 * showed in Siansa Dev and test builds only at first (the owner, 10 Nov); it's in the app everyone has from the
 * release that brings it (the owner, 14 Nov).
 */
object Offered {
    val beats: Boolean = true

    /** The services that work out what songs sound like, as a message names them. */
    val soundServices: String = soundServices(beats)

    /** "Needs …" in the few words a slide's label has room for. */
    val needsSound: String = if (beats) "Needs Siansa Beats" else "Needs AudioMuse-AI"

    internal fun soundServices(beats: Boolean): String = if (beats) "Siansa Beats or AudioMuse-AI" else "AudioMuse-AI"
}
