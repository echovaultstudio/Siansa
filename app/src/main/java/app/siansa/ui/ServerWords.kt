package app.siansa.ui

import app.siansa.net.SourceKind

/**
 * How Siansa names the user's server in what it shows (beta testers #31): someone on Emby shouldn't read "Navidrome".
 * Most texts just say "your server"; these are the few where the server's own name or abilities matter. A Subsonic
 * server is called OpenSubsonic, not Navidrome: it may be Gonic, Airsonic or another, and the app doesn't know (#573).
 */
internal object ServerWords {
    /** The server's own name, e.g. for a playlist without an owner: "Playlist · Emby · 12 songs". */
    fun name(kind: SourceKind): String = when (kind) {
        SourceKind.EMBY -> "Emby"
        else -> "OpenSubsonic"
    }

    /** Whether Siansa can have the server convert songs (Opus, MP3): Subsonic servers yes, Emby sends the original. */
    fun converts(kind: SourceKind): Boolean = kind == SourceKind.OPENSUBSONIC

    /** Settings › Playback, above the streaming quality. */
    fun streamingHint(kind: SourceKind): String =
        if (converts(kind)) {
            "Opus is converted by your server (on Navidrome, with its \"opus\" transcoding profile). It saves data on mobile; the original file is best on Wi-Fi. Applies from the next song."  // server-name: ok (how Navidrome does it)
        } else {
            "${name(kind)} sends the original file, so the quality chosen here doesn't change anything for your music. It applies to OpenSubsonic servers."
        }

    /** Downloads, under the download quality. */
    fun downloadHint(kind: SourceKind): String =
        if (converts(kind)) "Opus is converted by your server and takes far less space than the original file. Applies to songs downloaded from now on."
        else "${name(kind)} sends the original file, so downloads are always in the original quality."
}
