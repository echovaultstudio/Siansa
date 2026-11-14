package app.siansa.net

/**
 * AudioMuse-AI's error codes (its docs/ERROR_CODES.md) as plain messages (issue #208). Its answers carry
 * `error_code` and `error_message`; older servers only the HTTP status and an `error` text.
 */
object AudioMuseErrors {
    /**
     * A song AudioMuse can't find. Besides "not analysed yet", the usual cause is AudioMuse reading another server
     * than this user's (an Emby user, beta report 810dc85c): its name in AudioMuse goes in Settings › Connections.
     */
    const val NOT_KNOWN = "AudioMuse-AI doesn't know this song. Has it been analysed? If AudioMuse-AI reads more than one " +
        "music server, enter its name in Settings › Connections, under the server the song is from (another user's " +
        "too): Server name in AudioMuse-AI."

    /**
     * The Siansa plugin isn't installed on this AudioMuse (issue #788): a plugin call answered by AudioMuse's own web
     * page, not JSON. Before, this read as "doesn't know this song", which sent a tester after the wrong thing.
     */
    const val PLUGIN_MISSING = "The Siansa Analysis plugin isn't installed on this AudioMuse-AI. Add it under Plugins in " +
        "AudioMuse-AI (see the docs, AudioMuse-AI › Install the Siansa Analysis plugin)."

    /** Whether a failed answer is a web page rather than the API: AudioMuse's page for a path it doesn't have. */
    fun isWebPage(body: String): Boolean = body.trimStart().startsWith("<!doctype", ignoreCase = true) || body.trimStart().startsWith("<html", ignoreCase = true)

    /** The message for a failed call to the plugin's path: a web page for 404 means the plugin isn't there. */
    fun pluginMessage(code: Int?, http: Int, reason: String, body: String): String =
        if (code == null && http == 404 && isWebPage(body)) PLUGIN_MISSING else message(code, http, reason)

    fun message(code: Int?, http: Int, reason: String): String = when (code) {
        1003 -> "AudioMuse-AI didn't understand the request: $reason"
        1004 -> NOT_KNOWN
        1005 -> "AudioMuse-AI needs a login: set an API token, or its username and password, in Settings"
        1006 -> "AudioMuse-AI doesn't allow this for your login"
        1010 -> "AudioMuse-AI doesn't have that music server"
        in 1101..1107 -> "AudioMuse-AI couldn't reach its music server: $reason"
        3002 -> "AudioMuse-AI's index isn't ready yet. Run an analysis in AudioMuse-AI first."
        3005 -> "AudioMuse-AI's index is still being prepared. Try again in a while."
        4001, 4002 -> "AudioMuse-AI's database isn't reachable right now"
        6005 -> "AudioMuse-AI couldn't make Your sound: $reason"
        null -> when (http) {
            404 -> NOT_KNOWN
            503 -> "AudioMuse-AI's index isn't ready yet ($reason)"
            else -> "AudioMuse-AI answered HTTP $http: $reason"
        }
        else -> reason
    }
}
