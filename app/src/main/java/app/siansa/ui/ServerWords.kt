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
        SourceKind.JELLYFIN -> "Jellyfin"
        else -> "OpenSubsonic"
    }

    /** Settings › Streaming & storage, above the streaming quality. Every server converts to Opus (Emby and Jellyfin, issue #1204). */
    fun streamingHint(kind: SourceKind): String =
        if (kind.embyLike) {
            "Opus is converted by ${name(kind)}. It saves data on mobile; the original file is best on Wi-Fi. Applies from the next song."
        } else {
            "Opus is converted by your server (on Navidrome, with its \"opus\" transcoding profile). It saves data on mobile; the original file is best on Wi-Fi. Applies from the next song."  // server-name: ok (how Navidrome does it)
        }

    /** Downloads, under the download quality. */
    fun downloadHint(kind: SourceKind): String =
        "Opus is converted by your server and takes far less space than the original file. Applies to songs downloaded from now on."
}
