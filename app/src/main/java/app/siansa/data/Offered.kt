package app.siansa.data

import app.siansa.BuildConfig

/**
 * What this build offers. Siansa Beats' card shows in Siansa Dev (and test builds) first, not yet in the app everyone
 * has (the owner, 10 Nov), so a message can't send someone there to set up a service they have no page for.
 */
object Offered {
    val beats: Boolean = BuildConfig.BUILD_TYPE != "release"

    /** The services that work out what songs sound like, as a message names them. */
    val soundServices: String = soundServices(beats)

    /** "Needs …" in the few words a slide's label has room for. */
    val needsSound: String = if (beats) "Needs Siansa Beats" else "Needs AudioMuse"

    internal fun soundServices(beats: Boolean): String = if (beats) "Siansa Beats or AudioMuse-AI" else "AudioMuse-AI"
}
