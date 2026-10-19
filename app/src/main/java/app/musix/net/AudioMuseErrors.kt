package app.musix.net

/**
 * AudioMuse-AI's error codes (its docs/ERROR_CODES.md) as plain messages (issue #208). Its answers carry
 * `error_code` and `error_message`; older servers only the HTTP status and an `error` text.
 */
object AudioMuseErrors {
    fun message(code: Int?, http: Int, reason: String): String = when (code) {
        1003 -> "AudioMuse didn't understand the request: $reason"
        1004 -> "AudioMuse doesn't know this song yet. Has it been analysed?"
        1005 -> "AudioMuse needs a login: set an API token, or its username and password, in Settings"
        1006 -> "AudioMuse doesn't allow this for your login"
        1010 -> "AudioMuse doesn't have that music server"
        in 1101..1107 -> "AudioMuse couldn't reach Navidrome: $reason"
        3002 -> "AudioMuse's index isn't ready yet. Run an analysis in AudioMuse first."
        3005 -> "AudioMuse's index is still being prepared. Try again in a while."
        4001, 4002 -> "AudioMuse's database isn't reachable right now"
        6005 -> "AudioMuse couldn't make Your sound: $reason"
        null -> when (http) {
            404 -> "AudioMuse doesn't know this song yet. Has it been analysed?"
            503 -> "AudioMuse's index isn't ready yet ($reason)"
            else -> "AudioMuse answered HTTP $http: $reason"
        }
        else -> reason
    }
}
